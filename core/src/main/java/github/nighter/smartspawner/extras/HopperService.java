package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.BlockPos;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

public class HopperService {

    private final SmartSpawner plugin;
    @Getter
    private final HopperRegistry registry;
    private final HopperTransfer transfer;
    @Getter
    private final HopperTracker tracker;
    private final Scheduler.Task task;

    // Round-robin queue: processed maxPerTick entries per tick, rebuilding when exhausted.
    private final List<BlockPos> roundRobinQueue = new ArrayList<>();
    private int cursor = 0;

    public HopperService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.registry = new HopperRegistry();
        this.transfer = new HopperTransfer(plugin);
        this.tracker = new HopperTracker(plugin, registry);
        this.tracker.scanLoadedWorlds();

        // Run every tick. Throughput is controlled by hopper.max_per_tick.
        this.task = Scheduler.runTaskTimer(this::tick, 40L, 1L);
    }

    private void tick() {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        // Rebuild queue when exhausted or empty
        if (cursor >= roundRobinQueue.size()) {
            roundRobinQueue.clear();
            rebuildQueue();
            cursor = 0;
            if (roundRobinQueue.isEmpty()) return;
        }

        int perTick = plugin.getHopperConfig().getMaxPerTick();
        int processed = 0;

        while (processed < perTick && cursor < roundRobinQueue.size()) {
            BlockPos pos = roundRobinQueue.get(cursor++);

            World world = Bukkit.getWorld(pos.worldId());
            if (world != null) {
                Scheduler.runChunkTask(world, pos.getChunkX(), pos.getChunkZ(), () -> transfer.process(pos));
            }
            processed++;
        }
    }

    private void rebuildQueue() {
        registry.forEachChunk((worldId, chunkKey) -> {
            for (BlockPos pos : registry.getChunkHoppers(worldId, chunkKey)) {
                roundRobinQueue.add(pos);
            }
        });
    }

    /**
     * Must be called in plugin onDisable()
     */
    public void cleanup() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }

        if (tracker != null) {
            HandlerList.unregisterAll(tracker);
        }

        roundRobinQueue.clear();
    }
}