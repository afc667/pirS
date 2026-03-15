-- ============================================================================
-- Sovereignty — SQLite Schema Definitions
-- SQLite-compatible schema for local / single-server testing.
-- ============================================================================

-- Enable foreign key enforcement (SQLite has it off by default)
PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------------
-- 1. Players — Every unique player that has joined the server.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_players (
    uuid         TEXT         PRIMARY KEY,
    name         TEXT         NOT NULL,
    rank         TEXT         NOT NULL DEFAULT 'CITIZEN',
    council_role TEXT         NOT NULL DEFAULT 'NONE',
    influence    REAL         NOT NULL DEFAULT 0.0,
    wealth       REAL         NOT NULL DEFAULT 0.0,
    province_id  INTEGER      DEFAULT NULL,
    joined_at    TEXT         NOT NULL DEFAULT (datetime('now')),
    last_seen    TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_players_province ON sov_players (province_id);
CREATE INDEX IF NOT EXISTS idx_players_rank     ON sov_players (rank);

-- ---------------------------------------------------------------------------
-- 2. Provinces — The core political unit anchored to a Province Core block.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_provinces (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    name            TEXT         NOT NULL UNIQUE,
    owner_uuid      TEXT         NOT NULL,
    suzerain_id     INTEGER      DEFAULT NULL,
    core_world      TEXT         NOT NULL,
    core_x          INTEGER      NOT NULL,
    core_y          INTEGER      NOT NULL,
    core_z          INTEGER      NOT NULL,
    core_hp         INTEGER      NOT NULL DEFAULT 100,
    core_level      INTEGER      NOT NULL DEFAULT 1,
    stability       REAL         NOT NULL DEFAULT 50.0,
    tax_rate        REAL         NOT NULL DEFAULT 0.10,
    liberty_desire  REAL         NOT NULL DEFAULT 0.0,
    development     INTEGER      NOT NULL DEFAULT 1,
    war_exhaustion  REAL         NOT NULL DEFAULT 0.0,
    era             TEXT         NOT NULL DEFAULT 'TRIBAL',
    created_at      TEXT         NOT NULL DEFAULT (datetime('now')),

    CONSTRAINT fk_province_owner    FOREIGN KEY (owner_uuid)  REFERENCES sov_players (uuid),
    CONSTRAINT fk_province_suzerain FOREIGN KEY (suzerain_id) REFERENCES sov_provinces (id)
                                    ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_provinces_owner    ON sov_provinces (owner_uuid);
CREATE INDEX IF NOT EXISTS idx_provinces_suzerain ON sov_provinces (suzerain_id);

-- ---------------------------------------------------------------------------
-- 3. Claimed Chunks — Spatial index keyed by (world, chunkX, chunkZ).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_chunks (
    id          INTEGER      PRIMARY KEY AUTOINCREMENT,
    province_id INTEGER      NOT NULL,
    world       TEXT         NOT NULL,
    chunk_x     INTEGER      NOT NULL,
    chunk_z     INTEGER      NOT NULL,
    claimed_at  TEXT         NOT NULL DEFAULT (datetime('now')),

    CONSTRAINT fk_chunk_province FOREIGN KEY (province_id) REFERENCES sov_provinces (id)
                                 ON DELETE CASCADE,
    CONSTRAINT uq_chunk_coords  UNIQUE (world, chunk_x, chunk_z)
);

CREATE INDEX IF NOT EXISTS idx_chunks_spatial ON sov_chunks (world, chunk_x, chunk_z);

-- ---------------------------------------------------------------------------
-- 4. Treaties — Diplomatic relations between two provinces.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_treaties (
    id            INTEGER     PRIMARY KEY AUTOINCREMENT,
    province_a    INTEGER     NOT NULL,
    province_b    INTEGER     NOT NULL,
    relation      TEXT        NOT NULL DEFAULT 'NEUTRAL',
    signed_at     TEXT        NOT NULL DEFAULT (datetime('now')),
    expires_at    TEXT        DEFAULT NULL,

    CONSTRAINT fk_treaty_a FOREIGN KEY (province_a) REFERENCES sov_provinces (id)
                           ON DELETE CASCADE,
    CONSTRAINT fk_treaty_b FOREIGN KEY (province_b) REFERENCES sov_provinces (id)
                           ON DELETE CASCADE,
    CONSTRAINT uq_treaty   UNIQUE (province_a, province_b)
);

CREATE INDEX IF NOT EXISTS idx_treaties_a ON sov_treaties (province_a);
CREATE INDEX IF NOT EXISTS idx_treaties_b ON sov_treaties (province_b);

-- ---------------------------------------------------------------------------
-- 5. Wars — Active and historical conflicts tracked via a state machine.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_wars (
    id              INTEGER     PRIMARY KEY AUTOINCREMENT,
    attacker_id     INTEGER     NOT NULL,
    defender_id     INTEGER     NOT NULL,
    casus_belli     TEXT        NOT NULL,
    phase           TEXT        NOT NULL DEFAULT 'PREPARATION',
    siege_window_start TEXT     DEFAULT '20:00',
    siege_window_end   TEXT     DEFAULT '22:00',
    attacker_score  INTEGER     NOT NULL DEFAULT 0,
    defender_score  INTEGER     NOT NULL DEFAULT 0,
    declared_at     TEXT        NOT NULL DEFAULT (datetime('now')),
    active_since    TEXT        DEFAULT NULL,
    resolved_at     TEXT        DEFAULT NULL,

    CONSTRAINT fk_war_attacker FOREIGN KEY (attacker_id) REFERENCES sov_provinces (id)
                               ON DELETE CASCADE,
    CONSTRAINT fk_war_defender FOREIGN KEY (defender_id) REFERENCES sov_provinces (id)
                               ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_wars_attacker ON sov_wars (attacker_id);
CREATE INDEX IF NOT EXISTS idx_wars_defender ON sov_wars (defender_id);
CREATE INDEX IF NOT EXISTS idx_wars_phase    ON sov_wars (phase);

-- ---------------------------------------------------------------------------
-- 6. Council Roles — Specialized roles for small-team provinces.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_council_roles (
    id          INTEGER     PRIMARY KEY AUTOINCREMENT,
    province_id INTEGER     NOT NULL,
    player_uuid TEXT        NOT NULL,
    role        TEXT        NOT NULL DEFAULT 'NONE',
    assigned_at TEXT        NOT NULL DEFAULT (datetime('now')),

    CONSTRAINT fk_role_province FOREIGN KEY (province_id) REFERENCES sov_provinces (id)
                                ON DELETE CASCADE,
    CONSTRAINT fk_role_player   FOREIGN KEY (player_uuid) REFERENCES sov_players (uuid)
                                ON DELETE CASCADE,
    CONSTRAINT uq_role_player   UNIQUE (player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_council_province ON sov_council_roles (province_id);

-- ---------------------------------------------------------------------------
-- 7. Caravans — Physical trade caravan audit log.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_caravans (
    id              INTEGER     PRIMARY KEY AUTOINCREMENT,
    entity_uuid     TEXT        NOT NULL,
    source_province INTEGER     NOT NULL,
    target_province INTEGER     NOT NULL,
    vault_value     REAL        NOT NULL DEFAULT 0.0,
    status          TEXT        NOT NULL DEFAULT 'IN_TRANSIT',
    spawned_at      TEXT        NOT NULL DEFAULT (datetime('now')),
    completed_at    TEXT        DEFAULT NULL,
    killer_uuid     TEXT        DEFAULT NULL,

    CONSTRAINT fk_caravan_source FOREIGN KEY (source_province) REFERENCES sov_provinces (id)
                                 ON DELETE CASCADE,
    CONSTRAINT fk_caravan_target FOREIGN KEY (target_province) REFERENCES sov_provinces (id)
                                 ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_caravans_status ON sov_caravans (status);
CREATE INDEX IF NOT EXISTS idx_caravans_source ON sov_caravans (source_province);
CREATE INDEX IF NOT EXISTS idx_caravans_target ON sov_caravans (target_province);
