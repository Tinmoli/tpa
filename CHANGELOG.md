# Changelog
All notable changes to this project will be documented in this file.

---

## [1.0.4] - 2026-07-24

### Added
- Added a paginated icon picker for freely choosing Home or Warp icons from all vanilla items
- The Homes GUI supports middle-clicking a home to make it the default for `/home`
- Home and Warp GUI action hints are split across two lore lines to avoid overly long text
- The Homes GUI opens the picker with `Shift + Left-click` and quickly resets the icon with `Shift + Right-click`
- Home icon data supports both JSON and SQLite and remains compatible with existing homes without an icon field
- Added custom icons to the Warps GUI; only operators can open the icon picker or reset warp icons
- Added SQLite as a runtime storage backend, selectable through `storage.backend` with `json` or `sqlite`
- Added the operator-only `/tpastorage json-to-sqlite` command to convert `storage.json` to `storage.db`
- Added the operator-only `/tpastorage sqlite-to-json` command to convert `storage.db` to `storage.json`
- Added the SQLite JDBC dependency and bundled the driver into the mod JAR

### Fixed
- Fixed external language files missing new translation keys after a mod update, which caused raw keys to appear in chat and GUIs
- Unified administrator command visibility so regular players no longer see warp management or storage conversion commands in `/tpals`
- Fixed `/tpals` not showing the JSON and SQLite conversion commands to operators
- Fixed the `home.playerMaximum` configuration not being enforced
- Fixed players being able to create additional homes after reaching the configured limit
- Fixed duplicate home names being incorrectly reported as a limit error when the player was at the limit

### Improved
- Bundled English and Chinese language files are now synchronized on startup: new keys are added, obsolete keys are removed, and existing custom values are preserved
- Invalid built-in language files are backed up and restored from bundled content
- Configuration files are now explicitly written using UTF-8
- Made SQLite the default backend for new installations and configurations missing storage settings
- Added validation for `storage.backend`; unsupported values fall back to `sqlite`
- Negative `home.playerMaximum` values are normalized to `0`
- Updated the English and Chinese READMEs with SQLite configuration, data file, and conversion instructions

---

## [1.0.3] - 2026-03-31

### Major Changes
- **Upgraded to Minecraft 26.1** - Full support for Fabric 26.1 (Java 25)
- **Removed Multi-Platform Support** - Removed NeoForge and Quilt loader support, transitioned to Fabric-only project

> **Note**: Starting from v1.0.3, this project is now a Fabric-only mod. NeoForge and Quilt are no longer supported.

### Technical Updates
- **Build Chain Upgrade**:
  - Fabric Loom upgraded to 1.15-SNAPSHOT
  - Java version upgraded to 25
  - Removed Mappings dependency (using Mojang official mappings)

- **API Adaptations**:
  - Fixed compilation errors caused by removal of `ServerPlayer.displayClientMessage()`
  - Added `tools.sendPlayerMessage()` unified message sending method (Action Bar uses `ClientboundSetActionBarTextPacket`, chat uses `sendSystemMessage`)
  - Adapted to sgui 2.0+ new API (`setCallback` signature changes)
  - Removed `GuiElementBuilder.setSkullOwner()` calls (API removed)

### Bug Fixes
- Fixed GUI callback method ambiguity errors
- Fixed deprecated message sending API calls in all command classes

---

## [1.0.2] - 2026-03-23

### Added
- Config option `tpa.requestExpireReminder`: Reminder time before TPA request expires (seconds). Set to `0` to disable (previously fixed at 30 seconds)
- `/spawn` command now reads `spawn.world_id` from config, supporting custom spawn dimensions (previously hardcoded to Overworld)

### Fixed
- Fixed `ConcurrentModificationException` in `StorageManager.cleanup()` when removing elements during for-each loop
- Fixed resource leaks in `StorageLoader` and `StorageMigrator` where `FileReader` was not closed
- Fixed `StorageLoader` not returning early after initialization when storage file doesn't exist, causing continued migration logic
- Fixed race condition in `tpa.java` `exportBuiltinLangFiles()` due to repeated `listFiles()` calls
- Fixed TPA expiration reminder Timer still triggering and sending invalid messages after request accept/deny
- Fixed TPA accept/deny button click commands not quoting player names, causing parsing failures for names with spaces
- Fixed `DeathLocationStorage` using non-thread-safe `HashMap`, changed to `ConcurrentHashMap`
- Fixed `/rtp` command only attempting one random location, which almost always failed in ocean/void worlds. Now retries up to 10 times
- Removed leftover debug `System.out.println` in `warp.java`

### Improved
- Added in-memory cache for `tools.getTranslatedText()`, avoiding disk reads on every message, improving performance
- Changed `Random` to `ThreadLocalRandom` in `TeleportDelayManager` and `tools` to eliminate multi-threading contention
- Removed unused `ModCommand` enum
- Removed dead code methods `PrintHomes()` and `PrintWarps()` that were replaced by GUI

---

## [1.0.1] - 2026-03-21

### Added
- `/rtp` command: Random teleportation to random locations in the world, supports dimension specification
- Config option `rtp.enabled`: Enable/disable random teleport feature
- Config options `rtp.minRange` and `rtp.maxRange`: Set random teleport range (blocks)
- Configuration auto-upgrade: Automatically detects and fills missing config options on new version startup
- Chinese comments automatically added to each config item for easy reading and editing
- Added Quilt support (built on Fabric compatibility layer)
- Created NeoForge subproject

### Fixed
- Fixed `/tpaaccept` and `/tpadeny` command permission checks requiring confirmation execution
- Fixed `/back` and `/spawn` command force teleport button permission check issues
- Fixed snakeyaml dependency loss after server restart (properly bundled using Gradle `include`)
- Fixed TPA teleport countdown movement detection not working (changed to main thread player position reading)

### Improved
- Config system supports incremental updates, new config items won't overwrite user-modified content
- All chat box buttons unified to use chained syntax
- `settings.gradle` added NeoForge / Quilt Maven repositories, with `exclusiveContent` for precise dependency filtering

---

## [1.0.0] - 2026-03-16

### Added
- `/tpals` command: Display all available commands and their descriptions
- `/tpa` delayed teleport system: Countdown after accepting request (default 3 seconds), with actionbar countdown and enchanting table particle effects
- Config option `tpa.delay`: Set teleport wait time in seconds, 0 for instant teleport
- Config option `tpa.cancelOnMove`: Cancel pending teleport when player moves
- Language support: `zh_cn` (Simplified Chinese), `en_us` (English)

### Fixed
- None
