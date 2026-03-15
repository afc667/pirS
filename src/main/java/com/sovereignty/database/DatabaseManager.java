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
 * database access.
 *
 * <p><b>Thread-Safety Contract:</b> Every public method that touches the
 * database returns a {@link CompletableFuture} executed on a dedicated
 * thread pool — never on the main server thread — to prevent TPS drops.
 */
public final class DatabaseManager {

    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final Logger logger;

    /**
     * Creates and configures the HikariCP pool.
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

        this.dataSource = new HikariDataSource(config);
        // Dedicated pool — keeps DB I/O off the Netty / main-thread pools
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Sovereignty-DB");
            t.setDaemon(true);
            return t;
        });
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
     * Executes the bundled {@code schema.sql} to create / migrate tables.
     *
     * @return a future that completes when schema initialization is done
     */
    public CompletableFuture<Void> initializeSchema() {
        return CompletableFuture.runAsync(() -> {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
                if (is == null) {
                    logger.severe("schema.sql not found in plugin resources!");
                    return;
                }
                String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));

                try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                    // Execute each statement separated by semicolons
                    for (String statement : sql.split(";")) {
                        String trimmed = statement.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                            stmt.execute(trimmed);
                        }
                    }
                }
                logger.info("Database schema initialized successfully.");
            } catch (SQLException | IOException e) {
                logger.log(Level.SEVERE, "Failed to initialize database schema", e);
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
