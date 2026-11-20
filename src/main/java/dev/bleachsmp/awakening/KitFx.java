package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.awakening.ReleaseService.Release;
import dev.bleachsmp.awakening.ReleaseService.Theme;
import dev.bleachsmp.fx.Effect;
import dev.bleachsmp.fx.Vfx;
import dev.bleachsmp.fx.VfxKind;
import java.util.function.IntConsumer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Each kit's own look: the release of its first form (shikai, resurrección, Schrift, Fullbring), the entrance of its
 * advanced form (Bankai, Segunda Etapa, Vollständig, Complete Fullbring), and what hangs around the form while it is
 * out. Every kit has its own choreography, built from the shapes in {@link VfxKind}. Only players with the resource
 * pack see these; the faction's particle animation still plays for everyone else.
 */
final class KitFx {
    static final Color BLACK = Color.fromRGB(14, 10, 16);
    static final Color WHITE = Color.WHITE;
    static final Color BONE = Color.fromRGB(236, 228, 206);
    static final Color GOLD = Color.fromRGB(255, 205, 70);
    static final Color ICE = Color.fromRGB(170, 225, 255);
    static final Color FLAME = Color.fromRGB(255, 120, 25);
    static final Color EMBER = Color.fromRGB(255, 205, 90);
    static final Color SAKURA = Color.fromRGB(255, 160, 195);
    static final Color VENOM = Color.fromRGB(120, 210, 50);
    static final Color CERO_GREEN = Color.fromRGB(70, 220, 120);

    private static BleachSmpPlugin plugin;
    private static ReleaseService releases;

    private KitFx() {
    }

    static void init(BleachSmpPlugin owner, ReleaseService service) {
        plugin = owner;
        releases = service;
    }

    /** Whether {@code release} is still the player's active form, so lingering visuals know to stop. */
    static boolean live(Player p, Release release) {
        return p.isOnline() && releases != null && releases.get(p) == release;
    }

    static BleachSmpPlugin plugin() {
        return plugin;
    }

    // ================================================================ timing

    static void at(int delay, Runnable action) {
        if (plugin == null) {
            return;
        }
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "An ability effect failed", exception);
            }
        };
        if (delay <= 0) {
            safe.run();
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, safe, delay);
        }
    }

    /** Runs {@code step} with tick 0, period, 2 × period, ... while the tick is below {@code ticks}. */
    static void every(int ticks, int period, IntConsumer step) {
        if (plugin == null) {
            return;
        }
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick >= ticks) {
                    cancel();
                    return;
                }
                step.accept(tick);
                tick += period;
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    // ================================================================ geometry

    static Vector forward(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    static Vector right(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        return new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    static Location ground(Player p) {
        Location ground = ReleaseService.groundAt(p.getLocation());
        return ground != null ? ground : p.getLocation();
    }

    /** How much bigger than normal the player is (Furia, Hoheitsrecht and other growths), for body-anchored shapes. */
    static double size(Player p) {
        var scale = p.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        return scale == null ? 1 : Math.max(.25, scale.getValue());
    }

    static double rand(double spread) {
        return (Math.random() * 2 - 1) * spread;
    }

    static Vector3f v(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    // ================================================================ set pieces

    /** A column of reiatsu, coloured around a white core. */
    static void column(Location base, Color color, double radius, double height, int rise, int hold) {
        Vfx.pillar(base, color, radius, height, rise, hold);
    }

    /** A dark core inside a column, turning against it. */
    static void core(Location base, Color color, double radius, double height, int life) {
        Vfx.model(VfxKind.PILLAR, base, color)
                .pose(new Quaternionf(), Vfx.scale(radius, .1, radius))
                .to(1, life / 2, new Quaternionf().rotateY(-2.5f), Vfx.scale(radius, height, radius))
                .to(life / 2 + 1, life / 2, new Quaternionf().rotateY(-5f), Vfx.scale(radius * .3, height * 1.1, radius * .3))
                .life(life + 2, 8);
    }

    /** Models spiralling up around a player: petals, ash, flames, feathers, pages. */
    static void swarm(Player p, VfxKind kind, Color color, int count, double radius, double height, double size,
            int ticks) {
        Location base = p.getLocation();
        for (int i = 0; i < count; i++) {
            double start = Math.random() * Math.PI * 2;
            double r = radius * (.6 + Math.random() * .6);
            double h0 = Math.random() * .4;
            double turns = 1.2 + Math.random() * .8;
            int steps = 4;
            int stepTicks = Math.max(1, ticks / steps);
            Vector3f scale = Vfx.scale(size * (.7 + Math.random() * .6));
            Effect effect = Vfx.model(kind, base, color)
                    .pose(v(Math.cos(start) * r * .4, h0, Math.sin(start) * r * .4),
                            new Quaternionf().rotateXYZ((float) rand(3), (float) rand(3), 0), scale);
            for (int s = 1; s <= steps; s++) {
                double t = s / (double) steps;
                double a = start + t * turns * Math.PI * 2;
                double rr = r * (.5 + t * .6);
                effect.to(1 + (s - 1) * stepTicks, stepTicks, v(Math.cos(a) * rr, h0 + t * height, Math.sin(a) * rr),
                        new Quaternionf().rotateY((float) -a).rotateX((float) (t * 4 + i)), scale);
            }
            effect.life(ticks + 3, Math.max(4, ticks / 4));
        }
    }

    /** Models placed in a ring around {@code center} that turn {@code spin} radians over their life. */
    static void crown(VfxKind kind, Location center, Color color, int count, double radius, double size, int ticks,
            double spin, boolean faceOut) {
        for (int i = 0; i < count; i++) {
            double a0 = i * Math.PI * 2 / count;
            Vector3f scale = Vfx.scale(size);
            int steps = 4;
            int stepTicks = Math.max(1, (ticks - 4) / steps);
            Effect effect = Vfx.model(kind, center, color)
                    .pose(v(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), turn(a0, faceOut), Vfx.scale(size * .2));
            effect.to(1, 3, v(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), turn(a0, faceOut), scale);
            for (int s = 1; s <= steps; s++) {
                double a = a0 + spin * s / steps;
                effect.to(4 + (s - 1) * stepTicks, stepTicks, v(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                        turn(a, faceOut), scale);
            }
            effect.life(ticks, Math.max(4, ticks / 4));
        }
    }

    private static Quaternionf turn(double a, boolean faceOut) {
        Quaternionf rotation = Vfx.facing(new Vector(Math.cos(a), 0, Math.sin(a)));
        return faceOut ? rotation : rotation.rotateY((float) Math.PI);
    }

    /** Models flying in from a ring to {@code center}. */
    static void converge(VfxKind kind, Location center, Color color, int count, double radius, double size, int ticks) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + rand(.2);
            Location from = center.clone().add(Math.cos(a) * radius, rand(radius * .4), Math.sin(a) * radius);
            Vfx.fly(kind, from, center, color, Vfx.scale(size), ticks, 2);
        }
    }

    /** Models flying out from {@code center} in a ring (or a sphere, with {@code lift}). */
    static void radiate(VfxKind kind, Location center, Color color, int count, double radius, double size, int ticks,
            double lift) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + rand(.15);
            Location to = center.clone().add(Math.cos(a) * radius, rand(lift), Math.sin(a) * radius);
            Vfx.fly(kind, center, to, color, Vfx.scale(size), ticks, 4);
        }
    }

    /** A crescent slash in a vertical plane, sweeping from below to above, facing {@code forward}. */
    static void upcut(Location at, Vector forward, Color color, double size, int ticks, double roll) {
        Quaternionf aim = Vfx.facing(forward).rotateZ((float) (Math.PI / 2 + roll));
        Vfx.model(VfxKind.SLASH, at, color)
                .pose(aim, Vfx.scale(size * .5, 1.2, size * .5))
                .to(1, 3, aim, Vfx.scale(size, 1.5, size * 1.1))
                .to(4, ticks, new Vector3f(forward.toVector3f()).normalize().mul((float) size * .8f), aim,
                        Vfx.scale(size * 1.2, 1.2, size * 1.3))
                .life(ticks + 4, Math.max(4, ticks / 2));
    }

    /** Two crescents crossing in an X. */
    static void cross(Location at, Vector forward, Color color, double size, int ticks) {
        upcut(at, forward, color, size, ticks, .75);
        upcut(at, forward, color, size, ticks, -.75);
    }

    /** A pair of wings spread from a player's back, held still for {@code ticks}. */
    static void wings(Player p, Color color, double size, int ticks, double spread) {
        Vector fwd = forward(p);
        Vector side = right(p);
        for (int s = -1; s <= 1; s += 2) {
            Location at = p.getLocation().add(0, 1.35, 0).add(fwd.clone().multiply(-.3)).add(side.clone().multiply(s * .12));
            Quaternionf pose = Vfx.facing(fwd).rotateY((float) (-s * .35)).rotateZ((float) (-s * spread));
            Vector3f scale = Vfx.scale(s > 0 ? -size : size, size, size);
            Vfx.model(VfxKind.WING, at, color)
                    .pose(new Vector3f(), pose, new Vector3f(scale).mul(.2f))
                    .to(1, 5, new Vector3f(), pose, scale)
                    .life(ticks, Math.max(4, ticks / 3));
        }
    }

    /** A beast's head of reiatsu rising behind a player and lunging forward with a roar. */
    static void head(Player p, Color color, double size, int ticks, double behind, double height) {
        Vector fwd = forward(p);
        Location at = p.getLocation().add(fwd.clone().multiply(-behind)).add(0, height, 0);
        Quaternionf aim = Vfx.facing(fwd.clone().setY(.25));
        Vfx.model(VfxKind.HEAD, at, color)
                .pose(v(0, -height * .6, 0), aim, Vfx.scale(size * .3))
                .to(1, ticks / 3, v(0, 0, 0), aim, Vfx.scale(size))
                .to(ticks / 3 + 2, ticks / 3, fwd.clone().multiply(behind + 1.5).toVector3f(),
                        Vfx.facing(fwd.clone().setY(-.1)), Vfx.scale(size * 1.15))
                .life(ticks, Math.max(4, ticks / 3));
    }

    /** A Hollow's mask forms over the face and bursts apart. */
    static void maskBreak(Player p, int delay) {
        Vector fwd = forward(p);
        Location face = p.getEyeLocation().add(fwd.clone().multiply(.45));
        Quaternionf aim = Vfx.facing(fwd);
        Vfx.model(VfxKind.MASK, face, WHITE).pose(aim, Vfx.scale(.4)).grow(1, 4, Vfx.scale(1.2)).life(delay + 1, 2);
        at(delay, () -> {
            Vfx.scatter(VfxKind.FANG, face, BONE, 7, 1.4, .6, 16);
            Vfx.burst(face, BONE, 20, .3, 1.4f);
        });
    }

    /** The Quincy pentacle under the feet, and the Schrift's halo of light above the head. */
    static void quincySeal(Player p, Color color, double radius, int ticks) {
        Vfx.sigil(VfxKind.SIGIL, ground(p), color, radius, ticks);
        Vfx.model(VfxKind.RING, p.getLocation().add(0, 2.35, 0), WHITE)
                .pose(new Quaternionf(), Vfx.scale(.05, 1, .05))
                .to(3, 6, new Quaternionf().rotateY(2), Vfx.scale(.45, .8, .45))
                .life(ticks, 6);
    }

    /** Plates of the Fullbring flying in to lock onto the body. */
    static void armUp(Player p, Color color, int count, double size) {
        converge(VfxKind.PLATE, p.getLocation().add(0, 1.1, 0), color, count, 3.2, size, 8);
        at(9, () -> Vfx.burst(p.getLocation().add(0, 1.1, 0), color, 30, .4, 1.6f));
    }

    /** Models raining down from above an area. */
    static void rain(VfxKind kind, Location center, Color color, int count, double radius, double size, int ticks,
            double height) {
        for (int i = 0; i < count; i++) {
            int delay = (int) (Math.random() * ticks * .6);
            at(delay, () -> {
                double a = Math.random() * Math.PI * 2;
                double d = Math.sqrt(Math.random()) * radius;
                Location low = center.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                Location high = low.clone().add(rand(1), height, rand(1));
                Vfx.fly(kind, high, low, color, Vfx.scale(size), 6 + (int) (Math.random() * 4), 4);
            });
        }
    }

    /** Glyphs bubbling up around a player. */
    static void glyphs(Player p, VfxKind kind, Color color, int count, double radius, double size, int ticks) {
        for (int i = 0; i < count; i++) {
            int delay = (int) (Math.random() * ticks * .5);
            at(delay, () -> Vfx.glyph(kind, p.getLocation().add(rand(radius), .6 + Math.random() * 2, rand(radius)), color,
                    size * (.7 + Math.random() * .6), 16 + (int) (Math.random() * 10)));
        }
    }

    /** Fires several {@link Vfx#flame}s in a ring. */
    static void flameRing(Location base, Color color, int count, double radius, double height, int ticks) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            Vfx.flame(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    i % 3 == 0 ? EMBER : color, height * (.8 + Math.random() * .4), ticks);
        }
    }

    // ================================================================ first release

    /** The release of the first form: shikai, resurrección, Schrift, Fullbring. */
    static void release(Player p, Release r) {
        Color c = r.form.color();
        Color e = r.element.tint;
        Location base = p.getLocation();
        Location body = base.clone().add(0, 1, 0);
        Location floor = ground(p);
        Vector fwd = forward(p);
        Location front = body.clone().add(fwd.clone().multiply(1.2));
        switch (r.kit.name()) {
            // ------------------------------------------------------------ Soul Reapers
            case "Zangetsu" -> {
                upcut(front, fwd, BLACK, 2.2, 8, .2);
                upcut(front.clone().add(0, .05, 0), fwd, Color.fromRGB(200, 20, 40), 2.0, 8, .2);
                column(base, Color.fromRGB(150, 10, 30), .9, 4, 8, 6);
            }
            case "Ryūjin Jakka" -> {
                swarm(p, VfxKind.FLAME, FLAME, 14, 1.2, 3.2, 1.0, 20);
                flameRing(base, FLAME, 12, 2.2, 1.8, 18);
            }
            case "Nozarashi" -> {
                column(base, GOLD, 1.1, 5, 6, 8);
                core(base, Color.fromRGB(150, 15, 25), .7, 5, 18);
                for (int i = 0; i < 3; i++) {
                    Vfx.model(VfxKind.CLAW, body.clone().add(fwd.clone().rotateAroundY(i * 2.1).multiply(1.4)), GOLD)
                            .pose(Vfx.facing(fwd.clone().rotateAroundY(i * 2.1)), Vfx.scale(.4))
                            .grow(1 + i * 2, 2, Vfx.scale(1.6)).life(10 + i * 2, 5);
                }
            }
            case "Kyōka Suigetsu" -> {
                crown(VfxKind.HEX, body, Color.fromRGB(190, 160, 255), 8, 1.8, 1.1, 12, .4, false);
                at(12, () -> radiate(VfxKind.HEX, body, c, 10, 5, .5, 10, 1.5));
                Vfx.shockwave(plugin, base, Color.fromRGB(120, 160, 255), 4, 10);
            }
            case "Katen Kyōkotsu" -> {
                Vfx.flash(VfxKind.BLADE, front.clone().subtract(0, .6, 0), c, Vfx.facing(fwd).rotateZ(.6f), Vfx.scale(1.6), 14);
                Vfx.flash(VfxKind.BLADE, front.clone().subtract(0, .6, 0), BLACK, Vfx.facing(fwd).rotateZ(-.6f), Vfx.scale(1.6), 14);
                swarm(p, VfxKind.PETAL, SAKURA, 16, 1.5, 2.5, 1.0, 26);
            }
            case "Sakanade" -> {
                Vfx.model(VfxKind.RING, front, c)
                        .pose(Vfx.facing(fwd).rotateX(1.57f), Vfx.scale(.2))
                        .to(1, 4, Vfx.facing(fwd).rotateX(1.57f), Vfx.scale(.7))
                        .to(5, 14, Vfx.facing(fwd).rotateX(1.57f).rotateY(9f), Vfx.scale(.7)).life(20, 6);
                Vfx.smoke(body, Color.fromRGB(240, 225, 150), 6, 1.4, 1.0, 24);
                glyphs(p, VfxKind.NOTE, c, 5, 1.6, .5, 16);
            }
            case "Hyōrinmaru" -> {
                dragonCircle(p, ICE, 1.6, 2.2, 24);
                Vfx.erupt(VfxKind.SHARD, floor, ICE, 8, 1.8, 1.4, 30);
            }
            case "Senbonzakura" -> {
                Location hilt = front.clone().subtract(0, .9, 0);
                Vfx.flash(VfxKind.BLADE, hilt, SAKURA, Vfx.facing(fwd), Vfx.scale(1.8), 10);
                at(8, () -> {
                    Vfx.petals(hilt.clone().add(0, 1.2, 0), SAKURA, 30, 2.5, 22);
                    swarm(p, VfxKind.PETAL, c, 20, 1.6, 2.4, 1.0, 26);
                });
            }
            case "Shinsō" -> {
                Location tip = p.getEyeLocation().subtract(0, .3, 0);
                Vfx.model(VfxKind.SPEAR, tip, Color.fromRGB(220, 220, 235))
                        .pose(Vfx.facing(fwd), Vfx.scale(.6, .6, .5))
                        .to(1, 3, Vfx.facing(fwd), Vfx.scale(.6, .6, 12))
                        .to(8, 4, Vfx.facing(fwd), Vfx.scale(.6, .6, .5)).life(13, 2);
            }
            case "Sode no Shirayuki" -> {
                Vfx.model(VfxKind.RING, floor.clone().add(0, .06, 0), WHITE)
                        .pose(new Quaternionf(), Vfx.scale(.3, 1, .3)).grow(1, 6, Vfx.scale(3, 1, 3)).life(24, 8);
                at(6, () -> column(base, ICE, 2.6, 5, 4, 8));
                Vfx.helix(plugin, p, WHITE, 16, 2, .9);
            }
            case "Zabimaru" -> fangWhip(p, Color.fromRGB(230, 215, 185), 8, 2.4, 16);
            case "Haineko" -> {
                swarm(p, VfxKind.SMOKE, Color.fromRGB(215, 195, 165), 12, 1.5, 2.6, .9, 30);
                swarm(p, VfxKind.STAR, Color.fromRGB(255, 230, 190), 8, 1.4, 2.4, .2, 26);
            }
            case "Suzumebachi" -> {
                Vfx.glyph(VfxKind.BUTTERFLY, front.clone().add(0, .6, 0), GOLD, 1.0, 26);
                for (int i = 0; i < 5; i++) {
                    Vfx.zap(body, body.clone().add(rand(2), rand(1.2), rand(2)), GOLD, .09);
                }
            }
            case "Tenken" -> {
                Location fist = base.clone().add(fwd.clone().multiply(3.5)).add(0, 6, 0);
                Vfx.model(VfxKind.FIST, fist, Color.fromRGB(150, 110, 70))
                        .pose(Vfx.facing(fwd), Vfx.scale(1.2))
                        .to(4, 4, v(0, -5.5, 0), Vfx.facing(fwd), Vfx.scale(1.6))
                        .life(22, 8);
                at(8, () -> {
                    Location hit = base.clone().add(fwd.clone().multiply(3.5));
                    Vfx.crater(ReleaseService.groundAt(hit) != null ? ReleaseService.groundAt(hit) : hit, 4, 60);
                    Vfx.scatter(VfxKind.ROCK, hit.clone().add(0, .4, 0), e, 7, 1.4, .9, 16);
                });
            }
            case "Wabisuke" -> {
                for (int i = 0; i < 4; i++) {
                    int k = i;
                    at(i * 3, () -> Vfx.model(VfxKind.RING, base.clone().add(0, 3.2 - k * .7, 0), c)
                            .pose(new Quaternionf(), Vfx.scale(2.4 - k * .3, 1.4, 2.4 - k * .3))
                            .to(1, 6, v(0, -1.5, 0), new Quaternionf(), Vfx.scale(2.6, 1, 2.6)).life(9, 4));
                }
                Vfx.flash(VfxKind.BLADE, front.clone().subtract(0, .7, 0), WHITE, Vfx.facing(fwd).rotateZ(.2f), Vfx.scale(1.3), 10);
            }
            case "Tachikaze" -> {
                for (int i = 0; i < 6; i++) {
                    Vfx.gust(body, fwd.clone().rotateAroundY(i * Math.PI / 3), c, 2.4, 10);
                }
                Vfx.whirl(base, WHITE, 1.4, 14);
            }
            case "Minazuki" -> {
                wings(p, c, 1.6, 22, .1);
                Vfx.drops(body, Color.fromRGB(90, 200, 170), 14, 1.4, 16);
            }
            case "Sōgyo no Kotowari" -> {
                Location left = body.clone().add(right(p).multiply(-1.2));
                Location rite = body.clone().add(right(p).multiply(1.2));
                Vfx.flash(VfxKind.BLADE, left.clone().subtract(0, .5, 0), WHITE, Vfx.facing(fwd), Vfx.scale(1.4), 20);
                Vfx.flash(VfxKind.BLADE, rite.clone().subtract(0, .5, 0), WHITE, Vfx.facing(fwd), Vfx.scale(1.4), 20);
                chain(left, rite, Color.fromRGB(200, 200, 210), 20);
                for (int i = -1; i <= 1; i++) {
                    Vfx.flash(VfxKind.CARD, body.clone().add(right(p).multiply(i * .6)).subtract(0, .3, 0), WHITE,
                            Vfx.facing(fwd), Vfx.scale(.35), 20);
                }
            }
            case "Ashisogi Jizō" -> {
                for (int i = -1; i <= 1; i++) {
                    Vfx.flash(VfxKind.SPEAR, front.clone().add(right(p).multiply(i * .3)), GOLD,
                            Vfx.facing(new Vector(0, 1, 0)), Vfx.scale(.6, .6, 1.6), 18);
                }
                Vfx.smoke(body, c, 7, 1.6, 1.1, 30);
            }
            // ------------------------------------------------------------ Hollows
            case "Pantera" -> {
                maskBreak(p, 6);
                Vfx.whirl(base, Color.fromRGB(120, 200, 255), 2.2, 18);
                for (int i = 0; i < 4; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 2);
                    Vfx.model(VfxKind.CLAW, body.clone().add(out.clone().multiply(1.5)), c)
                            .pose(Vfx.facing(out), Vfx.scale(.4)).grow(6 + i, 2, Vfx.scale(1.5)).life(16 + i, 6);
                }
            }
            case "Ira" -> {
                maskBreak(p, 6);
                column(base, c, 1.8, 5, 6, 10);
                Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .4, 0), Color.fromRGB(150, 110, 80), 10, 1.8, 1.0, 18);
            }
            case "Santa Teresa" -> {
                maskBreak(p, 4);
                upcut(front, fwd, GOLD, 2.4, 8, .9);
                upcut(front, fwd, GOLD, 2.4, 8, -.9 + Math.PI);
            }
            case "Brujería" -> {
                maskBreak(p, 4);
                crown(VfxKind.EYE, body.clone().add(0, .6, 0), c, 6, 1.6, .7, 26, 2, true);
            }
            case "Glotonería" -> {
                maskBreak(p, 4);
                crown(VfxKind.TENTACLE, base, c, 8, 1.4, 2.2, 26, 1.2, true);
                Vfx.smoke(body, BLACK, 6, 1.2, 1.2, 24);
            }
            case "Fornicarás" -> {
                maskBreak(p, 4);
                wings(p, Color.fromRGB(240, 130, 210), 1.8, 24, .2);
                Vfx.smoke(body, Color.fromRGB(240, 120, 200), 5, 1.2, .9, 24);
            }
            case "Murciélago" -> {
                maskBreak(p, 4);
                wings(p, Color.fromRGB(20, 40, 30), 2.4, 30, .15);
                rain(VfxKind.DROP, base, CERO_GREEN, 14, 3, 1.2, 20, 6);
            }
            case "Los Lobos" -> {
                maskBreak(p, 4);
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    at(i * 4, () -> wolf(p, k, c));
                }
            }
            case "Tiburón" -> {
                maskBreak(p, 4);
                for (int i = 0; i < 4; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 2);
                    Vfx.wave(base.clone().add(out.clone().multiply(.5)), out, Color.fromRGB(60, 150, 235), 2.2, 1.6, 3.5, 14);
                }
                head(p, Color.fromRGB(230, 200, 80), 1.4, 22, .5, 1.6);
            }
            case "Giralda" -> {
                maskBreak(p, 4);
                for (int s = -1; s <= 1; s += 2) {
                    Location spot = base.clone().add(right(p).multiply(s * 1.6));
                    for (int h = 0; h < 4; h++) {
                        Vfx.whirl(spot.clone().add(0, h * .8, 0), WHITE, .8 + h * .3, 16);
                    }
                }
            }
            case "Golondrina" -> {
                maskBreak(p, 4);
                featherFan(p, c, 9, 2.2, 26);
            }
            case "Tigre Estoque" -> {
                maskBreak(p, 4);
                head(p, c, 1.2, 18, .3, 1.8);
                for (int i = 0; i < 5; i++) {
                    Vfx.zap(body, body.clone().add(rand(2), rand(1.5), rand(2)), Color.fromRGB(255, 200, 80), .09);
                }
            }
            case "Arrogante" -> {
                maskBreak(p, 4);
                Vfx.model(VfxKind.MASK, p.getEyeLocation().add(0, .9, 0), WHITE)
                        .pose(Vfx.facing(fwd), Vfx.scale(.3)).grow(6, 6, Vfx.scale(1.6)).life(30, 8);
                Vfx.smoke(body, Color.fromRGB(60, 40, 80), 10, 1.8, 1.4, 34);
            }
            case "Dragra" -> {
                maskBreak(p, 4);
                head(p, FLAME, 1.5, 22, .6, 2.0);
                flameRing(base, FLAME, 8, 1.5, 1.5, 16);
            }
            case "Verdugo" -> {
                maskBreak(p, 4);
                horns(p, 2.0, 30);
                Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .3, 0), e, 8, 1.4, .9, 16);
            }
            case "Gamuza" -> {
                maskBreak(p, 4);
                horns(p, 1.4, 30);
                Vfx.model(VfxKind.RING, body, CERO_GREEN).pose(new Quaternionf(), Vfx.scale(.3))
                        .grow(1, 10, Vfx.scale(3.5, 1.5, 3.5)).life(14, 6);
            }
            case "Trepadora" -> {
                maskBreak(p, 4);
                crown(VfxKind.TENTACLE, base, c, 8, 1.3, 2.4, 30, -1.4, true);
            }
            case "Tres Bestias" -> {
                maskBreak(p, 4);
                for (int i = -1; i <= 1; i++) {
                    Location at = base.clone().add(right(p).multiply(i * 1.8)).add(fwd.clone().multiply(-1));
                    Vfx.model(VfxKind.HEAD, at.clone().add(0, 1.4, 0), c)
                            .pose(Vfx.facing(fwd.clone().setY(.4)), Vfx.scale(.3))
                            .grow(2 + (i + 1) * 3, 6, Vfx.scale(1.2)).life(28, 8);
                }
            }
            // ------------------------------------------------------------ Quincy
            case "H — The Heat" -> {
                quincySeal(p, c, 1.6, 30);
                Vfx.flame(p.getLocation().add(0, 1.9, 0), FLAME, 1.4, 30);
                flameRing(base, FLAME, 10, 1.8, 1.4, 18);
            }
            case "O — The Overkill" -> {
                quincySeal(p, c, 1.6, 30);
                Vfx.drops(body, c, 16, 1.6, 18);
                Vfx.model(VfxKind.CLAW, front, c).pose(Vfx.facing(fwd), Vfx.scale(.4)).grow(1, 3, Vfx.scale(1.8)).life(12, 6);
            }
            case "P — The Power" -> {
                quincySeal(p, c, 1.6, 30);
                Vfx.flash(VfxKind.FIST, front.clone().add(0, -.3, 0), c, Vfx.facing(fwd).rotateX(1.57f), Vfx.scale(.7), 16);
                Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .3, 0), e, 8, 1.3, .8, 14);
            }
            case "F — The Fear" -> {
                quincySeal(p, c, 1.6, 30);
                Vfx.erupt(VfxKind.SPIKE, floor, Color.fromRGB(60, 30, 50), 10, 2.2, 1.6, 30);
                Vfx.glyph(VfxKind.EYE, p.getEyeLocation().add(0, 1, 0), Color.fromRGB(180, 40, 80), 1.0, 26);
            }
            case "V — The Visionary" -> {
                quincySeal(p, c, 1.6, 30);
                swarm(p, VfxKind.STAR, c, 14, 1.6, 2.8, .35, 28);
            }
            case "L — The Love" -> {
                quincySeal(p, c, 1.6, 30);
                swarm(p, VfxKind.HEART, c, 12, 1.5, 2.6, .45, 28);
            }
            case "X — The X-Axis" -> {
                quincySeal(p, c, 1.6, 30);
                Location x = front.clone().add(fwd.clone().multiply(.5));
                for (int s = -1; s <= 1; s += 2) {
                    Vector diag = right(p).multiply(s).add(new Vector(0, 1, 0)).normalize();
                    Vfx.line(x.clone().subtract(diag.clone().multiply(1.6)), x.clone().add(diag.clone().multiply(1.6)), c, 2f, .3);
                }
            }
            case "A — The Antithesis" -> {
                quincySeal(p, c, 1.6, 30);
                bow(p, c, 30);
                radiate(VfxKind.ARROW, body, c, 8, 6, .8, 6, .3);
            }
            case "T — The Thunderbolt" -> {
                quincySeal(p, c, 1.6, 30);
                for (int i = 0; i < 4; i++) {
                    Location strike = base.clone().add(rand(3), 0, rand(3));
                    Vfx.bolt(strike, c, 9, .3);
                }
            }
            case "W — The Wind" -> {
                quincySeal(p, c, 1.6, 30);
                for (int i = 0; i < 8; i++) {
                    Vfx.gust(body, fwd.clone().rotateAroundY(i * Math.PI / 4), c, 2.0, 10);
                }
            }
            case "S — The Superstar" -> {
                quincySeal(p, c, 1.6, 30);
                spotlights(p, c, 4, 30);
                glyphs(p, VfxKind.STAR, GOLD, 8, 1.8, .6, 20);
            }
            case "E — The Explode" -> {
                quincySeal(p, c, 1.6, 30);
                for (int i = 0; i < 6; i++) {
                    int k = i;
                    at(i * 2, () -> Vfx.burst(body.clone().add(rand(2.5), rand(1), rand(2.5)), k % 2 == 0 ? c : EMBER, 32, .5, 1.6f));
                }
            }
            case "I — The Iron" -> {
                quincySeal(p, c, 1.6, 30);
                armUp(p, Color.fromRGB(170, 170, 180), 6, 1.0);
            }
            case "M — The Miracle" -> {
                quincySeal(p, c, 1.6, 30);
                Vfx.flash(VfxKind.BLADE, front.clone().subtract(0, 1, 0), GOLD, Vfx.facing(fwd), Vfx.scale(2.4), 26);
                Vfx.flash(VfxKind.HEX, front.clone().add(right(p).multiply(-1)), GOLD, Vfx.facing(fwd), Vfx.scale(1.4), 26);
            }
            case "G — The Glutton" -> {
                quincySeal(p, c, 1.6, 30);
                head(p, c, 1.3, 20, -.8, 1.2);
            }
            case "J — The Jail" -> {
                quincySeal(p, c, 1.6, 30);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    column(base.clone().add(Math.cos(a) * 2, 0, Math.sin(a) * 2), WHITE, .15, 3, 6, 10);
                }
            }
            case "B — The Balance" -> {
                quincySeal(p, c, 2.2, 34);
                Vfx.flash(VfxKind.HEX, front, Color.fromRGB(120, 140, 200), Vfx.facing(fwd), Vfx.scale(2), 26);
            }
            case "Z — The Zombie" -> {
                quincySeal(p, c, 1.6, 30);
                Vfx.drops(body, c, 20, 1.8, 18);
                Vfx.erupt(VfxKind.SPIKE, floor, Color.fromRGB(110, 40, 60), 6, 1.8, 1.0, 22);
            }
            // ------------------------------------------------------------ Fullbringers
            case "Jackpot Knuckle" -> {
                armUp(p, c, 4, .8);
                slots(p, c, 24);
            }
            case "Brazo Derecha de Gigante" -> armUp(p, c, 6, 1.0);
            case "Book of the End" -> {
                swarm(p, VfxKind.PAGE, WHITE, 14, 1.4, 2.4, .5, 26);
                Vfx.flash(VfxKind.BLADE, front.clone().subtract(0, .8, 0), Color.fromRGB(150, 150, 190), Vfx.facing(fwd), Vfx.scale(1.4), 18);
            }
            case "Brazo Izquierda del Diablo" -> {
                armUp(p, c, 6, 1.0);
                Vfx.flash(VfxKind.MASK, front.clone().add(0, .4, 0), WHITE, Vfx.facing(fwd), Vfx.scale(1.4), 18);
            }
            case "Cross of Scaffold" -> cross(front, fwd, Color.fromRGB(120, 230, 160), 1.8, 10);
            case "Fullbring" -> {
                swarm(p, VfxKind.SMOKE, Color.fromRGB(70, 60, 50), 10, 1.0, 2.4, .8, 24);
                armUp(p, Color.fromRGB(60, 50, 40), 4, .9);
            }
            case "Dirty Boots" -> {
                Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .2, 0), Color.fromRGB(110, 80, 50), 10, 1.4, .8, 16);
                Vfx.drops(base.clone().add(0, .2, 0), Color.fromRGB(90, 65, 40), 10, 1.2, 14);
            }
            case "Dollhouse" -> {
                swarm(p, VfxKind.HEART, c, 10, 1.3, 2.4, .45, 24);
                Vfx.flash(VfxKind.CARD, front, c, Vfx.facing(fwd), Vfx.scale(.8), 16);
            }
            case "Invaders Must Die" -> {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Location spot = body.clone().add(Math.cos(a) * 2, .6 + (i % 2) * .5, Math.sin(a) * 2);
                    Vfx.glyph(VfxKind.INVADER, spot, c, .7, 26);
                }
            }
            case "Shun Shun Rikka" -> {
                radiate(VfxKind.FLARE, p.getEyeLocation(), Color.fromRGB(255, 190, 130), 6, 1.4, .4, 8, .2);
                at(8, () -> Vfx.flash(VfxKind.TRIANGLE, front, Color.fromRGB(255, 200, 120), Vfx.facing(fwd), Vfx.scale(2.2), 22));
            }
            case "Time Tells No Lies" -> {
                Vfx.flash(VfxKind.CLOCK, front.clone().add(0, .4, 0), c, Vfx.facing(fwd), Vfx.scale(1.6), 26);
                chain(body.clone().add(right(p).multiply(-1.5)), body.clone().add(right(p).multiply(1.5)), c, 20);
            }
            default -> column(base, c, 1.2, 4, 8, 8);
        }
    }

    // ================================================================ advanced release

    /** The entrance of the advanced form, starting with the bolt from the sky. */
    static void advanced(Player p, Release r) {
        Color c = r.form.color();
        Color e = r.element.tint;
        Location base = p.getLocation();
        Location body = base.clone().add(0, 1, 0);
        Location floor = ground(p);
        Vector fwd = forward(p);
        Location front = body.clone().add(fwd.clone().multiply(1.4));
        switch (r.kit.advancedName()) {
            // ------------------------------------------------------------ Soul Reapers: Bankai
            case "Tensa Zangetsu" -> {
                // Black reiatsu with a crimson edge erupts; the cloak forms; the column is cut in an X.
                column(base, Color.fromRGB(180, 10, 30), 1.9, 12, 16, 12);
                core(base, BLACK, 1.1, 12, 40);
                swarm(p, VfxKind.SMOKE, BLACK, 18, 1.6, 4.5, 1.2, 36);
                at(22, () -> {
                    cross(body, fwd, BLACK, 3.6, 12);
                    cross(body.clone().add(0, .05, 0), fwd, Color.fromRGB(200, 20, 40), 3.2, 12);
                    Vfx.crater(floor, 8, 200);
                });
            }
            case "Zanka no Tachi" -> {
                // Every flame gathers into the blade: a ring of fire collapses inward, leaving a scorched,
                // white-hot sword and ash on the wind.
                flameRing(base, FLAME, 24, 4.5, 4.2, 22);
                flameRing(base, EMBER, 16, 3, 3, 20);
                at(18, () -> {
                    converge(VfxKind.FLAME, body, FLAME, 16, 5, 1.6, 6);
                    Vfx.model(VfxKind.ORB, body, EMBER).pose(new Quaternionf(), Vfx.scale(3)).grow(1, 6, Vfx.scale(.2)).life(8, 2);
                });
                at(25, () -> {
                    Vfx.flash(VfxKind.BLADE, front.clone().subtract(0, 1.2, 0), Color.fromRGB(255, 240, 200),
                            Vfx.facing(fwd), Vfx.scale(2.8), 40);
                    Vfx.smoke(body, Color.fromRGB(110, 100, 95), 10, 2.5, 1.4, 50);
                    Vfx.crater(floor, 9, 260);
                });
            }
            case "Nozarashi (Bankai)" -> {
                // A demon of red reiatsu: horns burst from the head, claws of power rake the air.
                column(base, Color.fromRGB(220, 30, 30), 2.2, 11, 12, 14);
                core(base, Color.fromRGB(60, 0, 0), 1.2, 11, 36);
                horns(p, 1.8, 40);
                at(12, () -> {
                    for (int i = 0; i < 6; i++) {
                        Vector out = fwd.clone().rotateAroundY(i * Math.PI / 3);
                        Vfx.model(VfxKind.CLAW, body.clone().add(out.clone().multiply(2.2)), Color.fromRGB(230, 30, 30))
                                .pose(Vfx.facing(out), Vfx.scale(.5)).grow(1, 3, Vfx.scale(2.6)).life(12, 6);
                    }
                    Vfx.drops(body.clone().add(0, 1.5, 0), Color.fromRGB(150, 0, 15), 24, 2.4, 20);
                    Vfx.crater(floor, 8, 200);
                });
            }
            case "Kyōka Suigetsu: Kanzen Saimin" -> {
                // Complete hypnosis: a vast eye opens, mirrors close in and shatter.
                Vfx.flash(VfxKind.EYE, base.clone().add(0, 6, 0), Color.fromRGB(170, 120, 255), new Quaternionf(), Vfx.scale(5), 50);
                crown(VfxKind.HEX, body, Color.fromRGB(190, 170, 255), 12, 4, 2, 20, -.8, false);
                at(20, () -> radiate(VfxKind.HEX, body, c, 14, 9, .8, 12, 3));
                Vfx.smoke(base.clone().add(0, .4, 0), Color.fromRGB(110, 80, 170), 14, 4, 1.6, 50);
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    at(i * 8, () -> Vfx.shockwave(plugin, base, Color.fromRGB(120, 150, 255), 7 + k * 2, 14));
                }
            }
            case "Katen Kyōkotsu: Karamatsu Shinjū" -> {
                // A stage of shadow: a dark dome falls, flowers drift down inside it, red threads hang.
                Vfx.model(VfxKind.ORB, base, BLACK).tone(1).pose(new Quaternionf(), Vfx.scale(.5))
                        .to(1, 14, new Quaternionf().rotateY(1), Vfx.scale(14, 9, 14)).life(70, 14);
                rain(VfxKind.PETAL, base, SAKURA, 40, 6, 1.3, 50, 6);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    chain(base.clone().add(Math.cos(a) * 5, 5, Math.sin(a) * 5), base.clone().add(Math.cos(a) * 2, .2,
                            Math.sin(a) * 2), Color.fromRGB(200, 30, 60), 50);
                }
            }
            case "Sakashima Yokoshima Happō Fusagari" -> {
                // Everything is turned around: a column pours down from the sky, rings spin sideways.
                Location sky = base.clone().add(0, 12, 0);
                Vfx.model(VfxKind.PILLAR, sky, c).pose(new Quaternionf().rotateX((float) Math.PI), Vfx.scale(1.6, .1, 1.6))
                        .to(1, 14, new Quaternionf().rotateX((float) Math.PI).rotateY(3), Vfx.scale(1.6, 12, 1.6))
                        .life(36, 10);
                for (int i = 0; i < 4; i++) {
                    Vfx.model(VfxKind.RING, body.clone().add(0, i * .6, 0), c)
                            .pose(new Quaternionf().rotateZ(1.57f).rotateY(i), Vfx.scale(.3))
                            .to(1, 8, new Quaternionf().rotateZ(1.57f).rotateX(i * .8f), Vfx.scale(3 + i, 1.2, 3 + i))
                            .to(9, 24, new Quaternionf().rotateZ(1.57f).rotateX(i * .8f + 4), Vfx.scale(3 + i, 1.2, 3 + i))
                            .life(34, 8);
                }
                glyphs(p, VfxKind.NOTE, c, 10, 3, .7, 30);
                Vfx.smoke(body, Color.fromRGB(240, 225, 150), 10, 3, 1.4, 40);
            }
            case "Daiguren Hyōrinmaru" -> {
                // The ice dragon rises through a pillar of frost; wings of ice open; three ice flowers bloom.
                column(base, ICE, 2, 11, 16, 12);
                Vfx.model(VfxKind.HEAD, base.clone().add(0, 1, 0), ICE)
                        .pose(Vfx.facing(new Vector(0, 1, 0)), Vfx.scale(2))
                        .to(1, 16, v(0, 10, 0), Vfx.facing(new Vector(0, 1, 0)), Vfx.scale(3))
                        .life(22, 6);
                Vfx.erupt(VfxKind.SHARD, floor, ICE, 18, 4, 2.6, 60);
                at(16, () -> {
                    wings(p, ICE, 3.2, 40, .25);
                    for (int i = 0; i < 3; i++) {
                        iceFlower(p.getLocation().add(fwd.clone().multiply(-.9)).add(right(p).multiply((i - 1) * .7))
                                .add(0, 2.6 + (i == 1 ? .3 : 0), 0), .7, 40);
                    }
                    Vfx.crater(floor, 9, 220);
                });
            }
            case "Senbonzakura Kageyoshi" -> {
                // The blade drops into the ground; two rows of a thousand giant blades rise; they scatter into
                // a storm of petals.
                Vfx.model(VfxKind.BLADE, front.clone().add(0, .5, 0), SAKURA)
                        .pose(Vfx.facing(fwd).rotateX((float) Math.PI), Vfx.scale(1.6))
                        .to(1, 5, v(0, -1.6, 0), Vfx.facing(fwd).rotateX((float) Math.PI), Vfx.scale(1.6)).life(8, 2);
                Vector side = right(p);
                for (int i = 0; i < 10; i++) {
                    for (int s = -1; s <= 1; s += 2) {
                        Location spot = base.clone().add(side.clone().multiply(s * (2.2 + (i % 2) * .4)))
                                .add(fwd.clone().multiply(-5 + i * 1.1));
                        Location g = ReleaseService.groundAt(spot);
                        int delay = 5 + i;
                        int life = 56 - i;
                        at(delay, () -> Vfx.erupt(VfxKind.BLADE, g != null ? g : spot, Color.fromRGB(255, 225, 235),
                                1, 0, 4.5, life));
                    }
                }
                at(50, () -> {
                    swarm(p, VfxKind.PETAL, SAKURA, 60, 4, 4, 1.2, 40);
                    Vfx.petals(body.clone().add(0, 1, 0), c, 40, 6, 30);
                });
            }
            case "Kamishini no Yari" -> {
                // The spear stretches far past sight and snaps back in an instant.
                Location tip = p.getEyeLocation().subtract(0, .3, 0);
                Vfx.model(VfxKind.SPEAR, tip, Color.fromRGB(230, 230, 245))
                        .pose(Vfx.facing(fwd), Vfx.scale(.9, .9, .6))
                        .to(1, 2, Vfx.facing(fwd), Vfx.scale(.9, .9, 40))
                        .to(10, 2, Vfx.facing(fwd), Vfx.scale(.9, .9, .6)).life(13, 2);
                column(base, Color.fromRGB(210, 210, 225), 1.4, 10, 12, 12);
                at(12, () -> {
                    Vfx.smoke(body, Color.fromRGB(120, 180, 60), 8, 2.2, 1.3, 40);
                    Vfx.crater(floor, 7, 200);
                });
            }
            case "Hakka no Togame" -> {
                // Absolute zero: the body turns to ice, white mist spreads, and frost flowers burst around.
                Vfx.model(VfxKind.AURA, base, WHITE).pose(new Quaternionf(), Vfx.scale(1.2, .1, 1.2))
                        .grow(1, 10, Vfx.scale(1.2, 2.6, 1.2)).life(40, 10);
                Vfx.model(VfxKind.RING, floor.clone().add(0, .06, 0), WHITE).pose(new Quaternionf(), Vfx.scale(.3, 1, .3))
                        .grow(1, 16, Vfx.scale(10, 1, 10)).life(50, 14);
                Vfx.smoke(base.clone().add(0, .3, 0), WHITE, 18, 5, 1.6, 50);
                at(10, () -> {
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        iceFlower(base.clone().add(Math.cos(a) * 4, .3, Math.sin(a) * 4), 1.0, 60);
                    }
                    Vfx.erupt(VfxKind.SHARD, floor, WHITE, 14, 6, 2.2, 70);
                });
            }
            case "Sōō Zabimaru" -> {
                // A great bone serpent coils up around its master and roars, red flame in its jaws.
                boneSerpent(p, 40);
                column(base, Color.fromRGB(200, 40, 40), 1.5, 9, 12, 12);
                at(20, () -> flameRing(base, FLAME, 12, 2.6, 2.4, 20));
            }
            case "Haineko (Bankai)" -> {
                // A storm of blade ash: a tornado of ash with glittering edges.
                for (int h = 0; h < 6; h++) {
                    int k = h;
                    at(h * 2, () -> Vfx.whirl(base.clone().add(0, k * .9, 0), Color.fromRGB(225, 205, 175), 1.4 + k * .5, 22));
                }
                swarm(p, VfxKind.SMOKE, Color.fromRGB(215, 195, 165), 26, 2.5, 6, 1.3, 40);
                swarm(p, VfxKind.STAR, Color.fromRGB(255, 240, 210), 14, 2.4, 6, .25, 36);
            }
            case "Jakuhō Raikōben" -> {
                // A golden missile launcher locks onto the arm with a crackle of lightning.
                Location arm = p.getLocation().add(0, 1.3, 0).add(right(p).multiply(.45));
                Vfx.model(VfxKind.SPEAR, arm, GOLD).follow(p, arm.toVector().subtract(base.toVector()))
                        .pose(Vfx.facing(fwd), Vfx.scale(.2)).grow(4, 8, Vfx.scale(3.4, 3.4, 4.5)).life(40, 8);
                converge(VfxKind.PLATE, arm, GOLD, 6, 3, .9, 8);
                for (int i = 0; i < 10; i++) {
                    int k = i;
                    at(i * 3, () -> Vfx.zap(arm, arm.clone().add(rand(2.5), rand(2), rand(2.5)), GOLD, .12));
                }
                column(base, GOLD, 1.4, 10, 12, 10);
            }
            case "Kokujō Tengen Myō'ō" -> {
                // An armoured giant rises out of the earth behind its master.
                Location behind = floor.clone().add(fwd.clone().multiply(-2)).add(right(p).multiply(5));
                Vfx.model(VfxKind.GIANT, behind.clone().add(0, -4, 0), Color.fromRGB(170, 120, 70))
                        .pose(Vfx.facing(fwd), Vfx.scale(4))
                        .to(1, 20, v(0, 4, 0), Vfx.facing(fwd), Vfx.scale(4.5))
                        .life(60, 12);
                Vfx.erupt(VfxKind.SPIKE, behind, e, 12, 3.5, 2, 40);
                Vfx.scatter(VfxKind.ROCK, behind.clone().add(0, .5, 0), e, 14, 2.5, 1.4, 22);
                Vfx.crater(behind, 10, 200);
            }
            case "Wabisuke (Bankai)" -> {
                // The weight of every cut: dark rings fall and press the ground into cracks.
                for (int i = 0; i < 8; i++) {
                    int k = i;
                    at(i * 3, () -> Vfx.model(VfxKind.RING, base.clone().add(0, 6, 0), c)
                            .pose(new Quaternionf(), Vfx.scale(5 - k * .3, 2, 5 - k * .3))
                            .to(1, 8, v(0, -5.8, 0), new Quaternionf().rotateY(1), Vfx.scale(6, 1, 6)).life(10, 3));
                }
                at(24, () -> {
                    Vfx.crater(floor, 11, 240);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3;
                        chain(base.clone().add(Math.cos(a) * 4, 4, Math.sin(a) * 4), base.clone().add(Math.cos(a) * 4, 0, Math.sin(a) * 4), c, 40);
                    }
                });
            }
            case "Tekken Tachikaze" -> {
                // Steel knuckles and a blast of wind that flattens everything around.
                for (int s = -1; s <= 1; s += 2) {
                    Location hand = body.clone().add(right(p).multiply(s * .45)).add(fwd.clone().multiply(.4));
                    Vfx.flash(VfxKind.PLATE, hand, WHITE, Vfx.facing(fwd), Vfx.scale(.7), 30);
                }
                at(8, () -> {
                    for (int i = 0; i < 12; i++) {
                        Vfx.gust(body, fwd.clone().rotateAroundY(i * Math.PI / 6), c, 5, 14);
                    }
                    Vfx.whirl(base, WHITE, 3, 20);
                    Vfx.shockwave(plugin, base, c, 10, 12);
                });
            }
            case "Minazuki (Bankai)" -> {
                // A blade of blood and water: crashing waves on every side.
                for (int i = 0; i < 8; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 4);
                    Vfx.wave(base.clone().add(out.clone().multiply(.6)), out, i % 2 == 0 ? Color.fromRGB(70, 190, 160)
                            : Color.fromRGB(170, 20, 30), 3, 2.4, 6, 18);
                }
                column(base, Color.fromRGB(80, 180, 150), 1.6, 9, 12, 12);
                Vfx.drops(body.clone().add(0, 2, 0), Color.fromRGB(170, 20, 30), 20, 2.4, 20);
            }
            case "Kamikake: Mimihagi" -> {
                // The right arm of a god: a vast dark hand and a single eye.
                Location hand = base.clone().add(fwd.clone().multiply(1)).add(0, 5, 0);
                Vfx.model(VfxKind.FIST, hand, Color.fromRGB(30, 30, 40))
                        .pose(Vfx.facing(fwd).rotateX(-.4f), Vfx.scale(.5)).grow(1, 14, Vfx.scale(3.2)).life(50, 12);
                Vfx.flash(VfxKind.EYE, hand.clone().add(0, 3, 0), Color.fromRGB(220, 220, 255), new Quaternionf(), Vfx.scale(2.4), 50);
                for (int i = 0; i < 8; i++) {
                    int k = i;
                    at(i * 4, () -> Vfx.zap(hand, base.clone().add(rand(4), 0, rand(4)), WHITE, .12));
                }
            }
            case "Konjiki Ashisogi Jizō" -> {
                // A giant golden caterpillar with a baby's face rises from a cloud of poison.
                Vfx.smoke(base.clone().add(0, .4, 0), c, 20, 4, 1.8, 60);
                Location rear = base.clone().add(fwd.clone().multiply(-2.5));
                for (int i = 0; i < 6; i++) {
                    Location seg = rear.clone().add(fwd.clone().multiply(-i * 1.1)).add(0, 1 + Math.sin(i) * .5, 0);
                    int k = i;
                    at(4 + i * 2, () -> Vfx.model(VfxKind.ORB, seg, GOLD)
                            .pose(new Quaternionf(), Vfx.scale(.3)).grow(1, 6, Vfx.scale(2.2 - k * .2)).life(50 - k * 2, 10));
                }
                at(4, () -> Vfx.model(VfxKind.HEAD, rear.clone().add(0, 3, 0), GOLD)
                        .pose(Vfx.facing(fwd.clone().setY(-.2)), Vfx.scale(.4)).grow(1, 10, Vfx.scale(3.4)).life(52, 10));
                rain(VfxKind.DROP, base, Color.fromRGB(170, 80, 200), 20, 5, 1.4, 30, 6);
            }
            // ------------------------------------------------------------ Hollows: Segunda Etapa and the like
            case "Pantera — Desgarrón" -> {
                maskBreak(p, 4);
                Vfx.whirl(base, Color.fromRGB(120, 200, 255), 4, 24);
                column(base, c, 1.8, 10, 12, 10);
                at(14, () -> {
                    for (int i = 0; i < 10; i++) {
                        Vector out = fwd.clone().rotateAroundY(i * Math.PI / 5);
                        Vfx.fly(VfxKind.CLAW, body, body.clone().add(out.clone().multiply(8)), Color.fromRGB(100, 190, 255),
                                Vfx.scale(2.6), 8, 4);
                    }
                    Vfx.crater(floor, 9, 200);
                });
            }
            case "Ira — Furia" -> {
                maskBreak(p, 4);
                column(base, c, 3, 12, 14, 14);
                Vfx.erupt(VfxKind.SPIKE, floor, Color.fromRGB(150, 100, 70), 16, 4.5, 3, 50);
                Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .5, 0), Color.fromRGB(140, 100, 70), 18, 3, 1.6, 24);
                at(14, () -> Vfx.crater(floor, 13, 260));
            }
            case "Santa Teresa — Six Arms" -> {
                maskBreak(p, 4);
                core(base, BLACK, 1.2, 10, 36);
                column(base, GOLD, 1.8, 10, 12, 10);
                at(12, () -> sixArms(p, GOLD, 40));
            }
            case "Brujería — Amor" -> {
                maskBreak(p, 4);
                for (int ring = 0; ring < 3; ring++) {
                    crown(VfxKind.EYE, body.clone().add(0, ring * 1.2, 0), ring == 1 ? WHITE : c, 10 + ring * 3,
                            3 - ring * .6, .9, 50, ring % 2 == 0 ? 2 : -2, true);
                }
            }
            case "Glotonería — 33,650 Hollows" -> {
                maskBreak(p, 4);
                swarm(p, VfxKind.MASK, WHITE, 24, 3, 5, .9, 44);
                crown(VfxKind.TENTACLE, base, c, 12, 2.4, 3.4, 44, 1.5, true);
                Vfx.smoke(body, BLACK, 14, 3, 1.6, 44);
            }
            case "Fornicarás — Gabriel" -> {
                maskBreak(p, 4);
                wings(p, Color.fromRGB(245, 140, 215), 3, 44, .3);
                crown(VfxKind.TENTACLE, base, Color.fromRGB(230, 120, 200), 8, 1.4, 2.4, 44, -1, true);
                Vfx.smoke(body, Color.fromRGB(240, 130, 200), 12, 3, 1.4, 44);
            }
            case "Resurrección: Segunda Etapa" -> {
                // Black wings unfurl, rain falls black, and a lance of green light forms.
                maskBreak(p, 4);
                core(base, Color.fromRGB(10, 30, 20), 2, 12, 40);
                wings(p, Color.fromRGB(10, 25, 18), 4.2, 56, .1);
                rain(VfxKind.DROP, base, Color.fromRGB(20, 40, 30), 40, 8, 1.4, 40, 10);
                at(16, () -> Vfx.flash(VfxKind.SPEAR, body.clone().add(right(p).multiply(.6)), CERO_GREEN,
                        Vfx.facing(new Vector(0, 1, 0)), Vfx.scale(2.6, 2.6, 4.5), 40));
            }
            case "Los Lobos — Lilynette" -> {
                maskBreak(p, 4);
                for (int i = 0; i < 6; i++) {
                    int k = i;
                    at(i * 3, () -> wolf(p, k, c));
                }
                column(base, c, 1.6, 10, 12, 12);
            }
            case "Tiburón — Gran Caída" -> {
                maskBreak(p, 4);
                for (int i = 0; i < 8; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 4);
                    Vfx.wave(base.clone().add(out.clone().multiply(.5)), out, Color.fromRGB(60, 150, 235), 3.4, 2.8, 7, 18);
                }
                at(8, () -> Vfx.flash(VfxKind.BLADE, base.clone().add(0, 3, 0), Color.fromRGB(80, 170, 255),
                        Vfx.facing(fwd), Vfx.scale(4.5), 40));
            }
            case "Giralda — Torbellinos" -> {
                maskBreak(p, 4);
                for (int s = -1; s <= 1; s += 2) {
                    Location spot = base.clone().add(right(p).multiply(s * 2.4));
                    for (int h = 0; h < 7; h++) {
                        int k = h;
                        at(h, () -> Vfx.whirl(spot.clone().add(0, k * .9, 0), WHITE, .9 + k * .45, 30));
                    }
                }
            }
            case "Golondrina — Wings of Blades" -> {
                maskBreak(p, 4);
                featherFan(p, c, 14, 3.4, 44);
                at(20, () -> radiate(VfxKind.FEATHER, body.clone().add(0, 1, 0), c, 20, 9, 1.4, 10, 3));
            }
            case "Tigre Estoque — Zarpazo" -> {
                maskBreak(p, 4);
                head(p, c, 2.2, 28, .5, 2.2);
                for (int i = 0; i < 12; i++) {
                    int k = i;
                    at(i * 2, () -> Vfx.zap(body, body.clone().add(rand(4), rand(2.5), rand(4)), Color.fromRGB(255, 200, 80), .12));
                }
            }
            case "Arrogante — Respira" -> {
                maskBreak(p, 4);
                Vfx.model(VfxKind.MASK, p.getEyeLocation().add(0, 1.3, 0), WHITE)
                        .pose(Vfx.facing(fwd), Vfx.scale(.4)).grow(1, 10, Vfx.scale(3)).life(50, 12);
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    at(i * 6, () -> Vfx.smoke(base.clone().add(0, .4, 0), Color.fromRGB(40, 25, 55), 14, 3 + k * 2.5, 1.8, 40));
                }
                Vfx.flash(VfxKind.CLOCK, base.clone().add(0, 5, 0), Color.fromRGB(120, 90, 160), Vfx.facing(fwd), Vfx.scale(3), 40);
            }
            case "Dragra — Garra de Dragón" -> {
                maskBreak(p, 4);
                head(p, FLAME, 2.6, 30, .8, 2.6);
                flameRing(base, FLAME, 16, 3, 3, 24);
                column(base, c, 1.6, 9, 12, 10);
            }
            case "Verdugo — Stampede" -> {
                maskBreak(p, 4);
                horns(p, 2.8, 44);
                for (int i = 0; i < 4; i++) {
                    int k = i;
                    at(i * 4, () -> Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .3, 0), e, 6, 1.6 + k * .4, 1.1, 18));
                }
                Vfx.crater(floor, 9, 200);
            }
            case "Gamuza — Lanzador Verde" -> {
                maskBreak(p, 4);
                horns(p, 1.8, 40);
                Vfx.model(VfxKind.SPEAR, base.clone().add(0, 4, 0), CERO_GREEN)
                        .pose(Vfx.facing(fwd), Vfx.scale(.4)).grow(1, 10, Vfx.scale(3, 3, 5))
                        .to(12, 26, Vfx.facing(fwd).rotateZ(8), Vfx.scale(3, 3, 5)).life(40, 8);
                column(base, CERO_GREEN, 1.4, 9, 12, 10);
            }
            case "Trepadora — Eight Arms" -> {
                maskBreak(p, 4);
                crown(VfxKind.TENTACLE, base, c, 8, 1.8, 3.4, 44, 2, true);
                at(16, () -> radiate(VfxKind.SPEAR, body, c, 8, 7, 1.2, 6, .4));
            }
            case "Ayon" -> {
                // The three beasts give up their arms and the chimera Ayon rises.
                maskBreak(p, 4);
                Location behind = base.clone().add(fwd.clone().multiply(-3)).add(0, 3, 0);
                Vfx.model(VfxKind.HEAD, behind, Color.fromRGB(60, 45, 35))
                        .pose(Vfx.facing(fwd.clone().setY(.2)), Vfx.scale(.5)).grow(1, 16, Vfx.scale(5)).life(56, 12);
                Vfx.smoke(behind, BLACK, 14, 3, 2, 50);
                converge(VfxKind.ORB, behind, GOLD, 3, 5, 1, 12);
            }
            // ------------------------------------------------------------ Quincy: Vollständig
            case "The Heat: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, FLAME, 2.6, 50, .2);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    column(base.clone().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5), FLAME, .5, 7, 8, 10);
                }
                flameRing(base, FLAME, 20, 3.5, 3, 30);
            }
            case "The Overkill: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.6, 50, .2);
                Vfx.drops(body.clone().add(0, 2, 0), c, 30, 3, 24);
                for (int i = 0; i < 6; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 3);
                    Vfx.model(VfxKind.CLAW, body.clone().add(out.clone().multiply(2.5)), c)
                            .pose(Vfx.facing(out), Vfx.scale(.5)).grow(8, 3, Vfx.scale(2.4)).life(20, 6);
                }
            }
            case "The Power: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.4, 50, .2);
                Vfx.model(VfxKind.FIST, base.clone().add(fwd.clone().multiply(2)).add(0, 7, 0), c)
                        .pose(Vfx.facing(fwd), Vfx.scale(2)).to(6, 4, v(0, -6.5, 0), Vfx.facing(fwd), Vfx.scale(2.4)).life(30, 10);
                at(10, () -> {
                    Vfx.crater(floor, 10, 200);
                    Vfx.erupt(VfxKind.SPIKE, floor, e, 12, 4, 1.8, 40);
                });
            }
            case "Tatarforas" -> {
                // A cage of dread: black thorns close in, eyes open everywhere, and a giant bird-like shadow.
                quincySeal(p, c, 3, 50);
                Vfx.model(VfxKind.ORB, base, Color.fromRGB(30, 15, 30)).tone(1).pose(new Quaternionf(), Vfx.scale(.4))
                        .grow(1, 14, Vfx.scale(12, 8, 12)).life(60, 14);
                Vfx.erupt(VfxKind.SPIKE, floor, Color.fromRGB(50, 20, 40), 20, 5.5, 3.4, 60);
                glyphs(p, VfxKind.EYE, Color.fromRGB(190, 40, 80), 14, 5, 1.0, 40);
                wings(p, Color.fromRGB(40, 20, 35), 4, 50, 0);
            }
            case "The Visionary: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.6, 50, .2);
                swarm(p, VfxKind.STAR, c, 30, 3, 5, .5, 44);
                Location sky = base.clone().add(fwd.clone().multiply(14)).add(0, 20, 0);
                Vfx.model(VfxKind.ROCK, sky, Color.fromRGB(150, 120, 110)).pose(new Quaternionf(), Vfx.scale(6))
                        .to(1, 40, v(0, -8, 0), new Quaternionf().rotateXYZ(1, 2, 0), Vfx.scale(7)).life(42, 4);
            }
            case "The Love: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.4, 50, .2);
                rain(VfxKind.HEART, base, c, 30, 6, .9, 40, 6);
                swarm(p, VfxKind.HEART, WHITE, 12, 2, 3, .5, 30);
            }
            case "Jilliel" -> {
                // Lille Barro's true form: many wings of light, a halo, and rays falling from above.
                quincySeal(p, c, 3.4, 56);
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    at(i * 4, () -> wings(p, k == 1 ? GOLD : WHITE, 2.4 + k * .9, 56 - k * 4, .1 + k * .35));
                }
                Vfx.model(VfxKind.RING, p.getLocation().add(0, 2.6, 0), GOLD).follow(p, new Vector(0, 2.6, 0))
                        .pose(new Quaternionf(), Vfx.scale(.1, 1, .1)).grow(1, 6, Vfx.scale(.7, 1.4, .7)).life(60, 10);
                for (int i = 0; i < 10; i++) {
                    Location spot = base.clone().add(rand(6), 0, rand(6));
                    int k = i;
                    at(10 + k * 3, () -> column(spot, WHITE, .35, 14, 4, 6));
                }
            }
            case "Licht Regen" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.6, 50, .2);
                bow(p, c, 40);
                rain(VfxKind.ARROW, base.clone().add(fwd.clone().multiply(6)), c, 50, 6, 1.2, 36, 14);
            }
            case "The Thunderbolt: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.8, 50, .3);
                for (int i = 0; i < 12; i++) {
                    int k = i;
                    at(i * 3, () -> Vfx.bolt(base.clone().add(rand(6), 0, rand(6)), k % 3 == 0 ? WHITE : c, 14, .4));
                }
            }
            case "The Wind: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.6, 50, .2);
                for (int h = 0; h < 6; h++) {
                    int k = h;
                    at(h * 2, () -> Vfx.whirl(base.clone().add(0, k * 1.1, 0), c, 1.8 + k * .5, 26));
                }
            }
            case "The Superstar: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, GOLD, 2.6, 50, .2);
                spotlights(p, c, 8, 50);
                for (int i = 0; i < 4; i++) {
                    int k = i;
                    at(i * 6, () -> radiate(VfxKind.STAR, body.clone().add(0, 1, 0), k % 2 == 0 ? GOLD : c, 10, 6, .8, 10, 2));
                }
            }
            case "The Explode: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.6, 50, .2);
                for (int i = 0; i < 14; i++) {
                    int k = i;
                    at(i * 2, () -> {
                        Location spot = base.clone().add(rand(6), Math.random() * 3, rand(6));
                        Vfx.burst(spot, k % 2 == 0 ? c : EMBER, 40, .9, 2f);
                        Vfx.model(VfxKind.RING, spot, EMBER).pose(new Quaternionf().rotateX((float) rand(1)), Vfx.scale(.3))
                                .grow(1, 5, Vfx.scale(2.4)).life(7, 3);
                    });
                }
            }
            case "The Iron: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, Color.fromRGB(170, 170, 180), 2.6, 50, .2);
                armUp(p, Color.fromRGB(170, 170, 180), 10, 1.4);
                at(10, () -> {
                    for (int i = 0; i < 4; i++) {
                        Vector out = fwd.clone().rotateAroundY(i * Math.PI / 2 + .4);
                        Vfx.model(VfxKind.CLAW, body.clone().add(out.clone().multiply(2)), Color.fromRGB(200, 200, 210))
                                .pose(Vfx.facing(out), Vfx.scale(.5)).grow(1, 3, Vfx.scale(2.2)).life(14, 5);
                    }
                });
            }
            case "Hoheitsrecht" -> {
                // Gerard's true form: a giant of light with vast golden wings, sword and shield.
                quincySeal(p, GOLD, 4, 60);
                wings(p, GOLD, 4.6, 60, .25);
                Vfx.model(VfxKind.RING, p.getLocation().add(0, 2.6, 0), GOLD).follow(p, new Vector(0, 2.6, 0))
                        .pose(new Quaternionf(), Vfx.scale(.1, 1, .1)).grow(1, 6, Vfx.scale(.8, 1.4, .8)).life(60, 10);
                Vfx.flash(VfxKind.BLADE, base.clone().add(right(p).multiply(2.2)), GOLD, Vfx.facing(fwd), Vfx.scale(6), 60);
                Vfx.flash(VfxKind.HEX, body.clone().add(right(p).multiply(-2.2)).add(0, 1, 0), GOLD, Vfx.facing(fwd), Vfx.scale(3.4), 60);
                column(base, GOLD, 2.4, 14, 14, 16);
            }
            case "The Glutton: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.4, 50, .2);
                Vfx.model(VfxKind.HEAD, base.clone().add(fwd.clone().multiply(1.5)).add(0, 1.6, 0), c)
                        .pose(Vfx.facing(fwd), Vfx.scale(.4)).grow(1, 10, Vfx.scale(4.2))
                        .grow(30, 6, Vfx.scale(1)).life(38, 4);
            }
            case "Biskiel" -> {
                quincySeal(p, WHITE, 4, 60);
                wings(p, WHITE, 2.8, 56, .2);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    Location spot = base.clone().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
                    int k = i;
                    at(k * 2, () -> {
                        column(spot, WHITE, .3, 8, 6, 30);
                        chain(spot.clone().add(0, 4, 0), base.clone().add(0, 4, 0), WHITE, 40);
                    });
                }
            }
            case "Avaron" -> {
                quincySeal(p, c, 3, 56);
                wings(p, Color.fromRGB(200, 210, 255), 2.8, 56, .2);
                crown(VfxKind.HEX, body, Color.fromRGB(150, 170, 230), 8, 2.6, 1.6, 50, 1, true);
            }
            case "The Zombie: Vollständig" -> {
                quincySeal(p, c, 3, 50);
                wings(p, c, 2.4, 50, .2);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Location spot = base.clone().add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
                    int k = i;
                    at(k * 3, () -> {
                        Vfx.drops(spot.clone().add(0, .3, 0), c, 10, 1.4, 16);
                        Vfx.flash(VfxKind.MASK, spot.clone().add(0, 1.4, 0), WHITE,
                                Vfx.facing(base.toVector().subtract(spot.toVector())), Vfx.scale(1.4), 30);
                    });
                }
            }
            // ------------------------------------------------------------ Fullbringers: Complete Fullbring
            case "Jackpot Knuckle: Complete" -> {
                armUp(p, c, 8, 1.2);
                slots(p, c, 40);
                at(24, () -> {
                    radiate(VfxKind.STAR, body.clone().add(0, 1, 0), GOLD, 14, 6, .8, 10, 2);
                    radiate(VfxKind.CARD, body.clone().add(0, 1, 0), WHITE, 10, 5, .6, 12, 2);
                });
            }
            case "Brazo Derecha de Gigante: Full" -> {
                armUp(p, c, 10, 1.4);
                at(10, () -> Vfx.flash(VfxKind.FIST, body.clone().add(right(p).multiply(1.2)).add(0, 1, 0), c,
                        Vfx.facing(fwd).rotateX(-.2f), Vfx.scale(2), 40));
                column(base, c, 1.6, 9, 12, 10);
            }
            case "Book of the End: Complete" -> {
                swarm(p, VfxKind.PAGE, WHITE, 36, 3, 5, .7, 44);
                Vfx.flash(VfxKind.BLADE, front.clone().subtract(0, 1, 0), Color.fromRGB(160, 160, 210), Vfx.facing(fwd), Vfx.scale(2.4), 40);
                core(base, BLACK, 1.2, 10, 36);
            }
            case "La Muerte" -> {
                armUp(p, c, 10, 1.4);
                Vfx.model(VfxKind.MASK, base.clone().add(fwd.clone().multiply(2)).add(0, 3, 0), WHITE)
                        .pose(Vfx.facing(fwd.clone().multiply(-1)), Vfx.scale(.4)).grow(1, 10, Vfx.scale(4)).life(40, 10);
                column(base, c, 1.8, 10, 12, 12);
            }
            case "Cross of Scaffold: Complete" -> {
                column(base, Color.fromRGB(120, 230, 160), 1.6, 10, 12, 12);
                at(10, () -> {
                    cross(body, fwd, Color.fromRGB(120, 230, 160), 3.4, 12);
                    cross(body, fwd.clone().multiply(-1), BLACK, 3.0, 12);
                });
            }
            case "Complete Fullbring" -> {
                swarm(p, VfxKind.SMOKE, Color.fromRGB(40, 35, 30), 22, 1.6, 4, 1.1, 36);
                armUp(p, Color.fromRGB(50, 45, 40), 8, 1.2);
                at(16, () -> cross(body, fwd, BLACK, 3, 12));
            }
            case "Dirty Boots: Complete" -> {
                for (int i = 0; i < 6; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 3);
                    Vfx.wave(base.clone().add(out.clone().multiply(.5)), out, Color.fromRGB(100, 75, 45), 2.6, 1.6, 5, 16);
                }
                Vfx.scatter(VfxKind.ROCK, base.clone().add(0, .3, 0), Color.fromRGB(110, 80, 50), 16, 2.2, 1.0, 20);
            }
            case "Dollhouse: Complete" -> {
                Vfx.model(VfxKind.ORB, base, c).tone(2).pose(new Quaternionf(), Vfx.scale(.4))
                        .grow(1, 12, Vfx.scale(9, 7, 9)).life(50, 12);
                rain(VfxKind.HEART, base, c, 30, 4.5, .8, 40, 6);
            }
            case "Invaders Must Die: Final Stage" -> {
                for (int ring = 0; ring < 2; ring++) {
                    crown(VfxKind.INVADER, body.clone().add(0, .5 + ring * 1.4, 0), ring == 0 ? c : Color.fromRGB(255, 80, 200),
                            10, 3.2 + ring, .9, 50, ring == 0 ? 1.5 : -1.5, false);
                }
                crown(VfxKind.HEX, base.clone().add(0, 1.5, 0), c, 14, 5.5, 2.2, 50, 0, false);
            }
            case "Shiten Kōshun" -> {
                Location flower = front.clone().add(0, .3, 0);
                radiate(VfxKind.FLARE, p.getEyeLocation(), Color.fromRGB(255, 190, 130), 6, 2, .5, 8, .4);
                at(8, () -> {
                    Vfx.flash(VfxKind.TRIANGLE, flower, Color.fromRGB(255, 200, 120), Vfx.facing(fwd), Vfx.scale(3), 44);
                    Vfx.model(VfxKind.ORB, base, Color.fromRGB(255, 200, 140)).tone(2).pose(new Quaternionf(), Vfx.scale(.4))
                            .grow(1, 10, Vfx.scale(5, 4.4, 5)).life(40, 10);
                    Vfx.petals(body, Color.fromRGB(255, 200, 150), 24, 3, 30);
                });
            }
            case "Time Tells No Lies: Complete" -> {
                Vfx.model(VfxKind.CLOCK, base.clone().add(0, 4, 0), c)
                        .pose(new Quaternionf().rotateX(-1.57f), Vfx.scale(.5))
                        .to(1, 10, new Quaternionf().rotateX(-1.57f).rotateZ(2), Vfx.scale(7))
                        .to(11, 30, new Quaternionf().rotateX(-1.57f).rotateZ(-6), Vfx.scale(7)).life(44, 10);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    chain(base.clone().add(0, 4, 0), base.clone().add(Math.cos(a) * 4, 0, Math.sin(a) * 4), c, 40);
                }
            }
            default -> {
                column(base, c, 1.7, 10.5, 20, 14);
                core(base, BLACK, .9, 10, 40);
            }
        }
    }

    // ================================================================ auras

    /** What hangs around a released form, called every two ticks. */
    static void aura(Player p, Release r, int tick) {
        Color c = r.form.color();
        String key = r.kit.name() + ":" + p.getUniqueId();
        double angle = tick * .08;
        boolean adv = r.advanced;
        switch (r.kit.name()) {
            case "Senbonzakura" -> Vfx.orbit(key, p, VfxKind.PETAL, SAKURA, adv ? 8 : 4, 1.3, 1.2, .9, angle * 2, false);
            case "Haineko" -> {
                if (tick % 6 == 0) {
                    Vfx.smoke(p.getLocation().add(0, .8, 0), Color.fromRGB(215, 195, 165), 1, 1, .6, 16);
                }
            }
            case "Shun Shun Rikka" -> Vfx.orbit(key, p, VfxKind.FLARE, Color.fromRGB(255, 190, 130), 6, .9, 2.1, .3, angle * 1.5, false);
            case "Jackpot Knuckle" -> Vfx.orbit(key, p, VfxKind.CARD, c, 3, 1.1, 1.3, .45, angle, true);
            case "Book of the End" -> Vfx.orbit(key, p, VfxKind.PAGE, WHITE, adv ? 6 : 3, 1.2, 1.4, .45, angle, true);
            case "Invaders Must Die" -> Vfx.orbit(key, p, VfxKind.INVADER, c, 4, 1.4, 2.2, .4, angle, false);
            case "Brujería" -> Vfx.orbit(key, p, VfxKind.EYE, c, adv ? 8 : 4, 1.3, 1.5, .4, angle, true);
            case "Time Tells No Lies" -> Vfx.orbit(key, p, VfxKind.CLOCK, c, 1, .01, 2.5, .6, angle, true);
            case "Glotonería", "Trepadora" -> {
                if (adv) {
                    Vfx.orbit(key, p, VfxKind.TENTACLE, c, 6, .9, .2, 1.4, angle * .6, true);
                }
            }
            case "Santa Teresa" -> {
                if (adv) {
                    sixArmsAura(p, key, GOLD, tick);
                }
            }
            case "Hyōrinmaru" -> {
                if (adv) {
                    heldWings(p, key, ICE, 2.6, tick);
                    if (tick % 10 == 0) {
                        Vfx.scatter(VfxKind.SHARD, p.getLocation().add(0, 1, 0), ICE, 1, .5, .3, 14);
                    }
                }
            }
            case "Murciélago" -> {
                if (adv) {
                    heldWings(p, key, Color.fromRGB(15, 30, 22), 3.4, tick);
                }
            }
            case "Golondrina", "Fornicarás", "Minazuki" -> {
                if (adv) {
                    heldWings(p, key, c, 2.2, tick);
                }
            }
            case "Zangetsu" -> {
                if (adv && tick % 4 == 0) {
                    Vfx.smoke(p.getLocation().add(0, .3, 0), BLACK, 1, .5, .7, 16);
                }
            }
            case "Ryūjin Jakka" -> {
                if (tick % 4 == 0) {
                    Vfx.flame(p.getLocation().add(rand(.5), 0, rand(.5)), adv ? EMBER : FLAME, adv ? 1.6 : 1.0, 10);
                }
            }
            case "Nozarashi" -> {
                if (adv) {
                    hornsAura(p, key, Color.fromRGB(220, 40, 40));
                }
            }
            case "M — The Miracle" -> {
                if (adv) {
                    heldWings(p, key, GOLD, 3.2, tick);
                }
            }
            case "Dollhouse" -> {
                if (tick % 10 == 0) {
                    Vfx.glyph(VfxKind.HEART, p.getLocation().add(rand(.8), 1.4 + Math.random(), rand(.8)), c, .35, 16);
                }
            }
            case "L — The Love" -> {
                if (tick % 8 == 0) {
                    Vfx.glyph(VfxKind.HEART, p.getLocation().add(rand(.8), 1.4 + Math.random(), rand(.8)), c, .4, 16);
                }
            }
            case "S — The Superstar", "V — The Visionary" -> {
                if (tick % 8 == 0) {
                    Vfx.glyph(VfxKind.STAR, p.getLocation().add(rand(.9), 1.2 + Math.random(), rand(.9)), adv ? GOLD : c, .35, 14);
                }
            }
            case "Kyōka Suigetsu" -> {
                if (adv && tick % 10 == 0) {
                    Vfx.glyph(VfxKind.EYE, p.getLocation().add(rand(1.5), 1.5 + Math.random(), rand(1.5)), c, .4, 18);
                }
            }
            default -> { }
        }
    }

    // ================================================================ pieces of single kits

    /** Hyōrinmaru: the ice dragon swims once around its master. */
    private static void dragonCircle(Player p, Color color, double size, double radius, int ticks) {
        Location center = p.getLocation().add(0, 1.4, 0);
        int steps = 6;
        int stepTicks = ticks / steps;
        Effect dragon = Vfx.model(VfxKind.HEAD, center.clone().add(radius, -.4, 0), color)
                .pose(new Vector3f(), Vfx.facing(new Vector(0, 0, 1)), Vfx.scale(size));
        for (int s = 1; s <= steps; s++) {
            double a = s * Math.PI * 2 / steps;
            Vector tangent = new Vector(-Math.sin(a), .1, Math.cos(a));
            dragon.to(1 + (s - 1) * stepTicks, stepTicks,
                    v(Math.cos(a) * radius - radius, s * .25, Math.sin(a) * radius), Vfx.facing(tangent), Vfx.scale(size));
        }
        dragon.life(ticks + 4, 4);
        swarm(p, VfxKind.SHARD, color, 10, radius, 2, .35, ticks);
    }

    /** Hyōten Hyakkasō and Hakka no Togame: a flower of ice crystals. */
    static void iceFlower(Location at, double size, int ticks) {
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Quaternionf petal = new Quaternionf().rotateY((float) -a).rotateZ((float) -1.1);
            Vfx.model(VfxKind.SHARD, at, ICE)
                    .pose(new Vector3f(), petal, Vfx.scale(size * .2))
                    .to(1 + i, 4, new Vector3f(), petal, Vfx.scale(size * .6, size, size * .6))
                    .life(ticks, Math.max(4, ticks / 4));
        }
        Vfx.model(VfxKind.SHARD, at, WHITE).pose(new Quaternionf(), Vfx.scale(size * .4, size * .7, size * .4)).life(ticks, 6);
    }

    /** Zabimaru: a whip of bone segments that lashes in an arc. */
    private static void fangWhip(Player p, Color color, int segments, double reach, int ticks) {
        Vector fwd = forward(p);
        Vector side = right(p);
        Location hand = p.getLocation().add(0, 1.2, 0);
        for (int i = 0; i < segments; i++) {
            double t = (i + 1) / (double) segments;
            int delay = i;
            Effect fang = Vfx.model(VfxKind.FANG, hand, color);
            for (int s = 0; s <= 4; s++) {
                double sweep = -1.2 + s * .6;
                Vector out = fwd.clone().multiply(Math.cos(sweep)).add(side.clone().multiply(Math.sin(sweep)));
                Vector3f pos = out.clone().multiply(t * reach * 1.4).add(new Vector(0, Math.sin(t * Math.PI) * .6, 0)).toVector3f();
                Quaternionf aim = Vfx.facing(out.clone().add(side.clone().multiply(.4)));
                if (s == 0) {
                    fang.pose(pos, aim, Vfx.scale(1.6));
                } else {
                    fang.to(1 + delay / 2 + (s - 1) * 3, 3, pos, aim, Vfx.scale(1.6));
                }
            }
            fang.life(ticks, 5);
        }
    }

    /** Sōō Zabimaru: the bone serpent coils up its master and rears its head. */
    private static void boneSerpent(Player p, int ticks) {
        Location base = p.getLocation();
        int segments = 14;
        for (int i = 0; i < segments; i++) {
            double t = i / (double) segments;
            double a = t * Math.PI * 3;
            double r = 2.2;
            Location at = base.clone().add(Math.cos(a) * r, .3 + t * 5, Math.sin(a) * r);
            Vector tangent = new Vector(-Math.sin(a), .35, Math.cos(a));
            int k = i;
            at(i, () -> Vfx.model(VfxKind.FANG, at, BONE)
                    .pose(Vfx.facing(tangent), Vfx.scale(.6))
                    .grow(1, 4, Vfx.scale(3.4 - k * .08))
                    .life(ticks - k, 8));
        }
        double end = Math.PI * 3;
        Location top = base.clone().add(Math.cos(end) * 2.2, 5.6, Math.sin(end) * 2.2);
        at(segments, () -> Vfx.model(VfxKind.HEAD, top, Color.fromRGB(240, 225, 195))
                .pose(Vfx.facing(forward(p).setY(-.3)), Vfx.scale(.6)).grow(1, 6, Vfx.scale(3.4)).life(ticks - segments, 8));
    }

    /** Los Lobos: a wolf of reiatsu that bounds out from its master. */
    private static void wolf(Player p, int index, Color color) {
        Vector out = forward(p).rotateAroundY((index - 2.5) * .7);
        Location from = p.getLocation().add(0, .8, 0);
        Location to = from.clone().add(out.clone().multiply(7)).add(0, .5, 0);
        Vfx.fly(VfxKind.HEAD, from, to, color, Vfx.scale(1.1), 10, 6);
    }

    /** Golondrina: a fan of blade feathers spread behind like wings. */
    private static void featherFan(Player p, Color color, int count, double size, int ticks) {
        Vector fwd = forward(p);
        Vector side = right(p);
        Location back = p.getLocation().add(0, 1.4, 0).add(fwd.clone().multiply(-.4));
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < count; i++) {
                double t = i / (double) (count - 1);
                double lift = -.6 + t * 1.6;
                Vector dir = side.clone().multiply(s * Math.cos(lift)).add(new Vector(0, Math.sin(lift), 0))
                        .add(fwd.clone().multiply(-.25)).normalize();
                Location at = back.clone().add(dir.clone().multiply(size * .5));
                Quaternionf rot = Vfx.facing(dir).rotateY((float) (-Math.PI / 2));
                Vfx.model(VfxKind.FEATHER, at, color)
                        .pose(new Vector3f(), rot, Vfx.scale(size * .2))
                        .to(1 + i / 2, 4, new Vector3f(), rot, Vfx.scale(size, size, size))
                        .life(ticks, Math.max(4, ticks / 3));
            }
        }
    }

    /** Bull or Hollow horns sprouting from the head. */
    static void horns(Player p, double size, int ticks) {
        Vector fwd = forward(p);
        for (int s = -1; s <= 1; s += 2) {
            Location at = p.getLocation().add(0, 1.8, 0).add(right(p).multiply(s * .2));
            Quaternionf pose = Vfx.facing(fwd).rotateZ((float) (s * .7));
            Vfx.model(VfxKind.HORN, at, WHITE).follow(p, at.toVector().subtract(p.getLocation().toVector()))
                    .pose(pose, Vfx.scale(.3)).grow(1, 6, Vfx.scale(size)).life(ticks, 6);
        }
    }

    private static void hornsAura(Player p, String key, Color color) {
        Vector fwd = forward(p);
        double body = size(p);
        for (int s = -1; s <= 1; s += 2) {
            Vector offset = new Vector(0, 1.85, 0).add(right(p).multiply(s * .18)).multiply(body);
            Quaternionf pose = Vfx.facing(fwd).rotateZ((float) (s * .75));
            Vector3f scale = Vfx.scale(1.7 * body);
            Vfx.keep(key + ":horn:" + s, 6, () -> Vfx.model(VfxKind.HORN, p.getLocation().add(offset), WHITE)
                    .follow(p, offset).pose(pose, scale)).offset(offset).move(2, new Vector3f(), pose, scale);
        }
    }

    /** Santa Teresa: six scythe arms fanned out behind the shoulders. */
    private static void sixArms(Player p, Color color, int ticks) {
        Vector fwd = forward(p);
        Vector side = right(p);
        for (int i = 0; i < 6; i++) {
            int s = i % 2 == 0 ? 1 : -1;
            int row = i / 2;
            Vector dir = side.clone().multiply(s).add(new Vector(0, .6 - row * .5, 0)).add(fwd.clone().multiply(-.2)).normalize();
            Location at = p.getLocation().add(0, 1.4, 0).add(dir.clone().multiply(.6));
            Quaternionf rot = Vfx.facing(fwd).rotateZ((float) (s * (1.0 + row * .45)));
            Vfx.model(VfxKind.BLADE, at, color).pose(rot, Vfx.scale(.3)).grow(1 + i, 4, Vfx.scale(1.8)).life(ticks, 8);
        }
    }

    private static void sixArmsAura(Player p, String key, Color color, int tick) {
        Vector fwd = forward(p);
        for (int i = 0; i < 6; i++) {
            int s = i % 2 == 0 ? 1 : -1;
            int row = i / 2;
            double sway = Math.sin(tick * .15 + i) * .1;
            double body = size(p);
            Vector offset = right(p).multiply(s * .55).add(new Vector(0, 1.45 + .3 - row * .3, 0)).add(fwd.clone().multiply(-.25))
                    .multiply(body);
            Quaternionf pose = Vfx.facing(fwd).rotateZ((float) (s * (1.0 + row * .45 + sway)));
            Vector3f scale = Vfx.scale(1.4 * body);
            Vfx.keep(key + ":arm:" + i, 6, () -> Vfx.model(VfxKind.BLADE, p.getLocation().add(offset), color)
                    .follow(p, offset).pose(pose, scale)).offset(offset).move(2, new Vector3f(), pose, scale);
        }
    }

    /** Wings held open on the back for the whole form, gently beating. */
    static void heldWings(Player p, String key, Color color, double wingSize, int tick) {
        Vector fwd = forward(p);
        Vector side = right(p);
        double body = size(p);
        double size = wingSize * body;
        for (int s = -1; s <= 1; s += 2) {
            int sign = s;
            Vector offset = new Vector(0, 1.3, 0).add(fwd.clone().multiply(-.32)).add(side.clone().multiply(s * .12))
                    .multiply(body);
            float beat = (float) (Math.sin(tick * .2) * .2 + .15);
            Quaternionf pose = Vfx.facing(fwd).rotateY(-sign * .4f).rotateZ(-sign * beat);
            Vector3f scale = Vfx.scale(sign > 0 ? -size : size, size, size);
            Vfx.keep(key + ":wing:" + s, 6, () -> Vfx.model(VfxKind.WING, p.getLocation().add(offset), color)
                    .follow(p, offset).pose(pose, scale)).offset(offset).move(2, new Vector3f(), pose, scale);
        }
    }

    /** Uryū's bow of light: a tall vertical crescent in front of the left hand. */
    private static void bow(Player p, Color color, int ticks) {
        Vector fwd = forward(p);
        Location at = p.getLocation().add(0, 1.3, 0).add(fwd.clone().multiply(.9));
        Quaternionf aim = Vfx.facing(fwd).rotateZ((float) (Math.PI / 2));
        Vfx.model(VfxKind.SLASH, at, color).pose(aim, Vfx.scale(.3, 1.2, .3)).grow(1, 4, Vfx.scale(1.3, 1.6, .9)).life(ticks, 6);
    }

    /** Spotlights shining down on the star. */
    private static void spotlights(Player p, Color color, int count, int ticks) {
        Location base = p.getLocation();
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            Location spot = base.clone().add(Math.cos(a) * 3.5, 8, Math.sin(a) * 3.5);
            Vector down = base.clone().add(0, 1, 0).toVector().subtract(spot.toVector());
            Vfx.model(VfxKind.BEAM, spot, i % 2 == 0 ? WHITE : color).tone(2)
                    .pose(Vfx.facing(down), Vfx.scale(.2, .2, down.length()))
                    .grow(1, 6, Vfx.scale(1.4, 1.4, down.length())).life(ticks, 8);
        }
    }

    /** Jackpot Knuckle: three cards spinning like slot reels, coming to rest. */
    private static void slots(Player p, Color color, int ticks) {
        Vector fwd = forward(p);
        Location row = p.getEyeLocation().add(fwd.clone().multiply(1.6)).add(0, .4, 0);
        for (int i = -1; i <= 1; i++) {
            Location at = row.clone().add(right(p).multiply(i * .75));
            Quaternionf rest = Vfx.facing(fwd.clone().multiply(-1));
            Vfx.model(VfxKind.CARD, at, i == 0 ? GOLD : color)
                    .pose(new Quaternionf(rest).rotateX(0), Vfx.scale(.7))
                    .to(1, 6, new Vector3f(), new Quaternionf(rest).rotateX(6), Vfx.scale(.7))
                    .to(8 + (i + 1) * 3, 4, new Vector3f(), rest, Vfx.scale(.8))
                    .life(ticks, 6);
        }
    }

    // ================================================================ techniques (right-click and sneak)

    private static boolean quincy(Release r) {
        return r.theme == Theme.QUINCY;
    }

    /** One step of a travelling wave (Getsuga Tenshō, Taimatsu, Ola Azul, ...), {@code step} 0 at the caster. */
    static void wave(Release r, Location center, Vector forward, double size, int step) {
        Color c = r.form.color();
        Vfx.arcParticles(center, forward, size, c, 1.8f, 0);
        switch (r.kit.name()) {
            case "Zangetsu", "Fullbring" -> {
                // Getsuga Tenshō: a black crescent with a burning edge.
                Vfx.arc(center, forward, size * 1.1, BLACK, 1.8f, 0);
                Vfx.arc(center.clone().add(forward.clone().multiply(.25)), forward, size, Color.fromRGB(200, 20, 40), 1.4f, 0);
            }
            case "Ryūjin Jakka" -> {
                Vfx.arc(center, forward, size, FLAME, 1.8f, 0);
                if (step % 2 == 0) {
                    flameRing(center.clone().subtract(0, 1, 0), FLAME, 3, size * .6, 1.6, 10);
                }
            }
            case "Nozarashi" -> {
                Vfx.arc(center, forward, size * 1.3, GOLD, 2f, 0);
                Vfx.arc(center, forward, size, Color.fromRGB(200, 20, 30), 1.4f, 0);
            }
            case "Senbonzakura" -> Vfx.petals(center, SAKURA, 8, size * .8, 10);
            case "Tachikaze" -> Vfx.gust(center, forward, c, size * 1.4, 6);
            case "Santa Teresa" -> upcut(center, forward, GOLD, size * 1.2, 4, 0);
            case "Tiburón" -> {
                if (step % 2 == 0) {
                    Vfx.wave(ReleaseService.groundAt(center) != null ? ReleaseService.groundAt(center) : center, forward,
                            Color.fromRGB(60, 150, 235), size * 1.6, size * 1.2, 3, 6);
                }
            }
            case "H — The Heat" -> {
                Vfx.arc(center, forward, size, FLAME, 1.8f, 0);
                Vfx.flame(center.clone().subtract(0, .5, 0), EMBER, 1.2, 8);
            }
            case "O — The Overkill" -> {
                if (step % 3 == 0) {
                    for (int i = -2; i <= 2; i++) {
                        Vector dir = forward.clone().rotateAroundY(i * .18);
                        Vfx.fly(VfxKind.ARROW, center, center.clone().add(dir.multiply(4)), c, Vfx.scale(1.2), 3, 2);
                    }
                }
            }
            case "Cross of Scaffold" -> cross(center, forward, Color.fromRGB(120, 230, 160), size, 3);
            default -> {
                if (r.theme == Theme.HOLLOW) {
                    for (double y = -0.55; y <= 0.56; y += 0.55) {
                        Vfx.arc(center, forward, size * 0.75, c, 1.4f, y);
                    }
                } else {
                    Vfx.arc(center, forward, size, c, 1.8f, 0);
                }
            }
        }
        if (step % 3 == 1) {
            trail(r, center, forward);
        }
    }

    /** A little of the element left behind a moving technique. */
    static void trail(Release r, Location at, Vector forward) {
        Color e = r.element.tint;
        switch (r.element) {
            case FIRE -> Vfx.flame(at.clone().subtract(0, .6, 0), e, 1, 8);
            case ICE -> Vfx.scatter(VfxKind.SHARD, at, e, 2, .4, .3, 10);
            case LIGHTNING -> Vfx.zap(at, at.clone().add(rand(1), rand(1), rand(1)), e, .07);
            case WIND -> Vfx.gust(at, forward, e, 1.2, 5);
            case SHADOW -> Vfx.smoke(at, e, 1, .3, .6, 10);
            case WATER, BLOOD -> Vfx.drops(at, e, 2, .4, 8);
            case PETAL, PALE_PETAL -> Vfx.petals(at, e, 3, .6, 10);
            case EARTH -> Vfx.scatter(VfxKind.ROCK, at, e, 1, .4, .4, 8);
            case LIGHT -> Vfx.glyph(VfxKind.STAR, at, e, .25, 6);
            case POISON -> Vfx.smoke(at, e, 1, .3, .5, 12);
            case SOUND -> Vfx.glyph(VfxKind.NOTE, at, e, .3, 8);
        }
    }

    /** The muzzle of a fired beam, before it leaves: what gathers in the hand. */
    static void charge(Release r, Location at, Vector forward) {
        switch (r.kit.name()) {
            case "Hyōrinmaru" -> Vfx.erupt(VfxKind.SHARD, at.clone().subtract(0, .3, 0), ICE, 4, .4, .5, 8);
            case "A — The Antithesis", "X — The X-Axis", "B — The Balance" -> bow(r.form.color(), at, forward);
            case "Los Lobos", "Murciélago" -> Vfx.model(VfxKind.RING, at, r.form.color())
                    .pose(Vfx.facing(forward).rotateX(1.57f), Vfx.scale(1.2)).grow(1, 4, Vfx.scale(.2)).life(5, 2);
            default -> { }
        }
    }

    private static void bow(Color color, Location at, Vector forward) {
        Quaternionf aim = Vfx.facing(forward).rotateZ((float) (Math.PI / 2));
        Vfx.model(VfxKind.SLASH, at, color).pose(aim, Vfx.scale(.3, 1.2, .3)).grow(1, 2, Vfx.scale(.9, 1.4, .6)).life(8, 4);
    }

    /**
     * Draws one length of a fired beam, from {@code from} to {@code to}. Most kits fire the faction's beam with
     * their element along it; some fire something else entirely (an ice dragon, an arrow, a thrown shield).
     */
    static void beam(Release r, Location from, Location to, double width, int step) {
        Color c = r.form.color();
        Vector span = to.toVector().subtract(from.toVector());
        switch (r.kit.name()) {
            case "Hyōrinmaru" -> {
                // Hyōryū: the ice dragon itself flies down the beam's path.
                if (step == 0) {
                    Vfx.fly(VfxKind.HEAD, from, from.clone().add(span.clone().normalize().multiply(30)), ICE,
                            Vfx.scale(1.6), 10, 4);
                }
                ReleaseService.beamSegment(from, to, ICE, width * .6);
                Vfx.scatter(VfxKind.SHARD, to, ICE, 2, .5, .35, 10);
            }
            case "Sode no Shirayuki" -> {
                // Tsugi no Mai, Hakuren: an avalanche of ice rolling out.
                ReleaseService.beamSegment(from, to, WHITE, width * 2);
                Vfx.smoke(to, WHITE, 1, .5, 1.2, 10);
                Vfx.scatter(VfxKind.SHARD, to, ICE, 2, .6, .4, 10);
            }
            case "Shinsō", "Sōgyo no Kotowari" -> {
                if (r.kit.name().equals("Shinsō")) {
                    // Shinsō Thrust: the blade itself extends to the target.
                    Vfx.model(VfxKind.SPEAR, from, Color.fromRGB(225, 225, 240))
                            .pose(Vfx.facing(span), Vfx.scale(.6, .6, span.length() + .1)).life(4, 2);
                } else {
                    Vfx.zap(from, to, WHITE, .12);
                }
            }
            case "A — The Antithesis", "X — The X-Axis", "B — The Balance", "J — The Jail" -> {
                // Heilig Pfeil: one arrow of reishi with a thin wake.
                Vfx.fly(VfxKind.ARROW, from, to, c, Vfx.scale(1.6), 1, 0);
                Vfx.model(VfxKind.BEAM, from, c).pose(Vfx.facing(span), Vfx.scale(.12, .12, span.length())).life(5, 4);
            }
            case "H — The Heat" -> {
                ReleaseService.beamSegment(from, to, FLAME, width);
                Vfx.flame(to.clone().subtract(0, .4, 0), EMBER, .9, 8);
            }
            case "T — The Thunderbolt", "Los Lobos", "Pantera" -> {
                Vfx.zap(from, to, c, .18);
                Vfx.zap(from, to, WHITE, .06);
            }
            case "L — The Love", "Dollhouse" -> {
                Vfx.fly(VfxKind.HEART, from, to, c, Vfx.scale(.8), 1, 0);
                ReleaseService.beamSegment(from, to, c, width * .4);
            }
            case "F — The Fear" -> {
                ReleaseService.beamSegment(from, to, Color.fromRGB(60, 20, 50), width * .5);
                Location g = ReleaseService.groundAt(to);
                if (g != null) {
                    Vfx.spike(VfxKind.SPIKE, g, Color.fromRGB(70, 30, 60), 1.4, 18);
                }
            }
            case "Tiburón" -> {
                if (step == 0) {
                    Vfx.fly(VfxKind.DROP, from, from.clone().add(span.clone().normalize().multiply(30)),
                            Color.fromRGB(70, 160, 240), Vfx.scale(4), 10, 2);
                }
                Vfx.drops(to, Color.fromRGB(70, 160, 240), 2, .4, 8);
            }
            case "G — The Glutton", "Z — The Zombie" -> {
                ReleaseService.beamSegment(from, to, c, width * .6);
                Vfx.drops(to, c, 2, .5, 8);
            }
            case "Brazo Izquierda del Diablo" -> {
                if (step == 0) {
                    Vfx.fly(VfxKind.MASK, from, from.clone().add(span.clone().normalize().multiply(30)), WHITE,
                            Vfx.scale(1.8), 10, 2);
                }
                ReleaseService.beamSegment(from, to, c, width * 1.4);
            }
            case "Shun Shun Rikka" -> {
                // Koten Zanshun: Tsubaki flies out as a spinning triangle of light.
                if (step == 0) {
                    Effect tsubaki = Vfx.model(VfxKind.TRIANGLE, from, Color.fromRGB(255, 190, 120))
                            .pose(new Vector3f(), Vfx.facing(new Vector(0, 1, 0)), Vfx.scale(.6));
                    Vector dir = span.clone().normalize();
                    for (int s = 1; s <= 5; s++) {
                        tsubaki.to(s * 2 - 1, 2, dir.clone().multiply(s * 6).toVector3f(),
                                Vfx.facing(new Vector(0, 1, 0)).rotateZ(s * 2.4f), Vfx.scale(.6));
                    }
                    tsubaki.life(12, 3);
                }
            }
            case "Murciélago" -> {
                ReleaseService.beamSegment(from, to, Color.fromRGB(20, 60, 40), width * 1.2);
                ReleaseService.beamSegment(from, to, CERO_GREEN, width * .7);
            }
            case "Ashisogi Jizō" -> {
                ReleaseService.beamSegment(from, to, c, width);
                Vfx.smoke(to, c, 1, .4, .8, 14);
            }
            default -> {
                ReleaseService.beamSegment(from, to, c, width);
                if (step % 2 == 1) {
                    trail(r, to, span);
                }
            }
        }
    }

    /** Where a beam lands. */
    static void beamImpact(Release r, Location point) {
        r.element.shapes(point, 1.2);
        if (quincy(r)) {
            Vfx.glyph(VfxKind.STAR, point, r.form.color(), .9, 8);
        }
    }

    /** Leaving an afterimage: Shunpo, Sonido, Hirenkyaku, Bringer Light. */
    static void vanish(Release r, Location at) {
        Color c = r.form.color();
        switch (r.kit.name()) {
            case "Kyōka Suigetsu" -> radiate(VfxKind.HEX, at, c, 6, 2, .5, 6, .8);
            case "Book of the End" -> radiate(VfxKind.PAGE, at, WHITE, 6, 1.6, .4, 8, .6);
            case "V — The Visionary" -> radiate(VfxKind.STAR, at, c, 5, 1.4, .3, 8, .6);
            case "Sakanade" -> Vfx.glyph(VfxKind.NOTE, at, c, .5, 10);
            case "Murciélago" -> Vfx.model(VfxKind.RING, at, CERO_GREEN).pose(Vfx.facing(new Vector(0, 1, 0)), Vfx.scale(.3))
                    .grow(1, 3, Vfx.scale(1.4)).life(5, 3);
            default -> {
                switch (r.theme) {
                    case SOUL_REAPER -> Vfx.model(VfxKind.AURA, at.clone().subtract(0, 1, 0), c).tone(1)
                            .pose(new Quaternionf(), Vfx.scale(.8, 2, .8)).grow(1, 5, Vfx.scale(.2, 2.2, .2)).life(6, 4);
                    case HOLLOW -> Vfx.model(VfxKind.RING, at, c)
                            .pose(new Quaternionf().rotateX(1.57f), Vfx.scale(.2)).grow(1, 3, Vfx.scale(1.2)).life(5, 3);
                    case QUINCY -> Vfx.sigil(VfxKind.SIGIL, at.clone().subtract(0, 1, 0), c, .7, 10);
                    case FULLBRINGER -> Vfx.scatter(VfxKind.PLATE, at, c, 3, .6, .4, 8);
                }
                trail(r, at, new Vector(0, 0, 1));
            }
        }
    }

    /** Each tick of a dash. */
    static void dashTrail(Release r, Player p, int tick) {
        Location at = p.getLocation().add(0, 1, 0);
        Vector fwd = forward(p);
        switch (r.kit.name()) {
            case "Suzumebachi", "T — The Thunderbolt", "Tigre Estoque" -> Vfx.zap(at.clone().subtract(fwd), at, r.form.color(), .12);
            case "Verdugo", "Gamuza" -> {
                if (tick % 3 == 0) {
                    horns(p, 1.2, 6);
                }
            }
            case "Dirty Boots" -> Vfx.scatter(VfxKind.ROCK, p.getLocation().add(0, .2, 0), Color.fromRGB(100, 75, 45), 1, .5, .4, 8);
            case "W — The Wind", "Giralda" -> Vfx.gust(at, fwd, r.form.color(), 1.6, 5);
            case "S — The Superstar" -> Vfx.glyph(VfxKind.STAR, at, GOLD, .35, 8);
            case "Fullbring" -> Vfx.smoke(at, Color.fromRGB(40, 35, 30), 1, .3, .6, 10);
            default -> trail(r, at, fwd);
        }
    }

    /** A ground impact: slams and the landing of a dive. */
    static void slam(Release r, Player p, Location center, double radius) {
        Color c = r.form.color();
        Color e = r.element.tint;
        Location g = ReleaseService.groundAt(center) != null ? ReleaseService.groundAt(center) : center;
        Vector fwd = forward(p);
        switch (r.kit.name()) {
            case "Tenken" -> Vfx.model(VfxKind.FIST, g.clone().add(0, 6, 0), Color.fromRGB(150, 110, 70))
                    .pose(Vfx.facing(fwd), Vfx.scale(1.4)).to(1, 3, v(0, -5.6, 0), Vfx.facing(fwd), Vfx.scale(1.6)).life(14, 6);
            case "P — The Power", "Brazo Derecha de Gigante" -> Vfx.model(VfxKind.FIST, g.clone().add(0, 4, 0), c)
                    .pose(Vfx.facing(fwd), Vfx.scale(1)).to(1, 3, v(0, -3.6, 0), Vfx.facing(fwd), Vfx.scale(1.2)).life(12, 6);
            case "M — The Miracle" -> Vfx.model(VfxKind.BLADE, g.clone().add(0, 5, 0), GOLD)
                    .pose(Vfx.facing(fwd).rotateX((float) Math.PI), Vfx.scale(3))
                    .to(1, 3, v(0, -2.5, 0), Vfx.facing(fwd).rotateX((float) Math.PI), Vfx.scale(3.4)).life(16, 6);
            case "Jackpot Knuckle" -> radiate(VfxKind.CARD, g.clone().add(0, 1, 0), GOLD, 8, radius, .7, 8, 1);
            case "Dragra" -> {
                head(p, FLAME, 1.4, 14, -1, 1);
                flameRing(g, FLAME, 10, radius * .6, 2, 14);
            }
            case "Tachikaze" -> Vfx.whirl(g, c, radius, 12);
            case "Wabisuke" -> Vfx.model(VfxKind.RING, g.clone().add(0, 4, 0), c).pose(new Quaternionf(), Vfx.scale(radius, 2, radius))
                    .to(1, 4, v(0, -3.9, 0), new Quaternionf(), Vfx.scale(radius, 1, radius)).life(8, 3);
            case "Arrogante" -> Vfx.smoke(g.clone().add(0, .4, 0), Color.fromRGB(45, 30, 60), 8, radius * .7, 1.4, 24);
            case "Trepadora" -> crown(VfxKind.TENTACLE, g, c, 6, radius * .6, 1.8, 14, .5, true);
            default -> { }
        }
        switch (r.element) {
            case FIRE -> flameRing(g, e, 12, radius * .7, 1.8, 14);
            case ICE -> Vfx.erupt(VfxKind.SHARD, g, e, 10, radius * .7, 1.6, 26);
            case EARTH -> Vfx.erupt(VfxKind.SPIKE, g, e, 10, radius * .7, 1.6, 26);
            case WATER -> {
                for (int i = 0; i < 6; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 3);
                    Vfx.wave(g, out, e, 1.8, 1.2, radius * .8, 10);
                }
            }
            case LIGHTNING -> {
                for (int i = 0; i < 6; i++) {
                    Vfx.zap(g.clone().add(0, .3, 0), g.clone().add(rand(radius), .3, rand(radius)), e, .1);
                }
            }
            case WIND -> Vfx.whirl(g, e, radius * .7, 10);
            case SHADOW, POISON -> Vfx.smoke(g.clone().add(0, .4, 0), e, 6, radius * .6, 1.0, 18);
            case PETAL, PALE_PETAL -> Vfx.petals(g.clone().add(0, .5, 0), e, 14, radius, 16);
            case LIGHT -> Vfx.sigil(VfxKind.SIGIL, g, e, radius * .8, 16);
            case BLOOD -> Vfx.drops(g.clone().add(0, .4, 0), e, 10, 1.2, 12);
            case SOUND -> Vfx.glyph(VfxKind.NOTE, g.clone().add(0, 1.2, 0), e, .7, 12);
        }
        Vfx.crater(g, radius * 1.3, 60);
    }

    /** A protective technique around the caster. */
    static void ward(Release r, Player p) {
        Color c = r.form.color();
        Location body = p.getLocation().add(0, 1, 0);
        Vector fwd = forward(p);
        switch (r.kit.name()) {
            case "Shun Shun Rikka" -> {
                // Santen Kesshun: a triangle of light between three of the six flowers.
                Vfx.flash(VfxKind.TRIANGLE, body.clone().add(fwd.clone().multiply(1.3)), Color.fromRGB(255, 200, 120),
                        Vfx.facing(fwd), Vfx.scale(2.4), 30);
                radiate(VfxKind.FLARE, p.getEyeLocation(), Color.fromRGB(255, 190, 130), 3, 1.4, .3, 4, .3);
            }
            case "Senbonzakura" -> {
                // Danku: a wall that nothing passes.
                Vfx.flash(VfxKind.HEX, body.clone().add(fwd.clone().multiply(1.6)), SAKURA, Vfx.facing(fwd), Vfx.scale(3), 30);
            }
            case "M — The Miracle" -> Vfx.flash(VfxKind.HEX, body.clone().add(fwd.clone().multiply(1.4)), GOLD,
                    Vfx.facing(fwd), Vfx.scale(2.6), 30);
            case "Time Tells No Lies" -> Vfx.flash(VfxKind.CLOCK, body.clone().add(fwd.clone().multiply(1.3)), c,
                    Vfx.facing(fwd), Vfx.scale(2.2), 30);
            case "I — The Iron", "Brazo Derecha de Gigante" -> crown(VfxKind.PLATE, body, c, 6, 1.3, 1.2, 30, 1, true);
            case "J — The Jail", "B — The Balance" -> Vfx.model(VfxKind.AURA, p.getLocation(), Color.fromRGB(40, 40, 70))
                    .pose(new Quaternionf(), Vfx.scale(.9, 2, .9)).life(30, 8);
            case "Minazuki", "Gamuza" -> Vfx.drops(body.clone().add(0, 1, 0), r.element.tint, 10, 1, 14);
            case "Sōgyo no Kotowari" -> crown(VfxKind.CARD, body, WHITE, 6, 1.4, .5, 30, 2, true);
            default -> crown(VfxKind.HEX, body, c, 6, 1.6, 1.4, 24, .6, true);
        }
        Vfx.sigil(quincy(r) ? VfxKind.SIGIL : VfxKind.RING, ground(p), c, 3, 24);
    }

    /** One hit of a five-hit combo. */
    static void flurryHit(Release r, Location at, Vector facing, int strike, boolean last) {
        Color c = r.form.color();
        double tilt = rand(.6);
        double size = last ? 1.8 : 1.2;
        Vfx.arcParticles(at, facing, size, c, 1.4f, tilt);
        Quaternionf aim = Vfx.facing(facing).rotateZ((float) (strike * 1.1));
        switch (r.kit.name()) {
            case "Pantera", "Tigre Estoque", "I — The Iron", "Nozarashi" -> Vfx.model(VfxKind.CLAW, at, c)
                    .pose(aim, Vfx.scale(size * .4)).grow(1, 2, Vfx.scale(size)).life(6, 3);
            case "Jackpot Knuckle" -> Vfx.flash(VfxKind.CARD, at, strike % 2 == 0 ? GOLD : c, aim, Vfx.scale(.6), 6);
            case "S — The Superstar" -> Vfx.glyph(VfxKind.STAR, at, GOLD, .6, 8);
            case "G — The Glutton" -> Vfx.flash(VfxKind.HEAD, at.clone().subtract(facing.clone().normalize()), c,
                    Vfx.facing(facing), Vfx.scale(.8), 5);
            case "Dragra", "H — The Heat" -> {
                Vfx.flame(at.clone().subtract(0, .6, 0), FLAME, 1.2, 8);
                Vfx.burst(at, FLAME, 10, .2, 1.2f);
            }
            case "Shinsō" -> Vfx.model(VfxKind.SPEAR, at.clone().subtract(facing.clone().normalize().multiply(3)), c)
                    .pose(Vfx.facing(facing), Vfx.scale(.5, .5, 3.2)).life(4, 2);
            case "Dirty Boots" -> Vfx.scatter(VfxKind.ROCK, at, Color.fromRGB(100, 75, 45), 3, .6, .4, 8);
            case "Suzumebachi" -> Vfx.zap(at.clone().subtract(facing.clone().normalize().multiply(1.5)), at, GOLD, .1);
            default -> {
                switch (r.theme) {
                    case HOLLOW -> Vfx.model(VfxKind.CLAW, at, c).pose(aim, Vfx.scale(size * .4)).grow(1, 2, Vfx.scale(size)).life(6, 3);
                    case FULLBRINGER, QUINCY -> {
                        Vfx.burst(at, c, last ? 30 : 8, .2, 1.4f);
                        Vfx.model(VfxKind.RING, at, c).pose(Vfx.facing(facing).rotateX(1.57f), Vfx.scale(.2))
                                .grow(1, 3, Vfx.scale(size)).life(5, 3);
                    }
                    case SOUL_REAPER -> Vfx.arc(at, facing, size, c, last ? 1.7f : 1.2f, tilt);
                }
            }
        }
        if (last) {
            r.element.shapes(at, 1);
        }
    }

    /**
     * One bolt of a storm technique (Licht Regen, Cero Rain, Feather Rain, ...). Returns false to let the caller
     * draw its default bolt of light.
     */
    static boolean rainDrop(Release r, Location impact) {
        Color c = r.form.color();
        Location high = impact.clone().add(rand(1.5), 10, rand(1.5));
        switch (r.kit.name()) {
            case "A — The Antithesis", "X — The X-Axis", "O — The Overkill", "J — The Jail" -> Vfx.fly(VfxKind.ARROW, high, impact, c, Vfx.scale(1.8), 4, 4);
            case "Golondrina" -> Vfx.fly(VfxKind.FEATHER, high, impact, c, Vfx.scale(1.6), 5, 6);
            case "Los Lobos" -> {
                Vfx.fly(VfxKind.ORB, high, impact, c, Vfx.scale(.8), 4, 1);
                Vfx.fly(VfxKind.BEAM, high, impact, WHITE, Vfx.scale(.2, .2, 2), 4, 1);
            }
            case "V — The Visionary" -> Vfx.fly(VfxKind.STAR, high, impact, c, Vfx.scale(.9), 5, 2);
            case "E — The Explode" -> {
                Vfx.fly(VfxKind.ORB, high, impact, c, Vfx.scale(.5), 4, 0);
                at(4, () -> Vfx.burst(impact.clone().add(0, .5, 0), EMBER, 40, .7, 1.8f));
            }
            case "Invaders Must Die" -> Vfx.fly(VfxKind.INVADER, high, impact, c, Vfx.scale(.8), 6, 2);
            case "Ashisogi Jizō" -> Vfx.fly(VfxKind.DROP, high, impact, c, Vfx.scale(2), 5, 2);
            default -> {
                switch (r.element) {
                    case ICE -> Vfx.fly(VfxKind.SHARD, high, impact, ICE, Vfx.scale(1.2, 2, 1.2), 4, 6);
                    case FIRE -> {
                        Vfx.fly(VfxKind.ROCK, high, impact, Color.fromRGB(90, 50, 30), Vfx.scale(1.4), 5, 1);
                        Vfx.fly(VfxKind.FLAME, high, impact, FLAME, Vfx.scale(1.6), 5, 2);
                    }
                    case PETAL, PALE_PETAL -> Vfx.petals(impact.clone().add(0, 3, 0), r.element.tint, 8, 1.2, 10);
                    case POISON, WATER, BLOOD -> Vfx.fly(VfxKind.DROP, high, impact, r.element.tint, Vfx.scale(2), 5, 2);
                    case EARTH -> Vfx.fly(VfxKind.ROCK, high, impact, r.element.tint, Vfx.scale(1.4), 5, 2);
                    case SHADOW -> Vfx.fly(VfxKind.SPEAR, high, impact, BLACK, Vfx.scale(1.4, 1.4, 2.4), 4, 4);
                    case LIGHT -> Vfx.fly(VfxKind.ARROW, high, impact, c, Vfx.scale(1.8), 4, 4);
                    default -> {
                        return false;
                    }
                }
            }
        }
        r.element.shapes(impact.clone().add(0, .3, 0), .7);
        return true;
    }

    /** One erupting pillar of a pillar technique. */
    static void pillar(Release r, Location ground, double height, int step) {
        Color e = r.element.tint;
        switch (r.kit.name()) {
            case "Ryūjin Jakka" -> column(ground, FLAME, .7, height + 2, 3, 10);
            case "Invaders Must Die" -> Vfx.flash(VfxKind.HEX, ground.clone().add(0, 1, 0), r.form.color(),
                    Vfx.facing(new Vector(rand(1), 0, rand(1))), Vfx.scale(1.6), 26);
            case "Senbonzakura" -> Vfx.spike(VfxKind.BLADE, ground, SAKURA, height + 1, 26);
            default -> {
                switch (r.element) {
                    case ICE -> Vfx.spike(VfxKind.SHARD, ground, e, height + .5, 28);
                    case FIRE -> Vfx.flame(ground, e, height + .6, 16);
                    case LIGHT -> Vfx.spike(VfxKind.SPEAR, ground, e, height, 24);
                    default -> Vfx.spike(VfxKind.SPIKE, ground, e, height, 28);
                }
            }
        }
    }

    /** One binding of a chains technique, from the caster's chest to a target. */
    static void bind(Release r, Location from, Location to) {
        Color c = r.form.color();
        switch (r.kit.name()) {
            case "Zabimaru" -> {
                Vector span = to.toVector().subtract(from.toVector());
                int segments = Math.max(3, (int) (span.length() / .9));
                for (int i = 0; i < segments; i++) {
                    Location at = from.clone().add(span.clone().multiply((i + .5) / segments));
                    Vfx.flash(VfxKind.FANG, at, BONE, Vfx.facing(span), Vfx.scale(1.6), 12);
                }
            }
            case "Hyōrinmaru" -> {
                chain(from, to, ICE, 12);
                Vfx.erupt(VfxKind.SHARD, to.clone().subtract(0, .8, 0), ICE, 4, .5, .9, 30);
            }
            case "Trepadora", "Glotonería", "Tres Bestias" -> {
                Vector span = to.toVector().subtract(from.toVector());
                Vfx.model(VfxKind.BEAM, from, c).pose(Vfx.facing(span), Vfx.scale(.25, .25, span.length())).life(10, 4);
                Vfx.flash(VfxKind.TENTACLE, to.clone().subtract(0, .6, 0), c, Vfx.facing(span), Vfx.scale(1.4), 12);
            }
            case "Dollhouse" -> {
                chain(from, to, c, 12);
                Vfx.glyph(VfxKind.HEART, to.clone().add(0, .8, 0), c, .6, 14);
            }
            case "Time Tells No Lies" -> {
                chain(from, to, c, 14);
                Vfx.flash(VfxKind.CLOCK, to.clone().add(0, 1, 0), c, Vfx.facing(from.toVector().subtract(to.toVector())), Vfx.scale(.8), 14);
            }
            case "Minazuki" -> {
                chain(from, to, Color.fromRGB(90, 200, 170), 12);
                Vfx.drops(to, Color.fromRGB(90, 200, 170), 4, .6, 10);
            }
            default -> {
                chain(from, to, c, 12);
                r.element.shapes(to, .6);
            }
        }
    }

    /** A chain stretched between two points. */
    static void chain(Location from, Location to, Color color, int ticks) {
        Vector span = to.toVector().subtract(from.toVector());
        double length = span.length();
        if (length < .1) {
            return;
        }
        Location mid = from.clone().add(span.clone().multiply(.5));
        Quaternionf aim = Vfx.facing(span);
        Vfx.model(VfxKind.CHAIN, mid, color)
                .pose(aim, Vfx.scale(.8, .8, .1))
                .to(1, 3, new Vector3f(), aim, Vfx.scale(.8, .8, length))
                .life(ticks, Math.max(3, ticks / 4));
    }
}
