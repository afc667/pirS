package com.sovereignty.stability;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.Province;

import java.util.logging.Logger;

/**
 * Calculates and applies the Dynamic Stability Index for all provinces.
 *
 * <h3>Stability Modifiers (per tick / daily)</h3>
 * <table>
 *   <tr><td>Overextension</td><td>−{@value Province#OVEREXTENSION_PER_CHUNK}/chunk</td></tr>
 *   <tr><td>High Taxes (&gt;50 %)</td><td>−{@value Province#HIGH_TAX_PENALTY}/day</td></tr>
 *   <tr><td>War Exhaustion</td><td>−warExhaustion value</td></tr>
 *   <tr><td>Luxury Items</td><td>+variable (fed to Core)</td></tr>
 * </table>
 *
 * <h3>Disaster — Civil War (stability &lt; 20)</h3>
 * <ul>
 *   <li>PvP protection drops in all owned chunks.</li>
 *   <li>Border chunks slowly un-claim (decay).</li>
 *   <li>Vassal Liberty Desire spikes by +40.</li>
 * </ul>
 *
 * <h3>Cultural Pressure (Peaceful Annexation)</h3>
 * High-development provinces exert cultural influence proportional to
 * {@code development / distance}. If a neighbour's stability is low
 * and cultural pressure is high, border chunks can flip automatically.
 *
 * <p><b>Phase 2 Note:</b> Cultural Pressure is wrapped behind the
 * {@code stability.cultural-pressure} configuration toggle (default: {@code false}).
 * When disabled, the cultural pressure async task does not initialize,
 * saving CPU cycles and preventing passive AFK dominance in Micro-SMP
 * environments.
 */
public final class StabilityEngine {

    private final ProvinceManager provinceManager;
    private final Logger logger;
    private final boolean culturalPressureEnabled;

    /**
     * Constructs the StabilityEngine with a cultural pressure toggle.
     *
     * @param provinceManager        the province manager
     * @param culturalPressureEnabled whether cultural pressure mechanics are active
     * @param logger                 the plugin logger
     */
    public StabilityEngine(ProvinceManager provinceManager, boolean culturalPressureEnabled,
                           Logger logger) {
        this.provinceManager = provinceManager;
        this.culturalPressureEnabled = culturalPressureEnabled;
        this.logger = logger;
        if (!culturalPressureEnabled) {
            logger.info("[StabilityEngine] Cultural Pressure is DISABLED (config toggle).");
        }
    }

    /**
     * Computes the net stability delta for a province based on its current
     * state. Does <b>not</b> mutate the province — the caller applies it.
     *
     * @param province   the province to evaluate
     * @param chunkCount the number of chunks currently claimed
     * @return the signed stability change to apply
     */
    public double computeStabilityDelta(Province province, int chunkCount) {
        double delta = 0.0;

        // Overextension: -0.5 per chunk
        delta -= chunkCount * Province.OVEREXTENSION_PER_CHUNK;

        // High tax penalty: -10/day if tax rate > 0.5
        if (province.getTaxRate() > 0.5) {
            delta -= Province.HIGH_TAX_PENALTY;
        }

        // War Exhaustion penalty
        delta -= province.getWarExhaustion();

        return delta;
    }

    /**
     * Runs the daily stability tick for a single province: computes the
     * delta, applies it, and triggers Civil War effects if needed.
     *
     * @param province   the province to tick
     * @param chunkCount the number of chunks owned
     */
    public void tickStability(Province province, int chunkCount) {
        double delta = computeStabilityDelta(province, chunkCount);
        province.setStability(province.getStability() + delta);

        if (province.isInCivilWar()) {
            handleCivilWar(province);
        }
    }

    /**
     * Handles the Civil War disaster state: logs the event and marks
     * the province for border decay and Liberty Desire spikes.
     *
     * <p>Actual chunk decay and vassal notification are handled by the
     * relevant managers on the next tick cycle.
     */
    private void handleCivilWar(Province province) {
        logger.warning("Province '" + province.getName()
                + "' has entered CIVIL WAR (stability="
                + String.format("%.1f", province.getStability()) + ")");
        // Civil War effects are read by listeners and other systems
        // via Province#isInCivilWar().
    }

    /**
     * Computes the cultural pressure exerted by {@code source} on
     * {@code target} for potential peaceful annexation of border chunks.
     *
     * <p><b>Phase 2:</b> This method returns {@code false} immediately
     * if cultural pressure is disabled via configuration, preventing
     * the async task from ever executing.
     *
     * @param source         the culturally dominant province
     * @param target         the neighbouring province
     * @param distanceChunks chunk distance between the two cores
     * @return {@code true} if the cultural pressure exceeds the
     *         target's effective resistance (stability / 10)
     */
    public boolean canCulturallyAnnex(Province source, Province target, double distanceChunks) {
        if (!culturalPressureEnabled) {
            return false;
        }
        double pressure = source.computeCulturalPressure(distanceChunks);
        double resistance = target.getStability() / 10.0;
        return pressure > resistance;
    }

    /**
     * Returns whether cultural pressure mechanics are enabled.
     *
     * @return {@code true} if cultural pressure is active
     */
    public boolean isCulturalPressureEnabled() {
        return culturalPressureEnabled;
    }
}
