package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Owns three hologram-lifecycle jobs:
 *
 * <ul>
 *   <li><b>Visibility gate</b> — a spawner's {@link TextDisplay} only exists when at least one
 *       online player has it in their nearest-N set within {@code hologram.show_radius}.
 *       Runs every {@code hologram.visibility_interval} ticks.</li>
 *   <li><b>Startup sweep</b> — one-shot orphan cleanup, rate-limited across ticks.</li>
 *   <li><b>Recurring sweep</b> — periodic orphan cleanup, softer rate limit.</li>
 * </ul>
 *
 * "Orphan" means a TextDisplay named {@code SmartSpawner-Holo-*} whose spawner is not in
 * {@link SpawnerManager}, or whose block at the parsed location is no longer a SPAWNER.
 */
public class HologramVisibilityTask {
    private static final String IDENTIFIER_PREFIX = "SmartSpawner-Holo-";

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;

    private Scheduler.Task visibilityTask;
    private Scheduler.Task recurringSweepTask;

    // Config (refreshed on reload)
    private int showRadius;
    private int maxPerPlayer;
    private long visibilityIntervalTicks;
    private boolean cleanupOnStartup;
    private boolean cleanupRecurring;
    private int startupScanPerTick;
    private int runtimeScanPerTick;

    public HologramVisibilityTask(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        loadConfig();
    }

    public void loadConfig() {
        this.showRadius = plugin.getConfig().getInt("hologram.show_radius", 48);
        this.maxPerPlayer = Math.max(0, plugin.getConfig().getInt("hologram.max_per_player", 30));
        this.visibilityIntervalTicks = Math.max(20L, plugin.getTimeFormatter()
                .parseTimeToTicks(plugin.getConfig().getString("hologram.visibility_interval", "1s"), 20L));
        this.cleanupOnStartup = plugin.getConfig().getBoolean("hologram.cleanup.on_startup", true);
        this.cleanupRecurring = plugin.getConfig().getBoolean("hologram.cleanup.enabled", true);
        this.startupScanPerTick = Math.max(1, plugin.getConfig()
                .getInt("hologram.cleanup.scan_per_tick_startup", 5));
        this.runtimeScanPerTick = Math.max(1, plugin.getConfig()
                .getInt("hologram.cleanup.scan_per_tick_runtime", 2));
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void start() {
        if (!plugin.getConfig().getBoolean("hologram.enabled", false)) return;

        // Visibility gate — runs as long as either gate is active
        if (showRadius > 0 || maxPerPlayer > 0) {
            visibilityTask = Scheduler.runTaskTimer(
                    this::runVisibilityPass,
                    visibilityIntervalTicks,
                    visibilityIntervalTicks);
        }

        // Startup sweep (delayed once)
        if (cleanupOnStartup) {
            long delayTicks = Math.max(1L,
                    plugin.getConfig().getLong("hologram.cleanup.startup_delay_seconds", 10L) * 20L);
            Scheduler.runTaskLater(() -> runOrphanSweep(startupScanPerTick), delayTicks);
        }

        // Recurring sweep
        if (cleanupRecurring) {
            long intervalTicks = Math.max(20L, plugin.getTimeFormatter().parseTimeToTicks(
                    plugin.getConfig().getString("hologram.cleanup.interval", "30m"),
                    30L * 60L * 20L));
            recurringSweepTask = Scheduler.runTaskTimer(
                    () -> runOrphanSweep(runtimeScanPerTick),
                    intervalTicks,
                    intervalTicks);
        }
    }

    public void stop() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
        if (recurringSweepTask != null) {
            recurringSweepTask.cancel();
            recurringSweepTask = null;
        }
    }

    // =========================================================================
    // Visibility gate — show_radius × max_per_player
    // =========================================================================

    private void runVisibilityPass() {
        if (!plugin.getConfig().getBoolean("hologram.enabled", false)) return;

        List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();
        if (allSpawners.isEmpty()) return;

        final double radiusSq = (double) showRadius * showRadius;

        // allowed = union of each player's nearest-N spawners within showRadius.
        // Identity map because SpawnerData doesn't override equals/hashCode.
        Set<SpawnerData> allowed = Collections.newSetFromMap(new IdentityHashMap<>());

        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) continue;

            // Snapshot this world's spawners once, reused for every player in the world.
            List<SpawnerData> worldSpawners = new ArrayList<>();
            for (SpawnerData s : allSpawners) {
                Location l = s.getSpawnerLocation();
                if (l != null && l.getWorld() == world) worldSpawners.add(s);
            }
            if (worldSpawners.isEmpty()) continue;

            for (Player p : players) {
                if (!p.isOnline() || p.isDead()) continue;
                Location ploc = p.getLocation();

                if (maxPerPlayer == 0) {
                    // Unlimited: every spawner within showRadius qualifies
                    for (SpawnerData s : worldSpawners) {
                        if (s.getSpawnerLocation().distanceSquared(ploc) <= radiusSq) {
                            allowed.add(s);
                        }
                    }
                } else {
                    // Bounded top-N: max-heap keyed on distance (peek = furthest in current top-N)
                    PriorityQueue<SpawnerData> topN = new PriorityQueue<>(
                            maxPerPlayer,
                            (a, b) -> Double.compare(
                                    b.getSpawnerLocation().distanceSquared(ploc),
                                    a.getSpawnerLocation().distanceSquared(ploc)));

                    for (SpawnerData s : worldSpawners) {
                        double d = s.getSpawnerLocation().distanceSquared(ploc);
                        if (d > radiusSq) continue;

                        if (topN.size() < maxPerPlayer) {
                            topN.offer(s);
                        } else if (d < topN.peek().getSpawnerLocation().distanceSquared(ploc)) {
                            topN.poll();
                            topN.offer(s);
                        }
                    }
                    allowed.addAll(topN);
                }
            }
        }

        // Apply: suppress anything not in the allowed set; unsuppress anything in it.
        for (SpawnerData s : allSpawners) {
            boolean shouldShow = allowed.contains(s);
            boolean suppressed = s.isHologramSuppressed();
            if (!shouldShow && !suppressed) {
                s.suppressHologram();
            } else if (shouldShow && suppressed) {
                s.unsuppressHologram();
            }
        }
    }

    // =========================================================================
    // Orphan sweep
    // =========================================================================

    private void runOrphanSweep(int perTick) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", false)) return;

        List<TextDisplay> candidates = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                String name = display.getCustomName();
                if (name != null && name.startsWith(IDENTIFIER_PREFIX)) {
                    candidates.add(display);
                }
            }
        }

        if (candidates.isEmpty()) return;

        scheduleSweepBatch(candidates.iterator(), perTick, 0);
    }

    private void scheduleSweepBatch(Iterator<TextDisplay> it, int perTick, int removedSoFar) {
        int processed = 0;
        int removed = removedSoFar;

        while (it.hasNext() && processed < perTick) {
            TextDisplay display = it.next();
            processed++;

            if (!display.isValid()) continue;

            HologramDebugLogger.RemovalReason reason = shouldRemove(display);
            if (reason != null) {
                HologramDebugLogger.logRemoval(display, reason);
                display.remove();
                removed++;
            }
        }

        final int finalRemoved = removed;
        if (it.hasNext()) {
            Scheduler.runTaskLater(() -> scheduleSweepBatch(it, perTick, finalRemoved), 1L);
        } else if (finalRemoved > 0) {
            plugin.getLogger().info("[SmartSpawner] Hologram cleanup: removed "
                    + finalRemoved + " orphaned hologram(s).");
            HologramDebugLogger.logScan("periodic_sweep", 0, finalRemoved);
        }
    }

    /**
     * @return the reason this display should be removed, or {@code null} if it should be kept.
     */
    private HologramDebugLogger.RemovalReason shouldRemove(TextDisplay display) {
        String name = display.getCustomName();
        if (name == null || !name.startsWith(IDENTIFIER_PREFIX)) return null;

        Location parsed = parseLocationFromIdentifier(name, display.getWorld());
        if (parsed == null) return HologramDebugLogger.RemovalReason.MALFORMED_IDENTIFIER;

        SpawnerData data = spawnerManager.getSpawnerByLocation(parsed);
        if (data == null) return HologramDebugLogger.RemovalReason.ORPHAN_NO_SPAWNER_DATA;

        Material type = parsed.getBlock().getType();
        if (type != Material.SPAWNER) return HologramDebugLogger.RemovalReason.ORPHAN_BLOCK_NOT_SPAWNER;

        return null;
    }

    /**
     * Inverse of {@link SpawnerHologram}'s identifier format:
     * {@code SmartSpawner-Holo-<world>-<x>-<y>-<z>}. World names may contain dashes, so the
     * last three dash-separated tokens are the coordinates and everything between "Holo" and
     * them is the world name.
     */
    private Location parseLocationFromIdentifier(String name, World fallbackWorld) {
        try {
            String[] parts = name.split("-");
            if (parts.length < 6) return null;

            StringBuilder worldName = new StringBuilder();
            for (int i = 2; i < parts.length - 3; i++) {
                if (i > 2) worldName.append('-');
                worldName.append(parts[i]);
            }

            World world = Bukkit.getWorld(worldName.toString());
            if (world == null) world = fallbackWorld;
            if (world == null) return null;

            int x = Integer.parseInt(parts[parts.length - 3]);
            int y = Integer.parseInt(parts[parts.length - 2]);
            int z = Integer.parseInt(parts[parts.length - 1]);
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}