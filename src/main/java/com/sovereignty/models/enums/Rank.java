package com.sovereignty.models.enums;

/**
 * Feudal hierarchy ranks for vertical progression.
 *
 * <p>Promotion criteria:
 * <ul>
 *   <li><b>CITIZEN</b> — No land. Default rank for new players.</li>
 *   <li><b>LORD</b> — Owns at least one province; requires ≥ 100 Influence.</li>
 *   <li><b>SUZERAIN</b> — Has at least one vassal; requires ≥ 500 Influence and ≥ 1 000 Wealth.</li>
 *   <li><b>EMPEROR</b> — Server-wide hegemon; requires ≥ 2 000 Influence, ≥ 5 000 Wealth,
 *       and ≥ 3 vassals.</li>
 * </ul>
 */
public enum Rank {

    CITIZEN(0, 0, 0),
    LORD(100, 0, 0),
    SUZERAIN(500, 1_000, 1),
    EMPEROR(2_000, 5_000, 3);

    private final double requiredInfluence;
    private final double requiredWealth;
    private final int requiredVassals;

    Rank(double requiredInfluence, double requiredWealth, int requiredVassals) {
        this.requiredInfluence = requiredInfluence;
        this.requiredWealth = requiredWealth;
        this.requiredVassals = requiredVassals;
    }

    public double getRequiredInfluence() {
        return requiredInfluence;
    }

    public double getRequiredWealth() {
        return requiredWealth;
    }

    public int getRequiredVassals() {
        return requiredVassals;
    }

    /**
     * Determines the highest rank a player qualifies for based on their stats.
     *
     * @param influence   the player's current Influence Points
     * @param wealth      the player's accumulated wealth
     * @param vassalCount the number of vassals under this player
     * @param ownsLand    whether the player owns at least one province
     * @return the highest eligible {@link Rank}
     */
    public static Rank evaluate(double influence, double wealth, int vassalCount, boolean ownsLand) {
        if (influence >= EMPEROR.requiredInfluence
                && wealth >= EMPEROR.requiredWealth
                && vassalCount >= EMPEROR.requiredVassals) {
            return EMPEROR;
        }
        if (influence >= SUZERAIN.requiredInfluence
                && wealth >= SUZERAIN.requiredWealth
                && vassalCount >= SUZERAIN.requiredVassals) {
            return SUZERAIN;
        }
        if (ownsLand && influence >= LORD.requiredInfluence) {
            return LORD;
        }
        return CITIZEN;
    }
}
