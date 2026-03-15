# Sovereignty — Hardcore RPG Claim Engine

A highly advanced, hardcore RPG Claim Engine for PaperMC 1.20.1+ that merges the best aspects of Factions, Towny, and Paradox Interactive grand strategy games (Crusader Kings, Europa Universalis).

## Features

- **Feudal Hierarchy:** Citizen → Lord → Suzerain → Emperor vertical progression
- **Province Core System:** Physical, upgradeable claim anchors with 3×3 capital areas and exponential expansion costs
- **Advanced Diplomacy:** Relational state-machine (Neutral, NAP, Ally, Rival, Overlord, Vassal) with automated tribute and Liberty Desire
- **Dynamic Stability:** 0–100 index with overextension, taxes, war exhaustion modifiers; Civil War disaster state
- **Casus Belli Warfare:** Strict war justification, 24h preparation, windowed siege combat, Core destruction victory
- **O(1) Spatial Lookups:** Caffeine-cached chunk → province resolution
- **Fully Async Database:** All queries via `CompletableFuture` on dedicated thread pool (HikariCP)
- **Adventure API:** MiniMessage-powered hex/gradient territory announcements

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Platform | Java 17+, PaperMC API 1.20.1+ |
| Database | MySQL/PostgreSQL via HikariCP |
| Caching | Caffeine (O(1) chunk lookups) |
| Messaging | Adventure API (MiniMessage) |
| Persistence | PersistentDataContainer (PDC) for Core block NBT |

## Project Structure

```
src/main/java/com/sovereignty/
├── core/               # Main plugin entry point
├── cache/              # Caffeine chunk cache
├── database/           # HikariCP pool + async queries
│   └── queries/        # PreparedStatement DAOs
├── diplomacy/          # Diplomacy state-machine interface
├── listeners/          # Optimized event handlers
├── managers/           # Province lifecycle + cache sync
├── models/             # Data models (Province, CoreBlock, War, Treaty)
│   └── enums/          # Rank, DiplomaticRelation, CasusBelli, WarPhase
├── stability/          # Dynamic Stability Index + Cultural Pressure
└── warfare/            # Warfare state-machine interface
```

## Building

```bash
./gradlew build
```

Requires access to the PaperMC Maven repository (`https://repo.papermc.io/repository/maven-public/`).

## Configuration

Copy the generated `config.yml` and configure your database connection:

```yaml
database:
  host: "localhost"
  port: 3306
  name: "sovereignty"
  username: "root"
  password: ""
```

## License

All rights reserved.