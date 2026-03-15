package com.sovereignty.models;

import com.sovereignty.models.enums.CasusBelli;
import com.sovereignty.models.enums.WarPhase;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Data model for an active or resolved war between two provinces.
 * Maps to a row in {@code sov_wars}.
 */
public final class War {

    private long id;
    private final long attackerId;
    private final long defenderId;
    private final CasusBelli casusBelli;
    private WarPhase phase;
    private LocalTime siegeWindowStart;
    private LocalTime siegeWindowEnd;
    private int attackerScore;
    private int defenderScore;
    private Instant declaredAt;
    private Instant activeSince;
    private Instant resolvedAt;

    public War(long id, long attackerId, long defenderId, CasusBelli casusBelli,
               WarPhase phase, LocalTime siegeWindowStart, LocalTime siegeWindowEnd,
               int attackerScore, int defenderScore,
               Instant declaredAt, Instant activeSince, Instant resolvedAt) {
        this.id = id;
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.casusBelli = casusBelli;
        this.phase = phase;
        this.siegeWindowStart = siegeWindowStart;
        this.siegeWindowEnd = siegeWindowEnd;
        this.attackerScore = attackerScore;
        this.defenderScore = defenderScore;
        this.declaredAt = declaredAt;
        this.activeSince = activeSince;
        this.resolvedAt = resolvedAt;
    }

    /**
     * Convenience constructor for a freshly declared war.
     */
    public War(long attackerId, long defenderId, CasusBelli casusBelli) {
        this(0, attackerId, defenderId, casusBelli, WarPhase.PREPARATION,
                LocalTime.of(20, 0), LocalTime.of(22, 0),
                0, 0, Instant.now(), null, null);
    }

    // ── Phase Queries ────────────────────────────────────────────────────

    /**
     * Whether the war is still in the 24-hour preparation grace period.
     */
    public boolean isInPreparation() {
        return phase == WarPhase.PREPARATION;
    }

    /**
     * Whether the war has been resolved (win/loss determined).
     */
    public boolean isResolved() {
        return phase == WarPhase.RESOLVED;
    }

    /**
     * Checks whether the current server time falls within the daily siege window.
     *
     * @param now the current server local time
     * @return {@code true} if siege actions are allowed right now
     */
    public boolean isWithinSiegeWindow(LocalTime now) {
        if (phase != WarPhase.ACTIVE_SIEGE) return false;
        return !now.isBefore(siegeWindowStart) && now.isBefore(siegeWindowEnd);
    }

    /**
     * Checks whether a specific province is a belligerent in this war.
     */
    public boolean involves(long provinceId) {
        return attackerId == provinceId || defenderId == provinceId;
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getAttackerId() { return attackerId; }
    public long getDefenderId() { return defenderId; }
    public CasusBelli getCasusBelli() { return casusBelli; }

    public WarPhase getPhase() { return phase; }
    public void setPhase(WarPhase phase) { this.phase = phase; }

    public LocalTime getSiegeWindowStart() { return siegeWindowStart; }
    public void setSiegeWindowStart(LocalTime siegeWindowStart) { this.siegeWindowStart = siegeWindowStart; }

    public LocalTime getSiegeWindowEnd() { return siegeWindowEnd; }
    public void setSiegeWindowEnd(LocalTime siegeWindowEnd) { this.siegeWindowEnd = siegeWindowEnd; }

    public int getAttackerScore() { return attackerScore; }
    public void setAttackerScore(int attackerScore) { this.attackerScore = attackerScore; }

    public int getDefenderScore() { return defenderScore; }
    public void setDefenderScore(int defenderScore) { this.defenderScore = defenderScore; }

    public Instant getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(Instant declaredAt) { this.declaredAt = declaredAt; }

    public Instant getActiveSince() { return activeSince; }
    public void setActiveSince(Instant activeSince) { this.activeSince = activeSince; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
