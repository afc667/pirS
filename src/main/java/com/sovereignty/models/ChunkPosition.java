package com.sovereignty.models;

import java.util.Objects;

/**
 * Immutable value-object representing a single chunk position in a specific world.
 *
 * <p>Used as the key for O(1) spatial lookups in
 * {@link com.sovereignty.cache.ChunkCache}.
 * Implements {@link #equals(Object)} and {@link #hashCode()} using bitwise
 * packing for maximum hash distribution.
 */
public final class ChunkPosition {

    private final String world;
    private final int chunkX;
    private final int chunkZ;

    /**
     * @param world  the world name (e.g. {@code "world"})
     * @param chunkX chunk X coordinate (block X >> 4)
     * @param chunkZ chunk Z coordinate (block Z >> 4)
     */
    public ChunkPosition(String world, int chunkX, int chunkZ) {
        this.world = Objects.requireNonNull(world, "world");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    /**
     * Computes the squared chunk distance to another position (same world only).
     *
     * @param other the other chunk position
     * @return squared distance in chunk units, or {@link Integer#MAX_VALUE} if different worlds
     */
    public int distanceSquared(ChunkPosition other) {
        if (!this.world.equals(other.world)) {
            return Integer.MAX_VALUE;
        }
        int dx = this.chunkX - other.chunkX;
        int dz = this.chunkZ - other.chunkZ;
        return dx * dx + dz * dz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPosition that)) return false;
        return chunkX == that.chunkX
                && chunkZ == that.chunkZ
                && world.equals(that.world);
    }

    /**
     * High-quality hash using bitwise packing:
     * lower 16 bits = chunkX, upper 16 bits = chunkZ, XOR'd with world hash.
     */
    @Override
    public int hashCode() {
        return world.hashCode() ^ ((chunkX & 0xFFFF) | ((chunkZ & 0xFFFF) << 16));
    }

    @Override
    public String toString() {
        return world + ":" + chunkX + "," + chunkZ;
    }
}
