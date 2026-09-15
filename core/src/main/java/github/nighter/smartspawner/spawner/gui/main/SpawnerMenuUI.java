package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.spawner.properties.ItemSignature;
import net.kyori.adventure.text.Component;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.api.events.SpawnerOpenGUIEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

public class SpawnerMenuUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    private static final String LOOT_ITEM_FORMAT_KEY = "spawner_storage_item.loot_items";
    private static final String EMPTY_LOOT_MESSAGE_KEY = "spawner_storage_item.loot_items_empty";

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    private String lootItemFormat;
    private String emptyLootMessage;

    private Material cachedStorageMaterial = Material.CHEST;
    private Material cachedExpMaterial = Material.EXPERIENCE_BOTTLE;

    private final Map<String, ItemStack> itemCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_TIME_MS = 30000;

    public SpawnerMenuUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        loadConfig();
    }

    public void loadConfig() {
        clearCache();
        this.lootItemFormat = languageManager.getGuiItemName(LOOT_ITEM_FORMAT_KEY, EMPTY_PLACEHOLDERS);
        this.emptyLootMessage = languageManager.getGuiItemName(EMPTY_LOOT_MESSAGE_KEY, EMPTY_PLACEHOLDERS);

        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();

        Material storageMaterial = Material.CHEST;
        Material expMaterial = Material.EXPERIENCE_BOTTLE;

        for (GuiButton button : layout.getAllButtons().values()) {
            String action = button.getDefaultAction();
            if (action == null) continue;

            if ("open_storage".equals(action)) {
                storageMaterial = button.getMaterial();
            } else if ("collect_exp".equals(action)) {
                expMaterial = button.getMaterial();
            }
        }

        this.cachedStorageMaterial = storageMaterial;
        this.cachedExpMaterial = expMaterial;
    }

    public void clearCache() {
        itemCache.clear();
        cacheTimestamps.clear();
    }

    public void invalidateSpawnerCache(String spawnerId) {
        itemCache.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
        cacheTimestamps.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
    }

    private boolean isCacheEntryExpired(String cacheKey) {
        Long timestamp = cacheTimestamps.get(cacheKey);
        return timestamp == null || System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME_MS;
    }

    public void openSpawnerMenu(Player player, SpawnerData spawner, boolean refresh) {
        if (SpawnerOpenGUIEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerOpenGUIEvent openEvent = new SpawnerOpenGUIEvent(
                    player,
                    spawner.getSpawnerLocation(),
                    spawner.getEntityType(),
                    spawner.getStackSize(),
                    refresh
            );
            Bukkit.getPluginManager().callEvent(openEvent);

            if (openEvent.isCancelled()) {
                return;
            }
        }

        GuiLayout layout = plugin.getGuiLayoutConfig().getMainLayout(spawner, player);
        Inventory menu = createMenu(spawner, layout);

        ItemStack[] items = new ItemStack[INVENTORY_SIZE];

        for (GuiButton button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            if (button.hasCondition() && !evaluateButtonCondition(button, player)) {
                continue;
            }

            ItemStack item;
            if (button.isInfoButton()) {
                item = createSpawnerInfoItem(player, spawner, button);
            } else {
                String action = getAnyActionFromButton(button);
                if (action == null || action.isEmpty()) {
                    action = "none";
                }
                plugin.getLogger().info("[DEBUG] SELLWAND CHECK slot=" + button.getSlot() + " material=" + button.getMaterial() + " action=" + action);
                switch (action) {
                    case "open_storage":
                        item = createLootStorageItem(spawner, button);
                        break;
                    case "collect_exp":
                        item = createExpItem(spawner, button);
                        break;
                    case "sell_and_exp":
                        item = createSpawnerInfoItem(player, spawner, button);
                        break;
                    case "toggle_auto_sell":
                        item = createAutoSellToggleItem(player, button);
                        break;
                    case "open_sellwand_gui":
                        item = createSellwandButtonItem(player, button);
                        break;
                    case "none":
                        item = createStaticItem(button);
                        break;
                    default:
                        item = createStaticItem(button);
                        break;
                }
            }

            if (item != null) {
                items[button.getSlot()] = item;
            }
        }

        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                menu.setItem(i, items[i]);
            }
        }

        player.openInventory(menu);

        if (!refresh) {
            plugin.getGuiButtonInteractionService().playOpenSound(player);
        }

        if (plugin.getSpawnerGuiViewManager().isTimerPlaceholdersEnabled()
                && spawner.getSpawnerStop().get()) {
            plugin.getSpawnerGuiViewManager().forceTimerUpdateInactive(player, spawner);
        }
    }

    private Inventory createMenu(SpawnerData spawner, GuiLayout layout) {
        String entityName;
        if (spawner.isItemSpawner()) {
            entityName = languageManager.getVanillaItemName(spawner.getSpawnedItemMaterial());
        } else {
            entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        }
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);

        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        String title;
        if (spawner.getStackSize() > 1) {
            title = languageManager.getGuiTitle("gui_title_main.stacked_spawner", placeholders);
        } else {
            title = languageManager.getGuiTitle("gui_title_main.single_spawner", placeholders);
        }

        return Bukkit.createInventory(new SpawnerMenuHolder(spawner, layout), INVENTORY_SIZE, title);
    }

    public ItemStack createLootStorageItem(SpawnerData spawner, GuiButton button) {
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        String buttonConfig = (button != null) ? (button.getMaterial().name() + "_" + button.getCustomTexture()) : "default";
        String cacheKey = spawner.getSpawnerId() + "|storage|" + currentItems + "|" + maxSlots + "|" + virtualInventory.hashCode() + "|" + buttonConfig;

        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

        String nameTemplate = languageManager.getGuiItemName("spawner_storage_item.name", EMPTY_PLACEHOLDERS);
        List<String> loreTemplate = languageManager.getGuiItemLoreAsList("spawner_storage_item.lore", EMPTY_PLACEHOLDERS);

        Set<String> availablePlaceholders = Set.of(
            "max_slots", "current_items", "percent_storage_rounded", "total_sell_price", "loot_items"
        );

        Set<String> usedPlaceholders = new HashSet<>();
        usedPlaceholders.addAll(detectUsedPlaceholders(nameTemplate, availablePlaceholders));
        usedPlaceholders.addAll(detectUsedPlaceholders(loreTemplate, availablePlaceholders));

        Map<String, String> placeholders = new HashMap<>();

        if (usedPlaceholders.contains("max_slots")) {
            placeholders.put("max_slots", languageManager.formatNumber(maxSlots));
        }
        if (usedPlaceholders.contains("current_items")) {
            placeholders.put("current_items", String.valueOf(currentItems));
        }
        if (usedPlaceholders.contains("percent_storage_rounded")) {
            int percentStorage = calculatePercentage(currentItems, maxSlots);
            placeholders.put("percent_storage_rounded", String.valueOf(percentStorage));
        }
        if (usedPlaceholders.contains("total_sell_price")) {
            if (spawner.isSellValueDirty()) {
                spawner.recalculateSellValue();
            }
            placeholders.put("total_sell_price", languageManager.formatNumber(spawner.getAccumulatedSellValue()));
        }
        final List<Component> finalLootComponents = usedPlaceholders.contains("loot_items")
                ? buildLootItemComponents(spawner, virtualInventory.getConsolidatedItems())
                : Collections.emptyList();

        Consumer<ItemMeta> metaModifier = meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("spawner_storage_item.name", placeholders));
            List<Component> lore = languageManager.buildGuiLoreAsComponents(
                    "spawner_storage_item.lore", placeholders, finalLootComponents, EMPTY_LOOT_MESSAGE_KEY);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        };

        ItemStack chestItem;
        if (button != null && button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            chestItem = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else {
            Material mat = (button != null) ? button.getMaterial() : cachedStorageMaterial;
            chestItem = new ItemStack(mat);
            chestItem.editMeta(metaModifier);
        }

        if (chestItem.getType() == Material.BUNDLE) {
            ItemTooltipUtil.hideTooltip(chestItem);
        }

        itemCache.put(cacheKey, chestItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return chestItem;
    }

    private String buildLootItemsText(EntityType entityType, Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getMaterial();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
        }

        EntityLootConfig lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        List<LootItem> possibleLootItems = lootConfig != null
                ? lootConfig.getAllItems()
                : Collections.emptyList();

        if (possibleLootItems.isEmpty() && storedItems.isEmpty()) {
            return emptyLootMessage;
        }

        StringBuilder builder = new StringBuilder(Math.max(possibleLootItems.size(), storedItems.size()) * 40);

        if (!possibleLootItems.isEmpty()) {
            possibleLootItems.sort(Comparator.comparing(item -> languageManager.getVanillaItemName(item.material())));

            for (LootItem lootItem : possibleLootItems) {
                Material material = lootItem.material();
                long amount = materialAmountMap.getOrDefault(material, 0L);

                String materialName = languageManager.getVanillaItemName(material);
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.chance()) + "%";

                String line = lootItemFormat
                        .replace("{item_name}", materialName)
                        .replace("{amount}", formattedAmount)
                        .replace("{raw_amount}", String.valueOf(amount))
                        .replace("{chance}", chance);

                builder.append(line).append('\n');
            }
        } else if (!storedItems.isEmpty()) {
            List<Map.Entry<ItemSignature, Long>> sortedItems =
                    new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));

            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material material = entry.getKey().getMaterial();
                long amount = entry.getValue();

                String materialName = languageManager.getVanillaItemName(material);
                String formattedAmount = languageManager.formatNumber(amount);

                String line = lootItemFormat
                        .replace("{item_name}", materialName)
                        .replace("{amount}", formattedAmount)
                        .replace("{raw_amount}", String.valueOf(amount))
                        .replace("{chance}", "");

                builder.append(line).append('\n');
            }
        }

        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == '\n') {
            builder.setLength(length - 1);
        }

        return builder.toString();
    }

    public ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner, GuiButton button) {
        EntityType entityType = spawner.getEntityType();
        int stackSize = spawner.getStackSize();
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();

        boolean hasShopPermission = plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");
        String buttonConfig = (button != null) ? (button.getMaterial().name() + "_" + button.getCustomTexture()) : "default";
        String cacheKey = spawner.getSpawnerId() + "|info|" + currentItems + "|" + maxSlots + "|" + currentExp + "|" + maxExp + "|" + hasShopPermission + "|" + buttonConfig;

        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            ItemStack result = cachedItem.clone();
            applyTimerPlaceholder(result, spawner, player);
            return result;
        }

        String nameTemplate = languageManager.getGuiItemName("spawner_info_item.name", EMPTY_PLACEHOLDERS);
        String loreKey = hasShopPermission ? "spawner_info_item.lore" : "spawner_info_item.lore_no_shop";
        List<String> loreTemplate = languageManager.getGuiItemLoreAsList(loreKey, EMPTY_PLACEHOLDERS);

        Set<String> availablePlaceholders = Set.of(
            "entity", "ᴇɴᴛɪᴛʏ", "stack_size", "range", "delay", "min_mobs", "max_mobs",
            "current_items", "max_items", "percent_storage_decimal", "percent_storage_rounded",
            "current_exp", "max_exp", "raw_current_exp", "raw_max_exp", "percent_exp_decimal", "percent_exp_rounded",
            "total_sell_price", "time"
        );

        Set<String> usedPlaceholders = new HashSet<>();
        usedPlaceholders.addAll(detectUsedPlaceholders(nameTemplate, availablePlaceholders));
        usedPlaceholders.addAll(detectUsedPlaceholders(loreTemplate, availablePlaceholders));

        Map<String, String> placeholders = new HashMap<>();

        if (usedPlaceholders.contains("entity") || usedPlaceholders.contains("ᴇɴᴛɪᴛʏ")) {
            String entityName;
            if (spawner.isItemSpawner()) {
                entityName = languageManager.getVanillaItemName(spawner.getSpawnedItemMaterial());
            } else {
                entityName = languageManager.getFormattedMobName(entityType);
            }
            if (usedPlaceholders.contains("entity")) {
                placeholders.put("entity", entityName);
            }
            if (usedPlaceholders.contains("ᴇɴᴛɪᴛʏ")) {
                placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
            }
        }

        if (usedPlaceholders.contains("stack_size")) {
            placeholders.put("stack_size", String.valueOf(stackSize));
        }

        if (usedPlaceholders.contains("range")) {
            placeholders.put("range", String.valueOf(spawner.getSpawnerRange()));
        }
        if (usedPlaceholders.contains("delay")) {
            long delaySeconds = spawner.getSpawnDelay() / TICKS_PER_SECOND;
            placeholders.put("delay", String.valueOf(delaySeconds));
        }
        if (usedPlaceholders.contains("min_mobs")) {
            placeholders.put("min_mobs", String.valueOf(spawner.getMinMobs()));
        }
        if (usedPlaceholders.contains("max_mobs")) {
            placeholders.put("max_mobs", String.valueOf(spawner.getMaxMobs()));
        }

        if (usedPlaceholders.contains("current_items")) {
            placeholders.put("current_items", String.valueOf(currentItems));
        }
        if (usedPlaceholders.contains("max_items")) {
            placeholders.put("max_items", languageManager.formatNumber(maxSlots));
        }
        if (usedPlaceholders.contains("percent_storage_decimal") || usedPlaceholders.contains("percent_storage_rounded")) {
            double percentStorageDecimal = maxSlots > 0 ? ((double) currentItems / maxSlots) * 100 : 0;
            if (usedPlaceholders.contains("percent_storage_decimal")) {
                String formattedPercentStorage = String.format("%.1f", percentStorageDecimal);
                placeholders.put("percent_storage_decimal", formattedPercentStorage);
            }
            if (usedPlaceholders.contains("percent_storage_rounded")) {
                int percentStorageRounded = (int) Math.round(percentStorageDecimal);
                placeholders.put("percent_storage_rounded", String.valueOf(percentStorageRounded));
            }
        }

        if (usedPlaceholders.contains("current_exp")) {
            placeholders.put("current_exp", languageManager.formatNumber(currentExp));
        }
        if (usedPlaceholders.contains("max_exp")) {
            placeholders.put("max_exp", languageManager.formatNumber(maxExp));
        }
        if (usedPlaceholders.contains("raw_current_exp")) {
            placeholders.put("raw_current_exp", String.valueOf(currentExp));
        }
        if (usedPlaceholders.contains("raw_max_exp")) {
            placeholders.put("raw_max_exp", String.valueOf(maxExp));
        }
        if (usedPlaceholders.contains("percent_exp_decimal") || usedPlaceholders.contains("percent_exp_rounded")) {
            double percentExpDecimal = maxExp > 0 ? ((double) currentExp / maxExp) * 100 : 0;
            if (usedPlaceholders.contains("percent_exp_decimal")) {
                String formattedPercentExp = String.format("%.1f", percentExpDecimal);
                placeholders.put("percent_exp_decimal", formattedPercentExp);
            }
            if (usedPlaceholders.contains("percent_exp_rounded")) {
                int percentExpRounded = (int) Math.round(percentExpDecimal);
                placeholders.put("percent_exp_rounded", String.valueOf(percentExpRounded));
            }
        }

        if (usedPlaceholders.contains("total_sell_price")) {
            if (spawner.isSellValueDirty()) {
                spawner.recalculateSellValue();
            }
            double totalSellPrice = spawner.getAccumulatedSellValue();
            placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        }

        Consumer<ItemMeta> metaModifier = meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("spawner_info_item.name", placeholders));

            List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders(loreKey, placeholders);
            meta.setLore(lore);
        };

        ItemStack spawnerItem;

        if (spawner.isItemSpawner()) {
            spawnerItem = SpawnerMobHeadTexture.getItemSpawnerHead(spawner.getSpawnedItemMaterial(), player, metaModifier);
        } else if (button != null && button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            spawnerItem = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else if (button != null && button.getMaterial() == Material.PLAYER_HEAD) {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, player, metaModifier);
        } else if (button != null) {
            spawnerItem = new ItemStack(button.getMaterial());
            spawnerItem.editMeta(metaModifier);
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, player, metaModifier);
        }

        if (spawnerItem.getType() == Material.SPAWNER) ItemTooltipUtil.hideTooltip(spawnerItem);

        itemCache.put(cacheKey, spawnerItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        applyTimerPlaceholder(spawnerItem, spawner, player);

        return spawnerItem;
    }

    private void applyTimerPlaceholder(ItemStack item, SpawnerData spawner, Player player) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        boolean nameHasTimer = meta.hasDisplayName() && meta.getDisplayName().contains("{time}");
        List<String> lore = meta.hasLore() ? meta.getLore() : null;
        boolean loreHasTimer = false;
        if (lore != null) {
            for (String line : lore) {
                if (line.contains("{time}")) {
                    loreHasTimer = true;
                    break;
                }
            }
        }

        if (!nameHasTimer && !loreHasTimer) {
            return;
        }

        String timerValue = plugin.getSpawnerGuiViewManager().calculateTimerDisplay(spawner, player);

        if (nameHasTimer) {
            meta.setDisplayName(meta.getDisplayName().replace("{time}", timerValue));
        }
        if (loreHasTimer) {
            List<String> updatedLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                updatedLore.add(line.replace("{time}", timerValue));
            }
            meta.setLore(updatedLore);
        }
        item.setItemMeta(meta);
    }

    public ItemStack createExpItem(SpawnerData spawner, GuiButton button) {
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        int percentExp = calculatePercentage(currentExp, maxExp);

        String buttonConfig = (button != null) ? (button.getMaterial().name() + "_" + button.getCustomTexture()) : "default";
        String cacheKey = spawner.getSpawnerId() + "|exp|" + currentExp + "|" + maxExp + "|" + buttonConfig;

        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

        String formattedExp = languageManager.formatNumber(currentExp);
        String formattedMaxExp = languageManager.formatNumber(maxExp);

        Map<String, String> placeholders = new HashMap<>(5);
        placeholders.put("current_exp", formattedExp);
        placeholders.put("raw_current_exp", String.valueOf(currentExp));
        placeholders.put("max_exp", formattedMaxExp);
        placeholders.put("percent_exp_rounded", String.valueOf(percentExp));
        placeholders.put("u_max_exp", String.valueOf(maxExp));

        Consumer<ItemMeta> metaModifier = meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("exp_info_item.name", placeholders));
            List<String> loreExp = languageManager.getGuiItemLoreAsList("exp_info_item.lore", placeholders);
            meta.setLore(loreExp);
        };

        ItemStack expItem;
        if (button != null && button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            expItem = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else {
            Material mat = (button != null) ? button.getMaterial() : cachedExpMaterial;
            expItem = new ItemStack(mat);
            expItem.editMeta(metaModifier);
        }

        if (expItem.getType() == Material.BUNDLE) {
            ItemTooltipUtil.hideTooltip(expItem);
        }

        itemCache.put(cacheKey, expItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return expItem;
    }

    private ItemStack createAutoSellToggleItem(Player player, GuiButton button) {
        boolean enabled = github.nighter.smartspawner.spawner.gui.autosell.AutoSellPreferences.isEnabled(player);
        Material mat = enabled ? Material.LIME_DYE : button.getMaterial();
        ItemStack item = new ItemStack(mat);
        Map<String, String> placeholders = new HashMap<>(2);
        placeholders.put("state", enabled ? "ON" : "OFF");
        String nameKey = enabled ? "auto_sell_button.name_on" : "auto_sell_button.name_off";
        String loreKey = enabled ? "auto_sell_button.lore_on" : "auto_sell_button.lore_off";
        Consumer<ItemMeta> metaModifier = meta -> {
            meta.setDisplayName(languageManager.getGuiItemName(nameKey, placeholders));
            List<String> lore = languageManager.getGuiItemLoreAsList(loreKey, placeholders);
            meta.setLore(lore);
        };
        if (button.getMaterial() == Material.PLAYER_HEAD
                && button.getCustomTexture() != null
                && !button.getCustomTexture().trim().isEmpty()) {
            item = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else {
            item.editMeta(metaModifier);
        }
        return item;
    }

    private ItemStack createSellwandButtonItem(Player player, GuiButton button) {
        plugin.getLogger().info("[DEBUG] createSellwandButtonItem called, slot=" + button.getSlot() + ", material=" + button.getMaterial());
        ItemStack item = new ItemStack(button.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<String, String> ph = new HashMap<>(2);
            ph.put("multiplier", String.valueOf(github.nighter.smartspawner.commands.sellwand.SellwandStorage.getMultiplier(player)));
            ph.put("uses", languageManager.formatNumber(github.nighter.smartspawner.commands.sellwand.SellwandStorage.getUses(player)));

            boolean active = github.nighter.smartspawner.commands.sellwand.SellwandStorage.hasWand(player);
            String nameKey = active ? "sellwand_button.name_active" : "sellwand_button.name_inactive";
            String loreKey = active ? "sellwand_button.lore_active" : "sellwand_button.lore_inactive";

            String name = languageManager.getGuiItemName(nameKey, ph);
            plugin.getLogger().info("[DEBUG] createSellwandButtonItem nameKey=" + nameKey + " resolvedName=" + name);

            meta.setDisplayName(name);
            List<String> lore = languageManager.getGuiItemLoreAsList(loreKey, ph);
            if (!lore.isEmpty()) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        plugin.getLogger().info("[DEBUG] createSellwandButtonItem returning type=" + item.getType());
        return item;
    }

    private ItemStack createStaticItem(GuiButton button) {
        ItemStack item;
        if (button.getMaterial() == Material.PLAYER_HEAD
                && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            item = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), null);
        } else {
            item = new ItemStack(button.getMaterial());
        }
        if (item.getType() == Material.SPAWNER) {
            ItemTooltipUtil.hideTooltip(item);
        }
        return item;
    }

    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }

    private List<Component> buildLootItemComponents(SpawnerData spawner, Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getMaterial();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
        }

        EntityLootConfig lootConfig = spawner.getLootConfig();
        List<LootItem> possibleLootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();

        if (possibleLootItems.isEmpty() && storedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Component> components = new ArrayList<>();
        if (!possibleLootItems.isEmpty()) {
            possibleLootItems.sort(Comparator.comparing(item -> item.material().name()));
            for (LootItem lootItem : possibleLootItems) {
                Material material = lootItem.material();
                long amount = materialAmountMap.getOrDefault(material, 0L);
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.chance()) + "%";
                components.add(languageManager.buildTranslatableGuiLootLine(
                        LOOT_ITEM_FORMAT_KEY, lootItem.template(), formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems =
                    new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(languageManager.buildTranslatableGuiLootLine(
                        LOOT_ITEM_FORMAT_KEY, entry.getKey().getTemplate(), formattedAmount, ""));
            }
        }
        return components;
    }

    private Set<String> detectUsedPlaceholders(String text, Set<String> availablePlaceholders) {
        Set<String> usedPlaceholders = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return usedPlaceholders;
        }

        for (String placeholder : availablePlaceholders) {
            if (text.contains("{" + placeholder + "}")) {
                usedPlaceholders.add(placeholder);
            }
        }
        return usedPlaceholders;
    }

    private Set<String> detectUsedPlaceholders(List<String> textList, Set<String> availablePlaceholders) {
        Set<String> usedPlaceholders = new HashSet<>();
        if (textList == null || textList.isEmpty()) {
            return usedPlaceholders;
        }

        for (String text : textList) {
            for (String placeholder : availablePlaceholders) {
                if (text.contains("{" + placeholder + "}")) {
                    usedPlaceholders.add(placeholder);
                }
            }
        }
        return usedPlaceholders;
    }

    private boolean evaluateButtonCondition(GuiButton button, org.bukkit.entity.Player player) {
        String condition = button.getCondition();
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        switch (condition) {
            case "sell_integration":
                return plugin.hasSellIntegration();
            case "no_sell_integration":
                return !plugin.hasSellIntegration();
            default:
                plugin.getLogger().warning("Unknown button condition: " + condition);
                return true;
        }
    }

    private String getAnyActionFromButton(GuiButton button) {
        String action = button.getDefaultAction();
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = button.getAction("left_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = button.getAction("right_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        return null;
    }
}