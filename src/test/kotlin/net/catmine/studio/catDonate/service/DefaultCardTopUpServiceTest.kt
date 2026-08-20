package net.catmine.studio.catDonate.service

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.config.DonateConfig
import net.catmine.studio.catDonate.config.ProviderSettings
import net.catmine.studio.catDonate.config.RewardDefinition
import net.catmine.studio.catDonate.model.CardSubmission
import net.catmine.studio.catDonate.model.RewardState
import net.catmine.studio.catDonate.model.SubmissionResult
import net.catmine.studio.catDonate.model.Telco
import net.catmine.studio.catDonate.model.TransactionRecord
import net.catmine.studio.catDonate.model.TransactionStatus
import net.catmine.studio.catDonate.persistence.TransactionRepository
import net.catmine.studio.catDonate.provider.CardProviderClient
import net.catmine.studio.catDonate.provider.ProviderRequest
import net.catmine.studio.catDonate.provider.ProviderResponse
import net.catmine.studio.catDonate.security.CardSecrets
import org.bukkit.Location
import org.bukkit.entity.Player
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DefaultCardTopUpServiceTest {
    @TempDir lateinit var temp: Path
    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: TransactionRepository
    private lateinit var provider: FakeProvider
    private lateinit var rewards: FakeRewards
    private lateinit var notifier: FakeNotifier
    private lateinit var service: DefaultCardTopUpService
    private val now = Instant.parse("2026-08-20T00:00:00Z")
    private lateinit var clock: MutableClock

    @BeforeEach
    fun setup() {
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${temp.resolve("service.db")}"
            maximumPoolSize = 1
        })
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        repository = TransactionRepository(dataSource)
        provider = FakeProvider()
        rewards = FakeRewards()
        notifier = FakeNotifier()
        clock = MutableClock(now)
        service = DefaultCardTopUpService(
            config(), repository,
            CardSecrets.forTesting(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 1).toByte() }),
            DirectScheduler(), rewards, notifier, clock,
        ) { provider }
    }

    @AfterEach
    fun close() {
        dataSource.close()
    }

    @Test
    fun `status one multiplies reward amount and executes command once`() {
        provider.next = ProviderResponse(null, 1, 10_000, 10_000, 9_000, "tx-1", "ok")
        val result = submit(10_000)
        assertInstanceOf(SubmissionResult.Accepted::class.java, result)
        result as SubmissionResult.Accepted
        assertEquals(15, result.nextCheckSeconds)
        val requestId = result.requestId
        val record = repository.find(requestId)!!

        assertEquals(TransactionStatus.SUCCESS, record.status)
        assertEquals(10_000, record.actualValue)
        assertEquals(RewardState.COMPLETED, record.rewardState)
        assertEquals(listOf("points give %player% 12"), RewardSnapshotCodec.decode(record.rewardCommandsJson!!))
        assertEquals(BigDecimal("1.2"), record.rewardMultiplier)
        assertEquals(1, rewards.calls)
        assertEquals(listOf("points give %player% 12"), rewards.lastCommands)
        assertNull(record.encryptedCode)
        assertNull(record.encryptedSerial)
    }

    @Test
    fun `status two uses actual value and succeeds without reward when mapping is absent`() {
        provider.next = ProviderResponse(null, 2, 50_000, 30_000, 27_000, "tx-2", "wrong value")
        val result = submit(50_000) as SubmissionResult.Accepted
        val record = repository.find(result.requestId)!!

        assertEquals(TransactionStatus.SUCCESS, record.status)
        assertEquals(30_000, record.actualValue)
        assertEquals(RewardState.NONE, record.rewardState)
        assertEquals(0, rewards.calls)
        assertEquals("SUCCESS_NO_REWARD", record.notificationKey)
    }

    @Test
    fun `failed console command requires review without rerunning snapshot`() {
        rewards.result = RewardExecution(false, 0, "command failed")
        provider.next = ProviderResponse(null, 1, 10_000, 10_000, null, null, null)
        val result = submit(10_000) as SubmissionResult.Accepted
        val record = repository.find(result.requestId)!!

        assertEquals(RewardState.NEEDS_REVIEW, record.rewardState)
        assertEquals(1, rewards.calls)
        assertEquals("command failed", record.lastError)
        assertEquals("REWARD_FAILED", record.notificationKey)
        assertEquals(1, notifier.records.size)
    }

    @Test
    fun `mismatched request id never rewards and retains encrypted card for review`() {
        provider.next = ProviderResponse("different", 1, 10_000, 10_000, null, null, null)
        val result = submit(10_000) as SubmissionResult.Accepted
        val record = repository.find(result.requestId)!!

        assertEquals(TransactionStatus.NEEDS_REVIEW, record.status)
        assertEquals(RewardState.NEEDS_REVIEW, record.rewardState)
        assertEquals(0, rewards.calls)
        kotlin.test.assertNotNull(record.encryptedCode)
        kotlin.test.assertNotNull(record.sensitiveExpiresAt)
    }

    @Test
    fun `initial pending response is stored and reported once`() {
        provider.next = ProviderResponse(null, 99, 10_000, null, null, "tx-pending", "pending")

        val result = submit(10_000) as SubmissionResult.Accepted
        val record = repository.find(result.requestId)!!

        assertEquals(TransactionStatus.PENDING, record.status)
        assertEquals("PENDING", record.notificationKey)
        assertEquals(0, record.pollCount)
        assertEquals(1, notifier.records.size)
        assertEquals(result.requestId, notifier.records.single().requestId)
    }

    @Test
    fun `success is not reported until reward execution completes`() {
        provider.next = ProviderResponse(null, 1, 10_000, 10_000, null, "tx-reward", null)
        val pendingReward = CompletableFuture<RewardExecution>()
        rewards.future = pendingReward

        val result = submit(10_000) as SubmissionResult.Accepted

        val processing = repository.find(result.requestId)!!
        assertEquals(RewardState.PROCESSING, processing.rewardState)
        assertNull(processing.notificationKey)
        assertEquals(0, notifier.records.size)

        pendingReward.complete(RewardExecution(true, 1, null))

        assertEquals(RewardState.COMPLETED, repository.find(result.requestId)!!.rewardState)
        assertEquals("SUCCESS", notifier.records.single().notificationKey)
    }

    @Test
    fun `initial provider failure reports retry timing`() {
        provider.failure = RuntimeException("offline")

        val result = submit(10_000) as SubmissionResult.Accepted
        val record = repository.find(result.requestId)!!

        assertEquals(TransactionStatus.PENDING, record.status)
        assertEquals("PENDING", record.notificationKey)
        assertEquals(now.plusSeconds(15), record.nextPollAt)
        assertEquals("PENDING", notifier.records.single().notificationKey)
    }

    @Test
    fun `pending checks wait 15 seconds then add 5 seconds per unsuccessful check`() {
        provider.next = ProviderResponse(null, 99, 10_000, null, null, "tx-pending", "pending")

        val result = submit(10_000) as SubmissionResult.Accepted
        assertEquals(now.plusSeconds(15), repository.find(result.requestId)!!.nextPollAt)

        clock.current = now.plusSeconds(15)
        service.dispatchDue()

        val afterFirstCheck = repository.find(result.requestId)!!
        assertEquals(1, afterFirstCheck.pollCount)
        assertEquals(now.plusSeconds(35), afterFirstCheck.nextPollAt)
    }

    private fun submit(amount: Long): SubmissionResult {
        provider.requestIdFromRequest = true
        val result = service.submit(
            CardSubmission(UUID.randomUUID(), "DemonDucky", Telco.VIETTEL, amount, "SERIAL01", "CODE001")
        ).join()
        if (result is SubmissionResult.Accepted) service.startProcessing(result.requestId)
        return result
    }

    private fun config() = DonateConfig(
        provider = ProviderSettings("partner", "key", true, Duration.ofSeconds(1), Duration.ofSeconds(1)),
        cardList = Telco.entries.associateWith { setOf(10_000L, 50_000L) },
        rewards = mapOf(10_000L to RewardDefinition(BigDecimal.TEN, listOf("points give %player% %reward%"))),
        rewardMultiplier = BigDecimal("1.2"),
        pollInterval = Duration.ofSeconds(15), pollIntervalIncrement = Duration.ofSeconds(5),
        maxPollAttempts = 30, secretRetention = Duration.ofDays(7),
        submitCooldown = Duration.ofSeconds(5), maxPendingPerPlayer = 3,
    )

    private class FakeProvider : CardProviderClient {
        lateinit var next: ProviderResponse
        var failure: Throwable? = null
        var requestIdFromRequest = true
        override fun submit(request: ProviderRequest) = completed(request)
        override fun check(request: ProviderRequest) = completed(request)
        private fun completed(request: ProviderRequest): CompletableFuture<ProviderResponse> {
            failure?.let { throwable ->
                return CompletableFuture<ProviderResponse>().also { it.completeExceptionally(throwable) }
            }
            return CompletableFuture.completedFuture(
                if (requestIdFromRequest && next.requestId == null) next.copy(requestId = request.requestId) else next
            )
        }
    }

    private class FakeRewards : RewardExecutor {
        var result = RewardExecution(true, 1, null)
        var calls = 0
        var lastCommands = emptyList<String>()
        var future: CompletableFuture<RewardExecution>? = null
        override fun execute(record: TransactionRecord, commands: List<String>): CompletableFuture<RewardExecution> {
            calls++
            lastCommands = commands
            return future ?: CompletableFuture.completedFuture(result)
        }
    }

    private class FakeNotifier : OutcomeNotifier {
        val records = mutableListOf<TransactionRecord>()
        override fun notify(record: TransactionRecord) { records += record }
    }

    private class MutableClock(initial: Instant) : Clock() {
        @Volatile var current: Instant = initial
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = current
    }
}

private class DirectScheduler : CatScheduler {
    override fun runAsync(task: () -> Unit): ScheduledTask = execute(task)
    override fun runAsyncLater(delay: Long, unit: TimeUnit, task: () -> Unit): ScheduledTask = execute(task)
    override fun runAsyncTimer(delay: Long, period: Long, unit: TimeUnit, task: () -> Unit): ScheduledTask = execute(task)
    override fun runGlobal(task: () -> Unit): ScheduledTask = execute(task)
    override fun runGlobalTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask = execute(task)
    override fun runAt(location: Location, task: () -> Unit): ScheduledTask = execute(task)
    override fun runAtTimer(location: Location, delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask = execute(task)
    override fun runFor(player: Player, task: () -> Unit): ScheduledTask = execute(task)
    override fun runForOrNow(player: Player, task: () -> Unit): Boolean { task(); return true }
    override fun <T : Any?> supplyAsync(task: () -> T): CompletableFuture<T> = CompletableFuture.completedFuture(task())

    private fun execute(task: () -> Unit): ScheduledTask {
        task()
        return DUMMY_TASK
    }

    companion object {
        private val DUMMY_TASK = Proxy.newProxyInstance(
            ScheduledTask::class.java.classLoader,
            arrayOf(ScheduledTask::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as ScheduledTask
    }
}
