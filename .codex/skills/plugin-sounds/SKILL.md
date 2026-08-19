---
name: plugin-sounds
description: Work with configurable sound feedback in CatEngine-adopting Minecraft plugins. Use when adding, changing, reviewing, or wiring sounds.yml, sound enums/services, command/listener/GUI feedback, or Folia-safe player sound playback.
---

# Plugin Sounds

This skill is for standalone plugins that adopt CatEngine.

## Core Rules

- Prefer an existing plugin-local sound abstraction, e.g. `SoundPlayer`, `SoundService`, or `PluginSound`.
- If no abstraction exists and more than one sound is needed, create one instead of scattering `player.playSound(...)`.
- Keep user-facing sound definitions configurable in `src/main/resources/sounds.yml`.
- Use stable enum keys for stable plugin events; use string paths only for dynamic/rare paths.
- Preserve Folia safety: play for a player through `CatScheduler.runFor(player)` or `runForOrNow(player)`.
- Do not introduce BukkitScheduler.
- Do not add messages just to explain sounds.

## Minimal Shape

Use nested YAML sections. Bukkit treats dots as path separators.

```yml
sounds:
  homes:
    teleport-success:
      sound: minecraft:block.note_block.pling
      source: master
      volume: 1.0
      pitch: 1.4
```

Enum path:

```kotlin
HOMES_TELEPORT_SUCCESS("homes.teleport-success")
```

Common fields:

- `sound`: Adventure key string, or `NONE` to disable.
- `source`: Adventure sound source. Default `master`.
- `volume`: Clamp to `>= 0.0`.
- `pitch`: Clamp to `0.0..2.0`.

## Workflow

1. Inspect existing `sounds.yml`, sound enum, and sound service before editing.
2. Decide whether the sound is a stable plugin event.
3. Add enum entry and YAML config for stable events.
4. Wire playback where the event is known to have succeeded or failed.
5. Reload sound config from the plugin reload path if the plugin supports reload.
6. Run the plugin build/check command.

## Placement

- Put business-event sounds in the service that owns the state change.
- Put inventory-only click sounds in GUI click handlers.
- Keep command classes thin; avoid duplicating the same sound in command branches.
- Never play sounds for console senders.
- If a command accepts `CommandSender`, guard with `sender as? Player`.

## Folia Pattern

```kotlin
scheduler.runFor(player) {
    player.playSound(sound)
}
```

Use the plugin's existing CatEngine scheduler instance. If the project lacks one, create/wire `FoliaCatScheduler(plugin)` near plugin bootstrap and inject the `CatScheduler`.

## Review Checklist

- [ ] Sound config exists and can disable the sound.
- [ ] Stable events use enum keys.
- [ ] Playback is Folia-safe through CatEngine scheduler or equivalent entity scheduler.
- [ ] Success sounds play only after the action succeeds.
- [ ] Failure sounds play only on failure paths.
- [ ] Reload path refreshes sound config when reload is supported.
- [ ] Build/check passes.
