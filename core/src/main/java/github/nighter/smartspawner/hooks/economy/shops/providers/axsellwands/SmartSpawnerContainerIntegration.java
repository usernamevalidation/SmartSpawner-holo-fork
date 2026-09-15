package github.nighter.smartspawner.hooks.economy.shops.providers.axsellwands;

import com.artillexstudios.axsellwands.libs.axintegrations.types.ContainerIntegration;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers SmartSpawner blocks as sellable containers for AxSellWands.
 * AxSellWands will call isContainer() on right-click with a sellwand,
 * then getContents() to price and sell the items.
 */
public class SmartSpawnerContainerIntegration extends ContainerIntegration {

    public SmartSpawnerContainerIntegration() {
        super("SmartSpawner");
    }

    @Override
    public boolean canLoad() {
        return SmartSpawner.getInstance() != null;
    }

    @Override
    public boolean isContainer(@NotNull Block block) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null || plugin.getSpawnerManager() == null) return false;
        return plugin.getSpawnerManager().getSpawnerByLocation(block.getLocation()) != null;
    }

    @NotNull
    @Override
    public List<ItemStack> getContents(@NotNull Block block) {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null || plugin.getSpawnerManager() == null) return new ArrayList<>();

        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerByLocation(block.getLocation());
        if (spawner == null) return new ArrayList<>();

        SpawnerBackedItemList list = new SpawnerBackedItemList(spawner);

        // Schedule the sync one tick later, after AxSellWands finishes iterating.
        // Right-click sells items (sets amount to 0); left-click inspects (no mutation).
        // The delayed task will only remove items that ended up at amount 0.
        Scheduler.runLocationTaskLater(spawner.getSpawnerLocation(), list::syncAfterSale, 1L);

        return list;
    }
}