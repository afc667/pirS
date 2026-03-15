package com.sovereignty.managers;

import com.sovereignty.cache.ChunkCache;
import com.sovereignty.database.queries.ProvinceQueries;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central manager for Province lifecycle, claim validation, and
 * cache ↔ database synchronization.
 *
 * <h3>Caching Strategy</h3>
 * <ul>
 *   <li>{@link #provinceById} — In-memory map of all loaded provinces (keyed by ID).</li>
 *   <li>{@link #chunkCache}   — Caffeine-backed O(1) Chunk → Province ID lookup.</li>
 * </ul>
 *
 * <p>All mutations are applied to the in-memory state first, then
 * persisted asynchronously via {@link ProvinceQueries}.
 */
public final class ProvinceManager {

    private final ProvinceQueries queries;
    private final ChunkCache chunkCache;
    private final Logger logger;

    /** Province ID → Province (in-memory authoritative state). */
    private final Map<Long, Province> provinceById = new ConcurrentHashMap<>();

    public ProvinceManager(ProvinceQueries queries, ChunkCache chunkCache, Logger logger) {
        this.queries = queries;
        this.chunkCache = chunkCache;
        this.logger = logger;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Loads all provinces and chunk mappings from the database into memory.
     * Called once during {@code onEnable}.
     *
     * @return a future that completes when the warm-up is done
     */
    public CompletableFuture<Void> warmCaches() {
        return queries.findAll().thenCompose(provinces -> {
            for (Province p : provinces) {
                provinceById.put(p.getId(), p);
            }
            logger.info("Loaded " + provinces.size() + " provinces into cache.");

            // Now load all chunk → province mappings
            return loadAllChunkMappings(provinces);
        });
    }

    private CompletableFuture<Void> loadAllChunkMappings(List<Province> provinces) {
        // Load chunks for every province in parallel, then populate cache
        CompletableFuture<?>[] futures = provinces.stream()
                .map(p -> queries.findChunksByProvince(p.getId()).thenAccept(chunks -> {
                    for (ChunkPosition pos : chunks) {
                        chunkCache.put(pos, p.getId());
                    }
                }))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures)
                .thenRun(() -> logger.info("Chunk cache warmed: " + chunkCache.size() + " entries."));
    }

    // ── Province CRUD ────────────────────────────────────────────────────

    /**
     * Creates a new province, claims the 3×3 capital area, and persists
     * both the province and the chunks asynchronously.
     *
     * @param province the province to create (ID will be set after insertion)
     * @return a future containing the new province's database ID
     */
    public CompletableFuture<Long> createProvince(Province province) {
        return queries.insertProvince(province).thenCompose(id -> {
            if (id < 0) {
                return CompletableFuture.completedFuture(-1L);
            }
            province.setId(id);
            province.getCore().setProvinceId(id);
            provinceById.put(id, province);

            // Claim the 3×3 capital chunks around the core
            return claimCapitalArea(province).thenApply(v -> id);
        });
    }

    /**
     * Claims the 3×3 chunk area centred on the Province Core.
     */
    private CompletableFuture<Void> claimCapitalArea(Province province) {
        CoreBlock core = province.getCore();
        int cx = core.getCoreChunkX();
        int cz = core.getCoreChunkZ();
        String world = core.getWorld();
        int r = CoreBlock.CAPITAL_CLAIM_RADIUS; // 1 → 3×3

        CompletableFuture<?>[] futures = new CompletableFuture[(2 * r + 1) * (2 * r + 1)];
        int idx = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkPosition pos = new ChunkPosition(world, cx + dx, cz + dz);
                chunkCache.put(pos, province.getId());
                futures[idx++] = queries.insertChunk(province.getId(), pos);
            }
        }
        return CompletableFuture.allOf(futures);
    }

    // ── Spatial Queries (O(1)) ───────────────────────────────────────────

    /**
     * Resolves the owning province for a chunk in O(1) via the Caffeine cache.
     *
     * @param pos chunk coordinates
     * @return the owning {@link Province}, or empty if wilderness
     */
    public Optional<Province> getProvinceAt(ChunkPosition pos) {
        return chunkCache.get(pos).map(provinceById::get);
    }

    /**
     * Returns a province by its database ID from the in-memory map.
     */
    public Optional<Province> getProvinceById(long id) {
        return Optional.ofNullable(provinceById.get(id));
    }

    // ── Claim Validation ─────────────────────────────────────────────────

    /**
     * Validates whether a chunk can be claimed by a province, enforcing:
     * <ul>
     *   <li>The chunk is currently unclaimed (wilderness).</li>
     *   <li>The {@link Province#BUFFER_ZONE_CHUNKS minimum buffer zone}
     *       between non-allied provinces is respected.</li>
     * </ul>
     *
     * @param pos        the target chunk
     * @param provinceId the claiming province
     * @return {@code true} if the claim is valid
     */
    public boolean canClaim(ChunkPosition pos, long provinceId) {
        // Must be wilderness
        if (chunkCache.get(pos).isPresent()) {
            return false;
        }

        // Enforce buffer zone — check all neighbouring chunks within buffer range
        int buffer = Province.BUFFER_ZONE_CHUNKS;
        for (int dx = -buffer; dx <= buffer; dx++) {
            for (int dz = -buffer; dz <= buffer; dz++) {
                if (dx == 0 && dz == 0) continue;
                ChunkPosition neighbour = new ChunkPosition(pos.getWorld(),
                        pos.getChunkX() + dx, pos.getChunkZ() + dz);
                Optional<Long> neighbourOwner = chunkCache.get(neighbour);
                if (neighbourOwner.isPresent() && neighbourOwner.get() != provinceId) {
                    // TODO: Check alliance status; allow if allied
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Claims a single chunk for a province (after validation).
     *
     * @param pos        the chunk to claim
     * @param provinceId the claiming province's ID
     * @return a future that completes when the claim is persisted
     */
    public CompletableFuture<Void> claimChunk(ChunkPosition pos, long provinceId) {
        chunkCache.put(pos, provinceId);
        return queries.insertChunk(provinceId, pos);
    }

    /**
     * Un-claims a chunk (e.g. during Civil War decay).
     */
    public void unclaimChunk(ChunkPosition pos) {
        chunkCache.invalidate(pos);
        // DB deletion would be handled asynchronously
    }

    /**
     * Persists all dirty province state back to the database.
     * Called periodically or during {@code onDisable}.
     */
    public CompletableFuture<Void> saveAll() {
        CompletableFuture<?>[] futures = provinceById.values().stream()
                .map(queries::updateProvince)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Returns the underlying chunk cache (for direct access in listeners).
     */
    public ChunkCache getChunkCache() {
        return chunkCache;
    }

    /**
     * Returns all loaded provinces (read-only snapshot for iteration).
     *
     * @return an unmodifiable collection of all cached provinces
     */
    public java.util.Collection<Province> getAllProvinces() {
        return java.util.Collections.unmodifiableCollection(provinceById.values());
    }
}
