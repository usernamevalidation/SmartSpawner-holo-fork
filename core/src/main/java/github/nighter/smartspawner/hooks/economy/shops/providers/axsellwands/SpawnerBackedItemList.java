package github.nighter.smartspawner.hooks.economy.shops.providers.axsellwands;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import org.bukkit.inventory.ItemStack;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A List&lt;ItemStack&gt; view backed by a spawner's virtual inventory.
 * AxSellWands iterates this list and calls ItemStack.setAmount(0) on items it
 * sold. Since those ItemStacks are our own clones, we need to sync the
 * resulting amounts back into the spawner afterwards.
 */
public class SpawnerBackedItemList extends AbstractList<ItemStack> {

    private final List<ItemStack> backing = new ArrayList<>();
    private final List<ItemSignature> signatures = new ArrayList<>();
    private final SpawnerData spawner;

    public SpawnerBackedItemList(SpawnerData spawner) {
        this.spawner = spawner;
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        if (virtualInv == null) return;

        for (Map.Entry<ItemSignature, Long> entry : virtualInv.getConsolidatedItems().entrySet()) {
            long amount = entry.getValue();
            if (amount <= 0) continue;
            ItemStack stack = entry.getKey().getTemplate().clone();
            stack.setAmount((int) Math.min(amount, Integer.MAX_VALUE));
            backing.add(stack);
            signatures.add(entry.getKey());
        }
    }

    @Override
    public ItemStack get(int index) {
        return backing.get(index);
    }

    @Override
    public int size() {
        return backing.size();
    }

    /**
     * Called one tick after getContents() returns, by which point AxSellWands
     * has iterated the list and set amounts to 0 for the items it sold.
     * Builds a map of sold signatures and removes them from the spawner.
     */
    public void syncAfterSale() {
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) return;

        Scheduler.runLocationTask(spawner.getSpawnerLocation(), () -> {
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv == null) return;

            Map<ItemSignature, Long> toRemove = new HashMap<>();
            for (int i = 0; i < backing.size(); i++) {
                ItemStack after = backing.get(i);
                if (after == null || after.getAmount() <= 0) {
                    ItemSignature sig = signatures.get(i);
                    long originalAmount = virtualInv.getConsolidatedItems()
                            .getOrDefault(sig, 0L);
                    if (originalAmount > 0) {
                        toRemove.put(sig, originalAmount);
                    }
                }
            }

            if (toRemove.isEmpty()) return;

            virtualInv.removeItems(toRemove);
            spawner.updateHologramData();
            spawner.recalculateSellValue();
            plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
        });
    }
}