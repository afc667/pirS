package com.sovereignty.skirmish;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.Province;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages Border Skirmishes — low-stakes localized aggression between provinces.
 *
 * <h3>Design Philosophy</h3>
 * <p>Full-scale wars require a Casus Belli, a 24-hour prep phase, and end with
 * the destruction of a Core. Border Skirmishes allow players to raid a single
 * outermost border chunk of a rival Province without wiping out months of progress.
 *
 * <h3>Rules of Engagement</h3>
 * <ul>
 *   <li>Only chunks directly touching the Wilderness can be targeted.</li>
 *   <li>A 5-minute global warning is broadcasted before combat begins.</li>
 *   <li>For 15 minutes, PvP protection in THAT SPECIFIC CHUNK is disabled.</li>
 *   <li>During the window: chest protections and block protections (crops/livestock)
 *       in that single chunk are bypassed. Attackers can loot and steal.</li>
 *   <li>Attackers CANNOT claim the land or damage the Core.</li>
 *   <li>24-hour cooldown per province to prevent grief spamming.</li>
 * </ul>
 */
public final class SkirmishManager implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Warning phase duration before skirmish becomes active (5 minutes in ticks). */
    private static final long WARNING_PHASE_TICKS = 5L * 60L * 20L;

    /** Active skirmish combat duration (15 minutes in ticks). */
    private static final long SKIRMISH_DURATION_TICKS = 15L * 60L * 20L;

    /** Cooldown between skirmishes targeting the same province (24 hours). */
    private static final long COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L;

    private final Plugin plugin;
    private final ProvinceManager provinceManager;
    private final Logger logger;

    /**
     * Active skirmish states, keyed by the target chunk position.
     * Only one skirmish can be active per chunk at any time.
     */
    private final Map<ChunkPosition, SkirmishState> activeSkirmishes = new ConcurrentHashMap<>();

    /**
     * Cooldown tracker: Province ID → last skirmish end time (millis).
     * Prevents the same province from being skirmished more than once per 24 hours.
     */
    private final Map<Long, Long> skirmishCooldowns = new ConcurrentHashMap<>();

    /**
     * Constructs the SkirmishManager.
     *
     * @param plugin          the owning plugin instance
     * @param provinceManager the province manager for spatial lookups
     * @param logger          the plugin logger
     */
    public SkirmishManager(Plugin plugin, ProvinceManager provinceManager, Logger logger) {
        this.plugin = plugin;
        this.provinceManager = provinceManager;
        this.logger = logger;
        logger.info("[SkirmishManager] Border Skirmish system initialized.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SKIRMISH DECLARATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Declares a Border Skirmish targeting a specific outermost border chunk
     * of a rival province.
     *
     * @param attacker         the player declaring the skirmish (must be Lord or Marshal)
     * @param targetChunk      the border chunk to target
     * @param attackerProvince the attacker's province
     * @return {@code true} if the skirmish was successfully declared
     */
    public boolean declareSkirmish(Player attacker, ChunkPosition targetChunk,
                                   Province attackerProvince) {
        // ── Validation ──────────────────────────────────────────────────

        // 1. Target chunk must be claimed by a rival province
        Optional<Province> targetProvinceOpt = provinceManager.getProvinceAt(targetChunk);
        if (targetProvinceOpt.isEmpty()) {
            attacker.sendMessage(MINI.deserialize(
                    "<red>✖ That chunk is wilderness — you can only skirmish claimed territory.</red>"
            ));
            return false;
        }

        Province targetProvince = targetProvinceOpt.get();

        // 2. Cannot skirmish your own territory
        if (targetProvince.getId() == attackerProvince.getId()) {
            attacker.sendMessage(MINI.deserialize(
                    "<red>✖ You cannot declare a skirmish on your own territory!</red>"
            ));
            return false;
        }

        // 3. Target chunk must be on the border (touching wilderness)
        if (!isBorderChunk(targetChunk, targetProvince.getId())) {
            attacker.sendMessage(MINI.deserialize(
                    "<red>✖ That chunk is not on the border. "
                            + "Only outermost chunks touching wilderness can be targeted.</red>"
            ));
            return false;
        }

        // 4. Check if there's already an active skirmish on this chunk
        if (activeSkirmishes.containsKey(targetChunk)) {
            attacker.sendMessage(MINI.deserialize(
                    "<red>✖ A skirmish is already active on this chunk!</red>"
            ));
            return false;
        }

        // 5. Check 24-hour cooldown for the target province
        Long lastSkirmish = skirmishCooldowns.get(targetProvince.getId());
        if (lastSkirmish != null && System.currentTimeMillis() - lastSkirmish < COOLDOWN_MILLIS) {
            long remainingMs = COOLDOWN_MILLIS - (System.currentTimeMillis() - lastSkirmish);
            long remainingHours = remainingMs / (1000L * 60L * 60L);
            attacker.sendMessage(MINI.deserialize(
                    "<red>✖ This province is on skirmish cooldown. "
                            + "Try again in <white>" + remainingHours + "h</white>.</red>"
            ));
            return false;
        }

        // ── Declaration ─────────────────────────────────────────────────

        SkirmishState state = new SkirmishState(
                targetChunk,
                attackerProvince.getId(),
                targetProvince.getId(),
                System.currentTimeMillis()
        );
        activeSkirmishes.put(targetChunk, state);

        // 5-minute global warning broadcast
        Component warning = MINI.deserialize(
                "<red>⚔ <bold>BORDER SKIRMISH!</bold></red> <gray>A skirmish has erupted at <white>"
                        + targetProvince.getName() + "</white>'s border! "
                        + "Combat begins in <yellow>5 minutes</yellow> at chunk <white>("
                        + targetChunk.getChunkX() + ", " + targetChunk.getChunkZ()
                        + ")</white>.</gray>"
        );
        Bukkit.broadcast(warning);

        // Schedule activation after 5-minute warning phase
        new BukkitRunnable() {
            @Override
            public void run() {
                activateSkirmish(state, targetProvince);
            }
        }.runTaskLater(plugin, WARNING_PHASE_TICKS);

        logger.info("[SkirmishManager] Skirmish declared by " + attacker.getName()
                + " (" + attackerProvince.getName() + ") targeting "
                + targetProvince.getName() + " at chunk " + targetChunk);
        return true;
    }

    /**
     * Activates the skirmish after the 5-minute warning phase.
     * Enables PvP and protection bypass for 15 minutes.
     */
    private void activateSkirmish(SkirmishState state, Province targetProvince) {
        if (!activeSkirmishes.containsKey(state.getTargetChunk())) {
            return; // Skirmish was cancelled
        }

        state.setActive(true);

        Component alert = MINI.deserialize(
                "<red>⚔ <bold>SKIRMISH ACTIVE!</bold></red> <gray>PvP and protections "
                        + "are now disabled at <white>" + targetProvince.getName()
                        + "</white>'s border chunk for <yellow>15 minutes</yellow>!</gray>"
        );
        Bukkit.broadcast(alert);

        // Schedule skirmish end after 15 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                endSkirmish(state, targetProvince);
            }
        }.runTaskLater(plugin, SKIRMISH_DURATION_TICKS);

        logger.info("[SkirmishManager] Skirmish activated at " + state.getTargetChunk());
    }

    /**
     * Ends the skirmish and restores normal protection.
     */
    private void endSkirmish(SkirmishState state, Province targetProvince) {
        activeSkirmishes.remove(state.getTargetChunk());
        skirmishCooldowns.put(state.getDefenderProvinceId(), System.currentTimeMillis());

        Component alert = MINI.deserialize(
                "<green>🏳 <bold>SKIRMISH ENDED!</bold></green> <gray>Protections have been restored "
                        + "at <white>" + targetProvince.getName() + "</white>'s border.</gray>"
        );
        Bukkit.broadcast(alert);

        logger.info("[SkirmishManager] Skirmish ended at " + state.getTargetChunk()
                + " — 24h cooldown applied to province " + state.getDefenderProvinceId());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PROTECTION BYPASS LISTENERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Bypasses block break protection in active skirmish chunks.
     * This allows attackers to break blocks (crops, etc.) during the window.
     * NOTE: Province Cores remain protected — skirmishes cannot damage cores.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        // Only intervene if another listener cancelled this event (protection plugin)
        if (!event.isCancelled()) return;

        Block block = event.getBlock();
        ChunkPosition pos = toChunkPosition(block);
        SkirmishState state = activeSkirmishes.get(pos);
        if (state == null || !state.isActive()) return;

        // Never allow Core damage during skirmishes
        if (CoreBlockCheck.isCoreBlock(block)) return;

        // Bypass protection — skirmish is active in this chunk
        event.setCancelled(false);
    }

    /**
     * Bypasses chest/container interaction protection in active skirmish chunks.
     * Allows attackers to loot chests during the 15-minute window.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        Block block = event.getClickedBlock();
        if (!isContainer(block)) return;

        ChunkPosition pos = toChunkPosition(block);
        SkirmishState state = activeSkirmishes.get(pos);
        if (state == null || !state.isActive()) return;

        // Bypass protection — allow chest access during skirmish
        event.setCancelled(false);
    }

    /**
     * Allows PvP in active skirmish chunks by un-cancelling damage events
     * that were blocked by the standard protection listener.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;

        ChunkPosition pos = new ChunkPosition(
                victim.getWorld().getName(),
                victim.getLocation().getBlockX() >> 4,
                victim.getLocation().getBlockZ() >> 4
        );
        SkirmishState state = activeSkirmishes.get(pos);
        if (state == null || !state.isActive()) return;

        // Bypass PvP protection — skirmish is active
        event.setCancelled(false);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  QUERY METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Checks whether a specific chunk has an active skirmish.
     *
     * @param pos the chunk position to check
     * @return {@code true} if a skirmish is active in this chunk
     */
    public boolean isSkirmishActive(ChunkPosition pos) {
        SkirmishState state = activeSkirmishes.get(pos);
        return state != null && state.isActive();
    }

    /**
     * Returns the active skirmish state for a chunk, if any.
     *
     * @param pos the chunk position
     * @return the skirmish state, or {@code null}
     */
    public SkirmishState getSkirmishAt(ChunkPosition pos) {
        return activeSkirmishes.get(pos);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Determines if a chunk is on the border of a province (touching wilderness).
     * A border chunk has at least one adjacent chunk that is unclaimed.
     */
    private boolean isBorderChunk(ChunkPosition pos, long provinceId) {
        int[][] offsets = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] offset : offsets) {
            ChunkPosition neighbour = new ChunkPosition(
                    pos.getWorld(),
                    pos.getChunkX() + offset[0],
                    pos.getChunkZ() + offset[1]
            );
            Optional<Province> adj = provinceManager.getProvinceAt(neighbour);
            if (adj.isEmpty()) {
                return true; // Touches wilderness
            }
        }
        return false;
    }

    /** Converts a Block to its ChunkPosition. */
    private ChunkPosition toChunkPosition(Block block) {
        return new ChunkPosition(
                block.getWorld().getName(),
                block.getChunk().getX(),
                block.getChunk().getZ()
        );
    }

    /** Checks if a block is a container type (chest, barrel, etc.). */
    private boolean isContainer(Block block) {
        return block.getState() instanceof org.bukkit.block.Container;
    }

    /**
     * Static inner utility to check if a block is a Province Core.
     * Prevents Core damage during skirmishes.
     */
    private static final class CoreBlockCheck {
        static boolean isCoreBlock(Block block) {
            return com.sovereignty.models.CoreBlock.readPDC(block) != null;
        }
    }
}
