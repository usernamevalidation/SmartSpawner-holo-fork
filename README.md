# SmartSpawner — Extended Fork

> **Fork notice:** This is a personal fork of
> [OpenVdra/SmartSpawner](https://github.com/OpenVdra/SmartSpawner) (v1.8.1)
> with additional hologram lifecycle management, sellwand features, GUI improvements, and API
> enhancements. **Not affiliated with upstream.** Original plugin by NightExpress.
>
> Upstream documentation: https://docs.smartspawner.site

---

## What is this?

SmartSpawner is a Minecraft (Paper) plugin that turns vanilla spawners into fully-featured
smart spawners with virtual storage, EXP, stacking, GUI control, and shop/economy integration.

This fork keeps everything from upstream and adds a set of features aimed at large,
dense servers: reducing hologram entity overhead, expanding sellwand functionality,
optimizing hopper throughput, and exposing spawner data cleanly through the plugin API.

---

## Fork additions over upstream v1.8.1

### 1. Hologram lifecycle overhaul

Upstream spawns one `TextDisplay` per spawner and keeps it alive permanently. On servers with
thousands of spawners that becomes a measurable entity count and tick cost. This fork replaces
the always-on model with a gate + cap system.

- **`hologram.show_radius`** — a spawner's hologram only exists while at least one player is
  within N blocks. Set to `0` to disable and return to upstream behavior.
- **`hologram.max_per_player`** — within `show_radius`, each player only loads their *nearest N*
  spawner holograms. `0` = unlimited.
- **`hologram.visibility_interval`** — how often the gate is re-evaluated (default `1s`).
- **Rate-limited orphan cleanup** — startup sweep + recurring 30-minute sweep that removes
  orphaned `SmartSpawner-Holo-*` displays whose spawner no longer exists in the database or
  whose block is no longer a SPAWNER.
- **`SpawnerData.hologramSuppressed`** — a lifecycle flag that prevents reload/refresh paths
  from silently resurrecting a hologram the distance gate just removed.

Implemented in:

- `commands/hologram/HologramVisibilityTask.java`
- `spawner/properties/SpawnerData.java`
- `SmartSpawner.java`
- `config.yml` under `hologram:` and `hologram.cleanup:`

### 2. Sellwand sacrifice GUI with multiplier & uses

- New `/ss sellwand` GUI where players sacrifice a sellwand to apply its **multiplier** and
  **remaining uses** to a spawner.
- Multiplier and uses are read from the sellwand's item lore using configurable regex:

      sellwand:
        multiplier_regex: (?i)Multiplier:\s*([0-9.]+)x
        uses_regex: (?i)Uses:\s*([0-9]+|∞)

- Per-spawner tracked state (`SellwandStorage`) with usage countdown.

Implemented in:

- `commands/sellwand/SellwandGUI.java`
- `commands/sellwand/SellwandHolder.java`
- `commands/sellwand/SellwandListener.java`
- `commands/sellwand/SellwandSellListener.java`
- `commands/sellwand/SellwandStorage.java`
- `commands/sellwand/SellwandSubCommand.java`

### 3. AxSellWands integration

- Bundled `core/libs/AxSellWands.jar` and a dedicated listener + integration layer.
- Sellwand state shared with the spawner sell system so multipliers apply to the sell pipeline.

### 4. Glowing sellwands with hidden enchants

- Sellwand items are enchanted with a dummy enchantment and marked with the item's
  hide-enchants flag.
- Result: items appear enchanted (glow effect) but show no enchantment lines in the tooltip.

### 5. `/ss near` highlight cap

- `near.max_highlights` limits how many spawners `/ss near` will highlight per scan.
- Prevents client-side lag on servers with thousands of nearby spawners.

Implemented in:

- `commands/near/SpawnerHighlightManager.java`
- `commands/near/NearResultGUI.java`

### 6. Readable GUI with percentage display

- Spawner GUI now shows used / max storage as a percentage, and the same for EXP storage.
- Values are exposed in a format any other plugin can read from the GUI item's lore or through
  the API — no need to open the GUI or parse NBT manually.

### 7. Clean plugin API surface

- Spawner data is readable by any other plugin through the `SmartSpawnerAPI` interface.
- Player-aware sell values, programmatic sells, async spawner removal, and custom GUI
  layout providers are exposed.

See [API.md](API.md) for the full reference.

### 8. Auto-sell permission handling

- Auto-sell requires only `smartspawner.autosell` (no longer also requires `smartspawner.sellall`).
- Losing `smartspawner.autosell` clears the player's auto-sell flag automatically, so it stops
  running and the GUI reflects OFF.

### 9. Independent hologram debug logger

- New `HologramDebugLogger` writes hologram lifecycle events to a dedicated file
  (`plugins/SmartSpawner/hologram-debug.log`), gated by `hologram.debug_log.enabled`.
- Deliberately separate from the global `debug` flag so you can trace hologram removals
  without enabling console spam everywhere else.
- Records removals (orphan, malformed, admin clear) with reason, identifier, world, and
  coordinates, plus optional scan summaries and size-based rotation.

### 10. Config

The fork ships upstream's defaults unchanged, plus the new keys documented above
(`hologram.show_radius`, `hologram.max_per_player`, `hologram.visibility_interval`,
`hologram.cleanup.*`, `hologram.debug_log.*`, `near.max_highlights`,
`sellwand.multiplier_regex`, `sellwand.uses_regex`, `hopper.max_per_tick`).

### 11. Hopper transfer optimization

Upstream's hopper code scanned the spawner's full virtual inventory on every transfer cycle — with 1M pages of items stored, that's a sort of every entry, per hopper, per cycle. On a 10k-hopper oneblock this drove MSPT past 300 and dropped TPS to 1.

This fork replaces it with a constant-cost fast path:

- **Page-1 sorted cache** — `getDisplayRange(0, 45)` and `getDisplayPage(1, 45)` return a cached first page. Built once per inventory change, not once per hopper.
- **Unsorted fast cache** — `peekAnyItems(limit)` reads directly from the raw item map without sorting. Cost is O(stack_per_transfer), independent of total inventory depth.
- **Round-robin scheduling** — `HopperService` processes `hopper.max_per_tick` hoppers per tick instead of dispatching every hopper in one tick. Eliminates the per-tick spike.
- **GUI update gate** — spawner GUI refreshes only fire when a viewer is present, not on every hopper transfer.
- **`hopper.max_per_tick`** — new config key, default 50. Throughput = `max_per_tick * 20` hoppers per second. 10k hoppers with the default sweeps in ~10 seconds.

All three caches are invalidated on any inventory mutation (`addItem`, `addItems`, `removeItem`, `removeItems`, `sortItems`, `setMaxSlots`), so stale items can't be pulled.

Implemented in:

- `extras/HopperService.java`
- `extras/HopperTransfer.java`
- `extras/HopperConfig.java`
- `spawner/properties/VirtualInventory.java`
- `config.yml` under `hopper:`

Tested on a 10k-hopper oneblock: MSPT stays in single digits, TPS holds at 20.

---

## Requirements

- **Server:** Paper 1.21+ (Folia support is inherited from upstream but has not been
  regression-tested for the fork additions.)
- **Java:** 21+
- **Optional integrations:** Vault, EssentialsX Economy, EconomyShopGUI, AxSellWands,
  WorldGuard, SuperiorSkyblock2, AuraSkills, MythicMobs, and the other upstream-supported
  plugins.

### Building

    ./gradlew build

Jar output: `core/build/libs/`

### Folia note

The upstream plugin supports Folia. The fork additions in `HologramVisibilityTask` run the
visibility pass on the main thread and read blocks/entities directly. On Paper this is fine.
On Folia, the visibility pass and orphan sweep should be audited before deploying.

---

## License

Inherits the upstream license. See [LICENSE](LICENSE).

Original work © NightExpress. Fork additions © the fork author.

---

## Credits

- Upstream plugin: [OpenVdra/SmartSpawner](https://github.com/OpenVdra/SmartSpawner)
- Fork features, sellwand GUI, hologram lifecycle, API surface: this repo
