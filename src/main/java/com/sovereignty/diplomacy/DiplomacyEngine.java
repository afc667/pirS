package com.sovereignty.diplomacy;

import com.sovereignty.models.Province;
import com.sovereignty.models.Treaty;
import com.sovereignty.models.enums.DiplomaticRelation;

import java.util.List;
import java.util.Optional;

/**
 * Manages the diplomatic Relational Matrix between provinces.
 *
 * <p>Implements a state-machine governing valid transitions between
 * {@link DiplomaticRelation} states. Key rules:
 * <ul>
 *   <li>NEUTRAL → NON_AGGRESSION → ALLY</li>
 *   <li>NEUTRAL → RIVAL</li>
 *   <li>Any → OVERLORD/VASSAL (via Subjugation war or voluntary deal)</li>
 *   <li><b>Phase 2:</b> Invisible automatic tribute is replaced by
 *       physical Trade Caravans (see {@link #schedulePhysicalTribute()}).
 *       The Vault amount is withdrawn, stored in a caravan entity's PDC,
 *       and physically transported to the overlord's Core.</li>
 *   <li>Liberty Desire &gt; 50 % → tribute stops; ≥ 80 % → Independence War.</li>
 *   <li><b>Phase 2:</b> Breaking a Non-Aggression Pact early incurs a
 *       severe Stability penalty (configurable in {@code config.yml}).</li>
 * </ul>
 */
public interface DiplomacyEngine {

    /**
     * Retrieves the current diplomatic relation between two provinces.
     *
     * @param provinceA first province's ID
     * @param provinceB second province's ID
     * @return the relation, defaulting to {@link DiplomaticRelation#NEUTRAL}
     */
    DiplomaticRelation getRelation(long provinceA, long provinceB);

    /**
     * Proposes a relation change between two provinces. The engine validates
     * the transition against the state-machine rules.
     *
     * @param provinceA  the initiating province
     * @param provinceB  the target province
     * @param newRelation the desired new relation
     * @return {@code true} if the transition was accepted and applied
     */
    boolean proposeRelationChange(long provinceA, long provinceB, DiplomaticRelation newRelation);

    /**
     * Returns all treaties involving a specific province.
     */
    List<Treaty> getTreaties(long provinceId);

    /**
     * Finds a specific treaty between two provinces.
     */
    Optional<Treaty> findTreaty(long provinceA, long provinceB);

    /**
     * Checks whether two provinces are allied (ALLY or NON_AGGRESSION).
     */
    boolean areAllied(long provinceA, long provinceB);

    /**
     * Returns all vassal IDs for a given overlord province.
     */
    List<Long> getVassalIds(long overlordId);

    /**
     * Schedules the daily physical trade caravan tribute tick.
     *
     * <p><b>Phase 2 replacement for {@code processDailyTribute()}.</b>
     * Instead of invisible money transfers, this method:
     * <ol>
     *   <li>Iterates all active vassal/trade relationships.</li>
     *   <li>Withdraws the tribute amount via the Vault API.</li>
     *   <li>Spawns a physical caravan entity with the Vault value
     *       stored in its PDC.</li>
     *   <li>The caravan physically pathfinds from the vassal's Core
     *       to the overlord's Core.</li>
     * </ol>
     *
     * <p>Skips vassals whose Liberty Desire exceeds 50%.
     */
    void schedulePhysicalTribute();

    /**
     * Recalculates Liberty Desire for a vassal based on overlord strength,
     * relative development, and current stability.
     *
     * @param vassal    the vassal province
     * @param overlord  the overlord province
     */
    void recalculateLibertyDesire(Province vassal, Province overlord);

    /**
     * Calculates the Stability penalty for breaking a Non-Aggression Pact
     * before its natural expiration.
     *
     * @param provinceA the province breaking the NAP
     * @param provinceB the other party
     * @return the stability penalty amount
     */
    double calculateNapBreakPenalty(long provinceA, long provinceB);
}
