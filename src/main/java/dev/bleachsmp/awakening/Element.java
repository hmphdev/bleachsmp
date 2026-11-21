package dev.bleachsmp.awakening;

import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import dev.bleachsmp.fx.VfxKind;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * The element of a released form. It layers its own particles and sounds over everything the form does, so two
 * forms with the same technique still look and sound different. Advanced forms turn their element up: hits leave a
 * lingering hazard, and the transformation gets an element-specific finale.
 */
enum Element {
    FIRE("Fire", Particle.FLAME, Particle.LAVA, Material.MAGMA_BLOCK, Color.fromRGB(255, 120, 20),
            Sound.ITEM_FIRECHARGE_USE, Sound.ENTITY_BLAZE_SHOOT, "sets enemies alight, burns hotter on burning targets, and leaves fire along its path"),
    ICE("Ice", Particle.SNOWFLAKE, Particle.ITEM_SNOWBALL, Material.PACKED_ICE, Color.fromRGB(170, 230, 255),
            Sound.BLOCK_POWDER_SNOW_BREAK, Sound.BLOCK_GLASS_BREAK, "roots the first enemy it hits in ice and leaves frost along its path"),
    LIGHTNING("Lightning", Particle.ELECTRIC_SPARK, Particle.FIREWORK, Material.LIGHT_BLUE_CONCRETE,
            Color.fromRGB(150, 220, 255), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,
            "arcs to nearby enemies, stunning them"),
    WIND("Wind", Particle.SMALL_GUST, Particle.CLOUD, Material.WHITE_WOOL, Color.fromRGB(220, 255, 240),
            Sound.ENTITY_BREEZE_SHOOT, Sound.ENTITY_BREEZE_WIND_BURST, "blasts enemies far away, at slightly less damage"),
    SHADOW("Shadow", Particle.SQUID_INK, Particle.LARGE_SMOKE, Material.BLACK_CONCRETE, Color.fromRGB(25, 15, 35),
            Sound.ENTITY_WITHER_SHOOT, Sound.ENTITY_PHANTOM_BITE, "passes through walls, hits harder from behind, and blinds players"),
    WATER("Water", Particle.SPLASH, Particle.BUBBLE_POP, Material.BLUE_STAINED_GLASS, Color.fromRGB(40, 120, 230),
            Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, Sound.ENTITY_GENERIC_SPLASH, "drags enemies toward you"),
    BLOOD("Blood", Particle.CRIMSON_SPORE, Particle.DAMAGE_INDICATOR, Material.REDSTONE_BLOCK,
            Color.fromRGB(140, 0, 15), Sound.ENTITY_EVOKER_FANGS_ATTACK, Sound.ENTITY_PLAYER_ATTACK_CRIT,
            "steals life with every hit"),
    PETAL("Petal", Particle.CHERRY_LEAVES, Particle.SPORE_BLOSSOM_AIR, Material.PINK_PETALS,
            Color.fromRGB(255, 170, 200), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, Sound.BLOCK_CHERRY_LEAVES_BREAK,
            "keeps cutting with a swarm of petals after each hit"),
    PALE_PETAL("Pale Petal", Particle.PALE_OAK_LEAVES, Particle.WHITE_ASH, Material.PALE_MOSS_BLOCK,
            Color.fromRGB(215, 210, 205), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, Sound.BLOCK_CHERRY_LEAVES_BREAK,
            "keeps cutting with a swarm of pale leaves after each hit"),
    EARTH("Earth", Particle.BLOCK, Particle.DUST_PLUME, Material.COARSE_DIRT, Color.fromRGB(120, 85, 55),
            Sound.BLOCK_GRAVEL_BREAK, Sound.ENTITY_IRON_GOLEM_DAMAGE, "launches enemies with erupting rock"),
    LIGHT("Light", Particle.END_ROD, Particle.GLOW, Material.GLOWSTONE, Color.fromRGB(255, 250, 200),
            Sound.BLOCK_BEACON_POWER_SELECT, Sound.BLOCK_AMETHYST_BLOCK_CHIME, "marks enemies; the next hit on a mark lands harder"),
    POISON("Poison", Particle.SNEEZE, Particle.ITEM_SLIME, Material.SLIME_BLOCK, Color.fromRGB(110, 200, 40),
            Sound.ENTITY_LLAMA_SPIT, Sound.ENTITY_SLIME_SQUISH, "stacks venom: poisoned enemies start to wither, and it leaves toxic ground along its path"),
    SOUND("Sound", Particle.NOTE, Particle.ENCHANTED_HIT, Material.NOTE_BLOCK, Color.fromRGB(190, 120, 255),
            Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_BELL_USE, "rings out, hitting everything around the target");

    final String label;
    final Particle main;
    final Particle accent;
    final BlockData block;
    final Color tint;
    final Sound cast;
    final Sound impact;
    final String effect;

    Element(String label, Particle main, Particle accent, Material block, Color tint, Sound cast, Sound impact,
            String effect) {
        this.label = label;
        this.main = main;
        this.accent = accent;
        this.block = block.createBlockData();
        this.tint = tint;
        this.cast = cast;
        this.impact = impact;
        this.effect = effect;
    }

    /** Every form's element, by faction, playstyle and form variant. Shikai and Bankai of a variant share it. */
    static Element of(Faction faction, Playstyle style, int variant) {
        Element[] table = switch (faction) {
            // Akagetsu, Kagerō, Ryūsei, Hayate, Iwagane, Mamorigami / Homuramaru, Oboromaru, Amagumo, Kumokiri,
            // Iwaoni, Kusarigami
            case SOUL_REAPER -> variant == 0
                    ? new Element[] {BLOOD, SHADOW, LIGHT, WIND, EARTH, ICE}
                    : new Element[] {FIRE, PETAL, WATER, LIGHTNING, POISON, SOUND};
            // Lobo Carmesí, Espejismo, Halcón Negro, Viento Hueco, Coloso, Caparazón / Tiburón, Sombra Pálida,
            // Escorpión, Águila Hueca, Osario, Araña Blanca
            case HOLLOW -> variant == 0
                    ? new Element[] {BLOOD, LIGHT, SHADOW, WIND, FIRE, SOUND}
                    : new Element[] {WATER, PALE_PETAL, POISON, LIGHTNING, EARTH, ICE};
            // Assault, Illusion, Starshot, Flight, Bulwark, Haven / Rampage, Mirror, Deluge, Zenith, Pillar, Tether
            case QUINCY -> variant == 0
                    ? new Element[] {LIGHTNING, SHADOW, LIGHT, WIND, SOUND, PETAL}
                    : new Element[] {BLOOD, ICE, WATER, FIRE, EARTH, POISON};
            // Crimson Knuckle, Mirror Coat, Bullet Echo, Sky Runner, Iron Maker, Heart Lock / Rewind Blade,
            // Shuffle Deck, Echo Rain, Meteor Boots, Forge Ground, Lifeline
            case FULLBRINGER -> variant == 0
                    ? new Element[] {BLOOD, SHADOW, SOUND, WIND, EARTH, PETAL}
                    : new Element[] {LIGHT, POISON, WATER, LIGHTNING, FIRE, ICE};
        };
        return table[style.ordinal()];
    }

    // ---------------------------------------------------------------- particles

    private static void spawn(Particle particle, Element element, Location at, int count, double dx, double dy,
            double dz, double speed) {
        if (count <= 0) {
            return;
        }
        Class<?> type = particle.getDataType();
        if (type == Void.class) {
            Vfx.particle(particle, at, count, dx, dy, dz, speed, null);
        } else if (type == BlockData.class) {
            Vfx.particle(particle, at, count, dx, dy, dz, speed, element.block);
        }
    }

    /** The element's main particle. Wind and lightning move fast, so they get a little less speed. */
    void main(Location at, int count, double dx, double dy, double dz, double speed) {
        spawn(main, this, at, count, dx, dy, dz, speed);
    }

    void accent(Location at, int count, double dx, double dy, double dz, double speed) {
        spawn(accent, this, at, count, dx, dy, dz, speed);
    }

    /** Layered over a technique's trail or burst wherever the faction particle used to be the only one. */
    void layer(Location at, int count, double dx, double dy, double dz, double speed) {
        main(at, Math.max(1, count), dx, dy, dz, Math.min(speed, 0.08));
        if (count >= 3) {
            accent(at, Math.max(1, count / 3), dx, dy, dz, Math.min(speed, 0.05));
        }
        if (count >= 4) {
            Vfx.particle(Particle.DUST, at, Math.max(1, count / 4), dx, dy, dz, 0, Vfx.dust(tint, 1.3f));
        }
    }

    /** An element-coloured burst for impacts. */
    void burst(Location at, double scale) {
        int count = (int) (10 * scale);
        main(at, count, 0.35 * scale, 0.35 * scale, 0.35 * scale, 0.04);
        accent(at, Math.max(2, count / 2), 0.3 * scale, 0.3 * scale, 0.3 * scale, 0.05);
        Vfx.burst(at, tint, Math.max(3, count / 2), 0.3 * scale, 1.4f);
        shapes(at, scale);
        if (this == LIGHTNING) {
            // A short jagged arc out of the impact.
            Location point = at.clone();
            for (int i = 0; i < 5; i++) {
                Location next = point.clone().add(rand(0.7 * scale), rand(0.5 * scale) + 0.2, rand(0.7 * scale));
                Vfx.fallbackLine(point, next, tint, 0.8f, 0.2);
                point = next;
            }
        }
    }

    private static double rand(double spread) {
        return (Math.random() * 2 - 1) * spread;
    }

    // ---------------------------------------------------------------- shapes (resource-pack models)

    /** Lighter or darker shades of the element's colour, for layered shapes. */
    Color shade(double factor) {
        return Color.fromRGB(Math.clamp((int) (tint.getRed() * factor), 0, 255),
                Math.clamp((int) (tint.getGreen() * factor), 0, 255), Math.clamp((int) (tint.getBlue() * factor), 0, 255));
    }

    /** The element's impact, in shapes: what an element looks like when it lands. */
    void shapes(Location at, double scale) {
        Location ground = ReleaseService.groundAt(at);
        switch (this) {
            case FIRE -> {
                for (int i = 0; i < 3 + (int) (scale * 2); i++) {
                    Vfx.flame(at.clone().add(rand(.5 * scale), -.4, rand(.5 * scale)),
                            i % 2 == 0 ? tint : Color.fromRGB(255, 200, 80), .9 + scale * .5, 10);
                }
                Vfx.scatter(VfxKind.DROP, at, Color.fromRGB(255, 160, 40), (int) (3 * scale), .9 * scale, .7, 10);
            }
            case ICE -> {
                Vfx.scatter(VfxKind.SHARD, at, tint, 3 + (int) (scale * 2), .9 * scale, .45, 12);
                if (ground != null) {
                    Vfx.erupt(VfxKind.SHARD, ground, tint, 3 + (int) scale * 2, .5 * scale, .8 + scale * .5, 22);
                }
            }
            case LIGHTNING -> {
                for (int i = 0; i < 3 + (int) scale; i++) {
                    Vfx.zap(at, at.clone().add(rand(1.6 * scale), rand(1.2 * scale), rand(1.6 * scale)), tint, .1);
                }
            }
            case WIND -> {
                Vfx.whirl(at.clone().subtract(0, .3, 0), tint, 1 + scale * .5, 8);
                Vfx.gust(at, new Vector(rand(1), 0, rand(1)), tint, 1.5 * scale, 8);
            }
            case SHADOW -> {
                Vfx.smoke(at, tint, 3 + (int) (scale * 2), .5 * scale, .8 + scale * .4, 16);
                Vfx.smoke(at, Color.fromRGB(70, 0, 30), 1, .3, .6 + scale * .3, 12);
            }
            case WATER -> {
                Vfx.drops(at, tint, 4 + (int) (scale * 3), .9 * scale, 12);
                if (ground != null) {
                    Vfx.model(VfxKind.RING, ground.clone().add(0, .08, 0), shade(1.3))
                            .pose(new Quaternionf(), Vfx.scale(.3, .5, .3))
                            .grow(1, 8, Vfx.scale(1.2 + scale, .5, 1.2 + scale)).life(10, 5);
                }
            }
            case BLOOD -> {
                Vfx.drops(at, tint, 5 + (int) (scale * 3), 1.1 * scale, 12);
                Vfx.model(VfxKind.CLAW, at, shade(1.6))
                        .pose(Vfx.facing(new Vector(rand(1), 0, rand(1))), Vfx.scale(.4 * scale))
                        .grow(1, 2, Vfx.scale(1.1 * scale)).life(7, 4);
            }
            case PETAL, PALE_PETAL -> Vfx.petals(at, tint, 6 + (int) (scale * 4), 1.4 * scale, 18);
            case EARTH -> {
                Vfx.scatter(VfxKind.ROCK, at, tint, 3 + (int) (scale * 2), 1.0 * scale, .8, 14);
                if (ground != null) {
                    Vfx.erupt(VfxKind.SPIKE, ground, tint, 3 + (int) scale * 2, .6 * scale, .9 + scale * .5, 22);
                }
            }
            case LIGHT -> {
                Vfx.glyph(VfxKind.STAR, at, tint, .6 * scale, 10);
                Vfx.model(VfxKind.RING, at, Color.WHITE)
                        .pose(Vfx.facing(new Vector(0, 1, 0)).rotateX(1.2f), Vfx.scale(.3))
                        .grow(1, 6, Vfx.scale(1.4 * scale)).life(8, 4);
            }
            case POISON -> {
                Vfx.smoke(at, tint, 2 + (int) scale, .6 * scale, .7 + scale * .3, 20);
                Vfx.drops(at, shade(.7), 3 + (int) (scale * 2), .8 * scale, 12);
            }
            case SOUND -> {
                Vfx.glyph(VfxKind.NOTE, at.clone().add(rand(.6), .2, rand(.6)), tint, .5 * scale, 14);
                for (int i = 0; i < 2; i++) {
                    Vfx.model(VfxKind.RING, at, tint).tone(i)
                            .pose(Vfx.facing(new Vector(rand(1), rand(1), rand(1))), Vfx.scale(.2))
                            .grow(1 + i * 2, 6, Vfx.scale((1.2 + i * .8) * scale)).life(9 + i * 2, 4);
                }
            }
        }
    }

    /** The element rising around a player at the peak of a release. */
    void rise(Location base, double scale) {
        Location body = base.clone().add(0, 1, 0);
        int big = (int) Math.round(scale * 4);
        switch (this) {
            case FIRE -> {
                for (int i = 0; i < 6 + big; i++) {
                    double a = i * Math.PI * 2 / (6 + big);
                    Vfx.flame(base.clone().add(Math.cos(a) * 1.8 * scale, 0, Math.sin(a) * 1.8 * scale),
                            i % 3 == 0 ? Color.fromRGB(255, 210, 90) : tint, 1.6 + scale, 16);
                }
            }
            case ICE -> Vfx.erupt(VfxKind.SHARD, base, tint, 6 + big, 1.8 * scale, 1.4 + scale, 40);
            case LIGHTNING -> {
                for (int i = 0; i < 4 + big; i++) {
                    Vfx.zap(body, body.clone().add(rand(2.5 * scale), rand(1.5 * scale) + .5, rand(2.5 * scale)), tint, .14);
                }
            }
            case WIND -> {
                Vfx.whirl(base, tint, 1.6 * scale, 16);
                Vfx.whirl(base.clone().add(0, 1.4, 0), Color.WHITE, 1.2 * scale, 14);
            }
            case SHADOW -> Vfx.smoke(body, tint, 6 + big, 1.4 * scale, 1.3 * scale, 26);
            case WATER -> {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2;
                    Vector out = new Vector(Math.cos(a), 0, Math.sin(a));
                    Vfx.wave(base.clone().add(out.clone().multiply(.6)), out, tint, 1.8 * scale, 1.2 * scale, 2.5 * scale, 14);
                }
                Vfx.drops(body, shade(1.3), 6 + big, 1.2 * scale, 16);
            }
            case BLOOD -> {
                Vfx.drops(body, tint, 10 + big * 2, 1.4 * scale, 16);
                Vfx.pillar(base, shade(.6), .9 * scale, 3 * scale, 6, 6);
            }
            case PETAL, PALE_PETAL -> Vfx.petals(body, tint, 20 + big * 4, 2.6 * scale, 30);
            case EARTH -> {
                Vfx.erupt(VfxKind.SPIKE, base, tint, 6 + big, 2.0 * scale, 1.4 + scale, 36);
                Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .3, 0), tint, 4 + big, 1.4 * scale, 1.0, 18);
            }
            case LIGHT -> {
                Vfx.sigil(VfxKind.SIGIL, base, tint, 2 * scale, 34);
                Vfx.pillar(base, tint, .5 * scale, 6 * scale, 8, 10);
            }
            case POISON -> {
                Vfx.smoke(base.clone().add(0, .4, 0), tint, 6 + big, 1.8 * scale, 1.2 * scale, 30);
                Vfx.drops(body, shade(.6), 4 + big, 1.0 * scale, 14);
            }
            case SOUND -> {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Vfx.glyph(VfxKind.NOTE, body.clone().add(Math.cos(a) * 1.6, .6, Math.sin(a) * 1.6), tint, .6, 20);
                }
            }
        }
    }

    /** A little of the element hanging around an advanced form. */
    private void auraShapes(Location base, int tick) {
        Location body = base.clone().add(rand(.5), .3 + Math.random() * 1.4, rand(.5));
        switch (this) {
            case FIRE -> Vfx.flame(base.clone().add(rand(.5), 0, rand(.5)), tint, 1.0, 10);
            case ICE -> Vfx.scatter(VfxKind.SHARD, body, tint, 1, .3, .25, 12);
            case LIGHTNING -> Vfx.zap(body, body.clone().add(rand(.9), rand(.9), rand(.9)), tint, .06);
            case WIND -> Vfx.whirl(base.clone().add(0, .1, 0), tint, .9, 8);
            case SHADOW -> Vfx.smoke(base.clone().add(0, .2, 0), tint, 1, .4, .7, 14);
            case WATER, BLOOD -> Vfx.drops(body, tint, 1, .3, 10);
            case PETAL, PALE_PETAL -> Vfx.petals(body, tint, 2, .9, 16);
            case EARTH -> {
                if (tick % 18 == 0) {
                    Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .1, 0), tint, 1, .4, .4, 10);
                }
            }
            case LIGHT -> Vfx.glyph(VfxKind.STAR, body, tint, .2, 10);
            case POISON -> Vfx.smoke(base.clone().add(0, .3, 0), tint, 1, .4, .5, 16);
            case SOUND -> {
                if (tick % 18 == 0) {
                    Vfx.glyph(VfxKind.NOTE, body.add(0, .5, 0), tint, .35, 14);
                }
            }
        }
    }

    /** Shadow techniques pass through walls. */
    boolean phases() {
        return this == SHADOW;
    }

    void castSound(Location at, boolean advanced) {
        Vfx.sound(at, cast, this == LIGHTNING ? 0.35f : 0.8f, advanced ? 0.8f : 1.1f);
    }

    void impactSound(Location at) {
        Vfx.sound(at, impact, 0.7f, 0.9f + (float) Math.random() * 0.3f);
    }

    /** The element's own on-hit flavour. Kept light: the faction effect is still the main one. */
    void onHit(Player caster, LivingEntity target) {
        switch (this) {
            case FIRE -> target.setFireTicks(Math.max(target.getFireTicks(), 40));
            case ICE -> target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 40, target.getFreezeTicks() + 60));
            case LIGHTNING -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 2));
            case WIND -> { }
            case SHADOW -> {
                if (target instanceof Player) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0));
                }
            }
            case WATER -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
            case BLOOD -> target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 1));
            case EARTH -> target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 0));
            case LIGHT -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
            case POISON -> target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
            case SOUND -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 0));
            case PETAL, PALE_PETAL -> { }
        }
    }

    // ---------------------------------------------------------------- set pieces

    /** Ambient particles around a released player. Advanced forms are denser. */
    void aura(Location base, int tick, boolean advanced) {
        if (advanced && tick % 6 == 0) {
            auraShapes(base, tick);
        }
        if (tick % 4 != 0) {
            return;
        }
        Location body = base.clone().add(0, 1, 0);
        switch (this) {
            case FIRE -> {
                main(base.clone().add(0, 0.1, 0), advanced ? 4 : 2, 0.35, 0.05, 0.35, 0.02);
                if (advanced) {
                    accent(body, 1, 0.3, 0.4, 0.3, 0);
                }
            }
            case ICE -> {
                main(body, advanced ? 4 : 2, 0.5, 0.7, 0.5, 0.01);
                Vfx.ring(base.clone().add(0, 0.05, 0), advanced ? 1.2 : 0.8, advanced ? 14 : 8, tint, 0.9f);
            }
            case LIGHTNING -> {
                main(body, advanced ? 5 : 3, 0.45, 0.7, 0.45, 0.05);
                if (advanced && tick % 12 == 0) {
                    burst(body, 0.8);
                }
            }
            case WIND -> {
                double a = tick * 0.5;
                for (int i = 0; i < (advanced ? 3 : 2); i++) {
                    double angle = a + i * Math.PI * 2 / 3;
                    at(base, Math.cos(angle) * 1.1, 0.2 + i * 0.6, Math.sin(angle) * 1.1, Particle.CLOUD);
                }
                if (advanced) {
                    main(base.clone().add(0, 0.2, 0), 1, 0.6, 0, 0.6, 0);
                }
            }
            case SHADOW -> {
                main(base.clone().add(0, 0.2, 0), advanced ? 4 : 2, 0.45, 0.1, 0.45, 0.01);
                accent(body, advanced ? 2 : 1, 0.3, 0.5, 0.3, 0.01);
            }
            case WATER -> {
                main(body, advanced ? 6 : 3, 0.45, 0.6, 0.45, 0.05);
                accent(body, advanced ? 3 : 1, 0.45, 0.6, 0.45, 0.02);
            }
            case BLOOD -> {
                main(body, advanced ? 5 : 3, 0.45, 0.6, 0.45, 0.01);
                if (advanced) {
                    at(base, 0, 0.1, 0, Particle.DRIPPING_LAVA);
                }
            }
            case PETAL, PALE_PETAL -> {
                double a = tick * 0.25;
                for (int i = 0; i < (advanced ? 4 : 2); i++) {
                    double angle = a + i * Math.PI / 2;
                    main(base.clone().add(Math.cos(angle) * 1.3, 1.6, Math.sin(angle) * 1.3), 1, 0.1, 0.1, 0.1, 0);
                }
            }
            case EARTH -> {
                main(base.clone().add(0, 0.1, 0), advanced ? 4 : 2, 0.4, 0.05, 0.4, 0);
                if (advanced && tick % 8 == 0) {
                    accent(base.clone().add(0, 0.1, 0), 2, 0.5, 0, 0.5, 0.01);
                }
            }
            case LIGHT -> {
                main(body, advanced ? 3 : 1, 0.4, 0.6, 0.4, 0.01);
                if (advanced) {
                    Vfx.ring(base.clone().add(0, 2.4, 0), 0.45, 12, tint, 0.9f);
                }
            }
            case POISON -> {
                main(base.clone().add(0, 0.3, 0), advanced ? 4 : 2, 0.45, 0.2, 0.45, 0.01);
                if (advanced) {
                    accent(body, 1, 0.4, 0.5, 0.4, 0);
                }
            }
            case SOUND -> {
                double angle = tick * 0.4;
                at(base, Math.cos(angle) * 1.0, 1.9, Math.sin(angle) * 1.0, Particle.NOTE);
                if (advanced) {
                    at(base, -Math.cos(angle) * 1.0, 1.9, -Math.sin(angle) * 1.0, Particle.NOTE);
                    if (tick % 16 == 0) {
                        Vfx.ring(base.clone().add(0, 1, 0), 1.6, 18, tint, 0.9f);
                    }
                }
            }
        }
    }

    private static void at(Location base, double x, double y, double z, Particle particle) {
        base.getWorld().spawnParticle(particle, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
    }

    /** The finale of a release: a short element-specific eruption around the player. */
    void eruption(Location base, boolean advanced) {
        double scale = advanced ? 1.8 : 1.0;
        Location body = base.clone().add(0, 1, 0);
        burst(body, 2.0 * scale);
        rise(base, scale);
        castSound(base, advanced);
        switch (this) {
            case FIRE -> {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    main(base.clone().add(Math.cos(a) * 2.5 * scale, 0.1, Math.sin(a) * 2.5 * scale), 3, 0.05, 0.4, 0.05, 0.05);
                }
                accent(base, (int) (14 * scale), 1.0 * scale, 0.3, 1.0 * scale, 0);
                Vfx.sound(base, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.6f);
            }
            case ICE -> {
                base.getWorld().spawnParticle(Particle.BLOCK, body, (int) (40 * scale), 1.2 * scale, 0.8, 1.2 * scale,
                        0, block);
                main(body, (int) (60 * scale), 2.0 * scale, 1.5, 2.0 * scale, 0.02);
                Vfx.sound(base, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
            }
            case LIGHTNING -> {
                for (int bolt = 0; bolt < (advanced ? 5 : 3); bolt++) {
                    Location strike = base.clone().add(rand(3 * scale), 0, rand(3 * scale));
                    Location point = strike.clone().add(0, 12, 0);
                    while (point.getY() > strike.getY()) {
                        Location next = point.clone().add(rand(0.6), -1.2, rand(0.6));
                        Vfx.fallbackLine(point, next, tint, 1.3f, 0.25);
                        point = next;
                    }
                    burst(strike.add(0, 0.3, 0), 1.0);
                }
                Vfx.sound(base, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.6f);
            }
            case WIND -> {
                base.getWorld().spawnParticle(advanced ? Particle.GUST_EMITTER_LARGE : Particle.GUST_EMITTER_SMALL,
                        body, 1);
                for (int h = 0; h < 12; h++) {
                    double a = h * 0.8;
                    double r = 0.6 + h * 0.18 * scale;
                    at(base, Math.cos(a) * r, h * 0.35, Math.sin(a) * r, Particle.CLOUD);
                }
            }
            case SHADOW -> {
                main(body, (int) (80 * scale), 2.2 * scale, 1.2, 2.2 * scale, 0.02);
                Vfx.ring(base.clone().add(0, 0.1, 0), 3 * scale, (int) (30 * scale), tint, 2.0f);
                Vfx.sound(base, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 0.6f);
            }
            case WATER -> {
                for (int i = 0; i < 20; i++) {
                    double a = i * Math.PI / 10;
                    for (double h = 0; h < 2.5 * scale; h += 0.5) {
                        main(base.clone().add(Math.cos(a) * 1.8, h, Math.sin(a) * 1.8), 1, 0.05, 0.05, 0.05, 0);
                    }
                }
                base.getWorld().spawnParticle(Particle.FALLING_WATER, body.clone().add(0, 2, 0), (int) (60 * scale),
                        1.5, 0.5, 1.5, 0);
                Vfx.sound(base, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.6f);
            }
            case BLOOD -> {
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    Vfx.fallbackLine(body, body.clone().add(Math.cos(a) * 2.5 * scale, rand(0.6), Math.sin(a) * 2.5 * scale),
                            tint, 1.1f, 0.3);
                }
                accent(body, (int) (10 * scale), 0.8, 0.5, 0.8, 0.1);
                Vfx.sound(base, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
            }
            case PETAL, PALE_PETAL -> {
                for (int h = 0; h < 30; h++) {
                    double a = h * 0.5;
                    double r = 0.5 + h * 0.08 * scale;
                    main(base.clone().add(Math.cos(a) * r, h * 0.12, Math.sin(a) * r), 2, 0.1, 0.1, 0.1, 0);
                }
                Vfx.sound(base, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.6f);
            }
            case EARTH -> {
                base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0, 0.3, 0), (int) (60 * scale),
                        1.8 * scale, 0.2, 1.8 * scale, 0, base.clone().subtract(0, 1, 0).getBlock().getType().isSolid()
                                ? base.clone().subtract(0, 1, 0).getBlock().getBlockData() : block);
                accent(base, (int) (20 * scale), 1.5 * scale, 0.2, 1.5 * scale, 0.02);
                Vfx.sound(base, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.5f);
            }
            case LIGHT -> {
                for (double h = 0; h < 8 * scale; h += 0.3) {
                    main(base.clone().add(0, h, 0), 1, 0.08, 0.05, 0.08, 0);
                }
                accent(body, (int) (20 * scale), 1.2, 1.0, 1.2, 0.02);
                Vfx.sound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
            }
            case POISON -> {
                main(base.clone().add(0, 0.4, 0), (int) (60 * scale), 2.2 * scale, 0.3, 2.2 * scale, 0.01);
                accent(body, (int) (12 * scale), 1.0, 0.6, 1.0, 0.02);
                Vfx.sound(base, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 0.6f);
            }
            case SOUND -> {
                for (int ring = 1; ring <= (advanced ? 4 : 2); ring++) {
                    Vfx.ring(body, ring * 1.1, 14 + ring * 6, tint, 1.2f);
                }
                Vfx.fallbackParticle(body, Particle.SONIC_BOOM, 1, 0, 0);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    at(base, Math.cos(a) * 2.0, 1.6, Math.sin(a) * 2.0, Particle.NOTE);
                }
                Vfx.sound(base, Sound.BLOCK_BELL_RESONATE, 1.0f, 1.2f);
            }
        }
    }

    /**
     * Advanced forms only: a hit leaves a short-lived patch of the element on the ground. It applies the element's
     * effect (not extra damage) to enemies standing in it.
     */
    void hazardTick(Location center, int tick) {
        double radius = 1.6;
        switch (this) {
            case FIRE -> main(center, 4, radius * 0.6, 0.05, radius * 0.6, 0.01);
            case ICE -> {
                Vfx.ring(center, radius, 12, tint, 1.0f);
                main(center.clone().add(0, 0.3, 0), 2, radius * 0.5, 0.2, radius * 0.5, 0);
            }
            case LIGHTNING -> {
                main(center, 4, radius * 0.6, 0.1, radius * 0.6, 0.05);
                if (tick % 6 == 0) {
                    burst(center.clone().add(0, 0.2, 0), 0.5);
                }
            }
            case WIND -> at(center, rand(radius), 0.2, rand(radius), Particle.SMALL_GUST);
            case SHADOW -> main(center, 3, radius * 0.6, 0.05, radius * 0.6, 0);
            case WATER -> {
                main(center, 4, radius * 0.6, 0.05, radius * 0.6, 0.1);
                accent(center, 2, radius * 0.5, 0.1, radius * 0.5, 0);
            }
            case BLOOD -> Vfx.burst(center, tint, 4, radius * 0.5, 1.2f);
            case PETAL, PALE_PETAL -> {
                double a = tick * 0.6;
                main(center.clone().add(Math.cos(a) * radius, 0.6, Math.sin(a) * radius), 2, 0.1, 0.1, 0.1, 0);
            }
            case EARTH -> center.getWorld().spawnParticle(Particle.BLOCK, center, 3, radius * 0.6, 0.05, radius * 0.6,
                    0, block);
            case LIGHT -> Vfx.ring(center, radius, 12, tint, 0.9f);
            case POISON -> main(center.clone().add(0, 0.2, 0), 3, radius * 0.6, 0.1, radius * 0.6, 0);
            case SOUND -> {
                if (tick % 4 == 0) {
                    Vfx.ring(center.clone().add(0, 0.2, 0), radius * (0.4 + (tick % 12) / 12.0), 12, tint, 1.0f);
                }
            }
        }
    }
}
