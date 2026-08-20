package net.catmine.studio.catDonate.ui

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.catmine.studio.catDonate.message.DonateMessage
import net.catmine.studio.catDonate.message.DonateMessenger
import net.catmine.studio.catDonate.security.CardSecrets
import net.catmine.studio.catDonate.service.CardTopUpService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import java.time.Duration

class JavaTopUpDialog(
    private val service: CardTopUpService,
    private val messenger: DonateMessenger,
    private val submissions: TopUpSubmissionController,
) : TopUpUi {
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
        val telcoEntries = catalog.telcos.mapIndexed { index, telco ->
            SingleOptionDialogInput.OptionEntry.create(
                telco.name.lowercase(),
                Component.text(telco.displayName),
                selected?.telco == telco || selected == null && index == 0,
            )
        }
        val amountEntries = catalog.amounts.mapIndexed { index, amount ->
            SingleOptionDialogInput.OptionEntry.create(
                amount.toString(),
                Component.text(CardChoice.formatAmount(amount)),
                selected?.amount == amount || selected == null && index == 0,
            )
        }
        val action = DialogAction.customClick(
            DialogActionCallback { response, audience ->
                val respondingPlayer = audience as? Player ?: return@DialogActionCallback
                val telco = catalog.telcos.firstOrNull { it.name.equals(response.getText(TELCO_KEY), ignoreCase = true) }
                val amount = response.getText(AMOUNT_KEY)?.toLongOrNull()?.takeIf(catalog.amounts::contains)
                if (telco == null || amount == null) {
                    messenger.send(respondingPlayer, DonateMessage.INVALID_FORM_SELECTION)
                    return@DialogActionCallback
                }
                showConfirmation(
                    respondingPlayer,
                    catalog,
                    CardChoice(telco, amount),
                    response.getText(SERIAL_KEY).orEmpty(),
                    response.getText(CODE_KEY).orEmpty(),
                )
            },
            ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(5))
                .build(),
        )
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(messenger.component(DonateMessage.FORM_TITLE))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .inputs(
                            listOf(
                                DialogInput.singleOption(
                                    TELCO_KEY,
                                    messenger.component(DonateMessage.FORM_TELCO),
                                    telcoEntries,
                                )
                                    .width(INPUT_WIDTH)
                                    .build(),
                                DialogInput.singleOption(
                                    AMOUNT_KEY,
                                    messenger.component(DonateMessage.FORM_AMOUNT),
                                    amountEntries,
                                )
                                    .width(INPUT_WIDTH)
                                    .build(),
                                DialogInput.text(SERIAL_KEY, messenger.component(DonateMessage.FORM_SERIAL))
                                    .width(INPUT_WIDTH)
                                    .initial(initialSerial)
                                    .maxLength(CARD_DATA_MAX_LENGTH)
                                    .build(),
                                DialogInput.text(CODE_KEY, messenger.component(DonateMessage.FORM_CODE))
                                    .width(INPUT_WIDTH)
                                    .initial(initialCode)
                                    .maxLength(CARD_DATA_MAX_LENGTH)
                                    .build(),
                            ),
                        )
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            messenger.component(DonateMessage.FORM_CLOSE),
                            messenger.component(DonateMessage.FORM_CLOSE_TOOLTIP),
                            BUTTON_WIDTH,
                            null,
                        ),
                        ActionButton.create(
                            messenger.component(DonateMessage.FORM_SUBMIT),
                            messenger.component(DonateMessage.FORM_SUBMIT_TOOLTIP),
                            BUTTON_WIDTH,
                            action,
                        ),
                    ),
                )
        }
        player.showDialog(dialog)
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
        val confirmAction = DialogAction.customClick(
            DialogActionCallback { _, audience ->
                val respondingPlayer = audience as? Player ?: return@DialogActionCallback
                submissions.submit(respondingPlayer, choice, serial, code)
            },
            confirmationOptions(),
        )
        val backAction = DialogAction.customClick(
            DialogActionCallback { _, audience ->
                val respondingPlayer = audience as? Player ?: return@DialogActionCallback
                openForm(respondingPlayer, catalog, choice, serial, code)
            },
            confirmationOptions(),
        )
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(messenger.component(DonateMessage.FORM_CONFIRM_TITLE))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    messenger.component(DonateMessage.FORM_CONFIRM_DETAILS, placeholders),
                                    INPUT_WIDTH,
                                ),
                            ),
                        )
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            messenger.component(DonateMessage.FORM_BACK),
                            messenger.component(DonateMessage.FORM_BACK),
                            BUTTON_WIDTH,
                            backAction,
                        ),
                        ActionButton.create(
                            messenger.component(DonateMessage.FORM_CONFIRM),
                            messenger.component(DonateMessage.FORM_CONFIRM),
                            BUTTON_WIDTH,
                            confirmAction,
                        ),
                    ),
                )
        }
        player.showDialog(dialog)
    }

    private fun confirmationOptions(): ClickCallback.Options = ClickCallback.Options.builder()
        .uses(1)
        .lifetime(Duration.ofMinutes(2))
        .build()

    companion object {
        private const val TELCO_KEY = "telco"
        private const val AMOUNT_KEY = "amount"
        private const val SERIAL_KEY = "serial"
        private const val CODE_KEY = "code"
        private const val INPUT_WIDTH = 300
        private const val BUTTON_WIDTH = 150
        private const val CARD_DATA_MAX_LENGTH = 32
    }
}
