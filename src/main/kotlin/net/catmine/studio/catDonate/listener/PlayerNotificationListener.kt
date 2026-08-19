package net.catmine.studio.catDonate.listener

import net.catmine.studio.catDonate.message.BukkitOutcomeNotifier
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerNotificationListener(private val notifier: BukkitOutcomeNotifier) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        notifier.deliverPending(event.player.uniqueId)
    }
}
