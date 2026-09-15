package github.nighter.smartspawner.hooks.economy.shops.providers;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ShopProvider {

    String getPluginName();

    boolean isAvailable();

    double getSellPrice(Material material);

    /**
     * Gets the sell price a specific player would receive for the given material.
     * <p>
     * Providers that support player-aware pricing (e.g. EconomyShopGUI Premium's
     * rank or permission multipliers) should override this and return the
     * player-adjusted unit price.
     * <p>
     * The default implementation returns {@code 0.0}, which
     * {@link github.nighter.smartspawner.hooks.economy.shops.ShopIntegrationManager}
     * interprets as "no player-aware price available" and falls back to
     * {@link #getSellPrice(Material)}. This keeps providers that don't care
     * about the player working without changes.
     *
     * @param material the material to price
     * @param player the player the price is for
     * @return the player-aware unit price, or {@code 0.0} to signal "use the non-player price"
     */
    default double getSellPrice(Material material, Player player) {
        return 0.0;
    }
}