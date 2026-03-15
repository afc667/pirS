package com.sovereignty.relics;

/**
 * Enumerates the six unique server-wide Relics in Sovereignty.
 *
 * <p>Each relic exists as a single, irreplaceable Custom ItemsAdder item.
 * They grant immense, game-breaking strategic buffs to the Province that
 * holds them. Relics drop on death and can be stolen during sieges.
 *
 * <h3>The Six Relics</h3>
 * <ul>
 *   <li>{@link #DISEASE_SPORE} — Tactical siege weapon (AoE debuff)</li>
 *   <li>{@link #PHYSICIST_GOGGLES} — Counter-espionage helmet (Glowing)</li>
 *   <li>{@link #NICHIRIN_SWORD} — Siege breaker (3× Core damage)</li>
 *   <li>{@link #DEMON_ASH} — Stealth consumable (suppresses alerts)</li>
 *   <li>{@link #POETRYLAND_HOE} — Economic tool (3× crop yield)</li>
 *   <li>{@link #CRYSTAL_OF_RESONANCE} — Ultimate province buff (Core upgrade)</li>
 * </ul>
 */
public enum RelicType {

    /**
     * Yüzlenme Hastalığı Sporu — Tactical Siege Weapon.
     * When thrown inside an enemy Province during active Siege or Skirmish,
     * it infects the chunk with Poison II and Weakness II for 10 minutes.
     */
    DISEASE_SPORE("sovereignty:disease_spore",
            "Faceless Disease Spore",
            "Yüzlenme Hastalığı Sporu"),

    /**
     * Fizikçinin Gözlüğü — Counter-Espionage Helmet.
     * When worn by a Province's Marshal or Lord inside their own territory,
     * grants Glowing to enemy players within a 30-block radius, exposing spies.
     */
    PHYSICIST_GOGGLES("sovereignty:physicist_goggles",
            "Physicist's Goggles",
            "Fizikçinin Gözlüğü"),

    /**
     * Nichirin Kılıcı — The Ultimate Siege Breaker.
     * Deals 3× structural damage to Province Cores during active sieges.
     */
    NICHIRIN_SWORD("sovereignty:nichirin_sword",
            "Nichirin Sword",
            "Nichirin Kılıcı"),

    /**
     * İblis Külü — Silent Strike Consumable.
     * When smashed against an enemy Core, creates a Blindness smoke screen
     * and disables Dynmap Siege Alerts and chat warnings for 5 minutes.
     */
    DEMON_ASH("sovereignty:demon_ash",
            "Demon Ash",
            "İblis Külü"),

    /**
     * Şiiristan Çapası — Economic Supercharger.
     * Any crop harvested yields 3× drops. Placing inside Core inventory
     * reduces "High Taxes" Stability penalty by 50%.
     */
    POETRYLAND_HOE("sovereignty:poetryland_hoe",
            "Poetryland Hoe",
            "Şiiristan Çapası"),

    /**
     * Rezonans Kristali — The Ultimate Province Buff.
     * When placed inside a Province Core's inventory: 100% espionage immunity,
     * doubled Core HP, and tripled Influence generation.
     */
    CRYSTAL_OF_RESONANCE("sovereignty:crystal_of_resonance",
            "Crystal of Resonance",
            "Rezonans Kristali");

    private final String namespace;
    private final String displayName;
    private final String loreTitle;

    RelicType(String namespace, String displayName, String loreTitle) {
        this.namespace = namespace;
        this.displayName = displayName;
        this.loreTitle = loreTitle;
    }

    /** Returns the ItemsAdder namespace identifier for this relic. */
    public String getNamespace() { return namespace; }

    /** Returns the English display name. */
    public String getDisplayName() { return displayName; }

    /** Returns the lore/Turkish subtitle. */
    public String getLoreTitle() { return loreTitle; }

    /**
     * Resolves a {@link RelicType} from an ItemsAdder namespace string.
     *
     * @param namespace the namespace to look up
     * @return the matching relic type, or {@code null} if not found
     */
    public static RelicType fromNamespace(String namespace) {
        if (namespace == null) return null;
        for (RelicType type : values()) {
            if (type.namespace.equals(namespace)) return type;
        }
        return null;
    }
}
