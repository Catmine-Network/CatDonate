---
name: scoped-code-audit
description: Performs scoped code audits for bugs, leaks, resource cleanup, persistence desync, race conditions, stale caches, and state inconsistencies. Use when the user asks to review/check/audit a file, package, feature, module, diff, or says "co loi gi khong".
---

# Scoped Code Audit

Audit the smallest complete scope that can answer the user. Read code; do not infer from filenames alone.

## 1. Define Scope

Confirm or infer:

- Paths or diff to inspect.
- Focus: correctness, leaks, Folia/threading, persistence, cache, performance.
- Out of scope: explicitly note skipped areas.

If vague, map the smallest complete feature: command/listener -> service -> repository/storage -> config/messages -> scheduler/cache lifecycle.

## 2. Map Flow

Before reporting, identify:

- Entry points: commands, listeners, public API, scheduled tasks.
- Mutable state: caches, maps, static/companion fields, task handles.
- Persistence: DB, YAML, files, external APIs.
- Async/Folia paths.
- Lifecycle: enable, disable, reload, player join/quit.

## 3. Audit Categories

Track progress while reading:

```markdown
Audit Progress:
- [ ] 1. Correctness & edge cases
- [ ] 2. Lifecycle & cleanup
- [ ] 3. Memory/listener/task leaks
- [ ] 4. Concurrency & Folia safety
- [ ] 5. Persistence/cache desync
- [ ] 6. Error handling & partial failure
- [ ] 7. Project-specific standards
```

Use [reference.md](reference.md) only when the scope is broad or a category needs deeper checklist coverage. Use [examples.md](examples.md) only for output style examples.

## 4. Quick Search Signals

| Concern | Search patterns |
| --- | --- |
| Unsafe scheduler | `Bukkit.getScheduler`, `BukkitRunnable`, `runTask`, `runTimer` |
| Task leak | `runAtFixedRate`, `runTimer`, `ScheduledTask`, missing `cancel` |
| Listener leak | `registerEvents`, missing `unregister` |
| Player leak | fields/maps of `Player`, `Entity`, `World`, `Block` |
| Manual TTL | `currentTimeMillis`, `Instant.now`, `expiresAt`, `ConcurrentHashMap<UUID` |
| Persistence risk | read-modify-write, missing transaction, ignored exceptions |
| Reload risk | `reload`, cache rebuild, old tasks/listeners not cleared |

## 5. Reporting Format

Lead with findings, ordered by severity. For every issue:

```markdown
### [SEVERITY] Short title
- Location: `path:line`
- Category: correctness | leak | desync | concurrency | ...
- Problem: what can go wrong and when
- Evidence: code path or snippet reference
- Impact: player/server/data impact
- Fix: concrete minimal suggestion
```

Severity:

- `CRITICAL`: data loss/corruption, crash, exploit, guaranteed desync.
- `HIGH`: likely normal-use bug or unbounded leak.
- `MEDIUM`: edge case, reload issue, race under load.
- `LOW`: minor inconsistency or defensive improvement.

End with:

```markdown
## Audit summary
- Scope: ...
- Files reviewed: N
- Findings: X critical, Y high, Z medium, W low
- Clean areas: ...
- Test gaps / next checks: ...
```

## Rules

- Open every relevant file in scope.
- Trace writes to matching reads/persistence/invalidation.
- Trace every register/schedule/cache to cleanup on disable/reload/quit.
- Prefer UUID/stable IDs over live Bukkit references.
- Flag unverified external dependencies instead of guessing.
- No false positives without an executable code path.

## Related Skills

- General plugin standards: `minecraft-plugin-standards`.
- Folia/threading: `folia-plugin-development`.
- Cache/cooldown/rate-limit: `caffeine-cache`.
