package github.nighter.smartspawner.commands.clear;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.commands.hologram.HologramDebugLogger;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.TextDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

@NullMarked
public class ClearHologramsSubCommand extends BaseSubCommand {

    private static final String IDENTIFIER_PREFIX = "SmartSpawner-Holo-";

    public ClearHologramsSubCommand(SmartSpawner plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "holograms";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.clear";
    }

    @Override
    public String getDescription() {
        return "Remove orphaned SmartSpawner holograms (no matching spawner at the location)";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        int removed = 0;
        int kept = 0;
        int scanned = 0;

        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                String name = display.getCustomName();
                if (name == null || !name.startsWith(IDENTIFIER_PREFIX)) continue;
                scanned++;

                Location loc = parseLocationFromIdentifier(name, world);
                if (loc == null) {
                    // Malformed name — remove it, it's definitely ours and broken
                    HologramDebugLogger.logRemoval(display,
                            HologramDebugLogger.RemovalReason.MALFORMED_IDENTIFIER);
                    display.remove();
                    removed++;
                    continue;
                }

                boolean spawnerExists = plugin.getSpawnerManager() != null
                        && plugin.getSpawnerManager().getSpawnerByLocation(loc) != null;

                if (!spawnerExists) {
                    HologramDebugLogger.logRemoval(display,
                            HologramDebugLogger.RemovalReason.ADMIN_CLEAR_HOLOGRAMS);
                    display.remove();
                    removed++;
                } else {
                    kept++;
                }
            }
        }

        HologramDebugLogger.logScan("admin_clear", scanned, removed);

        Map<String, String> ph = Map.of(
                "removed", String.valueOf(removed),
                "kept", String.valueOf(kept)
        );
        plugin.getMessageService().sendMessage(sender, "hologram.orphans_cleared", ph);
        return 1;
    }

    /**
     * Parses a hologram identifier of the form
     * {@code SmartSpawner-Holo-<world>-<x>-<y>-<z>} back into a {@link Location}.
     * Returns null if the name is malformed.
     */
    private Location parseLocationFromIdentifier(String name, World fallbackWorld) {
        try {
            String[] parts = name.split("-");
            // Expected: ["SmartSpawner", "Holo", "<world>", "<x>", "<y>", "<z>"]
            if (parts.length < 6) return null;

            // World names can contain dashes, so reconstruct from the middle
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