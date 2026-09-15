package github.nighter.smartspawner.api;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.data.SpawnerDataModifier;
import github.nighter.smartspawner.api.gui.GuiLayoutRegistry;
import github.nighter.smartspawner.api.gui.GuiLayoutRegistryImpl;
import github.nighter.smartspawner.api.gui.SpawnerGuiLayoutProvider;
import github.nighter.smartspawner.api.impl.SpawnerDataModifierImpl;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerRemovalService;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of the SmartSpawnerAPI interface.
 */
public class SmartSpawnerAPIImpl implements SmartSpawnerAPI {

    private final SmartSpawner plugin;
    private final SpawnerItemFactory itemFactory;
    private final SpawnerManager spawnerManager;
    private final SpawnerRemovalService spawnerRemovalService;
    private final GuiLayoutRegistryImpl guiLayoutRegistry;
    private final NamespacedKey vanillaSpawnerKey;
    private final NamespacedKey itemSpawnerMaterialKey;
    private volatile SpawnerGuiLayoutProvider spawnerGuiLayoutProvider;

    public SmartSpawnerAPIImpl(SmartSpawner plugin) {
        this.plugin = plugin;
        this.itemFactory = new SpawnerItemFactory(plugin);
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerRemovalService = plugin.getSpawnerRemovalService();
        this.guiLayoutRegistry = plugin.getGuiLayoutRegistry();
        this.vanillaSpawnerKey = new NamespacedKey(plugin, "vanilla_spawner");
        this.itemSpawnerMaterialKey = new NamespacedKey(plugin, "item_spawner_material");
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType) {
        return itemFactory.createSmartSpawnerItem(entityType);
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType, int amount) {
        return itemFactory.createSmartSpawnerItem(entityType, amount);
    }

    @Override
    public ItemStack createVanillaSpawnerItem(EntityType entityType) {
        return itemFactory.createVanillaSpawnerItem(entityType);
    }

    @Override
    public ItemStack createVanillaSpawnerItem(EntityType entityType, int amount) {
        return itemFactory.createVanillaSpawnerItem(entityType, amount);
    }

    @Override
    public ItemStack createItemSpawnerItem(Material itemMaterial) {
        return itemFactory.createItemSpawnerItem(itemMaterial);
    }

    @Override
    public ItemStack createItemSpawnerItem(Material itemMaterial, int amount) {
        return itemFactory.createItemSpawnerItem(itemMaterial, amount);
    }

    @Override
    public boolean isSmartSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return false;
        }
        return !hasVanillaSpawnerKey(item) && !hasItemSpawnerKey(item);
    }

    @Override
    public boolean isVanillaSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return false;
        }
        return hasVanillaSpawnerKey(item);
    }

    @Override
    public boolean isItemSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return false;
        }
        return hasItemSpawnerKey(item);
    }

    @Override
    public EntityType getSpawnerEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) {
            return null;
        }

        BlockState blockState = blockMeta.getBlockState();
        if (!(blockState instanceof CreatureSpawner cs)) {
            return null;
        }

        return cs.getSpawnedType();
    }

    @Override
    public Material getItemSpawnerMaterial(ItemStack item) {
        if (!isItemSpawner(item)) {
            return null;
        }

        String materialName = item.getPersistentDataContainer()
                .get(itemSpawnerMaterialKey, PersistentDataType.STRING);

        if (materialName == null) {
            return null;
        }

        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public SpawnerDataDTO getSpawnerByLocation(Location location) {
        if (location == null) {
            return null;
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(location);
        return spawnerData != null ? convertToDTO(spawnerData) : null;
    }

    @Override
    public SpawnerDataDTO getSpawnerById(String spawnerId) {
        if (spawnerId == null) {
            return null;
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        return spawnerData != null ? convertToDTO(spawnerData) : null;
    }

    @Override
    public List<SpawnerDataDTO> getAllSpawners() {
        return spawnerManager.getAllSpawners().stream()
                .map(SmartSpawnerAPIImpl::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<SpawnerDataDTO> getLoadedSpawners() {
        List<SpawnerDataDTO> result = spawnerManager.getAllSpawners().stream()
                .filter(spawner -> {
                    Location loc = spawner.getSpawnerLocation();
                    return loc != null
                            && loc.getWorld() != null
                            && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                })
                .map(SmartSpawnerAPIImpl::convertToDTO)
                .collect(Collectors.toList());

        if (debug()) {
            plugin.getLogger().info("[DEBUG] API.getLoadedSpawners -> " + result.size()
                    + " loaded of " + spawnerManager.getAllSpawners().size() + " total");
        }
        return result;
    }

    @Override
    public double getSpawnerSellValue(String spawnerId) {
        if (spawnerId == null) {
            return 0.0;
        }
        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        if (spawnerData == null) {
            return 0.0;
        }
        double value = spawnerData.getAccumulatedSellValue();
        if (debug()) {
            plugin.getLogger().info("[DEBUG] API.getSpawnerSellValue id=" + spawnerId + " -> " + value);
        }
        return value;
    }

    @Override
    public double getSpawnerSellValue(Location location) {
        if (location == null) {
            return 0.0;
        }
        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(location);
        if (spawnerData == null) {
            return 0.0;
        }
        double value = spawnerData.getAccumulatedSellValue();
        if (debug()) {
            plugin.getLogger().info("[DEBUG] API.getSpawnerSellValue loc=" + formatLoc(location) + " -> " + value);
        }
        return value;
    }

    // NEW
    @Override
    public double getSpawnerSellValue(String spawnerId, Player player) {
        if (spawnerId == null || player == null) {
            return 0.0;
        }
        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        return computePlayerAwareValue(spawnerData, player, "id=" + spawnerId);
    }

    // NEW
    @Override
    public double getSpawnerSellValue(Location location, Player player) {
        if (location == null || player == null) {
            return 0.0;
        }
        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(location);
        return computePlayerAwareValue(spawnerData, player, "loc=" + formatLoc(location));
    }

    // NEW
    /**
     * Computes the sell value for the stored items, using player-aware prices when
     * the active shop integration exposes them.
     * <p>
     * Implementation note: we iterate the virtual inventory's consolidated item
     * signatures and ask {@link ItemPriceManager} for the player-aware unit price,
     * falling back to the stored accumulated value if the price manager can't
     * resolve a player-aware price for a given item.
     */
    private double computePlayerAwareValue(SpawnerData spawnerData, Player player, String debugTag) {
        if (spawnerData == null) {
            if (debug()) {
                plugin.getLogger().info("[DEBUG] API.getSpawnerSellValue " + debugTag + " -> spawner not found");
            }
            return 0.0;
        }

        ItemPriceManager priceManager = plugin.getItemPriceManager();
        VirtualInventory virtualInv = spawnerData.getVirtualInventory();

        double value;
        if (priceManager == null || virtualInv == null) {
            value = spawnerData.getAccumulatedSellValue();
        } else {
            Map<ItemSignature, Long> items = virtualInv.getConsolidatedItems();
            double sum = 0.0;
            for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
                Material material = entry.getKey().getMaterial();
                long amount = entry.getValue();
                if (amount <= 0) {
                    continue;
                }
                double unit = priceManager.getPrice(material, player);
                if (unit <= 0.0) {
                    unit = priceManager.getPrice(material);
                }
                sum += unit * amount;
            }
            value = sum;
        }

        if (debug()) {
            plugin.getLogger().info("[DEBUG] API.getSpawnerSellValue " + debugTag
                    + " player=" + player.getName() + " -> " + value);
        }
        return value;
    }

    @Override
    public CompletableFuture<Double> sellSpawner(String spawnerId, Player player) {
        if (spawnerId == null || player == null) {
            return CompletableFuture.completedFuture(0.0);
        }
        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        if (debug()) {
            plugin.getLogger().info("[DEBUG] API.sellSpawner requested id=" + spawnerId
                    + " player=" + player.getName());
        }
        return sellInternal(spawnerData, player);
    }

    @Override
    public CompletableFuture<Double> sellSpawner(Location location, Player player) {
        if (location == null || player == null) {
            return CompletableFuture.completedFuture(0.0);
        }
        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(location);
        if (debug()) {
            plugin.getLogger().info("[DEBUG] API.sellSpawner requested loc=" + formatLoc(location)
                    + " player=" + player.getName());
        }
        return sellInternal(spawnerData, player);
    }

    private CompletableFuture<Double> sellInternal(SpawnerData spawnerData, Player player) {
        if (spawnerData == null) {
            if (debug()) {
                plugin.getLogger().info("[DEBUG] API.sellSpawner -> spawner not found");
            }
            return CompletableFuture.completedFuture(0.0);
        }

        SpawnerSellManager sellManager = plugin.getSpawnerSellManager();
        if (sellManager == null) {
            if (debug()) {
                plugin.getLogger().info("[DEBUG] API.sellSpawner -> sellManager null");
            }
            return CompletableFuture.completedFuture(0.0);
        }

        CompletableFuture<Double> future = new CompletableFuture<>();
        sellManager.sellAllItems(player, spawnerData, () -> {
            double paid = spawnerData.getLastSellResult() != null
                    ? spawnerData.getLastSellResult().getTotalValue()
                    : 0.0;
            if (debug()) {
                plugin.getLogger().info("[DEBUG] API.sellSpawner completed player=" + player.getName()
                        + " paid=" + paid);
            }
            future.complete(paid);
        }, 0, 0, "API");
        return future;
    }

    @Override
    public SpawnerDataModifier getSpawnerModifier(String spawnerId) {
        if (spawnerId == null) {
            return null;
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        return spawnerData != null ? new SpawnerDataModifierImpl(spawnerData) : null;
    }

    @Override
    public CompletableFuture<Boolean> removeSpawner(String spawnerId) {
        if (spawnerId == null) {
            return CompletableFuture.completedFuture(false);
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        if (spawnerData == null) {
            return CompletableFuture.completedFuture(false);
        }

        return spawnerRemovalService.removeSpawner(spawnerData);
    }

    @Override
    public CompletableFuture<Boolean> removeSpawner(Location location) {
        if (location == null) {
            return CompletableFuture.completedFuture(false);
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(location);
        if (spawnerData == null) {
            return CompletableFuture.completedFuture(false);
        }

        return spawnerRemovalService.removeSpawner(spawnerData);
    }

    @Override
    public GuiLayoutRegistry getLayoutRegistry() {
        return guiLayoutRegistry;
    }

    @Override
    public void setSpawnerLayoutProvider(SpawnerGuiLayoutProvider provider) {
        this.spawnerGuiLayoutProvider = provider;
        plugin.getGuiLayoutConfig().setProvider(provider);
    }

    @Override
    public void clearSpawnerLayoutProvider() {
        this.spawnerGuiLayoutProvider = null;
        plugin.getGuiLayoutConfig().setProvider(null);
    }

    private boolean hasVanillaSpawnerKey(ItemStack spawnerItem) {
        return spawnerItem.getPersistentDataContainer()
                .has(vanillaSpawnerKey, PersistentDataType.BOOLEAN);
    }

    private boolean hasItemSpawnerKey(ItemStack spawnerItem) {
        return spawnerItem.getPersistentDataContainer()
                .has(itemSpawnerMaterialKey, PersistentDataType.STRING);
    }

    /**
     * Gets the currently active per-spawner layout provider.
     *
     * @return the provider, or null
     */
    public SpawnerGuiLayoutProvider getSpawnerLayoutProvider() {
        return spawnerGuiLayoutProvider;
    }

    /**
     * Converts SpawnerData to SpawnerDataDTO.
     *
     * @param spawnerData the spawner data to convert
     * @return the DTO representation
     */
    public static SpawnerDataDTO convertToDTO(SpawnerData spawnerData) {
        VirtualInventory virtualInv = spawnerData.getVirtualInventory();
        int currentItemCount = virtualInv != null ? virtualInv.getUsedSlots() : 0;
        boolean atCapacity = Boolean.TRUE.equals(spawnerData.getIsAtCapacity());

        return new SpawnerDataDTO(
                spawnerData.getSpawnerId(),
                spawnerData.getSpawnerLocation(),
                spawnerData.getEntityType(),
                spawnerData.getSpawnedItemMaterial(),
                spawnerData.getStackSize(),
                spawnerData.getMaxStackSize(),
                spawnerData.getBaseMaxStoragePages(),
                spawnerData.getBaseMinMobs(),
                spawnerData.getBaseMaxMobs(),
                spawnerData.getBaseMaxStoredExp(),
                spawnerData.getSpawnDelay(),
                atCapacity,
                currentItemCount,
                spawnerData.getLastInteractedPlayer()
        );
    }

    private static String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "?";
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}