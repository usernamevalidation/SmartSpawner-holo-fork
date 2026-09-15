package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckSell {
    public static boolean CanPlayerSell(@NotNull final Player player, @NotNull Location location) {
        if (player.isOp() || player.hasPermission("*")) return true;

        boolean debug = SmartSpawner.getInstance().getConfig().getBoolean("debug", false);

        for (ProtectionHook hook : SmartSpawner.getInstance().getIntegrationManager().getProtectionHooks()) {
            if (!hook.canSell(player, location)) {
                if (debug) {
                    SmartSpawner.getInstance().getLogger().info("[DEBUG] CheckSell: DENIED player="
                            + player.getName()
                            + " loc=" + formatLoc(location)
                            + " hook=" + hook.getClass().getSimpleName());
                }
                return false;
            }
        }
        return true;
    }

    private static String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "?";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}