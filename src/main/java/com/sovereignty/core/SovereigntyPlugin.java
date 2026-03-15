package com.sovereignty.core;

import com.sovereignty.cache.ChunkCache;
import com.sovereignty.database.DatabaseManager;
import com.sovereignty.database.queries.ProvinceQueries;
import com.sovereignty.listeners.BlockProtectionListener;
import com.sovereignty.listeners.ChunkBoundaryListener;
import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.CoreBlock;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * <b>Sovereignty</b> — A Hardcore RPG Claim Engine.
 *
 * <p>Merges the best aspects of Factions, Towny, and Paradox Interactive
 * grand strategy games into a single, high-performance PaperMC plugin.
 *
 * <h3>Startup Sequence</h3>
 * <ol>
 *   <li>Initialize {@link DatabaseManager} (HikariCP pool).</li>
 *   <li>Execute {@code schema.sql} to ensure tables exist.</li>
 *   <li>Warm {@link ProvinceManager} caches from DB.</li>
 *   <li>Register event listeners.</li>
 * </ol>
 */
public final class SovereigntyPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ProvinceManager provinceManager;

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

        // ── 6. Event Listeners ───────────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ChunkBoundaryListener(provinceManager), this);
        getServer().getPluginManager().registerEvents(
                new BlockProtectionListener(provinceManager, null /* WarfareEngine impl TBD */), this);

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
}
