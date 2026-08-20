package net.catmine.studio.catDonate.ui

import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.message.DonateMessage
import net.catmine.studio.catDonate.message.DonateMessenger
import net.catmine.studio.catDonate.model.CardSubmission
import net.catmine.studio.catDonate.model.SubmissionResult
import net.catmine.studio.catDonate.service.CardTopUpService
import org.bukkit.entity.Player

class TopUpSubmissionController(
    private val service: CardTopUpService,
    private val messenger: DonateMessenger,
    private val scheduler: CatScheduler,
) {
    fun submit(player: Player, choice: CardChoice, serial: String, code: String) {
        messenger.send(player, DonateMessage.FORM_SUBMITTING)
        val submission = CardSubmission(
            playerId = player.uniqueId,
            playerName = player.name,
            telco = choice.telco,
            amount = choice.amount,
            serial = serial.trim(),
            code = code.trim(),
        )
        service.submit(submission).whenComplete { result, failure ->
            scheduler.runFor(player) {
                if (failure != null || result == null) {
                    messenger.send(player, DonateMessage.INTERNAL_ERROR)
                } else {
                    sendResult(player, result, choice)
                }
            }
        }
    }

    private fun sendResult(player: Player, result: SubmissionResult, choice: CardChoice) {
        when (result) {
            is SubmissionResult.Accepted -> {
                messenger.send(
                    player,
                    DonateMessage.SUBMITTED_WAIT,
                    mapOf(
                        "seconds" to result.nextCheckSeconds.toString(),
                    ),
                )
                service.startProcessing(result.requestId)
            }
            is SubmissionResult.Duplicate -> {
                messenger.send(player, DonateMessage.DUPLICATE_CARD)
            }
            is SubmissionResult.Cooldown -> messenger.send(
                player,
                DonateMessage.COOLDOWN,
                mapOf("seconds" to result.remainingSeconds.toString()),
            )
            is SubmissionResult.TooManyPending -> messenger.send(
                player,
                DonateMessage.TOO_MANY_PENDING,
                mapOf("limit" to result.limit.toString()),
            )
            SubmissionResult.NotConfigured -> messenger.send(player, DonateMessage.NOT_CONFIGURED)
            is SubmissionResult.Invalid -> messenger.send(
                player,
                if (result.reason == "INVALID_AMOUNT") DonateMessage.INVALID_AMOUNT else DonateMessage.INVALID_CARD_DATA,
                mapOf("telco" to choice.telco.name.lowercase(), "amount" to choice.amount.toString()),
            )
            is SubmissionResult.Error -> messenger.send(
                player,
                DonateMessage.INTERNAL_ERROR,
            )
        }
    }
}
