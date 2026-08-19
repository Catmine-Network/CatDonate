---
name: plugin-effects
description: Work with configurable particle effects in CatEngine-adopting Minecraft plugins. Use when adding, changing, reviewing, or wiring effects.yml, effect enums/services, command/listener/GUI feedback, teleport visuals, or Folia-safe particle playback.
---

# Plugin Effects

This skill is for standalone plugins that adopt CatEngine.

## Core Rules

- Prefer an existing plugin-local effect abstraction, e.g. `EffectPlayer`, `EffectService`, or `PluginEffect`.
- If no abstraction exists and more than one effect is needed, create one instead of scattering `spawnParticle(...)`.
- Keep configurable effects in `src/main/resources/effects.yml`.
- Use stable enum keys for stable plugin events; use string paths only for dynamic/rare paths.
- Preserve Folia safety: player effects go through `CatScheduler.runFor(player)` or `runForOrNow(player)`.
- Location/block effects must run through `CatScheduler.runAt(location)`.
- Do not introduce BukkitScheduler.
- Do not add messages just to explain particles.

## Minimal Shape

Use nested YAML sections. Bukkit treats dots as path separators.

```yml
effects:
  homes:
    teleport-success:
      enabled: true
      particle: reverse_portal
      location: feet
      count: 24
      offset-x: 0.45
      offset-y: 0.8
      offset-z: 0.45
      extra: 0.0
```

Enum path:

```kotlin
HOMES_TELEPORT_SUCCESS("homes.teleport-success")
```

Common fields:

- `enabled`: `false` disables the effect.
- `particle`: Bukkit `Particle` enum name, normalized from lowercase/hyphen if the local service supports it.
- `location`: `feet` or `eyes` for player effects.
- `count`, `offset-x`, `offset-y`, `offset-z`, `extra`: clamp invalid negative values.

## Workflow

1. Inspect existing `effects.yml`, effect enum, and effect service before editing.
2. Decide whether the effect is a stable plugin event.
3. Add enum entry and YAML config for stable events.
4. Wire playback where the visible event is known to have succeeded or failed.
5. If shape/duration/data is unsupported, extend the effect service schema deliberately.
6. Reload effect config from the plugin reload path if reload is supported.
7. Run the plugin build/check command.

## When To Extend

Do not fake shapes with random offsets. If the request needs animation, add explicit schema:

```yml
shape: ring
radius: 1.2
points: 32
duration-ticks: 60
period-ticks: 5
y-offset: 0.05
```

For timed player-following effects, keep player access inside `CatScheduler.runFor(player)` callbacks or equivalent entity scheduler callbacks. For world/block/location particles, use `CatScheduler.runAt(location)`.

## Placement

- Put business-event effects in the service that owns the state change.
- Put inventory-only click effects in GUI click handlers.
- Keep command classes thin; avoid duplicating the same effect in command branches.
- Never play effects for console senders.
- If a command accepts `CommandSender`, guard with `sender as? Player`.

## Review Checklist

- [ ] Effect config exists and can disable the effect.
- [ ] Stable events use enum keys.
- [ ] Playback is Folia-safe through CatEngine scheduler or equivalent region/entity scheduler.
- [ ] Success effects play only after the action succeeds.
- [ ] Failure effects play only on failure paths.
- [ ] Requested shape/duration/particle data is actually supported.
- [ ] Reload path refreshes effect config when reload is supported.
- [ ] Build/check passes.
