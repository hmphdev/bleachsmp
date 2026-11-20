package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * First-form releases for every faction: Soul Reaper Shikai, Hollow Resurrección, Quincy Schrift and
 * Awakened Fullbring. A release is a timed state; while it is out, the starter item's right-click
 * ability becomes the released form's technique. The playstyle that unlocked the form picks the
 * technique's mechanic, and the faction picks its look, sound, passive and on-hit effect.
 */
public final class ReleaseService implements Listener {
    /** The technique mechanic each playstyle gets, shared across factions. */
    enum Mechanic {
        WAVE("Send a cutting wave tearing through everything in its path."),
        BLINK("Vanish and strike from behind a target up to 12 blocks away, blinding it."),
        BEAM("Fire a long-range blast that bursts on impact."),
        DASH("Dash forward, cutting every enemy you pass."),
        SLAM("Slam the ground and hurl nearby enemies skyward."),
        WARD("Heal and shield yourself and teammates while driving enemies back.");

        final String description;

        Mechanic(String description) {
            this.description = description;
        }

        static Mechanic of(Playstyle style) {
            return switch (style) {
                case VANGUARD -> WAVE;
                case MIRAGE -> BLINK;
                case MARKSMAN -> BEAM;
                case WAYFARER -> DASH;
                case ARTIFICER -> SLAM;
                case KEEPER -> WARD;
            };
        }
    }

    /** Faction-wide look, sound, passive and on-hit effect for released forms. */
    enum Theme {
        SOUL_REAPER(Particle.ELECTRIC_SPARK, PotionEffectType.SPEED, 0, "Hits slow enemies."),
        HOLLOW(Particle.SOUL_FIRE_FLAME, PotionEffectType.RESISTANCE, 0, "Hits wither enemies and heal you."),
        QUINCY(Particle.END_ROD, PotionEffectType.JUMP_BOOST, 1, "Hits mark enemies with Glowing."),
        FULLBRINGER(Particle.ENCHANT, PotionEffectType.HASTE, 0, "Hits weaken enemies.");

        final Particle accent;
        final PotionEffectType passive;
        final int passiveAmplifier;
        final String onHit;

        Theme(Particle accent, PotionEffectType passive, int passiveAmplifier, String onHit) {
            this.accent = accent;
            this.passive = passive;
            this.passiveAmplifier = passiveAmplifier;
            this.onHit = onHit;
        }

        static Theme of(Faction faction) {
            return valueOf(faction.name());
        }
    }

    /** One released form: what the player calls out, its technique, and its colours. */
    record Form(String name, String callout, String technique, Color color, TextColor text, BossBar.Color bar) {
        Particle.DustOptions dust(float size) {
            return Vfx.dust(color, size);
        }
    }

    private static Form form(String name, String callout, String technique, int r, int g, int b, TextColor text,
            BossBar.Color bar) {
        return new Form(name, callout, technique, Color.fromRGB(r, g, b), text, bar);
    }

    static Form form(Faction faction, Playstyle style) {
        return switch (faction) {
            case SOUL_REAPER -> switch (style) {
                case VANGUARD -> form("Akagetsu", "Howl, Akagetsu!", "Crimson Fang", 220, 30, 45,
                        NamedTextColor.RED, BossBar.Color.RED);
                case MIRAGE -> form("Kagerō", "Deceive, Kagerō!", "Haze Step", 150, 90, 230,
                        NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE);
                case MARKSMAN -> form("Ryūsei", "Pierce, Ryūsei!", "Falling Star", 255, 205, 60,
                        NamedTextColor.GOLD, BossBar.Color.YELLOW);
                case WAYFARER -> form("Hayate", "Soar, Hayate!", "Gale Passage", 120, 235, 200,
                        NamedTextColor.AQUA, BossBar.Color.GREEN);
                case ARTIFICER -> form("Iwagane", "Crush, Iwagane!", "Mountain Breaker", 165, 120, 75,
                        NamedTextColor.GOLD, BossBar.Color.WHITE);
                case KEEPER -> form("Mamorigami", "Shelter, Mamorigami!", "Shrine Ward", 110, 190, 255,
                        NamedTextColor.BLUE, BossBar.Color.BLUE);
            };
            case HOLLOW -> switch (style) {
                case VANGUARD -> form("Lobo Carmesí", "Desgarra, Lobo Carmesí!", "Garra Roja", 170, 15, 25,
                        NamedTextColor.DARK_RED, BossBar.Color.RED);
                case MIRAGE -> form("Espejismo", "Engaña, Espejismo!", "Sonido Fantasma", 205, 205, 215,
                        NamedTextColor.GRAY, BossBar.Color.WHITE);
                case MARKSMAN -> form("Halcón Negro", "Dispara, Halcón Negro!", "Cero Oscuro", 120, 20, 150,
                        NamedTextColor.DARK_PURPLE, BossBar.Color.PURPLE);
                case WAYFARER -> form("Viento Hueco", "Corre, Viento Hueco!", "Embestida", 220, 205, 160,
                        NamedTextColor.YELLOW, BossBar.Color.YELLOW);
                case ARTIFICER -> form("Coloso", "Aplasta, Coloso!", "Terremoto", 125, 90, 55,
                        NamedTextColor.GOLD, BossBar.Color.WHITE);
                case KEEPER -> form("Caparazón", "Protege, Caparazón!", "Coraza Viva", 60, 160, 115,
                        NamedTextColor.DARK_GREEN, BossBar.Color.GREEN);
            };
            case QUINCY -> switch (style) {
                case VANGUARD -> form("The Assault", "Schrift A — The Assault!", "Heiliger Sturm", 110, 170, 255,
                        NamedTextColor.BLUE, BossBar.Color.BLUE);
                case MIRAGE -> form("The Illusion", "Schrift I — The Illusion!", "Schattenschritt", 170, 140, 255,
                        NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE);
                case MARKSMAN -> form("The Starshot", "Schrift S — The Starshot!", "Sternregen", 200, 235, 255,
                        NamedTextColor.AQUA, BossBar.Color.BLUE);
                case WAYFARER -> form("The Flight", "Schrift F — The Flight!", "Lichtsprung", 150, 255, 235,
                        NamedTextColor.AQUA, BossBar.Color.GREEN);
                case ARTIFICER -> form("The Bulwark", "Schrift B — The Bulwark!", "Erdbrecher", 190, 200, 225,
                        NamedTextColor.GRAY, BossBar.Color.WHITE);
                case KEEPER -> form("The Haven", "Schrift H — The Haven!", "Lichtschild", 255, 250, 190,
                        NamedTextColor.YELLOW, BossBar.Color.YELLOW);
            };
            case FULLBRINGER -> switch (style) {
                case VANGUARD -> form("Crimson Knuckle", "Fullbring: Crimson Knuckle!", "Knuckle Breaker", 255, 115, 40,
                        NamedTextColor.GOLD, BossBar.Color.RED);
                case MIRAGE -> form("Mirror Coat", "Fullbring: Mirror Coat!", "Reflection Swap", 230, 150, 255,
                        NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE);
                case MARKSMAN -> form("Bullet Echo", "Fullbring: Bullet Echo!", "Echo Shot", 255, 220, 80,
                        NamedTextColor.YELLOW, BossBar.Color.YELLOW);
                case WAYFARER -> form("Sky Runner", "Fullbring: Sky Runner!", "Ground Skip", 130, 215, 255,
                        NamedTextColor.AQUA, BossBar.Color.BLUE);
                case ARTIFICER -> form("Iron Maker", "Fullbring: Iron Maker!", "Forge Quake", 175, 175, 190,
                        NamedTextColor.GRAY, BossBar.Color.WHITE);
                case KEEPER -> form("Heart Lock", "Fullbring: Heart Lock!", "Locket Ward", 255, 140, 185,
                        NamedTextColor.LIGHT_PURPLE, BossBar.Color.PINK);
            };
        };
    }

    static String description(Faction faction, Playstyle style) {
        Form form = form(faction, style);
        return "\"" + form.callout() + "\" A timed release; right-click uses " + form.technique() + ": "
                + Mechanic.of(style).description + " " + Theme.of(faction).onHit;
    }

    private static final class Release {
        final Form form;
        final Theme theme;
        final Mechanic mechanic;
        final long startedAt;
        final long endsAt;
        final BossBar bar;
        BukkitTask task;
        long techniqueReadyAt;

        Release(Form form, Theme theme, Mechanic mechanic, long startedAt, long endsAt, BossBar bar) {
            this.form = form;
            this.theme = theme;
            this.mechanic = mechanic;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.bar = bar;
        }
    }

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    private final Map<UUID, Release> active = new HashMap<>();

    ReleaseService(BleachSmpPlugin plugin, AwakeningService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    int durationSeconds() {
        return Math.clamp(plugin.getConfig().getInt("awakenings.release.duration-seconds", 30), 5, 300);
    }

    private double damage(double base) {
        return base * Math.clamp(plugin.getConfig().getDouble("awakenings.release.damage-multiplier", 1.0), 0.0, 5.0);
    }

    // ---------------------------------------------------------------- release

    /** Releases the first form: the call-out, the faction's release burst, and the timed released state. */
    void release(Player player, Faction faction, Playstyle style) {
        seal(player, false);
        Form form = form(faction, style);
        Theme theme = Theme.of(faction);
        long now = System.currentTimeMillis();
        int seconds = durationSeconds();
        BossBar bar = BossBar.bossBar(Component.text(AwakeningService.formName(faction, false) + " — " + form.name(),
                form.text(), TextDecoration.BOLD), 1.0f, form.bar(), BossBar.Overlay.NOTCHED_10);
        Release release = new Release(form, theme, Mechanic.of(style), now, now + seconds * 1000L, bar);
        active.put(player.getUniqueId(), release);

        Location loc = player.getLocation();
        Component callout = Component.text(player.getName() + ": ", NamedTextColor.GRAY)
                .append(Component.text("\"" + form.callout() + "\"", form.text(), TextDecoration.ITALIC));
        for (Player nearby : loc.getNearbyPlayers(32)) {
            nearby.sendMessage(callout);
        }
        player.showTitle(Title.title(Component.text(form.name(), form.text(), TextDecoration.BOLD),
                Component.text(AwakeningService.formName(faction, false), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(500))));
        player.showBossBar(bar);
        player.addPotionEffect(new PotionEffect(theme.passive, seconds * 20, theme.passiveAmplifier, false, false, true));

        releaseSounds(loc, theme);
        Vfx.shockwave(plugin, loc, form.color(), 5.0, 8);
        // Spiritual pressure: push nearby enemies away without damaging them.
        for (LivingEntity entity : loc.getNearbyLivingEntities(5)) {
            if (service.canStrike(player, entity)) {
                entity.setVelocity(outward(loc, entity).multiply(0.9).setY(0.35));
            }
        }
        releaseAnimation(player, form, theme);

        release.task = new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                long time = System.currentTimeMillis();
                if (!player.isOnline() || player.isDead() || !AwakeningService.canPlay(player)
                        || time >= release.endsAt) {
                    seal(player, true);
                    return;
                }
                release.bar.progress((float) Math.clamp(
                        (release.endsAt - time) / (double) (release.endsAt - release.startedAt), 0.0, 1.0));
                aura(player, release, tick);
                tick += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private static void releaseSounds(Location loc, Theme theme) {
        switch (theme) {
            case SOUL_REAPER -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
                Vfx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.4f);
                Vfx.sound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
            }
            case HOLLOW -> {
                Vfx.sound(loc, Sound.ENTITY_WARDEN_ROAR, 0.7f, 1.3f);
                Vfx.sound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.7f);
                Vfx.sound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.8f);
            }
            case QUINCY -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
                Vfx.sound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
                Vfx.sound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 1.6f);
            }
            case FULLBRINGER -> {
                Vfx.sound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.2f);
                Vfx.sound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
                Vfx.sound(loc, Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.1f);
            }
        }
    }

    /** The first second after a release: each faction gets its own rising effect. */
    private void releaseAnimation(Player player, Form form, Theme theme) {
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation().add(0, 1, 0), 1);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 20) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                var world = base.getWorld();
                switch (theme) {
                    case SOUL_REAPER -> {
                        // A three-strand helix of reiatsu.
                        for (int strand = 0; strand < 3; strand++) {
                            double a = tick * 0.6 + strand * (Math.PI * 2 / 3);
                            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 1.1, tick * 0.15,
                                    Math.sin(a) * 1.1), 2, 0.02, 0.02, 0.02, 0, form.dust(1.6f));
                        }
                    }
                    case HOLLOW -> {
                        // A column of soul fire and smoke, with the colour spiralling inward.
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, tick * 0.12, 0), 6,
                                0.45, 0.2, 0.45, 0.02);
                        world.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.4, 0), 2, 0.6, 0.2, 0.6, 0.01);
                        double r = 1.6 - tick * 0.06;
                        for (int s = 0; s < 2; s++) {
                            double a = tick * 0.8 + s * Math.PI;
                            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * r, 1.0, Math.sin(a) * r),
                                    3, 0.03, 0.3, 0.03, 0, form.dust(1.7f));
                        }
                        if (tick % 5 == 0) {
                            world.spawnParticle(Particle.SCULK_SOUL, base.clone().add(0, 1.2, 0), 4, 0.4, 0.4, 0.4, 0.05);
                        }
                    }
                    case QUINCY -> {
                        // A halo that descends while pillars of light rise around the archer.
                        Vfx.ring(base.clone().add(0, 2.6 - tick * 0.05, 0), 0.55, 14, form.color(), 1.0f);
                        for (int p = 0; p < 6; p++) {
                            double a = p * (Math.PI / 3);
                            world.spawnParticle(Particle.END_ROD, base.clone().add(Math.cos(a) * 1.8, tick * 0.13,
                                    Math.sin(a) * 1.8), 1, 0, 0, 0, 0);
                        }
                    }
                    case FULLBRINGER -> {
                        // Enchant glyphs and colour converge on the hand that holds the bringer item.
                        Location hand = base.clone().add(0, 1.2, 0)
                                .add(base.getDirection().getCrossProduct(new Vector(0, 1, 0)).multiply(-0.4));
                        double r = 2.2 - tick * 0.1;
                        for (int s = 0; s < 4; s++) {
                            double a = tick * 0.5 + s * (Math.PI / 2);
                            world.spawnParticle(Particle.DUST, hand.clone().add(Math.cos(a) * r, Math.sin(tick * 0.3) * 0.4,
                                    Math.sin(a) * r), 1, 0, 0, 0, 0, form.dust(1.4f));
                        }
                        world.spawnParticle(Particle.ENCHANT, hand, 10, 0.8, 0.8, 0.8, 0.6);
                        if (tick == 20) {
                            world.spawnParticle(Particle.TOTEM_OF_UNDYING, hand, 40, 0.3, 0.4, 0.3, 0.4);
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void aura(Player player, Release release, int tick) {
        Location base = player.getLocation();
        var world = base.getWorld();
        for (int i = 0; i < 2; i++) {
            double a = tick * 0.35 + i * Math.PI;
            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.75,
                    1.0 + Math.sin(tick * 0.2 + i) * 0.6, Math.sin(a) * 0.75), 1, 0, 0, 0, 0, release.form.dust(0.9f));
        }
        if (tick % 10 == 0) {
            switch (release.theme) {
                case SOUL_REAPER -> world.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 1, 0), 3,
                        0.35, 0.5, 0.35, 0.02);
                case HOLLOW -> {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, 0.2, 0), 2, 0.3, 0.05, 0.3, 0.01);
                    world.spawnParticle(Particle.SMOKE, base.clone().add(0, 1.2, 0), 2, 0.3, 0.4, 0.3, 0.01);
                }
                case QUINCY -> Vfx.ring(base.clone().add(0, 2.3, 0), 0.4, 10, release.form.color(), 0.8f);
                case FULLBRINGER -> world.spawnParticle(Particle.ENCHANT, base.clone().add(0, 1.2, 0), 6,
                        0.4, 0.5, 0.4, 0.4);
            }
        }
    }

    /** Ends a release. Returns whether one was active. */
    boolean seal(Player player, boolean announce) {
        Release release = active.remove(player.getUniqueId());
        if (release == null) {
            return false;
        }
        if (release.task != null) {
            release.task.cancel();
        }
        player.hideBossBar(release.bar);
        if (announce && player.isOnline()) {
            Location loc = player.getLocation();
            Vfx.sound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
            Vfx.burst(loc.clone().add(0, 1, 0), release.form.color(), 20, 0.5, 1.0f);
            player.sendMessage(Component.text(release.form.name() + " fades back into its sealed form.",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    public void sealAll() {
        for (UUID id : Set.copyOf(active.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                seal(player, false);
            } else {
                Release release = active.remove(id);
                if (release.task != null) {
                    release.task.cancel();
                }
            }
        }
    }

    // ---------------------------------------------------------------- techniques

    /**
     * Uses the released form's technique. Called by the starter item's right-click ability while a release
     * is active, in place of the sealed ability.
     */
    public void useTechnique(Player player) {
        Release release = active.get(player.getUniqueId());
        if (release == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (release.techniqueReadyAt > now) {
            player.sendActionBar(Component.text(release.form.technique() + " ready in "
                    + ((release.techniqueReadyAt - now + 999) / 1000) + "s", NamedTextColor.YELLOW));
            return;
        }
        boolean used = switch (release.mechanic) {
            case WAVE -> wave(player, release);
            case BLINK -> blink(player, release);
            case BEAM -> beam(player, release);
            case DASH -> dash(player, release);
            case SLAM -> slam(player, release);
            case WARD -> ward(player, release);
        };
        if (used) {
            int cooldown = Math.clamp(plugin.getConfig().getInt("awakenings.release.technique-cooldown-seconds", 6),
                    0, 600);
            release.techniqueReadyAt = now + cooldown * 1000L;
            player.sendActionBar(Component.text(release.form.technique() + "!", release.form.text(), TextDecoration.BOLD));
        }
    }

    /** Damages a target and applies the faction's on-hit effect with a small impact flash. */
    private void hit(Player caster, LivingEntity target, double amount, Release release) {
        target.damage(damage(amount), caster);
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        at.getWorld().spawnParticle(release.theme.accent, at, 8, 0.3, 0.3, 0.3, 0.05);
        Vfx.burst(at, release.form.color(), 8, 0.3, 1.2f);
        switch (release.theme) {
            case SOUL_REAPER -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
            case HOLLOW -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                var max = caster.getAttribute(Attribute.MAX_HEALTH);
                caster.setHealth(Math.min(max == null ? 20.0 : max.getValue(), caster.getHealth() + 1.0));
            }
            case QUINCY -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            case FULLBRINGER -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
        }
    }

    private boolean wave(Player player, Release release) {
        Location origin = player.getEyeLocation().subtract(0, 0.35, 0);
        Vector forward = origin.getDirection().normalize();
        Set<UUID> hit = new HashSet<>();
        switch (release.theme) {
            case SOUL_REAPER -> {
                Vfx.sound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
                Vfx.sound(origin, Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.8f);
            }
            case HOLLOW -> {
                Vfx.sound(origin, Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 0.8f);
                Vfx.sound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.4f);
            }
            case QUINCY -> {
                Vfx.sound(origin, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.6f);
                Vfx.sound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
            }
            case FULLBRINGER -> {
                Vfx.sound(origin, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.8f);
                Vfx.sound(origin, Sound.ITEM_MACE_SMASH_AIR, 1.0f, 0.9f);
            }
        }
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                Location center = origin.clone().add(forward.clone().multiply(1.5 + step * 1.5));
                if (step >= 11 || center.getBlock().getType().isSolid()) {
                    Vfx.burst(center, release.form.color(), 25, 0.6, 1.8f);
                    Vfx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.7f);
                    cancel();
                    return;
                }
                if (release.theme == Theme.HOLLOW) {
                    // Three claw marks instead of one crescent.
                    for (double y = -0.55; y <= 0.56; y += 0.55) {
                        Vfx.arc(center, forward, 1.5, release.form.color(), 1.4f, y);
                    }
                } else {
                    Vfx.arc(center, forward, 2.0, release.form.color(), 1.8f, 0);
                }
                center.getWorld().spawnParticle(release.theme.accent, center, 4, 0.5, 0.3, 0.5, 0.02);
                if (release.theme == Theme.SOUL_REAPER) {
                    center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1);
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(2.1)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        hit(player, entity, 8.0, release);
                        entity.setVelocity(forward.clone().multiply(0.6).setY(0.3));
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean blink(Player player, Release release) {
        var result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                12, 0.6, entity -> entity instanceof LivingEntity && !entity.equals(player));
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)
                || !service.canStrike(player, target)) {
            player.sendActionBar(Component.text(release.form.technique() + " needs an enemy within 12 blocks.",
                    NamedTextColor.GRAY));
            return false;
        }
        Vector facing = target.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) {
            facing = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        }
        if (facing.lengthSquared() < 0.01) {
            facing = new Vector(0, 0, 1);
        }
        Location behind = target.getLocation().subtract(facing.normalize().multiply(1.4));
        if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) {
            player.sendActionBar(Component.text("No room behind your target.", NamedTextColor.GRAY));
            return false;
        }
        behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
        Location from = player.getLocation().add(0, 1, 0);
        // An afterimage left where the caster stood, and a streak to where they reappear.
        Vfx.burst(from, release.form.color(), 40, 0.35, 1.5f);
        from.getWorld().spawnParticle(Particle.CLOUD, from, 12, 0.3, 0.6, 0.3, 0.02);
        from.getWorld().spawnParticle(release.theme.accent, from, 10, 0.3, 0.6, 0.3, 0.05);
        Vfx.line(from, behind.clone().add(0, 1, 0), release.form.color(), 0.8f, 0.6);
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
            case QUINCY -> Vfx.sound(from, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.6f);
            case FULLBRINGER -> Vfx.sound(from, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
            case SOUL_REAPER -> Vfx.sound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        }
        player.teleport(behind);
        hit(player, target, 7.0, release);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2, 0.3, 0.2, 0.3, 0);
        Vfx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        return true;
    }

    private boolean beam(Player player, Release release) {
        Location origin = player.getEyeLocation();
        Vector forward = origin.getDirection().normalize();
        boolean cero = release.theme == Theme.HOLLOW;
        switch (release.theme) {
            case HOLLOW -> {
                Vfx.sound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
                Vfx.sound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.2f);
            }
            case QUINCY -> {
                Vfx.sound(origin, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.8f);
                Vfx.sound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 2.0f);
            }
            case FULLBRINGER -> {
                Vfx.sound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f);
                Vfx.sound(origin, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.6f);
            }
            case SOUL_REAPER -> Vfx.sound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
        }
        // Charge glow at the caster's hand.
        Vfx.burst(origin.clone().add(forward), release.form.color(), cero ? 20 : 8, 0.15, 1.6f);
        int speed = cero ? 4 : 3;
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                for (int sub = 0; sub < speed; sub++) {
                    Location point = origin.clone().add(forward.clone().multiply(1 + step * speed + sub));
                    point.getWorld().spawnParticle(release.theme.accent, point, 1, 0, 0, 0, 0);
                    point.getWorld().spawnParticle(Particle.DUST, point, cero ? 6 : 2, cero ? 0.22 : 0.08,
                            cero ? 0.22 : 0.08, cero ? 0.22 : 0.08, 0, release.form.dust(cero ? 2.0f : 1.4f));
                    LivingEntity struck = null;
                    for (LivingEntity entity : point.getNearbyLivingEntities(cero ? 1.5 : 1.2)) {
                        if (!entity.equals(player)) {
                            struck = entity;
                            break;
                        }
                    }
                    if (struck != null || point.getBlock().getType().isSolid() || step >= 10) {
                        burst(point, struck);
                        cancel();
                        return;
                    }
                }
                step++;
            }

            private void burst(Location point, LivingEntity struck) {
                point.getWorld().spawnParticle(release.theme.accent, point, 40, 0.2, 0.2, 0.2, 0.25);
                Vfx.burst(point, release.form.color(), 30, 0.9, 1.8f);
                point.getWorld().spawnParticle(Particle.EXPLOSION, point, 1);
                Vfx.sound(point, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
                if (struck != null && service.canStrike(player, struck)) {
                    hit(player, struck, 9.0, release);
                }
                for (LivingEntity entity : point.getNearbyLivingEntities(2.5)) {
                    if (!entity.equals(struck) && service.canStrike(player, entity)) {
                        hit(player, entity, 4.0, release);
                    }
                }
            }
        }.runTaskTimer(plugin, cero ? 4L : 0L, 1L);
        return true;
    }

    private boolean dash(Player player, Release release) {
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.01) {
            forward = new Vector(0, 0, 1);
        }
        player.setVelocity(forward.normalize().multiply(2.1).setY(0.15));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, true));
        Location start = player.getLocation();
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(start, Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.5f);
            case QUINCY -> Vfx.sound(start, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
            case FULLBRINGER -> Vfx.sound(start, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.8f);
            case SOUL_REAPER -> { }
        }
        Vfx.sound(start, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.0f);
        Vfx.ring(start.clone().add(0, 0.1, 0), 1.2, 16, release.form.color(), 1.3f);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick++ >= 9) {
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 1, 0);
                at.getWorld().spawnParticle(Particle.CLOUD, at, 4, 0.3, 0.4, 0.3, 0.02);
                at.getWorld().spawnParticle(release.theme.accent, at, 2, 0.3, 0.4, 0.3, 0.02);
                Vfx.burst(at, release.form.color(), 6, 0.5, 1.3f);
                for (LivingEntity entity : at.getNearbyLivingEntities(1.9)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        hit(player, entity, 6.0, release);
                        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1, 0), 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private boolean slam(Player player, Release release) {
        Location center = player.getLocation();
        Material ground = center.clone().subtract(0, 1, 0).getBlock().getType();
        BlockData debris = (ground.isSolid() ? ground : Material.STONE).createBlockData();
        Vfx.sound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.7f);
        Vfx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(center, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 0.6f);
            case QUINCY -> Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.6f);
            case FULLBRINGER -> Vfx.sound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
            case SOUL_REAPER -> Vfx.sound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
        }
        for (LivingEntity entity : center.getNearbyLivingEntities(5)) {
            if (service.canStrike(player, entity)) {
                hit(player, entity, 6.0, release);
                entity.setVelocity(outward(center, entity).multiply(0.4).setY(0.95));
            }
        }
        new BukkitRunnable() {
            int ring;

            @Override
            public void run() {
                if (ring > 6) {
                    cancel();
                    return;
                }
                double radius = 0.6 + ring * 0.75;
                for (int i = 0; i < 24; i++) {
                    double a = i * (Math.PI * 2 / 24);
                    Location point = center.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius);
                    point.getWorld().spawnParticle(Particle.BLOCK, point, 4, 0.1, 0.2, 0.1, 0.1, debris);
                    if (i % 3 == 0) {
                        point.getWorld().spawnParticle(Particle.DUST, point.add(0, 0.4, 0), 1, 0, 0, 0, 0,
                                release.form.dust(1.5f));
                        point.getWorld().spawnParticle(release.theme.accent, point, 1, 0, 0.2, 0, 0.02);
                    }
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean ward(Player player, Release release) {
        Location center = player.getLocation();
        for (Player ally : center.getNearbyPlayers(6)) {
            if (ally.equals(player) || (AwakeningService.canPlay(ally) && AwakeningPowers.allies(player, ally))) {
                var maxHealth = ally.getAttribute(Attribute.MAX_HEALTH);
                double max = maxHealth == null ? 20.0 : maxHealth.getValue();
                ally.setHealth(Math.min(max, ally.getHealth() + 4.0));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 0));
                ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 2, 0.3, 0.1, 0.3, 0);
                Vfx.ring(ally.getLocation().add(0, 0.2, 0), 0.7, 12, release.form.color(), 1.0f);
            }
        }
        for (LivingEntity entity : center.getNearbyLivingEntities(4)) {
            if (service.canStrike(player, entity)) {
                hit(player, entity, 4.0, release);
                entity.setVelocity(outward(center, entity).multiply(1.1).setY(0.3));
            }
        }
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(center, Sound.ENTITY_TURTLE_EGG_CRACK, 1.0f, 0.5f);
            case QUINCY -> Vfx.sound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
            case FULLBRINGER -> Vfx.sound(center, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.7f);
            case SOUL_REAPER -> Vfx.sound(center, Sound.BLOCK_BELL_USE, 0.7f, 1.5f);
        }
        Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f);
        // A three-tier dome of light.
        for (int layer = 0; layer < 3; layer++) {
            Vfx.ring(center.clone().add(0, 0.3 + layer * 0.8, 0), 4 - layer * 0.9, 36 - layer * 8,
                    release.form.color(), 1.3f);
        }
        center.getWorld().spawnParticle(release.theme.accent, center.clone().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.05);
        return true;
    }

    private static Vector outward(Location from, LivingEntity entity) {
        Vector out = entity.getLocation().toVector().subtract(from.toVector()).setY(0);
        return out.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : out.normalize();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        seal(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        seal(event.getEntity(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        seal(event.getPlayer(), true);
    }
}
