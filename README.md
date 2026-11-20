# BleachSMP

A BLEACH-inspired plugin for Paper 1.21.11.

## Current milestone

- First-time players choose Soul Reaper, Hollow, Quincy, or Fullbringer.
- Faction choice is stored in `plugins/BleachSMP/players.yml` and cannot currently be changed.
- Each faction receives one tagged starter item. Right-clicking it in the main hand activates a faction ability at Rank 4 or better (configurable): Soul Reaper's Crescent Cut, Hollow's Cero Rush, Quincy's Heilig Pfeil, or Fullbringer's Bringer Guard. Abilities have configurable cooldowns.
- `/bleach` and `/bleach status` show the player's faction, rank, points to the next rank, and active weekly boss challenge; `/bleach choose` opens the selection menu before a choice is made.
- Five configurable power tiers map to faction-specific titles. Every rank uses vanilla health (10 hearts) so fights stay balanced; Rank 1 has a Strength I cap. Rank 0: Soul King is reserved for later event/lore implementation.
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

The first time each player arrives through a Garganta after installing this feature, a short camera sweep reveals the Severed Gate before returning control at the normal arrival spot. The intro needs no resource pack or client mod. The player’s seen flag is saved across reconnects and server restarts, so later trips skip the cinematic. To disable it, set `hueco-mundo.arrival-cutscene: false` in `plugins/BleachSMP/config.yml`.

Head north from the Severed Gate toward Las Noches at X 0, Z -384. The south causeway leads through the fortress wall into a lantern-lit courtyard with an arena ring and a walkable domed keep.

Hueco Mundo's Hollows have animated models that work on vanilla clients without a resource pack: the Gillian is an 11-block-tall cloaked giant that spawns in open terrain, the Adjuchas is a six-limbed dune hunter, and the Vasto Lorde has a split mask, a horned crown, and blade-like arms. Aim at the creature's body rather than its decorative extremities: combat still uses the underlying mob's hitbox.

Other future work includes additional boss encounters, death recovery, the Rank 0 event role, and the server resource pack/custom weapon models. Configure rank thresholds, faction titles, ability unlocks and cooldowns, PvP cooldowns, boss contribution, rewards, and the weekly challenge rotation in `plugins/BleachSMP/config.yml`.

## Playstyle awakenings

After choosing a faction, play in Survival or Adventure to build evidence from what you actually do. Melee, sprinting into fights, sneaking, landed projectiles, shield blocks, exploring new areas, mining, building, crafting, enchanting, harvesting, breeding, fishing, sharing beneficial potions, and boss rewards all count. Habits contribute different amounts toward different styles. Most habits have a short scoring cooldown, and no single habit gives enough evidence to unlock a form on its own.

Your first form unlocks automatically at 36 evidence **at any rank**. Advanced forms need 80 fresh evidence and **Rank 2 or better** by default. Each form locks in the style that best matches the habits you showed in that stage. A strong secondary habit can also add a short bonus to the release. For example, a sneaking enchanter who also mines can unlock the Mirage Shikai with a Haste bonus, then earn a Vanguard Bankai by fighting more aggressively. Progress, unlocked forms, and release cooldowns persist across restarts.

| Faction | First form | Advanced form |
| --- | --- | --- |
| Soul Reaper | Shikai | Bankai |
| Hollow | Resurrección | Segunda Etapa |
| Quincy | Schrift | Vollständig |
| Fullbringer | Awakened Fullbring | Complete Fullbring |

| Style | Example habits | First ability → Advanced ability |
| --- | --- | --- |
| Vanguard | Melee, sprint attacks, boss clears | **Crimson Rush** (lunging slash) → **Heaven-Splitting Charge** (stronger slash, splash damage, and Speed) |
| Mirage | Ambushes, enchanting, exploration | **Shattered Reflection** (disappear and fool hostile mobs; your next hit blinds) → **Mirror Labyrinth** (longer concealment and Speed) |
| Marksman | Projectile hits, fishing, boss clears | **Spirit Lance** (fast spirit arrow) → **Astral Volley** (spread of piercing arrows) |
| Wayfarer | Exploration, fishing, sprint attacks | **Riftstep** (leap and Slow Falling) → **Windwalk** (longer leap, Slow Falling, and Speed) |
| Artificer | Mining, building, crafting | **Stone Resonance** (Haste and Night Vision) → **Living Forge** (Haste II, Night Vision, and Absorption) |
| Keeper | Blocking, farming, sharing potions | **Warding Pulse** (Absorption) → **Sanctuary** (Regeneration and Resistance). Both also buff nearby scoreboard teammates. |

### First-form releases

Every faction's first form is a 30-second release rather than a single move: Soul Reaper **Shikai**, Hollow **Resurrección**, Quincy **Schrift**, and **Awakened Fullbring**. The playstyle that unlocked the form decides which one awakens. Releasing calls out its command to nearby players, shows its name on screen, plays the faction's release animation and sounds, and bursts spiritual pressure that pushes nearby enemies back. A countdown boss bar and a coloured aura stay up while it is released.

| Faction | Release animation | Passive while released | Technique hits also… |
| --- | --- | --- | --- |
| Soul Reaper | Rising three-strand reiatsu helix | Speed I | Slow the target |
| Hollow | Soul-fire column, smoke, and a roar | Resistance I | Wither the target and heal you |
| Quincy | Descending halo and pillars of light | Jump Boost II | Mark the target with Glowing |
| Fullbringer | Enchant glyphs converging on the hand, then a totem burst | Haste I | Weaken the target |

While released, the starter item has two techniques, both usable at any rank: **right-click** for the main technique (6-second cooldown) and **sneak + right-click** for the secondary one (10-second cooldown). The action bar shows both and their cooldowns.

Kits are built so players rarely match:

- **Two forms per playstyle.** Each playstyle has two form variants with different names, call-outs, colours, and main techniques. Which one a player gets comes from their UUID, so it never changes for them and players split evenly between the two.
- **A secondary technique from the secondary playstyle.** Players with a strong secondary habit get that playstyle's technique on sneak + right-click. Players without one get a stable pick from every other technique.
- **Twelve techniques, styled per faction.** Each faction gives every technique its own name, colours, particles, sounds, and on-hit effect.

That is more than a hundred kits per faction. In a simulation of 30 players with melee-heavy habits, about one pair shared an identical kit on average.

| Playstyle | Form A main technique | Form B main technique |
| --- | --- | --- |
| Vanguard | **Wave**: a cutting wave that tears through its path | **Flurry**: lunge at a target and land a five-hit combo |
| Mirage | **Blink**: strike from behind a target up to 12 blocks away, blinding it | **Scatter**: blind enemies, turn invisible, and hop around as afterimages |
| Marksman | **Beam**: a long-range blast that bursts on impact | **Rain**: call ten bolts down on the area you aim at |
| Wayfarer | **Dash**: cut every enemy you pass | **Skyfall**: leap high, then crash down in a shockwave (no fall damage) |
| Artificer | **Slam**: hurl nearby enemies skyward | **Pillars**: a line of erupting pillars that launch enemies |
| Keeper | **Ward**: heal and shield teammates, push enemies back | **Chains**: bind enemies in sight, drag them in, and root them |

Form names and call-outs by faction (Form A / Form B):

| Playstyle | Soul Reaper | Hollow | Quincy | Fullbringer |
| --- | --- | --- | --- | --- |
| Vanguard | Akagetsu / Homuramaru | Lobo Carmesí / Tiburón | The Assault / The Rampage | Crimson Knuckle / Rewind Blade |
| Mirage | Kagerō / Oboromaru | Espejismo / Sombra Pálida | The Illusion / The Mirror | Mirror Coat / Shuffle Deck |
| Marksman | Ryūsei / Amagumo | Halcón Negro / Escorpión | The Starshot / The Deluge | Bullet Echo / Echo Rain |
| Wayfarer | Hayate / Kumokiri | Viento Hueco / Águila Hueca | The Flight / The Zenith | Sky Runner / Meteor Boots |
| Artificer | Iwagane / Iwaoni | Coloso / Osario | The Bulwark / The Pillar | Iron Maker / Forge Ground |
| Keeper | Mamorigami / Kusarigami | Caparazón / Araña Blanca | The Haven / The Tether | Heart Lock / Lifeline |

Technique names by faction:

| Technique | Soul Reaper | Hollow | Quincy | Fullbringer |
| --- | --- | --- | --- | --- |
| Wave | Crimson Fang | Garra Roja (triple claw) | Heiliger Sturm | Knuckle Breaker |
| Blink | Haze Step | Sonido Fantasma | Schattenschritt | Reflection Swap |
| Beam | Falling Star | Cero Oscuro (wider, faster) | Sternregen | Echo Shot |
| Dash | Gale Passage | Embestida | Lichtsprung | Ground Skip |
| Slam | Mountain Breaker | Terremoto | Erdbrecher | Forge Quake |
| Ward | Shrine Ward | Coraza Viva | Lichtschild | Locket Ward |
| Flurry | Hundred Moon Flurry | Frenesí | Klingenwirbel | Rapid Rewind |
| Scatter | Mirror Haze | Eco Vacío | Spiegelbruch | Shuffle Step |
| Rain | Heaven's Rain | Lluvia de Cero | Pfeilregen | Echo Barrage |
| Skyfall | Falling Moon | Caída del Rey | Sternenfall | Meteor Drop |
| Pillars | Stone Fangs (calcite) | Colmillos de Hueso (bone) | Lichtsäulen (glass) | Forge Spikes (iron) |
| Chains | Binding Thread | Cadenas del Hambre | Bindungsfaden | Tether Line |

Pillars are temporary block displays, so they never change the world. `/bleach awakenings` shows each player's own form, call-out, and both techniques.

Press **F** again to seal the form early. It also seals on death, logout, or changing worlds. The first-form release cooldown starts once the form seals. Tune the duration, both technique cooldowns, and technique damage under `awakenings.release` in `config.yml`.

Advanced forms (Bankai, Segunda Etapa, Vollständig, Complete Fullbring) remain single powerful releases, now with a faction-coloured burst, a rising helix, a shockwave, and thunder. The sealed starter abilities also have their own effects: Crescent Cut draws a pale crescent through the target, Cero Rush flashes red and leaves a soul-fire trail, Heilig Pfeil shows a reishi bow and a light trail on the arrow, and Bringer Guard surrounds the player with orbiting gold guard plates.

Use `/bleach awakenings` to see your total progress, rank requirements, and unlocked powers. Your next power and its trait stay hidden until you unlock it. Hold your faction starter item and press **F** (swap hands) to release your highest unlocked form. `/bleach release first` and `/bleach release advanced` let you use a specific form instead. Both forms share a release cooldown (40 seconds for first forms, 95 for advanced forms by default). Right-click still uses the original starter ability on its own cooldown. Set the advanced rank requirement and cooldowns in the `awakenings` section of `config.yml`.

## First abilities

All four abilities use the tagged starter item in the main hand, unlock at Rank 4 by default, and report remaining cooldown when used too early. Existing tagged starter items work; newly created items also show the control in their lore.

| Faction | Ability | Default effect | Cooldown |
| --- | --- | --- | --- |
| Soul Reaper | Crescent Cut | Deals 6 damage to the aimed-at living target within 4 blocks and briefly slows it | 20s |
| Hollow | Cero Rush | A forward lunge | 15s |
| Quincy | Heilig Pfeil | Fires a fast, unpickable spirit arrow without consuming ammunition | 12s |
| Fullbringer | Bringer Guard | Grants Resistance I for 5 seconds | 25s |

Ability cooldowns are saved with your player data and continue counting down while you are offline or the server is stopped. Lower rank numbers are stronger, so an `unlock-rank` of 4 allows Ranks 4, 3, 2, and 1. Ability settings are under `abilities.<FACTION>` in `config.yml`; restart the server after changing them.
