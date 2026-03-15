package com.sovereignty.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sovereignty.models.ChunkPosition;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * O(1) spatial cache mapping {@link ChunkPosition} → Province ID.
 *
 * <p>Backed by Caffeine with configurable eviction. On a typical server
 * with thousands of claimed chunks this cache keeps lookups below
 * 100 ns, preventing any measurable TPS impact during
 * {@code PlayerMoveEvent} processing.
 *
 * <h3>Cache Invalidation Strategy</h3>
 * <ul>
 *   <li><b>Claim:</b>   {@link #put(ChunkPosition, long)}</li>
 *   <li><b>Unclaim:</b> {@link #invalidate(ChunkPosition)}</li>
 *   <li><b>Startup:</b> Bulk-loaded via {@link #put(ChunkPosition, long)} from DB.</li>
 * </ul>
 */
public final class ChunkCache {

    private final Cache<ChunkPosition, Long> cache;

    /**
     * @param maxSize           maximum entries before eviction
     * @param expireAfterAccess minutes of idle before eviction (0 = never)
     */
    public ChunkCache(long maxSize, long expireAfterAccess) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(maxSize);
        if (expireAfterAccess > 0) {
            builder.expireAfterAccess(expireAfterAccess, TimeUnit.MINUTES);
        }
        this.cache = builder.build();
    }

    /** Default constructor with generous defaults. */
    public ChunkCache() {
        this(100_000, 0);
    }

    /**
     * Associates a chunk with a province.
     *
     * @param pos        chunk coordinates
     * @param provinceId owning province's database ID
     */
    public void put(ChunkPosition pos, long provinceId) {
        cache.put(pos, provinceId);
    }

    /**
     * Looks up the owning province ID for a chunk in O(1).
     *
     * @param pos chunk coordinates
     * @return the province ID if claimed, otherwise empty
     */
    public Optional<Long> get(ChunkPosition pos) {
        return Optional.ofNullable(cache.getIfPresent(pos));
    }

    /**
     * Removes a chunk mapping (e.g. on unclaim or chunk decay).
     */
    public void invalidate(ChunkPosition pos) {
        cache.invalidate(pos);
    }

    /**
     * Drops all entries (e.g. on plugin reload).
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Returns the current number of cached entries.
     */
    public long size() {
        return cache.estimatedSize();
    }
}
