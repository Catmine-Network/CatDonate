package net.catmine.studio.catDonate.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.catmine.studio.catDonate.model.CardSubmission
import net.catmine.studio.catDonate.model.RewardState
import net.catmine.studio.catDonate.model.Telco
import net.catmine.studio.catDonate.model.TransactionStatus
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class TransactionRepositoryTest {
    @TempDir lateinit var temp: Path
    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: TransactionRepository

    @BeforeEach
    fun setup() {
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${temp.resolve("test.db")}"
            maximumPoolSize = 1
            connectionInitSql = "PRAGMA foreign_keys=ON"
        })
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        repository = TransactionRepository(dataSource)
    }

    @AfterEach
    fun close() {
        dataSource.close()
    }

    @Test
    fun `unique fingerprint and audit trail survive state transitions`() {
        val now = Instant.parse("2026-08-20T00:00:00Z")
        val submission = CardSubmission(UUID.randomUUID(), "DemonDucky", Telco.VIETTEL, 10_000, "SERIAL01", "CODE001")
        assertEquals(CreateTransactionResult.Created, create("10001", submission, "fingerprint", now))
        assertInstanceOf(CreateTransactionResult.Duplicate::class.java, create("10002", submission, "fingerprint", now))

        repository.markPending("10001", 1, now.plusSeconds(60), "provider-1", null, "PROVIDER_PENDING", now)
        repository.markTerminal(
            "10001", TransactionStatus.SUCCESS, 10_000, 9_000, "provider-1", null, "SUCCESS",
            "[\"points give %player% 12\"]", BigDecimal("1.2"), RewardState.PENDING, null, now.plusSeconds(2),
        )
        val record = repository.find("10001")!!
        assertEquals(TransactionStatus.SUCCESS, record.status)
        assertEquals(RewardState.PENDING, record.rewardState)
        assertEquals(BigDecimal("1.2"), record.rewardMultiplier)
        assertNull(record.encryptedCode)
        assertNull(record.encryptedSerial)
        assertTrue(repository.events("10001").map { it.type }.containsAll(listOf("CREATED", "PROVIDER_PENDING", "TERMINAL")))
    }

    @Test
    fun `recovers interrupted reward and purges expired review secrets`() {
        val now = Instant.parse("2026-08-20T00:00:00Z")
        val submission = CardSubmission(UUID.randomUUID(), "DemonDucky", Telco.GARENA, 20_000, "SERIAL02", "CODE002")
        create("20001", submission, "fp-2", now)
        repository.markTerminal(
            "20001", TransactionStatus.SUCCESS, 20_000, null, null, null, "SUCCESS", "[\"give %player% stone\"]",
            BigDecimal.ONE, RewardState.PENDING, null, now,
        )
        repository.beginReward("20001", false, null, now)
        assertEquals(1, repository.recoverInterruptedRewards(now.plusSeconds(1)))
        assertEquals(RewardState.NEEDS_REVIEW, repository.find("20001")!!.rewardState)

        create("20002", submission.copy(code = "CODE003"), "fp-3", now)
        repository.markTerminal(
            "20002", TransactionStatus.POLL_EXHAUSTED, null, null, null, "timeout", "POLL_EXHAUSTED", null,
            null, RewardState.NONE, now.plus(7, ChronoUnit.DAYS), now,
        )
        assertEquals(1, repository.purgeExpiredSecrets(now.plus(8, ChronoUnit.DAYS)))
        val expired = repository.find("20002")!!
        assertEquals(TransactionStatus.REVIEW_EXPIRED, expired.status)
        assertNull(expired.encryptedCode)
        assertNull(expired.encryptedSerial)
    }

    @Test
    fun `terminal notification replaces an already delivered pending notification`() {
        val now = Instant.parse("2026-08-20T00:00:00Z")
        val submission = CardSubmission(UUID.randomUUID(), "DemonDucky", Telco.VIETTEL, 10_000, "SERIAL03", "CODE003")
        create("30001", submission, "fp-4", now)

        repository.markPending(
            "30001", 0, now.plusSeconds(60), null, null, "PROVIDER_PENDING", now, "PENDING",
        )
        repository.markNotificationDelivered("30001")
        assertTrue(repository.undelivered(submission.playerId).isEmpty())

        repository.markTerminal(
            "30001", TransactionStatus.FAILED, null, null, null, "Thẻ lỗi", "FAILED",
            null, null, RewardState.NONE, null, now.plusSeconds(1),
        )

        assertEquals(listOf("30001"), repository.undelivered(submission.playerId).map { it.requestId })
    }

    private fun create(requestId: String, submission: CardSubmission, fingerprint: String, now: Instant) =
        repository.create(requestId, submission, "encrypted-serial", "encrypted-code", "SE****01", fingerprint, 3, now, now.plusSeconds(60))
}
