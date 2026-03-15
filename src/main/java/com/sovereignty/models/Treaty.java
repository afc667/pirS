package com.sovereignty.models;

import com.sovereignty.models.enums.DiplomaticRelation;

import java.time.Instant;

/**
 * Data model for a diplomatic treaty between two provinces.
 * Maps to a row in {@code sov_treaties}.
 */
public final class Treaty {

    private long id;
    private final long provinceA;
    private final long provinceB;
    private DiplomaticRelation relation;
    private Instant signedAt;
    private Instant expiresAt;  // nullable — permanent treaties

    public Treaty(long id, long provinceA, long provinceB,
                  DiplomaticRelation relation, Instant signedAt, Instant expiresAt) {
        this.id = id;
        this.provinceA = provinceA;
        this.provinceB = provinceB;
        this.relation = relation;
        this.signedAt = signedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Convenience constructor for new treaties (ID assigned by DB).
     */
    public Treaty(long provinceA, long provinceB, DiplomaticRelation relation) {
        this(0, provinceA, provinceB, relation, Instant.now(), null);
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getProvinceA() { return provinceA; }
    public long getProvinceB() { return provinceB; }

    public DiplomaticRelation getRelation() { return relation; }
    public void setRelation(DiplomaticRelation relation) { this.relation = relation; }

    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    /**
     * Checks whether a specific province is a party to this treaty.
     */
    public boolean involves(long provinceId) {
        return provinceA == provinceId || provinceB == provinceId;
    }
}
