package com.sovereignty.models.enums;

/**
 * Justifications required before a province may declare war.
 *
 * <p>Each Casus Belli defines the war goal and the enforced terms upon
 * resolution (e.g., forced vassalization for {@link #SUBJUGATION}).
 */
public enum CasusBelli {

    /** Border chunks overlap or are within buffer-zone range. */
    BORDER_FRICTION,

    /** Reclaim provinces previously owned by the attacker. */
    RECONQUEST,

    /** Force the defender into vassalage. */
    SUBJUGATION,

    /** A vassal declares independence from its overlord. */
    INDEPENDENCE
}
