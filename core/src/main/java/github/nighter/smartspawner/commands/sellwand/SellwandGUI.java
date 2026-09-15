package github.nighter.smartspawner.commands.sellwand;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellwandGUI {
    private static final int GUI_SIZE = 27;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;

    public SellwandGUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
    }

    public void open(Player player) {
        SellwandHolder holder = new SellwandHolder();
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE,
                languageManager.commandGui().title("gui_title_sellwand", java.util.Collections.emptyMap()));
        holder.setInventory(inv);

        // Fill background
        ItemStack filler = createFiller();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Drop slot — leave empty
        inv.setItem(holder.getDropSlot(), null);

        // Confirm button
        inv.setItem(holder.getConfirmSlot(), createConfirmButton(player));

        // Active wand preview
        inv.setItem(holder.getActiveSlot(), createActiveWandPreview(player));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public void refresh(Player player, Inventory inv) {
        if (!(inv.getHolder(false) instanceof SellwandHolder holder)) return;
        inv.setItem(holder.getConfirmSlot(), createConfirmButton(player));
        inv.setItem(holder.getActiveSlot(), createActiveWandPreview(player));
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createConfirmButton(Player player) {
        boolean alreadyHas = SellwandStorage.hasWand(player);
        Material mat = alreadyHas ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK;
        String key = alreadyHas ? "sellwand_confirm.already_active" : "sellwand_confirm.ready";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("multiplier", String.valueOf(SellwandStorage.getMultiplier(player)));
            ph.put("uses", languageManager.formatNumber(SellwandStorage.getUses(player)));
            meta.setDisplayName(languageManager.commandGui().name(key + ".name", ph));
            List<String> lore = languageManager.commandGui().loreList(key + ".lore", ph);
            if (!lore.isEmpty()) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createActiveWandPreview(Player player) {
        ItemStack item;
        ItemMeta meta;
        Map<String, String> ph = new HashMap<>();

        if (SellwandStorage.hasWand(player)) {
            item = new ItemStack(Material.BLAZE_ROD);
            meta = item.getItemMeta();
            if (meta != null) {
                ph.put("multiplier", String.valueOf(SellwandStorage.getMultiplier(player)));
                ph.put("uses", languageManager.formatNumber(SellwandStorage.getUses(player)));
                ph.put("wand_name", SellwandStorage.getDisplayName(player));
                meta.setDisplayName(languageManager.commandGui().name("sellwand_active.name", ph));
                List<String> lore = languageManager.commandGui().loreList("sellwand_active.lore", ph);
                if (!lore.isEmpty()) meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }
        } else {
            item = new ItemStack(Material.LIGHT_GRAY_DYE);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(languageManager.commandGui().name("sellwand_inactive.name", ph));
                List<String> lore = languageManager.commandGui().loreList("sellwand_inactive.lore", ph);
                if (!lore.isEmpty()) meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    public LanguageManager language() {
        return languageManager;
    }

    public MessageService messages() {
        return messageService;
    }

    public SmartSpawner plugin() {
        return plugin;
    }

    /**
     * Reads the wand's multiplier and uses from its lore based on config regexes.
     * Also enforces the "glow but no visible enchants" signature of real sellwands
     * so players can't fake one with a renamed item.
     */
    public WandData parseWandItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        // Anti-exploit: the item must visibly glow, but must NOT have visible enchantments.
        // Glow comes from either:
        //   1. Real enchants hidden behind HIDE_ENCHANTS flag (invisible enchants, still glows)
        //   2. Enchantment glint override set to true (glow with no enchants at all)
        //
        // Reject if the item has visible enchants — those are fake "wands" or normal tools.

        boolean hasHiddenEnchantsOnly = false;
        if (meta.hasEnchants()) {
            if (meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                hasHiddenEnchantsOnly = true;
            } else {
                return null;
            }
        }

        boolean hasGlintOverride = false;
        try {
            hasGlintOverride = meta.hasEnchantmentGlintOverride()
                    && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
        } catch (NoSuchMethodError e) {
            hasGlintOverride = false;
        }

        if (!hasHiddenEnchantsOnly && !hasGlintOverride) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        if (meta.hasDisplayName()) {
            lines.add(stripColor(meta.getDisplayName()));
        }
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line != null) lines.add(stripColor(line));
            }
        }

        double multiplier = 1.0;
        int uses = 0;
        boolean infinite = false;

        String multRegex = plugin.getConfig().getString("sellwand.multiplier_regex",
                "(?i)Multiplier:\\s*([0-9.]+)x");
        String usesRegex = plugin.getConfig().getString("sellwand.uses_regex",
                "(?i)Uses:\\s*([0-9]+|∞)");

        java.util.regex.Pattern multPattern = java.util.regex.Pattern.compile(multRegex);
        java.util.regex.Pattern usesPattern = java.util.regex.Pattern.compile(usesRegex);

        for (String line : lines) {
            java.util.regex.Matcher m = multPattern.matcher(line);
            if (m.find()) {
                try { multiplier = Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) {}
            }
            java.util.regex.Matcher u = usesPattern.matcher(line);
            if (u.find()) {
                String g = u.group(1);
                if ("∞".equals(g)) {
                    infinite = true;
                } else {
                    try { uses = Integer.parseInt(g); } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Reject unlimited-use wands — they'd never deplete and the sacrifice would be pointless
        if (infinite || uses < 0) {
            return null;
        }

        // Reject items that don't look like wands
        if (multiplier <= 1.0 && uses == 0) {
            return null;
        }

        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
        return new WandData(multiplier, uses, displayName);
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .replaceAll("(?i)&#[0-9A-F]{6}", "")
                .replaceAll("(?i)&[0-9A-FK-ORX]", "");
    }

    public static class WandData {
        public final double multiplier;
        public final int uses;
        public final String displayName;

        public WandData(double multiplier, int uses, String displayName) {
            this.multiplier = multiplier;
            this.uses = uses;
            this.displayName = displayName;
        }
    }
}