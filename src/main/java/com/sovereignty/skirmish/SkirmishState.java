package com.sovereignty.skirmish;

import com.sovereignty.models.ChunkPosition;

/**
 * Mutable state object tracking an active Border Skirmish.
 *
 * <p>Represents a single skirmish targeting one outermost border chunk
 * of a rival province. Tracks the attacking and defending provinces,
 * the target chunk coordinates, and the active combat phase.
 *
 * <p>The {@code active} field is volatile to support safe reads from
 * event listeners while it is updated by the scheduled activation task.
 */
public final class SkirmishState {

    private final ChunkPosition targetChunk;
    private final long attackerProvinceId;
    private final long defenderProvinceId;
    private final long declaredAtMillis;
    private volatile boolean active;

    /**
     * Constructs a SkirmishState in the warning (pre-active) phase.
     *
     * @param targetChunk         the targeted border chunk
     * @param attackerProvinceId  the attacking province ID
     * @param defenderProvinceId  the defending province ID
     * @param declaredAtMillis    the system time when the skirmish was declared
     */
    public SkirmishState(ChunkPosition targetChunk, long attackerProvinceId,
                         long defenderProvinceId, long declaredAtMillis) {
        this.targetChunk = targetChunk;
        this.attackerProvinceId = attackerProvinceId;
        this.defenderProvinceId = defenderProvinceId;
        this.declaredAtMillis = declaredAtMillis;
        this.active = false;
    }

    /** Returns the targeted border chunk position. */
    public ChunkPosition getTargetChunk() { return targetChunk; }

    /** Returns the attacking province's database ID. */
    public long getAttackerProvinceId() { return attackerProvinceId; }

    /** Returns the defending province's database ID. */
    public long getDefenderProvinceId() { return defenderProvinceId; }

    /** Returns the system time (millis) when the skirmish was declared. */
    public long getDeclaredAtMillis() { return declaredAtMillis; }

    /**
     * Returns whether the skirmish is in the active combat phase.
     * During the 5-minute warning phase, this returns {@code false}.
     */
    public boolean isActive() { return active; }

    /**
     * Sets the active combat phase state.
     *
     * @param active {@code true} to activate combat, {@code false} to deactivate
     */
    public void setActive(boolean active) { this.active = active; }
}
