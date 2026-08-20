package net.catmine.studio.catDonate.service

import net.catmine.engine.cache.CooldownService
import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.config.DonateConfig
import net.catmine.studio.catDonate.model.AdminAction
import net.catmine.studio.catDonate.model.CardSubmission
import net.catmine.studio.catDonate.model.RewardState
import net.catmine.studio.catDonate.model.SubmissionResult
import net.catmine.studio.catDonate.model.Telco
import net.catmine.studio.catDonate.model.TransactionRecord
import net.catmine.studio.catDonate.model.TransactionStatus
import net.catmine.studio.catDonate.persistence.CreateTransactionResult
import net.catmine.studio.catDonate.persistence.TransactionRepository
import net.catmine.studio.catDonate.provider.Card2KClient
import net.catmine.studio.catDonate.provider.CardProviderClient
import net.catmine.studio.catDonate.provider.ProviderRequest
import net.catmine.studio.catDonate.provider.ProviderResponse
import net.catmine.studio.catDonate.security.CardSecrets
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

class DefaultCardTopUpService(
    initialConfig: DonateConfig,
    private val repository: TransactionRepository,
    private val secrets: CardSecrets,
    private val scheduler: CatScheduler,
    private val rewardExecutor: RewardExecutor,
    private val notifier: OutcomeNotifier,
    private val clock: Clock = Clock.systemUTC(),
    private val providerFactory: (DonateConfig) -> CardProviderClient = { Card2KClient(it.provider) },
) : CardTopUpService {
    private val config = AtomicReference(initialConfig)
    private val provider = AtomicReference(providerFactory(initialConfig))
    @Volatile private var cooldowns = CooldownService<UUID>(initialConfig.submitCooldown)
    private val polling = AtomicBoolean(false)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val requestIds = AtomicLong(clock.millis() * 1_000 + SecureRandom().nextInt(1_000))

    override fun cardOptions(): Map<Telco, Set<Long>> = config.get().cardList

    override fun submit(submission: CardSubmission): CompletableFuture<SubmissionResult> {
        val snapshot = config.get()
        validate(submission, snapshot)?.let { return CompletableFuture.completedFuture(SubmissionResult.Invalid(it)) }
        if (!snapshot.provider.configured) return CompletableFuture.completedFuture(SubmissionResult.NotConfigured)
        val gate = cooldowns.tryUse(submission.playerId)
        if (!gate.allowed) return CompletableFuture.completedFuture(
            SubmissionResult.Cooldown(gate.remaining.seconds.coerceAtLeast(1))
        )

        val requestId = nextRequestId()
        return scheduler.supplyAsync {
            val create = repository.create(
                requestId = requestId,
                submission = submission,
                encryptedSerial = secrets.encrypt(submission.serial),
                encryptedCode = secrets.encrypt(submission.code),
                serialMasked = CardSecrets.maskSerial(submission.serial),
                fingerprint = secrets.fingerprint(submission.telco.apiName, submission.serial, submission.code),
                maxPending = snapshot.maxPendingPerPlayer,
                now = clock.instant(),
                firstPollAt = clock.instant().plus(snapshot.pollInterval),
            )
            when (create) {
                CreateTransactionResult.Created -> SubmissionResult.Accepted(
                    requestId = requestId,
                    nextCheckSeconds = snapshot.pollInterval.seconds,
                )
                is CreateTransactionResult.Duplicate -> SubmissionResult.Duplicate(create.requestId)
                CreateTransactionResult.TooManyPending -> SubmissionResult.TooManyPending(snapshot.maxPendingPerPlayer)
            }
        }.exceptionally { SubmissionResult.Error(requestId) }
    }

    override fun startProcessing(requestId: String) {
        scheduler.runAsync {
            val record = repository.find(requestId) ?: return@runAsync
            if (record.status.terminal) return@runAsync
            val serial = runCatching { record.encryptedSerial?.let(secrets::decrypt) }.getOrNull()
            val code = runCatching { record.encryptedCode?.let(secrets::decrypt) }.getOrNull()
            if (serial == null || code == null) {
                review(record, "Không thể giải mã dữ liệu thẻ", retainSecrets = false)
                return@runAsync
            }
            val isCheck = record.status != TransactionStatus.SUBMITTING
            sendProvider(record, serial, code, isCheck, if (isCheck) record.pollCount + 1 else record.pollCount)
        }
    }

    override fun findStatus(playerId: UUID, requestId: String?): CompletableFuture<TransactionRecord?> =
        scheduler.supplyAsync { repository.findOwned(playerId, requestId) }

    override fun findAdmin(requestId: String): CompletableFuture<TransactionRecord?> =
        scheduler.supplyAsync { repository.find(requestId) }

    override fun recentSuccessfulTransactions(limit: Int): CompletableFuture<List<TransactionRecord>> =
        scheduler.supplyAsync { repository.recentSuccessful(limit) }

    override fun adminAction(requestId: String, action: AdminAction, actor: String): CompletableFuture<Boolean> =
        scheduler.supplyAsync {
            when (action) {
                AdminAction.CONFIRM -> repository.confirmReward(requestId, actor, clock.instant()).also { confirmed ->
                    if (confirmed) notifyLatest(requestId)
                }
                AdminAction.RETRY_REWARD -> {
                    val record = repository.beginReward(requestId, true, actor, clock.instant()) ?: return@supplyAsync false
                    executeReward(record)
                    true
                }
                AdminAction.RECHECK -> {
                    val record = repository.find(requestId) ?: return@supplyAsync false
                    val serial = record.encryptedSerial?.let(secrets::decrypt) ?: return@supplyAsync false
                    val code = record.encryptedCode?.let(secrets::decrypt) ?: return@supplyAsync false
                    val isCheck = record.status != TransactionStatus.SUBMITTING
                    sendProvider(record, serial, code, isCheck, if (isCheck) record.pollCount + 1 else record.pollCount)
                    true
                }
            }
        }

    override fun reload(config: DonateConfig): CompletableFuture<Boolean> = scheduler.supplyAsync {
        val old = this.config.get()
        if (old.provider.identity() != config.provider.identity() && repository.countActive() > 0) {
            return@supplyAsync false
        }
        this.config.set(config)
        if (old.provider.identity() != config.provider.identity() || old.provider.requestTimeout != config.provider.requestTimeout || old.provider.connectTimeout != config.provider.connectTimeout) {
            provider.set(providerFactory(config))
        }
        cooldowns.clearAll()
        cooldowns = CooldownService(config.submitCooldown)
        true
    }

    fun dispatchDue() {
        if (!polling.compareAndSet(false, true)) return
        try {
            val snapshot = config.get()
            val now = clock.instant()
            repository.purgeExpiredSecrets(now)
            val leaseUntil = now.plus(snapshot.provider.requestTimeout).plusSeconds(POLL_LEASE_GRACE_SECONDS)
            repository.claimDue(now, leaseUntil).forEach { record ->
                val serial = runCatching { record.encryptedSerial?.let(secrets::decrypt) }.getOrNull()
                val code = runCatching { record.encryptedCode?.let(secrets::decrypt) }.getOrNull()
                if (serial == null || code == null) {
                    review(record, "Không thể giải mã dữ liệu thẻ", retainSecrets = false)
                } else {
                    val isCheck = record.status != TransactionStatus.SUBMITTING
                    sendProvider(record, serial, code, isCheck, if (isCheck) record.pollCount + 1 else record.pollCount)
                }
            }
        } finally {
            polling.set(false)
        }
    }

    private fun sendProvider(record: TransactionRecord, serial: String, code: String, check: Boolean, pollCount: Int) {
        sendProvider(ProviderRequest(record.requestId, record.telco, record.declaredAmount, serial, code), check, pollCount)
    }

    private fun sendProvider(request: ProviderRequest, check: Boolean, pollCount: Int) {
        if (!inFlight.add(request.requestId)) return
        val future = try {
            if (check) provider.get().check(request) else provider.get().submit(request)
        } catch (_: Throwable) {
            inFlight.remove(request.requestId)
            temporaryFailure(request.requestId, pollCount, "Lỗi tạm thời khi liên hệ Card2K")
            return
        }
        future.whenComplete { response, failure ->
            try {
                if (failure != null) {
                    temporaryFailure(request.requestId, pollCount, "Lỗi tạm thời khi liên hệ Card2K")
                } else {
                    handleResponse(request, response, pollCount)
                }
            } catch (_: Throwable) {
                review(repository.find(request.requestId) ?: return@whenComplete, "Lỗi state machine", retainSecrets = true)
            } finally {
                inFlight.remove(request.requestId)
            }
        }
    }

    private fun handleResponse(request: ProviderRequest, response: ProviderResponse, pollCount: Int) {
        if (response.requestId != request.requestId) {
            val record = repository.find(request.requestId) ?: return
            review(record, "Provider trả request_id không khớp", retainSecrets = true)
            return
        }
        val now = clock.instant()
        val snapshot = config.get()
        when (response.status) {
            1 -> success(request.requestId, request.amount, response, false)
            2 -> {
                val actual = response.value
                if (actual == null || actual <= 0) {
                    review(repository.find(request.requestId) ?: return, "Thiếu mệnh giá thực cho status 2", retainSecrets = true)
                } else success(request.requestId, actual, response, true)
            }
            3 -> terminalFailure(request.requestId, response, "Thẻ lỗi", "FAILED")
            100 -> terminalFailure(request.requestId, response, response.message ?: "Provider từ chối request", "FAILED")
            4, 99 -> {
                if (pollCount >= snapshot.maxPollAttempts) {
                    val record = repository.find(request.requestId) ?: return
                    repository.markTerminal(
                        request.requestId, TransactionStatus.POLL_EXHAUSTED, null, response.receivedAmount,
                        response.transactionId, "Đã hết ${snapshot.maxPollAttempts} lượt kiểm tra", "POLL_EXHAUSTED",
                        null, null, RewardState.NONE, now.plus(snapshot.secretRetention), now,
                    )
                    notifyLatest(record.requestId)
                } else {
                    val initialNotification = if (pollCount == 0) {
                        if (response.status == 4) "MAINTENANCE" else "PENDING"
                    } else null
                    repository.markPending(
                        request.requestId, pollCount, now.plus(nextPollDelay(snapshot, pollCount)), response.transactionId,
                        if (response.status == 4) "Card2K đang bảo trì" else null,
                        if (response.status == 4) "PROVIDER_MAINTENANCE" else "PROVIDER_PENDING", now,
                        if (initialNotification != null) "PENDING" else null,
                    )
                    if (initialNotification != null) notifyLatest(request.requestId)
                }
            }
            else -> review(repository.find(request.requestId) ?: return, "Provider trả status không hỗ trợ: ${response.status}", retainSecrets = true)
        }
    }

    private fun success(requestId: String, rewardAmount: Long, response: ProviderResponse, wrongValue: Boolean) {
        val snapshot = config.get()
        val commands = snapshot.rewards[rewardAmount]?.resolve(snapshot.rewardMultiplier).orEmpty()
        val state = if (commands.isEmpty()) RewardState.NONE else RewardState.PENDING
        val successNotification = if (commands.isEmpty()) {
            "SUCCESS_NO_REWARD"
        } else if (wrongValue) {
            "WRONG_VALUE"
        } else {
            "SUCCESS"
        }
        val now = clock.instant()
        repository.markTerminal(
            requestId, TransactionStatus.SUCCESS, rewardAmount, response.receivedAmount, response.transactionId,
            if (wrongValue) "Thành công sai mệnh giá khai báo" else null,
            if (state == RewardState.NONE) successNotification else null,
            RewardSnapshotCodec.encode(commands), snapshot.rewardMultiplier, state, null, now,
        )
        if (state == RewardState.PENDING) {
            repository.beginReward(requestId, false, null, clock.instant())?.let(::executeReward)
        } else {
            notifyLatest(requestId)
        }
    }

    private fun terminalFailure(requestId: String, response: ProviderResponse, reason: String, notification: String) {
        val now = clock.instant()
        repository.markTerminal(
            requestId, TransactionStatus.FAILED, null, response.receivedAmount, response.transactionId,
            reason.take(500), notification, null, null, RewardState.NONE, null, now,
        )
        notifyLatest(requestId)
    }

    private fun temporaryFailure(requestId: String, pollCount: Int, reason: String) {
        val snapshot = config.get()
        val now = clock.instant()
        if (pollCount >= snapshot.maxPollAttempts) {
            val record = repository.find(requestId) ?: return
            repository.markTerminal(
                requestId, TransactionStatus.POLL_EXHAUSTED, null, null, null, reason, "POLL_EXHAUSTED",
                null, null, RewardState.NONE, now.plus(snapshot.secretRetention), now,
            )
            notifyLatest(record.requestId)
        } else {
            val notification = if (pollCount == 0) "TIMEOUT" else null
            repository.markPending(
                requestId, pollCount, now.plus(nextPollDelay(snapshot, pollCount)), null, reason, "PROVIDER_RETRY", now,
                if (notification != null) "PENDING" else null,
            )
            if (notification != null) notifyLatest(requestId)
        }
    }

    private fun review(record: TransactionRecord, reason: String, retainSecrets: Boolean) {
        val now = clock.instant()
        repository.markTerminal(
            record.requestId, TransactionStatus.NEEDS_REVIEW, null, null, null, reason, "NEEDS_REVIEW",
            record.rewardCommandsJson, record.rewardMultiplier, RewardState.NEEDS_REVIEW,
            if (retainSecrets) now.plus(config.get().secretRetention) else null, now,
        )
        notifyLatest(record.requestId)
    }

    private fun executeReward(record: TransactionRecord) {
        val commands = record.rewardCommandsJson?.let(RewardSnapshotCodec::decode).orEmpty()
        val successNotification = if (record.actualValue != null && record.actualValue != record.declaredAmount) {
            "WRONG_VALUE"
        } else {
            "SUCCESS"
        }
        rewardExecutor.execute(record, commands).whenComplete { result, failure ->
            val outcome = result ?: RewardExecution(false, 0, "Lỗi reward executor")
            repository.finishReward(
                record.requestId, failure == null && outcome.success, outcome.executed,
                if (failure == null) outcome.error else "Lỗi reward executor", successNotification, clock.instant(),
            )
            notifyLatest(record.requestId)
        }
    }

    private fun notifyLatest(requestId: String) {
        repository.find(requestId)?.let(notifier::notify)
    }

    private fun validate(submission: CardSubmission, config: DonateConfig): String? {
        if (submission.amount !in config.cardList[submission.telco].orEmpty()) return "INVALID_AMOUNT"
        if (!CARD_DATA.matches(submission.serial) || !CARD_DATA.matches(submission.code)) return "INVALID_CARD_DATA"
        return null
    }

    private fun nextRequestId(): String = requestIds.updateAndGet { previous ->
        maxOf(previous + 1, clock.millis() * 1_000 + SecureRandom().nextInt(1_000))
    }.toString()

    private fun nextPollDelay(config: DonateConfig, completedPolls: Int): java.time.Duration =
        config.pollInterval.plus(config.pollIntervalIncrement.multipliedBy(completedPolls.toLong()))

    companion object {
        private val CARD_DATA = Regex("^[A-Za-z0-9]{6,32}$")
        private const val POLL_LEASE_GRACE_SECONDS = 5L
    }
}
