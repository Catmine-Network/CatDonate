---
name: triumph-gui
description: Create and maintain inventory GUIs with Triumph GUI 3.x (dev.triumphteam) in Paper/Folia plugins adopting CatEngine. Use when building menus, paginated lists, scrolling views, storage GUIs, GuiItem wrappers, click actions, fillers, GUI updates, or inventory menu workflows.
---

# Triumph GUI

Use this as compact Triumph GUI 3.x docs for standalone plugins that adopt CatEngine.

## Critical Rules

1. Use Triumph GUI 3.x builders: `Gui.gui()`, `Gui.paginated()`, `Gui.scrolling()`, `Gui.storage()`.
2. Do not use Triumph GUI 4.x APIs such as `buildGui` or `triumph-gui-kotlin` unless the project explicitly migrated.
3. In this codebase style, build normal Bukkit `ItemStack`s and wrap them in `GuiItem`; avoid Triumph `ItemBuilder`.
4. Titles, item names, lore, and feedback use Adventure `Component`, preferably via CatEngine `MessageService`/`ComponentParser`.
5. Keep user-facing text in `messages.yml` or the plugin's message source.
6. Open/update GUIs and touch player inventories only on the player's region thread.
7. Cancel clicks by default on interactive GUIs.
8. GUI classes build layout and route clicks; services own business logic.
9. `StorageGui` is memory-only. Persist contents yourself on close if needed.

## Dependency And Imports

Use the project dependency alias if present; otherwise add `dev.triumphteam:triumph-gui` 3.x.

```kotlin
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.catmine.engine.message.MessageService
import net.catmine.engine.scheduler.CatScheduler
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer
```

If the library is shaded/relocated at build time, source imports still normally stay `dev.triumphteam.gui.*` unless the target plugin already uses relocated imports.

## ItemStack To GuiItem

Build items with Bukkit APIs or a local item factory, then wrap with `GuiItem`.

```kotlin
private fun item(
    material: Material,
    name: Component,
    lore: List<Component> = emptyList(),
): ItemStack {
    val stack = ItemStack(material)
    stack.editMeta { meta ->
        meta.displayName(name)
        meta.lore(lore)
    }
    return stack
}

private fun guiItem(stack: ItemStack, action: ((InventoryClickEvent) -> Unit)? = null): GuiItem {
    return if (action == null) GuiItem(stack) else GuiItem(stack, Consumer { event -> action(event) })
}
```

Use cloned/base item factories when many slots share the same display item.

## GUI Types

| Type | Builder | Use for |
| --- | --- | --- |
| Basic | `Gui.gui()` | Fixed menus, settings, confirmation dialogs |
| Paginated | `Gui.paginated()` | Long lists with page navigation |
| Scrolling | `Gui.scrolling()` | Long lists with scroll controls |
| Storage | `Gui.storage()` | Player-editable slots, memory-only until saved |

Common container types via `GuiType`: `CHEST`, `WORKBENCH`, `HOPPER`, `DISPENSER`, `BREWING`. Chest rows support slots `0..(rows * 9 - 1)`.

## Basic GUI

```kotlin
class HomeMenu(
    private val messages: MessageService<HomeMessage>,
    private val service: HomeService,
) {
    fun open(player: Player) {
        val gui = Gui.gui()
            .title(messages.component(HomeMessage.MENU_TITLE))
            .rows(3)
            .create()

        gui.setDefaultClickAction { it.isCancelled = true }

        val setHome = guiItem(item(Material.EMERALD, messages.component(HomeMessage.SET_HOME))) { event ->
            event.isCancelled = true
            event.whoClicked.closeInventory()
            service.setHome(player)
        }

        gui.setItem(13, setHome)

        gui.filler.fill(guiItem(item(Material.GRAY_STAINED_GLASS_PANE, Component.empty())))
        gui.open(player)
    }
}
```

Slot indexing starts at `0`. `setItem(row, col, item)` is also available. A slot outside the container size throws.

Typed GUI:

```kotlin
val gui = Gui.gui()
    .title(title)
    .type(GuiType.HOPPER)
    .create()
```

## Paginated GUI

```kotlin
fun openHomeList(player: Player, entries: List<GuiItem>) {
    val gui = Gui.paginated()
        .title(messages.component(HomeMessage.LIST_TITLE))
        .rows(6)
        .pageSize(45)
        .create()

    gui.setDefaultClickAction { it.isCancelled = true }

    gui.setItem(6, 3, guiItem(item(Material.ARROW, messages.component(HomeMessage.PREVIOUS))) {
        it.isCancelled = true
        gui.previous()
    })

    gui.setItem(6, 7, guiItem(item(Material.ARROW, messages.component(HomeMessage.NEXT))) {
        it.isCancelled = true
        gui.next()
    })

    entries.forEach(gui::addItem)
    gui.open(player)
}
```

Rules:

- `addItem` is paginated content.
- `setItem` is static layout: borders, nav, headers.
- Cancel before `next()`/`previous()` unless default click action already cancels.

## Scrolling GUI

```kotlin
val gui = Gui.scrolling()
    .title(messages.component(HomeMessage.SCROLL_TITLE))
    .rows(6)
    .pageSize(45)
    .scrollType(ScrollType.VERTICAL)
    .create()
```

Use `addItem` for scrollable content and `setItem` for static controls. `ScrollType.HORIZONTAL` is available when the layout needs it.

## Storage GUI

`StorageGui` accepts raw `ItemStack`s for editable storage slots. Items persist only while the GUI instance lives.

```kotlin
val gui = Gui.storage()
    .title(messages.component(HomeMessage.STORAGE_TITLE))
    .rows(6)
    .create()

gui.addItem(existingStack)

gui.setItem(53, guiItem(item(Material.BARRIER, messages.component(HomeMessage.CLOSE))) {
    it.isCancelled = true
    it.whoClicked.closeInventory()
})

gui.setCloseGuiAction { event ->
    val contents = event.inventory.contents.filterNotNull()
    service.saveStorage(event.player.uniqueId, contents)
}
```

For long-term storage, save via service/DB/files on close or explicit save. Do not rely on Triumph to survive restart.

## Common Actions

```kotlin
gui.setDefaultClickAction { it.isCancelled = true }
gui.addSlotAction(22) { event -> event.isCancelled = true }
gui.setOpenGuiAction { event -> }
gui.setCloseGuiAction { event -> }
gui.updateItem(row, col, newStack)
gui.updateItem(slot, newStack)
gui.update()
gui.updateTitle(newTitle)
```

Prefer `updateItem` for changed slots. Avoid full `update()` on timers unless necessary.

## Folia And Async Data

Click/open/close handlers normally run on the player's event context. After any async work, schedule back before opening or updating.

```kotlin
@Execute(name = "homes")
fun homes(@Context player: Player) {
    service.loadHomes(player.uniqueId).whenComplete { homes, error ->
        scheduler.runFor(player) {
            if (!player.isOnline) return@runFor
            HomeListMenu(messages, service).open(player, homes)
        }
    }
}
```

Rules:

- `gui.open(player)`: player region.
- `gui.updateItem(...)`: player region for that viewer.
- DB/HTTP/item preparation can be async if it avoids Bukkit API.
- Do not use `Bukkit.getScheduler()`; use CatEngine `CatScheduler`.

## Messages Layout

Example:

```yml
homes:
  menu:
    title: "<dark_gray>Homes"
    set: "<green>Set Home"
    previous: "<yellow>Previous Page"
    next: "<yellow>Next Page"
    close: "<red>Close"
```

Create matching `MessageKey` enum entries and use `MessageService` in GUI classes.

## File Layout

```
src/main/kotlin/<plugin>/
  gui/
    HomeMenu.kt
    HomeListMenu.kt
  service/
    HomeService.kt
  command/
    HomeCommand.kt
```

- One class per screen, or a factory when screens share layout.
- Inject `MessageService`, services, and `CatScheduler`.
- Track open GUIs by UUID only when forced refresh/close is required; clear on close, quit, reload, and disable.

## Anti-Patterns

| Do not | Do instead |
| --- | --- |
| Use Triumph GUI 4.x APIs in a 3.x project | `Gui.gui()` / `paginated()` / `scrolling()` / `storage()` |
| Use Triumph `ItemBuilder` in this style | Build `ItemStack`, wrap `GuiItem` |
| Hardcode raw item text | `MessageService` + `ComponentParser` |
| Use `setItem` for paginated content | `addItem` |
| Open/update GUI after async work directly | `scheduler.runFor(player) { ... }` |
| Expect `StorageGui` to persist across restart | Save contents via service |
| Put business logic inside click lambdas | Delegate to service |
| Store `Player` as open-GUI key | Store `UUID` |

## Quick Reference

```kotlin
Gui.gui().title(component).rows(3).create()
Gui.paginated().title(component).rows(6).pageSize(45).create()
Gui.scrolling().title(component).rows(6).pageSize(45).scrollType(ScrollType.VERTICAL).create()
Gui.storage().title(component).rows(6).create()

gui.setDefaultClickAction { it.isCancelled = true }
gui.setItem(slot, guiItem(itemStack) { event -> })
gui.addItem(guiItem)
gui.open(player)
```

## Related Skills

- Folia scheduling: `folia-plugin-development`
- Commands opening GUIs: `lite-commands`
- Plugin standards: `minecraft-plugin-standards`
