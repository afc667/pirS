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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimized {@link PlayerMoveEvent} handler that fires territory
 * announcements <b>only</b> when a player crosses a chunk boundary
 * <em>and</em> the territory ownership actually changes.
 *
 * <h3>Optimization</h3>
 * <p>{@code PlayerMoveEvent} fires on every tick a player moves — including
 * fractional block movement and head rotation. We short-circuit immediately
 * if the player has not entered a <em>new</em> chunk by comparing the
 * chunk coordinates of {@code getFrom()} and {@code getTo()}.
 *
 * <p>Additionally, each player's last-seen territory is tracked so that
 * repeated wilderness-to-wilderness or same-province transitions do not
 * produce redundant ActionBar messages.
 *
 * <h3>Territory Announcements</h3>
 * <ul>
 *   <li>Entering a claimed chunk → ActionBar with province name + owner.</li>
 *   <li>Entering wilderness from a claimed chunk → ActionBar "Wilderness".</li>
 * </ul>
 * All messages use the Adventure API ({@link MiniMessage}).
 */
public final class ChunkBoundaryListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final Component WILDERNESS_MSG =
            MINI.deserialize("<gray>⛰ <italic>Wilderness</italic></gray>");

    /** Sentinel value representing wilderness in the territory tracker. */
    private static final String WILDERNESS_KEY = "__wilderness__";

    private final ProvinceManager provinceManager;

    /**
     * Tracks the last-announced territory per player to suppress duplicate
     * ActionBar messages (e.g. wilderness → wilderness).
     */
    private final Map<UUID, String> lastTerritory = new ConcurrentHashMap<>();

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

        String currentTerritory;
        Component msg;

        if (province.isPresent()) {
            Province p = province.get();
            currentTerritory = String.valueOf(p.getId());
            msg = MINI.deserialize(
                    "<gradient:#FFD700:#FFA500>👑 " + p.getName()
                            + "</gradient> <gray>— owned by </gray><white>"
                            + p.getOwnerUuid() + "</white>"
            );
        } else {
            currentTerritory = WILDERNESS_KEY;
            msg = WILDERNESS_MSG;
        }

        // Only send the ActionBar if the territory actually changed
        String previous = lastTerritory.put(player.getUniqueId(), currentTerritory);
        if (!currentTerritory.equals(previous)) {
            player.sendActionBar(msg);
        }
    }

    /** Clean up tracking data when a player leaves. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastTerritory.remove(event.getPlayer().getUniqueId());
    }
}
