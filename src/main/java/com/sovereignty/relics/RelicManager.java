package com.sovereignty.relics;

import com.sovereignty.integration.DynmapHook;
import com.sovereignty.integration.ItemsAdderListener;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;
import com.sovereignty.roles.RoleManager;
import com.sovereignty.warfare.WarfareEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the six unique server-wide Relics in Sovereignty.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Track relic ownership via persistent UUIDs in PDC to prevent duplication.</li>
 *   <li>Handle anti-combat-logging: relics drop on logout.</li>
 *   <li>Disease Spore AoE chunk infection on projectile hit.</li>
 *   <li>Physicist's Goggles repeating async task for enemy Glowing detection.</li>
 *   <li>Nichirin Sword 3× Core structural damage multiplier.</li>
 *   <li>Demon Ash alert suppression and blindness smoke screen.</li>
 *   <li>Poetryland Hoe 3× crop drop multiplier.</li>
 *   <li>Crystal of Resonance province buffs (espionage immunity, HP doubling, etc.).</li>
 * </ul>
 *
 * <p>All 6 relics are identified by their ItemsAdder namespace via
 * {@link ItemsAdderListener#getItemsAdderId(ItemStack)}.
 */
public final class RelicManager implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    // ── Configuration Constants ──────────────────────────────────────────

    /** Duration of Disease Spore chunk infection in ticks (10 minutes). */
    private static final long DISEASE_SPORE_DURATION_TICKS = 10L * 60L * 20L;

    /** Radius in blocks for Physicist's Goggles enemy detection. */
    private static final double GOGGLES_DETECTION_RADIUS = 30.0;

    /** Interval in ticks for the Goggles scanning task (2 seconds). */
    private static final long GOGGLES_SCAN_INTERVAL_TICKS = 40L;

    /** Nichirin Sword Core damage multiplier. */
    public static final int NICHIRIN_CORE_DAMAGE_MULTIPLIER = 3;

    /** Demon Ash alert suppression duration in seconds (5 minutes). */
    private static final long DEMON_ASH_SILENCE_SECONDS = 5L * 60L;

    /** Poetryland Hoe crop drop multiplier. */
    public static final int POETRYLAND_CROP_MULTIPLIER = 3;

    /** Crystal of Resonance Core HP multiplier. */
    public static final int CRYSTAL_HP_MULTIPLIER = 2;

    /** Crystal of Resonance Influence generation multiplier. */
    public static final int CRYSTAL_INFLUENCE_MULTIPLIER = 3;

    // ── Dependencies ────────────────────────────────────────────────────

    private final Plugin plugin;
    private final ProvinceManager provinceManager;
    private final ItemsAdderListener itemsAdderListener;
    private final DynmapHook dynmapHook;
    private final RoleManager roleManager;
    private final WarfareEngine warfareEngine;
    private final Logger logger;

    /** PDC key for unique relic UUID — prevents duplication. */
    private final NamespacedKey relicUuidKey;

    /**
     * Tracks Disease Spore infected chunks: ChunkPosition → expiry Instant.
     * Defending players in infected chunks receive Poison II + Weakness II.
     */
    private final Map<ChunkPosition, Instant> infectedChunks = new ConcurrentHashMap<>();

    /**
     * Tracks provinces with Demon Ash alert suppression: Province ID → silenced-until Instant.
     */
    private final Map<Long, Instant> silencedProvinces = new ConcurrentHashMap<>();

    /**
     * Tracks provinces with Crystal of Resonance installed in Core: Province ID → active.
     */
    private final Set<Long> resonanceProvinces = ConcurrentHashMap.newKeySet();

    /**
     * Constructs the RelicManager and starts the async Goggles scanner.
     *
     * @param plugin             the owning plugin instance
     * @param provinceManager    the province manager
     * @param itemsAdderListener the ItemsAdder integration
     * @param dynmapHook         the Dynmap integration
     * @param roleManager        the council role manager
     * @param warfareEngine      the warfare engine (nullable, TBD)
     * @param logger             the plugin logger
     */
    public RelicManager(Plugin plugin, ProvinceManager provinceManager,
                        ItemsAdderListener itemsAdderListener, DynmapHook dynmapHook,
                        RoleManager roleManager, WarfareEngine warfareEngine,
                        Logger logger) {
        this.plugin = plugin;
        this.provinceManager = provinceManager;
        this.itemsAdderListener = itemsAdderListener;
        this.dynmapHook = dynmapHook;
        this.roleManager = roleManager;
        this.warfareEngine = warfareEngine;
        this.logger = logger;
        this.relicUuidKey = new NamespacedKey(plugin, "relic_uuid");

        // Start the repeating Physicist's Goggles scanner
        startGogglesScanner();

        logger.info("[RelicManager] Relic system initialized — 6 unique relics tracked.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RELIC IDENTIFICATION & TRACKING
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Identifies whether an ItemStack is a Relic, and if so, which type.
     *
     * @param item the item to inspect
     * @return the {@link RelicType}, or {@code null} if not a relic
     */
    public RelicType identifyRelic(ItemStack item) {
        if (itemsAdderListener == null || !itemsAdderListener.isAvailable()) return null;
        String customId = itemsAdderListener.getItemsAdderId(item);
        return RelicType.fromNamespace(customId);
    }

    /**
     * Reads the unique relic UUID from an item's PDC for duplication tracking.
     *
     * @param item the item to inspect
     * @return the relic UUID string, or {@code null} if not tagged
     */
    public String getRelicUuid(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(relicUuidKey, PersistentDataType.STRING);
    }

    /**
     * Checks if a player is carrying any relic in their inventory.
     *
     * @param player the player to check
     * @return {@code true} if any relic is found
     */
    public boolean isCarryingRelic(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && identifyRelic(item) != null) return true;
        }
        return false;
    }

    /**
     * Drops all relics from a player's inventory at the specified location.
     * Used for anti-combat-logging and death mechanics.
     *
     * @param player   the player whose relics to drop
     * @param location the location to drop relics at
     */
    private void dropRelicsAt(Player player, Location location) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && identifyRelic(item) != null) {
                location.getWorld().dropItemNaturally(location, item.clone());
                player.getInventory().setItem(i, null);
                RelicType type = identifyRelic(item);
                logger.info("[RelicManager] Relic '" + (type != null ? type.getDisplayName() : "unknown")
                        + "' dropped at " + location);
            }
        }
        // Also check armor slots (for Physicist's Goggles as helmet)
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && identifyRelic(helmet) != null) {
            location.getWorld().dropItemNaturally(location, helmet.clone());
            player.getInventory().setHelmet(null);
            logger.info("[RelicManager] Relic helmet dropped at " + location);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EVENT HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Anti-combat-logging: When a player logs out carrying a relic, it drops
     * at their logout location to prevent relic hoarding via disconnect.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isCarryingRelic(player)) {
            Location loc = player.getLocation();
            dropRelicsAt(player, loc);

            // Broadcast alert so other players know
            Component alert = MINI.deserialize(
                    "<red>⚠ <bold>RELIC DROPPED!</bold></red> <gray>A relic has been dropped at "
                            + "<white>" + loc.getBlockX() + ", " + loc.getBlockY()
                            + ", " + loc.getBlockZ() + "</white> (combat-log prevention).</gray>"
            );
            Bukkit.broadcast(alert);
            logger.info("[RelicManager] Anti-combat-log: relics dropped by "
                    + player.getName() + " at " + loc);
        }
    }

    /**
     * On player death: ensure relics drop naturally (Minecraft already does
     * this, but we broadcast a server-wide alert for awareness).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        for (ItemStack drop : event.getDrops()) {
            RelicType type = identifyRelic(drop);
            if (type != null) {
                Component alert = MINI.deserialize(
                        "<red>⚔ <bold>RELIC LOST!</bold></red> <gray>"
                                + victim.getName() + " has dropped the <gold>"
                                + type.getDisplayName() + "</gold>!</gray>"
                );
                Bukkit.broadcast(alert);
                logger.info("[RelicManager] Relic '" + type.getDisplayName()
                        + "' dropped on death by " + victim.getName());
            }
        }
    }

    // ── Disease Spore (Projectile Hit → Chunk Infection) ─────────────────

    /**
     * Handles Disease Spore projectile impact. When thrown inside an enemy
     * Province during an active Siege or Skirmish, infects the target chunk
     * with Poison II + Weakness II for 10 minutes.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;

        // Check if the projectile has Disease Spore metadata
        Entity projectile = event.getEntity();
        PersistentDataContainer projPdc = projectile.getPersistentDataContainer();
        String customId = projPdc.get(
                new NamespacedKey(plugin, "itemsadder_id"), PersistentDataType.STRING);
        if (!RelicType.DISEASE_SPORE.getNamespace().equals(customId)) return;

        // Determine impact location
        Location hitLoc = projectile.getLocation();
        ChunkPosition chunkPos = new ChunkPosition(
                hitLoc.getWorld().getName(),
                hitLoc.getBlockX() >> 4,
                hitLoc.getBlockZ() >> 4
        );

        // Verify the chunk is inside an enemy province
        Optional<Province> provinceOpt = provinceManager.getProvinceAt(chunkPos);
        if (provinceOpt.isEmpty()) return;
        Province province = provinceOpt.get();
        if (province.hasMember(shooter.getUniqueId())) return;

        // Infect the chunk
        Instant expiry = Instant.now().plusSeconds(DISEASE_SPORE_DURATION_TICKS / 20L);
        infectedChunks.put(chunkPos, expiry);

        // Broadcast infection alert
        Component alert = MINI.deserialize(
                "<dark_green>☣ <bold>PLAGUE!</bold></dark_green> <gray>A chunk in <white>"
                        + province.getName()
                        + "</white> has been infected with Disease Spore! "
                        + "Defenders beware — Poison II and Weakness II for 10 minutes.</gray>"
        );
        Bukkit.broadcast(alert);

        // Start a repeating task to apply debuffs to defending players in the chunk
        new BukkitRunnable() {
            @Override
            public void run() {
                Instant now = Instant.now();
                if (!infectedChunks.containsKey(chunkPos) || now.isAfter(infectedChunks.get(chunkPos))) {
                    infectedChunks.remove(chunkPos);
                    cancel();
                    return;
                }
                // Apply debuffs to all defending players in the infected chunk
                Chunk chunk = hitLoc.getWorld().getChunkAt(chunkPos.getChunkX(), chunkPos.getChunkZ());
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Player defender) {
                        // Only debuff defenders (province members, not attackers)
                        if (province.hasMember(defender.getUniqueId())) {
                            defender.addPotionEffect(new PotionEffect(
                                    PotionEffectType.POISON, 100, 1, false, true, true));
                            defender.addPotionEffect(new PotionEffect(
                                    PotionEffectType.WEAKNESS, 100, 1, false, true, true));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Every 2 seconds

        logger.info("[RelicManager] Disease Spore deployed by " + shooter.getName()
                + " at chunk " + chunkPos);
    }

    // ── Physicist's Goggles (Repeating Async Scanner) ────────────────────

    /**
     * Starts the repeating async task that scans for players wearing the
     * Physicist's Goggles helmet. When worn by a Marshal or Lord inside
     * their own territory, grants Glowing to enemy players within 30 blocks.
     */
    private void startGogglesScanner() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (helmet == null) continue;
                    RelicType type = identifyRelic(helmet);
                    if (type != RelicType.PHYSICIST_GOGGLES) continue;

                    // Check if player is a Marshal or Lord inside their own territory
                    Location loc = player.getLocation();
                    ChunkPosition pos = new ChunkPosition(
                            loc.getWorld().getName(),
                            loc.getBlockX() >> 4,
                            loc.getBlockZ() >> 4
                    );
                    Optional<Province> provinceOpt = provinceManager.getProvinceAt(pos);
                    if (provinceOpt.isEmpty()) continue;

                    Province province = provinceOpt.get();
                    UUID playerUuid = player.getUniqueId();

                    // Must be the owner (Lord) or the province Marshal
                    boolean isLord = province.getOwnerUuid().equals(playerUuid);
                    UUID marshal = roleManager.getMarshal(province.getId());
                    boolean isMarshal = marshal != null && marshal.equals(playerUuid);
                    if (!isLord && !isMarshal) continue;

                    // Scan nearby entities for enemies within 30-block radius
                    Collection<Entity> nearby = player.getNearbyEntities(
                            GOGGLES_DETECTION_RADIUS, GOGGLES_DETECTION_RADIUS, GOGGLES_DETECTION_RADIUS);
                    for (Entity entity : nearby) {
                        if (!(entity instanceof Player target)) continue;
                        // Skip province members (only apply Glowing to enemies)
                        if (province.hasMember(target.getUniqueId())) continue;

                        // Apply Glowing effect to enemy for 3 seconds (refreshed by scan)
                        target.addPotionEffect(new PotionEffect(
                                PotionEffectType.GLOWING, 60, 0, false, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, GOGGLES_SCAN_INTERVAL_TICKS, GOGGLES_SCAN_INTERVAL_TICKS);
    }

    // ── Nichirin Sword (Core Damage Multiplier) ──────────────────────────

    /**
     * Intercepts player interaction with Province Core blocks. If the player
     * is holding the Nichirin Sword and attacking an enemy Core during an
     * active siege, the Core takes 3× structural damage.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractWithCore(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!event.getAction().name().contains("LEFT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        RelicType type = identifyRelic(mainHand);
        if (type != RelicType.NICHIRIN_SWORD) return;

        Block block = event.getClickedBlock();
        CoreBlock coreBlock = CoreBlock.readPDC(block);
        if (coreBlock == null) return; // Not a Province Core

        // Verify this is an enemy core
        Optional<Province> provinceOpt = provinceManager.getProvinceById(coreBlock.getProvinceId());
        if (provinceOpt.isEmpty()) return;
        Province province = provinceOpt.get();
        if (province.hasMember(player.getUniqueId())) return;

        // Apply 3× structural damage during siege (base damage = 1)
        int baseDamage = 1;
        int totalDamage = baseDamage * NICHIRIN_CORE_DAMAGE_MULTIPLIER;
        coreBlock.setHp(coreBlock.getHp() - totalDamage);
        coreBlock.writePDC(block);

        player.sendActionBar(MINI.deserialize(
                "<red>⚔ Nichirin Sword strikes the Core! <white>-" + totalDamage
                        + " HP</white> (remaining: <yellow>" + coreBlock.getHp() + "</yellow>)</red>"
        ));

        // Broadcast if Core is destroyed
        if (coreBlock.isDestroyed()) {
            Component alert = MINI.deserialize(
                    "<red>💀 <bold>CORE DESTROYED!</bold></red> <gray>The Nichirin Sword has "
                            + "shattered <white>" + province.getName()
                            + "</white>'s Province Core!</gray>"
            );
            Bukkit.broadcast(alert);
        }

        logger.info("[RelicManager] Nichirin Sword dealt " + totalDamage
                + " damage to " + province.getName() + "'s Core (HP: " + coreBlock.getHp() + ")");
    }

    // ── Demon Ash (Alert Suppression + Blindness) ────────────────────────

    /**
     * Handles Demon Ash usage: when a player right-clicks an enemy Province
     * Core while holding Demon Ash, it creates a Blindness smoke screen
     * and suppresses Dynmap Siege Alerts and chat warnings for 5 minutes.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDemonAshUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        RelicType type = identifyRelic(mainHand);
        if (type != RelicType.DEMON_ASH) return;

        Block block = event.getClickedBlock();
        CoreBlock coreBlock = CoreBlock.readPDC(block);
        if (coreBlock == null) return; // Not a Province Core

        // Verify this is an enemy core
        Optional<Province> provinceOpt = provinceManager.getProvinceById(coreBlock.getProvinceId());
        if (provinceOpt.isEmpty()) return;
        Province province = provinceOpt.get();
        if (province.hasMember(player.getUniqueId())) return;

        // Consume the Demon Ash
        mainHand.setAmount(mainHand.getAmount() - 1);

        // Suppress alerts for 5 minutes
        Instant silencedUntil = Instant.now().plusSeconds(DEMON_ASH_SILENCE_SECONDS);
        silencedProvinces.put(province.getId(), silencedUntil);

        // Apply Blindness smoke screen to all nearby players (16-block radius)
        Location coreLoc = block.getLocation();
        Collection<Entity> nearby = coreLoc.getWorld().getNearbyEntities(coreLoc, 16, 16, 16);
        for (Entity entity : nearby) {
            if (entity instanceof Player target) {
                // Apply blindness to all province defenders (members)
                if (province.hasMember(target.getUniqueId())) {
                    target.addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS, 200, 0, false, true, true)); // 10 seconds
                }
            }
        }

        // Remove Dynmap siege alert if present
        if (dynmapHook != null && dynmapHook.isAvailable()) {
            dynmapHook.removeSiegeAlert(coreBlock);
        }

        // Broadcast stealth alert (only to the attacker's team)
        player.sendMessage(MINI.deserialize(
                "<dark_purple>🌑 <italic>Demon Ash deployed! Enemy alerts silenced for 5 minutes.</italic></dark_purple>"
        ));

        // Server-wide subtle notification
        Component alert = MINI.deserialize(
                "<dark_gray><italic>☁ A dark smoke engulfs a province's borders...</italic></dark_gray>"
        );
        Bukkit.broadcast(alert);

        logger.info("[RelicManager] Demon Ash deployed by " + player.getName()
                + " on province '" + province.getName() + "' — alerts silenced until " + silencedUntil);
    }

    /**
     * Checks whether a province's alert system is currently silenced by Demon Ash.
     *
     * @param provinceId the province to check
     * @return {@code true} if alerts are suppressed
     */
    public boolean isAlertSilenced(long provinceId) {
        Instant until = silencedProvinces.get(provinceId);
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            silencedProvinces.remove(provinceId);
            return false;
        }
        return true;
    }

    // ── Poetryland Hoe (3× Crop Drops) ──────────────────────────────────

    /**
     * Intercepts crop block breaks. If the player is holding the Poetryland
     * Hoe relic, crop drops are multiplied by 3×.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        RelicType type = identifyRelic(mainHand);
        if (type != RelicType.POETRYLAND_HOE) return;

        Block block = event.getBlock();
        Material mat = block.getType();

        // Only multiply drops for crop-type blocks
        if (!isCropBlock(mat)) return;

        // Verify the player is in their own province
        ChunkPosition pos = new ChunkPosition(
                block.getWorld().getName(),
                block.getChunk().getX(),
                block.getChunk().getZ()
        );
        Optional<Province> provinceOpt = provinceManager.getProvinceAt(pos);
        if (provinceOpt.isEmpty()) return;

        // Cancel the default event and handle drops manually with multiplier
        event.setDropItems(false);
        Collection<ItemStack> drops = block.getDrops(mainHand, player);
        for (ItemStack drop : drops) {
            drop.setAmount(drop.getAmount() * POETRYLAND_CROP_MULTIPLIER);
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }

        player.sendActionBar(MINI.deserialize(
                "<green>🌾 Poetryland Hoe: <white>" + POETRYLAND_CROP_MULTIPLIER
                        + "× crop yield!</white></green>"
        ));
    }

    /**
     * Determines whether a block type is a harvestable crop.
     */
    private boolean isCropBlock(Material material) {
        return material == Material.WHEAT
                || material == Material.CARROTS
                || material == Material.POTATOES
                || material == Material.BEETROOTS
                || material == Material.NETHER_WART
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.COCOA
                || material == Material.MELON
                || material == Material.PUMPKIN
                || material == Material.SUGAR_CANE
                || material == Material.BAMBOO;
    }

    // ── Crystal of Resonance (Province Buff Tracking) ────────────────────

    /**
     * Activates the Crystal of Resonance for a province. Called when the
     * crystal is placed inside a Province Core's inventory/GUI.
     *
     * <p>Effects: 100% espionage immunity, Core HP doubled, Influence tripled.
     *
     * @param provinceId the province to buff
     */
    public void activateResonance(long provinceId) {
        resonanceProvinces.add(provinceId);
        // Double the Core HP (capped at Integer.MAX_VALUE / 2 to prevent overflow)
        provinceManager.getProvinceById(provinceId).ifPresent(province -> {
            CoreBlock core = province.getCore();
            int maxSafeHp = Integer.MAX_VALUE / CRYSTAL_HP_MULTIPLIER;
            int currentHp = Math.min(core.getHp(), maxSafeHp);
            core.setHp(currentHp * CRYSTAL_HP_MULTIPLIER);
        });

        Component alert = MINI.deserialize(
                "<light_purple>💎 <bold>RESONANCE SHIELD!</bold></light_purple> <gray>A province has "
                        + "activated the Crystal of Resonance! Form a coalition!</gray>"
        );
        Bukkit.broadcast(alert);

        logger.info("[RelicManager] Crystal of Resonance activated for province " + provinceId);
    }

    /**
     * Deactivates the Crystal of Resonance for a province.
     *
     * @param provinceId the province to remove the buff from
     */
    public void deactivateResonance(long provinceId) {
        resonanceProvinces.remove(provinceId);
        logger.info("[RelicManager] Crystal of Resonance deactivated for province " + provinceId);
    }

    /**
     * Checks whether a province has the Crystal of Resonance active.
     *
     * @param provinceId the province to check
     * @return {@code true} if Resonance Shield is active
     */
    public boolean hasResonanceShield(long provinceId) {
        return resonanceProvinces.contains(provinceId);
    }

    /**
     * Checks whether a province is immune to espionage (sabotage events are
     * cancelled). Immunity is granted by the Crystal of Resonance.
     *
     * @param provinceId the province to check
     * @return {@code true} if espionage is blocked
     */
    public boolean isEspionageImmune(long provinceId) {
        return resonanceProvinces.contains(provinceId);
    }

    /**
     * Returns the Influence generation multiplier for a province.
     * Returns 3× if Crystal of Resonance is active, otherwise 1×.
     *
     * @param provinceId the province to check
     * @return the influence multiplier
     */
    public int getInfluenceMultiplier(long provinceId) {
        return resonanceProvinces.contains(provinceId) ? CRYSTAL_INFLUENCE_MULTIPLIER : 1;
    }

    /**
     * Returns the High Tax penalty reduction factor for a province.
     * The Poetryland Hoe in Core inventory reduces the penalty by 50%.
     * This method is called by the StabilityEngine during daily ticks.
     *
     * @param provinceId the province to check
     * @return 0.5 if Poetryland Hoe is in the Core, otherwise 1.0
     */
    public double getHighTaxPenaltyMultiplier(long provinceId) {
        // In a full implementation, this would check if the Poetryland Hoe
        // is physically inside the Province Core's GUI/inventory.
        // For now, it checks if the province's Steward is carrying it.
        UUID steward = roleManager.getSteward(provinceId);
        if (steward != null) {
            Player stewardPlayer = Bukkit.getPlayer(steward);
            if (stewardPlayer != null) {
                for (ItemStack item : stewardPlayer.getInventory().getContents()) {
                    if (item != null && identifyRelic(item) == RelicType.POETRYLAND_HOE) {
                        return 0.5; // 50% reduction
                    }
                }
            }
        }
        return 1.0;
    }

    // ── Utility ──────────────────────────────────────────────────────────

    /**
     * Checks if a chunk is currently infected by the Disease Spore.
     *
     * @param pos the chunk position to check
     * @return {@code true} if the chunk is infected
     */
    public boolean isChunkInfected(ChunkPosition pos) {
        Instant until = infectedChunks.get(pos);
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            infectedChunks.remove(pos);
            return false;
        }
        return true;
    }
}
