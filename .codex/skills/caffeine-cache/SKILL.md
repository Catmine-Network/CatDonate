---
name: caffeine-cache
description: Enforces CatEngine/Caffeine cache services for Minecraft plugins. Use when adding or reviewing cache logic, cooldowns, rate limits, pending confirmations, TTL player state, DB-backed hot reads, or manual Map/ConcurrentHashMap timestamp cleanup.
---

# Caffeine Cache

Use CatEngine cache helpers first. Use raw Caffeine only when the helper does not fit.

```kotlin
import net.catmine.engine.cache.CooldownService
import net.catmine.engine.cache.RateLimitService
```

## Required Rules

- Use `CooldownService<K>` for fixed-window cooldowns.
- Use `RateLimitService<K>` for fixed-window action gates.
- Use raw Caffeine for DB-backed hot reads, computed results, pending confirmations, or TTL snapshots.
- Do not hand-roll `Map<UUID, Long>`, `ConcurrentHashMap<UUID, Instant>`, cleanup timers, or manual expiry sweeps.
- Every raw cache needs an eviction policy: `expireAfterWrite`, `expireAfterAccess`, `maximumSize`, or a clear combination.
- Key player data by `UUID` or stable composite keys, never by `Player`.
- Do not store live Bukkit objects (`Player`, `Entity`, `World`, `Block`) in caches. Store IDs, immutable data, snapshots, or cloned `ItemStack`s.
- Cache is disposable. Persist important data separately and invalidate cache entries after writes.

## CatEngine Patterns

Cooldown:

```kotlin
private val cooldowns = CooldownService<UUID>(Duration.ofSeconds(30))

fun use(playerId: UUID): Boolean {
    val result = cooldowns.tryUse(playerId)
    if (!result.allowed) return false
    return true
}
```

Rate limit:

```kotlin
private val limits = RateLimitService<UUID>(Duration.ofSeconds(2))

if (limits.isLimited(player.uniqueId)) return
limits.mark(player.uniqueId)
```

Raw Caffeine for cached data:

```kotlin
private val homes = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(10))
    .maximumSize(10_000)
    .build<UUID, List<Home>>()
```

Pending confirmation:

```kotlin
private val pendingDelete = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofSeconds(30))
    .maximumSize(5_000)
    .build<UUID, PendingDelete>()
```

## Use Caffeine For

- Player data loaded from DB/YAML: homes, economy, profiles, settings.
- Cooldowns and action rate limits.
- Pending confirmations and short GUI flows.
- Expensive computed data: leaderboards, permissions, formatted GUI items.
- Player heads, skin/profile lookups, temporary back-location snapshots.

## Do Not Use Cache For

- Live per-tick state such as current location, health, combat state, inventory contents.
- Active operations holding cancellable `ScheduledTask`s.
- Single-flight locks where TTL is not the main concern.

## Invalidation

Invalidate or rebuild when:

- Source data is written.
- Config reload changes TTL, limits, or derived data.
- Player quits and the entry is player-scoped temporary state.
- Plugin disables. Flush dirty persisted data first, then `invalidateAll()` or clear CatEngine services.

## Review Checklist

- [ ] CatEngine `CooldownService`/`RateLimitService` used where they fit.
- [ ] Raw Caffeine used for other TTL/hot-read caches.
- [ ] No timestamp maps, manual expiry sweeps, or unbounded caches.
- [ ] Keys are UUID/stable IDs; values do not hold live Bukkit objects.
- [ ] Writes, reloads, quits, and disable paths invalidate or flush correctly.
- [ ] Build/tests pass for the plugin being edited.

## Related Skills

- Folia scheduling: `folia-plugin-development`
- Leak/desync review: `scoped-code-audit`
