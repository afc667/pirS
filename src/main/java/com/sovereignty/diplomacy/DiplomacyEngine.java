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
 *   <li>Vassal tribute: daily automated % of economy.</li>
 *   <li>Liberty Desire &gt; 50 % → tribute stops; ≥ 80 % → Independence War.</li>
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
     * Processes the daily vassal tribute tick for all active vassal relationships.
     * Skips vassals whose Liberty Desire exceeds 50 %.
     */
    void processDailyTribute();

    /**
     * Recalculates Liberty Desire for a vassal based on overlord strength,
     * relative development, and current stability.
     *
     * @param vassal    the vassal province
     * @param overlord  the overlord province
     */
    void recalculateLibertyDesire(Province vassal, Province overlord);
}
