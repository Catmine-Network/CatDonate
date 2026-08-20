package net.catmine.studio.catDonate.model

import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

enum class Telco(val apiName: String, val displayName: String, vararg val aliases: String) {
    VIETTEL("VIETTEL", "Viettel", "viettel"),
    VINAPHONE("VINAPHONE", "Vinaphone", "vina", "vinaphone"),
    MOBIFONE("MOBIFONE", "Mobifone", "mobi", "mobifone"),
    GARENA("GARENA", "Garena", "garena");

    companion object {
        fun parse(value: String): Telco? = entries.firstOrNull { telco ->
            telco.aliases.any { it.equals(value, ignoreCase = true) }
        }

    }
}

enum class TransactionStatus(val terminal: Boolean) {
    SUBMITTING(false),
    PENDING(false),
    SUCCESS(true),
    FAILED(true),
    NEEDS_REVIEW(true),
    POLL_EXHAUSTED(true),
    REVIEW_EXPIRED(true)
}

enum class RewardState { NONE, PENDING, PROCESSING, COMPLETED, NEEDS_REVIEW }

data class CardSubmission(
    val playerId: UUID,
    val playerName: String,
    val telco: Telco,
    val amount: Long,
    val serial: String,
    val code: String,
)

sealed interface SubmissionResult {
    data class Accepted(
        val requestId: String,
        val nextCheckSeconds: Long,
    ) : SubmissionResult
    data class Duplicate(val requestId: String?) : SubmissionResult
    data class Cooldown(val remainingSeconds: Long) : SubmissionResult
    data class TooManyPending(val limit: Int) : SubmissionResult
    data object NotConfigured : SubmissionResult
    data class Invalid(val reason: String) : SubmissionResult
    data class Error(val requestId: String?) : SubmissionResult
}

data class TransactionRecord(
    val requestId: String,
    val playerId: UUID,
    val playerName: String,
    val telco: Telco,
    val declaredAmount: Long,
    val actualValue: Long?,
    val providerReceived: Long?,
    val status: TransactionStatus,
    val providerTransactionId: String?,
    val pollCount: Int,
    val nextPollAt: Instant?,
    val rewardCommandsJson: String?,
    val rewardMultiplier: BigDecimal?,
    val rewardState: RewardState,
    val rewardExecutedCount: Int,
    val encryptedCode: String?,
    val encryptedSerial: String?,
    val serialMasked: String,
    val fingerprint: String,
    val lastError: String?,
    val notificationKey: String?,
    val notificationDelivered: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val sensitiveExpiresAt: Instant?,
)

data class TransactionEvent(
    val requestId: String,
    val type: String,
    val oldStatus: TransactionStatus?,
    val newStatus: TransactionStatus?,
    val detail: String?,
    val actor: String?,
    val createdAt: Instant,
)

enum class AdminAction(val wireName: String) {
    RECHECK("kiem-tra-lai"), RETRY_REWARD("thu-lai"), CONFIRM("xac-nhan");

    companion object {
        fun parse(value: String): AdminAction? = entries.firstOrNull { it.wireName.equals(value, true) }
        val suggestions = entries.map { it.wireName }
    }
}
