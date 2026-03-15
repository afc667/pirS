package com.sovereignty.models;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutable data model representing a Province — the core political unit
 * in Sovereignty.
 *
 * <p>Each province is anchored to a physical {@link CoreBlock} and owns a
 * set of claimed {@link ChunkPosition chunks}. A province may optionally
 * have a suzerain (overlord), forming the feudal vassal tree.
 *
 * <h3>Stability Rules</h3>
 * <ul>
 *   <li>−0.5 per owned chunk (overextension)</li>
 *   <li>−10/day while tax rate is above 0.5 (high taxes)</li>
 *   <li>−war exhaustion (accumulated during active wars)</li>
 *   <li>+stability from feeding luxury items to the Core</li>
 * </ul>
 *
 * <h3>Civil War Trigger</h3>
 * If stability drops below 20, the province enters "Civil War" state:
 * PvP protection drops, border chunks begin to decay (un-claim), and
 * vassal Liberty Desire spikes by +40.
 */
public final class Province {

    /** Stability threshold below which Civil War is triggered. */
    public static final double CIVIL_WAR_THRESHOLD = 20.0;
    /** Overextension penalty per owned chunk. */
    public static final double OVEREXTENSION_PER_CHUNK = 0.5;
    /** Daily stability penalty when tax rate exceeds 50 %. */
    public static final double HIGH_TAX_PENALTY = 10.0;
    /** Liberty Desire spike applied to vassals during Civil War. */
    public static final double CIVIL_WAR_LIBERTY_SPIKE = 40.0;
    /** Minimum wilderness buffer chunks between non-allied provinces. */
    public static final int BUFFER_ZONE_CHUNKS = 1;

    private long id;
    private String name;
    private UUID ownerUuid;
    private Long suzerainId;    // nullable — top-level provinces
    private CoreBlock core;
    private double stability;
    private double taxRate;
    private double libertyDesire;
    private int development;
    private double warExhaustion;
    private Instant createdAt;

    /**
     * Full constructor — typically used when hydrating from the database.
     */
    public Province(long id, String name, UUID ownerUuid, Long suzerainId,
                    CoreBlock core, double stability, double taxRate,
                    double libertyDesire, int development, double warExhaustion,
                    Instant createdAt) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.suzerainId = suzerainId;
        this.core = Objects.requireNonNull(core, "core");
        this.stability = stability;
        this.taxRate = taxRate;
        this.libertyDesire = libertyDesire;
        this.development = development;
        this.warExhaustion = warExhaustion;
        this.createdAt = createdAt;
    }

    // ── Stability Mechanics ──────────────────────────────────────────────

    /**
     * Checks whether the province is currently in Civil War state.
     *
     * @return {@code true} if stability < {@value CIVIL_WAR_THRESHOLD}
     */
    public boolean isInCivilWar() {
        return stability < CIVIL_WAR_THRESHOLD;
    }

    /**
     * Calculates the Influence Point cost to claim a chunk at the given
     * squared distance from the Province Core.
     *
     * <p>Uses an exponential distance penalty to prevent snake-claiming:
     * <pre>cost = baseCost × 2^(distSq / scaleFactor)</pre>
     *
     * @param distanceSquared squared chunk distance from the core
     * @param baseCost        base influence cost per chunk
     * @param scaleFactor     distance scale divisor (higher = gentler curve)
     * @return the computed influence cost
     */
    public static double computeClaimCost(int distanceSquared, double baseCost, double scaleFactor) {
        return baseCost * Math.pow(2.0, distanceSquared / scaleFactor);
    }

    /**
     * Calculates the Cultural Influence this province exerts on a neighbour.
     *
     * <pre>culturalInfluence = development / distance</pre>
     *
     * @param distanceChunks the chunk distance to the target province
     * @return cultural pressure value
     */
    public double computeCulturalPressure(double distanceChunks) {
        if (distanceChunks <= 0) return development;
        return development / distanceChunks;
    }

    // ── Vassal Tribute ───────────────────────────────────────────────────

    /**
     * Whether this vassal should pay its daily tribute.
     * Tribute stops if Liberty Desire exceeds 50 %.
     */
    public boolean shouldPayTribute() {
        return libertyDesire < 50.0;
    }

    /**
     * Whether this vassal can trigger an Independence War.
     * Requires Liberty Desire ≥ 80 %.
     */
    public boolean canDeclareIndependence() {
        return libertyDesire >= 80.0;
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }

    public Long getSuzerainId() { return suzerainId; }
    public void setSuzerainId(Long suzerainId) { this.suzerainId = suzerainId; }

    public CoreBlock getCore() { return core; }
    public void setCore(CoreBlock core) { this.core = core; }

    public double getStability() { return stability; }
    public void setStability(double stability) {
        this.stability = Math.max(0.0, Math.min(100.0, stability));
    }

    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) {
        this.taxRate = Math.max(0.0, Math.min(1.0, taxRate));
    }

    public double getLibertyDesire() { return libertyDesire; }
    public void setLibertyDesire(double libertyDesire) {
        this.libertyDesire = Math.max(0.0, Math.min(100.0, libertyDesire));
    }

    public int getDevelopment() { return development; }
    public void setDevelopment(int development) { this.development = Math.max(1, development); }

    public double getWarExhaustion() { return warExhaustion; }
    public void setWarExhaustion(double warExhaustion) {
        this.warExhaustion = Math.max(0.0, warExhaustion);
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Province that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
