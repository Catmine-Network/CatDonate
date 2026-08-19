---
name: folia-plugin-development
description: Develop Folia-safe Minecraft plugins, especially plugins adopting CatEngine. Use when creating or modifying Bukkit/Paper/Folia plugins, schedulers, teleport, plugin.yml, async work, region threading, player/entity/world access, or any code that might touch Bukkit API off-thread.
---

# Folia Plugin Development

Folia has no single main thread. Game state is owned by region/entity/global tick contexts. Schedule work to the owner before touching Bukkit API.

## Prefer CatEngine Scheduler

For CatEngine-adopting plugins, inject `net.catmine.engine.scheduler.CatScheduler` and use `FoliaCatScheduler(plugin)` at bootstrap.

```kotlin
val scheduler: CatScheduler = FoliaCatScheduler(plugin)

scheduler.runFor(player) { player.sendMessage(message) }
scheduler.runAt(location) { location.block.type = Material.STONE }
scheduler.runAsync { repository.save(data) }
```

Use direct Folia APIs only when the project does not use CatEngine or needs an API not wrapped by `CatScheduler`.

## Mandatory Setup

```yaml
folia-supported: true
```

Without this in `plugin.yml`, Folia will not load the plugin.

## Scheduler Choice

- Player/entity API: `CatScheduler.runFor(player)` or `entity.scheduler.run(...)`.
- Location/block/world API at a known location: `CatScheduler.runAt(location)` or `Bukkit.getRegionScheduler()`.
- Non-region global state: `CatScheduler.runGlobal()` or global region scheduler.
- File/DB/HTTP/heavy computation: `CatScheduler.runAsync()` or async scheduler.
- Repeating tasks: store returned `ScheduledTask` and cancel on disable/reload/feature teardown.

Folia rejects non-positive delays for delayed/timer tasks. CatEngine coerces timer values to at least `1`; do the same if using raw APIs.

## Never Do This

```kotlin
Bukkit.getScheduler().runTask(...)
entity.teleport(location)
location.block.type = Material.STONE // from unknown thread
Bukkit.getOnlinePlayers().forEach { it.health = 20.0 } // cross-region
```

Rules:

- No `BukkitScheduler`.
- No synchronous `teleport`; use `teleportAsync`.
- No cross-region access inside one task.
- No Bukkit API from async callbacks unless scheduling back to the owning context first.
- Do not store or mutate live Bukkit objects from shared async state.
- Avoid Conversations API, Scoreboard API, portal events, and world load/unload unless the target platform explicitly supports them.

## Safe Patterns

Per-player iteration:

```kotlin
for (player in Bukkit.getOnlinePlayers()) {
    scheduler.runFor(player) {
        player.sendMessage(component)
    }
}
```

Async I/O then player response:

```kotlin
scheduler.supplyAsync { repository.load(playerId) }
    .thenAccept { data ->
        Bukkit.getPlayer(playerId)?.let { player ->
            scheduler.runFor(player) {
                messages.send(player, Message.LOADED)
            }
        }
    }
```

Teleport:

```kotlin
player.teleportAsync(target).thenAccept { success ->
    if (!success) return@thenAccept
    scheduler.runFor(player) { /* post-teleport player API */ }
}
```

Location effect/block change:

```kotlin
scheduler.runAt(location) {
    location.world.spawnParticle(Particle.HAPPY_VILLAGER, location, 8)
}
```

## Event Guidance

- Event callbacks are only safe for the event-owned context. After any delay or async gap, reschedule.
- `InventoryClickEvent`: cancel/read event data immediately, then schedule player inventory changes with `runFor`.
- `PlayerMoveEvent`: return early, e.g. `if (!event.hasChangedBlock()) return`.
- `AsyncChatEvent`: already async; schedule before touching Bukkit state beyond safe event data.

## Shared State

- Shared plugin data must be thread-safe: immutable snapshots, DB/repository boundaries, `ConcurrentHashMap`, Caffeine, or explicit locks.
- Thread-safe collections do not make Bukkit objects safe. Store UUIDs/IDs/data, then reschedule before Bukkit access.
- For caches/cooldowns/rate limits, load `caffeine-cache`.

## Review Checklist

- [ ] `plugin.yml` has `folia-supported: true`.
- [ ] No `Bukkit.getScheduler()` or `BukkitRunnable`.
- [ ] Bukkit API access is scheduled to player/entity/location/global owner.
- [ ] Async work contains no unsafe Bukkit API.
- [ ] Teleports use `teleportAsync`.
- [ ] Online-player/chunk/world iteration schedules per owner.
- [ ] Repeating tasks are cancelled on disable/reload.
- [ ] Shared state does not store live Bukkit objects.
