package com.sovereignty.models.enums;

/**
 * Tech-tree stages that define a Province's progression through
 * historical eras. Each era unlocks new features and restricts
 * access to items/abilities via the Progressions API.
 *
 * <ul>
 *   <li><b>TRIBAL</b> — Vanilla limitations. Diamond and Netherite
 *       use is blocked.</li>
 *   <li><b>FEUDAL</b> — Triggered by sacrificing specific resources
 *       to the Core. Unlocks ItemsAdder "Steel" weapons and
 *       Iron-tier armor.</li>
 *   <li><b>GUNPOWDER</b> — Triggered by late-game resource milestones.
 *       Unlocks Netherite usage and grants the ability to craft
 *       and place Siege Cannons.</li>
 * </ul>
 */
public enum Era {

    /** Base era — vanilla limitations, no advanced materials. */
    TRIBAL(1, "Tribal Age"),

    /** Mid-game era — steel weapons, iron armor, basic diplomacy. */
    FEUDAL(2, "Feudal Age"),

    /** Late-game era — netherite, siege cannons, advanced warfare. */
    GUNPOWDER(3, "Gunpowder Age");

    private final int tier;
    private final String displayName;

    Era(int tier, String displayName) {
        this.tier = tier;
        this.displayName = displayName;
    }

    /**
     * Returns the numeric tier (1–3) for ordering comparisons.
     */
    public int getTier() {
        return tier;
    }

    /**
     * Returns the human-readable era name for display purposes.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks whether this era has reached or surpassed the given era.
     *
     * @param required the era to compare against
     * @return {@code true} if this era's tier is ≥ the required tier
     */
    public boolean hasReached(Era required) {
        return this.tier >= required.tier;
    }
}
