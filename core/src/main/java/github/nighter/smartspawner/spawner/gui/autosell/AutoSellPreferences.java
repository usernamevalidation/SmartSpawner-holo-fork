package github.nighter.smartspawner.spawner.gui.autosell;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Per-player auto-sell preference, persisted in the player's PDC so it survives restarts
 * and is not bound to any world or session. Default is disabled.
 */
public final class AutoSellPreferences {

    private static final NamespacedKey KEY = new NamespacedKey("smartspawner", "auto_sell_enabled");

    private AutoSellPreferences() {}

    /**
     * @return true if the player has auto-sell enabled
     */
    public static boolean isEnabled(Player player) {
        if (player == null) return false;
        Byte value = player.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    /**
     * Enables or disables auto-sell for the player.
     */
    public static void setEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
    }

    /**
     * Flips the current value and returns the new state.
     */
    public static boolean toggle(Player player) {
        boolean newState = !isEnabled(player);
        setEnabled(player, newState);
        return newState;
    }
}