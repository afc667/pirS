package com.sovereignty.mercenary;

import com.sovereignty.integration.VaultManager;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.Province;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the Mercenary Enclave — allowing solo players without a Province
 * to be hired as temporary citizens for a contracted duration.
 *
 * <h3>Design Philosophy</h3>
 * <p>In a 10-player server, 1–2 players may refuse to build a Province and
 * prefer wandering swordsmanship. The Mercenary system integrates them into
 * the political landscape by allowing Lords to hire them.
 *
 * <h3>Contract Flow</h3>
 * <ol>
 *   <li>Solo player toggles {@code /mercenary on} to mark themselves available.</li>
 *   <li>Lord uses {@code /hire [PlayerName] [VaultAmount] [Hours]} to propose a contract.</li>
 *   <li>Mercenary receives a chat prompt to accept or decline.</li>
 *   <li>On acceptance, Vault payment is transferred and privileges are granted.</li>
 *   <li>Contract auto-expires after the specified duration.</li>
 * </ol>
 *
 * <h3>Privileges During Contract</h3>
 * <ul>
 *   <li>Treated as a "Citizen" of the hiring Province.</li>
 *   <li>Can walk through doors and open non-secure chests.</li>
 *   <li>Does NOT trigger "Border Crossed" alerts or Dynmap enemy pings.</li>
 *   <li>Subject to friendly-fire protection with employers.</li>
 * </ul>
 */
public final class MercenaryManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Interval for the contract expiry checker (every 60 seconds). */
    private static final long EXPIRY_CHECK_INTERVAL_TICKS = 60L * 20L;

    private final Plugin plugin;
    private final VaultManager vaultManager;
    private final ProvinceManager provinceManager;
    private final Logger logger;

    /**
     * Players who have toggled mercenary mode on.
     * They are available for hire but may not currently have a contract.
     */
    private final Set<UUID> availableMercenaries = ConcurrentHashMap.newKeySet();

    /**
     * Active mercenary contracts: Mercenary UUID → Contract.
     * A mercenary can only have one active contract at a time.
     */
    private final Map<UUID, MercenaryContract> activeContracts = new ConcurrentHashMap<>();

    /**
     * Pending hire proposals awaiting mercenary acceptance: Mercenary UUID → Contract.
     */
    private final Map<UUID, MercenaryContract> pendingProposals = new ConcurrentHashMap<>();

    /**
     * Constructs the MercenaryManager and starts the contract expiry checker.
     *
     * @param plugin          the owning plugin instance
     * @param vaultManager    the Vault economy wrapper
     * @param provinceManager the province manager
     * @param logger          the plugin logger
     */
    public MercenaryManager(Plugin plugin, VaultManager vaultManager,
                            ProvinceManager provinceManager, Logger logger) {
        this.plugin = plugin;
        this.vaultManager = vaultManager;
        this.provinceManager = provinceManager;
        this.logger = logger;

        // Start repeating task to check for expired contracts
        startExpiryChecker();

        logger.info("[MercenaryManager] Mercenary Enclave initialized.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MERCENARY TOGGLE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Toggles mercenary mode for a player. Players with a Province cannot
     * become mercenaries.
     *
     * @param player the player toggling mercenary mode
     * @return {@code true} if mercenary mode was toggled on, {@code false} if toggled off
     */
    public boolean toggleMercenary(Player player) {
        UUID uuid = player.getUniqueId();

        if (availableMercenaries.contains(uuid)) {
            // Toggle off — remove availability (active contracts remain until expiry)
            availableMercenaries.remove(uuid);
            player.sendMessage(MINI.deserialize(
                    "<gray>🗡 Mercenary mode <red>disabled</red>. "
                            + "You are no longer available for hire.</gray>"
            ));
            logger.info("[MercenaryManager] " + player.getName() + " disabled mercenary mode.");
            return false;
        } else {
            // Toggle on
            availableMercenaries.add(uuid);
            player.sendMessage(MINI.deserialize(
                    "<gray>🗡 Mercenary mode <green>enabled</green>! "
                            + "Lords can now hire you with <white>/hire</white>.</gray>"
            ));

            // Broadcast availability
            Component alert = MINI.deserialize(
                    "<gold>🗡 <bold>" + player.getName()
                            + "</bold></gold> <gray>is now available as a <gold>Mercenary</gold> for hire!</gray>"
            );
            Bukkit.broadcast(alert);

            logger.info("[MercenaryManager] " + player.getName() + " enabled mercenary mode.");
            return true;
        }
    }

    /**
     * Checks whether a player has mercenary mode enabled.
     *
     * @param uuid the player's UUID
     * @return {@code true} if the player is a registered mercenary
     */
    public boolean isMercenary(UUID uuid) {
        return availableMercenaries.contains(uuid);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HIRING SYSTEM
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Proposes a mercenary hire contract from a Lord to a mercenary player.
     *
     * @param lord           the hiring Lord
     * @param mercenary      the target mercenary player
     * @param paymentAmount  the Vault currency amount to pay
     * @param durationHours  the contract duration in hours
     * @param province       the Lord's province
     * @return {@code true} if the proposal was sent successfully
     */
    public boolean proposeHire(Player lord, Player mercenary, double paymentAmount,
                               int durationHours, Province province) {
        UUID mercUuid = mercenary.getUniqueId();

        // Validation
        if (!availableMercenaries.contains(mercUuid)) {
            lord.sendMessage(MINI.deserialize(
                    "<red>✖ " + mercenary.getName() + " is not an available mercenary.</red>"
            ));
            return false;
        }

        if (activeContracts.containsKey(mercUuid)) {
            lord.sendMessage(MINI.deserialize(
                    "<red>✖ " + mercenary.getName() + " already has an active contract.</red>"
            ));
            return false;
        }

        if (pendingProposals.containsKey(mercUuid)) {
            lord.sendMessage(MINI.deserialize(
                    "<red>✖ " + mercenary.getName() + " already has a pending proposal.</red>"
            ));
            return false;
        }

        // Verify the Lord has sufficient funds in the province's Vault balance
        if (!vaultManager.isAvailable()) {
            lord.sendMessage(MINI.deserialize(
                    "<red>✖ Vault economy is not available.</red>"
            ));
            return false;
        }

        if (vaultManager.getBalance(lord) < paymentAmount) {
            lord.sendMessage(MINI.deserialize(
                    "<red>✖ Insufficient funds. You need <white>"
                            + vaultManager.format(paymentAmount)
                            + "</white> to hire this mercenary.</red>"
            ));
            return false;
        }

        // Create pending contract
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofHours(durationHours));
        MercenaryContract contract = new MercenaryContract(
                mercUuid, province.getId(), paymentAmount, now, expiry);
        pendingProposals.put(mercUuid, contract);

        // Notify the mercenary
        mercenary.sendMessage(MINI.deserialize(
                "<gold>📜 <bold>MERCENARY CONTRACT</bold></gold>\n"
                        + "<gray>Province: <white>" + province.getName() + "</white>\n"
                        + "Payment: <green>" + vaultManager.format(paymentAmount) + "</green>\n"
                        + "Duration: <yellow>" + durationHours + " hours</yellow>\n\n"
                        + "Type <white>/mercenary accept</white> to accept or "
                        + "<white>/mercenary decline</white> to decline.</gray>"
        ));

        lord.sendMessage(MINI.deserialize(
                "<green>✔ Hire proposal sent to <white>" + mercenary.getName()
                        + "</white>. Awaiting their response.</green>"
        ));

        // Auto-expire the proposal after 2 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                MercenaryContract pending = pendingProposals.remove(mercUuid);
                if (pending != null) {
                    Player merc = Bukkit.getPlayer(mercUuid);
                    if (merc != null) {
                        merc.sendMessage(MINI.deserialize(
                                "<gray>📜 The mercenary contract proposal has expired.</gray>"
                        ));
                    }
                }
            }
        }.runTaskLater(plugin, 2L * 60L * 20L); // 2 minutes

        logger.info("[MercenaryManager] Hire proposal: " + lord.getName()
                + " → " + mercenary.getName() + " for "
                + vaultManager.format(paymentAmount) + " (" + durationHours + "h)");
        return true;
    }

    /**
     * Accepts a pending mercenary contract. Transfers Vault payment and
     * grants temporary citizen privileges.
     *
     * @param mercenary the mercenary accepting the contract
     * @return {@code true} if the contract was accepted successfully
     */
    public boolean acceptContract(Player mercenary) {
        UUID mercUuid = mercenary.getUniqueId();
        MercenaryContract contract = pendingProposals.remove(mercUuid);

        if (contract == null) {
            mercenary.sendMessage(MINI.deserialize(
                    "<red>✖ You have no pending contract to accept.</red>"
            ));
            return false;
        }

        // Retrieve the hiring province
        Optional<Province> provinceOpt = provinceManager.getProvinceById(contract.getProvinceId());
        if (provinceOpt.isEmpty()) {
            mercenary.sendMessage(MINI.deserialize(
                    "<red>✖ The hiring province no longer exists.</red>"
            ));
            return false;
        }
        Province province = provinceOpt.get();

        // Transfer payment: withdraw from Lord, deposit to Mercenary
        Player lord = Bukkit.getPlayer(province.getOwnerUuid());
        if (lord == null || !lord.isOnline()) {
            mercenary.sendMessage(MINI.deserialize(
                    "<red>✖ The hiring Lord is offline. Contract cannot be finalized.</red>"
            ));
            return false;
        }

        if (!vaultManager.withdraw(lord, contract.getPaymentAmount())) {
            mercenary.sendMessage(MINI.deserialize(
                    "<red>✖ Payment failed — the Lord may have insufficient funds.</red>"
            ));
            return false;
        }

        if (!vaultManager.deposit(mercenary, contract.getPaymentAmount())) {
            // Refund the Lord on deposit failure
            vaultManager.deposit(lord, contract.getPaymentAmount());
            mercenary.sendMessage(MINI.deserialize(
                    "<red>✖ Payment deposit failed. Contract cancelled.</red>"
            ));
            return false;
        }

        // Activate the contract with a fresh start time
        Instant now = Instant.now();
        Duration duration = Duration.between(contract.getStartTime(), contract.getExpiryTime());
        MercenaryContract activeContract = new MercenaryContract(
                mercUuid, contract.getProvinceId(), contract.getPaymentAmount(),
                now, now.plus(duration));
        activeContracts.put(mercUuid, activeContract);

        // Notify both parties
        mercenary.sendMessage(MINI.deserialize(
                "<green>✔ Contract accepted! You are now a temporary citizen of <white>"
                        + province.getName() + "</white> for <yellow>"
                        + duration.toHours() + " hours</yellow>.</green>"
        ));
        lord.sendMessage(MINI.deserialize(
                "<green>✔ <white>" + mercenary.getName()
                        + "</white> has accepted the mercenary contract!</green>"
        ));

        // Broadcast
        Component alert = MINI.deserialize(
                "<gold>🗡</gold> <gray><white>" + mercenary.getName()
                        + "</white> has been hired as a mercenary for <white>"
                        + province.getName() + "</white>.</gray>"
        );
        Bukkit.broadcast(alert);

        logger.info("[MercenaryManager] Contract accepted: " + mercenary.getName()
                + " → " + province.getName() + " for " + duration.toHours() + "h");
        return true;
    }

    /**
     * Declines a pending mercenary contract.
     *
     * @param mercenary the mercenary declining the contract
     * @return {@code true} if there was a pending contract to decline
     */
    public boolean declineContract(Player mercenary) {
        UUID mercUuid = mercenary.getUniqueId();
        MercenaryContract contract = pendingProposals.remove(mercUuid);

        if (contract == null) {
            mercenary.sendMessage(MINI.deserialize(
                    "<red>✖ You have no pending contract to decline.</red>"
            ));
            return false;
        }

        mercenary.sendMessage(MINI.deserialize(
                "<gray>📜 Contract declined.</gray>"
        ));

        // Notify the hiring Lord
        Optional<Province> provinceOpt = provinceManager.getProvinceById(contract.getProvinceId());
        provinceOpt.ifPresent(province -> {
            Player lord = Bukkit.getPlayer(province.getOwnerUuid());
            if (lord != null && lord.isOnline()) {
                lord.sendMessage(MINI.deserialize(
                        "<red>✖ <white>" + mercenary.getName()
                                + "</white> has declined the mercenary contract.</red>"
                ));
            }
        });

        logger.info("[MercenaryManager] Contract declined by " + mercenary.getName());
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTRACT QUERIES
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Checks whether a player is currently under an active mercenary contract.
     *
     * @param uuid the player's UUID
     * @return {@code true} if the player has an active contract
     */
    public boolean hasActiveContract(UUID uuid) {
        MercenaryContract contract = activeContracts.get(uuid);
        if (contract == null) return false;
        if (contract.isExpired()) {
            activeContracts.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Returns the active contract for a mercenary, if any.
     *
     * @param uuid the mercenary's UUID
     * @return the contract, or {@code null} if none active
     */
    public MercenaryContract getActiveContract(UUID uuid) {
        MercenaryContract contract = activeContracts.get(uuid);
        if (contract != null && contract.isExpired()) {
            activeContracts.remove(uuid);
            return null;
        }
        return contract;
    }

    /**
     * Checks whether a mercenary is currently employed by a specific province.
     * Used by Province.hasMember() logic to include active mercenaries.
     *
     * @param mercenaryUuid the player's UUID
     * @param provinceId    the province ID to check against
     * @return {@code true} if the mercenary is employed by this province
     */
    public boolean isEmployedBy(UUID mercenaryUuid, long provinceId) {
        MercenaryContract contract = getActiveContract(mercenaryUuid);
        return contract != null && contract.getProvinceId() == provinceId;
    }

    /**
     * Returns the province ID that a mercenary is currently working for,
     * or {@code -1} if no active contract.
     *
     * @param mercenaryUuid the mercenary's UUID
     * @return the employer province ID, or {@code -1}
     */
    public long getEmployerProvinceId(UUID mercenaryUuid) {
        MercenaryContract contract = getActiveContract(mercenaryUuid);
        return contract != null ? contract.getProvinceId() : -1L;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTRACT EXPIRY
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Starts a repeating task that checks for and revokes expired contracts.
     * Runs every 60 seconds to minimize overhead.
     */
    private void startExpiryChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Instant now = Instant.now();
                activeContracts.entrySet().removeIf(entry -> {
                    MercenaryContract contract = entry.getValue();
                    if (now.isAfter(contract.getExpiryTime())) {
                        revokeContract(entry.getKey(), contract);
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, EXPIRY_CHECK_INTERVAL_TICKS, EXPIRY_CHECK_INTERVAL_TICKS);
    }

    /**
     * Revokes a mercenary contract, removing temporary citizen privileges
     * and notifying both parties.
     */
    private void revokeContract(UUID mercenaryUuid, MercenaryContract contract) {
        Player mercenary = Bukkit.getPlayer(mercenaryUuid);
        if (mercenary != null && mercenary.isOnline()) {
            mercenary.sendMessage(MINI.deserialize(
                    "<red>📜 Your mercenary contract has expired! "
                            + "Temporary citizen privileges revoked.</red>"
            ));
        }

        // Notify the hiring province's Lord
        provinceManager.getProvinceById(contract.getProvinceId()).ifPresent(province -> {
            Player lord = Bukkit.getPlayer(province.getOwnerUuid());
            if (lord != null && lord.isOnline()) {
                lord.sendMessage(MINI.deserialize(
                        "<gray>📜 Mercenary contract with <white>"
                                + (mercenary != null ? mercenary.getName() : mercenaryUuid)
                                + "</white> has expired.</gray>"
                ));
            }
        });

        // Broadcast contract expiry
        String mercName = mercenary != null ? mercenary.getName() : mercenaryUuid.toString();
        Component alert = MINI.deserialize(
                "<gray>🗡 Mercenary <white>" + mercName
                        + "</white>'s contract has expired.</gray>"
        );
        Bukkit.broadcast(alert);

        logger.info("[MercenaryManager] Contract expired for mercenary " + mercenaryUuid);
    }

    /**
     * Returns the set of all available (registered) mercenary UUIDs.
     */
    public Set<UUID> getAvailableMercenaries() {
        return availableMercenaries;
    }

    /**
     * Returns all active contracts.
     */
    public Map<UUID, MercenaryContract> getActiveContracts() {
        return activeContracts;
    }
}
