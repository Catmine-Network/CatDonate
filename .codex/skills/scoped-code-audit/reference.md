# Audit Reference

Load this only for broad audits or when a category needs deeper coverage.

## Correctness

- Boundary inputs: empty, null, negative, zero, max values, offline player.
- Permission and ownership checks before state mutation.
- Success/failure paths send the right message/sound/effect once.
- Idempotency: repeated command, double click, reconnect, retry.
- Partial failure: one write succeeds and the next fails.

## Lifecycle And Cleanup

- `onDisable` or feature `disable()` cancels tasks, closes DB pools, flushes dirty data, clears caches.
- Reload does not double-register listeners/commands or leave old scheduled tasks running.
- Player quit invalidates temporary player state and cancels pending player operations.
- GUI/session state is removed on inventory close, quit, disable, and reload.

## Memory Leaks

- No long-lived fields/maps contain `Player`, `Entity`, `World`, `Block`, `Inventory`, or plugin classloader-heavy objects unless explicitly short-lived and cleaned.
- UUID-keyed maps/caches are bounded or cleared.
- Caffeine caches have maximum size and/or expiration.
- Listeners and repeating tasks are not registered repeatedly.

## Concurrency And Folia

- Bukkit API runs on owning player/entity/location/global context.
- Async code is limited to I/O, CPU work, immutable data, or thread-safe plugin state.
- `CompletableFuture.thenAccept` and callbacks reschedule before Bukkit API.
- No `Bukkit.getScheduler()` in Folia-compatible code.
- Teleport uses `teleportAsync`.
- Online players, worlds, chunks, and entities are not bulk-mutated from one arbitrary thread.

## Persistence And Desync

- Every in-memory mutation has a persistence path or is intentionally temporary.
- DB read-modify-write sequences are transaction-safe or single-writer.
- Cache invalidates after writes, config reload, permission/relation changes, and quit when relevant.
- YAML/file writes handle exceptions and avoid corrupt partial writes where possible.
- Shutdown flush order: stop new work, cancel tasks, flush dirty state, close resources.

## Error Handling

- External failures do not leave player-visible state half-mutated.
- Exceptions inside scheduled tasks/futures are logged with enough context.
- User receives a clear failure response for expected failures.
- Retry/backoff exists for operations that should tolerate transient failure.

## CatEngine Adoption Checks

- Scheduler work uses `CatScheduler`/`FoliaCatScheduler` where available.
- Cooldowns and rate limits use `CooldownService`/`RateLimitService`.
- Other TTL/hot-read state uses Caffeine, not manual timestamp maps.
- User-facing text uses `MessageService`/`ComponentParser` or the plugin's wrapper.
- Numeric and duration input uses `NumberInput`/`DurationInput` where applicable.
