package github.nighter.smartspawner.commands.sellwand;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SellwandListener implements Listener {

    private final SmartSpawner plugin;
    private final SellwandGUI gui;
    private final MessageService messageService;

    public SellwandListener(SmartSpawner plugin, SellwandGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.messageService = plugin.getMessageService();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SellwandHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int raw = event.getRawSlot();
        int topSize = event.getInventory().getSize();

        // Let the player's inventory behave normally
        if (raw < 0 || raw >= topSize) {
            return;
        }

        int dropSlot = holder.getDropSlot();
        int confirmSlot = holder.getConfirmSlot();
        int activeSlot = holder.getActiveSlot();

        // Drop slot — always allowed, no cancellation
        if (raw == dropSlot) {
            return;
        }

        // Everything else in the GUI is read-only
        event.setCancelled(true);

        if (raw == confirmSlot) {
            handleConfirm(player, holder);
        } else if (raw == activeSlot) {
            if (SellwandStorage.hasWand(player)) {
                SellwandStorage.clear(player);
                messageService.sendMessage(player, "sellwand.cleared");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
                gui.refresh(player, event.getInventory());
            }
        }
    }

    private void handleConfirm(Player player, SellwandHolder holder) {
        if (SellwandStorage.hasWand(player)) {
            messageService.sendMessage(player, "sellwand.already_active");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            return;
        }

        Inventory inv = holder.getInventory();
        ItemStack dropped = inv.getItem(holder.getDropSlot());

        if (dropped == null || dropped.getType().isAir()) {
            messageService.sendMessage(player, "sellwand.no_wand");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            return;
        }

        SellwandGUI.WandData data = gui.parseWandItem(dropped);
        if (data == null) {
            messageService.sendMessage(player, "sellwand.invalid_wand");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            return;
        }

        // Consume the wand
        inv.setItem(holder.getDropSlot(), null);

        // Store its effect
        SellwandStorage.store(player, data.multiplier, data.uses, data.displayName);

        Map<String, String> ph = new HashMap<>();
        ph.put("multiplier", String.valueOf(data.multiplier));
        ph.put("uses", plugin.getLanguageManager().formatNumber(data.uses));
        messageService.sendMessage(player, "sellwand.sacrificed", ph);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

        gui.refresh(player, inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SellwandHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // If they close with an item still in the drop slot, return it to them
        ItemStack dropped = holder.getInventory().getItem(holder.getDropSlot());
        if (dropped != null && !dropped.getType().isAir()) {
            holder.getInventory().setItem(holder.getDropSlot(), null);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(dropped);
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }
}