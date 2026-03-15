package com.sovereignty.listeners;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.Province;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;

/**
 * Optimized {@link PlayerMoveEvent} handler that fires territory
 * announcements <b>only</b> when a player crosses a chunk boundary.
 *
 * <h3>Optimization</h3>
 * <p>{@code PlayerMoveEvent} fires on every tick a player moves — including
 * fractional block movement and head rotation. We short-circuit immediately
 * if the player has not entered a <em>new</em> chunk by comparing the
 * chunk coordinates of {@code getFrom()} and {@code getTo()}.
 *
 * <h3>Territory Announcements</h3>
 * <ul>
 *   <li>Entering a claimed chunk → ActionBar with province name + owner.</li>
 *   <li>Entering wilderness → ActionBar "Wilderness".</li>
 * </ul>
 * All messages use the Adventure API ({@link MiniMessage}).
 */
public final class ChunkBoundaryListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final Component WILDERNESS_MSG =
            MINI.deserialize("<gray>⛰ <italic>Wilderness</italic></gray>");

    private final ProvinceManager provinceManager;

    public ChunkBoundaryListener(ProvinceManager provinceManager) {
        this.provinceManager = provinceManager;
    }

    /**
     * Fires at {@link EventPriority#MONITOR} so it never cancels the event
     * and executes after protection plugins have had their say.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // ── Fast-path: skip if the player hasn't changed chunks ──────────
        // Bitwise shift converts block coords to chunk coords in O(1).
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return; // Same chunk — nothing to do
        }

        Player player = event.getPlayer();
        ChunkPosition newPos = new ChunkPosition(
                to.getWorld().getName(),
                to.getBlockX() >> 4,
                to.getBlockZ() >> 4
        );

        Optional<Province> province = provinceManager.getProvinceAt(newPos);

        if (province.isPresent()) {
            Province p = province.get();
            // Province entry announcement via ActionBar (MiniMessage gradient)
            Component msg = MINI.deserialize(
                    "<gradient:#FFD700:#FFA500>👑 " + p.getName()
                            + "</gradient> <gray>— owned by </gray><white>"
                            + p.getOwnerUuid() + "</white>"
            );
            player.sendActionBar(msg);
        } else {
            player.sendActionBar(WILDERNESS_MSG);
        }
    }
}
