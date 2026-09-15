package github.nighter.smartspawner.spawner.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerSellEvent;
import github.nighter.smartspawner.commands.sellwand.SellwandStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class SellwandSellListener implements Listener {

    private final SmartSpawner plugin;

    public SellwandSellListener(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSell(SpawnerSellEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (!SellwandStorage.hasWand(player)) return;

        double multiplier = SellwandStorage.getMultiplier(player);
        if (multiplier <= 1.0) return;

        double original = event.getMoneyAmount();
        double boosted = original * multiplier;
        event.setMoneyAmount(boosted);

        boolean stillActive = SellwandStorage.consumeUse(player);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] Sellwand applied: player=" + player.getName()
                    + " mult=" + multiplier
                    + " " + original + " -> " + boosted
                    + " usesRemaining=" + SellwandStorage.getUses(player)
                    + (stillActive ? "" : " (wand consumed)"));
        }
    }
}