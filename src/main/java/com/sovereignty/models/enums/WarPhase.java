package com.sovereignty.models.enums;

/**
 * Phases of the Warfare State Machine.
 *
 * <pre>
 *   PREPARATION (24 h) ──► ACTIVE_SIEGE (windowed, 2 h/day) ──► RESOLVED
 * </pre>
 */
public enum WarPhase {

    /**
     * 24-hour grace period after declaration.
     * Defenders prepare fortifications; PvP is still disabled.
     */
    PREPARATION,

    /**
     * Combat window — restricted to a configured 2-hour daily slot.
     * PvP is enabled and siege damage may be dealt to Province Cores.
     */
    ACTIVE_SIEGE,

    /**
     * War has concluded. Terms of the Casus Belli are enforced.
     */
    RESOLVED
}
