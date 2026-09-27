# tpa <img alt="tpa Logo" src="fabric/src/main/resources/assets/tpa/icon.png" width="30"/>

> **[中文文档](https://github.com/Tinmoli/tpa/blob/main/README_CN.md)** | Click here for Chinese documentation

A Minecraft server-side mod that adds various teleportation-related commands, including /home, /tpa, /back, /rtp, and more.

Current version: **1.0.7**

Project URL: [https://github.com/Tinmoli/tpa](https://github.com/Tinmoli/tpa)

[Changelog](https://github.com/Tinmoli/tpa/blob/main/CHANGELOG.md)

## Supported Platforms

| Platform | Supported Version |
|----------|-------------------|
| Fabric | 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3 |

> Fabric only.

## Dependencies

- Fabric Loader (use the minimum version declared by each version-specific JAR)
- Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, or 26.3
- Java 21 for Minecraft 1.21.11, or Java 25 for the Minecraft 26.x series

## Available Commands

- `/tpals` - Display all available commands
- `/spawn [<disableSafetyCheck>]` - Teleport to the Overworld spawn point. Use `true` to skip safety checks
- `/back [<disableSafetyCheck>]` - Teleport to your last death location. Use `true` to skip safety checks
- `/sethome <name>` - Set a home location
- `/home [<name>]` - Teleport to a home. Omit name to go to the default home
- `/delhome <name>` - Delete a home
- `/renamehome <name> <newName>` - Rename a home
- `/defaulthome <name>` - Set the default home
- `/homes` - View all homes and choose icons from all vanilla items in the GUI
- `/warp <name>` - Teleport to a public warp
- `/warps` - View all public warps; operators can customize warp icons in the GUI
- `/setwarp <name>` - Set a public warp (requires operator permissions)
- `/delwarp <name>` - Delete a public warp (requires operator permissions)
- `/renamewarp <name> <newName>` - Rename a public warp (requires operator permissions)
- `/tpa <player>` - Send a teleport request to a player
- `/tpahere <player>` - Request a player to teleport to you
- `/tpaaccept <player>` - Accept a teleport request
- `/tpadeny <player>` - Deny a teleport request
- `/rtp [<dimension>]` - Random teleportation. Can specify a dimension
- `/tpastorage json-to-sqlite` - Import legacy JSON into SQLite and automatically reload it (requires administrator permissions)
- `/tpareload` - Reload configuration, SQLite data, and bundled language files (requires administrator permissions)

<br>

## Configuration

Configuration file is located at `config/tpa/config.yml`. Each option includes comments for easy customization:

```yaml
# TPA Plugin Configuration File
# Run /tpareload after editing, or restart the server

# Language setting, options: zh_cn, en_us
language: en_us
# /back command configuration
back:
  # Whether to enable this command
  enabled: true
  # Whether to delete death location record after teleport
  deleteAfterTeleport: false
# /home command configuration
home:
  enabled: true
  # Maximum number of homes per player
  playerMaximum: 20
  # Whether to auto-delete invalid locations (when world doesn't exist)
  deleteInvalid: false
  # Teleport delay in seconds, 0 for instant teleport
  delay: 0
# /tpa command configuration
tpa:
  enabled: true
  delay: 3
  # Whether moving cancels pending teleport
  cancelOnMove: true
  # Seconds before expiration to remind; requests expire after 120 seconds
  requestExpireReminder: 30
# /warp command configuration
warp:
  enabled: true
  deleteInvalid: false
# /spawn command configuration
spawn:
  enabled: true
  # World ID for spawn point, defaults to Overworld
  world_id: minecraft:overworld
# /rtp command configuration
rtp:
  enabled: true
  # Minimum random teleport range (blocks)
  minRange: 1000
  # Maximum random teleport range (blocks)
  maxRange: 2000
  # Enable success and failure cooldowns
  cooldownEnabled: true
  # Cooldown after success, in seconds; 0 disables it
  cooldownSeconds: 30
  # Concurrent requests across the server (1-10); reject requests when full
  maxConcurrentLoads: 10
  # Cooldown after final failure or cancellation, in seconds; 0 disables it
  failureCooldownSeconds: 30
  # Maximum random positions tried per request (1-100)
  maxAttempts: 10
  # Total search timeout, in seconds (1-120)
  timeoutSeconds: 15
  # Individual chunk loading timeout, in seconds (1-30)
  loadTimeoutSeconds: 5
  # Ordinary damage protection after teleport, in ticks (0-1200); 0 disables it
  invulnerabilityTicks: 60
  # Blocked biome IDs or quoted tags beginning with #
  biomeBlacklist: ['#minecraft:is_ocean', '#minecraft:is_river']
  # Dangerous block IDs or tags; fluids, leaves and bedrock are always rejected
  floorBlacklist: [minecraft:lava, minecraft:water, minecraft:magma_block, minecraft:powder_snow, '#minecraft:leaves', minecraft:cactus, minecraft:fire, minecraft:soul_fire, minecraft:sweet_berry_bush, minecraft:cobweb, minecraft:campfire, minecraft:soul_campfire]
  # Per-dimension search mode, center, range and blacklist settings
  dimensions: {}
```

### Random Teleport

Use `/rtp` in the current dimension, or specify `/rtp minecraft:the_nether`, `/rtp minecraft:the_end` or a loaded custom dimension ID. The action bar displays search progress until a safe destination is found or the search ends. There is no fixed countdown; timeouts or exhausted attempts report failure.

Coordinates are selected around the target dimension's spawn between minRange and maxRange. Open dimensions use surface ground; ceiling dimensions search interior ground. Fluids, leaves and dangerous blocks are excluded. Snow depends on its collision shape. Searches fail when no safe destination exists.

Cooldown starts after success or final failure. cooldownEnabled=false disables both cooldowns; cooldownSeconds=0 disables success cooldown and failureCooldownSeconds=0 disables failure cooldown. Reusing the command shows remaining seconds. Capacity rejection does not apply cooldown. Post-teleport protection does not block damage that bypasses invulnerability, such as the void or `/kill`.

Set per-dimension rules under rtp.dimensions using full dimension IDs. mode accepts auto, surface or interior. Omitted ranges and blacklists inherit global values; the center defaults to that dimension's spawn. Example:

```yaml
  dimensions:
    'custom:moon':
      mode: surface
      centerX: 0
      centerZ: 0
      minRange: 100
      maxRange: 800
      biomeBlacklist: []
```

Run `/tpareload` after editing configuration; pending searches are cancelled. Missing settings are populated with built-in comments. Higher concurrency increases server load, and new chunk generation time depends on hardware and terrain.

### Custom Home Icons

In the `/homes` GUI:

- Left-click a home to teleport
- Click the bottom compass, then left-click a Home to set the default without teleporting or changing its name/icon colors. Click the compass again to cancel. Middle-click remains available when the client supports it; use the compass in vanilla survival.
- Right-click a home to review its name and coordinates, then left-click Confirm to delete. Cancel or close to keep it.
- `Shift + Left-click` a home to open the vanilla item icon picker
- `Shift + Right-click` a home to quickly restore the default bed icon

After setting a default home, run `/home` without a name to teleport there. The
default selection does not change the name color or icon. Homes without a
custom icon retain the cyan bed. The existing `/defaulthome <name>` command remains available.

The picker displays 45 vanilla items per page and provides previous, next, back,
and reset controls. Only items in the `minecraft` namespace are listed; items
from other mods are excluded. Only the item registry ID is stored. Icon data is
stored in SQLite, and existing homes keep the default bed icon.

### Custom Warp Icons and Operator Permissions

All players can open `/warps` and left-click a warp to teleport. The following actions require administrator permissions:

- Right-click a warp to delete it
- `Shift + Left-click` a warp to open the vanilla item icon picker
- `Shift + Right-click` a warp to quickly restore the default Eye of Ender icon

`/setwarp`, `/delwarp`, `/renamewarp`, `/tpastorage`, and `/tpareload` require
operator permissions. `/tpals` displays these administrative commands only to
operators.

`/tpareload` rereads `config.yml` and `storage.db`, synchronizes the bundled
English and Chinese language files, and clears the language cache. Pending
delayed teleports and TPA requests are cancelled so callbacks created with old
settings cannot run afterward. Current `/back` death locations are preserved.

## SQLite Storage and Legacy Data Import

Players, homes and warps are stored in SQLite. Older databases migrate automatically on first startup without a command. A storage.db.pre-v2-*.bak backup is created first; failure rolls back and preserves the old database.

If storage.db does not exist, an existing storage.json is imported automatically and retained. Existing SQLite takes precedence; the explicit import command replaces its data. To downgrade, stop the server and restore the pre-migration backup; older mods cannot read the new schema.


The runtime database is located at `config/tpa/storage.db`.

To explicitly replace an existing database with legacy JSON data, place
`storage.json` in `config/tpa/` and run this command as an operator.
Normal SQLite upgrades do not require this command:

```text
/tpastorage json-to-sqlite
```

A successful import automatically performs the same complete reload as
`/tpareload`, so imported data is loaded immediately without a restart. The source JSON file is
not deleted, and an existing `storage.db` is retained as a timestamped backup.
Data is first written to a temporary database and read back for verification;
the live database is updated transactionally only after verification succeeds. Archive or
remove the JSON after checking the import. The command refuses to run when the
JSON file is missing or empty.

## Language Files

Language files are located in `config/tpa/lang/`. The bundled `zh_cn.json` and
`en_us.json` files are synchronized on every startup:

- Missing keys are added using the bundled default translations
- Keys removed from the bundled files are also removed from external files
- Existing values are preserved, so customized translations are not overwritten
- Other custom language files are never modified
- Invalid built-in language files are backed up as `.bak` before being rebuilt

This makes new translation keys available automatically after a mod update.
You can also create another language file and select it in the configuration.

<br>

## Data Storage

- Config file: `config/tpa/config.yml`
- Language files: `config/tpa/lang/`
- SQLite player data: `config/tpa/storage.db`
- Legacy JSON import source (optional): `config/tpa/storage.json`

<br>

## Building

Use the repository's standard Gradle Wrapper. No system Gradle installation or
PowerShell is required:

```bat
:: Windows
gradlew.bat buildAllVersions
```

```sh
# Linux / macOS
./gradlew buildAllVersions
```

This cross-platform Gradle task builds all six version projects and
collects the resulting JARs in the root `dist/` directory.

If you encounter any issues, please submit an [Issue](https://github.com/Tinmoli/tpa/issues)

<br>

## Credits

- [TeleportCommands](https://github.com/MrSn0wy/TeleportCommands) — Inspiration and reference implementation
- [Dalict](https://github.com/Dalict) — Contributions and support

## Development layout

All versions share `fabric/src/main/java` and `fabric/src/main/resources`. The `versions/` directories contain build configuration and require the full checkout. `buildAllVersions` compiles and packages all six versions. Builds require JDK 25; the 1.21.11 artifact targets Java 21.