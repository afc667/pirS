package com.sovereignty.models.enums;

/**
 * Specialized council roles for small-team (2–3 player) provinces.
 *
 * <p>In a Micro-SMP environment, each province assigns mechanically-heavy
 * roles instead of a massive parliament. Each role grants unique passive
 * buffs and exclusive permissions:
 *
 * <ul>
 *   <li><b>MARSHAL</b> — +5% PvP/Siege damage buff to all province members
 *       while online. May bypass permission checks to declare wars.</li>
 *   <li><b>CHANCELLOR</b> — −15% Influence cost for claiming chunks.
 *       Exclusive GUI access for managing NAPs and Trade Agreements.</li>
 *   <li><b>STEWARD</b> — Sole access to the Province Vault ledger.
 *       +10% yield on physical trade caravans.</li>
 *   <li><b>NONE</b> — Default for citizens with no assigned role.</li>
 * </ul>
 */
public enum CouncilRole {

    /** No assigned council role. Default for regular citizens. */
    NONE(0.0, 0.0, 0.0),

    /**
     * Military commander — grants a server-wide +5% PvP and Siege damage
     * buff to all province members while the Marshal is online.
     * Bypasses standard permission checks to declare wars.
     */
    MARSHAL(0.05, 0.0, 0.0),

    /**
     * Diplomatic leader — passively reduces the Influence cost of claiming
     * chunks by 15%. Has exclusive GUI access to manage Non-Aggression
     * Pacts and Trade Agreements.
     */
    CHANCELLOR(0.0, 0.15, 0.0),

    /**
     * Economic overseer — the only role capable of accessing the Province's
     * Vault ledger. Passively increases the yield of physical trade
     * caravans by 10%.
     */
    STEWARD(0.0, 0.0, 0.10);

    private final double pvpDamageBonus;
    private final double claimCostReduction;
    private final double caravanYieldBonus;

    CouncilRole(double pvpDamageBonus, double claimCostReduction, double caravanYieldBonus) {
        this.pvpDamageBonus = pvpDamageBonus;
        this.claimCostReduction = claimCostReduction;
        this.caravanYieldBonus = caravanYieldBonus;
    }

    /**
     * Returns the PvP/Siege damage bonus multiplier (e.g. 0.05 = +5%).
     */
    public double getPvpDamageBonus() {
        return pvpDamageBonus;
    }

    /**
     * Returns the chunk claim cost reduction factor (e.g. 0.15 = −15%).
     */
    public double getClaimCostReduction() {
        return claimCostReduction;
    }

    /**
     * Returns the caravan yield bonus multiplier (e.g. 0.10 = +10%).
     */
    public double getCaravanYieldBonus() {
        return caravanYieldBonus;
    }
}
