# SmartSpawner API

Public API for other Paper plugins to read and modify SmartSpawner spawners.

This fork inherits the upstream API surface. Anything documented here also applies to upstream
v1.8.1 unless noted otherwise.

---

## Getting the API

```java
SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
if (api == null) {
    // SmartSpawner is not installed or not enabled — bail out
    return;
}
```

`SmartSpawnerProvider.getAPI()` is a static method that returns `null` when SmartSpawner isn't
loaded. Null-check once at plugin enable and cache the reference for the rest of your plugin's
lifetime — don't call it on every event.

### Dependency

Add the `api` module as a `compileOnly` dependency. Build coordinates depend on how you consume
the fork — see `jitpack.yml` and `build.gradle.kts` at the repo root, or build the `api` module
locally and reference the jar directly.

---

## Reading spawner data

Spawner data is exposed as a read-only DTO. Every field is a snapshot — once you have a DTO it
will not update when the underlying spawner changes.

```java
SpawnerDataDTO spawner = api.getSpawnerByLocation(block.getLocation());
if (spawner == null) return;

int        stackSize = spawner.getStackSize();
int        usedSlots = spawner.getCurrentItemCount();
EntityType type      = spawner.getEntityType();
boolean    isItem    = spawner.isItemSpawner();
boolean    full      = spawner.isAtCapacity();
```

### Available fields

| Method | Returns | Notes |
| --- | --- | --- |
| `getSpawnerId()` | `String` | Unique spawner ID |
| `getLocation()` | `Location` | Block location |
| `getEntityType()` | `EntityType` | `ITEM` for item spawners |
| `getSpawnedItemMaterial()` | `Material` | Non-null only for item spawners |
| `isItemSpawner()` | `boolean` | Convenience check |
| `getStackSize()` | `int` | Current stack count |
| `getMaxStackSize()` | `int` | Config-limited maximum |
| `getCurrentItemCount()` | `int` | Used virtual-inventory slots |
| `isAtCapacity()` | `boolean` | Inventory and EXP both maxed |
| `getOwner()` | `String` | Last interacting player; may be `null` |
| `getBaseMaxStoragePages()` | `int` | |
| `getBaseMinMobs()` / `getBaseMaxMobs()` | `int` | Per-cycle mob roll range |
| `getBaseMaxStoredExp()` | `long` | |
| `getBaseSpawnerDelay()` | `long` | In ticks |

### Lookup methods

```java
List<SpawnerDataDTO> all    = api.getAllSpawners();
List<SpawnerDataDTO> loaded = api.getLoadedSpawners();

SpawnerDataDTO byId       = api.getSpawnerById("some-id");
SpawnerDataDTO byLocation = api.getSpawnerByLocation(location);
```

**Prefer `getLoadedSpawners()`** for anything that runs periodically or iterates spawners in
bulk. `getAllSpawners()` returns every registered spawner, including ones in unloaded chunks —
touching those can force a synchronous chunk load. The plugin itself uses
`getLoadedSpawners()` for its internal scan passes.

### Thread-safety

`SpawnerDataDTO` is a snapshot. Safe to read from any thread. Re-fetch to get current values.

---

## Modifying spawner data

Modifications go through a `SpawnerDataModifier`.

```java
SpawnerDataModifier mod = api.getSpawnerModifier(spawnerId);
if (mod == null) return;

mod.setMaxStackSize(5000)
   .setBaseMaxStoragePages(5)
   .setBaseMinMobs(10)
   .setBaseMaxMobs(50)
   .setBaseMaxStoredExp(500_000L)
   .setBaseSpawnerDelay(40L)
   .applyChanges();
```

**You must call `applyChanges()`.** Nothing takes effect until you do. It recalculates dependent
values (max storage slots, max stacked EXP, etc.) and refreshes the GUI.

### Modifiable fields

| Setter | Type | Notes |
| --- | --- | --- |
| `setMaxStackSize(int)` | `int` | |
| `setBaseMaxStoragePages(int)` | `int` | Each page = 45 slots |
| `setBaseMinMobs(int)` / `setBaseMaxMobs(int)` | `int` | |
| `setBaseMaxStoredExp(long)` | `long` | |
| `setBaseSpawnerDelay(long)` | `long` | In ticks |

`getStackSize()` is read-only. To change the stack size, use the in-game stacking system or the
spawner break/place events.

### Threading

`applyChanges()` dispatches work onto the spawner's region thread internally, so you can call
it from any thread. If you're iterating a large list, still batch your calls — each modifier
application touches the spawner's GUI cache.

---

## Creating spawner items

```java
ItemStack smartSpawner   = api.createSpawnerItem(EntityType.ZOMBIE);
ItemStack stackOfTen     = api.createSpawnerItem(EntityType.ZOMBIE, 10);
ItemStack vanillaSpawner = api.createVanillaSpawnerItem(EntityType.SKELETON);
ItemStack itemSpawner    = api.createItemSpawnerItem(Material.DIAMOND);
```

### Type checks

```java
boolean isSmart   = api.isSmartSpawner(item);
boolean isVanilla = api.isVanillaSpawner(item);
boolean isItem    = api.isItemSpawner(item);

EntityType type    = api.getSpawnerEntityType(item);
Material   spawned = api.getItemSpawnerMaterial(item);
```

---

## Selling

### Reading sell value

```java
double baseValue   = api.getSpawnerSellValue(spawnerId);
double playerValue = api.getSpawnerSellValue(spawnerId, player);
```

The player-aware overload applies rank/permission price multipliers exposed by the active shop
integration (e.g. EconomyShopGUI Premium). The non-player overload returns base prices only.
Both are also overloaded by `Location` in place of `spawnerId`.

### Triggering a sell

```java
api.sellSpawner(spawnerId, player).thenAccept(amountPaid -> {
    player.sendMessage("Sold for " + amountPaid);
});
```

Fires `SpawnerSellEvent` with source `"API"`. The returned `CompletableFuture<Double>` completes
with the amount paid, or `0.0` on failure (empty inventory, no permission, event cancelled).

---

## Removing a spawner

```java
api.removeSpawner(spawnerId).thenAccept(removed -> {
    if (removed) {
        // Block and stored data removed
    }
});
```

Also overloaded by `Location`. Loads the chunk asynchronously if not already loaded. Completes
with `false` if the spawner doesn't exist or is already being removed.

---

## GUI layouts

```java
GuiLayoutRegistry registry = api.getLayoutRegistry();
```

Custom layout registration is documented in the upstream developer docs at
https://docs.smartspawner.site/developer-api/gui-layout/.

### Per-spawner layout provider

```java
api.setSpawnerLayoutProvider((spawner, defaultLayout) -> {
    // Return a layout for this specific spawner, or defaultLayout to use the config default.
    return defaultLayout;
});
```

Only one provider can be active at a time — setting a new one replaces the previous. Call
`api.clearSpawnerLayoutProvider()` to return to config-driven layouts.

---

## Events

The plugin fires these events under `api/events/`. Every event extends `SpawnerEvent`, which
extends `org.bukkit.event.Event`. `SpawnerEvent` is the abstract base class — you don't listen
to it directly, but you can use it as a filter if you want a catch-all handler.

| Event | Fired when |
| --- | --- |
| `SpawnerPlaceEvent` | A spawner block is placed |
| `SpawnerBreakEvent` | A spawner is broken by any cause |
| `SpawnerPlayerBreakEvent` | A spawner is broken specifically by a player |
| `SpawnerRemoveEvent` | A spawner is removed (break, explode, API, etc.) |
| `SpawnerExplodeEvent` | An explosion affects a spawner |
| `SpawnerStackEvent` | A spawner's stack size changes |
| `SpawnerSellEvent` | A sell operation is about to run |
| `SpawnerEggChangeEvent` | A spawn egg changes a spawner's entity type |
| `SpawnerExpClaimEvent` | A player claims EXP from a spawner |
| `SpawnerOpenGUIEvent` | A player opens a spawner GUI |
| `SpawnerDropAllEvent` | A player drops all items out of a spawner |
| `SpawnerTakeAllEvent` | A player takes all items from a spawner |

Some events are cancellable. Check whether the specific class implements
`org.bukkit.event.Cancellable`. If it does, cancel with `event.setCancelled(true)`.

### Registering a listener

```java
@EventHandler
public void onSpawnerSell(SpawnerSellEvent event) {
    if (!event.getPlayer().hasPermission("myplugin.sellbonus")) return;
    // Inspect or modify
}
```

Register as a normal Bukkit listener:

```java
Bukkit.getPluginManager().registerEvents(listener, this);
```

---

## Fork additions

Anything above is inherited from upstream. If this fork adds methods to `SmartSpawnerAPI`, they
will be present on the interface but may not be documented here — check
`api/src/main/java/github/nighter/smartspawner/api/SmartSpawnerAPI.java` for the current
source of truth.

---

## Upstream API docs

Upstream documentation: https://docs.smartspawner.site/developer-api/. If a method here isn't
documented there, it's a fork addition.
