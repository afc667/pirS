package com.sovereignty.database.queries;

import com.sovereignty.database.DatabaseManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.CoreBlock;
import com.sovereignty.models.Province;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous data-access object for Province CRUD operations.
 *
 * <p><b>Every method returns a {@link CompletableFuture}</b> — no blocking
 * call ever touches the main server thread.
 */
public final class ProvinceQueries {

    private final DatabaseManager db;
    private final Logger logger;

    public ProvinceQueries(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Inserts a new province and returns its auto-generated ID.
     */
    public CompletableFuture<Long> insertProvince(Province province) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO sov_provinces
                    (name, owner_uuid, suzerain_id, core_world, core_x, core_y, core_z,
                     core_hp, core_level, stability, tax_rate, liberty_desire,
                     development, war_exhaustion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                CoreBlock c = province.getCore();
                ps.setString(1, province.getName());
                ps.setString(2, province.getOwnerUuid().toString());
                ps.setObject(3, province.getSuzerainId()); // nullable
                ps.setString(4, c.getWorld());
                ps.setInt(5, c.getX());
                ps.setInt(6, c.getY());
                ps.setInt(7, c.getZ());
                ps.setInt(8, c.getHp());
                ps.setInt(9, c.getLevel());
                ps.setDouble(10, province.getStability());
                ps.setDouble(11, province.getTaxRate());
                ps.setDouble(12, province.getLibertyDesire());
                ps.setInt(13, province.getDevelopment());
                ps.setDouble(14, province.getWarExhaustion());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to insert province: " + province.getName(), e);
            }
            return -1L;
        }, db.getExecutor());
    }

    /**
     * Loads a province by its database ID.
     */
    public CompletableFuture<Optional<Province>> findById(long id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM sov_provinces WHERE id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load province id=" + id, e);
            }
            return Optional.empty();
        }, db.getExecutor());
    }

    /**
     * Loads all provinces from the database (used on startup to warm caches).
     */
    public CompletableFuture<List<Province>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<Province> list = new ArrayList<>();
            String sql = "SELECT * FROM sov_provinces";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load all provinces", e);
            }
            return list;
        }, db.getExecutor());
    }

    /**
     * Persists updated province fields back to the database.
     */
    public CompletableFuture<Void> updateProvince(Province p) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                UPDATE sov_provinces SET
                    name = ?, owner_uuid = ?, suzerain_id = ?,
                    core_hp = ?, core_level = ?, stability = ?,
                    tax_rate = ?, liberty_desire = ?,
                    development = ?, war_exhaustion = ?
                WHERE id = ?
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.getName());
                ps.setString(2, p.getOwnerUuid().toString());
                ps.setObject(3, p.getSuzerainId());
                ps.setInt(4, p.getCore().getHp());
                ps.setInt(5, p.getCore().getLevel());
                ps.setDouble(6, p.getStability());
                ps.setDouble(7, p.getTaxRate());
                ps.setDouble(8, p.getLibertyDesire());
                ps.setInt(9, p.getDevelopment());
                ps.setDouble(10, p.getWarExhaustion());
                ps.setLong(11, p.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to update province id=" + p.getId(), e);
            }
        }, db.getExecutor());
    }

    /**
     * Claims a chunk for a province.
     */
    public CompletableFuture<Void> insertChunk(long provinceId, ChunkPosition pos) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO sov_chunks (province_id, world, chunk_x, chunk_z) VALUES (?, ?, ?, ?)";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, provinceId);
                ps.setString(2, pos.getWorld());
                ps.setInt(3, pos.getChunkX());
                ps.setInt(4, pos.getChunkZ());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to insert chunk " + pos, e);
            }
        }, db.getExecutor());
    }

    /**
     * Loads all claimed chunks for a province.
     */
    public CompletableFuture<List<ChunkPosition>> findChunksByProvince(long provinceId) {
        return CompletableFuture.supplyAsync(() -> {
            List<ChunkPosition> chunks = new ArrayList<>();
            String sql = "SELECT world, chunk_x, chunk_z FROM sov_chunks WHERE province_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, provinceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        chunks.add(new ChunkPosition(
                                rs.getString("world"),
                                rs.getInt("chunk_x"),
                                rs.getInt("chunk_z")));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load chunks for province id=" + provinceId, e);
            }
            return chunks;
        }, db.getExecutor());
    }

    /**
     * Loads all chunk-to-province mappings (used on startup to warm the spatial cache).
     */
    public CompletableFuture<List<long[]>> findAllChunkMappings() {
        return CompletableFuture.supplyAsync(() -> {
            List<long[]> mappings = new ArrayList<>();
            String sql = "SELECT province_id, world, chunk_x, chunk_z FROM sov_chunks";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // We encode world hash + coords separately; the caller
                    // rebuilds ChunkPosition → provinceId from these values.
                    mappings.add(new long[]{
                            rs.getLong("province_id"),
                            rs.getString("world").hashCode(),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z")
                    });
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load all chunk mappings", e);
            }
            return mappings;
        }, db.getExecutor());
    }

    // ── Row Mapper ───────────────────────────────────────────────────────

    private Province mapRow(ResultSet rs) throws SQLException {
        long suzerainRaw = rs.getLong("suzerain_id");
        Long suzerainId = rs.wasNull() ? null : suzerainRaw;
        CoreBlock core = new CoreBlock(
                rs.getString("core_world"),
                rs.getInt("core_x"),
                rs.getInt("core_y"),
                rs.getInt("core_z"),
                rs.getInt("core_hp"),
                rs.getInt("core_level"),
                rs.getLong("id")
        );
        Timestamp ts = rs.getTimestamp("created_at");
        return new Province(
                rs.getLong("id"),
                rs.getString("name"),
                UUID.fromString(rs.getString("owner_uuid")),
                suzerainId,
                core,
                rs.getDouble("stability"),
                rs.getDouble("tax_rate"),
                rs.getDouble("liberty_desire"),
                rs.getInt("development"),
                rs.getDouble("war_exhaustion"),
                ts != null ? ts.toInstant() : Instant.now()
        );
    }
}
