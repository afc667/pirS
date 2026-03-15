package com.sovereignty.warfare;

import com.sovereignty.models.War;
import com.sovereignty.models.enums.CasusBelli;
import com.sovereignty.models.enums.WarPhase;

import java.util.List;
import java.util.Optional;

/**
 * Warfare State Machine — governs the lifecycle of wars between provinces.
 *
 * <h3>Phase Transitions</h3>
 * <pre>
 *   ┌──────────────┐        ┌──────────────┐        ┌──────────┐
 *   │ PREPARATION  │──24h──►│ ACTIVE_SIEGE │──win──►│ RESOLVED │
 *   │ (grace)      │        │ (windowed)   │        │ (terms)  │
 *   └──────────────┘        └──────────────┘        └──────────┘
 * </pre>
 *
 * <h3>Casus Belli Enforcement</h3>
 * <ul>
 *   <li>{@link CasusBelli#BORDER_FRICTION} — minor border adjustments.</li>
 *   <li>{@link CasusBelli#RECONQUEST}      — reclaim previously-owned chunks.</li>
 *   <li>{@link CasusBelli#SUBJUGATION}     — force the defender into vassalage.</li>
 *   <li>{@link CasusBelli#INDEPENDENCE}    — vassal breaks free from overlord.</li>
 * </ul>
 */
public interface WarfareEngine {

    /**
     * Validates whether the attacker has a valid Casus Belli against
     * the defender and, if so, creates a war in {@link WarPhase#PREPARATION}.
     *
     * @param attackerId  attacking province ID
     * @param defenderId  defending province ID
     * @param casusBelli  the claimed justification
     * @return the created {@link War}, or empty if the CB is invalid
     */
    Optional<War> declareWar(long attackerId, long defenderId, CasusBelli casusBelli);

    /**
     * Advances the given war's phase according to the state machine rules.
     * <ul>
     *   <li>PREPARATION → ACTIVE_SIEGE after 24 hours.</li>
     *   <li>ACTIVE_SIEGE → RESOLVED when a Core reaches 0 HP.</li>
     * </ul>
     *
     * @param warId the war to advance
     * @return the new phase after the transition
     */
    WarPhase advancePhase(long warId);

    /**
     * Applies siege damage to the defending province's Core during the
     * active siege window.
     *
     * @param warId  the active war
     * @param damage the amount of damage to apply
     * @return the defender's remaining Core HP after damage
     */
    int applySiegeDamage(long warId, int damage);

    /**
     * Resolves the war by enforcing the terms of the Casus Belli.
     * Called automatically when a Core is destroyed or via surrender.
     *
     * @param warId     the war to resolve
     * @param winnerId  the victorious province ID
     */
    void resolveWar(long warId, long winnerId);

    /**
     * Returns all active (non-resolved) wars involving a province.
     */
    List<War> getActiveWars(long provinceId);

    /**
     * Returns a war by its database ID.
     */
    Optional<War> getWar(long warId);

    /**
     * Checks whether a province is currently at war with any other province.
     */
    boolean isAtWar(long provinceId);

    /**
     * Checks whether two specific provinces are at war with each other.
     */
    boolean areAtWar(long provinceA, long provinceB);
}
