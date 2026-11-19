# BleachSMP

A BLEACH-inspired plugin for Paper 1.21.11.

## Current milestone

- First-time players choose Soul Reaper, Hollow, Quincy, or Fullbringer.
- Faction choice is stored in `plugins/BleachSMP/players.yml` and cannot currently be changed.
- Each faction receives one tagged starter item. Right-clicking it in the main hand activates a faction ability at Rank 4 or better (configurable): Soul Reaper's Crescent Cut, Hollow's Cero Rush, Quincy's Heilig Pfeil, or Fullbringer's Bringer Guard. Abilities have configurable cooldowns.
- `/bleach` and `/bleach status` show the player's faction, rank, points to the next rank, and active weekly boss challenge; `/bleach choose` opens the selection menu before a choice is made.
- Five configurable power tiers map to faction-specific titles and max health. Rank 1 has 40 health points (20 hearts) and a Strength I cap. Rank 0: Soul King is reserved for later event/lore implementation.
- Eligible PvP kills award configurable rank points. The same pair of players shares a configurable cooldown in either direction to discourage kill trading.
- Custom boss first clears award points once per player and boss ID. A weekly rotating boss challenge offers a separate repeatable PvE route. Boss credit requires a configurable damage contribution. Damage credit is saved with the boss and carries over when that same boss is loaded again.
- Operators can mark the living entity they are looking at with `/bleach boss mark <id>` and remove the mark with `/bleach boss unmark`. IDs accept lowercase letters, digits, `_`, and `-`.
- Hueco Mundo is a separate custom-generated world with white dunes, the Severed Gate, the Cathedral of Ribs, the Broken Watch, and the Las Noches outskirts. Any player can travel with a Garganta, return to their saved location, and fight hostile Gillian, Adjuchas, and rare Vasto Lorde Hollows.

## Build

Requires Java 21 and Maven:

```sh
mvn package
```

Copy `target/bleach-smp-0.1.0.jar` into the server's `plugins` directory.

The Garganta recipe is one Echo Shard, one Ender Eye, and one Ghast Tear. Right-click the crafted item to travel; use it again in Hueco Mundo to return. `/bleach garganta` provides the same travel action without consuming an item. The per-player return location is saved across restarts. Players arrive at the far end of the Severed Gate's lantern-lined approach, facing its broad staircase and twin 48-block towers. Hueco Mundo's terrain generates as players explore. Use a dedicated world name. These landmarks generate in new chunks. Configure its world name and Hollow population in `plugins/BleachSMP/config.yml`.

Head north from the Severed Gate toward Las Noches at X 0, Z -384. The south causeway leads through the fortress wall into a lantern-lit courtyard with an arena ring and a walkable domed keep.

Hueco Mundo's Hollows have animated models that work on vanilla clients without a resource pack: the Gillian is an 11-block-tall cloaked giant that spawns in open terrain, the Adjuchas is a six-limbed dune hunter, and the Vasto Lorde has a split mask, a horned crown, and blade-like arms. Aim at the creature's body rather than its decorative extremities: combat still uses the underlying mob's hitbox.

Other future work includes additional boss encounters, awakenings and expanded ability kits, death recovery, the Rank 0 event role, and the server resource pack/custom weapon models. Configure rank thresholds, faction titles, health, ability unlocks and cooldowns, PvP cooldowns, boss contribution, rewards, and the weekly challenge rotation in `plugins/BleachSMP/config.yml`.

## First abilities

All four abilities use the tagged starter item in the main hand, unlock at Rank 4 by default, and report remaining cooldown when used too early. Existing tagged starter items work; newly created items also show the control in their lore.

| Faction | Ability | Default effect | Cooldown |
| --- | --- | --- | --- |
| Soul Reaper | Crescent Cut | Deals 6 damage to the aimed-at living target within 4 blocks and briefly slows it | 20s |
| Hollow | Cero Rush | A forward lunge | 15s |
| Quincy | Heilig Pfeil | Fires a fast, unpickable spirit arrow without consuming ammunition | 12s |
| Fullbringer | Bringer Guard | Grants Resistance I for 5 seconds | 25s |

Ability cooldowns are saved with your player data and continue counting down while you are offline or the server is stopped. Lower rank numbers are stronger, so an `unlock-rank` of 4 allows Ranks 4, 3, 2, and 1. Ability settings are under `abilities.<FACTION>` in `config.yml`; restart the server after changing them.
