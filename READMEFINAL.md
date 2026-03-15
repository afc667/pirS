# READMEFINAL.md — Sovereignty Phase 2 Complete Documentation

> **Sovereignty** — A Hardcore RPG Claim Engine for PaperMC 1.20.1+
> Phase 2: Micro-SMP (10-Player) Optimized Gameplay

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Gameplay Design](#gameplay-design)
4. [Phase 1 → Phase 2 Changes](#phase-1--phase-2-changes)
5. [Module Reference](#module-reference)
   - [Council Roles & Specialization](#1-council-roles--specialization)
   - [Vault Integration & Economy](#2-vault-integration--economy)
   - [Physical Trade Caravans](#3-physical-trade-caravans)
   - [ItemsAdder Integration](#4-itemsadder-integration)
   - [Progressions / Tech Tree](#5-progressions--tech-tree)
   - [Dynmap Integration](#6-dynmap-integration)
   - [Espionage & Subterfuge](#7-espionage--subterfuge)
6. [Configuration Reference](#configuration-reference)
7. [Database Schema](#database-schema)
8. [API Wrappers & Soft Dependencies](#api-wrappers--soft-dependencies)
9. [How to Build](#how-to-build)
10. [Deployment](#deployment)
11. [File Structure](#file-structure)

---

## Overview

**Sovereignty** is a custom PaperMC plugin that merges the best aspects of Factions, Towny, and Paradox Interactive grand strategy games (EU4, CK3, Victoria 3) into a single, high-performance claim engine.

### Design Philosophy: Micro-SMP

Phase 2 pivots the entire gameplay loop for a **10-player server** environment. In this tight-knit setting:

- **No massive faceless factions.** The game revolves around intimate **3v3v4 team rivalries**.
- **Deep "Cold War" paranoia.** Every diplomatic action is personal and visible.
- **Visible arms races.** Tech progression and military buildup are observable.
- **Physical geography matters.** Resources must be physically transported, borders are visible on the live map.
- **Quality over quantity.** Strategy and specialization always triumph over sheer numbers.

---

## Architecture

### Clean Architecture Principles

```
┌─────────────────────────────────────────────────┐
│                  SovereigntyPlugin               │  ← Entry point (JavaPlugin)
├─────────────────────────────────────────────────┤
│   Integration Layer (API Wrappers)               │
│   ├── VaultManager        (Economy)              │
│   ├── DynmapHook          (Live Map)             │
│   ├── ItemsAdderListener  (Custom Items)         │
│   └── ProgressionsHook    (Tech Tree)            │
├─────────────────────────────────────────────────┤
│   Manager Layer                                  │
│   ├── ProvinceManager     (Claims, CRUD)         │
│   ├── RoleManager         (Council Roles)        │
│   ├── CaravanManager      (Trade Entities)       │
│   └── StabilityEngine     (Stability Index)      │
├─────────────────────────────────────────────────┤
│   Listener Layer                                 │
│   ├── ChunkBoundaryListener                      │
│   ├── BlockProtectionListener                    │
│   ├── CaravanListener                            │
│   ├── CorePlacementListener                      │
│   ├── SiegeMechanicsListener                     │
│   └── EspionageListener                          │
├─────────────────────────────────────────────────┤
│   Data Layer                                     │
│   ├── DatabaseManager     (HikariCP Pool)        │
│   ├── ProvinceQueries     (Async SQL)            │
│   └── ChunkCache          (Caffeine O(1))        │
├─────────────────────────────────────────────────┤
│   Model Layer                                    │
│   ├── Province, CoreBlock, PlayerData            │
│   ├── War, Treaty, Caravan                       │
│   └── Enums: Rank, CouncilRole, Era,             │
│       DiplomaticRelation, CasusBelli, WarPhase   │
└─────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **All DB calls are async** (`CompletableFuture`) | Zero main-thread blocking; prevents TPS drops |
| **Caffeine cache** for chunk lookups | O(1) spatial queries, < 100 ns per lookup |
| **HikariCP** connection pool | Enterprise-grade JDBC pool with 10 max connections |
| **Interface-driven API wrappers** | Graceful degradation if third-party plugins are missing |
| **PDC for entity data** | Server-restart safe without extra DB sync |
| **MiniMessage** for all text | Modern Adventure API with gradients, hover, click |

### Threading Model

```
Main Thread (Bukkit)
  └── Event Listeners (PlayerMoveEvent, BlockBreakEvent, etc.)
       └── Cache reads: O(1) via Caffeine (no blocking)
       └── Cache writes: ConcurrentHashMap (thread-safe)

Sovereignty-DB Thread Pool (4 daemon threads)
  └── All SQL operations via CompletableFuture
  └── Schema init, province CRUD, chunk mappings

Bukkit Scheduler
  └── Caravan pathfinding ticks
  └── Sabotage progress timers
  └── Daily tribute/caravan spawn
```

---

## Gameplay Design

### The Micro-SMP Loop

```
 Found Province → Assign Roles → Claim Territory → Build Economy
        ↓                                              ↓
  Advance Era ← Tech Tree Sacrifices ← Physical Trade ←
        ↓
  Diplomacy (NAP / Ally / Rival) → War Declaration
        ↓                                    ↓
  Espionage / Sabotage               Siege Mechanics
        ↓                                    ↓
  Stability Management ←────────── War Resolution
```

### Core Gameplay Pillars

1. **Personal Rivalry:** Every player knows every other player. Diplomatic actions are deeply personal.
2. **Physical Economy:** No invisible money transfers. All wealth moves through the physical world via Trade Caravans.
3. **Visible Progression:** Tech tree advancement is a server event. Everyone sees when a province enters the Gunpowder Age.
4. **Strategic Depth:** Council roles force specialization. A team of 3 must choose: military power (Marshal), economic efficiency (Steward), or diplomatic leverage (Chancellor).
5. **Paranoia & Information:** Dynmap shows live borders. Forged passports enable stealth. Sabotage drains stability silently.

---

## Phase 1 → Phase 2 Changes

### ⚠️ Modified Logic

| Area | Phase 1 | Phase 2 |
|------|---------|---------|
| **Feudal Hierarchy** | Massive hierarchy (Citizen → Lord → Suzerain → Emperor) with multi-layered sub-vassal chains | Hierarchy **preserved in DB schema** for future-proofing, but active gameplay focuses entirely on **Lord** (Province Owner). Diplomacy centers on binary **Ally vs Rival** with **Non-Aggression Pacts** carrying severe Stability penalties if broken. |
| **Cultural Pressure** | Highly developed cities passively flip neighbor chunks over time | Wrapped behind `stability.cultural-pressure: false` config toggle. **Disabled by default.** Async task does not even initialize when false, saving CPU. |
| **Economy & Tribute** | Vassals paid automated invisible daily tribute (% of economy) | Invisible transfers **removed**. Tribute is withdrawn via Vault API, stored in physical Caravan entity's PDC, and physically transported across the map. |

### What Was NOT Changed

- Province creation, Core placement, 3×3 capital claim logic — **untouched**
- War state machine (PREPARATION → ACTIVE_SIEGE → RESOLVED) — **untouched**
- Chunk cache (Caffeine), database layer (HikariCP) — **untouched**
- Block protection, explosion handling — **preserved**, with new ItemsAdder layer added on top
- `StabilityEngine.computeStabilityDelta()` — **identical** math, only constructor updated

---

## Module Reference

### 1. Council Roles & Specialization

**Package:** `com.sovereignty.roles`
**Enum:** `com.sovereignty.models.enums.CouncilRole`

| Role | Passive Buff | Exclusive Permission |
|------|-------------|---------------------|
| **Marshal** | +5% PvP/Siege damage to ALL province members while online | Bypass permission checks to declare wars |
| **Chancellor** | −15% Influence cost for claiming chunks | Exclusive GUI access for NAPs and Trade Agreements |
| **Steward** | +10% yield on physical trade caravans | Sole access to Province Vault ledger |

**Rules:**
- Each province has **at most one** of each role (enforced by `UNIQUE(province_id, role)` constraint)
- A player may hold **only one** role at a time
- Leaving the province **revokes** the role automatically
- Marshal buff is **online-presence dependent** — if the Marshal logs off, the buff deactivates

**Code:**
```java
RoleManager roleManager = new RoleManager(logger);
roleManager.assignRole(playerUuid, provinceId, CouncilRole.MARSHAL);
double dmgMultiplier = roleManager.getPvpDamageMultiplier(provinceId); // 1.05 if Marshal online
double claimCost = baseCost * roleManager.getClaimCostMultiplier(provinceId); // 0.85 if Chancellor assigned
```

### 2. Vault Integration & Economy

**Package:** `com.sovereignty.integration`
**Class:** `VaultManager`

- **Interface-driven:** If Vault is not installed, `isAvailable()` returns `false` and all methods gracefully no-op
- **Thread-safe:** Vault API calls are safe on the main thread (they are simple balance operations)
- **Formatting:** Uses Vault's native currency formatting via `economy.format(amount)`

**API Methods:**
| Method | Description |
|--------|-------------|
| `getBalance(OfflinePlayer)` | Returns the player's Vault balance |
| `withdraw(OfflinePlayer, double)` | Debits the player's balance |
| `deposit(OfflinePlayer, double)` | Credits the player's balance |
| `format(double)` | Formats currency using Vault's economy provider |

### 3. Physical Trade Caravans

**Package:** `com.sovereignty.caravan`
**Class:** `CaravanManager`

**Lifecycle:**
1. **Trigger:** Daily tick at configured time (default: 12:00 server time) for all active Trade Agreements
2. **Withdraw:** Vault API debits the tribute amount from the sender's balance
3. **Spawn:** A Llama entity spawns at the source province's Core with:
   - Custom display name showing the Vault value
   - PDC keys: `is_caravan`, `caravan_value`, `caravan_source`, `caravan_target`
4. **Pathfind:** The Llama navigates toward the target province's Core
5. **Delivery:** On arrival, the Vault value is deposited to the recipient's balance
6. **Ambush:** If killed by a player, the PDC value is deposited to the killer's balance

**PDC Keys:**
| Key | Type | Purpose |
|-----|------|---------|
| `sovereignty:is_caravan` | BYTE | Flags the entity as a Sovereignty caravan |
| `sovereignty:caravan_value` | DOUBLE | The Vault currency value being transported |
| `sovereignty:caravan_source` | LONG | Source province database ID |
| `sovereignty:caravan_target` | LONG | Target province database ID |

**Ambush Alert (MiniMessage):**
```
⚔ CARAVAN AMBUSHED! PlayerName has intercepted a trade caravan and stolen $500.00!
```

### 4. ItemsAdder Integration

**Package:** `com.sovereignty.integration`
**Class:** `ItemsAdderListener` (implements `Listener`)

| Feature | Namespace | Event |
|---------|-----------|-------|
| Custom Province Core | `sovereignty:government_stone` | `BlockPlaceEvent` |
| Siege Cannon | `sovereignty:siege_cannon` | `EntityExplodeEvent` |
| Forged Passport | `sovereignty:forged_passport` | `PlayerMoveEvent` |
| Custom Weapons | `sovereignty:steel_sword` | `EntityDamageByEntityEvent` |

**Siege Rule:** Vanilla TNT cannot damage blocks in claimed territory. Only explosives with the `sovereignty:siege_cannon` PDC tag deal block damage during active Siege warfare.

**Graceful Fallback:** If ItemsAdder is not installed, `isAvailable()` returns `false` and all event handlers skip processing immediately.

### 5. Progressions / Tech Tree

**Package:** `com.sovereignty.integration`
**Class:** `ProgressionsHook`
**Enum:** `com.sovereignty.models.enums.Era`

| Era | Tier | Unlocks | Trigger |
|-----|------|---------|---------|
| **Tribal** | 1 | Vanilla only. Diamond/Netherite **blocked**. | Default starting era |
| **Feudal** | 2 | ItemsAdder Steel weapons, Iron-tier armor | Sacrifice 64 Iron, 32 Gold, 16 Emerald to Core |
| **Gunpowder** | 3 | Netherite usage, Siege Cannon crafting/placement | Sacrifice 32 Diamond, 8 Netherite Scrap, 64 Gunpowder to Core |

**Permissive Fallback:** If the Progressions plugin is not installed, all feature gates default to **unlocked** — no content is blocked.

**Advancement is one-way.** Once a province reaches Feudal, it cannot regress to Tribal.

### 6. Dynmap Integration

**Package:** `com.sovereignty.integration`
**Class:** `DynmapHook`

| Feature | Description |
|---------|-------------|
| **Live Borders** | 2D polygons drawn for every claimed chunk, updated on claim/unclaim events |
| **Diplomatic Colors** | Blue = Neutral, Green = Allied/NAP, Red = Rival/War, Gold = Own territory |
| **Siege Alerts** | Crossed-swords icon placed at Core coordinates during active siege |
| **Stealth Integration** | Player icons hidden from Dynmap when using Forged Passports |

**Reflective API Access:** Dynmap integration uses Java reflection to avoid compile-time coupling. If Dynmap is not present, all methods gracefully no-op.

### 7. Espionage & Subterfuge

**Package:** `com.sovereignty.listeners`
**Class:** `EspionageListener`

#### Forged Passports
- **Item:** `sovereignty:forged_passport` (ItemsAdder custom item)
- **Mechanic:** When crossing into rival territory, the border alert is suppressed and the player's Dynmap icon is hidden
- **Detection:** Checks off-hand first, then full inventory

#### Sabotage
1. Player sneaks (`PlayerToggleSneakEvent`) within 5 blocks of an enemy Core
2. Must carry a Forged Passport
3. An async repeating task starts, rendering a particle progress bar above the player's head
4. Color transitions from red → green as progress completes
5. Action bar shows a `[████████████████████]` style progress bar
6. After 15 seconds of uninterrupted sneaking:
   - Enemy province Stability is drained by 15 points
   - Forged Passport is consumed
   - Server-wide sabotage alert is broadcast
7. **Cancellation:** Un-sneaking, taking damage, or logging off cancels the sabotage

---

## Configuration Reference

```yaml
# config.yml — Full Phase 2 Configuration

database:
  host: "localhost"
  port: 3306
  name: "sovereignty"
  username: "root"
  password: ""

core:
  default-hp: 100
  max-level: 5
  capital-claim-radius: 1

claims:
  base-cost: 10.0
  distance-scale-factor: 16.0
  buffer-zone-chunks: 1

stability:
  overextension-per-chunk: 0.5
  high-tax-penalty: 10.0
  civil-war-threshold: 20.0
  civil-war-liberty-spike: 40.0
  cultural-pressure: false          # ← Phase 2: Disabled by default

warfare:
  preparation-hours: 24
  siege-window-start: "20:00"
  siege-window-end: "22:00"

vassalage:
  tribute-rate: 0.10
  liberty-desire-tribute-halt: 50.0
  liberty-desire-independence: 80.0

caravan:                              # ← Phase 2: New
  entity-type: LLAMA
  speed: 0.6
  despawn-timeout-minutes: 30
  daily-spawn-tick: "12:00"

diplomacy:                            # ← Phase 2: New
  nap-break-stability-penalty: 25.0

itemsadder:                           # ← Phase 2: New
  enabled: true
  namespaces:
    core-block: "sovereignty:government_stone"
    siege-cannon: "sovereignty:siege_cannon"
    forged-passport: "sovereignty:forged_passport"
    steel-sword: "sovereignty:steel_sword"

progressions:                         # ← Phase 2: New
  enabled: true
  era-advancement:
    feudal-sacrifice:
      IRON_INGOT: 64
      GOLD_INGOT: 32
      EMERALD: 16
    gunpowder-sacrifice:
      DIAMOND: 32
      NETHERITE_SCRAP: 8
      GUNPOWDER: 64

dynmap:                               # ← Phase 2: New
  enabled: true
  border-opacity: 0.35
  border-weight: 2
  fill-opacity: 0.15

espionage:                            # ← Phase 2: New
  sabotage-radius: 5
  sabotage-duration-seconds: 15
  sabotage-stability-drain: 15.0
```

---

## Database Schema

### New Tables (Phase 2)

#### `sov_council_roles`
```sql
CREATE TABLE IF NOT EXISTS sov_council_roles (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    province_id BIGINT NOT NULL,       -- FK → sov_provinces
    player_uuid CHAR(36) NOT NULL,     -- FK → sov_players
    role        VARCHAR(16) DEFAULT 'NONE',  -- NONE, MARSHAL, CHANCELLOR, STEWARD
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (player_uuid),              -- One role per player
    UNIQUE (province_id, role)         -- One role slot per province
);
```

#### `sov_caravans`
```sql
CREATE TABLE IF NOT EXISTS sov_caravans (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_uuid     CHAR(36) NOT NULL,
    source_province BIGINT NOT NULL,    -- FK → sov_provinces
    target_province BIGINT NOT NULL,    -- FK → sov_provinces
    vault_value     DOUBLE DEFAULT 0.0,
    status          VARCHAR(16) DEFAULT 'IN_TRANSIT',  -- IN_TRANSIT, DELIVERED, AMBUSHED, EXPIRED
    spawned_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP DEFAULT NULL,
    killer_uuid     CHAR(36) DEFAULT NULL   -- Non-null if AMBUSHED
);
```

### Modified Tables

#### `sov_players`
- Added column: `council_role VARCHAR(16) DEFAULT 'NONE'`

#### `sov_provinces`
- Added column: `era VARCHAR(16) DEFAULT 'TRIBAL'`

---

## API Wrappers & Soft Dependencies

All third-party integrations use **soft dependencies** (`softdepend` in `plugin.yml`). The plugin starts and functions fully even if none of the third-party plugins are installed.

| Plugin | Class | Fallback Behavior |
|--------|-------|-------------------|
| **Vault** | `VaultManager` | Economy features disabled. Caravans cannot spawn. |
| **Dynmap** | `DynmapHook` | Map features disabled. No border polygons or siege alerts. |
| **ItemsAdder** | `ItemsAdderListener` | Custom item features disabled. Vanilla blocks/items used. |
| **Progressions** | `ProgressionsHook` | All features unlocked by default. No era restrictions. |

### Integration Pattern

```java
// VaultManager: Standard Bukkit service provider lookup
RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);

// DynmapHook: Reflective API access (no compile-time coupling)
Object dynmapApi = dynmap.getClass().getMethod("getAPI").invoke(dynmap);

// ItemsAdderListener: Plugin presence check + PDC-based item identification
boolean available = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

// ProgressionsHook: Plugin presence check with permissive fallback
boolean available = Bukkit.getPluginManager().getPlugin("Progressions") != null;
```

---

## How to Build

### Prerequisites

- **Java 17+** (JDK, not JRE)
- **Gradle 8.x** (or use the included Gradle wrapper)
- **Internet connection** (for downloading dependencies)

### Build Steps

```bash
# 1. Clone the repository
git clone https://github.com/afc667/pirS.git
cd pirS

# 2. Build the plugin JAR
./gradlew build

# The compiled JAR will be at:
# build/libs/Sovereignty-2.0.0-SNAPSHOT.jar

# 3. (Optional) Clean build
./gradlew clean build

# 4. (Optional) Build without tests
./gradlew build -x test
```

### Gradle Configuration

The project uses **Gradle Kotlin DSL** (`build.gradle.kts`):

| Dependency | Scope | Purpose |
|-----------|-------|---------|
| `io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT` | `compileOnly` | PaperMC server API |
| `com.github.MilkBowl:VaultAPI:1.7.1` | `compileOnly` | Vault economy API |
| `com.zaxxer:HikariCP:5.1.0` | `implementation` | JDBC connection pool |
| `com.github.ben-manes.caffeine:caffeine:3.1.8` | `implementation` | High-performance cache |

### Repositories

| Repository | URL |
|-----------|-----|
| Maven Central | `https://repo.maven.apache.org/maven2` |
| PaperMC | `https://repo.papermc.io/repository/maven-public/` |
| JitPack | `https://jitpack.io` |

---

## Deployment

### Server Requirements

- **PaperMC 1.20.1+** (or any Paper fork: Purpur, Folia, etc.)
- **Java 17+** runtime
- **MySQL 8.0+** or **MariaDB 10.6+** database
- **10-player** recommended server size

### Installation

1. Place `Sovereignty-2.0.0-SNAPSHOT.jar` in the server's `plugins/` directory
2. (Optional) Install soft dependencies:
   - [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy provider (e.g., EssentialsX)
   - [Dynmap](https://www.spigotmc.org/resources/dynmap.274/)
   - [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/)
   - Progressions
3. Start the server — Sovereignty will generate `config.yml` with default values
4. Configure `database` section in `config.yml` with your MySQL credentials
5. Restart the server — schema will auto-initialize

### Directory Structure After First Run

```
plugins/
├── Sovereignty/
│   ├── config.yml          ← Main configuration
│   └── ... (generated data)
└── Sovereignty-2.0.0-SNAPSHOT.jar
```

---

## File Structure

```
src/
├── main/
│   ├── java/com/sovereignty/
│   │   ├── cache/
│   │   │   └── ChunkCache.java              ← Caffeine O(1) spatial cache
│   │   ├── caravan/
│   │   │   └── CaravanManager.java           ← [NEW] Physical trade caravan lifecycle
│   │   ├── core/
│   │   │   └── SovereigntyPlugin.java        ← [MODIFIED] Main plugin entry point
│   │   ├── database/
│   │   │   ├── DatabaseManager.java          ← HikariCP connection pool
│   │   │   └── queries/
│   │   │       └── ProvinceQueries.java      ← Async province CRUD
│   │   ├── diplomacy/
│   │   │   └── DiplomacyEngine.java          ← [MODIFIED] Interface with caravan tribute
│   │   ├── integration/
│   │   │   ├── VaultManager.java             ← [NEW] Vault economy wrapper
│   │   │   ├── DynmapHook.java               ← [NEW] Dynmap live map integration
│   │   │   ├── ItemsAdderListener.java       ← [NEW] Custom items event listener
│   │   │   └── ProgressionsHook.java         ← [NEW] Tech tree feature gating
│   │   ├── listeners/
│   │   │   ├── BlockProtectionListener.java  ← Territory protection (unchanged)
│   │   │   ├── CaravanListener.java          ← [NEW] Caravan ambush handler
│   │   │   ├── ChunkBoundaryListener.java    ← Border announcements (unchanged)
│   │   │   ├── CorePlacementListener.java    ← [NEW] Custom core registration
│   │   │   ├── EspionageListener.java        ← [NEW] Forged passports & sabotage
│   │   │   └── SiegeMechanicsListener.java   ← [NEW] Siege cannon enforcement
│   │   ├── managers/
│   │   │   └── ProvinceManager.java          ← Province lifecycle (unchanged)
│   │   ├── models/
│   │   │   ├── Caravan.java                  ← [NEW] Caravan data model
│   │   │   ├── ChunkPosition.java            ← Immutable chunk coordinates
│   │   │   ├── CoreBlock.java                ← Province Core with PDC
│   │   │   ├── PlayerData.java               ← Player data model
│   │   │   ├── Province.java                 ← Province data model
│   │   │   ├── Treaty.java                   ← Diplomatic treaty model
│   │   │   ├── War.java                      ← War state machine model
│   │   │   └── enums/
│   │   │       ├── CasusBelli.java           ← War justifications
│   │   │       ├── CouncilRole.java          ← [NEW] MARSHAL, CHANCELLOR, STEWARD
│   │   │       ├── DiplomaticRelation.java   ← Diplomatic states
│   │   │       ├── Era.java                  ← [NEW] TRIBAL, FEUDAL, GUNPOWDER
│   │   │       ├── Rank.java                 ← Player hierarchy ranks
│   │   │       └── WarPhase.java             ← PREPARATION, ACTIVE_SIEGE, RESOLVED
│   │   ├── roles/
│   │   │   └── RoleManager.java              ← [NEW] Council role assignment & buffs
│   │   ├── stability/
│   │   │   └── StabilityEngine.java          ← [MODIFIED] Cultural pressure toggle
│   │   └── warfare/
│   │       └── WarfareEngine.java            ← War state machine (unchanged)
│   └── resources/
│       ├── config.yml                        ← [MODIFIED] Full Phase 2 config
│       ├── plugin.yml                        ← [MODIFIED] Soft dependencies added
│       └── schema.sql                        ← [MODIFIED] New tables added
├── build.gradle.kts                          ← [MODIFIED] VaultAPI dependency added
└── READMEFINAL.md                            ← [NEW] This documentation file
```

---

## License

This plugin is developed by the Sovereignty Team for private server use.

---

*Last updated: Phase 2 — Micro-SMP Integration Build*
