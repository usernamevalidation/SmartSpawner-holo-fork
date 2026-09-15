package github.nighter.smartspawner.commands.sellwand;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@RequiredArgsConstructor
public class SellwandHolder implements InventoryHolder {

    @Getter
    private final int dropSlot = 11;
    @Getter
    private final int confirmSlot = 13;
    @Getter
    private final int activeSlot = 15;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}