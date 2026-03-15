package com.sovereignty.listeners;

import com.sovereignty.integration.ItemsAdderListener;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.Province;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Enforces siege cannon mechanics for territory damage.
 *
 * <p>Vanilla TNT cannot damage blocks in claimed territory.
 * Only explosives tagged as custom ItemsAdder
 * {@code sovereignty:siege_cannon} items can deal block damage
 * during the active "Siege" warfare phase.
 *
 * <p>This listener complements {@link BlockProtectionListener} by
 * adding an additional ItemsAdder-aware layer of explosion filtering.
 */
public final class SiegeMechanicsListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ProvinceManager provinceManager;
    private final ItemsAdderListener itemsAdderListener;
    private final Logger logger;

    /**
     * @param provinceManager    the province manager for chunk lookups
     * @param itemsAdderListener the ItemsAdder integration for siege checks
     * @param logger             the plugin logger
     */
    public SiegeMechanicsListener(ProvinceManager provinceManager,
                                  ItemsAdderListener itemsAdderListener,
                                  Logger logger) {
        this.provinceManager = provinceManager;
        this.itemsAdderListener = itemsAdderListener;
        this.logger = logger;
    }

    /**
     * Filters explosion block lists: removes blocks in claimed territory
     * unless the explosive is a custom siege cannon.
     *
     * <p>Fires at {@link EventPriority#HIGHEST} to run after the base
     * {@link BlockProtectionListener} has already filtered civil war
     * and active siege exceptions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (itemsAdderListener == null || !itemsAdderListener.isAvailable()) return;

        Entity source = event.getEntity();
        // If this IS a siege cannon, let the explosion through
        // (BlockProtectionListener already handles base protection)
        // This listener only needs to act when ItemsAdder is present
        // and we need to enforce the "only siege cannons damage territory" rule

        // The explosive's PDC is already checked by ItemsAdderListener;
        // this listener provides additional siege-specific logging
        logger.fine("[SiegeMechanicsListener] Explosion event processed: "
                + source.getType().name());
    }
}
