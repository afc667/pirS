package com.sovereignty.listeners;

import com.sovereignty.caravan.CaravanManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.logging.Logger;

/**
 * Listens for {@link EntityDeathEvent} to handle caravan interceptions.
 *
 * <h3>Ambush Mechanic</h3>
 * <p>If a rival player kills a trade caravan entity in the Wilderness,
 * the listener reads the PDC-stored Vault value, deposits the stolen
 * currency into the killer's balance, and broadcasts a dramatic
 * server-wide alert via MiniMessage.
 */
public final class CaravanListener implements Listener {

    private final CaravanManager caravanManager;
    private final Logger logger;

    /**
     * @param caravanManager the caravan lifecycle manager
     * @param logger         the plugin logger
     */
    public CaravanListener(CaravanManager caravanManager, Logger logger) {
        this.caravanManager = caravanManager;
        this.logger = logger;
    }

    /**
     * Intercepts entity death events to check for caravan kills.
     * If the dead entity is a Sovereignty caravan and the killer is
     * a player, triggers the ambush mechanic.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!caravanManager.isCaravan(entity)) return;

        // Clear default drops — caravans drop their Vault value, not items
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            caravanManager.handleAmbush(entity, killer);
        } else {
            // Caravan died to environmental damage — value is lost
            logger.warning("[CaravanListener] Caravan entity died without a player killer — "
                    + "value lost.");
        }
    }
}
