# Audit Output Examples

## Prompt

> Check feature `homes` xem co loi gi khong: memory leak, desync, Folia.

## Good Finding

```markdown
### [HIGH] Pending teleport task is not cancelled on quit
- Location: `src/main/kotlin/.../HomeTeleportService.kt:74`
- Category: lifecycle | leak
- Problem: `startTeleport` stores a repeating `ScheduledTask`, but `PlayerQuitEvent` only removes the player from `pendingTeleports`; it never cancels the task.
- Evidence: `pendingTasks[playerId] = scheduler.runForTimer(...)`; quit handler calls `pendingTeleports.remove(playerId)` only.
- Impact: tasks can keep firing after disconnect/reload and retain service/player-related state.
- Fix: store task handles by UUID and cancel/remove them on quit, reload, and disable.
```

## Good Summary

```markdown
## Audit summary
- Scope: `src/main/kotlin/.../homes`
- Files reviewed: 8
- Findings: 0 critical, 1 high, 2 medium, 1 low
- Clean areas: repository transaction paths and message lookup
- Test gaps / next checks: add reload + quit coverage for pending teleport cleanup
```
