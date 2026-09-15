package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Independent debug logger for hologram lifecycle events.
 * <p>
 * Writes to a dedicated file (default {@code hologram-debug.log}) when enabled in
 * {@code hologram.debug_log.enabled}. Deliberately separate from the global {@code debug}
 * config so admins can trace hologram removals without enabling console spam everywhere else.
 * <p>
 * Thread-safe: all writes are synchronized on the class lock.
 */
public final class HologramDebugLogger {

    public enum RemovalReason {
        ORPHAN_NO_SPAWNER_DATA,
        ORPHAN_BLOCK_NOT_SPAWNER,
        MALFORMED_IDENTIFIER,
        ADMIN_CLEAR_HOLOGRAMS,
        DISTANCE_GATE_SUPPRESS
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static SmartSpawner plugin;
    private static Path logPath;
    private static long maxSizeBytes;
    private static boolean enabled;
    private static boolean logScans;
    private static final Object LOCK = new Object();

    private HologramDebugLogger() {}

    public static void initialize(SmartSpawner pluginInstance) {
        plugin = pluginInstance;
        loadConfig();
    }

    public static void loadConfig() {
        if (plugin == null) return;
        enabled = plugin.getConfig().getBoolean("hologram.debug_log.enabled", false);
        logScans = plugin.getConfig().getBoolean("hologram.debug_log.log_scans", false);
        String filename = plugin.getConfig().getString("hologram.debug_log.file", "hologram-debug.log");
        int maxMb = plugin.getConfig().getInt("hologram.debug_log.max_size_mb", 5);
        maxSizeBytes = Math.max(0L, (long) maxMb * 1024L * 1024L);
        logPath = plugin.getDataFolder().toPath().resolve(filename);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isScanLoggingEnabled() {
        return enabled && logScans;
    }

    public static void logRemoval(TextDisplay display, RemovalReason reason) {
        if (!enabled || display == null) return;
        String id = display.getCustomName();
        Location loc = display.getLocation();
        write(String.format("REMOVED reason=%s id=%s world=%s x=%d y=%d z=%d",
                reason.name(),
                id == null ? "<null>" : id,
                loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public static void logRemoval(String identifier, Location loc, RemovalReason reason) {
        if (!enabled) return;
        write(String.format("REMOVED reason=%s id=%s world=%s x=%d y=%d z=%d",
                reason.name(),
                identifier == null ? "<null>" : identifier,
                loc == null || loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                loc == null ? 0 : loc.getBlockX(),
                loc == null ? 0 : loc.getBlockY(),
                loc == null ? 0 : loc.getBlockZ()));
    }

    public static void logSuppress(Location spawnerLoc, String spawnerId) {
        if (!enabled) return;
        write(String.format("SUPPRESSED spawner=%s world=%s x=%d y=%d z=%d",
                spawnerId == null ? "<null>" : spawnerId,
                spawnerLoc == null || spawnerLoc.getWorld() == null ? "?" : spawnerLoc.getWorld().getName(),
                spawnerLoc == null ? 0 : spawnerLoc.getBlockX(),
                spawnerLoc == null ? 0 : spawnerLoc.getBlockY(),
                spawnerLoc == null ? 0 : spawnerLoc.getBlockZ()));
    }

    public static void logScan(String label, int candidates, int removed) {
        if (!isScanLoggingEnabled()) return;
        write(String.format("SCAN label=%s candidates=%d removed=%d", label, candidates, removed));
    }

    private static void write(String line) {
        String stamped = "[" + LocalDateTime.now().format(TS) + "] " + line + System.lineSeparator();
        synchronized (LOCK) {
            try {
                if (logPath == null) return;
                Files.createDirectories(logPath.getParent());
                rotateIfNeeded();
                try (Writer w = Files.newBufferedWriter(logPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND)) {
                    w.write(stamped);
                }
            } catch (IOException e) {
                if (plugin != null) {
                    plugin.getLogger().warning("Hologram debug log write failed: " + e.getMessage());
                }
            }
        }
    }

    private static void rotateIfNeeded() throws IOException {
        if (maxSizeBytes <= 0 || !Files.exists(logPath)) return;
        long size = Files.size(logPath);
        if (size < maxSizeBytes) return;
        byte[] all = Files.readAllBytes(logPath);
        int keepFrom = all.length / 2;
        byte[] kept = new byte[all.length - keepFrom];
        System.arraycopy(all, keepFrom, kept, 0, kept.length);
        Files.write(logPath, kept, StandardOpenOption.TRUNCATE_EXISTING);
    }
}