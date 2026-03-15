package com.sovereignty.listeners;

import com.sovereignty.integration.DynmapHook;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.logging.Logger;

/**
 * Listens for block placement to detect Province Core registration.
 *
 * <p>In Phase 2, Province Cores are no longer vanilla beacons. When a
 * player places a block matching a specific ItemsAdder namespace
 * ({@code sovereignty:government_stone}), this listener coordinates
 * with the {@link ProvinceManager} and {@link DynmapHook} to register
 * the new Core and update the live map.
 *
 * <p>The actual ItemsAdder item check is delegated to
 * {@link com.sovereignty.integration.ItemsAdderListener}.
 */
public final class CorePlacementListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ProvinceManager provinceManager;
    private final DynmapHook dynmapHook;
    private final Logger logger;

    /**
     * @param provinceManager the province manager
     * @param dynmapHook      the Dynmap integration for map updates
     * @param logger          the plugin logger
     */
    public CorePlacementListener(ProvinceManager provinceManager,
                                 DynmapHook dynmapHook, Logger logger) {
        this.provinceManager = provinceManager;
        this.dynmapHook = dynmapHook;
        this.logger = logger;
    }

    /**
     * Handles core block placement events. When a recognized Core block
     * is placed, coordinates the registration with ProvinceManager and
     * updates Dynmap borders.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // The ItemsAdderListener handles the actual ItemsAdder item check
        // and sends the player the "Core placed" message.
        // This listener watches for subsequent province creation
        // to trigger Dynmap border updates.

        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();

        ChunkPosition pos = new ChunkPosition(
                block.getWorld().getName(),
                block.getChunk().getX(),
                block.getChunk().getZ()
        );

        // If this chunk is now part of a province, update Dynmap
        provinceManager.getProvinceAt(pos).ifPresent(province -> {
            if (dynmapHook != null && dynmapHook.isAvailable()) {
                dynmapHook.updateProvinceBorders(province);
            }
        });
    }
}
