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

### Hueco Mundo terrain

The desert is split into regions that blend into each other:

- **Dune seas**: long, wind-aligned ridges with a gentle windward slope and a steep slip face.
- **Salt flats**: flat basins of bleached stone broken by a web of grey cracks.
- **Bleached mesas**: terraced plateaus up to about 100 blocks high. Their cliffs show bands of calcite and older rock.
- **The Forest of Menos**: dark caverns at about y 16–45 under roughly a third of the desert. Their floors are basalt and ash, and quartz trees grow in them, some tipped with faint light. Sinkholes about every 500 blocks drop into them. Each shaft ends in a drift of powder snow that breaks the fall. The only way back up is to build or dig out.

Across the surface are leafless quartz trees, tilted crystal spires, the half-buried skeletons of something enormous with walk-in skulls, and scattered bone shards and faint lights. The terrain eases flat around every landmark. Hollows spawn on the sand, not on top of trees, spires or bones.

Terrain is generated once per chunk, so a world explored before this update keeps its old terrain in visited areas. To see the new terrain everywhere, stop the server and delete the Hueco Mundo world folder (`HuecoMundo` by default). It and every landmark regenerate on the next start. Players' saved return locations are kept.

The first time each player arrives through a Garganta after installing this feature, a short camera sweep reveals the Severed Gate before returning control at the normal arrival spot. The intro needs no resource pack or client mod. The player’s seen flag is saved across reconnects and server restarts, so later trips skip the cinematic. To disable it, set `hueco-mundo.arrival-cutscene: false` in `plugins/BleachSMP/config.yml`.

Head north from the Severed Gate toward Las Noches at X 0, Z -384. The south causeway leads through the fortress wall into a lantern-lit courtyard with an arena ring and a walkable domed keep.

Hueco Mundo's Hollows have animated models that work on vanilla clients without a resource pack: the Gillian is an 11-block-tall cloaked giant that spawns in open terrain, the Adjuchas is a six-limbed dune hunter, and the Vasto Lorde has a split mask, a horned crown, and blade-like arms. Aim at the creature's body rather than its decorative extremities: combat still uses the underlying mob's hitbox.

### The Vasto Lorde

The Vasto Lorde is the apex Hollow and a server-wide event, not a random spawn. Only one can exist at a time.

- **How it appears:** about once every 8 minutes that someone spends deep in the desert (250+ blocks from the Gate and away from landmarks), the wind stops, the sand trembles, and a few seconds later it rises nearby. The whole server is told, and players in Hueco Mundo get its rough coordinates. After a kill there is a 90-minute cooldown.
- **Toughness:** 600 health, plus 150 for each extra player nearby when it appears (up to 1024). It has heavy armour and can't be knocked back.
- **Hierro:** nothing but players can hurt it. Fall damage, lava, suffocation, poison, TNT and tamed or summoned mobs do nothing, and player projectiles deal 60%.
- **Spiritual pressure:** standing near it slows you.
- **Abilities, all telegraphed:**
  - **Cero:** it charges, shows a locked aim line, then fires a beam that stops at blocks. Step off the line.
  - **Sonído:** it vanishes, appears behind you, and strikes a moment later.
  - **Reiatsu crush:** a red ring marks the area; it leaps and slams down. Leave the ring.
  - **Adjuchas:** it calls two at 70% and two at 35% health.
- **Resurrección (50% health):** a 3-second invulnerable release with a shockwave. Afterwards it is faster and hits harder, fires three Ceros in a fan, and uses **Gran Rey Cero**: a long charge, a ring marking where you stood, then a slow sphere that detonates there.
- **No leashing or kiting:** if it is pulled more than 56 blocks from where it rose, it Sonídos back and heals 5%. If nobody is within 40 blocks it regenerates, and after 5 minutes alone it sinks back into the sand.
- **Rewards:** everyone who dealt at least 5% of its health gets:
  - its boss first-clear rank points
  - a glinting **Vasto Lorde Mask** trophy naming the slayers
  - a large amount of XP
  - if they are mastering their Bankai and fought in their first-form release, one mastery victory

  The kill is announced server-wide.

Admins can summon one for testing with `/bleach boss vasto` while standing in Hueco Mundo. Tune everything under `hueco-mundo.vasto-lorde` in `config.yml`.

Other future work includes additional boss encounters, death recovery, the Rank 0 event role, and the server resource pack/custom weapon models. Configure rank thresholds, faction titles, ability unlocks and cooldowns, PvP cooldowns, boss contribution, rewards, and the weekly challenge rotation in `plugins/BleachSMP/config.yml`.

## Playstyle awakenings

After choosing a faction, play in Survival or Adventure to build evidence from what you actually do. Melee, sprinting into fights, sneaking, landed projectiles, shield blocks, exploring new areas, mining, building, crafting, enchanting, harvesting, breeding, fishing, sharing beneficial potions, and boss rewards all count. Habits contribute different amounts toward different styles. Most habits have a short scoring cooldown, and no single habit gives enough evidence to unlock a form on its own.

Your first form becomes dormant at 36 evidence **at any rank**. The advanced-form quest begins at 80 fresh evidence and **Rank 2 or better** by default. Each form is based on the habits you showed while earning it. A strong secondary habit can also add a short bonus to the release. For example, a sneaking enchanter who also mines can unlock the Mirage Shikai with a Haste bonus, then earn a Vanguard Bankai by fighting more aggressively. Progress, unlocked forms, and release cooldowns persist across restarts.

| Faction | First form | Advanced form |
| --- | --- | --- |
| Soul Reaper | Shikai | Bankai |
| Hollow | Resurrección | Segunda Etapa |
| Quincy | Schrift | Vollständig |
| Fullbringer | Awakened Fullbring | Complete Fullbring |

| Style | Example habits | Advanced-form ultimate |
| --- | --- | --- |
| Vanguard | Melee, sprint attacks, boss clears | Two volleys of three giant waves |
| Mirage | Ambushes, enchanting, exploration | Strike up to five enemies in turn from behind, then return |
| Marksman | Projectile hits, fishing, boss clears | A 26-bolt storm over a wide area |
| Wayfarer | Exploration, fishing, sprint attacks | A three-second tornado that drags enemies in, then hurls them skyward |
| Artificer | Mining, building, crafting | Three expanding rings of erupting pillars |
| Keeper | Blocking, farming, sharing potions | An eight-second sanctum: allies regenerate and resist, enemies are slowed and worn down |

### Crisis awakening (first forms)

Full playstyle evidence no longer unlocks a first form by itself. The form becomes **dormant** and only wakes when a
true rival pushes the player to the brink in a real player fight. Mobs never count.

A hidden breaking-point meter fills during a fight where both players are trading hits:

| Moment | Meter |
| --- | --- |
| A rival takes you to 3 hearts or less | +20 |
| Your rival has a form released and you don't | +25 |
| Each extra attacker beyond one (2v1, 3v1…) | +15 |
| Your rival outranks you | +15 |
| You hit back while on 3 hearts or less | +10 per hit |
| A friend or teammate dies near you mid-fight | +20 |

When the meter is full (100), the blow that would kill you is cancelled. Instead the world slows, your faction's inner
voice speaks, and after two seconds of invulnerability the form bursts out, releasing immediately at half health. The
whole server hears that someone has awakened, but not who.

Self-moderation, since nobody has to watch:
- **Friends don't count.** Players who spend an hour near each other without fighting, or who have ever shared a team,
  are friends until one of them really kills the other. Bonds are saved in `bonds.yml`.
- **Real exchanges only.** The meter only grows while you are also hitting that rival, and drains quickly out of combat.
- **No rematch farming.** A rival who pushed you hard in a fight that ended without an awakening stops counting for 7 days.
- **No fresh alts.** Rivals need 2 hours of playtime.

Everything is tunable under `awakenings.crisis`; set `enabled: false` to unlock first forms on full evidence.

### Bankai awakening (advanced forms)

1. **Mastery.** Once you meet the advanced evidence and rank requirements, land 40 first-form technique hits on valid rivals and win 5 fights while your first form is released. Only one hit per rival per second and one win per rival per 24 hours count. Friendship, minimum playtime, and rematch rules still apply.
2. **Subjugation.** With your form sealed, hold your faction starter and sneak without moving for 30 seconds. You must be alone within 24 blocks and out of PvP for 15 seconds. In the inner world, beat your spirit within five minutes by dodging telegraphed attacks from your future advanced kit. A loss (including disconnecting) returns you with two hearts, keeps your gear, and starts a 24-hour trial cooldown. A win locks in the kit and makes the advanced form dormant.
3. **Crisis.** While you still meet the rank requirement, face a valid rival who has a form released. Unreleased opponents don't build your meter. A first-form rival grants 10 outclassed points; an advanced-form rival grants 25. If a released rival lands an otherwise lethal blow with your meter full (200 by default), you awaken instead. Your form releases immediately and the whole server hears who awakened.

`/bleach awakenings` shows the current stage. Set `awakenings.bankai.enabled: false` to restore advanced unlocks on evidence and rank alone. Existing advanced forms are unaffected. Operators with `bleachsmp.admin` can use `/bleach bankai <online player> stage <NONE|MASTERING|MASTERED|SUBJUGATED>`, `/bleach bankai <online player> trial`, or `/bleach bankai <online player> reset` to test progression without grinding.

### Signature moves

Every form has its own signature move, used with **Q** (drop) on the starter item while released. No two forms share one:
48 in all. Advanced forms use an empowered version: longer, wider and harder-hitting. Cooldown: 20s (14s in an advanced
form), set by `signature-cooldown-seconds`. Signature hits get the same faction and element effects as techniques.

**Soul Reaper**

| Form | Signature | What it does |
| --- | --- | --- |
| Akagetsu | Blood Moon | Mark an enemy; your hits on it heal you, and the mark bursts when it fades. |
| Kagerō | Heat Haze Double | Leave a decoy of yourself and vanish; the decoy explodes in darkness when struck. |
| Ryūsei | Meteor Lance | Charge, then fire a piercing lance that hits harder the farther it flies. |
| Hayate | Updraft | A wind column flings nearby enemies up and lifts you; your next plunging hit lands hard. |
| Iwagane | Iron Wall | Raise a wall that destroys incoming projectiles and shoves enemies back. |
| Mamorigami | Frozen Shrine | Encase yourself in ice, invulnerable and healing, then shatter it to freeze enemies. |
| Homuramaru | Inferno Blade | Your melee hits explode in fire for a few seconds. |
| Oboromaru | Thousand Petals | Steer a cloud of blade petals with your crosshair. |
| Amagumo | Rain Prison | A raincloud traps an area, slowing enemies and striking them with water bolts. |
| Kumokiri | Thunder Step | Three lightning blinks; press Q again to step, cutting everything you pass. |
| Iwaoni | Toxic Spire | Plant a spire that pulses poison and drags enemies toward it. |
| Kusarigami | Bell of Binding | A bell silences nearby enemies' techniques and signatures. |

**Hollow**

| Form | Signature | What it does |
| --- | --- | --- |
| Lobo Carmesí | Feral Hunt | Pay health to frenzy: speed, and every melee hit heals and stacks strength. |
| Espejismo | Mirage Swap | Swap places with an enemy and blind it. |
| Halcón Negro | Black Talon Cero | Root yourself and fire a huge piercing cero. |
| Viento Hueco | Sonido Storm | Flash around a target, striking it from every side. |
| Coloso | Magma Fist | A line of magma eruptions that launches and burns. |
| Caparazón | Shell Reflect | A shell that reflects projectiles and punishes melee attackers. |
| Tiburón | Shark Dive | Dive into the ground and burst up under a target. |
| Sombra Pálida | Fading Shade | Vanish from enemy sight; your first hit out of it lands hard. |
| Escorpión | Stinger | Shoot a stinger; the venom detonates, stronger for every hit you land meanwhile. |
| Águila Hueca | Eagle Dive | Soar and steer through the air, then crash down with lightning. |
| Osario | Bone Cage | Cage an enemy in bone; it cannot leave. |
| Araña Blanca | Web of Frost | Throw a frozen web that slows enemies and shields allies. |

**Quincy**

| Form | Signature | What it does |
| --- | --- | --- |
| The Assault | Blitz Volley | A rapid stream of reishi bolts that follows your aim. |
| The Illusion | Mirror Feint | The next hit on you misses, and you strike back from behind the attacker. |
| The Starshot | Sniper Star | Charge a 60-block shot; headshots deal extra damage. |
| The Flight | Hirenkyaku Glide | Fly on reishi platforms for a few seconds. |
| The Bulwark | Resonant Bastion | A dome that destroys projectiles and wears down enemies inside. |
| The Haven | Blossom Haven | A blossom field that heals allies and pushes enemies out. |
| The Rampage | Blood Debt | Store the damage you take, then release it in a blast that heals you. |
| The Mirror | Mirror Theft | Steal an enemy's buffs. |
| The Deluge | Tidal Wave | A wall of water that sweeps enemies away. |
| The Zenith | Sunburst Halo | Orbiting suns that explode on enemies who come close. |
| The Pillar | Pillar Vault | Ride a rising pillar, then topple it onto the enemies in front. |
| The Tether | Soul Tether | Chain an enemy: it cannot run, and shares the damage you take. |

**Fullbringer**

| Form | Signature | What it does |
| --- | --- | --- |
| Crimson Knuckle | Charge Punch | Charge a punch (Q again to throw it); more charge, more damage. |
| Mirror Coat | Mirror Coat | Melee attackers take back most of the damage they deal you. |
| Bullet Echo | Echo Mark | Your next three hits echo a second later. |
| Sky Runner | Sky Steps | Jump again and again in mid-air. |
| Iron Maker | Iron Construct | Build an iron construct that fights for you. |
| Heart Lock | Heart Link | Link to an ally: you share damage and both regenerate. |
| Rewind Blade | Rewind | Mark this moment; Q again (or wait) to return to it, with your health from then. |
| Shuffle Deck | Shuffle | Every enemy around you, and you, swap places at random. |
| Echo Rain | Echo Turret | A floating turret that fires at nearby enemies. |
| Meteor Boots | Meteor Kick | Dash-kick an enemy into the air and stomp it back down. |
| Forge Ground | Molten Floor | Melt the ground: enemies burn, allies get speed and fire resistance. |
| Lifeline | Last Line | The next lethal hit on you or a nearby ally is stopped. |

### Elements

Every form has an element that layers its own particles, sounds and a light on-hit effect over everything the form
does: its techniques, impacts, aura, ultimate and release. Two forms with the same technique look and sound different.
A form's Shikai and Bankai share an element.

Elements also change how every technique plays:

| Element | Look | Mechanic |
| --- | --- | --- |
| Fire | flames, lava sparks | burns; +20% on burning targets; leaves fire along wave, dash and beam paths |
| Ice | snowflakes, frost rings | roots the first enemy hit (once per target every 4s); leaves frost trails |
| Lightning | sparks, jagged arcs | arcs to 2 nearby enemies (3 in advanced forms) for 40% damage, stunning them |
| Wind | gusts, swirling cloud | blasts enemies far away, at 85% damage |
| Shadow | ink, black smoke | waves and beams pass through walls; +30% from behind; darkness on players |
| Water | splashes, bubbles | drags enemies toward you |
| Blood | crimson spores, red streaks | lifesteal: 15% of damage (25% advanced) |
| Petal / Pale Petal | cherry or pale-oak leaves | a petal swarm keeps cutting for 3 more hits (5 advanced) |
| Earth | rock debris, dust plumes | launches enemies with erupting rock |
| Light | light rods, glow | marks enemies; the next hit on a mark deals +30% and consumes it |
| Poison | green spores, slime | poison II; already-poisoned enemies start to wither; toxic trails |
| Sound | notes, ringing rings | the hit rings out, dealing 30% to everything around the target |

**Advanced forms turn the element up.** Their transformation ends in a bigger element-specific eruption (falling lightning,
a petal cyclone, a pillar of light, a water wall…), their aura is denser, and their hits leave a two-second patch of the
element on the ground that keeps applying its on-hit effect to enemies standing in it.

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

### Advanced-form releases

Advanced forms (Soul Reaper **Bankai**, Hollow **Segunda Etapa**, Quincy **Vollständig**, and **Complete Fullbring**) are 40-second releases built the same way as first forms, with more power:

- **A two-second transformation.** A bolt of light (dark for Hollows) strikes the player, then a ten-block pillar of reiatsu rises. A nine-block shockwave and explosion follow. Nearby enemies are thrown back, and enemy players are briefly covered in Darkness. The call-out reaches players within 64 blocks.
- **A lasting look.** Quincy grow flapping wings of light, Hollows grow bone horns over a ring of souls, Fullbringers are circled by four armour plates, and Soul Reapers give off black-and-colour reiatsu like flame.
- **Stronger kits.** Techniques deal 40% more damage on shorter cooldowns (5 seconds, 8 for sneak), and the faction passive goes up one level. The advanced form rolls its own variant and secondary technique separately from the first form, so a player's Bankai does not simply mirror their Shikai.
- **An ultimate on F, once per release**, set by the advanced form's playstyle (see the table above). Sneak + F seals the form early. The action bar shows whether the ultimate is ready.

| Playstyle | Soul Reaper Bankai | Hollow Segunda Etapa | Quincy Vollständig | Complete Fullbring |
| --- | --- | --- | --- | --- |
| Vanguard | Akagetsu Tenrō / Homuramaru Gōen | Lobo Carmesí Rey / Tiburón Abisal | Sturmengel / Zornflügel | Crimson Knuckle Overdrive / Rewind Blade Zero |
| Mirage | Kagerō Mugenkyō / Oboromaru Yumeutsutsu | Espejismo Eterno / Sombra Infinita | Spiegelengel / Trugbild | Mirror Coat Infinite / Shuffle Deck Joker |
| Marksman | Ryūsei Gingaō / Amagumo Raiu | Halcón Negro Abismal / Escorpión Rey | Sternengel / Sintflut | Bullet Echo Arsenal / Echo Rain Storm |
| Wayfarer | Hayate Tenkū / Kumokiri Shippū | Tormenta Hueca / Águila Tempestad | Himmelsflügel / Zenitflug | Sky Runner Ascension / Meteor Boots Impact |
| Artificer | Iwagane Daichiō / Iwaoni Senzan | Coloso Titán / Osario Imperial | Bollwerkengel / Säulenthron | Iron Maker Foundry / Forge Ground Colossus |
| Keeper | Mamorigami Kekkaiden / Kusarigami Shinbaku | Caparazón Sagrado / Araña Reina | Heiligenschein / Bandengel | Heart Lock Sanctuary / Lifeline Eternal |

| Ultimate | Soul Reaper | Hollow | Quincy | Fullbringer |
| --- | --- | --- | --- | --- |
| Vanguard | Heaven-Rending Moon | Desgarro Final | Heiliges Gericht | Final Knuckle |
| Mirage | Thousand Mirage Execution | Ejecución Fantasma | Phantomhinrichtung | Grand Shuffle |
| Marksman | Galaxy Fall | Lluvia del Abismo | Sternenflut | Full Clip Storm |
| Wayfarer | Divine Tempest | Huracán Hueco | Himmelssturm | Stratosphere Drop |
| Artificer | World Breaker | Fin del Mundo | Weltbrecher | Foundry Quake |
| Keeper | Sanctum of Light | Santuario Vacío | Lichtdom | Lifeline Sanctuary |

Tune the advanced duration, technique cooldowns, and power under `awakenings.advanced-release` in `config.yml`.

The sealed starter abilities also have their own effects: Crescent Cut draws a pale crescent through the target, Cero Rush flashes red and leaves a soul-fire trail, Heilig Pfeil shows a reishi bow and a light trail on the arrow, and Bringer Guard surrounds the player with orbiting gold guard plates.

Use `/bleach awakenings` to see your total progress, rank requirements, and unlocked powers. Your next power and its trait stay hidden until you unlock it. Hold your faction starter item and press **F** (swap hands) to release your highest unlocked form. `/bleach release first` and `/bleach release advanced` let you use a specific form instead. Both forms share a release cooldown that starts when the form seals (40 seconds for first forms, 95 for advanced forms by default). While no form is released, right-click uses the original starter ability on its own cooldown. Set the advanced rank requirement and cooldowns in the `awakenings` section of `config.yml`.

## First abilities

All four abilities use the tagged starter item in the main hand, unlock at Rank 4 by default, and report remaining cooldown when used too early. Existing tagged starter items work; newly created items also show the control in their lore.

| Faction | Ability | Default effect | Cooldown |
| --- | --- | --- | --- |
| Soul Reaper | Crescent Cut | Deals 6 damage to the aimed-at living target within 4 blocks and briefly slows it | 20s |
| Hollow | Cero Rush | A forward lunge | 15s |
| Quincy | Heilig Pfeil | Fires a fast, unpickable spirit arrow without consuming ammunition | 12s |
| Fullbringer | Bringer Guard | Grants Resistance I for 5 seconds | 25s |

Ability cooldowns are saved with your player data and continue counting down while you are offline or the server is stopped. Lower rank numbers are stronger, so an `unlock-rank` of 4 allows Ranks 4, 3, 2, and 1. Ability settings are under `abilities.<FACTION>` in `config.yml`; restart the server after changing them.
