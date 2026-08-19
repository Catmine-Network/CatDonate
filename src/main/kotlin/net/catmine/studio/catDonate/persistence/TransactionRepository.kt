package net.catmine.studio.catDonate.persistence

import net.catmine.studio.catDonate.model.CardSubmission
import net.catmine.studio.catDonate.model.RewardState
import net.catmine.studio.catDonate.model.Telco
import net.catmine.studio.catDonate.model.TransactionEvent
import net.catmine.studio.catDonate.model.TransactionRecord
import net.catmine.studio.catDonate.model.TransactionStatus
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

sealed interface CreateTransactionResult {
    data object Created : CreateTransactionResult
    data class Duplicate(val requestId: String?) : CreateTransactionResult
    data object TooManyPending : CreateTransactionResult
}

class TransactionRepository(private val dataSource: DataSource) {
    fun create(
        requestId: String,
        submission: CardSubmission,
        encryptedSerial: String,
        encryptedCode: String,
        serialMasked: String,
        fingerprint: String,
        maxPending: Int,
        now: Instant,
        firstPollAt: Instant,
    ): CreateTransactionResult = transaction { connection ->
        val pending = connection.prepareStatement(
            "SELECT COUNT(*) FROM card_transactions WHERE player_uuid=? AND status IN ('SUBMITTING','PENDING')"
        ).use { statement ->
            statement.setString(1, submission.playerId.toString())
            statement.executeQuery().use { it.next(); it.getInt(1) }
        }
        if (pending >= maxPending) return@transaction CreateTransactionResult.TooManyPending

        try {
            connection.prepareStatement(
                """INSERT INTO card_transactions
                    (request_id, player_uuid, player_name, provider, telco, declared_amount, status,
                     encrypted_code, encrypted_serial, serial_masked, fingerprint, created_at, updated_at, next_poll_at)
                    VALUES (?, ?, ?, 'CARD2K', ?, ?, 'SUBMITTING', ?, ?, ?, ?, ?, ?, ?)"""
            ).use { statement ->
                statement.setString(1, requestId)
                statement.setString(2, submission.playerId.toString())
                statement.setString(3, submission.playerName)
                statement.setString(4, submission.telco.name)
                statement.setLong(5, submission.amount)
                statement.setString(6, encryptedCode)
                statement.setString(7, encryptedSerial)
                statement.setString(8, serialMasked)
                statement.setString(9, fingerprint)
                statement.setLong(10, now.toEpochMilli())
                statement.setLong(11, now.toEpochMilli())
                statement.setLong(12, firstPollAt.toEpochMilli())
                statement.executeUpdate()
            }
            event(connection, requestId, "CREATED", null, TransactionStatus.SUBMITTING, "Đã lưu trước khi gọi provider", null, now)
            CreateTransactionResult.Created
        } catch (exception: SQLException) {
            if (!exception.isConstraintViolation()) throw exception
            val duplicateId = connection.prepareStatement(
                "SELECT request_id FROM card_transactions WHERE fingerprint=?"
            ).use { statement ->
                statement.setString(1, fingerprint)
                statement.executeQuery().use { if (it.next()) it.getString(1) else null }
            }
            CreateTransactionResult.Duplicate(duplicateId)
        }
    }

    fun find(requestId: String): TransactionRecord? = queryOne(
        "SELECT * FROM card_transactions WHERE request_id=?",
        { it.setString(1, requestId) },
    )

    fun findOwned(playerId: UUID, requestId: String?): TransactionRecord? = if (requestId == null) {
        queryOne(
            "SELECT * FROM card_transactions WHERE player_uuid=? ORDER BY created_at DESC LIMIT 1",
            { it.setString(1, playerId.toString()) },
        )
    } else {
        queryOne(
            "SELECT * FROM card_transactions WHERE player_uuid=? AND request_id=?",
            { statement ->
                statement.setString(1, playerId.toString())
                statement.setString(2, requestId)
            },
        )
    }

    fun due(now: Instant, limit: Int = 50): List<TransactionRecord> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """SELECT * FROM card_transactions
               WHERE status IN ('SUBMITTING','PENDING') AND next_poll_at IS NOT NULL AND next_poll_at<=?
               ORDER BY next_poll_at LIMIT ?"""
        ).use { statement ->
            statement.setLong(1, now.toEpochMilli())
            statement.setInt(2, limit)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toRecord()) } }
        }
    }

    fun countActive(): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT COUNT(*) FROM card_transactions WHERE status IN ('SUBMITTING','PENDING')"
        ).use { statement -> statement.executeQuery().use { it.next(); it.getInt(1) } }
    }

    fun markPending(
        requestId: String,
        pollCount: Int,
        nextPollAt: Instant,
        providerTransactionId: String?,
        error: String?,
        eventType: String,
        now: Instant,
        notificationKey: String? = null,
    ) = transaction { connection ->
        val old = status(connection, requestId) ?: return@transaction
        connection.prepareStatement(
            """UPDATE card_transactions SET status='PENDING', poll_count=?, next_poll_at=?,
               provider_transaction_id=COALESCE(?, provider_transaction_id), last_error=?,
               notification_key=COALESCE(?, notification_key),
               notification_delivered=CASE WHEN ? IS NULL THEN notification_delivered ELSE 0 END,
               updated_at=?
               WHERE request_id=?"""
        ).use { statement ->
            statement.setInt(1, pollCount)
            statement.setLong(2, nextPollAt.toEpochMilli())
            statement.setString(3, providerTransactionId)
            statement.setString(4, error)
            statement.setString(5, notificationKey)
            statement.setString(6, notificationKey)
            statement.setLong(7, now.toEpochMilli())
            statement.setString(8, requestId)
            statement.executeUpdate()
        }
        event(connection, requestId, eventType, old, TransactionStatus.PENDING, error, null, now)
    }

    fun markTerminal(
        requestId: String,
        newStatus: TransactionStatus,
        actualValue: Long?,
        providerReceived: Long?,
        providerTransactionId: String?,
        error: String?,
        notificationKey: String,
        rewardsJson: String?,
        rewardMultiplier: BigDecimal?,
        rewardState: RewardState,
        retainSecretsUntil: Instant?,
        now: Instant,
    ) = transaction { connection ->
        require(newStatus.terminal)
        val old = status(connection, requestId) ?: return@transaction
        connection.prepareStatement(
            """UPDATE card_transactions SET status=?, actual_value=?, provider_received=?,
               provider_transaction_id=COALESCE(?, provider_transaction_id), last_error=?, notification_key=?,
               notification_delivered=0,
               reward_commands=?, reward_multiplier=?, reward_state=?, next_poll_at=NULL, completed_at=?, updated_at=?,
               encrypted_code=?, encrypted_serial=?, sensitive_expires_at=? WHERE request_id=?"""
        ).use { statement ->
            statement.setString(1, newStatus.name)
            statement.setNullableLong(2, actualValue)
            statement.setNullableLong(3, providerReceived)
            statement.setString(4, providerTransactionId)
            statement.setString(5, error)
            statement.setString(6, notificationKey)
            statement.setString(7, rewardsJson)
            statement.setNullableDecimal(8, rewardMultiplier)
            statement.setString(9, rewardState.name)
            statement.setLong(10, now.toEpochMilli())
            statement.setLong(11, now.toEpochMilli())
            if (retainSecretsUntil == null) statement.setNull(12, java.sql.Types.VARCHAR) else statement.setString(12, findSecret(connection, requestId, "encrypted_code"))
            if (retainSecretsUntil == null) statement.setNull(13, java.sql.Types.VARCHAR) else statement.setString(13, findSecret(connection, requestId, "encrypted_serial"))
            statement.setNullableLong(14, retainSecretsUntil?.toEpochMilli())
            statement.setString(15, requestId)
            statement.executeUpdate()
        }
        event(connection, requestId, "TERMINAL", old, newStatus, error, null, now)
    }

    fun beginReward(requestId: String, allowRetry: Boolean, actor: String?, now: Instant): TransactionRecord? =
        transaction { connection ->
            val current = find(connection, requestId) ?: return@transaction null
            val allowed = current.rewardState == RewardState.PENDING ||
                (allowRetry && current.rewardState == RewardState.NEEDS_REVIEW && current.rewardCommandsJson != null)
            if (!allowed) return@transaction null
            connection.prepareStatement(
                "UPDATE card_transactions SET reward_state='PROCESSING', reward_executed_count=0, updated_at=? WHERE request_id=?"
            ).use {
                it.setLong(1, now.toEpochMilli()); it.setString(2, requestId); it.executeUpdate()
            }
            event(connection, requestId, if (allowRetry) "ADMIN_REWARD_RETRY" else "REWARD_STARTED", current.status, current.status, null, actor, now)
            current.copy(rewardState = RewardState.PROCESSING, rewardExecutedCount = 0, updatedAt = now)
        }

    fun finishReward(requestId: String, success: Boolean, executed: Int, error: String?, now: Instant) =
        transaction { connection ->
            val record = find(connection, requestId) ?: return@transaction
            val state = if (success) RewardState.COMPLETED else RewardState.NEEDS_REVIEW
            connection.prepareStatement(
                "UPDATE card_transactions SET reward_state=?, reward_executed_count=?, last_error=?, updated_at=? WHERE request_id=?"
            ).use {
                it.setString(1, state.name); it.setInt(2, executed); it.setString(3, error)
                it.setLong(4, now.toEpochMilli()); it.setString(5, requestId); it.executeUpdate()
            }
            event(connection, requestId, if (success) "REWARD_COMPLETED" else "REWARD_FAILED", record.status, record.status, error, null, now)
        }

    fun recoverInterruptedRewards(now: Instant): Int = transaction { connection ->
        val ids = connection.prepareStatement(
            "SELECT request_id FROM card_transactions WHERE reward_state='PROCESSING'"
        ).use { statement -> statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } } }
        connection.prepareStatement(
            "UPDATE card_transactions SET reward_state='NEEDS_REVIEW', last_error='Server dừng khi đang trao thưởng', updated_at=? WHERE reward_state='PROCESSING'"
        ).use { it.setLong(1, now.toEpochMilli()); it.executeUpdate() }
        ids.forEach { event(connection, it, "REWARD_RECOVERY_REVIEW", null, null, "Không tự động chạy lại để tránh thưởng trùng", null, now) }
        ids.size
    }

    fun confirmReward(requestId: String, actor: String, now: Instant): Boolean = transaction { connection ->
        val record = find(connection, requestId) ?: return@transaction false
        if (record.rewardState !in setOf(RewardState.PENDING, RewardState.NEEDS_REVIEW, RewardState.PROCESSING)) return@transaction false
        connection.prepareStatement(
            "UPDATE card_transactions SET reward_state='COMPLETED', last_error=NULL, updated_at=? WHERE request_id=?"
        ).use { it.setLong(1, now.toEpochMilli()); it.setString(2, requestId); it.executeUpdate() }
        event(connection, requestId, "ADMIN_CONFIRMED", record.status, record.status, "Đánh dấu hoàn thành, không chạy lệnh", actor, now)
        true
    }

    fun markNotificationDelivered(requestId: String) {
        dataSource.connection.use { connection -> connection.prepareStatement(
            "UPDATE card_transactions SET notification_delivered=1 WHERE request_id=?"
        ).use { it.setString(1, requestId); it.executeUpdate() } }
    }

    fun undelivered(playerId: UUID): List<TransactionRecord> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT * FROM card_transactions WHERE player_uuid=? AND notification_key IS NOT NULL AND notification_delivered=0 ORDER BY created_at"
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toRecord()) } }
        }
    }

    fun purgeExpiredSecrets(now: Instant): Int = transaction { connection ->
        val records = connection.prepareStatement(
            "SELECT request_id,status FROM card_transactions WHERE sensitive_expires_at IS NOT NULL AND sensitive_expires_at<=?"
        ).use { statement ->
            statement.setLong(1, now.toEpochMilli())
            statement.executeQuery().use { rows -> buildList {
                while (rows.next()) add(rows.getString(1) to TransactionStatus.valueOf(rows.getString(2)))
            } }
        }
        connection.prepareStatement(
            """UPDATE card_transactions SET encrypted_code=NULL, encrypted_serial=NULL, sensitive_expires_at=NULL,
               status=CASE WHEN status IN ('POLL_EXHAUSTED','NEEDS_REVIEW') THEN 'REVIEW_EXPIRED' ELSE status END, updated_at=?
               WHERE sensitive_expires_at IS NOT NULL AND sensitive_expires_at<=?"""
        ).use { it.setLong(1, now.toEpochMilli()); it.setLong(2, now.toEpochMilli()); it.executeUpdate() }
        records.forEach { (id, old) -> event(connection, id, "SECRET_PURGED", old, TransactionStatus.REVIEW_EXPIRED, "Dữ liệu nhạy cảm đã hết hạn", null, now) }
        records.size
    }

    fun events(requestId: String): List<TransactionEvent> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM transaction_events WHERE request_id=? ORDER BY created_at").use { statement ->
            statement.setString(1, requestId)
            statement.executeQuery().use { rows -> buildList {
                while (rows.next()) add(TransactionEvent(
                    requestId = rows.getString("request_id"), type = rows.getString("event_type"),
                    oldStatus = rows.getString("old_status")?.let(TransactionStatus::valueOf),
                    newStatus = rows.getString("new_status")?.let(TransactionStatus::valueOf),
                    detail = rows.getString("detail"), actor = rows.getString("actor"),
                    createdAt = Instant.ofEpochMilli(rows.getLong("created_at")),
                ))
            } }
        }
    }

    private fun queryOne(sql: String, bind: (java.sql.PreparedStatement) -> Unit): TransactionRecord? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement -> bind(statement); statement.executeQuery().use { if (it.next()) it.toRecord() else null } }
        }

    private fun find(connection: Connection, requestId: String): TransactionRecord? = connection.prepareStatement(
        "SELECT * FROM card_transactions WHERE request_id=?"
    ).use { it.setString(1, requestId); it.executeQuery().use { rows -> if (rows.next()) rows.toRecord() else null } }

    private fun status(connection: Connection, requestId: String): TransactionStatus? = connection.prepareStatement(
        "SELECT status FROM card_transactions WHERE request_id=?"
    ).use { it.setString(1, requestId); it.executeQuery().use { rows -> if (rows.next()) TransactionStatus.valueOf(rows.getString(1)) else null } }

    private fun findSecret(connection: Connection, requestId: String, column: String): String? = connection.prepareStatement(
        "SELECT $column FROM card_transactions WHERE request_id=?"
    ).use { it.setString(1, requestId); it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null } }

    private fun event(
        connection: Connection, requestId: String, type: String, old: TransactionStatus?, new: TransactionStatus?,
        detail: String?, actor: String?, now: Instant,
    ) {
        connection.prepareStatement(
            "INSERT INTO transaction_events(request_id,event_type,old_status,new_status,detail,actor,created_at) VALUES(?,?,?,?,?,?,?)"
        ).use {
            it.setString(1, requestId); it.setString(2, type); it.setString(3, old?.name); it.setString(4, new?.name)
            it.setString(5, detail?.take(500)); it.setString(6, actor); it.setLong(7, now.toEpochMilli()); it.executeUpdate()
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (throwable: Throwable) {
            connection.rollback()
            throw throwable
        }
    }
}

private fun SQLException.isConstraintViolation(): Boolean = sqlState?.startsWith("23") == true || message?.contains("UNIQUE", true) == true
private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) = if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)
private fun java.sql.PreparedStatement.setNullableDecimal(index: Int, value: BigDecimal?) = if (value == null) setNull(index, java.sql.Types.NUMERIC) else setString(index, value.toPlainString())
private fun ResultSet.nullableLong(column: String): Long? = getLong(column).let { if (wasNull()) null else it }
private fun ResultSet.nullableDecimal(column: String): BigDecimal? = getString(column)?.toBigDecimalOrNull()
private fun ResultSet.toRecord(): TransactionRecord = TransactionRecord(
    requestId = getString("request_id"), playerId = UUID.fromString(getString("player_uuid")), playerName = getString("player_name"),
    telco = Telco.valueOf(getString("telco")), declaredAmount = getLong("declared_amount"), actualValue = nullableLong("actual_value"),
    providerReceived = nullableLong("provider_received"), status = TransactionStatus.valueOf(getString("status")),
    providerTransactionId = getString("provider_transaction_id"), pollCount = getInt("poll_count"),
    nextPollAt = nullableLong("next_poll_at")?.let(Instant::ofEpochMilli), rewardCommandsJson = getString("reward_commands"),
    rewardMultiplier = nullableDecimal("reward_multiplier"), rewardState = RewardState.valueOf(getString("reward_state")),
    rewardExecutedCount = getInt("reward_executed_count"), encryptedCode = getString("encrypted_code"), encryptedSerial = getString("encrypted_serial"),
    serialMasked = getString("serial_masked"), fingerprint = getString("fingerprint"), lastError = getString("last_error"),
    notificationKey = getString("notification_key"), notificationDelivered = getInt("notification_delivered") != 0,
    createdAt = Instant.ofEpochMilli(getLong("created_at")), updatedAt = Instant.ofEpochMilli(getLong("updated_at")),
    completedAt = nullableLong("completed_at")?.let(Instant::ofEpochMilli), sensitiveExpiresAt = nullableLong("sensitive_expires_at")?.let(Instant::ofEpochMilli),
)
