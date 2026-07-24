# tpa <img alt="tpa Logo" src="https://github.com/Tinmoli/Tpa/fabric/src/main/resources/tpa.png" width="30"/>

> **[中文文档](https://github.com/Tinmoli/tpa/blob/main/README_CN.md)** | Click here for Chinese documentation

A Minecraft server-side mod that adds various teleportation-related commands, including /home, /tpa, /back, /rtp, and more.

Current version: **1.0.4**

Project URL: [https://github.com/Tinmoli/tpa](https://github.com/Tinmoli/tpa)

[Changelog](https://github.com/Tinmoli/tpa/blob/main/CHANGELOG.md)

## Supported Platforms

| Platform | Supported Version |
|----------|-------------------|
| Fabric | 26.1 |

> **Note**: Starting from v1.0.3, the project has transitioned to a Fabric-only mod. NeoForge and Quilt support has been removed.

## Dependencies

- Fabric Loader >= 0.16.10
- Minecraft 26.1
- Java 25

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
- `/tpastorage json-to-sqlite` - Convert JSON data to SQLite (operators only)
- `/tpastorage sqlite-to-json` - Convert SQLite data to JSON (operators only)

<br>

## Configuration

Configuration file is located at `config/tpa/config.yml`. Each option includes comments for easy customization:

```yaml
# TPA Plugin Configuration File
# Changes require server restart to take effect

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
# Storage backend; supported values: json, sqlite; defaults to sqlite
storage:
  backend: sqlite
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
compatible with both JSON and SQLite, and existing homes keep the default bed icon.

### Custom Warp Icons and Operator Permissions

All players can open `/warps` and left-click a warp to teleport. Only operators can:

- Right-click a warp to delete it
- `Shift + Left-click` a warp to open the vanilla item icon picker
- `Shift + Right-click` a warp to quickly restore the default Eye of Ender icon

`/setwarp`, `/delwarp`, `/renamewarp`, and `/tpastorage` require operator
permissions. `/tpals` displays these administrative commands only to operators.

## Storage Backends and Data Conversion

The mod supports JSON and SQLite as runtime storage backends. SQLite is the default:

- `storage.backend: sqlite` uses `config/tpa/storage.db` (default)
- `storage.backend: json` uses `config/tpa/storage.json`

Existing configurations explicitly set to `json` continue using JSON. New
installations, configurations missing the `storage` section, and invalid backend
values use SQLite.

Only server operators can run the in-game conversion commands:

```text
/tpastorage json-to-sqlite
/tpastorage sqlite-to-json
```

Conversion does not delete the source file. Back up your data before converting,
change `storage.backend` after conversion, and restart the server to activate the
new runtime backend.

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
- JSON player data: `config/tpa/storage.json`
- SQLite player data: `config/tpa/storage.db`

<br>

## Building

```bash
# Linux
./gradlew build

# Windows
.\gradlew.bat build
```

If you encounter any issues, please submit an [Issue](https://github.com/Tinmoli/tpa/issues)

<br>

## Credits

- [TeleportCommands](https://github.com/MrSn0wy/TeleportCommands) — Inspiration and reference implementation
- [Dalict](https://github.com/Dalict) — Contributions and support
