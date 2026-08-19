---
name: lite-commands
description: Create and migrate Minecraft plugin commands using LiteCommands (dev.rollczi) with Kotlin/Java on Paper/Folia and CatEngine. Use when adding commands, refactoring CommandExecutor/TabCompleter, using @Command/@Execute/@Arg/@Permission/@Async, LiteBukkitFactory, FoliaExtension, custom argument resolvers, or LiteCommands APIs.
---

# LiteCommands

Use this as compact LiteCommands docs for standalone plugins that adopt CatEngine.

## Critical Rules

1. Do not declare LiteCommands-managed commands in `plugin.yml`; keep plugin metadata and `permissions:` only.
2. Always call `liteCommands.unregister()` on disable.
3. On Folia, register `.extension(FoliaExtension(plugin))`.
4. Enable Java parameter metadata (`-parameters`; Kotlin: `javaParameters.set(true)`) so argument names render correctly.
5. Command classes are plain classes. Inject services, `MessageService`, and `CatScheduler` through constructors.
6. Keep command methods thin: parse, validate, delegate to services, then send response.

## Dependency And Bootstrap

Use the project dependency aliases if present; otherwise add `litecommands-bukkit` and `litecommands-folia` matching the project version.

```kotlin
import dev.rollczi.litecommands.LiteCommands
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory
import dev.rollczi.litecommands.folia.FoliaExtension
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class MyPlugin : JavaPlugin() {
    private lateinit var liteCommands: LiteCommands<CommandSender>

    override fun onEnable() {
        val scheduler = FoliaCatScheduler(this)

        liteCommands = LiteBukkitFactory.builder("myplugin", this)
            .extension(FoliaExtension(this))
            .commands(
                HomeCommand(homeService, messages, scheduler),
                HomeAdminCommand(homeService, messages),
            )
            .build()
    }

    override fun onDisable() {
        if (::liteCommands.isInitialized) liteCommands.unregister()
    }
}
```

The builder namespace is the fallback prefix for `namespace:command`; use a lowercase plugin id.

## Command Classes

Basic command:

```kotlin
@Command(name = "home")
class HomeCommand(
    private val service: HomeService,
    private val messages: MessageService<HomeMessage>,
) {
    @Execute
    fun home(@Context player: Player) {
        service.teleportHome(player)
    }
}
```

Subcommands:

```kotlin
@Command(name = "home")
class HomeCommand(private val service: HomeService) {
    @Execute(name = "set")
    @Permission("myplugin.home.set")
    fun set(@Context player: Player, @Arg name: String) {
        service.setHome(player.uniqueId, name, player.location)
    }

    @Execute(name = "delete")
    @Permission("myplugin.home.delete")
    fun delete(@Context player: Player, @Arg name: String) {
        service.deleteHome(player.uniqueId, name)
    }
}
```

Multiple root commands in one class:

```kotlin
@RootCommand
class HomeRootCommands(private val service: HomeService) {
    @Execute(name = "home")
    @Shortcut("h")
    fun home(@Context player: Player) {}

    @Execute(name = "sethome")
    fun setHome(@Context player: Player, @Arg name: String) {}
}
```

Fallback usage:

```kotlin
@ExecuteDefault
fun usage(@Context sender: CommandSender) {
    sender.sendMessage(Component.text("Usage: /home <set|delete|list>"))
}
```

## Annotation Reference

| Annotation | Purpose |
| --- | --- |
| `@Command(name = "...")` | Root command class. |
| `@RootCommand` | Multiple roots/subcommands in one class. |
| `@Execute` / `@Execute(name = "sub")` | Command executor method. Nested names like `"set day"` are valid. |
| `@Context T` / `@Sender T` | Sender/context object, e.g. `Player`, `CommandSender`. |
| `@Arg T` | Parsed command argument. |
| `@Permission("node")` | Permission on class or method; multiple permissions are AND. |
| `@Shortcut("alias")` | Alias replacement for plugin.yml aliases. |
| `@Cooldown(key, count, unit, bypass)` | Built-in command cooldown. Prefer CatEngine cooldowns for domain cooldowns. |
| `@Async` | Runs the whole method asynchronously. Reschedule before Bukkit API. |
| `@Priority(PriorityValue.HIGH)` | Select overload when several executors match. |
| `@ExecuteDefault` | Catch-all when no executor matches. |

## Arguments And Suggestions

Common built-ins include `String`, numbers, `BigDecimal`, `Boolean`, `UUID`, `Duration`, enums, `Player`, `OfflinePlayer`, `World`, and `Location`.

```kotlin
@Execute(name = "pay")
fun pay(@Context player: Player, @Arg target: OfflinePlayer, @Arg amount: BigDecimal) {}
```

Prefer separate `@Execute` overloads instead of deeply optional signatures when that reads better. Use CatEngine `NumberInput`/`DurationInput` in custom resolvers when you need compact forms such as `1.5k` or `10m`.

Custom argument:

```kotlin
class HomeNameArgument(private val service: HomeService) :
    ArgumentResolver<CommandSender, HomeName>() {

    override fun parse(
        invocation: Invocation<CommandSender>,
        context: Argument<HomeName>,
        input: String,
    ): ParseResult<HomeName> {
        return HomeName.of(input)?.let(ParseResult<HomeName>::success)
            ?: ParseResult.failure("Invalid home name.")
    }

    override fun suggest(
        invocation: Invocation<CommandSender>,
        context: Argument<HomeName>,
        suggestionContext: SuggestionContext,
    ): SuggestionResult {
        val player = invocation.sender() as? Player ?: return SuggestionResult.empty()
        return service.homeNames(player.uniqueId).collect(SuggestionResult.collector())
    }
}
```

Register:

```kotlin
.argument(HomeName::class.java, HomeNameArgument(homeService))
```

Custom context:

```kotlin
class PlayerProfileContext(private val service: ProfileService) :
    ContextProvider<CommandSender, PlayerProfile> {

    override fun provide(invocation: Invocation<CommandSender>): ContextResult<PlayerProfile> {
        val player = invocation.sender() as? Player
            ?: return ContextResult.error("Only players can use this command.")
        return ContextResult.ok { service.profile(player.uniqueId) }
    }
}
```

Register:

```kotlin
.context(PlayerProfile::class.java, PlayerProfileContext(profileService))
```

## Messages And Handlers

Customize framework messages in the builder:

```kotlin
.message(LiteMessages.MISSING_PERMISSIONS) { permissions ->
    "Missing permission: ${permissions.asJoinedText()}"
}
.message(LiteMessages.COMMAND_COOLDOWN) { _, state ->
    "Wait ${state.remainingDuration.toSeconds()}s."
}
.invalidUsage(MyInvalidUsageHandler(messages))
```

Prefer project `MessageService`/`ComponentParser` for final user-facing output when the handler type allows components. Keep text in `messages.yml`.

## Async And Folia

Use async for DB, files, HTTP, and heavy computation. Do not touch Bukkit API from `@Async` methods or future callbacks until you schedule back to the owner context.

```kotlin
@Async
@Execute(name = "top")
fun top(@Context sender: CommandSender) {
    val rows = service.loadTopHomes()
    if (sender is Player) {
        scheduler.runFor(sender) {
            messages.send(sender, HomeMessage.TOP, mapOf("rows" to format(rows)))
        }
    } else {
        sender.sendMessage(formatPlain(rows))
    }
}
```

Future-based service:

```kotlin
@Execute(name = "info")
fun info(@Context sender: CommandSender, @Arg target: OfflinePlayer) {
    service.loadInfo(target.uniqueId).whenComplete { info, error ->
        val player = sender as? Player
        if (player != null) {
            scheduler.runFor(player) { reply(sender, info, error) }
        } else {
            reply(sender, info, error)
        }
    }
}
```

Folia rules:

- Register `FoliaExtension(plugin)`.
- Use `CatScheduler.runFor(player)` before player/entity/inventory API after async work.
- Use `CatScheduler.runAt(location)` before location/block/world API.
- Do not use `Bukkit.getScheduler()`.

## Migration From Bukkit Commands

- Remove `commands:` entries for LiteCommands-managed commands from `plugin.yml`; keep `permissions:`.
- Remove `getCommand(...).setExecutor(...)` and `setTabCompleter(...)`.
- Convert each command to `@Command` / `@Execute`.
- Replace manual permission checks with `@Permission`.
- Replace aliases with `@Shortcut`.
- Replace `TabCompleter` with built-in argument suggestions or custom `suggest`.
- Register command instances in `LiteBukkitFactory.builder(...).commands(...)`.
- Unregister on disable.

## Builder Cheatsheet

```kotlin
LiteBukkitFactory.builder("myplugin", plugin)
    .extension(FoliaExtension(plugin))
    .commands(HomeCommand(...), AdminCommand(...))
    .argument(HomeName::class.java, HomeNameArgument(...))
    .context(PlayerProfile::class.java, PlayerProfileContext(...))
    .invalidUsage(MyInvalidUsageHandler(...))
    .message(LiteMessages.INVALID_USAGE) { "Invalid usage." }
    .strictMode(StrictMode.DISABLED)
    .build()
```

## Anti-Patterns

| Do not | Do instead |
| --- | --- |
| Declare managed commands in `plugin.yml` | `@Command` + builder registration |
| Block a region thread with SQL in `@Execute` | `@Async` or async service |
| Touch player/inventory/world from `@Async` | `CatScheduler.runFor` / `runAt` |
| Use `Bukkit.getScheduler()` on Folia | `FoliaExtension` + CatEngine scheduler |
| Use static service locators in commands | Constructor injection |
| Duplicate permission strings in command yml | `@Permission` + yml `permissions:` only |
| Handwrite player tab completion | `@Arg Player` or custom resolver suggestions |

## Related Skills

- Folia scheduling: `folia-plugin-development`
- Plugin standards: `minecraft-plugin-standards`
