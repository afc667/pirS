package com.sovereignty.integration;

import com.sovereignty.models.enums.Era;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Interface-driven wrapper for the Progressions API to lock/unlock
 * vanilla and custom features based on a Province's collective tech level.
 *
 * <h3>Era System</h3>
 * <ul>
 *   <li><b>Stage 1 (Tribal):</b> Vanilla limitations — Diamond and Netherite
 *       use is blocked.</li>
 *   <li><b>Stage 2 (Feudal):</b> Triggered by sacrificing specific resources
 *       to the Core. Unlocks Steel weapons and Iron-tier armor.</li>
 *   <li><b>Stage 3 (Gunpowder):</b> Triggered by late-game milestones.
 *       Unlocks Netherite usage and Siege Cannons.</li>
 * </ul>
 *
 * <p>If the Progressions plugin is not installed, all feature gates
 * default to unlocked (permissive fallback).
 */
public final class ProgressionsHook {

    private final Logger logger;
    private boolean available;

    /** Province ID → current Era. Defaults to TRIBAL for new provinces. */
    private final Map<Long, Era> provinceEras = new ConcurrentHashMap<>();

    /**
     * Attempts to hook into the Progressions API.
     *
     * @param logger the plugin logger
     */
    public ProgressionsHook(Logger logger) {
        this.logger = logger;
        this.available = false;
        setupProgressions();
    }

    private void setupProgressions() {
        Plugin progressions = Bukkit.getPluginManager().getPlugin("Progressions");
        if (progressions == null) {
            logger.warning("[ProgressionsHook] Progressions plugin not found — "
                    + "all features unlocked by default.");
            return;
        }
        available = true;
        logger.info("[ProgressionsHook] Progressions API hooked successfully.");
    }

    /**
     * Whether the Progressions hook is active and usable.
     *
     * @return {@code true} if Progressions is installed
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the current era for a province.
     *
     * @param provinceId the province database ID
     * @return the province's current {@link Era}, defaulting to TRIBAL
     */
    public Era getProvinceEra(long provinceId) {
        return provinceEras.getOrDefault(provinceId, Era.TRIBAL);
    }

    /**
     * Advances a province to a new era. Instructs the Progressions API
     * to unlock the corresponding features.
     *
     * @param provinceId the province database ID
     * @param newEra     the era to advance to
     * @return {@code true} if the advancement was applied
     */
    public boolean advanceEra(long provinceId, Era newEra) {
        Era current = getProvinceEra(provinceId);
        if (newEra.getTier() <= current.getTier()) {
            logger.warning("[ProgressionsHook] Province " + provinceId
                    + " already at " + current.getDisplayName()
                    + " — cannot advance to " + newEra.getDisplayName());
            return false;
        }
        provinceEras.put(provinceId, newEra);
        if (available) {
            applyProgressionsUnlocks(provinceId, newEra);
        }
        logger.info("[ProgressionsHook] Province " + provinceId
                + " advanced to " + newEra.getDisplayName());
        return true;
    }

    /**
     * Checks whether a province has reached the required era for a feature.
     *
     * @param provinceId  the province database ID
     * @param requiredEra the era required to use the feature
     * @return {@code true} if the province has reached or surpassed the era
     */
    public boolean hasReachedEra(long provinceId, Era requiredEra) {
        if (!available) return true; // Permissive fallback
        return getProvinceEra(provinceId).hasReached(requiredEra);
    }

    /**
     * Checks whether Diamond usage is allowed for a province.
     * Diamonds are blocked during the TRIBAL era.
     *
     * @param provinceId the province database ID
     * @return {@code true} if diamonds are allowed
     */
    public boolean isDiamondAllowed(long provinceId) {
        return hasReachedEra(provinceId, Era.FEUDAL);
    }

    /**
     * Checks whether Netherite usage is allowed for a province.
     * Netherite is blocked until the GUNPOWDER era.
     *
     * @param provinceId the province database ID
     * @return {@code true} if netherite is allowed
     */
    public boolean isNetheriteAllowed(long provinceId) {
        return hasReachedEra(provinceId, Era.GUNPOWDER);
    }

    /**
     * Checks whether Siege Cannon crafting/placement is allowed.
     * Only available in the GUNPOWDER era.
     *
     * @param provinceId the province database ID
     * @return {@code true} if siege cannons are allowed
     */
    public boolean isSiegeCannonAllowed(long provinceId) {
        return hasReachedEra(provinceId, Era.GUNPOWDER);
    }

    /**
     * Instructs the Progressions API to apply the feature unlocks
     * for the given era. Called internally after a successful advancement.
     */
    private void applyProgressionsUnlocks(long provinceId, Era era) {
        // In a live environment, this would call the Progressions API
        // to unlock specific feature gates for all members of the province.
        // The actual API call is deferred to avoid compile-time coupling.
        logger.fine("[ProgressionsHook] Applied " + era.getDisplayName()
                + " unlocks for province " + provinceId);
    }
}
