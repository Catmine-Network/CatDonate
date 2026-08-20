package net.catmine.studio.catDonate.command

import dev.rollczi.litecommands.annotations.command.Command
import dev.rollczi.litecommands.annotations.context.Context
import dev.rollczi.litecommands.annotations.execute.Execute
import dev.rollczi.litecommands.annotations.execute.ExecuteDefault
import dev.rollczi.litecommands.annotations.permission.Permission
import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.message.DonateMessage
import net.catmine.studio.catDonate.message.DonateMessenger
import net.catmine.studio.catDonate.model.TransactionRecord
import net.catmine.studio.catDonate.model.TransactionStatus
import net.catmine.studio.catDonate.service.CardTopUpService
import net.catmine.studio.catDonate.ui.CardChoice
import net.catmine.studio.catDonate.ui.TopUpUi
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant

@Command(name = "napthe")
class TopUpCommand(
    private val ui: TopUpUi,
    private val service: CardTopUpService,
    private val messenger: DonateMessenger,
    private val scheduler: CatScheduler,
) {
    @Execute
    @Permission("catdonate.use")
    fun open(@Context player: Player) = ui.open(player)

    @Execute(name = "trangthai")
    @Permission("catdonate.status")
    fun latest(@Context player: Player) = showStatus(player)

    @ExecuteDefault
    fun usage(@Context player: Player) = messenger.send(player, DonateMessage.USAGE_SUBMIT)

    private fun showStatus(player: Player) {
        service.findStatus(player.uniqueId).whenComplete { record, failure -> scheduler.runFor(player) {
            when {
                failure != null -> messenger.send(player, DonateMessage.STATUS_LOAD_FAILED)
                record == null -> messenger.send(player, DonateMessage.STATUS_NOT_FOUND)
                else -> messenger.send(player, DonateMessage.STATUS, record.statusPlaceholders())
            }
        } }
    }

    private fun TransactionRecord.statusPlaceholders(): Map<String, String> = mapOf(
        "status" to messenger.plain(status.messageKey()),
        "next_check" to nextPollAt?.let {
            messenger.plain(
                DonateMessage.STATUS_NEXT_CHECK,
                mapOf("seconds" to Duration.between(Instant.now(), it).seconds.coerceAtLeast(1).toString()),
            )
        }.orEmpty(),
    )

    private fun TransactionStatus.messageKey(): DonateMessage = when (this) {
        TransactionStatus.SUBMITTING -> DonateMessage.STATUS_SUBMITTING
        TransactionStatus.PENDING -> DonateMessage.STATUS_PENDING
        TransactionStatus.SUCCESS -> DonateMessage.STATUS_SUCCESS
        TransactionStatus.FAILED -> DonateMessage.STATUS_FAILED
        TransactionStatus.NEEDS_REVIEW -> DonateMessage.STATUS_NEEDS_REVIEW
        TransactionStatus.POLL_EXHAUSTED -> DonateMessage.STATUS_POLL_EXHAUSTED
        TransactionStatus.REVIEW_EXPIRED -> DonateMessage.STATUS_REVIEW_EXPIRED
    }

}
