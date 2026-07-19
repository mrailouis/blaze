<h1 align="center">
  <img src="src/main/resources/assets/blaze/icon.png" alt="BLAZE logo" width="110"><br>
  BLAZE
</h1>

<div align="center">
  <b>A client-side Fabric mod for Minecraft Blaze Slayer utilities.</b><br>
  <sub>Provides a configurable ClickGUI, autoclicking, Blaze highlighting, and route-following tools.</sub>
</div>

<br>

<div align="center">

[![GitHub](https://img.shields.io/badge/GitHub-Source-d45b34?style=for-the-badge&logo=github&logoColor=white)](https://github.com/mrailouis/blaze)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.10%20%7C%201.21.11%20%7C%2026.1-237c76?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net)
[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-947022?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org)
[![Fabric](https://img.shields.io/badge/Fabric-Client%20Mod-8b8068?style=for-the-badge)](https://fabricmc.net)
[![License](https://img.shields.io/github/license/mrailouis/blaze?style=for-the-badge&color=237c76)](LICENSE)

</div>

---

## What is BLAZE?

**BLAZE** is a client-only Fabric mod containing focused tools for Minecraft Blaze Slayer. Its current gameplay features include configurable left- and right-click automation, Blaze highlighting, and a route follower that can pathfind to a nearby Blaze or a set of coordinates.

Open the main ClickGUI with **Right Shift** or `/blaze`. Settings are stored locally and restored when the client starts.

---

## Implemented features

- **ClickGUI:** browse modules, toggle available features, and configure supported settings.
- **Autoclicker:** configure left and right clicking independently, with hold or toggle activation, custom keyboard or mouse binds, and up to 30 clicks per second.
- **Blaze ESP:** highlight nearby living Blaze entities through terrain.
- **Pathfinder:** build and follow a route to the nearest Blaze or supplied block coordinates, with route rendering, automatic replanning, and configurable camera behaviour.
- **Pathfinding diagnostics:** show route diagnostics in chat and render open platform-edge blocks for development and testing.
- **Global toggle:** disable BLAZE systems while leaving `/blaze toggle` available.

---

## Commands

| Command | Behaviour |
| --- | --- |
| `/blaze` | Opens the main ClickGUI. |
| `/blaze help` | Displays the command list. |
| `/blaze toggle` | Enables or disables BLAZE systems. |
| `/blaze splits recent` | Displays stored recent boss splits. |
| `/blaze splits average` | Displays stored average boss splits. |
| `/blaze profit` | Displays stored session and total profit values. |
| `/blaze profit reset` | Resets the current session profit. |
| `/blaze profit fullreset` | Clears all stored profit values. |
| `/blaze cleandata` | Clears configuration, profit, and split data. |
| `/blaze pathfind` | Pathfinds to the nearest Blaze. |
| `/blaze pathfind <x> <y> <z>` | Pathfinds to the supplied coordinates. |
| `/blaze pathdebug on` | Enables pathfinding diagnostics in chat. |
| `/blaze pathdebug off` | Disables pathfinding diagnostics in chat. |
| `/blaze edge` | Renders open edge blocks on the current platform. |
| `/blaze edithuds` | Opens the HUD editor. |

`/blaze hud`, `/blaze gui`, and `/blaze edit` are aliases for `/blaze edithuds`.

---

## Data and client behaviour

| Item | BLAZE behaviour |
| --- | --- |
| Stored data | Saves the global enabled state, module toggles, autoclicker and pathfinder settings, and profit and split values in `config/blaze/client-state.json`. |
| Input automation | The autoclicker invokes Minecraft's normal attack and use-item actions. The pathfinder controls movement and view direction while following an active route. |
| Network traffic | BLAZE contains no custom network client, analytics, or telemetry service. |
| Data removal | `/blaze cleandata` deletes the saved state file and restores the default in-memory state. |
| Default state | BLAZE begins enabled, while individual modules begin disabled. |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.2 or newer for the exact Minecraft version you intend to run.
2. Install matching versions of [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
3. Use Java 21 for Minecraft 1.21.10 or 1.21.11, and Java 25 for Minecraft 26.1.
4. Download the matching BLAZE release from [GitHub Releases](https://github.com/mrailouis/blaze/releases).
5. Place the normal BLAZE JAR, not the `-sources.jar`, in your Minecraft `mods` directory.
6. Launch the game and press **Right Shift** or run `/blaze`.

---

## Support and contributing

- **Bug reports:** open a [GitHub issue](https://github.com/mrailouis/blaze/issues)
- **Feature requests:** open a [GitHub issue](https://github.com/mrailouis/blaze/issues)

Contributions are welcome. Fork the repository, create a focused branch, build every affected Minecraft target, and open a pull request describing the change and its compatibility impact.

---

## License

BLAZE is released under **CC0 1.0**; see [LICENSE](LICENSE).
