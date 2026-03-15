package com.sovereignty.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the HikariCP connection pool and provides asynchronous
 * database access for both MySQL and SQLite backends.
 *
 * <p><b>Thread-Safety Contract:</b> Every public method that touches the
 * database returns a {@link CompletableFuture} executed on a dedicated
 * thread pool — never on the main server thread — to prevent TPS drops.
 */
public final class DatabaseManager {

    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final Logger logger;
    private final boolean sqlite;

    /**
     * Creates and configures the HikariCP pool for MySQL.
     *
     * @param host     database host
     * @param port     database port
     * @param database database / schema name
     * @param username JDBC username
     * @param password JDBC password
     * @param logger   plugin logger
     */
    public DatabaseManager(String host, int port, String database,
                           String username, String password, Logger logger) {
        this.logger = logger;
        this.sqlite = false;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        // Allow the pool to start even when the database is temporarily unreachable.
        // Connections will be established lazily when getConnection() is called,
        // and callers already handle SQLException gracefully.
        config.setInitializationFailTimeout(-1);

        this.dataSource = new HikariDataSource(config);
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Sovereignty-DB");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates and configures the HikariCP pool for SQLite.
     *
     * @param sqlitePath absolute path to the SQLite database file
     * @param logger     plugin logger
     */
    public DatabaseManager(String sqlitePath, Logger logger) {
        this.logger = logger;
        this.sqlite = true;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
        config.setDriverClassName("org.sqlite.JDBC");
        // SQLite supports only one writer at a time; keep the pool small
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        // Disable eviction — SQLite connections are local and never go stale
        config.setIdleTimeout(0);
        config.setMaxLifetime(0);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("foreign_keys", "true");
        // Fail fast for SQLite — the DB is local, so a connection failure is a
        // real configuration error and should surface immediately.
        config.setInitializationFailTimeout(5_000);

        this.dataSource = new HikariDataSource(config);
        // Dedicated pool — keeps DB I/O off the Netty / main-thread pools
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Sovereignty-DB");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Returns {@code true} if this manager is backed by SQLite.
     */
    public boolean isSQLite() {
        return sqlite;
    }

    /**
     * Obtains a pooled JDBC connection.
     *
     * @return a live {@link Connection}
     * @throws SQLException if the pool is exhausted or shut down
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Executes the bundled schema SQL to create / migrate tables.
     * Uses {@code schema-sqlite.sql} for SQLite or {@code schema.sql} for MySQL.
     *
     * @return a future that completes when schema initialization is done
     */
    public CompletableFuture<Void> initializeSchema() {
        return CompletableFuture.runAsync(() -> {
            String schemaFile = sqlite ? "schema-sqlite.sql" : "schema.sql";
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(schemaFile)) {
                if (is == null) {
                    logger.severe(schemaFile + " not found in plugin resources!");
                    return;
                }
                String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));

                try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                    // Execute each statement separated by semicolons
                    for (String statement : sql.split(";")) {
                        // Strip comment-only lines so that blocks like
                        //   "-- section header\nCREATE TABLE ..."
                        // are not incorrectly skipped.
                        String cleaned = statement.lines()
                                .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("--"))
                                .collect(Collectors.joining("\n"))
                                .trim();
                        if (!cleaned.isEmpty()) {
                            stmt.execute(cleaned);
                        }
                    }
                }
                logger.info("Database schema initialized successfully (" + (sqlite ? "SQLite" : "MySQL") + ").");
            } catch (SQLException | IOException e) {
                logger.log(Level.SEVERE, "Failed to initialize database schema", e);
                throw new RuntimeException("Schema initialization failed", e);
            }
        }, executor);
    }

    /**
     * Returns the dedicated async executor for DB operations.
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Gracefully shuts down the pool and executor.
     */
    public void shutdown() {
        executor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
