# Handoff: three-step Bankai awakening

Implement a three-step unlock for the **advanced form** of every faction: Bankai (Soul Reaper), Segunda Etapa
(Hollow), Vollständig (Quincy) and Complete Fullbring (Fullbringer). The code calls these "advanced forms"; this doc
says "Bankai" for all four.

The three steps, in order:

1. **Mastery.** The player proves their Shikai in real PvP.
2. **Subjugation.** They meditate, get pulled into their inner world, and beat their own spirit in a mirror fight.
3. **Crisis.** The Bankai stays dormant until a real rival nearly kills them in a big fight. The killing blow becomes
   the awakening. The cutscene is bigger than Shikai's, and the whole server sees the player's name.

The SMP has no staff, so the plugin must moderate itself. Every rule below that says "rival" means a player the
existing friendship system (`CrisisService`) does not mark as a friend.

---

## Branch and file ownership (read first)

- Branch from `awakening-releases` into a new branch, e.g. `bankai-awakening`. Push it and say when it's ready; it will
  be reviewed.
- **Other work is happening in parallel** in `ReleaseService.java`, `Element.java`, and a new signature-move system (Q
  key while released). To avoid merge conflicts:
  - Put new logic in **new files**, for example:
    - `awakening/BankaiService.java`: mastery tracking, stage machine, meditation.
    - `awakening/InnerWorld.java`: the world, arenas, entering and leaving.
    - `awakening/SpiritFight.java`: the spirit boss and its AI.
    - `awakening/BankaiCrisis.java`: optional, if you'd rather not grow `CrisisService`.
  - Edits to existing files should be **small hooks only**: `AwakeningService`, `CrisisService` and
    `BleachSmpPlugin`.
  - **Do not edit technique bodies in `ReleaseService` or anything in `Element`.**
- A hook already exists for you: `AwakeningService.techniqueHit(Player caster, LivingEntity target, boolean advanced)`
  is called every time a release technique damages something. It is empty right now; fill it in, or make it delegate.

---

## The codebase in five minutes

- Paper 1.21.11, Java 21, Maven (`mvn package`). Adventure text API. Everything is in `src/main/java/dev/bleachsmp`.
- **`BleachSmpPlugin`** wires everything up:
  - `getFaction(uuid)` and `getRank(uuid)`. Ranks run 5 to 1, and a **lower number is stronger**.
  - Listeners are registered in `onEnable`. `onDisable` calls `awakenings.releases().sealAll()` and
    `awakenings.crisis().shutdown()`.
- **`awakening/AwakeningService`** is the awakening state. All of it lives in the player's PersistentDataContainer:
  - **Unlocked forms:** `firstKey` and `advancedKey` store the unlocked playstyle's name. `firstTraitKey` and
    `advancedTraitKey` store the secondary playstyle.
  - **Evidence:** `evidence(player)` returns the habit-evidence array.
    `Playstyle.ready(evidence, advanced, rank, requiredRank)` checks whether there's enough. The advanced form needs
    80 evidence and rank ≤ `awakenings.advanced-unlock-rank`.
  - **`checkUnlock(player)`** currently unlocks the advanced form as soon as the evidence and rank are ready.
    **That is what you replace** (see the stage machine below). The first form already goes dormant there instead of
    unlocking, so follow that pattern.
  - **`release(player, Boolean advanced)`** handles the F key:
    - Nothing released: release a form.
    - First form out: seal it.
    - Advanced form out: F fires the ultimate, and sneak + F seals it.
  - **Crisis helpers:**
    - `isDormant(player)` means the first form is ready and waiting for a crisis.
    - `awakenFirst(player)` unlocks the first form.
    - `releaseAwakened(player)` forces an immediate first-form release.
  - You'll need advanced equivalents of these three.
- **`awakening/CrisisService`** is the Shikai crisis. Reuse as much of it as you can:
  - `friends(a, b)`: shared team, or ≥ `friend-minutes` of peaceful time near each other. Reset by a real kill.
    Stored in `bonds.yml`.
  - `validOpponent(victim, attacker)`: not a friend, has the minimum playtime, and is not on rematch cooldown.
  - `Fight`: the per-player hidden meter, with gains in `onPlayerHit`. `drainFights()` handles cool-off and the
    7-day rematch cooldown.
  - The trigger: lethal damage while the meter is ≥ the goal. The handler cancels the damage and calls `awaken(...)`.
  - `awaken(...)`: cutscene, invulnerability window (the `awakening` set), unlock, then `burst(...)` releases.
- **`awakening/ReleaseService`** is the timed release state:
  - `release(player, faction, style, trait, advanced)`, `seal(player, announce)`, `isActive`, `isAdvanced`.
  - `form(uuid, faction, style, advanced)` returns the form name and colours.
  - `Element` returns the form's element. `Element.eruption(location, advanced)` is a ready-made finale effect.
- **`fx/Vfx`** has particle helpers: `ring`, `burst`, `line`, `arc`, `helix`, `shockwave` and `trail`.
- **Particle rule:** only use particles whose `getDataType()` is `Void`, plus `DUST` (DustOptions) and `BLOCK`
  (BlockData). Others need data and will throw.
- **`hueco/HuecoMundoService`** already creates a custom world with a `WorldCreator` and a custom
  `ChunkGenerator`. Copy that pattern for the inner world.
- **Controls already in use while holding the faction starter item:**

  | Input | Action |
  | --- | --- |
  | Right-click | main technique |
  | Sneak + right-click | secondary technique |
  | F | release / ultimate |
  | Sneak + F | seal |
  | Q (drop) | signature move (being added now) |

  Meditation must use none of these. It is **sneak while standing still**, holding the starter item, with no click.

---

## Stage machine

Store a stage in the player's PDC, e.g. key `bankai_stage` as a string:

| Stage | Meaning | How you leave it |
| --- | --- | --- |
| `NONE` | Default. First form not unlocked yet, or advanced evidence and rank not ready | evidence + rank ready → `MASTERING` |
| `MASTERING` | Evidence and rank are ready; the Shikai must now prove itself | mastery hits and wins reached → `MASTERED` |
| `MASTERED` | May meditate to enter the inner world | beat the spirit → `SUBJUGATED` |
| `SUBJUGATED` | Bankai is dormant and waits for a crisis | crisis trigger → advanced form unlocked, stage removed |

- **Lock the advanced playstyle at subjugation.** Write `bankai_pending_style` and `bankai_pending_trait` from
  `Playstyle.dominant` and `Playstyle.secondary` when the spirit is beaten. The crisis then unlocks exactly that
  style, even if evidence keeps shifting. The spirit fight should use this same pending style's kit (see step 2).
- **Existing players** who already have `advancedKey` set are untouched.
- **Advancing a stage** sends one short message, italic and in faction colour. It must never reveal numbers beyond
  what is listed below.
- `/bleach awakenings` (in `showProgress`) shows one line for the current stage, for example:
  - "Your Shikai must still prove itself: 12/40 technique hits on rivals, 2/5 victories while released."
  - "Your spirit is waiting. Sneak, still and alone, with your starter in hand."
  - "Your Bankai sleeps. Only a desperate fight can wake it."
- **Config switch:** `awakenings.bankai.enabled: false` must restore the old behaviour (unlock on evidence + rank).

---

## Step 1: Mastery

Track two counters in the player's PDC. They only count while the stage is `MASTERING`.

- **Technique hits** (`bankai_mastery_hits`): in `techniqueHit(caster, target, advanced)`, count a hit when **all** of
  these are true:
  - `advanced == false`
  - the target is a `Player`
  - `CrisisService.validOpponent(caster, target)` is true (make it package-private)
  - at most one counted hit per target per second
- **Victories** (`bankai_mastery_wins`): on `PlayerDeathEvent`, count a win for the killer when **all** of these are
  true:
  - the killer has a first form released at the moment of the kill (`releases().isActive(killer)` and
    `!releases().isAdvanced(killer)`)
  - the victim is a valid opponent of the killer
  - the same victim counts **at most once per 24h** for that killer (store per-pair timestamps, like `rematch` in
    `bonds.yml`)
- **Goals:** `awakenings.bankai.mastery-hits` (default 40) and `awakenings.bankai.mastery-wins` (default 5). Reaching
  both moves the stage to `MASTERED`.
  - Message: *"Your Shikai has nothing left to teach you. Your spirit is waiting."*
  - Play a quiet `BLOCK_BELL_RESONATE`.

---

## Step 2: Subjugation (inner world)

### Meditating

The player meditates when **all** of these hold:

- the stage is `MASTERED`
- they are holding their own faction starter item (`AwakeningService.holdsStarter`)
- they are sneaking and their position doesn't move more than 0.15 blocks (head turning is fine)
- no other player is within 24 blocks
- they haven't dealt or taken player damage in the last 15s
- no release is active
- they are in Survival or Adventure
- they are not already in the inner world
- the 24h trial cooldown has passed (`bankai_trial_ready_at`)

Run a 30-second meditation (`awakenings.bankai.meditation-seconds`):

- The action bar shows "Meditating… 17s".
- Slow, inward-spiralling dust in the form's colour builds up, using `Element.aura`/`burst` lightly.
- From 20s, a soft `ENTITY_WARDEN_HEARTBEAT` plays every 2s.
- Moving, un-sneaking, switching item, or taking damage cancels it: "Your focus breaks."
- At 30s: the screen goes dark (Darkness for 2s), then teleport to the arena.

### The inner world

- **World:** a dedicated world, `awakenings.bankai.inner-world-name` (default `bleachsmp_inner`). Use an empty
  generator, a `ChunkGenerator` that generates nothing.
  - No mob spawns, fixed time, no weather, `keepInventory` true.
  - Copy the setup from `HuecoMundoService.initialize()`.
- **Arenas:** give each trial its own slot far from the others (e.g. x = slot × 2000, z = 0) so players never see each
  other. Build the arena with real blocks when a trial starts (it's a dedicated world, so that's fine). Clear it or
  reuse it afterwards. Each arena is a platform of radius 22 with a wall or edge so you can't fall off, themed by
  faction:

  | Faction | Arena theme |
  | --- | --- |
  | Soul Reaper | A sideways city: a floor of glass and smooth-stone "skyscraper windows" with a few tall pillars lying across, blue sky (day). |
  | Hollow | White sand under a black sky (night); a few bone-block dead trees; a pale moon (no daylight cycle). |
  | Quincy | A frozen cathedral: packed ice and quartz, glass columns, light-blue stained-glass "windows" ring. |
  | Fullbringer | A broken room: oak and stone floor in pieces, a floating bed, bookshelves and a door frame standing alone, all floating in the void. |

- **Entering:**
  - Save the return location to a file, like `garganta-returns.yml` in Hueco Mundo (`inner-returns.yml`).
  - Restore full health, then teleport.
  - Title: the form's name, in grey, as a whisper.
- **Rules inside:**
  - No block breaking or placing, no item drops or pickups, no ender pearls or chorus fruit.
  - The player keeps their gear and may release their Shikai and use its techniques and signature.
- **Leaving:**
  - Always return to the saved location.
  - **Quitting mid-trial counts as a loss.** On the next join, return them before anything else.
  - On plugin disable, return everyone online and save returns for everyone else.
  - Any teleport out of the inner world that the plugin didn't cause also counts as a loss and clears the trial.

### The spirit

- **Entity:** use a `Mannequin` (player-like entity, 1.21.9+) if the Paper API exposes it cleanly. Otherwise use a
  `Zombie` or `WitherSkeleton` with no AI-driven targeting changes, with:
  - leather armour dyed in the form colour, and the player's starter item in its hand
  - `customName` set to the form's Bankai name, e.g. "Akagetsu Tenrō", in the form's colour
  - `setPersistent(false)`
- **Health:** `awakenings.bankai.spirit-health` (default 120). Melee damage about 6.
- **AI:** keep vanilla pathing toward the player. On top of it, run a repeating task that casts the player's own kit
  every 4–6s, alternating main and secondary. **Telegraph every cast** with 15 ticks of particles on the ground or along
  the path, then resolve. Write simplified spirit versions of each `ReleaseService.Mechanic` in `SpiritFight`; don't call
  the player-only technique methods. The kit is:

  ```java
  ReleaseService.primary(uuid, pendingStyle, true)
  ReleaseService.secondary(uuid, pendingStyle, pendingTrait, true)
  ```

  using the form's `Element` for visuals. It's a mirror match: the player fights the moves they'll get.
- **Phases:**
  - **50% HP:** it speaks (see voices), gets Speed I, and casts every 3–4s.
  - **25% HP:** it does one `Element.eruption(location, true)` with a knock-back, then fights to the end.
- **Voices:** two lines per phase per faction, shown as subtitles. Examples:
  - **Soul Reaper**, start: "You learned my name. Now learn what I am."; at 50%: "Is that all your resolve is worth?"
  - **Hollow:** "A king rides the horse. Which of us is the horse?"
  - **Quincy:** "Every Quincy before you stands behind me."
  - **Fullbringer:** "This was always yours. Take it, if you can."
- **Win:**
  - Death is cancelled, then a 3-second ending: the spirit kneels (it stops moving), then dissolves into
    `Element.eruption`.
  - Message: *"<form name> acknowledges you."*
  - Stage becomes `SUBJUGATED`, and the pending style and trait are stored.
  - Return the player.
- **Loss:**
  - Lethal damage to the player in the inner world is **cancelled**: no death, no item loss.
  - Their health is set to 4 and they are returned.
  - Message: *"You are not ready."*
  - Set `bankai_trial_ready_at` to now + 24h (`awakenings.bankai.trial-cooldown-hours`).
- **Time limit:** 5 minutes (`awakenings.bankai.trial-minutes`), then it counts as a loss.

---

## Step 3: Bankai crisis

Extend `CrisisService` (or add `BankaiCrisis`) for players whose stage is `SUBJUGATED` and whose rank still meets the
requirement.

- **Same meter mechanics** as Shikai, with these differences:
  - Goal is `awakenings.crisis.bankai-meter-goal` (default 200), in **one fight**: the meter drains just as fast.
  - **Only attackers who have a form released count.** Ignore gains from attackers where `releases().isActive(attacker)` is
    false, including the low-health gain.
  - "Outclassed" (+25) means the attacker has an **advanced** form out. An attacker with only a first form gives +10 instead.
  - All other gains, friend rules, playtime and rematch cooldowns stay the same.
- **Trigger:** lethal blow from a valid rival with the meter ≥ the goal. Cancel the damage and start the Bankai awakening.
- **Cutscene (about 4 seconds):**
  1. Add the player to the invulnerable `awakening` set.
  2. If they have a first form out, `seal` it silently.
  3. **Darkness** for 3s on the player. For every player within 96 blocks, `setPlayerTime(18000, false)` for 6s, then
     `resetPlayerTime()`.
  4. **The spirit's voice**, two lines, using the same voice as the trial, e.g. "You finally fight like you mean it." /
     "Then say it. Say my name with everything you have."
  5. A **pillar of light 40 blocks tall** at the player, in the form's colour, for the whole scene. Players up to 128
     blocks away can see it. Use dust and `END_ROD` in a column.
  6. On the last beat:
     - Unlock `advancedKey` from the pending style and trait, and clear the stage and pending keys.
     - Heal to 60%.
     - Call `releases().release(player, faction, style, trait, true)`. Its own advanced entrance and element eruption
       play here.
     - Do not apply the release cooldown before this release. Set it as a normal release would afterwards.
- **Broadcast to the whole server, with the name** (unlike Shikai):
  `⚔ <player> has achieved <Bankai|Segunda Etapa|Vollständig|Complete Fullbring>: <form name>.` in the form's colour.
  Play `ENTITY_ENDER_DRAGON_GROWL` at volume 0.3 to everyone.
- If the player is in the inner world, the crisis can't trigger there (no rivals exist).

---

## Config to add (under `awakenings`)

```yaml
  bankai:
    enabled: true
    mastery-hits: 40
    mastery-wins: 5
    meditation-seconds: 30
    inner-world-name: bleachsmp_inner
    spirit-health: 120
    trial-minutes: 5
    trial-cooldown-hours: 24
  crisis:
    # (existing keys stay)
    bankai-meter-goal: 200
```

Update `README.md`: add a "Bankai awakening" section next to "Crisis awakening".

---

## Admin and testing

Add an op-only subcommand, `bleachsmp.admin`:

```
/bleach bankai <player> stage <NONE|MASTERING|MASTERED|SUBJUGATED>
/bleach bankai <player> trial
/bleach bankai <player> reset
```

`trial` starts the inner-world trial immediately. Use these to test without grinding.

Test checklist, using two accounts. Nothing has been tested on a server yet, so please actually run these:

1. **Mastery:**
   - Technique hits from a friend (shared team) don't count. Hits from a rival do. Kills while released count once
     per rival per day.
2. **Meditation:**
   - It cancels on moving, on being hit, and with another player within 24 blocks.
   - It completes after 30s.
3. **Trial:**
   - The arena is built per faction, and two simultaneous trials don't overlap.
   - The spirit uses the player's advanced kit with telegraphs.
   - **Winning** stores `SUBJUGATED` and the pending style.
   - **Losing** returns you with 2 hearts, no item loss, and the 24h cooldown.
   - **Quitting mid-trial** returns you on the next join and counts as a loss.
   - **`/stop` mid-trial** returns players.
4. **Crisis:**
   - The meter doesn't fill from attackers without a release.
   - A full meter plus a lethal hit plays the cutscene, unlocks the form, releases it immediately, and broadcasts with
     the name.
   - A meter that isn't full means you just die.
5. **Fallback:**
   - `awakenings.bankai.enabled: false` restores the old unlock.
   - Existing advanced-form owners are unaffected.
6. **Build:** `mvn package` passes with no new warnings.
