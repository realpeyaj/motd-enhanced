# motd-enhanced

[![Version](https://img.shields.io/badge/version-1.0-blue.svg)](https://github.com/realpeyaj/motd-enhanced/releases)
[![Java](https://img.shields.io/badge/java-25-orange.svg)](https://openjdk.org)
[![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Folia%20%7C%20Spigot-green.svg)](https://papermc.io)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

Real-time Minecraft server MOTD and icon editor powered by an embedded local web interface. Standalone fork of [motdgg-bukkit](https://github.com/aternosorg/motdgg-bukkit).

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/motd editor` | Open local web editor session | `motdenhanced.editor` |
| `/motd apply` | Reload MOTD and icon from disk | `motdenhanced.apply` |
| `/motd get <host>` | Copy MOTD and icon from another server | `motdenhanced.apply` |
| `/motd maintenance <on\|off>` | Toggle maintenance mode | `motdenhanced.maintenance` |
| `/motd reload` | Reload configuration | `motdenhanced.reload` |

## License

MIT
