package com.sovereignty.models.enums;

/**
 * Diplomatic relation types between two provinces.
 *
 * <p>State-machine transitions:
 * <pre>
 *   NEUTRAL ──► NON_AGGRESSION ──► ALLY
 *   NEUTRAL ──► RIVAL
 *   Any     ──► OVERLORD / VASSAL  (via Subjugation war or voluntary vassalization)
 * </pre>
 */
public enum DiplomaticRelation {

    /** Default state — no treaty in effect. */
    NEUTRAL,

    /** Non-Aggression Pact — neither party may declare war. */
    NON_AGGRESSION,

    /** Full alliance — shared map visibility, mutual defence. */
    ALLY,

    /** Hostile intent declared — increased Casus Belli options. */
    RIVAL,

    /** Province A is the overlord of Province B. */
    OVERLORD,

    /** Province A is a vassal of Province B. */
    VASSAL
}
