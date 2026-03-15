package com.sovereignty.models;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Represents the physical Province Core block placed in the world.
 *
 * <p>Custom NBT data (Core HP, Level) is persisted on the block itself
 * via the {@link PersistentDataContainer} API so it survives server restarts
 * without requiring additional DB synchronization.
 *
 * <h3>PDC Keys</h3>
 * <ul>
 *   <li>{@code sovereignty:core_hp}    — {@code INTEGER}</li>
 *   <li>{@code sovereignty:core_level} — {@code INTEGER}</li>
 *   <li>{@code sovereignty:province_id} — {@code LONG}</li>
 * </ul>
 */
public final class CoreBlock {

    /** Default hit-points for a freshly placed core. */
    public static final int DEFAULT_HP = 100;
    /** Maximum core level (determines claim radius bonuses). */
    public static final int MAX_LEVEL = 5;
    /** Chunk radius automatically claimed on placement (3×3 = radius 1). */
    public static final int CAPITAL_CLAIM_RADIUS = 1;

    // PDC keys — initialized lazily via #initKeys(Plugin)
    private static NamespacedKey KEY_HP;
    private static NamespacedKey KEY_LEVEL;
    private static NamespacedKey KEY_PROVINCE_ID;

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private int hp;
    private int level;
    private long provinceId;

    /**
     * Constructs a CoreBlock from raw coordinates (typically loaded from DB).
     */
    public CoreBlock(String world, int x, int y, int z, int hp, int level, long provinceId) {
        this.world = Objects.requireNonNull(world, "world");
        this.x = x;
        this.y = y;
        this.z = z;
        this.hp = hp;
        this.level = level;
        this.provinceId = provinceId;
    }

    /**
     * One-time initialization of {@link NamespacedKey}s.
     * Must be called during {@code onEnable} before any PDC read/write.
     *
     * @param plugin the owning plugin instance
     */
    public static void initKeys(Plugin plugin) {
        KEY_HP = new NamespacedKey(plugin, "core_hp");
        KEY_LEVEL = new NamespacedKey(plugin, "core_level");
        KEY_PROVINCE_ID = new NamespacedKey(plugin, "province_id");
    }

    // ── PDC Persistence Helpers ──────────────────────────────────────────

    /**
     * Writes this core's data into the {@link PersistentDataContainer} of the
     * given block's TileEntity (the block <b>must</b> be a tile-entity type
     * such as a Player Head or Beacon).
     *
     * @param block the block to write PDC data to
     */
    public void writePDC(Block block) {
        if (!(block.getState() instanceof org.bukkit.block.TileState tile)) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        pdc.set(KEY_HP, PersistentDataType.INTEGER, hp);
        pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, level);
        pdc.set(KEY_PROVINCE_ID, PersistentDataType.LONG, provinceId);
        tile.update();
    }

    /**
     * Reads core data from a block's PDC. Returns {@code null} if the block
     * does not contain Sovereignty core data.
     *
     * @param block the block to read from
     * @return a populated {@link CoreBlock}, or {@code null}
     */
    public static CoreBlock readPDC(Block block) {
        if (!(block.getState() instanceof org.bukkit.block.TileState tile)) return null;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        if (!pdc.has(KEY_HP, PersistentDataType.INTEGER)) return null;

        int readHp = pdc.getOrDefault(KEY_HP, PersistentDataType.INTEGER, DEFAULT_HP);
        int readLevel = pdc.getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
        long readId = pdc.getOrDefault(KEY_PROVINCE_ID, PersistentDataType.LONG, -1L);
        Location loc = block.getLocation();
        return new CoreBlock(
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                readHp, readLevel, readId
        );
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = Math.max(0, hp); }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.min(MAX_LEVEL, Math.max(1, level)); }

    public long getProvinceId() { return provinceId; }
    public void setProvinceId(long provinceId) { this.provinceId = provinceId; }

    /** Returns the chunk X coordinate that contains this core. */
    public int getCoreChunkX() { return x >> 4; }

    /** Returns the chunk Z coordinate that contains this core. */
    public int getCoreChunkZ() { return z >> 4; }

    /**
     * Checks whether the core has been destroyed (HP ≤ 0).
     */
    public boolean isDestroyed() { return hp <= 0; }
}
