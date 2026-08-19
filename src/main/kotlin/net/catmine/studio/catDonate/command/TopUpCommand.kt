package net.catmine.studio.catDonate.command

import dev.rollczi.litecommands.annotations.argument.Arg
import dev.rollczi.litecommands.annotations.command.Command
import dev.rollczi.litecommands.annotations.context.Context
import dev.rollczi.litecommands.annotations.execute.Execute
import dev.rollczi.litecommands.annotations.execute.ExecuteDefault
import dev.rollczi.litecommands.annotations.permission.Permission
import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.message.DonateMessage
import net.catmine.studio.catDonate.message.DonateMessenger
import net.catmine.studio.catDonate.model.CardSubmission
import net.catmine.studio.catDonate.model.SubmissionResult
import net.catmine.studio.catDonate.model.Telco
import net.catmine.studio.catDonate.model.TransactionRecord
import net.catmine.studio.catDonate.service.CardTopUpService
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Command(name = "napthe")
class TopUpCommand(
    private val service: CardTopUpService,
    private val messenger: DonateMessenger,
    private val scheduler: CatScheduler,
) {
    @Execute
    @Permission("catdonate.use")
    fun submit(
        @Context player: Player,
        @Arg telco: Telco,
        @Arg amount: Long,
        @Arg serial: String,
        @Arg code: String,
    ) {
        service.submit(CardSubmission(player.uniqueId, player.name, telco, amount, serial, code))
            .whenComplete { result, failure -> scheduler.runFor(player) {
                if (failure != null || result == null) {
                    messenger.send(player, DonateMessage.INTERNAL_ERROR, mapOf("request_id" to "-"))
                } else sendSubmissionResult(player, result, telco, amount)
            } }
    }

    @Execute(name = "trangthai")
    @Permission("catdonate.status")
    fun latest(@Context player: Player) = showStatus(player, null)

    @Execute(name = "trangthai")
    @Permission("catdonate.status")
    fun byId(@Context player: Player, @Arg requestId: String) = showStatus(player, requestId)

    @ExecuteDefault
    fun usage(@Context player: Player) = messenger.send(player, DonateMessage.USAGE_SUBMIT)

    private fun showStatus(player: Player, requestId: String?) {
        service.findStatus(player.uniqueId, requestId).whenComplete { record, failure -> scheduler.runFor(player) {
            if (failure != null || record == null) messenger.send(player, DonateMessage.STATUS_NOT_FOUND)
            else messenger.send(player, DonateMessage.STATUS, record.statusPlaceholders())
        } }
    }

    private fun sendSubmissionResult(player: Player, result: SubmissionResult, telco: Telco, amount: Long) {
        when (result) {
            is SubmissionResult.Accepted -> messenger.send(
                player,
                DonateMessage.SUBMITTED_WAIT,
                mapOf("request_id" to result.requestId, "seconds" to result.nextCheckSeconds.toString()),
            )
            is SubmissionResult.Duplicate -> messenger.send(player, DonateMessage.DUPLICATE_CARD)
            is SubmissionResult.Cooldown -> messenger.send(player, DonateMessage.COOLDOWN, mapOf("seconds" to result.remainingSeconds.toString()))
            is SubmissionResult.TooManyPending -> messenger.send(player, DonateMessage.TOO_MANY_PENDING, mapOf("limit" to result.limit.toString()))
            SubmissionResult.NotConfigured -> messenger.send(player, DonateMessage.NOT_CONFIGURED)
            is SubmissionResult.Invalid -> messenger.send(
                player,
                if (result.reason == "INVALID_AMOUNT") DonateMessage.INVALID_AMOUNT else DonateMessage.INVALID_CARD_DATA,
                mapOf("telco" to telco.name.lowercase(), "amount" to amount.toString()),
            )
            is SubmissionResult.Error -> messenger.send(player, DonateMessage.INTERNAL_ERROR, mapOf("request_id" to (result.requestId ?: "-")))
        }
    }

    companion object {
        private val STATUS_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

        fun TransactionRecord.statusPlaceholders(): Map<String, String> = mapOf(
            "request_id" to requestId,
            "status" to status.name,
            "telco" to telco.name,
            "amount" to (actualValue ?: declaredAmount).toString(),
            "created_at" to STATUS_TIME.format(createdAt),
        )
    }
}
