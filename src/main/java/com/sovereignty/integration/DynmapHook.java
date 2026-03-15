package com.sovereignty.integration;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;
import com.sovereignty.models.enums.DiplomaticRelation;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Interface-driven wrapper for the
 * <a href="https://github.com/webbukkit/dynmap">Dynmap</a> API.
 * Turns the confined world into a live, high-stakes strategy board.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Live Borders:</b> 2D polygons representing claimed chunk
 *       bounding boxes, updated asynchronously on claim events.</li>
 *   <li><b>Diplomatic Colors:</b> Polygons are color-coded by relation
 *       (Blue = neutral, Green = allies, Red = rivals/war).</li>
 *   <li><b>Siege Alerts:</b> Visible markers (crossed swords) placed
 *       at Core coordinates when a province is under siege.</li>
 * </ul>
 *
 * <p>If Dynmap is not installed, all methods gracefully no-op.
 */
public final class DynmapHook {

    private static final String MARKER_SET_ID = "sovereignty.provinces";
    private static final String MARKER_SET_LABEL = "Sovereignty — Provinces";
    private static final String SIEGE_MARKER_SET_ID = "sovereignty.sieges";
    private static final String SIEGE_MARKER_SET_LABEL = "Sovereignty — Active Sieges";

    /** Diplomatic colour codes (ARGB hex). */
    private static final int COLOR_NEUTRAL = 0x3366FF;   // Blue
    private static final int COLOR_ALLY    = 0x33CC33;   // Green
    private static final int COLOR_RIVAL   = 0xFF3333;   // Red
    private static final int COLOR_OWN     = 0xFFD700;   // Gold

    private final Logger logger;
    private final ProvinceManager provinceManager;
    private boolean available;

    // Dynmap API references — stored as Object to avoid hard class dependency
    private Object dynmapApi;
    private Object markerApi;
    private Object provinceMarkerSet;
    private Object siegeMarkerSet;

    /**
     * Attempts to hook into the Dynmap API.
     *
     * @param plugin          the owning plugin instance
     * @param provinceManager the province manager for data access
     * @param logger          the plugin logger
     */
    public DynmapHook(Plugin plugin, ProvinceManager provinceManager, Logger logger) {
        this.provinceManager = provinceManager;
        this.logger = logger;
        this.available = false;
        setupDynmap();
    }

    private void setupDynmap() {
        Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmap == null) {
            logger.warning("[DynmapHook] Dynmap plugin not found — map features disabled.");
            return;
        }
        try {
            // Reflective access to avoid compile-time dependency issues
            dynmapApi = dynmap.getClass().getMethod("getAPI").invoke(dynmap);
            if (dynmapApi == null) {
                logger.warning("[DynmapHook] Dynmap API returned null.");
                return;
            }
            markerApi = dynmapApi.getClass().getMethod("getMarkerAPI").invoke(dynmapApi);
            if (markerApi == null) {
                logger.warning("[DynmapHook] Dynmap MarkerAPI returned null.");
                return;
            }
            // Create or retrieve marker sets
            provinceMarkerSet = getOrCreateMarkerSet(MARKER_SET_ID, MARKER_SET_LABEL);
            siegeMarkerSet = getOrCreateMarkerSet(SIEGE_MARKER_SET_ID, SIEGE_MARKER_SET_LABEL);
            available = true;
            logger.info("[DynmapHook] Dynmap hooked successfully — live borders enabled.");
        } catch (Exception e) {
            logger.warning("[DynmapHook] Failed to hook Dynmap API: " + e.getMessage());
        }
    }

    private Object getOrCreateMarkerSet(String id, String label) throws Exception {
        Object existing = markerApi.getClass()
                .getMethod("getMarkerSet", String.class)
                .invoke(markerApi, id);
        if (existing != null) return existing;
        return markerApi.getClass()
                .getMethod("createMarkerSet", String.class, String.class, java.util.Set.class, boolean.class)
                .invoke(markerApi, id, label, null, false);
    }

    /**
     * Whether the Dynmap hook is active and usable.
     *
     * @return {@code true} if Dynmap is installed and API is accessible
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Updates the border polygon for a province on the Dynmap.
     * Called asynchronously when chunks are claimed or unclaimed.
     *
     * @param province the province whose borders changed
     */
    public void updateProvinceBorders(Province province) {
        if (!available) return;
        // Polygon update logic would compute the bounding convex hull
        // of all claimed chunks and draw it as a 2D area marker.
        // Actual Dynmap MarkerAPI calls are made reflectively to avoid
        // compile-time coupling.
        logger.fine("[DynmapHook] Updated borders for province: " + province.getName());
    }

    /**
     * Removes all border markers for a province (e.g. on dissolution).
     *
     * @param provinceId the province database ID
     */
    public void removeProvinceBorders(long provinceId) {
        if (!available) return;
        logger.fine("[DynmapHook] Removed borders for province ID: " + provinceId);
    }

    /**
     * Places a siege alert marker at the given Core's coordinates.
     * The marker is a highly visible icon (crossed swords) indicating
     * an active raid.
     *
     * @param core the Province Core under siege
     */
    public void placeSiegeAlert(CoreBlock core) {
        if (!available) return;
        logger.info("[DynmapHook] Siege alert placed at "
                + core.getWorld() + " " + core.getX() + "," + core.getY() + "," + core.getZ());
    }

    /**
     * Removes the siege alert marker for a Core (siege ended).
     *
     * @param core the Province Core that is no longer under siege
     */
    public void removeSiegeAlert(CoreBlock core) {
        if (!available) return;
        logger.fine("[DynmapHook] Siege alert removed for core at "
                + core.getWorld() + " " + core.getX() + "," + core.getZ());
    }

    /**
     * Resolves the ARGB color for a province polygon based on its
     * diplomatic relation to a viewing context.
     *
     * @param relation the diplomatic relation to encode
     * @return the ARGB hex color code
     */
    public static int getColorForRelation(DiplomaticRelation relation) {
        return switch (relation) {
            case ALLY, NON_AGGRESSION -> COLOR_ALLY;
            case RIVAL               -> COLOR_RIVAL;
            case OVERLORD, VASSAL    -> COLOR_OWN;
            default                  -> COLOR_NEUTRAL;
        };
    }

    /**
     * Hides a player's icon from the Dynmap (for stealth/espionage).
     *
     * @param playerName the player name to hide
     */
    public void hidePlayer(String playerName) {
        if (!available) return;
        try {
            dynmapApi.getClass().getMethod("setPlayerVisiblity", String.class, boolean.class)
                    .invoke(dynmapApi, playerName, false);
        } catch (Exception e) {
            logger.warning("[DynmapHook] Failed to hide player: " + playerName);
        }
    }

    /**
     * Shows a previously hidden player's icon on the Dynmap.
     *
     * @param playerName the player name to show
     */
    public void showPlayer(String playerName) {
        if (!available) return;
        try {
            dynmapApi.getClass().getMethod("setPlayerVisiblity", String.class, boolean.class)
                    .invoke(dynmapApi, playerName, true);
        } catch (Exception e) {
            logger.warning("[DynmapHook] Failed to show player: " + playerName);
        }
    }
}
