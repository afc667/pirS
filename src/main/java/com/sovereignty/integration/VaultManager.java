package com.sovereignty.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Interface-driven wrapper for the <a href="https://github.com/MilkBowl/VaultAPI">Vault</a>
 * economy API. Prevents hard crashes if Vault is not installed.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   VaultManager vault = new VaultManager(logger);
 *   if (vault.isAvailable()) {
 *       vault.withdraw(player, 100.0);
 *       vault.deposit(player, 50.0);
 *   }
 * }</pre>
 *
 * <h3>Caravan Integration</h3>
 * <p>When a Trade Agreement tribute is due, the plugin calls
 * {@link #withdraw(OfflinePlayer, double)} to extract the scheduled
 * amount, stores the value in the caravan entity's PDC, and on
 * successful delivery calls {@link #deposit(OfflinePlayer, double)}
 * to credit the recipient.
 */
public final class VaultManager {

    private final Logger logger;
    private Economy economy;
    private boolean available;

    /**
     * Attempts to hook into the Vault economy service.
     *
     * @param logger the plugin logger
     */
    public VaultManager(Logger logger) {
        this.logger = logger;
        this.available = false;
        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.warning("[VaultManager] Vault plugin not found — economy features disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("[VaultManager] No Economy provider registered with Vault.");
            return;
        }
        economy = rsp.getProvider();
        available = true;
        logger.info("[VaultManager] Vault economy hooked successfully: " + economy.getName());
    }

    /**
     * Whether the Vault economy hook is active and usable.
     *
     * @return {@code true} if Vault and an economy provider are present
     */
    public boolean isAvailable() {
        return available && economy != null;
    }

    /**
     * Returns the player's current Vault balance.
     *
     * @param player the player to query
     * @return the balance, or {@code 0.0} if Vault is unavailable
     */
    public double getBalance(OfflinePlayer player) {
        if (!isAvailable()) return 0.0;
        return economy.getBalance(player);
    }

    /**
     * Withdraws currency from a player's Vault balance.
     *
     * @param player the player to debit
     * @param amount the amount to withdraw
     * @return {@code true} if the transaction succeeded
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isAvailable()) return false;
        EconomyResponse resp = economy.withdrawPlayer(player, amount);
        if (!resp.transactionSuccess()) {
            logger.warning("[VaultManager] Withdraw failed for " + player.getName()
                    + ": " + resp.errorMessage);
            return false;
        }
        return true;
    }

    /**
     * Deposits currency into a player's Vault balance.
     *
     * @param player the player to credit
     * @param amount the amount to deposit
     * @return {@code true} if the transaction succeeded
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isAvailable()) return false;
        EconomyResponse resp = economy.depositPlayer(player, amount);
        if (!resp.transactionSuccess()) {
            logger.warning("[VaultManager] Deposit failed for " + player.getName()
                    + ": " + resp.errorMessage);
            return false;
        }
        return true;
    }

    /**
     * Formats a currency amount using the Vault economy's formatting.
     *
     * @param amount the amount to format
     * @return the formatted string, or a plain string if Vault is unavailable
     */
    public String format(double amount) {
        if (!isAvailable()) return String.format("%.2f", amount);
        return economy.format(amount);
    }
}
