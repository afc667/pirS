package com.sovereignty.core;

import com.sovereignty.cache.ChunkCache;
import com.sovereignty.caravan.CaravanManager;
import com.sovereignty.database.DatabaseManager;
import com.sovereignty.database.queries.ProvinceQueries;
import com.sovereignty.integration.DynmapHook;
import com.sovereignty.integration.ItemsAdderListener;
import com.sovereignty.integration.ProgressionsHook;
import com.sovereignty.integration.VaultManager;
import com.sovereignty.listeners.BlockProtectionListener;
import com.sovereignty.listeners.CaravanListener;
import com.sovereignty.listeners.ChunkBoundaryListener;
import com.sovereignty.listeners.CorePlacementListener;
import com.sovereignty.listeners.EspionageListener;
import com.sovereignty.listeners.SiegeMechanicsListener;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.roles.RoleManager;
import com.sovereignty.stability.StabilityEngine;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * <b>Sovereignty</b> — A Hardcore RPG Claim Engine.
 *
 * <p>Merges the best aspects of Factions, Towny, and Paradox Interactive
 * grand strategy games into a single, high-performance PaperMC plugin.
 *
 * <h3>Phase 2: Micro-SMP Integrations</h3>
 * <ul>
 *   <li>{@link VaultManager} — Economy integration for physical caravans</li>
 *   <li>{@link DynmapHook} — Live strategy-board borders and siege alerts</li>
 *   <li>{@link ItemsAdderListener} — Custom cores, siege cannons, armory</li>
 *   <li>{@link ProgressionsHook} — Era-based tech tree feature gating</li>
 *   <li>{@link RoleManager} — Council roles (Marshal, Chancellor, Steward)</li>
 *   <li>{@link CaravanManager} — Physical trade caravan lifecycle</li>
 * </ul>
 *
 * <h3>Startup Sequence</h3>
 * <ol>
 *   <li>Initialize {@link DatabaseManager} (HikariCP pool).</li>
 *   <li>Execute {@code schema.sql} to ensure tables exist.</li>
 *   <li>Warm {@link ProvinceManager} caches from DB.</li>
 *   <li>Hook third-party APIs (Vault, Dynmap, ItemsAdder, Progressions).</li>
 *   <li>Initialize Phase 2 managers (RoleManager, CaravanManager).</li>
 *   <li>Register event listeners.</li>
 * </ol>
 */
public final class SovereigntyPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ProvinceManager provinceManager;
    private StabilityEngine stabilityEngine;
    private RoleManager roleManager;
    private VaultManager vaultManager;
    private DynmapHook dynmapHook;
    private ItemsAdderListener itemsAdderListener;
    private ProgressionsHook progressionsHook;
    private CaravanManager caravanManager;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        // ── 1. Configuration ─────────────────────────────────────────────
        saveDefaultConfig();
        String dbHost = getConfig().getString("database.host", "localhost");
        int dbPort = getConfig().getInt("database.port", 3306);
        String dbName = getConfig().getString("database.name", "sovereignty");
        String dbUser = getConfig().getString("database.username", "root");
        String dbPass = getConfig().getString("database.password", "");

        // ── 2. Database ──────────────────────────────────────────────────
        databaseManager = new DatabaseManager(dbHost, dbPort, dbName, dbUser, dbPass, getLogger());

        // ── 3. Initialize PDC keys for CoreBlock ─────────────────────────
        CoreBlock.initKeys(this);

        // ── 4. Province Manager + Cache ──────────────────────────────────
        ChunkCache chunkCache = new ChunkCache();
        ProvinceQueries queries = new ProvinceQueries(databaseManager, getLogger());
        provinceManager = new ProvinceManager(queries, chunkCache, getLogger());

        // ── 5. Schema init → warm caches (async) ────────────────────────
        databaseManager.initializeSchema()
                .thenCompose(v -> provinceManager.warmCaches())
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        getLogger().log(Level.SEVERE, "Failed to initialize Sovereignty", ex);
                    } else {
                        getLogger().info("Caches warmed in "
                                + (System.currentTimeMillis() - start) + " ms.");
                    }
                });

        // ── 6. Phase 2: Stability Engine (with cultural-pressure toggle) ─
        boolean culturalPressure = getConfig().getBoolean("stability.cultural-pressure", false);
        stabilityEngine = new StabilityEngine(provinceManager, culturalPressure, getLogger());

        // ── 7. Phase 2: Third-Party API Hooks ────────────────────────────
        vaultManager = new VaultManager(getLogger());
        dynmapHook = new DynmapHook(this, provinceManager, getLogger());
        progressionsHook = new ProgressionsHook(getLogger());

        // ItemsAdder integration with configurable namespaces
        String coreNs = getConfig().getString("itemsadder.namespaces.core-block",
                "sovereignty:government_stone");
        String siegeNs = getConfig().getString("itemsadder.namespaces.siege-cannon",
                "sovereignty:siege_cannon");
        String passportNs = getConfig().getString("itemsadder.namespaces.forged-passport",
                "sovereignty:forged_passport");
        itemsAdderListener = new ItemsAdderListener(this, provinceManager,
                coreNs, siegeNs, passportNs, getLogger());

        // ── 8. Phase 2: Role Manager + Caravan Manager ───────────────────
        roleManager = new RoleManager(getLogger());
        caravanManager = new CaravanManager(this, vaultManager, provinceManager,
                roleManager, getLogger());

        // ── 9. Event Listeners ───────────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ChunkBoundaryListener(provinceManager), this);
        getServer().getPluginManager().registerEvents(
                new BlockProtectionListener(provinceManager, null /* WarfareEngine impl TBD */), this);
        getServer().getPluginManager().registerEvents(itemsAdderListener, this);
        getServer().getPluginManager().registerEvents(
                new CaravanListener(caravanManager, getLogger()), this);
        getServer().getPluginManager().registerEvents(
                new CorePlacementListener(provinceManager, dynmapHook, getLogger()), this);
        getServer().getPluginManager().registerEvents(
                new SiegeMechanicsListener(provinceManager, itemsAdderListener, getLogger()), this);
        getServer().getPluginManager().registerEvents(
                new EspionageListener(this, provinceManager, itemsAdderListener,
                        dynmapHook, stabilityEngine, getLogger()), this);

        getLogger().info("Sovereignty v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // Persist all dirty state synchronously on shutdown
        if (provinceManager != null) {
            provinceManager.saveAll().join();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("Sovereignty disabled — all data saved.");
    }

    // ── Accessors for sub-systems ────────────────────────────────────────

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ProvinceManager getProvinceManager() { return provinceManager; }
    public StabilityEngine getStabilityEngine() { return stabilityEngine; }
    public RoleManager getRoleManager() { return roleManager; }
    public VaultManager getVaultManager() { return vaultManager; }
    public DynmapHook getDynmapHook() { return dynmapHook; }
    public ItemsAdderListener getItemsAdderListener() { return itemsAdderListener; }
    public ProgressionsHook getProgressionsHook() { return progressionsHook; }
    public CaravanManager getCaravanManager() { return caravanManager; }
}
