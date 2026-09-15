package github.nighter.smartspawner.spawner.properties;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems;
    @Getter private int maxSlots;

    // Caches. All invalidated on any mutation.
    private List<Map.Entry<ItemSignature, Long>> sortedEntriesCache;
    private Int2ObjectMap<ItemStack> firstPageSortedCache;
    private List<ItemStack> firstStacksFastCache;

    private Material preferredSortMaterial;

    private static final int FIRST_PAGE_SIZE = 45;
    private static final int FAST_CACHE_SIZE = 64;

    public VirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.sortedEntriesCache = null;
        this.firstPageSortedCache = null;
        this.firstStacksFastCache = null;
        this.preferredSortMaterial = null;
    }

    public static ItemSignature getSignature(ItemStack item) {
        return new ItemSignature(item);
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = Math.max(0, maxSlots);
        invalidateCaches();
    }

    private void invalidateCaches() {
        sortedEntriesCache = null;
        firstPageSortedCache = null;
        firstStacksFastCache = null;
    }

    /*
     * FAST PATH
     * Used for loading already-consolidated storage data.
     */
    public void addItem(ItemStack item, long amount) {
        if (item == null || amount <= 0) {
            return;
        }

        ItemSignature signature = getSignature(item);

        consolidatedItems.merge(signature, amount, Long::sum);

        invalidateCaches();
    }

    /*
     * Bulk insert for already-consolidated storage data.
     */
    public void addItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        boolean changed = false;

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long amountValue = entry.getValue();

            if (amountValue <= 0) {
                continue;
            }

            consolidatedItems.merge(signature, amountValue, Long::sum);
            changed = true;
        }

        if (changed) {
            invalidateCaches();
        }
    }

    /**
     * Adds an already-consolidated entry: one item template plus its total count.
     *
     * @param template the item template, its own amount is ignored
     * @param amount   how many of that item are stored, ignored when not positive
     */
    public void addConsolidatedItem(ItemStack template, long amount) {
        addItem(template, amount);
    }

    public boolean removeItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        Map<ItemSignature, Long> toRemove = new HashMap<>(items.size());

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Number amountValue = entry.getValue();

            if (signature == null || amountValue == null) {
                continue;
            }

            long amount = amountValue.longValue();
            if (amount <= 0) {
                continue;
            }

            toRemove.merge(signature, amount, Long::sum);
        }

        if (toRemove.isEmpty()) {
            return true;
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            if (consolidatedItems.getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                return false;
            }
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            consolidatedItems.computeIfPresent(entry.getKey(), (key, current) -> {
                long remaining = current - entry.getValue();
                return remaining <= 0 ? null : remaining;
            });
        }

        invalidateCaches();

        return true;
    }

    public Int2ObjectMap<ItemStack> getDisplayPage(int page, int pageSize) {
        if (pageSize <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        int safePage = Math.max(1, page);
        int startSlot = (safePage - 1) * pageSize;

        // Page 1 fast path
        if (startSlot == 0 && pageSize <= FIRST_PAGE_SIZE) {
            return getFirstPageSorted();
        }

        return buildDisplaySection(startSlot, pageSize);
    }

    public Int2ObjectMap<ItemStack> getDisplayRange(int startSlot, int maxResults) {
        // Page 1 fast path
        if (startSlot == 0 && maxResults <= FIRST_PAGE_SIZE) {
            return getFirstPageSorted();
        }
        return buildDisplaySection(startSlot, maxResults);
    }

    private Int2ObjectMap<ItemStack> getFirstPageSorted() {
        if (firstPageSortedCache == null) {
            firstPageSortedCache = buildDisplaySection(0, FIRST_PAGE_SIZE);
        }
        return firstPageSortedCache;
    }

    /**
     * Fast path for hoppers and other bulk drains that don't care about sort order.
     * Returns at most {@code limit} cloned stacks from the raw map. Does not sort.
     * Cost is O(min(limit, FAST_CACHE_SIZE)) after the first call; the first call
     * iterates up to FAST_CACHE_SIZE entries of the map.
     */
    public List<ItemStack> peekAnyItems(int limit) {
        if (limit <= 0 || consolidatedItems.isEmpty()) {
            return Collections.emptyList();
        }

        if (firstStacksFastCache == null) {
            firstStacksFastCache = buildFastCache();
        }

        int n = Math.min(limit, firstStacksFastCache.size());
        List<ItemStack> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(firstStacksFastCache.get(i).clone());
        }
        return out;
    }

    private List<ItemStack> buildFastCache() {
        List<ItemStack> result = new ArrayList<>(FAST_CACHE_SIZE);
        for (Map.Entry<ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            if (result.size() >= FAST_CACHE_SIZE) break;

            ItemSignature sig = entry.getKey();
            long total = entry.getValue();
            int maxStack = sig.getMaxStackSize();
            if (maxStack <= 0 || total <= 0) continue;

            ItemStack tpl = sig.getTemplate();
            tpl.setAmount((int) Math.min(total, maxStack));
            result.add(tpl);
        }
        return result;
    }

    public Map<ItemSignature, Long> getConsolidatedItems() {
        return new HashMap<>(consolidatedItems);
    }

    public int getUsedSlots() {
        if (consolidatedItems.isEmpty()) {
            return 0;
        }

        int estimatedSlots = 0;
        for (Map.Entry<ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            long amount = entry.getValue();
            int maxStackSize = entry.getKey().getMaxStackSize();
            estimatedSlots += (int) Math.ceil((double) amount / maxStackSize);
            if (estimatedSlots >= maxSlots) {
                return maxSlots;
            }
        }
        return estimatedSlots;
    }

    public void sortItems(org.bukkit.Material preferredMaterial) {
        this.preferredSortMaterial = preferredMaterial;
        invalidateCaches();

        if (consolidatedItems.isEmpty()) {
            return;
        }

        if (preferredMaterial != null) {
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted((e1, e2) -> {
                    boolean e1Preferred = e1.getKey().getMaterial() == preferredMaterial;
                    boolean e2Preferred = e2.getKey().getMaterial() == preferredMaterial;

                    if (e1Preferred && !e2Preferred) return -1;
                    if (!e1Preferred && e2Preferred) return 1;

                    return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
                })
                .collect(java.util.stream.Collectors.toList());
        } else {
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getMaterialName()))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    private Int2ObjectMap<ItemStack> buildDisplaySection(int startSlot, int maxResults) {
        if (maxResults <= 0 || startSlot >= maxSlots) {
            return Int2ObjectMaps.emptyMap();
        }

        if (consolidatedItems.isEmpty()) {
            return Int2ObjectMaps.emptyMap();
        }

        int safeStart = Math.max(0, startSlot);
        int sectionLimit = Math.min(maxResults, maxSlots - safeStart);
        if (sectionLimit <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        Int2ObjectOpenHashMap<ItemStack> section = new Int2ObjectOpenHashMap<>(Math.min(sectionLimit, 45));
        List<Map.Entry<ItemSignature, Long>> sortedEntries = getSortedEntries();

        int currentGlobalSlot = 0;
        int relativeSlot = 0;

        for (Map.Entry<ItemSignature, Long> entry : sortedEntries) {
            if (relativeSlot >= sectionLimit || currentGlobalSlot >= maxSlots) {
                break;
            }

            ItemSignature sig = entry.getKey();
            int maxStackSize = sig.getMaxStackSize();
            if (maxStackSize <= 0) {
                continue;
            }

            long totalAmount = entry.getValue();
            int stacksForEntry = (int) Math.min(
                    Integer.MAX_VALUE,
                    (totalAmount + maxStackSize - 1L) / maxStackSize
            );

            if (currentGlobalSlot + stacksForEntry <= safeStart) {
                currentGlobalSlot += stacksForEntry;
                continue;
            }

            int stacksToSkip = Math.max(0, safeStart - currentGlobalSlot);
            long remainingAmount = totalAmount - ((long) stacksToSkip * maxStackSize);
            currentGlobalSlot += stacksToSkip;

            while (remainingAmount > 0 && relativeSlot < sectionLimit && currentGlobalSlot < maxSlots) {
                ItemStack displayItem = sig.getTemplate();
                displayItem.setAmount((int) Math.min(remainingAmount, maxStackSize));
                section.put(relativeSlot++, displayItem);

                remainingAmount -= maxStackSize;
                currentGlobalSlot++;
            }
        }

        return Int2ObjectMaps.unmodifiable(section);
    }

    private List<Map.Entry<ItemSignature, Long>> getSortedEntries() {
        if (sortedEntriesCache == null) {
            sortedEntriesCache = new ArrayList<>(consolidatedItems.entrySet());
            sortEntries(sortedEntriesCache);
        }
        return sortedEntriesCache;
    }

    private void sortEntries(List<Map.Entry<ItemSignature, Long>> entries) {
        if (preferredSortMaterial != null) {
            entries.sort((e1, e2) -> {
                boolean e1Preferred = e1.getKey().getMaterial() == preferredSortMaterial;
                boolean e2Preferred = e2.getKey().getMaterial() == preferredSortMaterial;

                if (e1Preferred && !e2Preferred) return -1;
                if (!e1Preferred && e2Preferred) return 1;

                return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
            });
            return;
        }

        entries.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
    }
}