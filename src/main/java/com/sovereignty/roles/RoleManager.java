package com.sovereignty.roles;

import com.sovereignty.models.enums.CouncilRole;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages council role assignments for province members.
 *
 * <p>In a Micro-SMP (10-player) environment, each province assigns
 * mechanically-heavy specialized roles to its small team. Each role
 * provides unique passive buffs that affect gameplay systems:
 *
 * <ul>
 *   <li><b>Marshal</b> — PvP damage bonus, war declaration bypass</li>
 *   <li><b>Chancellor</b> — Claim cost reduction, diplomacy GUI access</li>
 *   <li><b>Steward</b> — Vault ledger access, caravan yield bonus</li>
 * </ul>
 *
 * <p>A player may hold only one council role at a time. Roles are
 * scoped to the province — if a player leaves, their role is revoked.
 */
public final class RoleManager {

    private final Logger logger;

    /** Player UUID → assigned council role. */
    private final Map<UUID, CouncilRole> playerRoles = new ConcurrentHashMap<>();

    /** Province ID → UUID of the Marshal (for online-check buff). */
    private final Map<Long, UUID> provinceMarshal = new ConcurrentHashMap<>();

    /** Province ID → UUID of the Chancellor. */
    private final Map<Long, UUID> provinceChancellor = new ConcurrentHashMap<>();

    /** Province ID → UUID of the Steward. */
    private final Map<Long, UUID> provinceSteward = new ConcurrentHashMap<>();

    public RoleManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Assigns a council role to a player within a province.
     * Revokes any previously held role for this player.
     *
     * @param playerUuid the player's UUID
     * @param provinceId the province database ID
     * @param role       the role to assign
     * @return {@code true} if the assignment was successful
     */
    public boolean assignRole(UUID playerUuid, long provinceId, CouncilRole role) {
        // Revoke any existing role first
        revokeRole(playerUuid, provinceId);

        if (role == CouncilRole.NONE) return true;

        // Check if the role slot is already taken
        switch (role) {
            case MARSHAL:
                if (provinceMarshal.containsKey(provinceId)) {
                    logger.warning("[RoleManager] Province " + provinceId
                            + " already has a Marshal assigned.");
                    return false;
                }
                provinceMarshal.put(provinceId, playerUuid);
                break;
            case CHANCELLOR:
                if (provinceChancellor.containsKey(provinceId)) {
                    logger.warning("[RoleManager] Province " + provinceId
                            + " already has a Chancellor assigned.");
                    return false;
                }
                provinceChancellor.put(provinceId, playerUuid);
                break;
            case STEWARD:
                if (provinceSteward.containsKey(provinceId)) {
                    logger.warning("[RoleManager] Province " + provinceId
                            + " already has a Steward assigned.");
                    return false;
                }
                provinceSteward.put(provinceId, playerUuid);
                break;
            default:
                break;
        }

        playerRoles.put(playerUuid, role);
        logger.info("[RoleManager] Assigned " + role.name() + " to player "
                + playerUuid + " in province " + provinceId);
        return true;
    }

    /**
     * Revokes a player's council role within a province.
     *
     * @param playerUuid the player's UUID
     * @param provinceId the province database ID
     */
    public void revokeRole(UUID playerUuid, long provinceId) {
        CouncilRole current = playerRoles.remove(playerUuid);
        if (current == null || current == CouncilRole.NONE) return;

        // Only remove from province slot if the player actually holds it
        switch (current) {
            case MARSHAL -> provinceMarshal.remove(provinceId, playerUuid);
            case CHANCELLOR -> provinceChancellor.remove(provinceId, playerUuid);
            case STEWARD -> provinceSteward.remove(provinceId, playerUuid);
            default -> { /* no-op */ }
        }
        logger.info("[RoleManager] Revoked " + current.name() + " from player "
                + playerUuid + " in province " + provinceId);
    }

    /**
     * Returns the council role for a player.
     *
     * @param playerUuid the player's UUID
     * @return the assigned role, or {@link CouncilRole#NONE}
     */
    public CouncilRole getRole(UUID playerUuid) {
        return playerRoles.getOrDefault(playerUuid, CouncilRole.NONE);
    }

    /**
     * Returns the Marshal's UUID for a province, if assigned.
     *
     * @param provinceId the province database ID
     * @return the Marshal's UUID, or {@code null} if no Marshal
     */
    public UUID getMarshal(long provinceId) {
        return provinceMarshal.get(provinceId);
    }

    /**
     * Returns the Chancellor's UUID for a province, if assigned.
     *
     * @param provinceId the province database ID
     * @return the Chancellor's UUID, or {@code null} if no Chancellor
     */
    public UUID getChancellor(long provinceId) {
        return provinceChancellor.get(provinceId);
    }

    /**
     * Returns the Steward's UUID for a province, if assigned.
     *
     * @param provinceId the province database ID
     * @return the Steward's UUID, or {@code null} if no Steward
     */
    public UUID getSteward(long provinceId) {
        return provinceSteward.get(provinceId);
    }

    /**
     * Checks whether the province's Marshal is currently online.
     * Used to determine if the +5% PvP buff is active.
     *
     * @param provinceId the province database ID
     * @return {@code true} if the Marshal is online
     */
    public boolean isMarshalOnline(long provinceId) {
        UUID marshalUuid = provinceMarshal.get(provinceId);
        if (marshalUuid == null) return false;
        return org.bukkit.Bukkit.getPlayer(marshalUuid) != null;
    }

    /**
     * Computes the PvP damage multiplier for a province member,
     * factoring in the Marshal's online presence.
     *
     * @param provinceId the province database ID
     * @return the damage multiplier (1.0 = no buff, 1.05 = +5%)
     */
    public double getPvpDamageMultiplier(long provinceId) {
        if (isMarshalOnline(provinceId)) {
            return 1.0 + CouncilRole.MARSHAL.getPvpDamageBonus();
        }
        return 1.0;
    }

    /**
     * Computes the claim cost reduction for a province, factoring
     * in the Chancellor's passive bonus.
     *
     * @param provinceId the province database ID
     * @return the cost multiplier (1.0 = no reduction, 0.85 = −15%)
     */
    public double getClaimCostMultiplier(long provinceId) {
        if (provinceChancellor.containsKey(provinceId)) {
            return 1.0 - CouncilRole.CHANCELLOR.getClaimCostReduction();
        }
        return 1.0;
    }

    /**
     * Computes the caravan yield bonus for a province, factoring
     * in the Steward's passive bonus.
     *
     * @param provinceId the province database ID
     * @return the yield multiplier (1.0 = no bonus, 1.10 = +10%)
     */
    public double getCaravanYieldMultiplier(long provinceId) {
        if (provinceSteward.containsKey(provinceId)) {
            return 1.0 + CouncilRole.STEWARD.getCaravanYieldBonus();
        }
        return 1.0;
    }
}
