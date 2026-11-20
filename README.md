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

Hueco Mundo's Hollows have animated models: the Gillian is an 11-block-tall cloaked giant that spawns in open terrain, the Adjuchas is a six-limbed dune hunter, and the Vasto Lorde has a split mask, a horned crown, and blade-like arms. Aim at the creature's body rather than its decorative extremities: combat still uses the underlying mob's hitbox.

### Hollow models and the resource pack

The plugin builds its own resource pack at startup and offers it to every player on join. With it, each Hollow is a set of textured 3D models, one per limb, animated the same way as before. They have painted bone, cloth and mask textures, glowing eyes, smooth curved horns, and detail that block displays can't carry: the Gillian's tattered hem, collar spikes and teeth, the Adjuchas's ribs, vertebrae and claws, and the Vasto Lorde's mane and fur cuffs. Players who decline the pack, or whose download fails, see the original block-built Hollows instead. Each player only ever sees one of the two.

The pack is generated from the same geometry as the block version (`HollowModel`, painted by `HollowPackAssets`), so changing a Hollow's shape updates both. By default the server hosts the pack itself over HTTP on port 8163. Open that TCP port on your host or firewall. If you can't, upload `plugins/BleachSMP/BleachSMP-resources.zip` somewhere public (it is rewritten on every start) and set its link as `resource-pack.url`. Re-upload it whenever you update the plugin, or the hash check will fail and players fall back to block Hollows. The `resource-pack` section of `config.yml` also sets the public host, whether the pack is required, and its prompt.

To see the models in the real client, run `.github/scripts/hollow-screenshot.py` under a virtual display (as the screenshot workflow does). It photographs each Hollow from several angles into `target/hueco-capture/hollows/`. Pass `--no-pack` to photograph the block fallback.

### Ability effects

The same resource pack carries the ability effects. For players with it, the shared effect helpers draw glowing, animated 3D effects instead of particle dots. Every move that uses them gets the new look:

- **Slashes** are diagonal crescents of light with a white-hot edge that leave a fading trail as they travel. Hollow waves are three claw marks.
- **Beams and ceros** charge as a swelling orb at the muzzle, then fire a thick beam with a white core that thins out behind the shot. They end in a flash and a shockwave.
- **Shockwaves** are rings of light racing across the ground. **Bursts** are star flashes, and big ones swell an orb of energy.
- **Lines and tethers** become beams of light. Projectiles and dashes leave glowing streaks.

Every kit (all 66 zanpakutō, resurrecciones, Schriften and Fullbrings) has its own look, built from about 30 effect shapes in the pack. These include ice crystals, stone spikes, lightning, gusts, smoke, breaking waves, drops, petals, feathers, blades, Heilig Pfeil arrows, spears, chains, bone fangs, claws, beast heads, armoured fists, a giant, Hollow masks, tentacles, and glyphs such as hearts, notes, eyes, stars, butterflies, invaders, cards, clocks, the Quincy pentacle, Orihime's triangle, hex barriers and pages.

- **First release:** each kit releases its own way.
  - Hyōrinmaru's ice dragon swims a circle around its owner.
  - Senbonzakura's blade dissolves into petals.
  - Tenken's armoured fist slams down.
  - Hollows' masks form and shatter.
  - Quincy stand on a glowing pentacle under a halo.
  - Fullbringers' armour plates fly in and lock on.
- **Advanced entrance:** a bolt strikes from the sky, then the kit's own entrance plays. Some examples:
  - Tensa Zangetsu's black-and-crimson column is cut in an X.
  - Zanka no Tachi's ring of fire collapses into a white-hot blade.
  - Daiguren Hyōrinmaru rises with ice wings and three ice flowers.
  - Senbonzakura Kageyoshi raises two rows of giant blades that scatter into a petal storm.
  - Sōō Zabimaru's bone serpent coils up its master.
  - Kokujō Tengen Myō'ō's armoured giant rises out of the earth.
  - Murciélago's black wings unfurl over black rain and a green lance.
  - Santa Teresa's six scythe arms fan out.
  - Hoheitsrecht brings golden wings, a sword and a shield.
  - Jackpot Knuckle's slot reels spin.
  - Shiten Kōshun raises a dome inside a triangle of light.
  - Time Tells No Lies hangs a giant clock with chains.
- **Elements:** each element has its own shapes on every hit, release and aura:
  - fire burns in tongues of flame;
  - ice bursts into crystals;
  - lightning crackles;
  - wind whirls;
  - shadow billows into smoke;
  - water splashes and rolls in waves;
  - blood sprays;
  - petals and leaves flutter;
  - earth throws up spikes and rocks;
  - light leaves stars and a pentacle;
  - poison clouds;
  - sound rings with notes.
- **Techniques:** waves, beams, blinks, dashes, slams, wards, combos, storms, pillars and chains take the kit's look. Some examples:
  - Getsuga Tenshō is a black crescent with a burning edge.
  - Hyōryū's ice dragon flies down the beam.
  - Heilig Pfeil fire as arrows, and Licht Regen rains them.
  - Ola Azul rolls as a wave.
  - Koten Zanshun throws a spinning triangle.
  - Danku is a hex wall, and Santen Kesshun is a triangle shield.
  - Pantera's combo rakes with claws.
  - Golondrina's storm rains feathers.
- **Signature (Q) and advanced (F) abilities:** every one has its own choreography. Some examples:
  - Getsuga Jūjishō flies as a cross.
  - Jōkaku Enjō is a ring of fire walls.
  - Hyōten Hyakkasō opens the sky over a field of ice flowers.
  - Senkei walls everyone in with swords.
  - Korose's sixty-block thrust leaves a sliver that dissolves its target three seconds later.
  - Hikotsu Taihō fires from the serpent's jaws.
  - Myō'ō's giant stands beside its master for the rest of the release.
  - Jakuhō Raikōben's missile flies to its mark and goes off.
  - Lanza del Relámpago leaves a towering green explosion.
  - Gremmy imagines a meteor.
  - Sprenger pins five Seele Schneider in a pentacle.
  - The Jail closes bars of light.
  - La Muerte fires a skull.
  - Time Limit spins a giant clock.
- **Advanced aura:** these effects stay with the form while it is out:
  - Quincy wear wings of light, Hollows grow horns, and Fullbringers are circled by armour plates.
  - Some kits add their own: ice wings, black wings, six arms, petals, cards, pages, invaders, eyes or Rikka's six flowers.
  - Every released form also wears a faint, turning sheath of reiatsu that grows with its owner.

Effects are tinted to each form's colour, glow at full brightness and fade out smoothly. Players without the pack see the original particles. Pack users see vanilla particles thinned to a quarter, so the models aren't buried. Effects are short-lived display entities that are never saved, and at most 1200 exist at once. Lingering effects end as soon as their release does.

Admins can preview any kit without unlocking it:

- `/bleach vfx release <first|advanced> [faction] [playstyle] [kit]` releases a form. `kit` is a name (spaces as underscores) or its number in the playstyle's pool.
- `/bleach vfx use <main|second|signature|ultimate>` fires an ability.
- `/bleach vfx seal` seals the form.
- `/bleach vfx demo` plays every base effect in a row.

`.github/scripts/vfx-screenshot.py` uses these commands to photograph each faction in the real client. `.github/scripts/kit-screenshot.py faction:playstyle:index ...` photographs individual kits.

### Garganta tears

Every 45–90 minutes, if at least two players are in the overworld, a black rift starts tearing open in the sky 50–110 blocks from one of them. It only opens over open, dry ground. The server is told roughly where, and there is a 60-second countdown, so anyone can head there.

- **Waves:** two waves of Adjuchas, then a **Menos Grande**. Waves scale with the number of players nearby.
  - The Menos Grande has 300 health, plus 80 for each extra player.
  - It fires a telegraphed Cero across the area: it charges, shows its aim line, then fires a beam that stops at blocks.
- **Sealing:** clear the last wave to seal the tear.
  - A shared loot pile drops where the rift stood, so expect a fight over it. It can hold diamonds, emeralds, golden apples, ender pearls, XP bottles, echo shards and ghast tears, which are the Garganta recipe ingredients, and rarely netherite scrap, totems or a notched apple.
  - Everyone who dealt at least 20 damage also gets a personal share and XP.
  - The Menos Grande counts as a boss for first-clear rank credit.
- **Faction bonuses:**
  - **Soul Reapers:** closing Gargantas is their duty, so their personal share and XP are doubled.
  - **Hollows:** they devour every tear Hollow they kill, healing 2 hearts and gaining absorption.
  - **Quincy:** each kill with a bow or crossbow adds a spoil to their share, up to 3.
  - **Fullbringers:** they draw on the rift's residue for double XP from kills and a large XP bonus at the seal.
- **No cheese:**
  - Tear Hollows take no damage from lava, falls, drowning or traps.
  - The Menos Grande can only be hurt by players.
  - Hollows dragged more than 40 blocks away are pulled back.
  - The tear closes on its own after 12 minutes, or after 2 minutes with no one nearby.
- **Safe for builds:** no blocks are ever changed.

Admins can open a tear near themselves with `/bleach boss tear`, which uses a 10-second countdown. Tune everything under `tears` in `config.yml`.

### Spiritual pressure

Every player has a reiatsu level. Rank gives 1–5: rank 5 is 1 and rank 1 is 5. A first-form release adds 2 and an advanced release adds 4, so a rank 1 player in Bankai is at 9. Pressure never slows, blinds or stuns players.

- **Sensing:** when someone 2 or more levels above you is within 40 blocks, you feel it. A line like "A heavy reiatsu presses down on you, to the northeast" appears with a low hum, and it is more urgent the bigger the gap. Each person is felt at most every 90 seconds, but a sudden spike such as a Bankai release is felt immediately. Allies and friends, invisible or vanished players, spectators and the inner world are ignored.
- **Suppression:** stay crouched for 3 seconds while unreleased to hide your reiatsu from sensing. This is useful for ambushes, at the cost of moving at sneaking speed.
- **The fight edge:** once you are in a fight with someone 3 or more levels above you, you deal 10% less damage to them, and your technique and signature cooldowns are 15% longer. After 10 seconds you adapt, the edge fades over 5 seconds, and from then on it is an even fight. You get one chat line when it lands and one when you have adapted. It is worked out per opponent, so fighting several strong players at once does not stack it.

Tune it under `pressure` in `config.yml`, or set `pressure.enabled: false` to turn it off.

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

Other future work includes additional boss encounters, death recovery, the Rank 0 event role, and custom weapon models in the resource pack. Configure rank thresholds, faction titles, ability unlocks and cooldowns, PvP cooldowns, boss contribution, rewards, and the weekly challenge rotation in `plugins/BleachSMP/config.yml`.

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

### The real roster

Every released form is a real one from the series: Soul Reapers get zanpakutō, Hollows get Arrancar resurrecciones,
Quincy get Sternritter Schrifts and Fullbringers get Fullbrings. There are 66 in all (19 zanpakutō, 18 resurrecciones,
18 Schrifts and all 11 Fullbrings the series has).

- **Your playstyle picks the pool, your UUID picks the form.** Each playstyle has two to four forms per faction. The
  pick is stable per player and spread evenly, so two players can share a form, but on a server of 25 most won't.
- **The Bankai is the same weapon.** A player's advanced form is always their first form's Bankai, Segunda Etapa,
  Vollständig or Complete Fullbring, chosen by their first form's playstyle. Players who unlocked an advanced form
  before this change also follow their first form.
- **Q is the signature ability** (20s cooldown, 14s and empowered in the advanced form). **F is the advanced form's
  defining ability**, once per release. Where the series never revealed a Bankai (Aizen, Matsumoto, Kira, Ukitake), the
  advanced ability says so and builds on what was shown.
- Many abilities change the rules instead of just dealing damage: Kyōka Suigetsu hypnotises everyone who saw the
  release, Suzumebachi kills in two stings on the same spot, Wabisuke doubles weight with every cut, Sakanade inverts
  senses, Irooni and Karamatsu Shinjū play by game rules, The Balance passes misfortune on, Book of the End rewrites
  memories so enemies can't hurt you, and Time Tells No Lies makes you pay for a broken contract. Hits that bend an
  enemy's attacks only bend attacks on you (or, for redirect effects, onto whoever stands next to them).

**Soul Reaper — zanpakutō (Shikai → Bankai)** — 19 forms

| Playstyle | Form (whose) | Right-click / Sneak | Q: signature | Advanced form | F: advanced ability |
| --- | --- | --- | --- | --- | --- |
| Vanguard | **Zangetsu** (Ichigo Kurosaki) | Getsuga Tenshō / Shunpo Cut | **Getsuga Jūjishō**: two crossing Getsuga Tenshō fired as one cross-shaped slash | Tensa Zangetsu | **Kuroi Getsuga**: a giant black Getsuga Tenshō, then Bankai speed: every melee hit throws a black getsuga through your target |
| Vanguard | **Ryūjin Jakka** (Genryūsai Yamamoto) | Taimatsu / Ennetsu Jigoku | **Jōkaku Enjō**: a ring of fire walls traps everyone near you; touching the wall burns | Zanka no Tachi | **Zanka no Tachi**: Higashi: one cut that erases everything in a line — then Nishi: a 15-million-degree aura that scorches everything near you |
| Vanguard | **Nozarashi** (Kenpachi Zaraki) | Nozarashi Cleave / Kendō Rampage | **Eyepatch Off**: tear off the eyepatch: pay health to frenzy; every melee hit heals and stacks strength | Nozarashi (Bankai) | **Demon Form**: become a red demon: unstoppable, every swing cleaves everything in front and hits harder the longer you fight |
| Mirage | **Kyōka Suigetsu** (Sōsuke Aizen) | Illusory Strike / Mirror Flower | **Kanzen Saimin**: complete hypnosis: everyone who saw you release loses sight of you and sees you standing still; your first hit on each lands hard | Kyōka Suigetsu: Kanzen Saimin | **Mugen Kyōka**: (Aizen never revealed a Bankai) every hypnotised enemy strikes their own side: their attacks land on whoever is nearest them, never you |
| Mirage | **Katen Kyōkotsu** (Shunsui Kyōraku) | Kageoni / Takaoni | **Irooni**: call a colour at an enemy: you both take the colour's damage multiplier from each other — black hits hardest | Katen Kyōkotsu: Karamatsu Shinjū | **Karamatsu Shinjū**: a tragic play in four acts on one enemy: shared wounds, the pool of shame, the binding thread, and the final beheading |
| Mirage | **Sakanade** (Shinji Hirako) | Inverted Haze / Reversed Strike | **Inverted World**: up is down, left is right: nearby enemies spin around and half their swings at you miss | Sakashima Yokoshima Happō Fusagari | **Happō Fusagari**: everyone around you loses friend from foe: their hits land on whoever stands near them |
| Marksman | **Hyōrinmaru** (Tōshirō Hitsugaya) | Hyōryū / Hyōrinmaru Chain | **Hyōten Hyakkasō**: open the sky over an area; every enemy the snow touches becomes an ice flower | Daiguren Hyōrinmaru | **Sennen Hyōrō**: ice wings carry you, and a ring of ice pillars closes on the area you aim at, crushing everything inside |
| Marksman | **Senbonzakura** (Byakuya Kuchiki) | Petal Torrent / Danku | **Senbonzakura**: steer a cloud of a thousand blade petals with your crosshair | Senbonzakura Kageyoshi | **Senkei**: rows of a thousand swords wall in everyone near you; blade petals cut everything trapped inside |
| Marksman | **Shinsō** (Gin Ichimaru) | Shinsō Thrust / Butō Renjin | **Full Extension**: charge, then extend the blade in a piercing thrust that hits harder the farther it reaches | Kamishini no Yari | **Korose**: a 60-block thrust leaves a sliver of blade inside the first enemy hit; three seconds later it dissolves them from within |
| Marksman | **Sode no Shirayuki** (Rukia Kuchiki) | Tsugi no Mai: Hakuren / San no Mai: Shirafune | **Some no Mai: Tsukishiro**: a circle opens under an enemy; everything inside freezes from the ground to the sky | Hakka no Togame | **Hakka no Togame**: drop to absolute zero: a white mist freezes enemies around you, and frozen enemies shatter when hit |
| Wayfarer | **Zabimaru** (Renji Abarai) | Zabimaru Lash / Snake Fang Rush | **Higa Zekkō**: the blade's segments scatter around an enemy and all fang in at once | Sōō Zabimaru | **Hikotsu Taihō**: the bone snake opens its jaws and fires a huge reiatsu blast |
| Wayfarer | **Haineko** (Rangiku Matsumoto) | Ash Veil / Shunpo | **Haineko**: the blade turns to ash that swirls around you, cutting; press Q again to fling it where you aim | Haineko (Bankai) | **Ash Storm**: (no Bankai revealed) a storm of blade ash follows you, cutting and blinding every enemy inside |
| Wayfarer | **Suzumebachi** (Suì-Fēng) | Shunpo Sting / Shunkō | **Nigeki Kessatsu**: your first sting leaves the Hōmonka butterfly; a second sting on the same mark is death in two steps | Jakuhō Raikōben | **Jakuhō Raikōben**: brace, then fire a massive missile at where you aim; the recoil throws you back |
| Artificer | **Tenken** (Sajin Komamura) | Tenken Fist / Earth Split | **Tenken**: a giant armoured arm appears overhead and slams down on where you aim | Kokujō Tengen Myō'ō | **Myō'ō**: a towering armoured giant rises behind you, cutting your enemies for the rest of the release |
| Artificer | **Wabisuke** (Izuru Kira) | Hook Blade / Downward Cut | **Wabisuke**: every hit doubles your target's weight until they're crushed to their knees with their head bowed for the final cut | Wabisuke (Bankai) | **Bowed Heads**: (no Bankai revealed) the weight of every past cut falls on everyone near you at once |
| Artificer | **Tachikaze** (Kensei Muguruma) | Wind Blade / Tachikaze Burst | **Tachikaze**: throw a wind blade that cuts and detonates in a blast of wind on whatever it touches | Tekken Tachikaze | **Tekken Tachikaze**: steel knuckle dusters: every melee hit explodes and blasts enemies away |
| Keeper | **Minazuki** (Retsu Unohana) | Healing Kidō / Minazuki's Embrace | **Minazuki**: the great ray swallows your allies' wounds: heals and cleanses everyone near you | Minazuki (Bankai) | **Bankai: Minazuki**: a pool of blood that rots enemies and heals you and your allies; your blade heals you with every cut |
| Keeper | **Sōgyo no Kotowari** (Jūshirō Ukitake) | Wave Shield / Returned Lightning | **Sōgyo no Kotowari**: absorb the damage of incoming attacks for a few seconds, then return it as lightning around you | Kamikake: Mimihagi | **Kamikake: Mimihagi**: (no Bankai revealed) Mimihagi stills everything: enemies near you lose their buffs and slow to a crawl; allies recover |
| Keeper | **Ashisogi Jizō** (Mayuri Kurotsuchi) | Ashisogi Hook / Poison Mist | **Ashisogi Jizō**: each cut paralyses a little more: legs, then arms, then the whole body | Konjiki Ashisogi Jizō | **Konjiki Ashisogi Jizō**: the golden giant breathes a lethal poison cloud ahead of you; you carry the antidote |

**Hollow — resurrección (Resurrección → Segunda Etapa)** — 18 forms

| Playstyle | Form (whose) | Right-click / Sneak | Q: signature | Advanced form | F: advanced ability |
| --- | --- | --- | --- | --- | --- |
| Vanguard | **Pantera** (Grimmjow Jaegerjaquez) | Pantera Claws / Garra de la Pantera | **Pantera Pounce**: flash around a target, clawing it from every side | Pantera — Desgarrón | **Desgarrón**: ten giant claws of reiatsu, five from each hand, rake everything in front of you |
| Vanguard | **Ira** (Yammy Llargo) | Ira Stomp / Cero | **Ira**: your rage makes you grow: bigger, tougher and stronger for a while | Ira — Furia | **Furia**: grow into a colossus; every blow shakes the ground around you |
| Vanguard | **Santa Teresa** (Nnoitra Gilga) | Scythe Flurry / Crescent Scythe | **Hierro Más Fuerte**: the strongest Hierro of the Espada: shrug off most damage for a few seconds | Santa Teresa — Six Arms | **Six Arms**: grow four more arms: every melee hit strikes three times |
| Mirage | **Brujería** (Zommari Rureaux) | Gemelos Sonido / Sonido Strike | **Amor**: an eye opens on an enemy: you control them — they can't hurt you and stagger toward your other enemies | Brujería — Amor | **Fifty Eyes**: fifty eyes open: every enemy near you falls under Amor |
| Mirage | **Glotonería** (Aaroniero Arruruerie) | Shapeshift / Tentacle Grab | **Devour**: bite an enemy: heal by what you tear off, and steal their buffs | Glotonería — 33,650 Hollows | **Banquet**: the 33,650 Hollows inside you lash out with tentacles at everyone near you, feeding you |
| Mirage | **Fornicarás** (Szayelaporro Granz) | Gas Veil / Cero | **Voodoo Doll**: make a doll of an enemy; press Q again to crush one of its organs | Fornicarás — Gabriel | **Organ Crush**: dolls of every enemy near you, crushed one after another |
| Marksman | **Murciélago** (Ulquiorra Cifer) | Cero Oscuras / Sonido | **Luz de la Luna**: throw a green lance of light that bursts on impact | Resurrección: Segunda Etapa | **Lanza del Relámpago**: charge a lance of lightning and hurl it; the explosion is enormous |
| Marksman | **Los Lobos** (Coyote Starrk) | Cero / Cero Rain | **Los Lobos**: a pack of spirit wolves runs at your enemies and explodes on them | Los Lobos — Lilynette | **Cero Metralleta**: a thousand ceros: a three-second machine-gun stream of ceros where you aim |
| Marksman | **Tiburón** (Tier Harribel) | Ola Azul / La Gota | **Cascada**: a massive waterfall crashes down on the area you aim at | Tiburón — Gran Caída | **Gran Caída**: a colossal blade of compressed water, then Hirviendo: scalding water around you |
| Wayfarer | **Giralda** (Dordoni Alessandro Del Socaccio) | Sonido Kick / Air Dive | **Torbellinos**: two tornado drills race forward from your legs, sweeping enemies up | Giralda — Torbellinos | **Cero Tornado**: a three-second tornado that drags enemies in and hurls them skyward |
| Wayfarer | **Golondrina** (Cirucci Sanderwicci) | Feather Rain / Wing Rush | **Golondrina**: take to the air on wings of blades and shower the ground below with razor feathers | Golondrina — Wings of Blades | **Hundred Feathers**: a storm of razor feathers over a wide area |
| Wayfarer | **Tigre Estoque** (Ggio Vega) | Pounce / Fang Flurry | **Sonido Tigre**: three sonido flashes; press Q again to step, cutting everything you pass | Tigre Estoque — Zarpazo | **Zarpazo**: strike up to five enemies in sequence from behind, then return |
| Artificer | **Arrogante** (Barragan Luisenbarn) | Gran Caída / Arrogante Grip | **Respira**: breathe a cloud of senescence ahead of you: everything it touches ages and rots | Arrogante — Respira | **Senescencia**: you become time itself: everything near you rots, and arrows decay before they reach you |
| Artificer | **Dragra** (Gantenbainne Mosqueda) | Dragra Fists / Dragon Stomp | **Dragon Rugir**: a dragon of reiatsu bursts from your fist and drills through everything ahead | Dragra — Garra de Dragón | **Garras de Dragón**: two volleys of three giant dragon-claw waves |
| Artificer | **Verdugo** (Tesla Lindocruz) | Verdugo Charge / Tusk Slam | **Gore**: charge forward, carry everyone you hit on your tusks, and slam them down | Verdugo — Stampede | **Stampede**: three expanding rings of erupting earth |
| Keeper | **Gamuza** (Nelliel Tu Odelschwanck) | Healing Saliva / Gamuza Charge | **Cero Doble**: swallow the damage of incoming attacks, then spit it back as a doubled cero | Gamuza — Lanzador Verde | **Lanzador Verde**: hurl a spinning green lance that drills through everything in a long line |
| Keeper | **Trepadora** (Luppi Antenor) | Tentacle Bind / Tentacle Slam | **Trepadora**: eight tentacles seize the nearest enemies, holding and crushing them | Trepadora — Eight Arms | **Spike Barrage**: every tentacle sprouts spikes and stabs everything near you over and over |
| Keeper | **Tres Bestias** (Apache, Mila Rose & Sung-Sun) | Pack Ward / Anaconda Bind | **Quimera Parca**: summon Ayon, the beast made from three arms, to fight for you | Ayon | **Tres Bestias**: Cierva gores, León smashes and Anaconda constricts the three nearest enemies |

**Quincy — Schrift (Schrift → Vollständig)** — 18 forms

| Playstyle | Form (whose) | Right-click / Sneak | Q: signature | Advanced form | F: advanced ability |
| --- | --- | --- | --- | --- | --- |
| Vanguard | **H — The Heat** (Bazz-B) | Burner Finger 1 / Burner Finger 2 | **Burner Finger 3**: three fingers: a wide cone of fire that blasts everything in front | The Heat: Vollständig | **Burner Finger 4**: all four fingers: a colossal blade of fire sweeps all the way around you |
| Vanguard | **O — The Overkill** (Driscoll Berci) | Heilig Bogen Fists / Heilig Pfeil Spread | **The Overkill**: every hit you land makes the next one stronger | The Overkill: Vollständig | **Kill Count**: every enemy that falls near you feeds you: full heal and more strength |
| Vanguard | **P — The Power** (Meninas McAllon) | Power Slam / Power Rush | **The Power**: one punch with impossible strength sends an enemy flying | The Power: Vollständig | **Ground Splitter**: smash the ground so hard everyone around you is launched |
| Mirage | **F — The Fear** (Äs Nödt) | Fear Thorns / Dread | **The Fear**: fear thorns pierce an enemy: blind, weak, slow and shaking | Tatarforas | **Tatarforas**: a cage of fear: enemies inside can't see, can't fight back and can't use their powers |
| Mirage | **V — The Visionary** (Gremmy Thoumeaux) | Imagined Bombs / Imagined Step | **Imagination**: imagine something into being: bombs, quicksand or blades, at random | The Visionary: Vollständig | **Meteor**: imagine a meteor: it falls on where you aim |
| Mirage | **L — The Love** (Pepe Waccabrada) | Love Shot / Heart Haze | **Love Kiss**: a heart that charms the enemy it hits: they can't hurt you and walk to you | The Love: Vollständig | **Love Rain**: hearts rain on everyone near you: every enemy is charmed |
| Marksman | **X — The X-Axis** (Lille Barro) | X-Axis Shot / Heilig Pfeil Rain | **The X-Axis**: a shot that pierces everything between the muzzle and the target: walls, shields, everyone | Jilliel | **Holy Rays**: your halo rains X-axis rays over a wide area around where you aim |
| Marksman | **A — The Antithesis** (Uryū Ishida) | Heilig Pfeil / Licht Regen | **The Antithesis**: reverse what has happened between you and an enemy: your wounds become theirs | Licht Regen | **Sprenger**: five Seele Schneider form a pentacle around where you aim, then it explodes |
| Marksman | **T — The Thunderbolt** (Candice Catnipp) | Thunder Shot / Lightning Kick | **Galvano Blast**: call a bolt of lightning down on an enemy | The Thunderbolt: Vollständig | **Galvano Javelin**: a giant javelin of lightning, followed by a storm of bolts |
| Wayfarer | **W — The Wind** (Nianzol Weizol) | Lichtsprung / Drift | **The Wind**: attacks pass through you like wind: the next three hits on you miss | The Wind: Vollständig | **Gale**: a storm wind blasts every enemy around you away and leaves you untouchable for a moment |
| Wayfarer | **S — The Superstar** (Mask De Masculine) | Star Dash / Hero Punches | **Star Cheer**: the crowd cheers: heal and power up | The Superstar: Vollständig | **Star Rocket Dynamite**: fly like a rocket through enemies and explode at the end |
| Wayfarer | **E — The Explode** (Bambietta Basterbine) | Reishi Bombs / Blast Jump | **The Explode**: anything you hit becomes a bomb that goes off a second later | The Explode: Vollständig | **Explode Barrage**: rise into the air and rain explosive reishi all around |
| Artificer | **I — The Iron** (Cang Du) | Iron Claws / Iron Guard | **Eisern**: skin of steel: take far less damage, ignore knockback, and hurt anyone who hits you | The Iron: Vollständig | **Steel Field**: three expanding rings of erupting iron |
| Artificer | **M — The Miracle** (Gerard Valkyrie) | Sword of Victory / Shield of the Miracle | **The Miracle**: every wound makes you bigger and stronger | Hoheitsrecht | **Hoheitsrecht**: become a giant; every blow sends out a shockwave |
| Artificer | **G — The Glutton** (Liltotto Lamperd) | Bite Flurry / Spit | **Devour**: a huge mouth bites an enemy: heal by what you eat and steal their buffs | The Glutton: Vollständig | **The Glutton**: a gigantic mouth swallows everything in front of you, pulling them in |
| Keeper | **J — The Jail** (Quilge Opie) | Jail Chains / Blut Vene | **The Jail**: lock an enemy inside a cage of light; it cannot leave | Biskiel | **Sklaverei**: drain the reishi out of everything around you: enemies wither and you heal |
| Keeper | **B — The Balance** (Jugram Haschwalth) | Blut Vene / Heilig Pfeil | **The Balance**: half of every misfortune that befalls you is passed to the nearest enemy | Avaron | **Freund Schild**: every wound dealt to you is shared out among the enemies around you instead |
| Keeper | **Z — The Zombie** (Giselle Gewelle) | Blood Spray / Zombie Grab | **The Zombie**: your blood splashes enemies: their attacks on you heal you, and creatures that die rise for you | The Zombie: Vollständig | **Zombie Army**: raise a horde of zombies to fight for you |

**Fullbringer — Fullbring (Awakened → Complete Fullbring)** — 11 forms

| Playstyle | Form (whose) | Right-click / Sneak | Q: signature | Advanced form | F: advanced ability |
| --- | --- | --- | --- | --- | --- |
| Vanguard | **Jackpot Knuckle** (Shishigawara Moe) | Slot Punches / Big Win | **Spin**: spin the reels and punch: a jackpot hits like a truck, a bust hits you instead | Jackpot Knuckle: Complete | **Triple Seven**: the machine is rigged: every punch is a jackpot for a while |
| Vanguard | **Brazo Derecha de Gigante** (Yasutora Sado) | Golpe / Giant's Guard | **El Directo**: a straight punch that throws a blast of reiatsu ahead | Brazo Derecha de Gigante: Full | **Gigante**: the giant's arm armours you: shrug off damage and hit back at anyone who strikes you |
| Mirage | **Book of the End** (Shūkurō Tsukishima) | Bookmark Cut / Turned Page | **Insert**: cut an enemy to insert yourself into their past: they remember you as a friend and won't harm you | Book of the End: Complete | **Rewrite**: insert yourself into the past of every enemy around you at once |
| Marksman | **Brazo Izquierda del Diablo** (Yasutora Sado) | Diablo Shot / Diablo Rush | **La Muerte**: a skull-shaped blast of reiatsu that explodes at the end of its path | La Muerte | **Muerte Barrage**: a storm of La Muerte blasts over a wide area |
| Marksman | **Cross of Scaffold** (Kūgo Ginjō) | Cross Slash / Fullbring Blast | **Steal**: the cross drinks from an enemy: steal their health and buffs | Cross of Scaffold: Complete | **Stolen Getsuga**: the power you stole from Ichigo: a giant black Getsuga Tenshō |
| Wayfarer | **Fullbring** (Ichigo Kurosaki) | Bringer Light / Fullbring Getsuga | **Bringer Light**: step on the air: jump again and again in mid-air | Complete Fullbring | **Bringer Light Rush**: flash through up to five enemies one after another, then return |
| Wayfarer | **Dirty Boots** (Jackie Tristan) | Boot Kicks / Mud Slide | **Dirty Boots**: the dirtier you get, the stronger you are: power up for every hit you've taken | Dirty Boots: Complete | **Filthy Stomp**: a stomp that sends everyone near you flying |
| Artificer | **Dollhouse** (Riruka Dokugamine) | Love Gun Leash / Love Gun | **Love Gun**: shoot an enemy with the Love Gun: they shrink into a doll and can't fight | Dollhouse: Complete | **Bring In**: pull everyone near you into the dollhouse at once |
| Artificer | **Invaders Must Die** (Yukio Hans Vorarlberna) | Pixel Walls / Invader Fire | **Invaders Must Die**: trap an area in a game box; pixel invaders shoot whoever is inside | Invaders Must Die: Final Stage | **Final Stage**: release the boss stage: invader drones dive-bomb every enemy near you |
| Keeper | **Shun Shun Rikka** (Orihime Inoue) | Santen Kesshun / Koten Zanshun | **Sōten Kisshun**: a dome that rejects what happened: heals and cleanses everyone inside | Shiten Kōshun | **Shiten Kōshun**: reject every attack: damage enemies deal to you or allies near you is sent back at them |
| Keeper | **Time Tells No Lies** (Giriko Kutsuzawa) | Clockwork Guard / Contract Chain | **Contract**: bet on an enemy: land three hits within ten seconds and it explodes; fail and you pay | Time Tells No Lies: Complete | **Time Limit**: a contract with time: overwhelming power for fifteen seconds, and weakness after |

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

Each real form names its own two techniques (Getsuga Tenshō, Cero Oscuras, Burner Finger 1…; see the roster above). Underneath, every technique is one of twelve mechanics:

| Mechanic | What it does |
| --- | --- |
| Wave | a cutting wave that tears through its path |
| Blink | strike from behind a target up to 12 blocks away, blinding it |
| Beam | a long-range blast that bursts on impact |
| Dash | cut every enemy you pass |
| Slam | hurl nearby enemies skyward |
| Ward | heal and shield teammates, push enemies back |
| Flurry | lunge at a target and land a five-hit combo |
| Scatter | blind enemies, turn invisible, and hop around as afterimages |
| Rain | call ten bolts down on the area you aim at |
| Skyfall | leap high, then crash down in a shockwave (no fall damage) |
| Pillars | a line of erupting pillars that launch enemies |
| Chains | bind enemies in sight, drag them in, and root them |

Pillars are temporary block displays, so they never change the world. `/bleach awakenings` shows each player's own form, call-out, and both techniques.

Press **F** again to seal the form early. It also seals on death, logout, or changing worlds. The first-form release cooldown starts once the form seals. Tune the duration, both technique cooldowns, and technique damage under `awakenings.release` in `config.yml`.

### Advanced-form releases

Advanced forms (Soul Reaper **Bankai**, Hollow **Segunda Etapa**, Quincy **Vollständig**, and **Complete Fullbring**) are 40-second releases built the same way as first forms, with more power:

- **A two-second transformation.** A bolt of light (dark for Hollows) strikes the player, then a ten-block pillar of reiatsu rises. A nine-block shockwave and explosion follow. Nearby enemies are thrown back, and enemy players are briefly covered in Darkness. The call-out reaches players within 64 blocks.
- **A lasting look.** Quincy grow flapping wings of light, Hollows grow bone horns over a ring of souls, Fullbringers are circled by four armour plates, and Soul Reapers give off black-and-colour reiatsu like flame.
- **Stronger kits.** Techniques deal 40% more damage on shorter cooldowns (5 seconds, 8 for sneak), and the faction passive goes up one level. The signature (Q) is empowered.
- **The form's real advanced ability on F, once per release** (see the roster). Sneak + F seals the form early. The action bar shows whether it is ready.

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
