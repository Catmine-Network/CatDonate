---
name: minecraft-plugin-standards
description: Coding standards for Minecraft plugins, especially Paper/Folia plugins adopting CatEngine. Use when creating, reviewing, or debugging plugin code involving Bukkit/Paper/Folia, plugin.yml, JavaPlugin, listeners, commands, configs, messages, persistence, Vault, PlaceholderAPI, or CatEngine utilities. For Folia threading, also use folia-plugin-development.
---

# Minecraft Plugin Standards

Use these standards for the target plugin, not for CatEngine internals unless the user is editing CatEngine itself.

## CatEngine First

For plugins that adopt CatEngine, prefer existing library utilities over recreating plugin-local boilerplate:

- Scheduling: `net.catmine.engine.scheduler.CatScheduler`, `FoliaCatScheduler`.
- Cooldowns/rate limits: `CooldownService`, `RateLimitService`.
- Messages: `MessageService<K : MessageKey>`, `ComponentParser`.
- Numeric input: `NumberInput`.
- Duration/time input: `DurationInput`.

If the target plugin already has wrappers around these utilities, follow the local wrapper.

## Structure

- Main plugin class owns bootstrap only: config load, service construction, listener/command registration, shutdown hooks.
- Commands parse input and delegate. Use LiteCommands when that skill applies.
- Listeners observe events and delegate. Keep business logic in services.
- Repositories/storage own persistence boundaries.
- Models should be plain data and avoid Bukkit dependencies unless they are short-lived snapshots.
- Prefer constructor injection over static singletons.

## plugin.yml

Minimum:

```yaml
name: MyPlugin
version: '${version}'
main: com.example.myplugin.MyPlugin
api-version: '1.21'
folia-supported: true
softdepend: [Vault, PlaceholderAPI]
```

Rules:

- Always set `api-version`.
- Set `folia-supported: true` only when the plugin is actually Folia-safe.
- Use `softdepend` for optional integrations and isolate integration code.
- If using LiteCommands, avoid duplicate command declarations unless the project pattern requires them.

## Text And Messages

- Use Adventure `Component` and MiniMessage-compatible text.
- In CatEngine-adopting plugins, prefer `MessageService` and `ComponentParser`.
- Do not use `ChatColor` or raw `&` formatting in new user-facing code.
- Escape untrusted player text with `ComponentParser.escapeTags(...)` before inserting into MiniMessage.
- Keep user-facing strings in `messages.yml` or the plugin's established message source.

Example:

```kotlin
enum class HomeMessage(
    override val path: String,
    override val defaultText: String,
) : MessageKey {
    TELEPORTED("homes.teleported", "<green>Teleported home.")
}

messages.send(player, HomeMessage.TELEPORTED)
```

## Input Parsing

- Use `NumberInput` for compact numbers such as `1.5k`, exact longs, and formatted display.
- Use `DurationInput` for config/command durations such as `30s`, `5m`, `2h`, `7d`.
- Validate bounds at command/service boundary and return clear messages.

## Events

- Add `ignoreCancelled = true` by default unless the handler intentionally observes cancelled events.
- Use `MONITOR` only for read-only observation.
- Guard high-frequency events with early returns, e.g. `PlayerMoveEvent.hasChangedBlock()`.
- Never register the same listener repeatedly without unregistering.
- Custom events that can be cancelled must implement `Cancellable`; callers must respect cancellation.

## Config And Reload

- `saveDefaultConfig()` on startup is fine; do not overwrite user config.
- Cache parsed config values in a config/service object. Avoid repeated `getConfig().get*()` in hot paths.
- Reload should refresh config, messages, sounds/effects if present, and cache TTLs or derived data.
- Reload must not leak old tasks/listeners/caches.

## Persistence

- Key player data by `UUID`, never `Player`.
- Run file/DB I/O async, except controlled startup/shutdown/reload file reads.
- On shutdown, synchronously flush critical dirty data if needed.
- Use transactions or single-writer discipline for multi-step persistence changes.
- Optional DB pools such as HikariCP must be closed on disable.

## Cache

For cache, cooldown, rate-limit, pending confirmation, or TTL player state, load `caffeine-cache`.

Quick rule: use CatEngine `CooldownService`/`RateLimitService` where possible; use raw Caffeine for other bounded TTL/hot-read caches. Never use timestamp maps or caches keyed by `Player`.

## Scheduling

- For Folia or mixed Paper/Folia plugins, load `folia-plugin-development`.
- Do not block the server tick context with DB, file, HTTP, or heavy computation.
- After async work, reschedule before touching Bukkit API.
- Store repeating task handles and cancel them on disable/reload.

## Integrations

- Optional integrations must be behind presence checks.
- Isolate integration classes so missing plugins do not cause `NoClassDefFoundError`.
- PlaceholderAPI expansions should `persist() = true` and register only when PAPI is present.
- Vault economy/chat/permission hooks should fail gracefully when unavailable.

## Performance

- Avoid repeated item/meta construction in hot GUI paths; prebuild and clone where safe.
- Avoid scanning all worlds/entities on timers; track IDs or react to events.
- Use async chunk APIs and snapshots for read-only chunk work.
- Profile with Spark or server timings before broad optimization.

## Review Checklist

- [ ] Main class is bootstrap-only.
- [ ] Commands/listeners are thin and delegate to services.
- [ ] CatEngine utilities are used instead of copied boilerplate.
- [ ] Messages and numeric/duration parsing use CatEngine helpers.
- [ ] Cache/cooldown logic follows `caffeine-cache`.
- [ ] Folia-sensitive code follows `folia-plugin-development`.
- [ ] Persistence has load/save/flush/error paths.
- [ ] Optional integrations are isolated and guarded.
- [ ] Build/check command passes.
