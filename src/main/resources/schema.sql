-- ============================================================================
-- Sovereignty — SQL Schema Definitions
-- Relational schema for PostgreSQL / MySQL with proper indexing for
-- spatial chunk lookups and the feudal vassal tree.
-- Phase 2: Council Roles, Caravans, Era Progression
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Players — Every unique player that has joined the server.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_players (
    uuid         CHAR(36)     PRIMARY KEY,
    name         VARCHAR(16)  NOT NULL,
    rank         VARCHAR(16)  NOT NULL DEFAULT 'CITIZEN',   -- CITIZEN, LORD, SUZERAIN, EMPEROR
    council_role VARCHAR(16)  NOT NULL DEFAULT 'NONE',      -- NONE, MARSHAL, CHANCELLOR, STEWARD
    influence    DOUBLE       NOT NULL DEFAULT 0.0,
    wealth       DOUBLE       NOT NULL DEFAULT 0.0,
    province_id  BIGINT       DEFAULT NULL,                 -- FK → sov_provinces (nullable for landless)
    joined_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_players_province ON sov_players (province_id);
CREATE INDEX idx_players_rank     ON sov_players (rank);

-- ---------------------------------------------------------------------------
-- 2. Provinces — The core political unit anchored to a Province Core block.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_provinces (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL UNIQUE,
    owner_uuid      CHAR(36)     NOT NULL,                  -- FK → sov_players
    suzerain_id     BIGINT       DEFAULT NULL,              -- Self-ref FK for vassal tree
    core_world      VARCHAR(128) NOT NULL,
    core_x          INT          NOT NULL,
    core_y          INT          NOT NULL,
    core_z          INT          NOT NULL,
    core_hp         INT          NOT NULL DEFAULT 100,
    core_level      INT          NOT NULL DEFAULT 1,
    stability       DOUBLE       NOT NULL DEFAULT 50.0,     -- 0–100
    tax_rate        DOUBLE       NOT NULL DEFAULT 0.10,     -- 0.0–1.0
    liberty_desire  DOUBLE       NOT NULL DEFAULT 0.0,      -- 0–100 (vassals only)
    development     INT          NOT NULL DEFAULT 1,
    war_exhaustion  DOUBLE       NOT NULL DEFAULT 0.0,
    era             VARCHAR(16)  NOT NULL DEFAULT 'TRIBAL', -- TRIBAL, FEUDAL, GUNPOWDER
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_province_owner    FOREIGN KEY (owner_uuid)  REFERENCES sov_players (uuid),
    CONSTRAINT fk_province_suzerain FOREIGN KEY (suzerain_id) REFERENCES sov_provinces (id)
                                    ON DELETE SET NULL
);

CREATE INDEX idx_provinces_owner    ON sov_provinces (owner_uuid);
CREATE INDEX idx_provinces_suzerain ON sov_provinces (suzerain_id);

-- ---------------------------------------------------------------------------
-- 3. Claimed Chunks — Spatial index keyed by (world, chunkX, chunkZ).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_chunks (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    province_id BIGINT       NOT NULL,                      -- FK → sov_provinces
    world       VARCHAR(128) NOT NULL,
    chunk_x     INT          NOT NULL,
    chunk_z     INT          NOT NULL,
    claimed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chunk_province FOREIGN KEY (province_id) REFERENCES sov_provinces (id)
                                 ON DELETE CASCADE,
    CONSTRAINT uq_chunk_coords  UNIQUE (world, chunk_x, chunk_z)
);

-- O(1) spatial lookup: world + chunkX + chunkZ → province
CREATE INDEX idx_chunks_spatial ON sov_chunks (world, chunk_x, chunk_z);

-- ---------------------------------------------------------------------------
-- 4. Treaties — Diplomatic relations between two provinces.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_treaties (
    id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
    province_a    BIGINT      NOT NULL,                     -- FK → sov_provinces
    province_b    BIGINT      NOT NULL,                     -- FK → sov_provinces
    relation      VARCHAR(32) NOT NULL DEFAULT 'NEUTRAL',   -- NEUTRAL, NON_AGGRESSION, ALLY, RIVAL, OVERLORD, VASSAL
    signed_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMP   DEFAULT NULL,

    CONSTRAINT fk_treaty_a FOREIGN KEY (province_a) REFERENCES sov_provinces (id)
                           ON DELETE CASCADE,
    CONSTRAINT fk_treaty_b FOREIGN KEY (province_b) REFERENCES sov_provinces (id)
                           ON DELETE CASCADE,
    CONSTRAINT uq_treaty   UNIQUE (province_a, province_b)
);

CREATE INDEX idx_treaties_a ON sov_treaties (province_a);
CREATE INDEX idx_treaties_b ON sov_treaties (province_b);

-- ---------------------------------------------------------------------------
-- 5. Wars — Active and historical conflicts tracked via a state machine.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_wars (
    id              BIGINT      PRIMARY KEY AUTO_INCREMENT,
    attacker_id     BIGINT      NOT NULL,                   -- FK → sov_provinces
    defender_id     BIGINT      NOT NULL,                   -- FK → sov_provinces
    casus_belli     VARCHAR(32) NOT NULL,                   -- BORDER_FRICTION, RECONQUEST, SUBJUGATION, INDEPENDENCE
    phase           VARCHAR(16) NOT NULL DEFAULT 'PREPARATION', -- PREPARATION, ACTIVE_SIEGE, RESOLVED
    siege_window_start VARCHAR(5) DEFAULT '20:00',          -- HH:mm server time
    siege_window_end   VARCHAR(5) DEFAULT '22:00',          -- HH:mm server time
    attacker_score  INT         NOT NULL DEFAULT 0,
    defender_score  INT         NOT NULL DEFAULT 0,
    declared_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_since    TIMESTAMP   DEFAULT NULL,               -- When PREPARATION ends
    resolved_at     TIMESTAMP   DEFAULT NULL,

    CONSTRAINT fk_war_attacker FOREIGN KEY (attacker_id) REFERENCES sov_provinces (id)
                               ON DELETE CASCADE,
    CONSTRAINT fk_war_defender FOREIGN KEY (defender_id) REFERENCES sov_provinces (id)
                               ON DELETE CASCADE
);

CREATE INDEX idx_wars_attacker ON sov_wars (attacker_id);
CREATE INDEX idx_wars_defender ON sov_wars (defender_id);
CREATE INDEX idx_wars_phase    ON sov_wars (phase);

-- ---------------------------------------------------------------------------
-- 6. Council Roles — Phase 2: Specialized roles for small-team provinces.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_council_roles (
    id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
    province_id BIGINT      NOT NULL,                       -- FK → sov_provinces
    player_uuid CHAR(36)    NOT NULL,                       -- FK → sov_players
    role        VARCHAR(16) NOT NULL DEFAULT 'NONE',        -- NONE, MARSHAL, CHANCELLOR, STEWARD
    assigned_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_role_province FOREIGN KEY (province_id) REFERENCES sov_provinces (id)
                                ON DELETE CASCADE,
    CONSTRAINT fk_role_player   FOREIGN KEY (player_uuid) REFERENCES sov_players (uuid)
                                ON DELETE CASCADE,
    CONSTRAINT uq_role_player   UNIQUE (player_uuid),
    CONSTRAINT uq_role_slot     UNIQUE (province_id, role)
);

CREATE INDEX idx_council_province ON sov_council_roles (province_id);

-- ---------------------------------------------------------------------------
-- 7. Caravans — Phase 2: Physical trade caravan audit log.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sov_caravans (
    id              BIGINT      PRIMARY KEY AUTO_INCREMENT,
    entity_uuid     CHAR(36)    NOT NULL,
    source_province BIGINT      NOT NULL,                   -- FK → sov_provinces
    target_province BIGINT      NOT NULL,                   -- FK → sov_provinces
    vault_value     DOUBLE      NOT NULL DEFAULT 0.0,
    status          VARCHAR(16) NOT NULL DEFAULT 'IN_TRANSIT', -- IN_TRANSIT, DELIVERED, AMBUSHED, EXPIRED
    spawned_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP   DEFAULT NULL,
    killer_uuid     CHAR(36)    DEFAULT NULL,               -- Non-null if AMBUSHED

    CONSTRAINT fk_caravan_source FOREIGN KEY (source_province) REFERENCES sov_provinces (id)
                                 ON DELETE CASCADE,
    CONSTRAINT fk_caravan_target FOREIGN KEY (target_province) REFERENCES sov_provinces (id)
                                 ON DELETE CASCADE
);

CREATE INDEX idx_caravans_status ON sov_caravans (status);
CREATE INDEX idx_caravans_source ON sov_caravans (source_province);
CREATE INDEX idx_caravans_target ON sov_caravans (target_province);
