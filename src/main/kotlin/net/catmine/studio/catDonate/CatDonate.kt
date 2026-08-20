package net.catmine.studio.catDonate

import dev.rollczi.litecommands.LiteCommands
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory
import dev.rollczi.litecommands.folia.FoliaExtension
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.catmine.engine.message.MessageService
import net.catmine.engine.scheduler.CatScheduler
import net.catmine.engine.scheduler.FoliaCatScheduler
import net.catmine.studio.catDonate.command.AdminActionArgument
import net.catmine.studio.catDonate.command.AdminCommand
import net.catmine.studio.catDonate.command.TopUpCommand
import net.catmine.studio.catDonate.config.DonateConfig
import net.catmine.studio.catDonate.listener.PlayerNotificationListener
import net.catmine.studio.catDonate.message.BukkitOutcomeNotifier
import net.catmine.studio.catDonate.message.DonateMessage
import net.catmine.studio.catDonate.message.DonateMessenger
import net.catmine.studio.catDonate.model.AdminAction
import net.catmine.studio.catDonate.persistence.DonateDatabase
import net.catmine.studio.catDonate.security.CardSecrets
import net.catmine.studio.catDonate.service.BukkitRewardExecutor
import net.catmine.studio.catDonate.service.DefaultCardTopUpService
import net.catmine.studio.catDonate.ui.FloodgateUiFactory
import net.catmine.studio.catDonate.ui.JavaTopUpDialog
import net.catmine.studio.catDonate.ui.TopUpSubmissionController
import net.catmine.studio.catDonate.ui.TopUpUiRouter
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.util.concurrent.TimeUnit

class CatDonate : JavaPlugin() {
    lateinit var scheduler: CatScheduler
        private set

    private lateinit var database: DonateDatabase
    private lateinit var liteCommands: LiteCommands<CommandSender>
    private var dispatcherTask: ScheduledTask? = null

    override fun onEnable() {
        try {
            saveDefaultConfig()
            scheduler = FoliaCatScheduler(this)
            val donateConfig = DonateConfig.load(config)
            val messageService = MessageService(this, DonateMessage.entries.toTypedArray(), "messages.yml")
            messageService.reload()
            val messenger = DonateMessenger(messageService)

            database = DonateDatabase(this).also { it.connect() }
            val repository = database.repository
            val recovered = repository.recoverInterruptedRewards(Instant.now())
            if (recovered > 0) logger.warning("Đã chuyển $recovered reward bị gián đoạn sang NEEDS_REVIEW.")

            val notifier = BukkitOutcomeNotifier(scheduler, messenger, repository)
            val service = DefaultCardTopUpService(
                donateConfig,
                repository,
                CardSecrets.loadOrCreate(dataFolder.toPath()),
                scheduler,
                BukkitRewardExecutor(this, scheduler),
                notifier,
            )
            val submissions = TopUpSubmissionController(service, messenger, scheduler)
            val javaUi = JavaTopUpDialog(service, messenger, submissions)
            val bedrockUi = if (server.pluginManager.isPluginEnabled("floodgate")) {
                runCatching { FloodgateUiFactory.create(service, messenger, submissions, scheduler) }
                    .onFailure { logger.warning("Không thể khởi tạo Floodgate form: ${it.message}") }
                    .getOrNull()
            } else null
            val topUpUi = TopUpUiRouter(javaUi, bedrockUi)

            server.pluginManager.registerEvents(PlayerNotificationListener(notifier), this)
            liteCommands = LiteBukkitFactory.builder("catdonate", this)
                .extension(FoliaExtension(this))
                .argument(AdminAction::class.java, AdminActionArgument())
                .commands(
                    TopUpCommand(topUpUi, service, messenger, scheduler),
                    AdminCommand(this, service, messenger, scheduler),
                )
                .build()

            dispatcherTask = scheduler.runAsyncTimer(1, 1, TimeUnit.SECONDS) { service.dispatchDue() }
            logger.info("CatDonate đã sẵn sàng với Card2K và SQLite.")
        } catch (throwable: Throwable) {
            logger.severe("Không thể khởi động CatDonate: ${throwable.message}")
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        dispatcherTask?.cancel()
        if (::liteCommands.isInitialized) liteCommands.unregister()
        if (::database.isInitialized) database.close()
    }
}
