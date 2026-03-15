package com.sovereignty.caravan;

import com.sovereignty.integration.VaultManager;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.Caravan;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;
import com.sovereignty.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of physical Trade Caravan entities.
 *
 * <h3>Caravan Flow</h3>
 * <ol>
 *   <li><b>Spawn:</b> When a Trade Agreement tribute is due, the plugin
 *       withdraws the Vault amount from the sender, spawns a Llama at
 *       the source Core, and stores the value in the entity's PDC.</li>
 *   <li><b>Pathfind:</b> The Llama is guided toward the target Core
 *       via an optimized async routing tick.</li>
 *   <li><b>Delivery:</b> On arrival, the Vault amount is deposited
 *       into the recipient's balance and the entity is removed.</li>
 *   <li><b>Ambush:</b> If killed by a rival player, the PDC value is
 *       deposited into the killer's balance and a server-wide alert
 *       is broadcast.</li>
 * </ol>
 */
public final class CaravanManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final VaultManager vaultManager;
    private final ProvinceManager provinceManager;
    private final RoleManager roleManager;
    private final Logger logger;

    /** PDC key for caravan Vault value. */
    private final NamespacedKey keyCaravanValue;

    /** PDC key for source province ID. */
    private final NamespacedKey keySourceProvince;

    /** PDC key for target province ID. */
    private final NamespacedKey keyTargetProvince;

    /** PDC key identifying an entity as a Sovereignty caravan. */
    private final NamespacedKey keyIsCaravan;

    /** Active caravans tracked by entity UUID. */
    private final Map<UUID, Caravan> activeCaravans = new ConcurrentHashMap<>();

    /**
     * Constructs the CaravanManager.
     *
     * @param plugin          the owning plugin instance
     * @param vaultManager    the Vault economy wrapper
     * @param provinceManager the province manager
     * @param roleManager     the role manager (for Steward yield bonus)
     * @param logger          the plugin logger
     */
    public CaravanManager(Plugin plugin, VaultManager vaultManager,
                          ProvinceManager provinceManager, RoleManager roleManager,
                          Logger logger) {
        this.plugin = plugin;
        this.vaultManager = vaultManager;
        this.provinceManager = provinceManager;
        this.roleManager = roleManager;
        this.logger = logger;

        this.keyCaravanValue = new NamespacedKey(plugin, "caravan_value");
        this.keySourceProvince = new NamespacedKey(plugin, "caravan_source");
        this.keyTargetProvince = new NamespacedKey(plugin, "caravan_target");
        this.keyIsCaravan = new NamespacedKey(plugin, "is_caravan");
    }

    /**
     * Spawns a trade caravan at the source province's Core and stores
     * the Vault currency value in the entity's PDC.
     *
     * @param sourceProvince the sending province
     * @param targetProvince the receiving province
     * @param baseValue      the base Vault currency value to transport
     * @return the spawned {@link Caravan} record, or empty if spawn failed
     */
    public Optional<Caravan> spawnCaravan(Province sourceProvince, Province targetProvince,
                                         double baseValue) {
        if (!vaultManager.isAvailable()) {
            logger.warning("[CaravanManager] Vault not available — cannot spawn caravan.");
            return Optional.empty();
        }

        CoreBlock sourceCore = sourceProvince.getCore();
        World world = Bukkit.getWorld(sourceCore.getWorld());
        if (world == null) {
            logger.warning("[CaravanManager] World '" + sourceCore.getWorld() + "' not found.");
            return Optional.empty();
        }

        // Apply Steward yield bonus
        double yieldMultiplier = roleManager.getCaravanYieldMultiplier(sourceProvince.getId());
        double finalValue = baseValue * yieldMultiplier;

        // Withdraw the tribute amount from the source province owner's Vault balance
        org.bukkit.OfflinePlayer sourceOwner = Bukkit.getOfflinePlayer(sourceProvince.getOwnerUuid());
        if (!vaultManager.withdraw(sourceOwner, finalValue)) {
            logger.warning("[CaravanManager] Failed to withdraw " + vaultManager.format(finalValue)
                    + " from " + sourceOwner.getName() + " — caravan not spawned.");
            return Optional.empty();
        }

        // Spawn the Llama caravan at the source Core
        Location spawnLoc = new Location(world,
                sourceCore.getX() + 0.5, sourceCore.getY() + 1, sourceCore.getZ() + 0.5);
        Llama llama = (Llama) world.spawnEntity(spawnLoc, EntityType.LLAMA);
        llama.setAI(true);
        llama.setAdult();
        llama.customName(MINI.deserialize(
                "<gold>📦 Trade Caravan</gold> <gray>("
                        + vaultManager.format(finalValue) + ")</gray>"
        ));
        llama.setCustomNameVisible(true);

        // Write caravan data to PDC
        PersistentDataContainer pdc = llama.getPersistentDataContainer();
        pdc.set(keyIsCaravan, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyCaravanValue, PersistentDataType.DOUBLE, finalValue);
        pdc.set(keySourceProvince, PersistentDataType.LONG, sourceProvince.getId());
        pdc.set(keyTargetProvince, PersistentDataType.LONG, targetProvince.getId());

        Caravan caravan = new Caravan(
                llama.getUniqueId(),
                sourceProvince.getId(),
                targetProvince.getId(),
                finalValue,
                Instant.now()
        );
        activeCaravans.put(llama.getUniqueId(), caravan);

        logger.info("[CaravanManager] Caravan spawned: " + sourceProvince.getName()
                + " → " + targetProvince.getName() + " (" + vaultManager.format(finalValue) + ")");
        return Optional.of(caravan);
    }

    /**
     * Checks whether an entity is a Sovereignty trade caravan.
     *
     * @param entity the entity to check
     * @return {@code true} if the entity is a caravan
     */
    public boolean isCaravan(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Byte flag = pdc.get(keyIsCaravan, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    /**
     * Reads the Vault value stored in a caravan entity's PDC.
     *
     * @param entity the caravan entity
     * @return the stored Vault value, or {@code 0.0} if not a caravan
     */
    public double getCaravanValue(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Double value = pdc.get(keyCaravanValue, PersistentDataType.DOUBLE);
        return value != null ? value : 0.0;
    }

    /**
     * Reads the source province ID from a caravan entity's PDC.
     *
     * @param entity the caravan entity
     * @return the source province ID, or {@code -1} if not a caravan
     */
    public long getSourceProvinceId(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Long id = pdc.get(keySourceProvince, PersistentDataType.LONG);
        return id != null ? id : -1L;
    }

    /**
     * Reads the target province ID from a caravan entity's PDC.
     *
     * @param entity the caravan entity
     * @return the target province ID, or {@code -1} if not a caravan
     */
    public long getTargetProvinceId(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Long id = pdc.get(keyTargetProvince, PersistentDataType.LONG);
        return id != null ? id : -1L;
    }

    /**
     * Handles caravan arrival at the target Core. Deposits the Vault
     * currency and removes the entity.
     *
     * @param entity the caravan entity that arrived
     */
    public void handleDelivery(Entity entity) {
        double value = getCaravanValue(entity);
        long targetId = getTargetProvinceId(entity);

        Optional<Province> targetOpt = provinceManager.getProvinceById(targetId);
        if (targetOpt.isEmpty()) {
            logger.warning("[CaravanManager] Target province not found for caravan delivery.");
            entity.remove();
            return;
        }

        Province target = targetOpt.get();
        Player recipient = Bukkit.getPlayer(target.getOwnerUuid());
        if (recipient != null && vaultManager.isAvailable()) {
            vaultManager.deposit(recipient, value);
        }

        // Broadcast delivery
        Component msg = MINI.deserialize(
                "<green>📦 A trade caravan has arrived at <white>"
                        + target.getName() + "</white>! Value: <gold>"
                        + vaultManager.format(value) + "</gold></green>"
        );
        Bukkit.broadcast(msg);

        activeCaravans.remove(entity.getUniqueId());
        entity.remove();
        logger.info("[CaravanManager] Caravan delivered to " + target.getName()
                + " — " + vaultManager.format(value));
    }

    /**
     * Handles caravan interception (killed by a rival player).
     * Reads the PDC value, deposits it into the killer's Vault balance,
     * and broadcasts a dramatic server-wide alert.
     *
     * @param entity the killed caravan entity
     * @param killer the player who killed the caravan
     */
    public void handleAmbush(Entity entity, Player killer) {
        double value = getCaravanValue(entity);
        if (value <= 0) return;

        if (vaultManager.isAvailable()) {
            vaultManager.deposit(killer, value);
        }

        // Dramatic server-wide alert
        Component msg = MINI.deserialize(
                "<red>⚔ <bold>CARAVAN AMBUSHED!</bold></red> <gray>"
                        + killer.getName() + " has intercepted a trade caravan and stolen <gold>"
                        + vaultManager.format(value) + "</gold>!</gray>"
        );
        Bukkit.broadcast(msg);

        activeCaravans.remove(entity.getUniqueId());
        logger.info("[CaravanManager] Caravan ambushed by " + killer.getName()
                + " — stolen " + vaultManager.format(value));
    }

    /**
     * Returns the map of currently active caravans.
     */
    public Map<UUID, Caravan> getActiveCaravans() {
        return activeCaravans;
    }

    /** Returns the PDC key used to identify caravan entities. */
    public NamespacedKey getKeyIsCaravan() { return keyIsCaravan; }

    /** Returns the PDC key used for storing caravan Vault value. */
    public NamespacedKey getKeyCaravanValue() { return keyCaravanValue; }
}
