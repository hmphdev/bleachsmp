package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import dev.bleachsmp.fx.VfxKind;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First-form releases for every faction: Soul Reaper Shikai, Hollow Resurrección, Quincy Schrift and
 * Awakened Fullbring. A release is a timed state with two techniques on the starter item:
 * right-click uses the main technique, sneak + right-click the secondary one.
 *
 * <p>Kits are built so players rarely share one: each playstyle has two form variants (picked per player
 * from their UUID) with different main techniques, and the secondary technique comes from the player's
 * secondary playstyle. With 12 techniques that gives over a hundred kits per faction, and each faction
 * styles every technique with its own colours, particles, sounds and on-hit effect.
 */
public final class ReleaseService implements Listener {
    /** Technique mechanics. Each playstyle owns two, one per form variant. */
    enum Mechanic {
        WAVE("a cutting wave that tears through everything in its path"),
        BLINK("vanish and strike from behind a target up to 12 blocks away, blinding it"),
        BEAM("a long-range blast that bursts on impact"),
        DASH("dash forward, cutting every enemy you pass"),
        SLAM("slam the ground and hurl nearby enemies skyward"),
        WARD("heal and shield yourself and teammates while driving enemies back"),
        FLURRY("lunge at a target and unleash a five-hit combo"),
        SCATTER("scatter into afterimages, hop around the battlefield, and blind nearby enemies"),
        RAIN("call bolts down on the area you aim at"),
        SKYFALL("leap high, then crash down in a shockwave"),
        PILLARS("raise a line of erupting pillars that launch enemies"),
        CHAINS("bind nearby enemies, dragging them in and rooting them");

        final String description;

        Mechanic(String description) {
            this.description = description;
        }

        static Mechanic of(Playstyle style, int variant) {
            return switch (style) {
                case VANGUARD -> variant == 0 ? WAVE : FLURRY;
                case MIRAGE -> variant == 0 ? BLINK : SCATTER;
                case MARKSMAN -> variant == 0 ? BEAM : RAIN;
                case WAYFARER -> variant == 0 ? DASH : SKYFALL;
                case ARTIFICER -> variant == 0 ? SLAM : PILLARS;
                case KEEPER -> variant == 0 ? WARD : CHAINS;
            };
        }
    }

    /** Faction-wide look, sound, passive and on-hit effect for released forms. */
    enum Theme {
        SOUL_REAPER(Particle.ELECTRIC_SPARK, PotionEffectType.SPEED, 0, Material.CALCITE, "Hits slow enemies."),
        HOLLOW(Particle.SOUL_FIRE_FLAME, PotionEffectType.RESISTANCE, 0, Material.BONE_BLOCK,
                "Hits wither enemies and heal you."),
        QUINCY(Particle.END_ROD, PotionEffectType.JUMP_BOOST, 1, Material.LIGHT_BLUE_STAINED_GLASS,
                "Hits mark enemies with Glowing."),
        FULLBRINGER(Particle.ENCHANT, PotionEffectType.HASTE, 0, Material.IRON_BLOCK, "Hits weaken enemies.");

        final Particle accent;
        final PotionEffectType passive;
        final int passiveAmplifier;
        final Material pillar;
        final String onHit;

        Theme(Particle accent, PotionEffectType passive, int passiveAmplifier, Material pillar, String onHit) {
            this.accent = accent;
            this.passive = passive;
            this.passiveAmplifier = passiveAmplifier;
            this.pillar = pillar;
            this.onHit = onHit;
        }

        static Theme of(Faction faction) {
            return valueOf(faction.name());
        }
    }

    /** One released form: its name, what the player calls out, and its colours. */
    record Form(String name, String callout, Color color, TextColor text, BossBar.Color bar) {
        Particle.DustOptions dust(float size) {
            return Vfx.dust(color, size);
        }
    }

    static Form form(Canon.Kit kit, boolean advanced) {
        return new Form(advanced ? kit.advancedName() : kit.name(), advanced ? kit.advancedCommand() : kit.command(),
                kit.color(), kit.text(), kit.bar());
    }

    // ---------------------------------------------------------------- kit selection

    static long hash(UUID id, int salt) {
        long h = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)
                ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    /** The real form a player gets for a playstyle; Shikai and Bankai of a player are the same weapon. */
    static Canon.Kit kit(UUID id, Faction faction, Playstyle style) {
        return Canon.kit(id, faction, style);
    }

    static Form form(UUID id, Faction faction, Playstyle style, boolean advanced) {
        return form(kit(id, faction, style), advanced);
    }

    static Element element(UUID id, Faction faction, Playstyle style, boolean advanced) {
        return kit(id, faction, style).element();
    }

    static Mechanic primary(UUID id, Faction faction, Playstyle style) {
        return kit(id, faction, style).main();
    }

    static Mechanic secondary(UUID id, Faction faction, Playstyle style) {
        return kit(id, faction, style).second();
    }

    static String description(UUID id, Faction faction, Playstyle style, Playstyle trait, boolean advanced) {
        Canon.Kit kit = kit(id, faction, style);
        Form form = form(kit, advanced);
        String text = kit.owner() + "'s " + kit.name() + ". \"" + form.callout() + "\" Right-click: " + kit.mainName()
                + " — " + kit.main().description + ". Sneak + right-click: " + kit.secondName() + " — "
                + kit.second().description + ". Q: " + kit.signatureName() + " — " + kit.signatureText()
                + (advanced ? " (empowered)." : ".") + " " + Theme.of(faction).onHit + " Element: " + kit.element().label
                + " — it " + kit.element().effect + ".";
        if (advanced) {
            text += " F (once per release): " + kit.specialName() + " — " + kit.specialText() + ". Sneak + F seals.";
        }
        return text;
    }

    // ---------------------------------------------------------------- state

    static final class Release {
        final Faction faction;
        final Playstyle style;
        final Canon.Kit kit;
        final int variant;
        final boolean advanced;
        final double power;
        final Form form;
        final Theme theme;
        final Element element;
        final Mechanic main;
        final Mechanic second;
        final long startedAt;
        final long endsAt;
        final BossBar bar;
        BukkitTask task;
        long mainReadyAt;
        long secondReadyAt;
        boolean ultimateUsed;
        long nextImpactSoundAt;
        long nextHazardAt;
        long nextPatchAt;
        long signatureReadyAt;
        /** Light: targets carrying a mark, and when it fades. Ice: when each target may be rooted again. */
        final Map<UUID, Long> marks = new HashMap<>();
        final Map<UUID, Long> rootReadyAt = new HashMap<>();

        Release(Faction faction, Playstyle style, Canon.Kit kit, boolean advanced, double power, Form form,
                long startedAt, long endsAt, BossBar bar) {
            this.faction = faction;
            this.style = style;
            this.kit = kit;
            int variant = kit.index();
            Element element = kit.element();
            Mechanic main = kit.main();
            Mechanic second = kit.second();
            this.variant = variant;
            this.advanced = advanced;
            this.power = power;
            this.form = form;
            this.theme = Theme.of(faction);
            this.element = element;
            this.main = main;
            this.second = second;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.bar = bar;
        }
    }

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    private final Map<UUID, Release> active = new HashMap<>();
    /** The admin effect preview's pick of kit for a player's next release. */
    final Map<UUID, Canon.Kit> previewKits = new HashMap<>();
    private final Set<BlockDisplay> displays = new HashSet<>();

    ReleaseService(BleachSmpPlugin plugin, AwakeningService service) {
        this.plugin = plugin;
        this.service = service;
        KitFx.init(plugin, this);
    }

    Release get(Player player) {
        return active.get(player.getUniqueId());
    }

    public boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    boolean isAdvanced(Player player) {
        Release release = active.get(player.getUniqueId());
        return release != null && release.advanced;
    }

    /** Reads a setting from {@code awakenings.release} or {@code awakenings.advanced-release}. */
    private int setting(boolean advanced, String key, int fallback, int min, int max) {
        return Math.clamp(plugin.getConfig().getInt("awakenings." + (advanced ? "advanced-release." : "release.") + key,
                fallback), min, max);
    }

    int durationSeconds(boolean advanced) {
        return setting(advanced, "duration-seconds", advanced ? 40 : 30, 5, 300);
    }

    private double damage(double base) {
        return base * Math.clamp(plugin.getConfig().getDouble("awakenings.release.damage-multiplier", 1.0), 0.0, 5.0);
    }

    // ---------------------------------------------------------------- release

    /** Releases a form: the call-out, the faction's release burst, and the timed released state. */
    void release(Player player, Faction faction, Playstyle style, Playstyle trait, boolean advanced) {
        seal(player, false);
        UUID id = player.getUniqueId();
        Canon.Kit forced = previewKits.remove(id);
        Canon.Kit kit = forced != null && forced.faction() == faction ? forced : kit(id, faction, style);
        Form form = form(kit, advanced);
        Theme theme = Theme.of(faction);
        long now = System.currentTimeMillis();
        int seconds = durationSeconds(advanced);
        double power = advanced ? Math.clamp(plugin.getConfig().getDouble("awakenings.advanced-release.power", 1.4),
                0.0, 5.0) : 1.0;
        String tier = AwakeningService.formName(faction, advanced);
        BossBar bar = BossBar.bossBar(Component.text(tier + " — " + form.name(), form.text(), TextDecoration.BOLD),
                1.0f, form.bar(), advanced ? BossBar.Overlay.NOTCHED_20 : BossBar.Overlay.NOTCHED_10);
        Element element = kit.element();
        Release release = new Release(faction, style, kit, advanced, power, form, now, now + seconds * 1000L, bar);
        active.put(id, release);
        service.canon().released(player, release);

        Location loc = player.getLocation();
        Component callout = Component.text(player.getName() + ": ", NamedTextColor.GRAY)
                .append(Component.text("\"" + form.callout() + "\"", form.text(),
                        advanced ? TextDecoration.BOLD : TextDecoration.ITALIC));
        for (Player nearby : loc.getNearbyPlayers(advanced ? 64 : 32)) {
            nearby.sendMessage(callout);
        }
        player.showTitle(Title.title(Component.text(advanced ? tier.toUpperCase(java.util.Locale.ROOT) : form.name(),
                        advanced ? NamedTextColor.WHITE : form.text(), TextDecoration.BOLD),
                Component.text(advanced ? form.name() : tier, advanced ? form.text() : NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(advanced ? 300 : 150), Duration.ofMillis(advanced ? 2200 : 1400),
                        Duration.ofMillis(600))));
        player.sendMessage(Component.text("Right-click: " + kit.mainName()
                + "  |  Sneak + right-click: " + kit.secondName()
                + "  |  Q: " + kit.signatureName()
                + (advanced ? "  |  F: " + kit.specialName() + "  |  Sneak + F: seal" : ""), form.text()));
        player.showBossBar(bar);
        player.addPotionEffect(new PotionEffect(theme.passive, seconds * 20, theme.passiveAmplifier + (advanced ? 1 : 0),
                false, false, true));

        if (advanced) {
            advancedEntrance(player, release);
        } else {
            releaseSounds(loc, theme);
            Vfx.shockwave(plugin, loc, form.color(), 5.0, 8);
            // Spiritual pressure: push nearby enemies away without damaging them.
            for (LivingEntity entity : loc.getNearbyLivingEntities(5)) {
                if (service.canStrike(player, entity)) {
                    entity.setVelocity(outward(loc, entity).multiply(0.9).setY(0.35));
                }
            }
            releaseAnimation(player, release, form, theme);
        }

        // The element erupts as the release completes: after the faction animation, or at the peak of the entrance.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && active.get(id) == release) {
                element.eruption(player.getLocation(), advanced);
            }
        }, 20L);

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
                if (release.advanced) {
                    advancedAura(player, release, tick);
                }
                if (tick % 10 == 0) {
                    showCooldowns(player, release, time);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private static void showCooldowns(Player player, Release release, long now) {
        player.sendActionBar(cooldown(release.kit.mainName(), release.mainReadyAt, now, release)
                .append(Component.text("   |   Sneak: ", NamedTextColor.DARK_GRAY))
                .append(cooldown(release.kit.secondName(), release.secondReadyAt, now, release))
                .append(Component.text("   |   Q: ", NamedTextColor.DARK_GRAY))
                .append(cooldown(release.kit.signatureName(), release.signatureReadyAt, now, release))
                .append(release.advanced ? Component.text("   |   F: ", NamedTextColor.DARK_GRAY)
                        .append(release.ultimateUsed
                                ? Component.text(release.kit.specialName() + " spent", NamedTextColor.GRAY)
                                : Component.text(release.kit.specialName() + " ✔", release.form.text(),
                                        TextDecoration.BOLD))
                        : Component.empty()));
    }

    private static Component cooldown(String name, long readyAt, long now, Release release) {
        return readyAt > now
                ? Component.text(name + " " + ((readyAt - now + 999) / 1000) + "s", NamedTextColor.GRAY)
                : Component.text(name + " ✔", release.form.text());
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
    private void releaseAnimation(Player player, Release release, Form form, Theme theme) {
        Vfx.fallbackParticle(player.getLocation().add(0, 1, 0), Particle.SONIC_BOOM, 1, 0, 0);
        // The kit's own release, in effect models; the faction's particle animation below plays for everyone else.
        KitFx.release(player, release);
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
                        for (int strand = 0; strand < 3; strand++) {
                            double a = tick * 0.6 + strand * (Math.PI * 2 / 3);
                            Vfx.fallbackDust(base.clone().add(Math.cos(a) * 1.1, tick * 0.15, Math.sin(a) * 1.1),
                                    form.dust(1.6f));
                        }
                    }
                    case HOLLOW -> {
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, tick * 0.12, 0), 6,
                                0.45, 0.2, 0.45, 0.02);
                        world.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.4, 0), 2, 0.6, 0.2, 0.6, 0.01);
                        double r = 1.6 - tick * 0.06;
                        for (int s = 0; s < 2; s++) {
                            double a = tick * 0.8 + s * Math.PI;
                            Vfx.fallbackDust(base.clone().add(Math.cos(a) * r, 1.0, Math.sin(a) * r), form.dust(1.7f));
                        }
                        if (tick % 5 == 0) {
                            world.spawnParticle(Particle.SCULK_SOUL, base.clone().add(0, 1.2, 0), 4, 0.4, 0.4, 0.4, 0.05);
                        }
                    }
                    case QUINCY -> {
                        for (int p = 0; p < 14; p++) {
                            double a = p * (Math.PI / 7);
                            Vfx.fallbackDust(base.clone().add(Math.cos(a) * .55, 2.6 - tick * 0.05, Math.sin(a) * .55),
                                    form.dust(1.0f));
                        }
                        for (int p = 0; p < 6; p++) {
                            double a = p * (Math.PI / 3);
                            world.spawnParticle(Particle.END_ROD, base.clone().add(Math.cos(a) * 1.8, tick * 0.13,
                                    Math.sin(a) * 1.8), 1, 0, 0, 0, 0);
                        }
                    }
                    case FULLBRINGER -> {
                        Location hand = base.clone().add(0, 1.2, 0)
                                .add(base.getDirection().getCrossProduct(new Vector(0, 1, 0)).multiply(-0.4));
                        double r = 2.2 - tick * 0.1;
                        for (int s = 0; s < 4; s++) {
                            double a = tick * 0.5 + s * (Math.PI / 2);
                            Vfx.fallbackDust(hand.clone().add(Math.cos(a) * r, Math.sin(tick * 0.3) * 0.4, Math.sin(a) * r),
                                    form.dust(1.4f));
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
        // A faint, slowly turning sheath of reiatsu for players with the resource pack.
        double body = KitFx.size(player);
        double sheath = (release.advanced ? 1.15 : .9) * body;
        Vfx.keep("sheath:" + player.getUniqueId(), 6, () -> Vfx.model(VfxKind.AURA, base, release.form.color())
                        .follow(player, new Vector()).tone(release.advanced ? 1 : 2)
                        .pose(new Quaternionf(), Vfx.scale(sheath, 2.3 * body, sheath)))
                .move(2, new Vector3f(), new Quaternionf().rotateY(tick * .09f),
                        Vfx.scale(sheath + Math.sin(tick * .2) * .06, (2.2 + Math.sin(tick * .13) * .15) * body, sheath));
        release.element.aura(base, tick, release.advanced);
        KitFx.aura(player, release, tick);
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
        service.signatures().onSeal(player);
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
        for (BlockDisplay display : Set.copyOf(displays)) {
            display.remove();
        }
        displays.clear();
    }

    // ---------------------------------------------------------------- techniques

    /**
     * Uses a released form's technique: the main one, or the secondary one when {@code secondary} is set
     * (sneak + right-click). Called by the starter item's right-click ability in place of the sealed ability.
     */
    public void useTechnique(Player player, boolean secondary) {
        Release release = active.get(player.getUniqueId());
        if (release == null) {
            return;
        }
        if (service.signatures().silenced(player)) {
            player.sendActionBar(Component.text("Silenced!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            return;
        }
        Mechanic mechanic = secondary ? release.second : release.main;
        String name = secondary ? release.kit.secondName() : release.kit.mainName();
        long now = System.currentTimeMillis();
        long readyAt = secondary ? release.secondReadyAt : release.mainReadyAt;
        if (readyAt > now) {
            player.sendActionBar(Component.text(name + " ready in " + ((readyAt - now + 999) / 1000) + "s",
                    NamedTextColor.YELLOW));
            return;
        }
        boolean used = switch (mechanic) {
            case WAVE -> wave(player, release);
            case BLINK -> blink(player, release, name);
            case BEAM -> beam(player, release);
            case DASH -> dash(player, release);
            case SLAM -> slam(player, release);
            case WARD -> ward(player, release);
            case FLURRY -> flurry(player, release, name);
            case SCATTER -> scatter(player, release);
            case RAIN -> rain(player, release);
            case SKYFALL -> skyfall(player, release);
            case PILLARS -> pillars(player, release);
            case CHAINS -> chains(player, release, name);
        };
        if (!used) {
            return;
        }
        release.element.castSound(player.getLocation(), release.advanced);
        int cooldown = setting(release.advanced,
                secondary ? "secondary-technique-cooldown-seconds" : "technique-cooldown-seconds",
                release.advanced ? (secondary ? 8 : 5) : (secondary ? 10 : 6), 0, 600);
        long cooldownMillis = Math.round(cooldown * 1000L * service.pressure().cooldownFactor(player));
        if (secondary) {
            release.secondReadyAt = now + cooldownMillis;
        } else {
            release.mainReadyAt = now + cooldownMillis;
        }
        player.sendActionBar(Component.text(name + "!", release.form.text(), TextDecoration.BOLD));
    }

    /** Damages a target and applies the faction's on-hit effect with a small impact flash. */
    private void hit(Player caster, LivingEntity target, double amount, Release release) {
        double dealt = damage(amount) * release.power * elementMultiplier(caster, target, release);
        target.damage(dealt, caster);
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        accent(release, at, 8, 0.3, 0.3, 0.3, 0.05);
        Vfx.burst(at, release.form.color(), 8, 0.3, 1.2f);
        release.element.burst(at, release.advanced ? 1.4 : 1.0);
        release.element.onHit(caster, target);
        service.techniqueHit(caster, target, release.advanced);
        long now = System.currentTimeMillis();
        if (now >= release.nextImpactSoundAt) {
            release.nextImpactSoundAt = now + 120;
            release.element.impactSound(at);
        }
        elementEffect(caster, target, dealt, release, now);
        if (release.advanced) {
            hazard(caster, release, target.getLocation(), now);
        }
        switch (release.theme) {
            case SOUL_REAPER -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
            case HOLLOW -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                heal(caster, 1.0);
            }
            case QUINCY -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            case FULLBRINGER -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
        }
    }

    /** Signature moves and other systems strike through here so every hit gets the same faction and element rules. */
    void strike(Player caster, LivingEntity target, double amount, Release release) {
        hit(caster, target, amount, release);
    }

    private static void heal(Player player, double amount) {
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(max == null ? 20.0 : max.getValue(), player.getHealth() + amount));
    }

    // ---------------------------------------------------------------- element mechanics

    /** Elements that change how hard a hit lands. */
    private static double elementMultiplier(Player caster, LivingEntity target, Release release) {
        long now = System.currentTimeMillis();
        return switch (release.element) {
            // Wind trades damage for much stronger knockback.
            case WIND -> 0.85;
            // Fire burns hotter on a target that is already alight.
            case FIRE -> target.getFireTicks() > 0 ? 1.2 : 1.0;
            // Shadow strikes from behind for extra damage.
            case SHADOW -> {
                Vector facing = target.getLocation().getDirection().setY(0);
                Vector toCaster = caster.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                yield facing.lengthSquared() > 0.01 && toCaster.lengthSquared() > 0.01
                        && facing.normalize().dot(toCaster.normalize()) < -0.3 ? 1.3 : 1.0;
            }
            // Light: a marked target takes extra damage from the hit that consumes the mark.
            case LIGHT -> release.marks.getOrDefault(target.getUniqueId(), 0L) > now ? 1.3 : 1.0;
            default -> 1.0;
        };
    }

    /**
     * What each element does on top of a hit. These change how every technique plays: lightning chains, sound
     * splashes, water pulls, wind blasts away, earth launches, ice roots, blood drains, poison stacks, light marks,
     * petals keep cutting.
     */
    private void elementEffect(Player caster, LivingEntity target, double dealt, Release release, long now) {
        boolean advanced = release.advanced;
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        switch (release.element) {
            case LIGHTNING -> {
                // Arcs to the nearest other enemies.
                int jumps = advanced ? 3 : 2;
                LivingEntity from = target;
                Set<UUID> struck = new HashSet<>(Set.of(target.getUniqueId()));
                for (int i = 0; i < jumps; i++) {
                    LivingEntity next = null;
                    double best = 4.5 * 4.5;
                    for (LivingEntity entity : from.getLocation().getNearbyLivingEntities(4.5)) {
                        double distance = entity.getLocation().distanceSquared(from.getLocation());
                        if (!struck.contains(entity.getUniqueId()) && distance < best && service.canStrike(caster, entity)) {
                            best = distance;
                            next = entity;
                        }
                    }
                    if (next == null) {
                        break;
                    }
                    struck.add(next.getUniqueId());
                    Location a = from.getLocation().add(0, from.getHeight() / 2, 0);
                    Location b = next.getLocation().add(0, next.getHeight() / 2, 0);
                    jagged(a, b, release.element.tint);
                    next.damage(dealt * 0.4, caster);
                    next.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 2));
                    Vfx.sound(b, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 0.8f, 1.6f);
                    from = next;
                }
            }
            case SOUND -> {
                // The blow rings out and hits everything around the target.
                double radius = advanced ? 3.5 : 2.5;
                Vfx.ring(at, radius, 24, release.element.tint, 1.2f);
                for (LivingEntity entity : target.getLocation().getNearbyLivingEntities(radius)) {
                    if (!entity.equals(target) && service.canStrike(caster, entity)) {
                        entity.damage(dealt * 0.3, caster);
                        entity.getWorld().spawnParticle(Particle.NOTE, entity.getLocation().add(0, 2.1, 0), 1);
                    }
                }
            }
            case WATER -> later(() -> {
                // Drags the target toward the caster, overriding the technique's push.
                Vector pull = caster.getLocation().toVector().subtract(target.getLocation().toVector());
                if (pull.lengthSquared() > 1) {
                    target.setVelocity(pull.normalize().multiply(advanced ? 0.9 : 0.6).setY(0.2));
                }
            });
            case WIND -> later(() -> {
                Vector push = target.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0);
                if (push.lengthSquared() < 0.01) {
                    push = caster.getLocation().getDirection().setY(0);
                }
                target.setVelocity(push.normalize().multiply(advanced ? 1.9 : 1.4).setY(0.45));
            });
            case EARTH -> later(() -> {
                target.setVelocity(target.getVelocity().setY(advanced ? 0.8 : 0.6));
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation(), 20, 0.4, 0.1, 0.4, 0,
                        target.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()
                                ? target.getLocation().subtract(0, 1, 0).getBlock().getBlockData()
                                : release.element.block);
            });
            case ICE -> {
                // The first hit on a target roots it; it can't be rooted again for a few seconds.
                if (release.rootReadyAt.getOrDefault(target.getUniqueId(), 0L) <= now) {
                    release.rootReadyAt.put(target.getUniqueId(), now + 4000);
                    int ticks = advanced ? 30 : 20;
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6));
                    target.setVelocity(new Vector(0, Math.min(0, target.getVelocity().getY()), 0));
                    target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 0.5, 0), 25, 0.3, 0.5,
                            0.3, 0, release.element.block);
                    Vfx.sound(target.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.0f, 0.6f);
                }
            }
            case BLOOD -> heal(caster, dealt * (advanced ? 0.25 : 0.15));
            case POISON -> {
                // Venom stacks: a target already poisoned starts to wither too.
                if (target.hasPotionEffect(PotionEffectType.POISON)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, advanced ? 60 : 40, 0));
                }
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, advanced ? 80 : 60, 1));
            }
            case LIGHT -> {
                if (release.marks.getOrDefault(target.getUniqueId(), 0L) > now) {
                    release.marks.remove(target.getUniqueId());
                    target.getWorld().spawnParticle(Particle.END_ROD, at, 30, 0.2, 0.6, 0.2, 0.15);
                    Vfx.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
                } else {
                    release.marks.put(target.getUniqueId(), now + (advanced ? 7000 : 5000));
                    Vfx.ring(target.getLocation().add(0, target.getHeight() + 0.3, 0), 0.4, 10, release.element.tint, 0.9f);
                }
            }
            case PETAL, PALE_PETAL -> {
                // A swarm of blades keeps cutting for a moment after the hit.
                int cuts = advanced ? 5 : 3;
                new BukkitRunnable() {
                    int cut;

                    @Override
                    public void run() {
                        if (cut++ >= cuts || !target.isValid() || target.isDead()) {
                            cancel();
                            return;
                        }
                        Location body = target.getLocation().add(0, target.getHeight() / 2, 0);
                        release.element.main(body, 8, 0.5, 0.6, 0.5, 0.02);
                        Vfx.sound(body, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.6f, 1.4f);
                        int noDamage = target.getNoDamageTicks();
                        target.setNoDamageTicks(0);
                        target.damage(1.0, caster);
                        target.setNoDamageTicks(Math.max(noDamage, 0));
                    }
                }.runTaskTimer(plugin, 6L, 6L);
            }
            default -> { }
        }
    }

    private void later(Runnable action) {
        plugin.getServer().getScheduler().runTaskLater(plugin, action, 1L);
    }

    private static void jagged(Location from, Location to, Color color) {
        Location point = from.clone();
        Vector step = to.toVector().subtract(from.toVector()).multiply(0.25);
        for (int i = 0; i < 4; i++) {
            Location next = i == 3 ? to.clone() : point.clone().add(step).add((Math.random() - 0.5) * 0.6,
                    (Math.random() - 0.5) * 0.6, (Math.random() - 0.5) * 0.6);
            Vfx.line(point, next, color, 0.9f, 0.2);
            point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, next, 2, 0.05, 0.05, 0.05, 0.02);
            point = next;
        }
    }

    /** Fire, ice and poison techniques leave a short-lived trail of their element along their path. */
    private void trailPatch(Player caster, Release release, Location at) {
        Element element = release.element;
        if (at == null || (element != Element.FIRE && element != Element.ICE && element != Element.POISON)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < release.nextPatchAt) {
            return;
        }
        release.nextPatchAt = now + 150;
        Location center = at.clone().add(0, 0.1, 0);
        int duration = release.advanced ? 50 : 30;
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick > duration || !caster.isOnline()) {
                    cancel();
                    return;
                }
                element.main(center, 2, 0.5, 0.05, 0.5, 0.01);
                if (tick % 10 == 0) {
                    for (LivingEntity entity : center.getNearbyLivingEntities(1.3, 1.5)) {
                        if (service.canStrike(caster, entity)) {
                            element.onHit(caster, entity);
                        }
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** The faction's accent particle with the form's element layered over it. */
    private static void accent(Release release, Location at, int count, double dx, double dy, double dz, double speed) {
        at.getWorld().spawnParticle(release.theme.accent, at, Math.max(1, (count + 1) / 2), dx, dy, dz, speed);
        release.element.layer(at, count, dx, dy, dz, speed);
    }

    /** Advanced forms: a hit leaves a two-second patch of the element that keeps applying its effect. */
    private void hazard(Player caster, Release release, Location at, long now) {
        if (now < release.nextHazardAt) {
            return;
        }
        release.nextHazardAt = now + 700;
        Location center = at.clone();
        for (int i = 0; i < 4 && center.getBlock().isPassable()
                && center.clone().subtract(0, 1, 0).getBlock().isPassable(); i++) {
            center.subtract(0, 1, 0);
        }
        center.setY(center.getBlockY() + 0.1);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick > 40 || !caster.isOnline()) {
                    cancel();
                    return;
                }
                release.element.hazardTick(center, tick);
                if (tick % 10 == 0) {
                    for (LivingEntity entity : center.getNearbyLivingEntities(1.6, 1.5)) {
                        if (service.canStrike(caster, entity)) {
                            release.element.onHit(caster, entity);
                        }
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Faction-flavoured cast sound shared by several techniques. */
    private static void castSound(Location at, Theme theme, float pitch) {
        switch (theme) {
            case SOUL_REAPER -> Vfx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, pitch);
            case HOLLOW -> Vfx.sound(at, Sound.ENTITY_RAVAGER_ATTACK, 1.0f, pitch);
            case QUINCY -> Vfx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, pitch);
            case FULLBRINGER -> Vfx.sound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, pitch);
        }
    }

    private boolean wave(Player player, Release release) {
        waveSound(player.getEyeLocation(), release.theme);
        launchWave(player, release, player.getEyeLocation().getDirection(), release.advanced ? 2.6 : 2.0, 8.0);
        return true;
    }

    private static void waveSound(Location origin, Theme theme) {
        switch (theme) {
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
    }

    /** A travelling wave: a crescent (three claw marks for Hollows) that hits each enemy once. */
    private void launchWave(Player player, Release release, Vector direction, double size, double amount) {
        Location origin = player.getEyeLocation().subtract(0, 0.35, 0);
        Vector forward = direction.clone().normalize();
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                Location center = origin.clone().add(forward.clone().multiply(1.5 + step * 1.5));
                if (step >= 11 || (!release.element.phases() && center.getBlock().getType().isSolid())) {
                    Vfx.burst(center, release.form.color(), 25, 0.6, 1.8f);
                    Vfx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.7f);
                    cancel();
                    return;
                }
                KitFx.wave(release, center, forward, size, step);
                accent(release, center, 4, 0.5, 0.3, 0.5, 0.02);
                if (release.theme == Theme.SOUL_REAPER) {
                    center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1);
                }
                if (step % 2 == 0) {
                    trailPatch(player, release, groundAt(center));
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(size + 0.1)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        hit(player, entity, amount, release);
                        entity.setVelocity(forward.clone().multiply(0.6).setY(0.3));
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** One length of a fired beam: a white core inside the form's colour, swelling then thinning out. */
    static void beamSegment(Location from, Location to, Color color, double width) {
        Vector span = to.toVector().subtract(from.toVector());
        double length = span.length();
        if (length < .05) {
            return;
        }
        Quaternionf aim = Vfx.facing(span);
        Vfx.model(VfxKind.BEAM, from, color)
                .pose(aim, Vfx.scale(width * .6, width * .6, length + .05))
                .to(1, 2, aim, Vfx.scale(width, width, length + .05))
                .to(4, 7, aim, Vfx.scale(width * .15, width * .15, length + .05))
                .life(11, 6);
        Vfx.model(VfxKind.BEAM, from, Color.WHITE)
                .pose(aim, Vfx.scale(width * .35, width * .35, length + .05))
                .to(3, 6, aim, Vfx.scale(width * .05, width * .05, length + .05))
                .life(9, 5);
    }

    private LivingEntity aimedEnemy(Player player, double range) {
        var result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                range, 0.6, entity -> entity instanceof LivingEntity && !entity.equals(player));
        if (result != null && result.getHitEntity() instanceof LivingEntity target && service.canStrike(player, target)) {
            return target;
        }
        return null;
    }

    private boolean blink(Player player, Release release, String name) {
        LivingEntity target = aimedEnemy(player, 12);
        if (target == null) {
            player.sendActionBar(Component.text(name + " needs an enemy within 12 blocks.", NamedTextColor.GRAY));
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
        if (!standable(behind)) {
            player.sendActionBar(Component.text("No room behind your target.", NamedTextColor.GRAY));
            return false;
        }
        behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
        Location from = player.getLocation().add(0, 1, 0);
        afterimage(from, release);
        Vfx.line(from, behind.clone().add(0, 1, 0), release.form.color(), 0.8f, 0.6);
        blinkSound(from, release.theme);
        player.teleport(behind);
        hit(player, target, 7.0, release);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2, 0.3, 0.2, 0.3, 0);
        Vfx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        return true;
    }

    private static void afterimage(Location at, Release release) {
        Vfx.burst(at, release.form.color(), 40, 0.35, 1.5f);
        KitFx.vanish(release, at);
        at.getWorld().spawnParticle(Particle.CLOUD, at, 12, 0.3, 0.6, 0.3, 0.02);
        accent(release, at, 10, 0.3, 0.6, 0.3, 0.05);
    }

    private static void blinkSound(Location at, Theme theme) {
        switch (theme) {
            case HOLLOW -> Vfx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
            case QUINCY -> Vfx.sound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.6f);
            case FULLBRINGER -> Vfx.sound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
            case SOUL_REAPER -> Vfx.sound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        }
    }

    private static boolean standable(Location at) {
        return at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable();
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
        Vfx.burst(origin.clone().add(forward), release.form.color(), cero ? 20 : 8, 0.15, 1.6f);
        // Energy gathers at the muzzle before it fires: a cero charges for a moment.
        Vfx.model(VfxKind.ORB, origin.clone().add(forward), release.form.color())
                .pose(new Quaternionf(), Vfx.scale(.1))
                .to(1, cero ? 4 : 1, new Quaternionf().rotateY(1.5f).rotateX(.7f), Vfx.scale(cero ? 1.1 : .55))
                .grow(cero ? 6 : 3, 4, Vfx.scale(.05))
                .life(cero ? 11 : 7, 3);
        KitFx.charge(release, origin.clone().add(forward), forward);
        double beamWidth = cero ? 1.15 : .45;
        int speed = cero ? 4 : 3;
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                Location from = origin.clone().add(forward.clone().multiply(1 + step * speed));
                for (int sub = 0; sub < speed; sub++) {
                    Location point = origin.clone().add(forward.clone().multiply(1 + step * speed + sub));
                    if (sub == 0) {
                        trailPatch(player, release, groundAt(point));
                    }
                    accent(release, point, 1, 0, 0, 0, 0);
                    Particle.DustOptions dust = release.form.dust(cero ? 2.0f : 1.4f);
                    for (int d = 0; d < (cero ? 6 : 2); d++) {
                        Vfx.fallbackDust(point.clone().add(ThreadLocalRandom.current().nextGaussian() * (cero ? .22 : .08),
                                ThreadLocalRandom.current().nextGaussian() * (cero ? .22 : .08),
                                ThreadLocalRandom.current().nextGaussian() * (cero ? .22 : .08)), dust);
                    }
                    LivingEntity struck = null;
                    for (LivingEntity entity : point.getNearbyLivingEntities(cero ? 1.5 : 1.2)) {
                        if (!entity.equals(player)) {
                            struck = entity;
                            break;
                        }
                    }
                    if (struck != null || (!release.element.phases() && point.getBlock().getType().isSolid())
                            || step >= 10) {
                        KitFx.beam(release, from, point, beamWidth, step);
                        KitFx.beamImpact(release, point);
                        burst(point, struck);
                        cancel();
                        return;
                    }
                }
                KitFx.beam(release, from, origin.clone().add(forward.clone().multiply(1 + (step + 1) * speed)),
                        beamWidth, step);
                step++;
            }

            private void burst(Location point, LivingEntity struck) {
                Vfx.shockwave(plugin, point.clone().subtract(0, .6, 0), release.form.color(), cero ? 3.5 : 2.0, 6);
                accent(release, point, 40, 0.2, 0.2, 0.2, 0.25);
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
        Vector forward = horizontal(player);
        player.setVelocity(forward.multiply(2.1).setY(0.15));
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
                trailPatch(player, release, groundAt(player.getLocation()));
                accent(release, at, 2, 0.3, 0.4, 0.3, 0.02);
                Vfx.burst(at, release.form.color(), 6, 0.5, 1.3f);
                KitFx.dashTrail(release, player, tick);
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
        groundImpact(player, release, center, 5.0, 6.0, 0.95);
        switch (release.theme) {
            case HOLLOW -> Vfx.sound(center, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 0.6f);
            case QUINCY -> Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.6f);
            case FULLBRINGER -> Vfx.sound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
            case SOUL_REAPER -> Vfx.sound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
        }
        return true;
    }

    /** Shared ground impact: damage and launch enemies in a radius, with rippling debris rings. */
    private void groundImpact(Player player, Release release, Location center, double radius, double amount,
            double launch) {
        Material ground = center.clone().subtract(0, 1, 0).getBlock().getType();
        BlockData debris = (ground.isSolid() ? ground : Material.STONE).createBlockData();
        Vfx.sound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.7f);
        Vfx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
        KitFx.slam(release, player, center, radius);
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (service.canStrike(player, entity)) {
                hit(player, entity, amount, release);
                entity.setVelocity(outward(center, entity).multiply(0.4).setY(launch));
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
                double r = 0.6 + ring * (radius - 0.6) / 6;
                for (int i = 0; i < 24; i++) {
                    double a = i * (Math.PI * 2 / 24);
                    Location point = center.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                    point.getWorld().spawnParticle(Particle.BLOCK, point, 4, 0.1, 0.2, 0.1, 0.1, debris);
                    if (i % 3 == 0) {
                        point.getWorld().spawnParticle(Particle.DUST, point.add(0, 0.4, 0), 1, 0, 0, 0, 0,
                                release.form.dust(1.5f));
                        accent(release, point, 1, 0, 0.2, 0, 0.02);
                    }
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
        for (int layer = 0; layer < 3; layer++) {
            Vfx.ring(center.clone().add(0, 0.3 + layer * 0.8, 0), 4 - layer * 0.9, 36 - layer * 8,
                    release.form.color(), 1.3f);
        }
        accent(release, center.clone().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.05);
        KitFx.ward(release, player);
        return true;
    }

    /** Lunge at the aimed enemy and land a five-hit combo, each slash at a new angle. */
    private boolean flurry(Player player, Release release, String name) {
        LivingEntity target = aimedEnemy(player, 6);
        if (target == null) {
            player.sendActionBar(Component.text(name + " needs an enemy within 6 blocks.", NamedTextColor.GRAY));
            return false;
        }
        Vector toward = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (toward.lengthSquared() > 0.01) {
            player.setVelocity(toward.normalize().multiply(0.7).setY(0.1));
        }
        new BukkitRunnable() {
            int strike;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || target.isDead()
                        || target.getLocation().distanceSquared(player.getLocation()) > 49 || strike >= 5) {
                    cancel();
                    return;
                }
                boolean last = strike == 4;
                Location at = target.getLocation().add(0, target.getHeight() * 0.55, 0);
                Vector facing = at.toVector().subtract(player.getLocation().toVector()).setY(0);
                if (facing.lengthSquared() < 0.01) {
                    facing = new Vector(0, 0, 1);
                }
                double tilt = ThreadLocalRandom.current().nextDouble(-0.6, 0.6);
                KitFx.flurryHit(release, at, facing, strike, last);
                at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1);
                castSound(at, release.theme, 1.0f + strike * 0.18f);
                target.setNoDamageTicks(0);
                hit(player, target, last ? 3.5 : 2.0, release);
                if (last) {
                    target.setVelocity(facing.normalize().multiply(0.8).setY(0.35));
                    at.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.4, 0.4, 0.4, 0.3);
                    Vfx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
                }
                strike++;
            }
        }.runTaskTimer(plugin, 3L, 3L);
        return true;
    }

    /** Scatter into afterimages: blind and shake nearby enemies, turn invisible, and hop three times. */
    private boolean scatter(Player player, Release release) {
        Location start = player.getLocation();
        for (LivingEntity entity : start.getNearbyLivingEntities(5)) {
            if (service.canStrike(player, entity)) {
                hit(player, entity, 3.0, release);
                entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
                if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                    mob.setTarget(null);
                }
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false, true));
        service.conceal(player, 3);
        Vfx.ring(start.clone().add(0, 0.2, 0), 5, 40, release.form.color(), 1.2f);
        Vfx.sound(start, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
        new BukkitRunnable() {
            int hop;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || hop >= 3) {
                    cancel();
                    return;
                }
                hop++;
                Location from = player.getLocation();
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int attempt = 0; attempt < 8; attempt++) {
                    double a = random.nextDouble(Math.PI * 2);
                    double d = random.nextDouble(2.5, 4.5);
                    Location to = from.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    to.setY(Math.floor(to.getY()));
                    if (standable(to) && to.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                        afterimage(from.clone().add(0, 1, 0), release);
                        blinkSound(from, release.theme);
                        to.setYaw(from.getYaw());
                        to.setPitch(from.getPitch());
                        player.teleport(to);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 2L, 5L);
        return true;
    }

    /** Call down a storm of bolts on the area the player aims at. */
    private boolean rain(Player player, Release release) {
        Location target = aimPoint(player, 24);
        castSound(player.getLocation(), release.theme, 0.6f);
        Vfx.sound(target, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
        storm(player, release, target, release.advanced ? 4.0 : 3.2, 10, 3.0, 3, 3L);
        return true;
    }

    /** The ground spot the player is aiming at, up to {@code range} blocks away. */
    private static Location aimPoint(Player player, double range) {
        var aim = player.rayTraceBlocks(range);
        Location center = aim != null ? aim.getHitPosition().toLocation(player.getWorld())
                : player.getLocation().add(horizontal(player).multiply(range / 2));
        Location ground = groundAt(center);
        return ground != null ? ground : center;
    }

    /** Drops {@code bolts} bolts at random inside a circle; each entity can be hit at most {@code cap} times. */
    private void storm(Player player, Release release, Location target, double radius, int bolts, double amount,
            int cap, long period) {
        Vfx.ring(target.clone().add(0, 0.15, 0), radius, (int) (radius * 10), release.form.color(), 1.3f);
        Map<UUID, Integer> hits = new HashMap<>();
        new BukkitRunnable() {
            int bolt;

            @Override
            public void run() {
                if (!player.isOnline() || bolt++ >= bolts) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                double a = random.nextDouble(Math.PI * 2);
                double d = Math.sqrt(random.nextDouble()) * radius;
                Location spot = target.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                Location floor = groundAt(spot);
                Location impact = floor != null ? floor : spot;
                Vfx.fallbackLine(impact.clone().add(0, 9, 0), impact, release.form.color(), 1.1f, 0.45);
                if (!KitFx.rainDrop(release, impact)) {
                    Vfx.bolt(impact, release.form.color(), 9, .32);
                }
                Vfx.crater(impact, 1.6, 50);
                accent(release, impact, 8, 0.2, 0.1, 0.2, 0.05);
                Vfx.burst(impact.clone().add(0, 0.3, 0), release.form.color(), 10, 0.4, 1.3f);
                Vfx.sound(impact, release.theme == Theme.HOLLOW ? Sound.ENTITY_BLAZE_SHOOT
                        : Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f + random.nextFloat() * 0.4f);
                for (LivingEntity entity : impact.getNearbyLivingEntities(1.5)) {
                    int count = hits.getOrDefault(entity.getUniqueId(), 0);
                    if (count < cap && service.canStrike(player, entity)) {
                        hits.put(entity.getUniqueId(), count + 1);
                        entity.setNoDamageTicks(0);
                        hit(player, entity, amount, release);
                    }
                }
            }
        }.runTaskTimer(plugin, 6L, period);
    }

    /** Leap high, hang for a moment, then dive into a crushing shockwave. No fall damage. */
    private boolean skyfall(Player player, Release release) {
        Vector forward = horizontal(player);
        player.setVelocity(forward.clone().multiply(0.45).setY(1.25));
        Location start = player.getLocation();
        Vfx.ring(start.clone().add(0, 0.1, 0), 1.3, 18, release.form.color(), 1.4f);
        Vfx.sound(start, Sound.ENTITY_BREEZE_JUMP, 1.0f, 0.8f);
        castSound(start, release.theme, 0.7f);
        new BukkitRunnable() {
            int tick;
            boolean diving;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                tick++;
                player.setFallDistance(0);
                Location at = player.getLocation();
                Vfx.burst(at.clone().add(0, 1, 0), release.form.color(), 4, 0.3, 1.3f);
                accent(release, at, 2, 0.2, 0.2, 0.2, 0.01);
                if (!diving && tick >= 9) {
                    diving = true;
                    player.setVelocity(horizontal(player).multiply(0.6).setY(-1.9));
                    Vfx.sound(at, Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 0.8f);
                }
                boolean landed = at.clone().subtract(0, 0.15, 0).getBlock().getType().isSolid();
                if (diving && (landed || tick > 50)) {
                    groundImpact(player, release, at, 4.5, 7.0, 0.8);
                    Vfx.shockwave(plugin, at, release.form.color(), 5.0, 6);
                    at.getWorld().spawnParticle(Particle.EXPLOSION, at, 2, 0.5, 0.1, 0.5, 0);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** A line of pillars (block displays, no world edits) erupts forward and launches enemies. */
    private boolean pillars(Player player, Release release) {
        Vector forward = horizontal(player);
        Location origin = player.getLocation();
        BlockData block = release.theme.pillar.createBlockData();
        Set<UUID> hit = new HashSet<>();
        castSound(origin, release.theme, 0.6f);
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (!player.isOnline() || step >= 9) {
                    cancel();
                    return;
                }
                step++;
                Location point = origin.clone().add(forward.clone().multiply(1.2 + step * 1.1));
                Location ground = groundAt(point);
                if (ground == null) {
                    cancel();
                    return;
                }
                raisePillar(ground, block, 1.6 + step * 0.12);
                KitFx.pillar(release, ground, 1.6 + step * 0.12, step);
                ground.getWorld().spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.2, 0), 14, 0.4, 0.2, 0.4, 0.1,
                        block);
                accent(release, ground.clone().add(0, 1, 0), 4, 0.2, 0.6, 0.2, 0.02);
                Vfx.sound(ground, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.6f + step * 0.05f);
                for (LivingEntity entity : ground.getNearbyLivingEntities(1.5, 2.5)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        hit(player, entity, 6.0, release);
                        entity.setVelocity(new Vector(0, 0.85, 0).add(forward.clone().multiply(0.2)));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    private void raisePillar(Location ground, BlockData block, double height) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location base = ground.clone();
        base.setYaw(0);
        base.setPitch(0);
        Quaternionf tilt = new Quaternionf().rotateXYZ((float) random.nextDouble(-0.25, 0.25), 0f,
                (float) random.nextDouble(-0.25, 0.25));
        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(block);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.35f, -0.2f, -0.35f), tilt,
                    new Vector3f(0.7f, 0.05f, 0.7f), new Quaternionf()));
        });
        displays.add(display);
        var scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(3);
                display.setTransformation(new Transformation(new Vector3f(-0.35f, -0.2f, -0.35f), tilt,
                        new Vector3f(0.7f, (float) height, 0.7f), new Quaternionf()));
            }
        }, 1L);
        scheduler.runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(6);
                display.setTransformation(new Transformation(new Vector3f(-0.35f, -0.2f, -0.35f), tilt,
                        new Vector3f(0.7f, 0.05f, 0.7f), new Quaternionf()));
            }
        }, 28L);
        scheduler.runTaskLater(plugin, () -> {
            display.remove();
            displays.remove(display);
        }, 35L);
    }

    /** Bind every enemy nearby with spirit chains, dragging them in and rooting them briefly. */
    private boolean chains(Player player, Release release, String name) {
        Location center = player.getLocation();
        Location chest = center.clone().add(0, 1.1, 0);
        int bound = 0;
        for (LivingEntity entity : center.getNearbyLivingEntities(7)) {
            if (!service.canStrike(player, entity) || !player.hasLineOfSight(entity)) {
                continue;
            }
            bound++;
            Location at = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
            Vfx.fallbackLine(chest, at, release.form.color(), 1.0f, 0.3);
            Vfx.fallbackLine(chest.clone().add(0, 0.12, 0), at.clone().add(0, 0.12, 0), Color.fromRGB(80, 80, 90), 0.7f, 0.45);
            KitFx.bind(release, chest, at);
            hit(player, entity, 3.0, release);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
            Vector pull = center.toVector().subtract(entity.getLocation().toVector());
            double distance = pull.length();
            if (distance > 1.5) {
                entity.setVelocity(pull.normalize().multiply(Math.min(1.3, distance * 0.22)).setY(0.25));
            }
        }
        if (bound == 0) {
            player.sendActionBar(Component.text(name + " needs an enemy in sight within 7 blocks.", NamedTextColor.GRAY));
            return false;
        }
        Vfx.sound(center, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.6f);
        Vfx.sound(center, Sound.BLOCK_CHAIN_HIT, 1.0f, 0.8f);
        castSound(center, release.theme, 0.8f);
        Vfx.ring(center.clone().add(0, 0.1, 0), 1.0, 14, release.form.color(), 1.2f);
        return true;
    }

    // ---------------------------------------------------------------- advanced: entrance and aura

    /** The advanced transformation: a two-second eruption of reiatsu with a faction-specific signature. */
    private void advancedEntrance(Player player, Release release) {
        Location loc = player.getLocation();
        Color dark = Color.fromRGB(18, 12, 22);
        switch (release.theme) {
            case SOUL_REAPER -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
                Vfx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
                Vfx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
            }
            case HOLLOW -> {
                Vfx.sound(loc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 1.2f);
                Vfx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
            }
            case QUINCY -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                Vfx.sound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
                Vfx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
            }
            case FULLBRINGER -> {
                Vfx.sound(loc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.8f);
                Vfx.sound(loc, Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                Vfx.sound(loc, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
            }
        }
        // Spiritual pressure: enemies are thrown back, and enemy players are briefly swallowed by darkness.
        for (LivingEntity entity : loc.getNearbyLivingEntities(8)) {
            if (service.canStrike(player, entity)) {
                entity.setVelocity(outward(loc, entity).multiply(1.2).setY(0.45));
                if (entity instanceof Player) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                }
            }
        }
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 40) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                var world = base.getWorld();
                // A pillar of reiatsu, coloured with a dark core, that grows to ten blocks.
                double top = Math.min(10, tick * 0.5);
                for (double h = 0; h <= top; h += 0.7) {
                    double a = tick * 0.4 + h;
                    Vfx.fallbackDust(base.clone().add(Math.cos(a) * 0.7, h, Math.sin(a) * 0.7), release.form.dust(1.8f));
                    if (release.theme != Theme.QUINCY) {
                        Vfx.fallbackDust(base.clone().add(-Math.cos(a) * 0.4, h, -Math.sin(a) * 0.4), Vfx.dust(dark, 1.6f));
                    }
                }
                accent(release, base.clone().add(0, 1, 0), 6, 0.6, 1.0, 0.6, 0.05);
                if (tick == 0) {
                    // A bolt of light from the sky (no thunder, so it is not heard server-wide).
                    Color bolt = release.theme == Theme.HOLLOW ? dark : Color.WHITE;
                    Vfx.fallbackLine(base.clone().add(0, 18, 0), base, bolt, 1.6f, 0.4);
                    Vfx.bolt(base, release.theme == Theme.HOLLOW ? release.form.color() : Color.WHITE, 18, .55);
                    Vfx.fallbackParticle(base.clone().add(0, 1, 0), Particle.SONIC_BOOM, 1, 0, 0);
                    // Then the kit's own entrance: every Bankai, Segunda Etapa, Vollständig and Complete Fullbring
                    // has its own.
                    KitFx.advanced(player, release);
                }
                if (tick == 20) {
                    Vfx.shockwave(plugin, base, release.form.color(), 9.0, 12);
                    Vfx.burst(base.clone().add(0, 1, 0), release.form.color(), 80, 1.2, 2.0f);
                    world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1, 0), 3, 0.8, 0.5, 0.8, 0);
                    Vfx.sound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
                    if (release.theme == Theme.FULLBRINGER) {
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.6);
                    } else if (release.theme == Theme.HOLLOW) {
                        world.spawnParticle(Particle.SCULK_SOUL, base.clone().add(0, 1, 0), 30, 1.0, 1.0, 1.0, 0.08);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Extra aura while an advanced form is out: wings, horns, armour plates, or dark rising reiatsu. */
    private static void advancedAura(Player player, Release release, int tick) {
        Location base = player.getLocation();
        var world = base.getWorld();
        double yaw = Math.toRadians(base.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        double body = KitFx.size(player);
        switch (release.theme) {
            case QUINCY -> {
                // Two flapping wings of light behind the shoulders.
                double flap = 1 - 0.18 * Math.sin(tick * 0.25);
                for (int side = -1; side <= 1; side += 2) {
                    int s = side;
                    Vector offset = new Vector(0, 1.3, 0).add(forward.clone().multiply(-0.3))
                            .add(right.clone().multiply(side * 0.12)).multiply(body);
                    // In the facing frame, -X is the player's right; the right wing is mirrored onto it.
                    float beat = (float) (Math.sin(tick * 0.25) * 0.3 + 0.12);
                    Quaternionf pose = Vfx.facing(forward).rotateY(-s * 0.42f).rotateZ(-s * beat);
                    Vector3f size = Vfx.scale((s > 0 ? -2.1 : 2.1) * body, 2.1 * body, 2.1 * body);
                    Vfx.keep("wing:" + player.getUniqueId() + ":" + side, 6, () -> Vfx.model(VfxKind.WING,
                                    player.getLocation().add(offset), release.form.color())
                            .follow(player, offset).pose(pose, size))
                            .offset(offset).move(2, new Vector3f(), pose, size);
                }
                if (tick % 4 != 0) {
                    return;
                }
                for (int side = -1; side <= 1; side += 2) {
                    for (int row = 0; row < 2; row++) {
                        int points = row == 0 ? 10 : 7;
                        for (int i = 0; i < points; i++) {
                            double t = i / (double) (points - 1);
                            Location point = base.clone().add(0, 1.35 - row * 0.35 + Math.sin(t * Math.PI) * 0.75 - t * 0.35, 0)
                                    .add(forward.clone().multiply(-(0.35 + t * 0.25)))
                                    .add(right.clone().multiply(side * (0.25 + t * (row == 0 ? 1.7 : 1.2) * flap)));
                            Vfx.fallbackDust(point, release.form.dust(1.0f));
                        }
                    }
                    if (tick % 12 == 0) {
                        world.spawnParticle(Particle.END_ROD, base.clone().add(0, 1.2, 0)
                                .add(right.clone().multiply(side * 1.9 * flap)), 1, 0, 0, 0, 0);
                    }
                }
            }
            case SOUL_REAPER -> {
                // Black-and-colour reiatsu rising off the body like flame.
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble(Math.PI * 2);
                    Location point = base.clone().add(Math.cos(a) * 0.5, random.nextDouble(0, 2.0), Math.sin(a) * 0.5);
                    Vfx.fallbackDust(point, Vfx.dust(i == 0 ? release.form.color() : Color.fromRGB(20, 20, 25), 1.3f));
                }
                if (tick % 4 == 0) {
                    double a = random.nextDouble(Math.PI * 2);
                    Vfx.flame(base.clone().add(Math.cos(a) * .45, random.nextDouble(0, .6), Math.sin(a) * .45),
                            tick % 8 == 0 ? release.form.color() : Color.fromRGB(26, 22, 34), 1.2 + random.nextDouble(.8), 10);
                }
                if (tick % 10 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0.03);
                }
            }
            case HOLLOW -> {
                // Bone horns above the mask, swept out to each side.
                for (int side = -1; side <= 1; side += 2) {
                    Vector offset = new Vector(0, 1.78, 0).add(right.clone().multiply(side * 0.17))
                            .add(forward.clone().multiply(0.04)).multiply(body);
                    Quaternionf pose = Vfx.facing(forward).rotateZ(side * 0.55f);
                    Vfx.keep("horn:" + player.getUniqueId() + ":" + side, 6, () -> Vfx.model(VfxKind.HORN,
                                    player.getLocation().add(offset), Color.WHITE)
                            .follow(player, offset).pose(pose, Vfx.scale(1.3 * body)))
                            .offset(offset).move(2, new Vector3f(), pose, Vfx.scale(1.3 * body));
                }
                if (tick % 4 == 0) {
                    Color bone = Color.fromRGB(235, 228, 205);
                    Location head = base.clone().add(0, 1.85, 0);
                    for (int side = -1; side <= 1; side += 2) {
                        Vfx.fallbackLine(head.clone().add(right.clone().multiply(side * 0.18)),
                                head.clone().add(right.clone().multiply(side * 0.38)).add(forward.clone().multiply(0.15))
                                        .add(0, 0.5, 0), bone, 0.6f, 0.1);
                    }
                }
                if (tick % 6 == 0) {
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                    Vfx.flame(base.clone().add(Math.cos(a) * .6, 0, Math.sin(a) * .6), Color.fromRGB(70, 200, 225), 1.1, 12);
                }
                if (tick % 10 == 0) {
                    Vfx.ring(base.clone().add(0, 0.1, 0), 1.0, 10, Particle.SOUL);
                    world.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.3, 0), 2, 0.4, 0.1, 0.4, 0.01);
                }
            }
            case FULLBRINGER -> {
                // Four armour plates in the form's colour orbiting the body.
                for (int plate = 0; plate < 4; plate++) {
                    double a = tick * 0.15 + plate * (Math.PI / 2);
                    Vector out = new Vector(Math.cos(a), 0, Math.sin(a));
                    Vector offset = out.clone().multiply(1.1).setY(1.0).multiply(body);
                    Quaternionf pose = Vfx.facing(out);
                    Vfx.keep("plate:" + player.getUniqueId() + ":" + plate, 6, () -> Vfx.model(VfxKind.PLATE,
                                    player.getLocation().add(offset), release.form.color())
                            .follow(player, offset).pose(pose, Vfx.scale(1.15 * body)))
                            .offset(offset).move(2, new Vector3f(), pose, Vfx.scale(1.15 * body));
                    for (double h = -0.5; h <= 0.51; h += 0.5) {
                        Vfx.fallbackDust(base.clone().add(Math.cos(a) * 1.1, 1.0 + h, Math.sin(a) * 1.1),
                                release.form.dust(1.1f));
                    }
                }
                if (tick % 10 == 0) {
                    world.spawnParticle(Particle.ENCHANT, base.clone().add(0, 1.2, 0), 10, 0.5, 0.6, 0.5, 0.5);
                }
            }
        }
    }

    // ---------------------------------------------------------------- advanced: ultimates

    /** Fires the advanced form's ultimate, once per release. */
    void ultimate(Player player) {
        Release release = active.get(player.getUniqueId());
        if (release == null || !release.advanced) {
            return;
        }
        String name = release.kit.specialName();
        if (release.ultimateUsed) {
            player.sendActionBar(Component.text(name + " is spent for this release. Sneak + F to seal.",
                    NamedTextColor.GRAY));
            return;
        }
        boolean used = switch (release.kit.special()) {
            case U_CATACLYSM -> cataclysm(player, release);
            case U_PHANTOM -> phantomExecution(player, release, name);
            case U_STARFALL -> starfall(player, release);
            case U_TEMPEST -> tempest(player, release);
            case U_WORLD_BREAKER -> worldBreaker(player, release);
            case U_SANCTUM -> sanctum(player, release);
            default -> service.canon().special(player, release);
        };
        if (!used) {
            return;
        }
        MoveFx.special(player, release);
        release.ultimateUsed = true;
        Location loc = player.getLocation();
        player.showTitle(Title.title(Component.text(name, release.form.text(), TextDecoration.BOLD),
                Component.text(release.form.name(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(400))));
        Component callout = Component.text(player.getName() + ": ", NamedTextColor.GRAY)
                .append(Component.text("\"" + name + "!\"", release.form.text(), TextDecoration.BOLD));
        for (Player nearby : loc.getNearbyPlayers(48)) {
            nearby.sendMessage(callout);
        }
        Vfx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        Vfx.fallbackParticle(loc.clone().add(0, 1, 0), Particle.SONIC_BOOM, 1, 0, 0);
    }

    /** Two volleys of three giant waves in a fan. */
    private boolean cataclysm(Player player, Release release) {
        Vector aim = player.getEyeLocation().getDirection();
        for (int volley = 0; volley < 2; volley++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                waveSound(player.getEyeLocation(), release.theme);
                for (int fan = -1; fan <= 1; fan++) {
                    launchWave(player, release, aim.clone().rotateAroundY(Math.toRadians(fan * 22)), 3.0, 9.0);
                }
            }, volley * 10L);
        }
        return true;
    }

    /** Blink behind up to five enemies in turn, striking each, then return to where it began. */
    private boolean phantomExecution(Player player, Release release, String name) {
        Location home = player.getLocation();
        java.util.List<LivingEntity> targets = new java.util.ArrayList<>();
        for (LivingEntity entity : home.getNearbyLivingEntities(12)) {
            if (service.canStrike(player, entity) && player.hasLineOfSight(entity)) {
                targets.add(entity);
            }
        }
        if (targets.isEmpty()) {
            player.sendActionBar(Component.text(name + " needs an enemy in sight within 12 blocks.", NamedTextColor.GRAY));
            return false;
        }
        targets.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(home)));
        java.util.List<LivingEntity> marks = targets.subList(0, Math.min(5, targets.size()));
        afterimage(home.clone().add(0, 1, 0), release);
        blinkSound(home, release.theme);
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                if (index >= marks.size()) {
                    afterimage(player.getLocation().add(0, 1, 0), release);
                    player.teleport(home);
                    blinkSound(home, release.theme);
                    Vfx.burst(home.clone().add(0, 1, 0), release.form.color(), 40, 0.5, 1.6f);
                    cancel();
                    return;
                }
                LivingEntity target = marks.get(index++);
                if (!target.isValid() || target.isDead()) {
                    return;
                }
                Vector facing = target.getLocation().getDirection().setY(0);
                if (facing.lengthSquared() < 0.01) {
                    facing = new Vector(0, 0, 1);
                }
                Location behind = target.getLocation().subtract(facing.normalize().multiply(1.3));
                Location from = player.getLocation().add(0, 1, 0);
                if (standable(behind)) {
                    behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
                    Vfx.line(from, behind.clone().add(0, 1, 0), release.form.color(), 0.8f, 0.6);
                    afterimage(from, release);
                    player.teleport(behind);
                }
                target.setNoDamageTicks(0);
                hit(player, target, 7.0, release);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                Location at = target.getLocation().add(0, target.getHeight() * 0.55, 0);
                Vfx.arc(at, at.toVector().subtract(player.getLocation().toVector()).setY(0), 1.6,
                        release.form.color(), 1.6f, 0.2);
                at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 2, 0.3, 0.2, 0.3, 0);
                Vfx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.7f + index * 0.1f);
            }
        }.runTaskTimer(plugin, 3L, 5L);
        return true;
    }

    /** A wide storm of bolts on the aimed area. */
    private boolean starfall(Player player, Release release) {
        Location target = aimPoint(player, 30);
        Vfx.sound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
        Vfx.shockwave(plugin, target, release.form.color(), 5.5, 10);
        storm(player, release, target, 5.5, 26, 4.0, 4, 2L);
        return true;
    }

    /** A tornado around the caster that drags enemies in a spiral, then hurls them skyward. */
    private boolean tempest(Player player, Release release) {
        Vfx.sound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.5f);
        Vfx.sound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.6f, 1.2f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                var world = center.getWorld();
                for (int k = 0; k < 12; k++) {
                    double h = k * 0.5;
                    double r = 0.6 + h * 0.4;
                    double a = tick * 0.5 + k * 0.7;
                    Location point = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
                    world.spawnParticle(Particle.CLOUD, point, 1, 0, 0, 0, 0);
                    Vfx.fallbackDust(point, release.form.dust(1.4f));
                }
                // The funnel: three spinning bands of wind, widening as they climb.
                for (int k = 0; k < 3; k++) {
                    int band = k;
                    double r = 1.3 + k * 1.1;
                    Vector offset = new Vector(0, k * 1.7, 0);
                    Color tint = k == 1 ? Color.WHITE : release.form.color();
                    Vfx.keep("tempest:" + player.getUniqueId() + ":" + k, 4, () -> Vfx.model(VfxKind.SPIRAL,
                                    center.clone().add(offset), tint).follow(player, offset).tone(band == 1 ? 1 : 0)
                            .pose(new Quaternionf(), Vfx.scale(r * 2, 2.0, r * 2)))
                            .move(2, new Vector3f(), new Quaternionf().rotateY(-(float) (tick * .55 + k)),
                                    Vfx.scale(r * 2, 2.0, r * 2));
                }
                if (tick >= 60) {
                    for (LivingEntity entity : center.getNearbyLivingEntities(8)) {
                        if (service.canStrike(player, entity)) {
                            entity.setNoDamageTicks(0);
                            hit(player, entity, 4.0, release);
                            entity.setVelocity(new Vector(0, 1.3, 0));
                        }
                    }
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, true));
                    Vfx.sound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.7f);
                    Vfx.burst(center.clone().add(0, 2, 0), release.form.color(), 60, 1.5, 1.8f);
                    cancel();
                    return;
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(8)) {
                    if (!service.canStrike(player, entity)) {
                        continue;
                    }
                    Vector toCenter = center.toVector().subtract(entity.getLocation().toVector()).setY(0);
                    if (toCenter.lengthSquared() < 0.01) {
                        continue;
                    }
                    Vector inward = toCenter.clone().normalize();
                    Vector around = new Vector(-inward.getZ(), 0, inward.getX());
                    entity.setVelocity(inward.multiply(0.25).add(around.multiply(0.4)).setY(0.08));
                    if (tick % 10 == 0) {
                        entity.setNoDamageTicks(0);
                        hit(player, entity, 2.0, release);
                    }
                }
                if (tick % 10 == 0) {
                    Vfx.sound(center, Sound.ENTITY_BREEZE_IDLE_AIR, 1.0f, 0.6f);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    /** Three rings of pillars erupt outward from the caster. */
    private boolean worldBreaker(Player player, Release release) {
        Location center = player.getLocation();
        BlockData block = release.theme.pillar.createBlockData();
        Set<UUID> hit = new HashSet<>();
        double[] radii = {2.5, 4.5, 6.5};
        Location floor = groundAt(center);
        Vfx.crater(floor != null ? floor : center, 9, 140);
        for (int ring = 0; ring < radii.length; ring++) {
            double radius = radii[ring];
            double height = 2.0 + ring * 0.6;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Vfx.sound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.6f);
                Vfx.sound(center, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.5f);
                int count = (int) (radius * 2.2);
                for (int i = 0; i < count; i++) {
                    double a = i * (Math.PI * 2 / count);
                    Location ground = groundAt(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
                    if (ground == null) {
                        continue;
                    }
                    raisePillar(ground, block, height);
                    KitFx.pillar(release, ground, height, i);
                    ground.getWorld().spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.2, 0), 10, 0.4, 0.2, 0.4,
                            0.1, block);
                    for (LivingEntity entity : ground.getNearbyLivingEntities(1.7, 2.5)) {
                        if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                            hit(player, entity, 7.0, release);
                            entity.setVelocity(outward(center, entity).multiply(0.3).setY(1.0));
                        }
                    }
                }
                Vfx.shockwave(plugin, center, release.form.color(), radius + 1, 6);
            }, ring * 6L);
        }
        return true;
    }

    /** An eight-second zone: allies inside regenerate and resist, enemies are slowed and worn down. */
    private boolean sanctum(Player player, Release release) {
        Location center = player.getLocation();
        Vfx.sound(center, Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
        Vfx.sound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f);
        // A dome of light over the zone, with its rings of power turning slowly inside.
        Vfx.model(VfxKind.ORB, center, release.form.color()).tone(2)
                .pose(new Quaternionf(), Vfx.scale(.5))
                .to(1, 10, new Quaternionf().rotateY(1f), Vfx.scale(11, 9, 11))
                .to(11, 160, new Quaternionf().rotateY(5f), Vfx.scale(11.5, 9.2, 11.5))
                .life(172, 10);
        for (int layer = 0; layer < 4; layer++) {
            double h = layer * 1.2;
            double r = Math.sqrt(Math.max(0.5, 36 - h * h * 2.2));
            float turn = layer % 2 == 0 ? 6f : -6f;
            Vfx.model(VfxKind.RING, center.clone().add(0, 0.2 + h, 0), release.form.color())
                    .pose(new Quaternionf(), Vfx.scale(.3, 1, .3))
                    .to(1 + layer * 3, 8, new Quaternionf().rotateY(turn * .1f), Vfx.scale(r, 1.2, r))
                    .to(10 + layer * 3, 150, new Quaternionf().rotateY(turn), Vfx.scale(r, 1.2, r))
                    .life(172, 10);
        }
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 160) {
                    cancel();
                    return;
                }
                for (int layer = 0; layer < 4; layer++) {
                    double h = layer * 1.2;
                    double r = Math.sqrt(Math.max(0.5, 36 - h * h * 2.2));
                    int points = (int) (r * 6);
                    for (int i = 0; i < points; i++) {
                        double a = i * (Math.PI * 2 / points);
                        Vfx.fallbackDust(center.clone().add(Math.cos(a) * r, 0.2 + h, Math.sin(a) * r),
                                release.form.dust(1.2f));
                    }
                }
                accent(release, center.clone().add(0, 1, 0), 10, 3, 1.5, 3, 0.02);
                for (Player ally : center.getNearbyPlayers(6)) {
                    if (ally.equals(player) || (AwakeningService.canPlay(ally) && AwakeningPowers.allies(player, ally))) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 1, false, true, true));
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, false, true, true));
                    }
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(6)) {
                    if (service.canStrike(player, entity)) {
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                        if (tick % 20 == 0) {
                            entity.setNoDamageTicks(0);
                            hit(player, entity, 2.0, release);
                        }
                    }
                }
                if (tick % 40 == 0) {
                    Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
                }
                tick += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static Vector horizontal(Player player) {
        Vector forward = player.getLocation().getDirection().setY(0);
        return forward.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : forward.normalize();
    }

    /** The standing spot on the ground at or just below/above a point, or null if there is none nearby. */
    static Location groundAt(Location point) {
        Block block = point.getBlock();
        for (int dy = 2; dy >= -4; dy--) {
            Block candidate = block.getRelative(0, dy, 0);
            if (candidate.getType().isSolid() && candidate.getRelative(0, 1, 0).isPassable()) {
                return candidate.getLocation().add(0.5, 1, 0.5);
            }
        }
        return null;
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
