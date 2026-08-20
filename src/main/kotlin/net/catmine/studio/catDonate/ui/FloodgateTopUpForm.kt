package net.catmine.studio.catDonate.ui

import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.message.DonateMessage
import net.catmine.studio.catDonate.message.DonateMessenger
import net.catmine.studio.catDonate.security.CardSecrets
import net.catmine.studio.catDonate.service.CardTopUpService
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.floodgate.api.FloodgateApi

class FloodgateTopUpForm(
    private val api: FloodgateApi,
    private val service: CardTopUpService,
    private val messenger: DonateMessenger,
    private val submissions: TopUpSubmissionController,
    private val scheduler: CatScheduler,
) : BedrockTopUpUi {
    override fun isBedrockPlayer(player: Player): Boolean = api.isFloodgatePlayer(player.uniqueId)

    override fun open(player: Player) {
        val catalog = CardCatalog.from(service)
        if (catalog.telcos.isEmpty() || catalog.amounts.isEmpty()) {
            messenger.send(player, DonateMessage.NO_CARD_OPTIONS)
            return
        }
        openForm(player, catalog)
    }

    private fun openForm(
        player: Player,
        catalog: CardCatalog,
        selected: CardChoice? = null,
        initialSerial: String = "",
        initialCode: String = "",
    ) {
        val form = CustomForm.builder()
            .title(messenger.plain(DonateMessage.FORM_TITLE))
            .dropdown(
                messenger.plain(DonateMessage.FORM_TELCO),
                catalog.telcos.map { it.displayName },
                selected?.let { catalog.telcos.indexOf(it.telco).coerceAtLeast(0) } ?: 0,
            )
            .dropdown(
                messenger.plain(DonateMessage.FORM_AMOUNT),
                catalog.amounts.map(CardChoice::formatAmount),
                selected?.let { catalog.amounts.indexOf(it.amount).coerceAtLeast(0) } ?: 0,
            )
            .input(
                messenger.plain(DonateMessage.FORM_SERIAL),
                messenger.plain(DonateMessage.FORM_SERIAL_PLACEHOLDER),
                initialSerial,
            )
            .input(
                messenger.plain(DonateMessage.FORM_CODE),
                messenger.plain(DonateMessage.FORM_CODE_PLACEHOLDER),
                initialCode,
            )
            .validResultHandler { response ->
                val telco = catalog.telcos.getOrNull(response.asDropdown(0))
                val amount = catalog.amounts.getOrNull(response.asDropdown(1))
                val serial = response.asInput(2).orEmpty()
                val code = response.asInput(3).orEmpty()
                scheduler.runFor(player) {
                    if (telco == null || amount == null) {
                        messenger.send(player, DonateMessage.INVALID_FORM_SELECTION)
                    } else {
                        showConfirmation(player, catalog, CardChoice(telco, amount), serial, code)
                    }
                }
            }
            .build()

        if (!api.sendForm(player.uniqueId, form)) {
            messenger.send(player, DonateMessage.FORM_UNAVAILABLE)
        }
    }

    private fun showConfirmation(
        player: Player,
        catalog: CardCatalog,
        choice: CardChoice,
        serial: String,
        code: String,
    ) {
        val placeholders = mapOf(
            "telco" to choice.telco.displayName,
            "amount" to CardChoice.formatAmount(choice.amount),
            "serial" to CardSecrets.maskSerial(serial.trim()),
            "code" to code.trim(),
        )
        val form = ModalForm.builder()
            .title(messenger.plain(DonateMessage.FORM_CONFIRM_TITLE))
            .content(messenger.plain(DonateMessage.FORM_CONFIRM_DETAILS, placeholders))
            .button1(messenger.plain(DonateMessage.FORM_BACK))
            .button2(messenger.plain(DonateMessage.FORM_CONFIRM))
            .validResultHandler { response ->
                scheduler.runFor(player) {
                    if (response.clickedFirst()) {
                        openForm(player, catalog, choice, serial, code)
                    } else {
                        submissions.submit(player, choice, serial, code)
                    }
                }
            }
            .build()
        if (!api.sendForm(player.uniqueId, form)) {
            messenger.send(player, DonateMessage.FORM_UNAVAILABLE)
        }
    }
}

object FloodgateUiFactory {
    fun create(
        service: CardTopUpService,
        messenger: DonateMessenger,
        submissions: TopUpSubmissionController,
        scheduler: CatScheduler,
    ): BedrockTopUpUi = FloodgateTopUpForm(
        FloodgateApi.getInstance(),
        service,
        messenger,
        submissions,
        scheduler,
    )
}
