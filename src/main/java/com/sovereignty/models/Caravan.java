package com.sovereignty.models;

import java.time.Instant;
import java.util.UUID;

/**
 * Data model for a physical Trade Caravan entity that transports
 * Vault currency between two Province Cores.
 *
 * <p>When a Trade Agreement is active between two provinces, a physical
 * caravan entity (Llama) spawns at a scheduled daily tick. The caravan
 * stores its real Vault currency value in the entity's
 * {@link org.bukkit.persistence.PersistentDataContainer PDC} and
 * pathfinds from Province A's Core to Province B's Core.
 *
 * <h3>Ambush Mechanic</h3>
 * If a rival player kills the caravan in the Wilderness, the PDC
 * value is read, the stolen currency is deposited into the killer's
 * Vault balance, and a dramatic server-wide chat alert is broadcast.
 */
public final class Caravan {

    private final UUID entityUuid;
    private final long sourceProvinceId;
    private final long targetProvinceId;
    private final double vaultValue;
    private final Instant spawnedAt;

    /**
     * Constructs a new Caravan record.
     *
     * @param entityUuid       the UUID of the spawned Llama entity
     * @param sourceProvinceId the sending province's database ID
     * @param targetProvinceId the receiving province's database ID
     * @param vaultValue       the Vault currency value stored in the caravan
     * @param spawnedAt        the instant the caravan was spawned
     */
    public Caravan(UUID entityUuid, long sourceProvinceId, long targetProvinceId,
                   double vaultValue, Instant spawnedAt) {
        this.entityUuid = entityUuid;
        this.sourceProvinceId = sourceProvinceId;
        this.targetProvinceId = targetProvinceId;
        this.vaultValue = vaultValue;
        this.spawnedAt = spawnedAt;
    }

    /** Returns the UUID of the spawned caravan entity. */
    public UUID getEntityUuid() { return entityUuid; }

    /** Returns the sending province's database ID. */
    public long getSourceProvinceId() { return sourceProvinceId; }

    /** Returns the receiving province's database ID. */
    public long getTargetProvinceId() { return targetProvinceId; }

    /** Returns the Vault currency value stored in the caravan. */
    public double getVaultValue() { return vaultValue; }

    /** Returns the instant the caravan was spawned. */
    public Instant getSpawnedAt() { return spawnedAt; }
}
