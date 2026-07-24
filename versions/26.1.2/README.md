# tpa <img alt="tpa Logo" src="https://github.com/Tinmoli/Tpa/fabric/src/main/resources/tpa.png" width="30"/>

> **[中文文档](https://github.com/Tinmoli/tpa/blob/main/README_CN.md)** | Click here for Chinese documentation

A Minecraft server-side mod that adds various teleportation-related commands, including /home, /tpa, /back, /rtp, and more.

Current version: **1.0.4**

Project URL: [https://github.com/Tinmoli/tpa](https://github.com/Tinmoli/tpa)

[Changelog](https://github.com/Tinmoli/tpa/blob/main/CHANGELOG.md)

## Supported Platforms

| Platform | Supported Version |
|----------|-------------------|
| Fabric | 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 |

> **Note**: Starting from v1.0.3, the project has transitioned to a Fabric-only mod. NeoForge and Quilt support has been removed.

## Dependencies

- Fabric Loader (use the minimum version declared by each version-specific JAR)
- Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, or 26.2
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
```

### Custom Home Icons

In the `/homes` GUI:

- Left-click a home to teleport
- Middle-click a home to make it the default home
- Right-click a home to delete it
- `Shift + Left-click` a home to open the vanilla item icon picker
- `Shift + Right-click` a home to quickly restore the default bed icon

After setting a default home, run `/home` without a name to teleport there. The
default home has a gold name in the GUI and uses a yellow bed when it has no
custom icon. The existing `/defaulthome <name>` command remains available.

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

The mod now always uses `config/tpa/storage.db` as its runtime storage. The JSON
runtime backend and the `storage.backend` setting have been removed.

When upgrading from an older version, back up your data, place the old
`storage.json` in `config/tpa/`, and run this command as an operator:

```text
/tpastorage json-to-sqlite
```

A successful import automatically performs the same complete reload as
`/tpareload`, so imported data is loaded immediately without a restart. The source JSON file is
not deleted, and an existing `storage.db` is retained as a timestamped backup.
Data is first written to a temporary database and read back for verification;
the live database is replaced only after verification succeeds. Archive or
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

This cross-platform Gradle task builds all five independent projects and
collects the resulting JARs in the root `dist/` directory.

If you encounter any issues, please submit an [Issue](https://github.com/Tinmoli/tpa/issues)

<br>

## Credits

- [TeleportCommands](https://github.com/MrSn0wy/TeleportCommands) — Inspiration and reference implementation
- [Dalict](https://github.com/Dalict) — Contributions and support
