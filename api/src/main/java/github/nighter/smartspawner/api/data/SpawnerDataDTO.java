package github.nighter.smartspawner.api.data;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Data Transfer Object containing read-only spawner information.
 * This class provides read-only access to spawner data through the API.
 * To modify spawner properties, use {@link SpawnerDataModifier} obtained from
 * {@link github.nighter.smartspawner.api.SmartSpawnerAPI#getSpawnerModifier(String)}.
 */
@Getter
public class SpawnerDataDTO {

    private final String spawnerId;
    private final Location location;
    private final EntityType entityType;
    private final Material spawnedItemMaterial;
    private final int stackSize;
    private final int maxStackSize;
    private final int baseMaxStoragePages;
    private final int baseMinMobs;
    private final int baseMaxMobs;
    private final long baseMaxStoredExp;
    private final long baseSpawnerDelay;

    /**
     * Whether the spawner is completely full (inventory slots AND stored exp both maxed).
     */
    private final boolean atCapacity;

    /**
     * Current used virtual-inventory slots.
     */
    private final int currentItemCount;

    /**
     * Name of the last player who interacted with this spawner, or {@code null} if none.
     * <p>
     * NOTE: SmartSpawner does not currently persist a true "owner" — this is the closest
     * available field. A real owner field is tracked separately for a future release.
     */
    private final String owner;

    /**
     * Creates a new spawner data DTO.
     *
     * @param spawnerId the unique spawner ID
     * @param location the spawner location
     * @param entityType the entity type
     * @param spawnedItemMaterial the spawned item material for item spawners
     * @param stackSize the current stack size (read-only)
     * @param maxStackSize the maximum stack size
     * @param baseMaxStoragePages the base storage pages
     * @param baseMinMobs the base minimum mobs
     * @param baseMaxMobs the base maximum mobs
     * @param baseMaxStoredExp the base maximum stored experience
     * @param baseSpawnerDelay the base spawner delay in ticks
     * @param atCapacity whether the spawner is currently at capacity
     * @param currentItemCount the current number of used storage slots
     * @param owner the name of the last interacting player, may be {@code null}
     */
    public SpawnerDataDTO(String spawnerId, Location location, EntityType entityType,
                          Material spawnedItemMaterial, int stackSize, int maxStackSize,
                          int baseMaxStoragePages, int baseMinMobs, int baseMaxMobs,
                          long baseMaxStoredExp, long baseSpawnerDelay,
                          boolean atCapacity, int currentItemCount, String owner) {
        this.spawnerId = spawnerId;
        this.location = location;
        this.entityType = entityType;
        this.spawnedItemMaterial = spawnedItemMaterial;
        this.stackSize = stackSize;
        this.maxStackSize = maxStackSize;
        this.baseMaxStoragePages = baseMaxStoragePages;
        this.baseMinMobs = baseMinMobs;
        this.baseMaxMobs = baseMaxMobs;
        this.baseMaxStoredExp = baseMaxStoredExp;
        this.baseSpawnerDelay = baseSpawnerDelay;
        this.atCapacity = atCapacity;
        this.currentItemCount = currentItemCount;
        this.owner = owner;
    }

    /**
     * Checks if this is an item spawner.
     *
     * @return true if spawner spawns items instead of entities
     */
    public boolean isItemSpawner() {
        return entityType == EntityType.ITEM && spawnedItemMaterial != null;
    }
}