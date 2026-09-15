package github.nighter.smartspawner.spawner.gui.autosell;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Per-player auto-sell preference, persisted in the player's PDC so it survives restarts
 * and is not bound to any world or session. Default is disabled.
 */
public final class AutoSellPreferences {

    /** Permission node required to toggle auto-sell and to have it fire. */
    public static final String PERMISSION = "smartspawner.autosell";

    private static final NamespacedKey KEY = new NamespacedKey("smartspawner", "auto_sell_enabled");

    private AutoSellPreferences() {}

    public static boolean isEnabled(Player player) {
        if (player == null) return false;
        Byte value = player.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public static void setEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
    }

    public static boolean toggle(Player player) {
        boolean newState = !isEnabled(player);
        setEnabled(player, newState);
        return newState;
    }

    public static boolean hasPermission(Player player) {
        return player != null && player.hasPermission(PERMISSION);
    }

    /**
     * Whether auto-sell should currently run for this player.
     * <p>
     * If the stored preference is enabled but the player no longer has the
     * {@link #PERMISSION}, the stored flag is cleared so the GUI reflects OFF
     * and auto-sell does not run. This means losing the permission turns auto-sell
     * off for the player, and regaining the permission requires re-toggling.
     */
    public static boolean isActive(Player player) {
        if (!isEnabled(player)) return false;
        if (!hasPermission(player)) {
            setEnabled(player, false);
            return false;
        }
        return true;
    }
}