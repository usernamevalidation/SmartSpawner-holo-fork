package github.nighter.smartspawner.commands.sellwand;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Per-player sellwand storage. The wand itself is consumed when placed;
 * only its multiplier, remaining uses, and a display name are persisted in the player's PDC.
 */
public final class SellwandStorage {

    private static final NamespacedKey KEY_MULTIPLIER = new NamespacedKey("smartspawner", "sellwand_multiplier");
    private static final NamespacedKey KEY_USES = new NamespacedKey("smartspawner", "sellwand_uses");
    private static final NamespacedKey KEY_NAME = new NamespacedKey("smartspawner", "sellwand_name");

    private SellwandStorage() {}

    public static boolean hasWand(Player player) {
        if (player == null) return false;
        Integer uses = player.getPersistentDataContainer().get(KEY_USES, PersistentDataType.INTEGER);
        return uses != null && uses > 0;
    }

    public static double getMultiplier(Player player) {
        if (player == null) return 1.0;
        Double m = player.getPersistentDataContainer().get(KEY_MULTIPLIER, PersistentDataType.DOUBLE);
        return m != null ? m : 1.0;
    }

    public static int getUses(Player player) {
        if (player == null) return 0;
        Integer u = player.getPersistentDataContainer().get(KEY_USES, PersistentDataType.INTEGER);
        return u != null ? u : 0;
    }

    public static String getDisplayName(Player player) {
        if (player == null) return "";
        String n = player.getPersistentDataContainer().get(KEY_NAME, PersistentDataType.STRING);
        return n != null ? n : "";
    }

    public static void store(Player player, double multiplier, int uses, String displayName) {
        if (player == null) return;
        player.getPersistentDataContainer().set(KEY_MULTIPLIER, PersistentDataType.DOUBLE, multiplier);
        player.getPersistentDataContainer().set(KEY_USES, PersistentDataType.INTEGER, uses);
        player.getPersistentDataContainer().set(KEY_NAME, PersistentDataType.STRING, displayName == null ? "" : displayName);
    }

    /**
     * Decrements uses by one. Clears the wand entirely when uses reach zero.
     *
     * @return true if the wand is still active after the decrement, false if it was consumed
     */
    public static boolean consumeUse(Player player) {
        if (player == null) return false;
        int uses = getUses(player);
        if (uses <= 0) {
            clear(player);
            return false;
        }
        uses--;
        if (uses <= 0) {
            clear(player);
            return false;
        }
        player.getPersistentDataContainer().set(KEY_USES, PersistentDataType.INTEGER, uses);
        return true;
    }

    public static void clear(Player player) {
        if (player == null) return;
        player.getPersistentDataContainer().remove(KEY_MULTIPLIER);
        player.getPersistentDataContainer().remove(KEY_USES);
        player.getPersistentDataContainer().remove(KEY_NAME);
    }
}