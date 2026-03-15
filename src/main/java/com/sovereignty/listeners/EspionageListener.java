package com.sovereignty.listeners;

import com.sovereignty.integration.DynmapHook;
import com.sovereignty.integration.ItemsAdderListener;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;
import com.sovereignty.stability.StabilityEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles espionage and subterfuge mechanics for stealth incursions.
 *
 * <h3>Forged Passports</h3>
 * <p>When a player crosses a chunk boundary into rival territory while
 * carrying a {@code sovereignty:forged_passport} item, the standard
 * "Border Crossed" action-bar alert is suppressed and their icon is
 * temporarily hidden from Dynmap.
 *
 * <h3>Sabotage</h3>
 * <p>If a player sneaks within a 5-block radius of an enemy Core,
 * an async repeating task starts rendering a particle progress bar.
 * If the sneak is held for 15 seconds uninterrupted (taking damage
 * cancels it), the enemy province's Stability is drained and the
 * Forged Passport item is consumed.
 */
public final class EspionageListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final int sabotageRadius;
    private final int sabotageDurationTicks;
    private final double sabotageStabilityDrain;

    private final Plugin plugin;
    private final ProvinceManager provinceManager;
    private final ItemsAdderListener itemsAdderListener;
    private final DynmapHook dynmapHook;
    private final StabilityEngine stabilityEngine;
    private final Logger logger;

    /** Tracks active sabotage tasks by player UUID. */
    private final Map<UUID, Integer> activeSabotages = new ConcurrentHashMap<>();

    /**
     * Constructs the espionage listener with configurable parameters.
     *
     * @param plugin             the owning plugin
     * @param provinceManager    the province manager
     * @param itemsAdderListener the ItemsAdder integration (for passport checks)
     * @param dynmapHook         the Dynmap integration (for hiding players)
     * @param stabilityEngine    the stability engine (for sabotage drain)
     * @param logger             the plugin logger
     */
    public EspionageListener(Plugin plugin, ProvinceManager provinceManager,
                             ItemsAdderListener itemsAdderListener,
                             DynmapHook dynmapHook,
                             StabilityEngine stabilityEngine,
                             Logger logger) {
        this.plugin = plugin;
        this.provinceManager = provinceManager;
        this.itemsAdderListener = itemsAdderListener;
        this.dynmapHook = dynmapHook;
        this.stabilityEngine = stabilityEngine;
        this.logger = logger;
        // Read configurable values from plugin config, with sensible defaults
        this.sabotageRadius = plugin.getConfig().getInt("espionage.sabotage-radius", 5);
        int durationSeconds = plugin.getConfig().getInt("espionage.sabotage-duration-seconds", 15);
        this.sabotageDurationTicks = durationSeconds * 20;
        this.sabotageStabilityDrain = plugin.getConfig().getDouble("espionage.sabotage-stability-drain", 15.0);
    }

    /**
     * Intercepts chunk boundary crossings to enable forged passport stealth.
     * If the player carries a forged passport and enters rival territory,
     * the border alert is suppressed and their Dynmap icon is hidden.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Fast-path: skip if same chunk
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }

        Player player = event.getPlayer();
        if (itemsAdderListener == null || !itemsAdderListener.isAvailable()) return;
        if (!itemsAdderListener.hasForgedPassport(player)) return;

        ChunkPosition newPos = new ChunkPosition(
                to.getWorld().getName(),
                to.getBlockX() >> 4,
                to.getBlockZ() >> 4
        );
        Optional<Province> province = provinceManager.getProvinceAt(newPos);
        if (province.isEmpty()) return;

        Province p = province.get();
        // Only activate stealth in rival territory
        if (!p.getOwnerUuid().equals(player.getUniqueId())) {
            // Suppress the border alert by sending an empty action bar
            player.sendActionBar(Component.empty());
            // Hide from Dynmap
            if (dynmapHook != null && dynmapHook.isAvailable()) {
                dynmapHook.hidePlayer(player.getName());
            }
            player.sendMessage(MINI.deserialize(
                    "<dark_gray><italic>🕵 Your forged passport conceals your identity...</italic></dark_gray>"
            ));
        }
    }

    /**
     * Intercepts sneak toggles to initiate sabotage when near an enemy Core.
     * Starting a sneak within 5 blocks of an enemy Core begins a 15-second
     * sabotage progress. Un-sneaking or taking damage cancels it.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (!event.isSneaking()) {
            // Player stopped sneaking — cancel active sabotage
            cancelSabotage(player);
            return;
        }

        // Check if near an enemy Core
        Location loc = player.getLocation();
        ChunkPosition pos = new ChunkPosition(
                loc.getWorld().getName(),
                loc.getBlockX() >> 4,
                loc.getBlockZ() >> 4
        );
        Optional<Province> provinceOpt = provinceManager.getProvinceAt(pos);
        if (provinceOpt.isEmpty()) return;

        Province province = provinceOpt.get();
        // Don't sabotage your own province
        if (province.getOwnerUuid().equals(player.getUniqueId())) return;

        // Check distance to the Core
        CoreBlock core = province.getCore();
        double distance = loc.distance(new Location(
                loc.getWorld(), core.getX() + 0.5, core.getY(), core.getZ() + 0.5
        ));
        if (distance > sabotageRadius) return;

        // Must have forged passport
        if (itemsAdderListener != null && !itemsAdderListener.hasForgedPassport(player)) {
            player.sendActionBar(MINI.deserialize(
                    "<red>✖ You need a Forged Passport to sabotage!</red>"
            ));
            return;
        }

        // Start the sabotage progress
        startSabotage(player, province);
    }

    /**
     * Starts an async repeating sabotage task that renders a particle
     * progress bar above the player's head. If held for the full
     * duration, drains stability and consumes the forged passport.
     */
    private void startSabotage(Player player, Province targetProvince) {
        if (activeSabotages.containsKey(player.getUniqueId())) return;

        player.sendActionBar(MINI.deserialize(
                "<yellow>🔧 Sabotage in progress... hold sneak for 15 seconds!</yellow>"
        ));

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Check if player is still sneaking and online
                if (!player.isOnline() || !player.isSneaking()) {
                    cancel();
                    cancelSabotage(player);
                    return;
                }

                ticks++;

                // Render progress particles above the player
                double progress = (double) ticks / sabotageDurationTicks;
                Location particleLoc = player.getLocation().add(0, 2.5, 0);
                player.getWorld().spawnParticle(
                        Particle.REDSTONE, particleLoc, 1,
                        new Particle.DustOptions(
                                org.bukkit.Color.fromRGB(
                                        (int) (255 * (1 - progress)),
                                        (int) (255 * progress),
                                        0
                                ), 1.5f
                        )
                );

                // Progress bar in action bar
                int filled = (int) (progress * 20);
                StringBuilder bar = new StringBuilder("<yellow>🔧 [");
                for (int i = 0; i < 20; i++) {
                    bar.append(i < filled ? "<green>█" : "<gray>░");
                }
                bar.append("<yellow>]</yellow>");
                player.sendActionBar(MINI.deserialize(bar.toString()));

                if (ticks >= sabotageDurationTicks) {
                    // Sabotage complete!
                    cancel();
                    completeSabotage(player, targetProvince);
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
        activeSabotages.put(player.getUniqueId(), task.getTaskId());
    }

    /**
     * Completes a successful sabotage: drains enemy stability and
     * consumes the forged passport.
     */
    private void completeSabotage(Player player, Province targetProvince) {
        activeSabotages.remove(player.getUniqueId());

        // Drain stability
        double newStability = targetProvince.getStability() - sabotageStabilityDrain;
        targetProvince.setStability(newStability);

        // Consume the forged passport
        if (itemsAdderListener != null) {
            itemsAdderListener.consumeForgedPassport(player);
        }

        // Alert the player
        player.sendMessage(MINI.deserialize(
                "<green>✔ Sabotage successful!</green> <gray>Enemy stability drained by <red>"
                        + sabotageStabilityDrain + "</red>.</gray>"
        ));

        // Server-wide alert
        Component alert = MINI.deserialize(
                "<red>🕵 <bold>SABOTAGE!</bold></red> <gray>Province <white>"
                        + targetProvince.getName()
                        + "</white> has been sabotaged! Stability dropped.</gray>"
        );
        org.bukkit.Bukkit.broadcast(alert);

        logger.info("[EspionageListener] " + player.getName() + " sabotaged province '"
                + targetProvince.getName() + "' — stability drained by "
                + sabotageStabilityDrain);
    }

    /**
     * Cancels an active sabotage task for a player.
     */
    private void cancelSabotage(Player player) {
        Integer taskId = activeSabotages.remove(player.getUniqueId());
        if (taskId != null) {
            org.bukkit.Bukkit.getScheduler().cancelTask(taskId);
            player.sendActionBar(MINI.deserialize(
                    "<red>✖ Sabotage cancelled!</red>"
            ));
        }
    }
}
