package net.catmine.studio.catDonate.command

import dev.rollczi.litecommands.annotations.argument.Arg
import dev.rollczi.litecommands.annotations.command.Command
import dev.rollczi.litecommands.annotations.context.Context
import dev.rollczi.litecommands.annotations.execute.Execute
import dev.rollczi.litecommands.annotations.execute.ExecuteDefault
import dev.rollczi.litecommands.annotations.permission.Permission
import net.catmine.engine.scheduler.CatScheduler
import net.catmine.studio.catDonate.CatDonate
import net.catmine.studio.catDonate.config.DonateConfig
import net.catmine.studio.catDonate.message.DonateMessage
import net.catmine.studio.catDonate.message.DonateMessenger
import net.catmine.studio.catDonate.model.AdminAction
import net.catmine.studio.catDonate.service.CardTopUpService
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Command(name = "catdonate")
class AdminCommand(
    private val plugin: CatDonate,
    private val service: CardTopUpService,
    private val messenger: DonateMessenger,
    private val scheduler: CatScheduler,
) {
    @Execute(name = "reload")
    @Permission("catdonate.admin.reload")
    fun reload(@Context sender: CommandSender) {
        scheduler.supplyAsync {
            plugin.reloadConfig()
            messenger.reload()
            DonateConfig.load(plugin.config)
        }.thenCompose(service::reload).whenComplete { accepted, failure -> reply(sender) {
            when {
                failure != null -> messenger.send(sender, DonateMessage.RELOAD_FAILED, mapOf("reason" to (failure.cause?.message ?: failure.message ?: "không rõ")))
                accepted -> messenger.send(sender, DonateMessage.RELOAD_SUCCESS)
                else -> messenger.send(sender, DonateMessage.RELOAD_REJECTED)
            }
        } }
    }

    @Execute(name = "xem")
    @Permission("catdonate.admin.review")
    fun view(@Context sender: CommandSender, @Arg requestId: String) {
        service.findAdmin(requestId).whenComplete { record, failure -> reply(sender) {
            if (failure != null || record == null) messenger.send(sender, DonateMessage.STATUS_NOT_FOUND)
            else messenger.send(sender, DonateMessage.ADMIN_VIEW, mapOf(
                "request_id" to record.requestId, "player" to record.playerName, "status" to record.status.name,
                "reward_state" to record.rewardState.name, "polls" to record.pollCount.toString(), "error" to (record.lastError ?: "-"),
            ))
        } }
    }

    @Execute(name = "lichsu")
    @Permission("catdonate.admin.review")
    fun history(@Context sender: CommandSender) {
        service.recentSuccessfulTransactions(HISTORY_LIMIT).whenComplete { records, failure -> reply(sender) {
            if (failure != null) {
                messenger.send(sender, DonateMessage.STATUS_LOAD_FAILED)
            } else if (records.isEmpty()) {
                messenger.send(sender, DonateMessage.ADMIN_HISTORY_EMPTY)
            } else {
                var message = messenger.component(DonateMessage.PREFIX)
                    .append(messenger.component(DonateMessage.ADMIN_HISTORY_HEADER, mapOf("count" to records.size.toString())))
                records.forEachIndexed { index, record ->
                    message = message.append(Component.newline()).append(messenger.component(DonateMessage.ADMIN_HISTORY_ENTRY, mapOf(
                        "number" to (index + 1).toString(),
                        "completed_at" to HISTORY_TIME_FORMAT.format(record.completedAt ?: record.updatedAt),
                        "player" to record.playerName,
                        "telco" to record.telco.displayName,
                        "amount" to formatAmount(record.actualValue ?: record.declaredAmount),
                    )))
                }
                sender.sendMessage(message)
            }
        } }
    }

    @Execute(name = "xuly")
    @Permission("catdonate.admin.review")
    fun action(@Context sender: CommandSender, @Arg requestId: String, @Arg action: AdminAction) {
        if (action == AdminAction.RETRY_REWARD) messenger.send(sender, DonateMessage.ADMIN_ACTION_WARNING)
        service.adminAction(requestId, action, sender.name).whenComplete { success, failure -> reply(sender) {
            if (failure != null || success != true) messenger.send(sender, DonateMessage.ADMIN_ACTION_INVALID)
            else messenger.send(sender, DonateMessage.ADMIN_ACTION_DONE, mapOf("request_id" to requestId, "action" to action.wireName))
        } }
    }

    @ExecuteDefault
    fun usage(@Context sender: CommandSender) = messenger.send(sender, DonateMessage.USAGE_ADMIN)

    private fun reply(sender: CommandSender, action: () -> Unit) {
        if (sender is Player) scheduler.runFor(sender) { action() } else scheduler.runGlobal { action() }
    }

    private fun formatAmount(amount: Long): String = amount.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
        .plus("đ")

    companion object {
        private const val HISTORY_LIMIT = 10
        private val HISTORY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }
}
