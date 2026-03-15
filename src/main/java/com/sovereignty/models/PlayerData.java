package com.sovereignty.models;

import com.sovereignty.models.enums.Rank;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutable data model representing a player in the Sovereignty system.
 *
 * <p>Maps 1-to-1 with a row in the {@code sov_players} table.
 */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private Rank rank;
    private double influence;
    private double wealth;
    private Long provinceId;    // nullable — CITIZEN may be landless
    private Instant joinedAt;
    private Instant lastSeen;

    public PlayerData(UUID uuid, String name) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.rank = Rank.CITIZEN;
        this.influence = 0.0;
        this.wealth = 0.0;
        this.joinedAt = Instant.now();
        this.lastSeen = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public Rank getRank() { return rank; }
    public double getInfluence() { return influence; }
    public double getWealth() { return wealth; }
    public Long getProvinceId() { return provinceId; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLastSeen() { return lastSeen; }

    // ── Setters ──────────────────────────────────────────────────────────

    public void setName(String name) { this.name = name; }
    public void setRank(Rank rank) { this.rank = rank; }
    public void setInfluence(double influence) { this.influence = influence; }
    public void setWealth(double wealth) { this.wealth = wealth; }
    public void setProvinceId(Long provinceId) { this.provinceId = provinceId; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerData that)) return false;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
