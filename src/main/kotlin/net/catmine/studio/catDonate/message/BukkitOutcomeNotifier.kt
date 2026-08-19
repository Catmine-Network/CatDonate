package net.catmine.studio.catDonate.message

import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.model.TransactionRecord
import net.catmine.studio.catDonate.persistence.TransactionRepository
import net.catmine.studio.catDonate.service.OutcomeNotifier
import org.bukkit.Bukkit

class BukkitOutcomeNotifier(
    private val scheduler: CatScheduler,
    private val messenger: DonateMessenger,
    private val repository: TransactionRepository,
) : OutcomeNotifier {
    override fun notify(record: TransactionRecord) {
        scheduler.runGlobal {
            val player = Bukkit.getPlayer(record.playerId) ?: return@runGlobal
            scheduler.runFor(player) {
                send(player, record)
                scheduler.runAsync { repository.markNotificationDelivered(record.requestId) }
            }
        }
    }

    fun deliverPending(playerId: java.util.UUID) {
        scheduler.supplyAsync { repository.undelivered(playerId) }.thenAccept { records ->
            scheduler.runGlobal {
                val player = Bukkit.getPlayer(playerId) ?: return@runGlobal
                scheduler.runFor(player) {
                    records.forEach { send(player, it) }
                    scheduler.runAsync { records.forEach { repository.markNotificationDelivered(it.requestId) } }
                }
            }
        }
    }

    private fun send(player: org.bukkit.entity.Player, record: TransactionRecord) {
        val key = runCatching { DonateMessage.valueOf(record.notificationKey ?: "") }.getOrElse { DonateMessage.NEEDS_REVIEW }
        messenger.send(player, key, mapOf(
            "request_id" to record.requestId,
            "amount" to (record.actualValue ?: record.declaredAmount).toString(),
            "reason" to (record.lastError ?: "Không rõ nguyên nhân"),
        ))
    }
}
