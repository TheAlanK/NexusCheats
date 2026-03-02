# NexusCheats

A **cheat panel** mod for [Starsector](https://fractalsoftworks.com/) built on the [NexusUI](https://github.com/TheAlanK/NexusUI) framework. Add credits, resources, weapons, ships, XP, and story points via a convenient in-game overlay.

![Starsector 0.98a-RC7](https://img.shields.io/badge/Starsector-0.98a--RC7-blue)
![Version 0.9.0-beta](https://img.shields.io/badge/Version-0.9.0--beta-orange)
![License: MIT](https://img.shields.io/badge/License-MIT-green)

## Features

- **Credits** — Add credits with preset amounts (1K, 10K, 100K, 1M) or custom values
- **Resources** — Add supplies, fuel, crew, marines, heavy machinery, metals, transplutonics, organics, volatiles, food, drugs, organs, hand weapons, and luxury goods
- **Weapons** — Add any weapon by ID with configurable quantity
- **Ships** — Add any ship variant by ID to your fleet (spawns at max CR)
- **XP & Story Points** — Add XP (10K–1M presets) and story points (1–25 presets)

All commands are executed thread-safely through the NexusUI command queue system, ensuring game state is modified on the correct thread.

## Installation

1. Install [LazyLib](https://fractalsoftworks.com/forum/index.php?topic=5444.0)
2. Install [NexusUI](https://github.com/TheAlanK/NexusUI)
3. Download the latest release or clone this repository
4. Copy the `NexusCheats` folder into your `Starsector/mods/` directory
5. Enable **NexusCheats** in the Starsector launcher

## Usage

1. Load a save game with all three mods enabled
2. Click the floating **N** button on the campaign screen
3. Switch to the **Cheats** tab in the overlay
4. Select a category from the sidebar (Credits, Resources, Weapons, Ships, XP & Skills)
5. Enter values or use preset buttons, then click the action button
6. Status feedback appears below confirming the action or showing errors

### Common Weapon IDs
| ID | Weapon |
|----|--------|
| `autopulse` | Autopulse Laser |
| `squall` | Squall MLRS |
| `harpoon_single` | Harpoon MRM |
| `gauss` | Gauss Cannon |
| `hellbore` | Hellbore Cannon |

### Common Ship Variant IDs
| ID | Ship |
|----|------|
| `onslaught_Standard` | Onslaught (Standard) |
| `paragon_Standard` | Paragon (Standard) |
| `conquest_Standard` | Conquest (Standard) |
| `doom_Strike` | Doom (Strike) |
| `wolf_CS` | Wolf (CS) |

## Dependencies

- [LazyLib](https://fractalsoftworks.com/forum/index.php?topic=5444.0)
- [NexusUI](https://github.com/TheAlanK/NexusUI) v0.9.0+

## Building from Source

Requires JDK 8+ and `NexusUI.jar` on the classpath. Run `build.bat` in the mod root.

## License

[MIT](LICENSE)
