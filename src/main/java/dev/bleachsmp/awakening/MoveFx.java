package dev.bleachsmp.awakening;

import static dev.bleachsmp.awakening.KitFx.BLACK;
import static dev.bleachsmp.awakening.KitFx.BONE;
import static dev.bleachsmp.awakening.KitFx.CERO_GREEN;
import static dev.bleachsmp.awakening.KitFx.EMBER;
import static dev.bleachsmp.awakening.KitFx.FLAME;
import static dev.bleachsmp.awakening.KitFx.GOLD;
import static dev.bleachsmp.awakening.KitFx.ICE;
import static dev.bleachsmp.awakening.KitFx.SAKURA;
import static dev.bleachsmp.awakening.KitFx.WHITE;
import static dev.bleachsmp.awakening.KitFx.at;
import static dev.bleachsmp.awakening.KitFx.chain;
import static dev.bleachsmp.awakening.KitFx.column;
import static dev.bleachsmp.awakening.KitFx.converge;
import static dev.bleachsmp.awakening.KitFx.cross;
import static dev.bleachsmp.awakening.KitFx.crown;
import static dev.bleachsmp.awakening.KitFx.every;
import static dev.bleachsmp.awakening.KitFx.flameRing;
import static dev.bleachsmp.awakening.KitFx.forward;
import static dev.bleachsmp.awakening.KitFx.glyphs;
import static dev.bleachsmp.awakening.KitFx.rain;
import static dev.bleachsmp.awakening.KitFx.radiate;
import static dev.bleachsmp.awakening.KitFx.rand;
import static dev.bleachsmp.awakening.KitFx.right;
import static dev.bleachsmp.awakening.KitFx.swarm;
import static dev.bleachsmp.awakening.KitFx.upcut;
import static dev.bleachsmp.awakening.KitFx.v;

import dev.bleachsmp.awakening.Canon.Move;
import dev.bleachsmp.awakening.ReleaseService.Release;
import dev.bleachsmp.fx.Effect;
import dev.bleachsmp.fx.Vfx;
import dev.bleachsmp.fx.VfxKind;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The look of every signature (Q) and advanced-form (F) ability. Each move gets its own choreography, drawn when it
 * fires, on top of whatever particles the move's own code spawns. Only resource-pack players see it.
 */
final class MoveFx {
    private MoveFx() {
    }

    // ================================================================ aiming

    /** The living thing the player is looking at within {@code range}, or null. */
    private static LivingEntity aimed(Player p, double range) {
        var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, .8,
                entity -> entity instanceof LivingEntity && !entity.equals(p));
        return hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    /** The ground spot the player is aiming at, up to {@code range} blocks away. */
    private static Location aimPoint(Player p, double range) {
        var hit = p.rayTraceBlocks(range);
        Location point = hit != null ? hit.getHitPosition().toLocation(p.getWorld())
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));
        Location ground = ReleaseService.groundAt(point);
        return ground != null ? ground : point;
    }

    /** The aimed target's body, else the aimed ground. */
    private static Location targetSpot(Player p, double range) {
        LivingEntity target = aimed(p, range);
        return target != null ? target.getLocation().add(0, target.getHeight() * .5, 0) : aimPoint(p, range).add(0, 1, 0);
    }

    private static List<LivingEntity> near(Player p, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity entity : p.getLocation().getNearbyLivingEntities(radius)) {
            if (!entity.equals(p)) {
                list.add(entity);
            }
        }
        return list;
    }

    private static Location eye(Player p) {
        return p.getEyeLocation().subtract(0, .25, 0);
    }

    private static Location ahead(Player p, double distance) {
        return eye(p).add(p.getEyeLocation().getDirection().multiply(distance));
    }

    private static Location ground(Location at) {
        Location ground = ReleaseService.groundAt(at);
        return ground != null ? ground : at;
    }

    /** A model travelling down the aim line, turning as it goes. */
    private static Effect shoot(Player p, VfxKind kind, Color color, Vector3f size, double distance, int ticks, double spin) {
        Vector dir = p.getEyeLocation().getDirection();
        Quaternionf aim = Vfx.facing(dir);
        Location from = eye(p).add(dir.clone().multiply(1));
        Effect effect = Vfx.model(kind, from, color).pose(new Vector3f(), aim, size);
        int steps = 3;
        for (int s = 1; s <= steps; s++) {
            effect.to(1 + (s - 1) * ticks / steps, ticks / steps, dir.clone().multiply(distance * s / steps).toVector3f(),
                    new Quaternionf(aim).rotateZ((float) (spin * s)), size);
        }
        return effect.life(ticks + 4, 4);
    }

    /** Keeps a model following the player for {@code ticks}. */
    private static void hold(Player p, Release r, String key, VfxKind kind, Color color, Vector offset, Quaternionf pose,
            Vector3f scale, int ticks) {
        every(ticks, 2, tick -> {
            if (!KitFx.live(p, r)) {
                return;
            }
            Vfx.keep(key + p.getUniqueId(), 6, () -> Vfx.model(kind, p.getLocation().add(offset), color)
                    .follow(p, offset).pose(pose, scale)).offset(offset).move(2, new Vector3f(), pose, scale);
        });
    }

    /** An aura of the given shape orbiting the player while a mode lasts. */
    private static void modeOrbit(Player p, Release r, String key, VfxKind kind, Color color, int count, double radius,
            double height, double size, int ticks) {
        every(ticks, 2, tick -> {
            if (KitFx.live(p, r)) {
                Vfx.orbit(key + p.getUniqueId(), p, kind, color, count, radius, height, size, tick * .1, true);
            }
        });
    }

    // ================================================================ signature (Q)

    static void signature(Player p, Release r) {
        Move move = r.kit.signature();
        Color c = r.form.color();
        Location body = p.getLocation().add(0, 1, 0);
        Vector fwd = forward(p);
        Vector dir = p.getEyeLocation().getDirection();
        int mode = r.advanced ? 300 : 200;
        switch (move) {
            // ------------------------------------------------------------ Soul Reapers
            case JUJISHO -> {
                // Two Getsuga Tenshō crossed into one: an X of black and blue flying down the aim.
                for (int s = -1; s <= 1; s += 2) {
                    Quaternionf aim = Vfx.facing(dir).rotateZ((float) (Math.PI / 2 + s * .78));
                    Vfx.model(VfxKind.SLASH, ahead(p, 1.5), s < 0 ? BLACK : Color.fromRGB(150, 210, 255))
                            .pose(new Vector3f(), aim, Vfx.scale(2.6, 1.5, 2.6))
                            .to(1, 8, dir.clone().multiply(24).toVector3f(), aim, Vfx.scale(3.2, 1.5, 3.2)).life(12, 4);
                }
            }
            case JOKAKU_ENJO -> {
                // A ring of fire walls: a wall of flame tongues that keeps burning.
                Location base = p.getLocation();
                int ticks = (int) (160 * (r.advanced ? 1.5 : 1));
                every(ticks, 8, tick -> flameRing(base, FLAME, 22, 7 * (r.advanced ? 1.22 : 1), 3.6, 14));
                Vfx.model(VfxKind.RING, base.clone().add(0, .1, 0), FLAME).pose(new Quaternionf(), Vfx.scale(1, 2, 1))
                        .grow(1, 8, Vfx.scale(7.5, 2, 7.5)).life(ticks, 10);
            }
            case FERAL_HUNT -> {
                // The eyepatch comes off: a burst of wild yellow reiatsu.
                column(p.getLocation(), GOLD, 1.6, 7, 6, 10);
                Vfx.drops(body, Color.fromRGB(150, 0, 20), 12, 1.3, 14);
                Vfx.flash(VfxKind.CLAW, body.clone().add(fwd), GOLD, Vfx.facing(fwd), Vfx.scale(2), 10);
            }
            case THOUSAND_PETALS -> {
                // Senbonzakura: a cloud of a thousand blade petals that follows the crosshair.
                every(120, 3, tick -> {
                    if (KitFx.live(p, r)) {
                        Location aim = ahead(p, 9);
                        Vfx.petals(aim, SAKURA, 5, 1.8, 10);
                        Vfx.orbit("senbon:" + p.getUniqueId(), p, VfxKind.PETAL, SAKURA, 6, 1.2, 1.1, 1, tick * .2, false);
                    }
                });
            }
            case METEOR_LANCE -> {
                // Full Extension: the blade's light gathers, then it extends in a piercing thrust.
                converge(VfxKind.FLARE, ahead(p, 1.2), c, 6, 2, .4, 8);
                at(8, () -> {
                    Vector aim = p.getEyeLocation().getDirection();
                    Vfx.model(VfxKind.SPEAR, ahead(p, 1), Color.fromRGB(225, 225, 240)).pose(Vfx.facing(aim), Vfx.scale(.8, .8, .5))
                            .to(1, 2, Vfx.facing(aim), Vfx.scale(.8, .8, 22)).to(7, 3, Vfx.facing(aim), Vfx.scale(.8, .8, .5))
                            .life(11, 2);
                });
            }
            case KANZEN_SAIMIN -> {
                Vfx.flash(VfxKind.EYE, p.getLocation().add(0, 3.5, 0), c, new Quaternionf(), Vfx.scale(2.6), 30);
                radiate(VfxKind.HEX, body, Color.fromRGB(190, 170, 255), 10, 7, .7, 10, 1.5);
                Vfx.shockwave(KitFx.plugin(), p.getLocation(), c, 16, 14);
            }
            case IROONI -> {
                // Irooni: four colours to call, circling the chosen enemy.
                Location spot = targetSpot(p, 16);
                Color[] colours = {BLACK, WHITE, Color.fromRGB(220, 30, 40), Color.fromRGB(40, 90, 230)};
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2;
                    Vfx.model(VfxKind.FLARE, spot, colours[i])
                            .pose(v(Math.cos(a) * 1.2, .6, Math.sin(a) * 1.2), new Quaternionf(), Vfx.scale(.8))
                            .to(1, 20, v(Math.cos(a + 3) * 1.2, .6, Math.sin(a + 3) * 1.2), new Quaternionf(), Vfx.scale(.8))
                            .life(24, 6);
                }
                Vfx.glyph(VfxKind.CARD, spot.clone().add(0, 1.6, 0), c, .6, 24);
            }
            case SAKANADE -> {
                // The world turns upside down: sideways rings and inverted notes.
                for (int i = 0; i < 3; i++) {
                    Vfx.model(VfxKind.RING, body, c).pose(new Quaternionf().rotateZ(1.57f).rotateX(i), Vfx.scale(.4))
                            .to(1, 8, new Quaternionf().rotateZ(1.57f).rotateX(i + 2f), Vfx.scale(8, 1.2, 8))
                            .life(16, 6);
                }
                glyphs(p, VfxKind.NOTE, c, 8, 6, .7, 24);
                Vfx.smoke(body, Color.fromRGB(240, 225, 150), 8, 5, 1.2, 30);
            }
            case HYOTEN -> {
                // Hyōten Hyakkasō: the sky opens and snow falls; every flake that lands blooms into an ice flower.
                Location spot = aimPoint(p, 24);
                Vfx.model(VfxKind.RING, spot.clone().add(0, 10, 0), WHITE).pose(new Quaternionf(), Vfx.scale(.5, 2, .5))
                        .grow(1, 10, Vfx.scale(6, 2, 6)).life(60, 14);
                Vfx.flash(VfxKind.ORB, spot.clone().add(0, 10, 0), ICE, new Quaternionf(), Vfx.scale(5, .3, 5), 60);
                rain(VfxKind.SHARD, spot, WHITE, 30, 5, .35, 50, 9);
                at(20, () -> {
                    for (int i = 0; i < 5; i++) {
                        KitFx.iceFlower(spot.clone().add(rand(4), .4, rand(4)), .9, 50);
                    }
                });
            }
            case TSUKISHIRO -> {
                // Some no Mai, Tsukishiro: a circle opens under the enemy and freezes from the ground to the sky.
                Location spot = ground(targetSpot(p, 20));
                Vfx.sigil(VfxKind.RING, spot, WHITE, 2.5, 30);
                at(6, () -> {
                    column(spot, WHITE, 2.5, 14, 4, 14);
                    Vfx.erupt(VfxKind.SHARD, spot, ICE, 10, 2.2, 2, 40);
                });
            }
            case HIGA_ZEKKO -> {
                // Higa Zekkō: the blade's segments scatter around the enemy, then all fang in at once.
                Location spot = targetSpot(p, 18);
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5;
                    double h = rand(1.2);
                    Location from = spot.clone().add(Math.cos(a) * 3.5, h, Math.sin(a) * 3.5);
                    Vector in = spot.toVector().subtract(from.toVector());
                    Vfx.model(VfxKind.FANG, from, BONE).pose(new Vector3f(), Vfx.facing(in), Vfx.scale(1.8))
                            .to(8, 3, in.toVector3f(), Vfx.facing(in), Vfx.scale(1.8)).life(14, 3);
                }
            }
            case HAINEKO -> {
                // The blade turns to ash that swirls around its owner.
                every(80, 4, tick -> {
                    if (KitFx.live(p, r)) {
                        Vfx.orbit("haineko:" + p.getUniqueId(), p, VfxKind.SMOKE, Color.fromRGB(215, 195, 165), 6, 1.8, 1,
                                .9, tick * .2, false);
                    }
                });
            }
            case NIGEKI -> {
                Vfx.glyph(VfxKind.BUTTERFLY, body.clone().add(fwd), GOLD, 1, 24);
                modeOrbit(p, r, "nigeki:", VfxKind.BUTTERFLY, GOLD, 2, .9, 1.4, .35, mode);
            }
            case TENKEN -> {
                // A giant armoured arm appears overhead and slams down where the player aims.
                Location spot = aimPoint(p, 24);
                Vfx.model(VfxKind.FIST, spot.clone().add(0, 9, 0), Color.fromRGB(150, 110, 70))
                        .pose(Vfx.facing(fwd), Vfx.scale(1.5))
                        .to(6, 4, v(0, -8.4, 0), Vfx.facing(fwd), Vfx.scale(2.2)).life(26, 8);
                at(10, () -> {
                    Vfx.crater(spot, 7, 120);
                    Vfx.erupt(VfxKind.SPIKE, spot, r.element.tint, 10, 3.2, 1.6, 30);
                    Vfx.scatter(VfxKind.ROCK, spot.clone().add(0, .5, 0), r.element.tint, 10, 2, 1, 18);
                });
            }
            case WABISUKE -> modeOrbit(p, r, "wabisuke:", VfxKind.RING, c, 1, .01, .1, 1.4, mode);
            case TACHIKAZE -> {
                // A wind blade thrown out, detonating in a blast of air at the end.
                shoot(p, VfxKind.GUST, c, Vfx.scale(2.4, 1, 2.4), 20, 8, 3);
                Location end = ahead(p, 21);
                at(8, () -> {
                    Vfx.whirl(end, WHITE, 3, 12);
                    Vfx.burst(end, c, 40, 1.2, 2f);
                });
            }
            case MINAZUKI -> {
                // The manta: a great ray of water sweeping over the caster and its allies.
                KitFx.wings(p, Color.fromRGB(90, 200, 170), 2.4, 30, .05);
                Vfx.drops(body.clone().add(0, 1, 0), Color.fromRGB(110, 220, 180), 16, 1.6, 20);
                Vfx.sigil(VfxKind.RING, KitFx.ground(p), Color.fromRGB(90, 200, 170), 5, 30);
            }
            case SOGYO -> {
                // The twin blades take the blow in and send it back: talismans on the rope.
                crown(VfxKind.CARD, body, WHITE, 8, 1.6, .5, 60, 3, true);
                KitFx.chain(body.clone().add(right(p).multiply(-1.4)), body.clone().add(right(p).multiply(1.4)), WHITE, 60);
            }
            case ASHISOGI -> modeOrbit(p, r, "ashisogi:", VfxKind.SMOKE, c, 3, 1, .8, .6, mode);
            // ------------------------------------------------------------ Hollows
            case SONIDO_STORM -> {
                Location spot = targetSpot(p, 14);
                for (int i = 0; i < 6; i++) {
                    int k = i;
                    at(i * 2, () -> {
                        Vector out = new Vector(Math.cos(k * 1.05), rand(.4), Math.sin(k * 1.05));
                        Vfx.flash(VfxKind.CLAW, spot.clone().add(out), c, Vfx.facing(out.clone().multiply(-1)), Vfx.scale(1.4), 6);
                    });
                }
            }
            case IRA -> {
                Vfx.model(VfxKind.AURA, p.getLocation(), Color.fromRGB(200, 40, 20)).pose(new Quaternionf(), Vfx.scale(1.2, 2.4, 1.2))
                        .grow(1, 10, Vfx.scale(2, 4, 2)).life(30, 10);
                Vfx.scatter(VfxKind.ROCK, p.getLocation().add(0, .3, 0), Color.fromRGB(150, 110, 80), 8, 1.6, 1, 16);
            }
            case HIERRO -> {
                crown(VfxKind.PLATE, body, Color.fromRGB(200, 200, 190), 8, 1.1, 1.2, 40, 0, true);
                Vfx.flash(VfxKind.HEX, body.clone().add(fwd), WHITE, Vfx.facing(fwd), Vfx.scale(1.6), 14);
            }
            case AMOR -> {
                // Amor: eyes fly from the body to every enemy around and stick to them.
                for (LivingEntity entity : near(p, 16)) {
                    Location to = entity.getLocation().add(0, entity.getHeight() + .3, 0);
                    Vfx.fly(VfxKind.EYE, body, to, c, Vfx.scale(.6), 8, 40);
                }
            }
            case DEVOUR -> {
                // A maw opens and lunges at the target.
                Location spot = targetSpot(p, 12);
                Vector in = spot.toVector().subtract(body.toVector());
                Color maw = r.faction == dev.bleachsmp.faction.Faction.QUINCY ? c : Color.fromRGB(70, 40, 100);
                Vfx.model(VfxKind.HEAD, body.clone().add(fwd), maw).pose(new Vector3f(), Vfx.facing(in), Vfx.scale(1))
                        .to(1, 5, in.clone().multiply(.85).toVector3f(), Vfx.facing(in), Vfx.scale(2.4))
                        .grow(8, 3, Vfx.scale(1.4)).life(14, 3);
            }
            case VOODOO -> {
                Location spot = targetSpot(p, 16);
                Vfx.flash(VfxKind.MASK, body.clone().add(fwd).add(0, .3, 0), Color.fromRGB(240, 140, 210), Vfx.facing(fwd), Vfx.scale(.8), 30);
                chain(body.clone().add(fwd), spot, Color.fromRGB(240, 120, 200), 30);
            }
            case LUZ_LANZA -> {
                Location end = targetSpot(p, 30);
                Vfx.fly(VfxKind.SPEAR, eye(p).add(fwd), end, CERO_GREEN, Vfx.scale(1.4, 1.4, 2.4), 6, 2);
                at(6, () -> {
                    Vfx.burst(end, CERO_GREEN, 40, 1, 2f);
                    Vfx.model(VfxKind.RING, end, CERO_GREEN).pose(Vfx.facing(dir).rotateX(1.57f), Vfx.scale(.4))
                            .grow(1, 6, Vfx.scale(3.5)).life(8, 4);
                });
            }
            case LOBOS -> {
                for (int i = 0; i < 6; i++) {
                    int k = i;
                    at(i * 3, () -> {
                        Vector out = forward(p).rotateAroundY((k - 2.5) * .45);
                        Location from = p.getLocation().add(0, .8, 0);
                        Vfx.fly(VfxKind.HEAD, from, from.clone().add(out.multiply(12)), c, Vfx.scale(1), 10, 4);
                    });
                }
            }
            case CASCADA -> {
                // A waterfall poured out in front.
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    at(i * 3, () -> Vfx.wave(p.getLocation().add(fwd.clone().multiply(1 + k)), fwd, Color.fromRGB(60, 150, 235),
                            4, 3, 12, 14));
                }
                rain(VfxKind.DROP, p.getLocation().add(fwd.clone().multiply(6)), Color.fromRGB(70, 160, 240), 20, 3, 1.6, 20, 5);
            }
            case REMOLINO -> {
                for (int s = -1; s <= 1; s += 2) {
                    Location start = p.getLocation().add(right(p).multiply(s * 1.5));
                    Vector way = fwd.clone().rotateAroundY(s * .25);
                    every(30, 3, tick -> {
                        Location at = start.clone().add(way.clone().multiply(tick * .5));
                        for (int h = 0; h < 3; h++) {
                            Vfx.whirl(at.clone().add(0, h * .9, 0), WHITE, .8 + h * .4, 6);
                        }
                    });
                }
            }
            case GOLONDRINA -> {
                for (int i = -4; i <= 4; i++) {
                    Vector way = dir.clone().rotateAroundY(i * .1);
                    Vfx.fly(VfxKind.FEATHER, body.clone().add(fwd), body.clone().add(way.multiply(18)), c,
                            Vfx.scale(1.4), 8, 2);
                }
            }
            case THUNDER_STEP -> {
                for (int i = 0; i < 4; i++) {
                    Vfx.zap(body, body.clone().add(rand(3), rand(1), rand(3)), c, .14);
                }
                KitFx.head(p, c, .9, 12, 0, 1.5);
            }
            case RESPIRA -> {
                // Respira: a breath of decay rolls out in a cone.
                for (int i = 0; i < 4; i++) {
                    int k = i;
                    at(i * 3, () -> Vfx.smoke(eye(p).add(dir.clone().multiply(2 + k * 2.5)), Color.fromRGB(40, 25, 55),
                            4 + k, .6 + k * .5, 1.2 + k * .3, 24));
                }
            }
            case DRAGON_RUGIR -> {
                KitFx.head(p, FLAME, 1.6, 16, -.5, 1.4);
                at(6, () -> {
                    Location from = eye(p).add(dir.clone().multiply(2));
                    Vfx.model(VfxKind.BEAM, from, FLAME).pose(Vfx.facing(dir), Vfx.scale(1.6, 1.6, 18))
                            .grow(1, 8, Vfx.scale(.3, .3, 18)).life(10, 5);
                    for (int i = 0; i < 6; i++) {
                        Vfx.flame(ground(from.clone().add(dir.clone().multiply(3 + i * 2.5))), FLAME, 1.6, 14);
                    }
                });
            }
            case BULL_CHARGE -> {
                KitFx.dashTrail(r, p, 0);
                every(14, 2, tick -> {
                    if (KitFx.live(p, r)) {
                        Vfx.scatter(VfxKind.ROCK, p.getLocation().add(0, .2, 0), r.element.tint, 2, .8, .5, 10);
                    }
                });
            }
            case CERO_DOBLE -> {
                // Swallows the attack and spits it back: an orb drawn into the mouth.
                converge(VfxKind.FLARE, eye(p).add(fwd.clone().multiply(.6)), CERO_GREEN, 8, 3, .6, 8);
                Vfx.flash(VfxKind.ORB, eye(p).add(fwd.clone().multiply(.6)), CERO_GREEN, new Quaternionf(), Vfx.scale(.8), 30);
            }
            case TREPADORA -> {
                for (LivingEntity entity : near(p, 10)) {
                    Location to = entity.getLocation().add(0, entity.getHeight() * .5, 0);
                    Vfx.fallbackLine(body, to, c, 1, .4);
                    Vector span = to.toVector().subtract(body.toVector());
                    Vfx.model(VfxKind.BEAM, body, c).pose(Vfx.facing(span), Vfx.scale(.3, .3, .1))
                            .grow(1, 3, Vfx.scale(.3, .3, span.length())).life(14, 5);
                    Vfx.flash(VfxKind.TENTACLE, to.clone().subtract(0, .5, 0), c, Vfx.facing(span), Vfx.scale(1.2), 14);
                }
            }
            case IRON_CONSTRUCT -> {
                // Quimera Parca: Ayon rises behind its masters.
                Location behind = p.getLocation().add(fwd.clone().multiply(-2.5)).add(0, 2.5, 0);
                Vfx.model(VfxKind.HEAD, behind, Color.fromRGB(60, 45, 35)).pose(Vfx.facing(fwd.clone().setY(.2)), Vfx.scale(.4))
                        .grow(1, 10, Vfx.scale(3.6)).life(40, 10);
                Vfx.smoke(behind, BLACK, 8, 2, 1.4, 30);
            }
            // ------------------------------------------------------------ Quincy
            case BURNER3 -> {
                // Burner Finger 3: a sword of fire swept around the body.
                Effect sword = Vfx.model(VfxKind.BLADE, body, FLAME);
                for (int s = 0; s <= 4; s++) {
                    Quaternionf pose = Vfx.facing(fwd.clone().rotateAroundY(-1.4 + s * .7)).rotateX(1.57f);
                    if (s == 0) {
                        sword.pose(pose, Vfx.scale(4));
                    } else {
                        sword.to(1 + (s - 1) * 2, 2, pose, Vfx.scale(4));
                    }
                }
                sword.life(12, 4);
                flameRing(p.getLocation(), FLAME, 12, 3, 1.8, 14);
            }
            case OVERKILL -> modeOrbit(p, r, "overkill:", VfxKind.DROP, c, 4, .8, 1.2, 1.2, mode);
            case POWER_PUNCH -> {
                Vfx.fly(VfxKind.FIST, body.clone().add(fwd), body.clone().add(dir.clone().multiply(6)), c,
                        Vfx.scale(.9), 4, 6);
                at(4, () -> Vfx.shockwave(KitFx.plugin(), ground(body.clone().add(dir.clone().multiply(6))), c, 4, 8));
            }
            case FEAR -> {
                for (LivingEntity entity : near(p, 14)) {
                    Location g = ground(entity.getLocation());
                    Vfx.erupt(VfxKind.SPIKE, g, Color.fromRGB(60, 25, 50), 5, 1, 1.6, 30);
                    Vfx.glyph(VfxKind.EYE, entity.getLocation().add(0, entity.getHeight() + .6, 0), Color.fromRGB(190, 40, 80), .6, 30);
                }
            }
            case VISIONARY -> {
                swarm(p, VfxKind.STAR, c, 16, 2.4, 3, .5, 30);
                Vfx.glyph(VfxKind.EYE, p.getLocation().add(0, 3, 0), c, 1.2, 24);
            }
            case LOVE_KISS -> {
                Location spot = targetSpot(p, 18);
                Vfx.fly(VfxKind.HEART, body.clone().add(fwd), spot, c, Vfx.scale(1.2), 8, 30);
            }
            case X_AXIS -> {
                // The X-Axis: whatever lies on the line is pierced; an X burns at the muzzle.
                Location from = eye(p).add(dir);
                Vfx.model(VfxKind.BEAM, from, c).pose(Vfx.facing(dir), Vfx.scale(.6, .6, 40))
                        .grow(1, 10, Vfx.scale(.05, .05, 40)).life(12, 6);
                Vector side = right(p);
                for (int s = -1; s <= 1; s += 2) {
                    Vector diag = side.clone().multiply(s).add(new Vector(0, 1, 0)).normalize();
                    Vfx.line(from.clone().subtract(diag.clone().multiply(1.2)), from.clone().add(diag.clone().multiply(1.2)), c, 1.6f, .3);
                }
            }
            case ANTITHESIS -> {
                // The Antithesis: two mirrored sigils, and the wounds trade places between them.
                Location spot = targetSpot(p, 20);
                Vfx.sigil(VfxKind.SIGIL, KitFx.ground(p), c, 1.4, 30);
                Vfx.sigil(VfxKind.SIGIL, ground(spot), c, 1.4, 30);
                chain(body, spot, c, 20);
            }
            case GALVANO_BLAST -> {
                Location end = targetSpot(p, 24);
                for (int i = 0; i < 4; i++) {
                    int k = i;
                    at(i, () -> Vfx.zap(body.clone().add(fwd), end, k % 2 == 0 ? c : WHITE, .2));
                }
                at(4, () -> Vfx.burst(end, c, 40, 1, 2f));
            }
            case WIND -> {
                for (int i = 0; i < 6; i++) {
                    Vfx.gust(body, fwd.clone().rotateAroundY(i * Math.PI / 3), c, 2, 10);
                }
            }
            case STAR_CHEER -> {
                glyphs(p, VfxKind.STAR, GOLD, 14, 2.4, .7, 24);
                column(p.getLocation(), GOLD, 1.2, 6, 6, 10);
            }
            case EXPLODE -> modeOrbit(p, r, "explode:", VfxKind.ORB, c, 3, .9, 1.2, .3, mode);
            case EISERN -> {
                KitFx.armUp(p, Color.fromRGB(170, 170, 180), 8, 1.1);
                Vfx.model(VfxKind.AURA, p.getLocation(), Color.fromRGB(160, 160, 170)).pose(new Quaternionf(), Vfx.scale(1, 2.3, 1))
                        .life(60, 10);
            }
            case MIRACLE -> {
                modeOrbit(p, r, "miracle:", VfxKind.STAR, GOLD, 3, 1, 2.2, .4, mode);
                Vfx.model(VfxKind.RING, p.getLocation().add(0, 2.4, 0), GOLD).follow(p, new Vector(0, 2.4, 0))
                        .pose(new Quaternionf(), Vfx.scale(.45, 1, .45)).life(mode, 10);
            }
            case BALANCE -> modeOrbit(p, r, "balance:", VfxKind.HEX, c, 2, 1.1, 1.1, .7, mode);
            case ZOMBIE -> {
                Location spot = targetSpot(p, 16);
                Vfx.drops(spot, c, 14, 1.2, 16);
                Vfx.flash(VfxKind.MASK, spot.clone().add(0, 1, 0), WHITE, Vfx.facing(body.toVector().subtract(spot.toVector())),
                        Vfx.scale(1), 24);
            }
            case BONE_CAGE -> {
                // The Jail: bars of light close around the enemy.
                Location spot = ground(targetSpot(p, 18));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    column(spot.clone().add(Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3), WHITE, .12, 3, 5, 60);
                }
                Vfx.model(VfxKind.RING, spot.clone().add(0, 3, 0), WHITE).pose(new Quaternionf(), Vfx.scale(1.3, 1, 1.3)).life(70, 10);
            }
            // ------------------------------------------------------------ Fullbringers
            case JACKPOT -> {
                Location row = eye(p).add(fwd.clone().multiply(1.6)).add(0, .5, 0);
                for (int i = -1; i <= 1; i++) {
                    Quaternionf rest = Vfx.facing(fwd.clone().multiply(-1));
                    Vfx.model(VfxKind.CARD, row.clone().add(right(p).multiply(i * .7)), i == 0 ? GOLD : c)
                            .pose(new Quaternionf(rest), Vfx.scale(.6))
                            .to(1, 8, new Vector3f(), new Quaternionf(rest).rotateX(9), Vfx.scale(.6))
                            .to(10 + (i + 1) * 3, 4, new Vector3f(), rest, Vfx.scale(.7)).life(30, 6);
                }
            }
            case EL_DIRECTO -> {
                Location from = body.clone().add(fwd).add(right(p).multiply(.3));
                Vfx.fly(VfxKind.FIST, from, from.clone().add(dir.clone().multiply(14)), c, Vfx.scale(1.4), 6, 4);
                Vfx.model(VfxKind.BEAM, from, c).pose(Vfx.facing(dir), Vfx.scale(1.4, 1.4, 14))
                        .grow(2, 8, Vfx.scale(.2, .2, 14)).life(10, 5);
            }
            case INSERT -> {
                Location spot = targetSpot(p, 14);
                crown(VfxKind.PAGE, spot, WHITE, 6, 1.2, .6, 24, 2, true);
                Vfx.flash(VfxKind.BLADE, spot.clone().subtract(0, 1, 0), Color.fromRGB(160, 160, 210),
                        Vfx.facing(fwd).rotateX(.5f), Vfx.scale(1.6), 10);
            }
            case LA_MUERTE -> {
                Location end = targetSpot(p, 24);
                Vfx.fly(VfxKind.MASK, body.clone().add(fwd), end, WHITE, Vfx.scale(2.6), 8, 2);
                at(8, () -> {
                    Vfx.burst(end, c, 50, 1.4, 2f);
                    Vfx.model(VfxKind.ORB, end, c).pose(new Quaternionf(), Vfx.scale(.4)).grow(1, 6, Vfx.scale(4)).life(10, 4);
                });
            }
            case CROSS_STEAL -> {
                Location spot = targetSpot(p, 12);
                cross(spot, fwd, Color.fromRGB(120, 230, 160), 1.6, 6);
                at(6, () -> converge(VfxKind.FLARE, body, Color.fromRGB(120, 230, 160), 8, 3, .4, 8));
            }
            case SKY_STEPS -> every(100, 4, tick -> {
                if (KitFx.live(p, r) && !p.isOnGround()) {
                    Vfx.flash(VfxKind.HEX, p.getLocation().subtract(0, .05, 0), c, new Quaternionf().rotateX(1.57f),
                            Vfx.scale(1.2), 8);
                }
            });
            case DIRTY_BOOTS -> {
                for (int s = -1; s <= 1; s += 2) {
                    Location foot = p.getLocation().add(right(p).multiply(s * .2)).add(0, .15, 0);
                    Vfx.scatter(VfxKind.ROCK, foot, Color.fromRGB(100, 75, 45), 5, .9, .5, 12);
                }
                modeOrbit(p, r, "boots:", VfxKind.ROCK, Color.fromRGB(100, 75, 45), 3, .6, .15, .5, mode);
            }
            case DOLLHOUSE -> {
                // A heart-shaped mark, then a dollhouse of light around the target.
                Location spot = targetSpot(p, 16);
                Vfx.fly(VfxKind.HEART, body.clone().add(fwd), spot, c, Vfx.scale(1), 6, 4);
                at(6, () -> Vfx.model(VfxKind.ORB, spot, c).tone(2).pose(new Quaternionf(), Vfx.scale(.3))
                        .grow(1, 6, Vfx.scale(2.6)).life(60, 10));
            }
            case INVADERS -> {
                for (LivingEntity entity : near(p, 16)) {
                    Vfx.fly(VfxKind.INVADER, body.clone().add(0, 1.2, 0), entity.getLocation().add(0, entity.getHeight() + .6, 0),
                            c, Vfx.scale(.7), 10, 30);
                }
            }
            case SOTEN -> {
                // Sōten Kisshun: a dome that rejects the wound.
                Vfx.model(VfxKind.ORB, p.getLocation().add(0, 1, 0), Color.fromRGB(255, 200, 140)).tone(1)
                        .pose(new Quaternionf(), Vfx.scale(.4)).grow(1, 6, Vfx.scale(2.6, 3, 2.6)).life(60, 10);
                Vfx.petals(body, Color.fromRGB(255, 210, 160), 16, 2, 30);
            }
            case TIME_BET -> {
                Location spot = targetSpot(p, 16);
                Vfx.flash(VfxKind.CLOCK, spot.clone().add(0, 1.2, 0), c, Vfx.facing(body.toVector().subtract(spot.toVector())),
                        Vfx.scale(1.2), 40);
                chain(body, spot, c, 30);
            }
            default -> r.element.shapes(body.clone().add(fwd), 1.4);
        }
    }

    // ================================================================ advanced (F)

    static void special(Player p, Release r) {
        Move move = r.kit.special();
        Color c = r.form.color();
        Location base = p.getLocation();
        Location body = base.clone().add(0, 1, 0);
        Vector fwd = forward(p);
        Vector dir = p.getEyeLocation().getDirection();
        switch (move) {
            // ------------------------------------------------------------ Soul Reapers
            case TENSA -> {
                // Kuroi Getsuga: a giant black crescent with a crimson edge, then a black mantle of speed.
                for (int k = 0; k < 2; k++) {
                    Quaternionf aim = Vfx.facing(dir).rotateZ(.35f);
                    Vfx.model(VfxKind.SLASH, ahead(p, 1.5), k == 0 ? BLACK : Color.fromRGB(200, 20, 40))
                            .pose(new Vector3f(), aim, Vfx.scale(3.5 - k * .5, 1.6, 3.5 - k * .5))
                            .to(1, 10, dir.clone().multiply(32).toVector3f(), aim, Vfx.scale(5 - k * .6, 1.6, 5 - k * .6))
                            .life(14, 4);
                }
                every(400, 4, tick -> {
                    if (KitFx.live(p, r)) {
                        Vfx.smoke(p.getLocation().add(0, .3, 0), BLACK, 1, .4, .7, 12);
                    }
                });
            }
            case ZANKA -> {
                // Higashi: one white-hot cut; Nishi: the air itself burns.
                Location from = eye(p).add(dir);
                Vfx.model(VfxKind.BEAM, from, Color.fromRGB(255, 240, 200)).pose(Vfx.facing(dir), Vfx.scale(.3, 2.4, 40))
                        .grow(1, 10, Vfx.scale(.05, .1, 40)).life(12, 6);
                Vfx.flash(VfxKind.BLADE, body.clone().add(fwd).subtract(0, .9, 0), Color.fromRGB(255, 240, 200), Vfx.facing(fwd), Vfx.scale(2.4), 20);
                every(240, 6, tick -> {
                    if (KitFx.live(p, r)) {
                        flameRing(p.getLocation(), tick % 12 == 0 ? EMBER : Color.fromRGB(255, 240, 200), 6, 2.6, 2.2, 10);
                    }
                });
            }
            case BERSERKER -> {
                column(base, Color.fromRGB(220, 30, 30), 1.8, 9, 8, 10);
                every(400, 2, tick -> {
                    if (KitFx.live(p, r)) {
                        Vfx.keep("berserk:" + p.getUniqueId(), 6, () -> Vfx.model(VfxKind.AURA, p.getLocation(),
                                Color.fromRGB(220, 30, 30)).follow(p, new Vector()).pose(new Quaternionf(), Vfx.scale(1.4, 2.8, 1.4)))
                                .move(2, new Vector3f(), new Quaternionf().rotateY(tick * .2f), Vfx.scale(1.4, 2.8, 1.4));
                    }
                });
            }
            case MUGEN_KYOKA -> {
                for (LivingEntity entity : near(p, 20)) {
                    Vfx.glyph(VfxKind.EYE, entity.getLocation().add(0, entity.getHeight() + .7, 0), c, .8, 60);
                }
                Vfx.shockwave(KitFx.plugin(), base, c, 20, 16);
            }
            case KARAMATSU -> {
                // A tragedy in four acts, played out on one enemy.
                Location spot = targetSpot(p, 16);
                Vfx.model(VfxKind.ORB, spot, BLACK).tone(1).pose(new Quaternionf(), Vfx.scale(.3)).grow(1, 10, Vfx.scale(6))
                        .life(120, 12);
                rain(VfxKind.PETAL, ground(spot), SAKURA, 30, 2.6, 1, 100, 4);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2;
                    chain(spot.clone().add(Math.cos(a) * 2.5, 2.5, Math.sin(a) * 2.5), spot, Color.fromRGB(200, 30, 60), 110);
                }
                at(100, () -> upcut(spot, fwd, Color.fromRGB(220, 30, 50), 2.6, 6, 1.4));
            }
            case HAPPO_FUSAGARI -> {
                for (LivingEntity entity : near(p, 16)) {
                    Location head = entity.getLocation().add(0, entity.getHeight() + .5, 0);
                    Vfx.model(VfxKind.RING, head, c).pose(new Quaternionf().rotateZ(1.3f), Vfx.scale(.5))
                            .to(1, 30, new Quaternionf().rotateZ(1.3f).rotateX(8), Vfx.scale(.6)).life(60, 10);
                }
                glyphs(p, VfxKind.NOTE, c, 12, 8, .8, 40);
            }
            case DAIGUREN -> {
                // Sennen Hyōrō: walls of ice rise in rings and close on the area.
                Location spot = aimPoint(p, 28);
                KitFx.wings(p, ICE, 3, 50, .25);
                for (int ring = 0; ring < 3; ring++) {
                    int k = ring;
                    at(ring * 6, () -> Vfx.erupt(VfxKind.SHARD, spot, ICE, 18 - k * 4, 6 - k * 2, 4 - k * .6, 60 - k * 6));
                }
                at(18, () -> column(spot, WHITE, 2, 10, 4, 20));
            }
            case KAGEYOSHI -> {
                // Senkei: rows of a thousand swords wall in everyone nearby.
                for (int ring = 0; ring < 2; ring++) {
                    double radius = 7 + ring * 1.2;
                    int count = 26 + ring * 6;
                    for (int i = 0; i < count; i++) {
                        double a = i * Math.PI * 2 / count;
                        Location g = ground(base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
                        int delay = i / 4;
                        at(delay, () -> Vfx.model(VfxKind.BLADE, g, Color.fromRGB(255, 225, 235))
                                .pose(new Quaternionf().rotateY((float) -a), Vfx.scale(1.5, .1, 1.5))
                                .to(1, 5, new Quaternionf().rotateY((float) -a), Vfx.scale(2.4, 5, 2.4)).life(140 - delay, 14));
                    }
                }
                every(120, 6, tick -> Vfx.petals(base.clone().add(rand(5), 1 + Math.random() * 2, rand(5)), SAKURA, 6, 2, 14));
            }
            case KAMISHINI -> {
                // Korose: a sixty-block thrust leaves a sliver inside; three seconds later it dissolves them.
                Location from = eye(p).add(dir);
                Vfx.model(VfxKind.SPEAR, from, Color.fromRGB(230, 230, 245)).pose(Vfx.facing(dir), Vfx.scale(.9, .9, .6))
                        .to(1, 2, Vfx.facing(dir), Vfx.scale(.9, .9, 60)).to(6, 2, Vfx.facing(dir), Vfx.scale(.9, .9, .6))
                        .life(9, 2);
                LivingEntity target = aimed(p, 60);
                if (target != null) {
                    at(60, () -> {
                        if (target.isValid()) {
                            Location at = target.getLocation().add(0, target.getHeight() * .5, 0);
                            Vfx.smoke(at, VENOM, 10, 1, 1.2, 30);
                            Vfx.drops(at, Color.fromRGB(120, 30, 60), 16, 1.4, 16);
                            Vfx.burst(at, VENOM, 50, 1, 2f);
                        }
                    });
                }
            }
            case HAKKA -> {
                Vfx.smoke(base.clone().add(0, .3, 0), WHITE, 16, 5, 1.6, 50);
                Vfx.model(VfxKind.RING, base.clone().add(0, .1, 0), WHITE).pose(new Quaternionf(), Vfx.scale(.5, 1, .5))
                        .grow(1, 12, Vfx.scale(7, 1, 7)).life(50, 12);
                at(8, () -> Vfx.erupt(VfxKind.SHARD, base, WHITE, 16, 5, 1.8, 60));
            }
            case HIKOTSU -> {
                // Hikotsu Taihō: the serpent's jaws open and fire a huge blast.
                // The serpent rears above and ahead of its master, so it never fills the screen.
                Location mouth = eye(p).add(fwd.clone().multiply(3.5)).add(0, 2, 0);
                Vfx.model(VfxKind.HEAD, mouth, BONE).pose(Vfx.facing(dir), Vfx.scale(.6)).grow(1, 6, Vfx.scale(2.6)).life(30, 8);
                at(8, () -> {
                    Vfx.model(VfxKind.BEAM, mouth, Color.fromRGB(230, 40, 40)).pose(Vfx.facing(dir), Vfx.scale(3, 3, 34))
                            .grow(1, 12, Vfx.scale(.5, .5, 34)).life(14, 6);
                    Vfx.model(VfxKind.BEAM, mouth, WHITE).pose(Vfx.facing(dir), Vfx.scale(1.2, 1.2, 34))
                            .grow(1, 10, Vfx.scale(.1, .1, 34)).life(12, 6);
                });
            }
            case ASH_STORM -> every(200, 4, tick -> {
                if (KitFx.live(p, r)) {
                    Location at = p.getLocation();
                    Vfx.whirl(at.clone().add(0, (tick / 4) % 3 * .9, 0), Color.fromRGB(225, 205, 175), 3, 8);
                    Vfx.smoke(at.clone().add(0, 1, 0), Color.fromRGB(215, 195, 165), 2, 3, 1, 16);
                }
            });
            case JAKUHO -> {
                // Jakuhō Raikōben: the golden missile flies to the aimed spot and goes off.
                Location spot = aimPoint(p, 40);
                Vfx.fly(VfxKind.SPEAR, eye(p).add(fwd), spot.clone().add(0, 1, 0), GOLD, Vfx.scale(2.4, 2.4, 3), 12, 1);
                at(12, () -> {
                    Vfx.model(VfxKind.ORB, spot, WHITE).pose(new Quaternionf(), Vfx.scale(1)).grow(1, 8, Vfx.scale(14))
                            .life(16, 8);
                    Vfx.model(VfxKind.ORB, spot, GOLD).pose(new Quaternionf(), Vfx.scale(1)).grow(1, 10, Vfx.scale(17))
                            .life(20, 10);
                    Vfx.shockwave(KitFx.plugin(), spot, GOLD, 14, 14);
                    Vfx.crater(spot, 14, 300);
                    for (int i = 0; i < 8; i++) {
                        Vfx.zap(spot.clone().add(0, 2, 0), spot.clone().add(rand(8), Math.random() * 6, rand(8)), GOLD, .2);
                    }
                });
            }
            case MYOO -> {
                // Myō'ō: the armoured giant stands behind its master for the rest of the release.
                // Beside and a little behind, so a third-person camera is not inside it.
                Vector offset = fwd.clone().multiply(-2).add(right(p).multiply(5)).add(new Vector(0, -1, 0));
                every(r.endsAt > 0 ? (int) Math.max(40, (r.endsAt - System.currentTimeMillis()) / 50) : 600, 2, tick -> {
                    if (KitFx.live(p, r)) {
                        Vector now = forward(p).multiply(-2).add(right(p).multiply(5)).add(new Vector(0, -1, 0));
                        Quaternionf pose = Vfx.facing(forward(p));
                        Vfx.keep("myoo:" + p.getUniqueId(), 6, () -> Vfx.model(VfxKind.GIANT, p.getLocation().add(offset),
                                        Color.fromRGB(170, 120, 70)).follow(p, offset).pose(pose, Vfx.scale(3.6)))
                                .offset(now).move(3, new Vector3f(), pose, Vfx.scale(3.6));
                    }
                });
            }
            case WEIGHT_FIELD -> {
                for (LivingEntity entity : near(p, 10)) {
                    Location at = entity.getLocation();
                    Vfx.model(VfxKind.RING, at.clone().add(0, 4, 0), c).pose(new Quaternionf(), Vfx.scale(1.4, 2, 1.4))
                            .to(1, 4, v(0, -3.9, 0), new Quaternionf(), Vfx.scale(1.6, 1, 1.6)).life(8, 3);
                    at(4, () -> Vfx.crater(ground(at), 2.6, 80));
                }
            }
            case TEKKEN -> every(300, 2, tick -> {
                if (KitFx.live(p, r)) {
                    for (int s = -1; s <= 1; s += 2) {
                        int side = s;
                        Vector offset = right(p).multiply(s * .42).add(new Vector(0, .95, 0)).add(forward(p).multiply(.3));
                        Quaternionf pose = Vfx.facing(forward(p));
                        Vfx.keep("tekken:" + p.getUniqueId() + side, 6, () -> Vfx.model(VfxKind.PLATE, p.getLocation().add(offset),
                                Color.fromRGB(150, 230, 170)).follow(p, offset).pose(pose, Vfx.scale(.6)))
                                .offset(offset).move(2, new Vector3f(), pose, Vfx.scale(.6));
                    }
                }
            });
            case MINAZUKI_BANKAI -> {
                for (int i = 0; i < 8; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 4);
                    Vfx.wave(base.clone().add(out.clone().multiply(.5)), out, Color.fromRGB(170, 20, 30), 2.6, 2, 6, 16);
                }
                Vfx.drops(body.clone().add(0, 1, 0), Color.fromRGB(170, 20, 30), 20, 2, 18);
            }
            case KAMIKAKE -> {
                Location hand = base.clone().add(fwd.clone().multiply(2)).add(0, 4, 0);
                Vfx.model(VfxKind.FIST, hand, Color.fromRGB(30, 30, 40)).pose(Vfx.facing(fwd).rotateX(-.5f), Vfx.scale(.4))
                        .grow(1, 10, Vfx.scale(3)).life(60, 12);
                Vfx.flash(VfxKind.EYE, hand.clone().add(0, 3, 0), WHITE, new Quaternionf(), Vfx.scale(2), 60);
                Vfx.smoke(body, BLACK, 10, 3, 1.4, 40);
            }
            case KONJIKI -> {
                Location rear = base.clone().add(fwd.clone().multiply(-2)).add(0, 3, 0);
                Vfx.model(VfxKind.HEAD, rear, GOLD).pose(Vfx.facing(fwd.clone().setY(-.3)), Vfx.scale(.4))
                        .grow(1, 10, Vfx.scale(3.6)).to(30, 6, fwd.clone().multiply(4).toVector3f(),
                                Vfx.facing(fwd.clone().setY(-.4)), Vfx.scale(3.6)).life(50, 8);
                at(30, () -> {
                    Vfx.smoke(base.clone().add(fwd.clone().multiply(4)), Color.fromRGB(170, 80, 200), 18, 4, 1.8, 60);
                    rain(VfxKind.DROP, base.clone().add(fwd.clone().multiply(4)), Color.fromRGB(170, 80, 200), 24, 4, 1.4, 30, 5);
                });
            }
            // ------------------------------------------------------------ Hollows
            case DESGARRON -> {
                // Desgarrón: ten giant claws of blue reiatsu raking out in a fan.
                for (int i = 0; i < 10; i++) {
                    Vector way = dir.clone().rotateAroundY((i - 4.5) * .12);
                    int k = i;
                    at(i % 5, () -> Vfx.fly(VfxKind.CLAW, body.clone().add(fwd), body.clone().add(way.multiply(16)),
                            k % 2 == 0 ? c : WHITE, Vfx.scale(3, 3, 3), 8, 4));
                }
            }
            case FURIA -> {
                column(base, c, 3, 14, 10, 14);
                Vfx.erupt(VfxKind.SPIKE, base, Color.fromRGB(150, 100, 70), 14, 4, 2.4, 40);
                Vfx.crater(KitFx.ground(p), 12, 260);
            }
            case SIX_ARMS -> every(300, 2, tick -> {
                if (KitFx.live(p, r)) {
                    KitFx.aura(p, r, tick);
                }
            });
            case AMOR_MASS -> {
                for (LivingEntity entity : near(p, 24)) {
                    Vfx.fly(VfxKind.EYE, body, entity.getLocation().add(0, entity.getHeight() + .3, 0), c, Vfx.scale(.7), 8, 60);
                }
                for (int ring = 0; ring < 3; ring++) {
                    crown(VfxKind.EYE, body.clone().add(0, ring, 0), c, 12, 3 - ring * .6, .8, 50, ring % 2 == 0 ? 2 : -2, true);
                }
            }
            case BANQUET -> {
                swarm(p, VfxKind.MASK, WHITE, 30, 3.4, 5, 1, 50);
                Vfx.smoke(body, BLACK, 14, 3, 1.6, 50);
            }
            case GABRIEL -> {
                Location spot = targetSpot(p, 16);
                Vfx.smoke(spot, Color.fromRGB(240, 130, 200), 8, 1.2, 1.2, 30);
                Vfx.drops(spot, Color.fromRGB(180, 30, 60), 14, 1.2, 16);
                converge(VfxKind.HEART, spot, Color.fromRGB(240, 120, 200), 6, 3, .6, 8);
            }
            case LANZA_RELAMPAGO -> {
                // Lanza del Relámpago: a spear of green lightning that leaves a towering explosion.
                Location spot = aimPoint(p, 40);
                Vfx.flash(VfxKind.SPEAR, body.clone().add(right(p).multiply(.5)).add(0, 1, 0), CERO_GREEN,
                        Vfx.facing(dir), Vfx.scale(2.4, 2.4, 3.6), 10);
                at(10, () -> Vfx.fly(VfxKind.SPEAR, body.clone().add(0, 1, 0), spot.clone().add(0, 1, 0), CERO_GREEN,
                        Vfx.scale(2.4, 2.4, 3.6), 6, 0));
                at(16, () -> {
                    column(spot, CERO_GREEN, 4, 18, 6, 14);
                    Vfx.model(VfxKind.ORB, spot, CERO_GREEN).pose(new Quaternionf(), Vfx.scale(1)).grow(1, 8, Vfx.scale(12)).life(16, 8);
                    for (int i = 0; i < 8; i++) {
                        Vfx.zap(spot.clone().add(0, 2, 0), spot.clone().add(rand(7), Math.random() * 7, rand(7)), CERO_GREEN, .22);
                    }
                    Vfx.crater(spot, 12, 260);
                });
            }
            case METRALLETA -> {
                for (int i = 0; i < 24; i++) {
                    int k = i;
                    at(i, () -> {
                        Vector way = p.getEyeLocation().getDirection().rotateAroundY(rand(.25)).add(new Vector(0, rand(.1), 0));
                        Vfx.fly(VfxKind.ORB, eye(p).add(way), eye(p).add(way.clone().multiply(24)), k % 3 == 0 ? WHITE : c,
                                Vfx.scale(.6), 6, 1);
                    });
                }
            }
            case GRAN_CAIDA -> {
                Location spot = aimPoint(p, 30);
                Vfx.model(VfxKind.BLADE, spot.clone().add(0, 14, 0), Color.fromRGB(80, 170, 255))
                        .pose(Vfx.facing(fwd).rotateX((float) Math.PI), Vfx.scale(6))
                        .to(4, 5, v(0, -8, 0), Vfx.facing(fwd).rotateX((float) Math.PI), Vfx.scale(6)).life(24, 8);
                at(9, () -> {
                    for (int i = 0; i < 8; i++) {
                        Vector out = fwd.clone().rotateAroundY(i * Math.PI / 4);
                        Vfx.wave(spot, out, Color.fromRGB(60, 150, 235), 3, 2.4, 6, 14);
                    }
                });
            }
            case SENESCENCIA -> {
                Vfx.model(VfxKind.ORB, base, Color.fromRGB(40, 25, 55)).tone(1).pose(new Quaternionf(), Vfx.scale(.4))
                        .grow(1, 12, Vfx.scale(14, 9, 14)).life(100, 14);
                Vfx.flash(VfxKind.CLOCK, base.clone().add(0, 6, 0), Color.fromRGB(140, 110, 180), new Quaternionf().rotateX(-1.57f), Vfx.scale(5), 100);
                every(100, 10, tick -> Vfx.smoke(base.clone().add(rand(6), .5, rand(6)), Color.fromRGB(45, 30, 60), 3, 1.2, 1.4, 30));
            }
            case LANZADOR -> {
                Location spot = aimPoint(p, 34);
                Effect lance = Vfx.model(VfxKind.SPEAR, base.clone().add(0, 3, 0), CERO_GREEN)
                        .pose(new Vector3f(), Vfx.facing(dir), Vfx.scale(3, 3, 5));
                Vector span = spot.toVector().add(new Vector(0, 1, 0)).subtract(base.clone().add(0, 3, 0).toVector());
                lance.to(6, 8, span.toVector3f(), Vfx.facing(span).rotateZ(9), Vfx.scale(3, 3, 5)).life(16, 2);
                at(14, () -> {
                    Vfx.burst(spot.clone().add(0, 1, 0), CERO_GREEN, 60, 2, 2f);
                    Vfx.shockwave(KitFx.plugin(), spot, CERO_GREEN, 8, 10);
                });
            }
            case TENTACLE_STORM -> {
                crown(VfxKind.TENTACLE, base, c, 8, 1.6, 2.6, 30, 1, true);
                at(6, () -> radiate(VfxKind.SPEAR, body, c, 16, 10, 1.2, 6, 2));
            }
            case TRES_BESTIAS -> {
                for (int i = -1; i <= 1; i++) {
                    Location from = base.clone().add(right(p).multiply(i * 2)).add(0, 1.2, 0);
                    int k = i;
                    at((i + 1) * 4, () -> Vfx.fly(VfxKind.HEAD, from, from.clone().add(fwd.clone().rotateAroundY(k * .2).multiply(14)),
                            GOLD, Vfx.scale(1.6), 12, 4));
                }
            }
            // ------------------------------------------------------------ Quincy
            case BURNER4 -> {
                // Burner Finger 4: fire raised to the sky and brought down along the aim.
                Location spot = aimPoint(p, 30);
                column(base, FLAME, .9, 18, 6, 6);
                at(10, () -> {
                    Vfx.fly(VfxKind.BEAM, base.clone().add(0, 18, 0), spot, FLAME, Vfx.scale(2.4, 2.4, 4), 6, 2);
                    at(6, () -> {
                        flameRing(spot, FLAME, 18, 4, 4, 24);
                        column(spot, FLAME, 3, 10, 4, 10);
                        Vfx.crater(spot, 10, 200);
                    });
                });
            }
            case HOHEIT -> {
                // Hoheitsrecht: Gerard grows into a giant of light with golden wings and a halo.
                KitFx.wings(p, GOLD, 4.6, 40, .25);
                every(300, 2, tick -> {
                    if (KitFx.live(p, r)) {
                        KitFx.heldWings(p, "hoheit:" + p.getUniqueId(), GOLD, 4.2, tick);
                        Vfx.keep("hoheit-halo:" + p.getUniqueId(), 6, () -> Vfx.model(VfxKind.RING, p.getLocation().add(0, 5.6, 0), GOLD)
                                .follow(p, new Vector(0, 5.6, 0)).pose(new Quaternionf(), Vfx.scale(1.1, 1.5, 1.1)));
                    }
                });
            }
            case KILL_COUNT -> every(300, 2, tick -> {
                if (KitFx.live(p, r)) {
                    Vfx.orbit("killcount:" + p.getUniqueId(), p, VfxKind.CLAW, c, 3, 1, 1.2, .5, tick * .12, true);
                }
            });
            case POWER_QUAKE -> {
                Location spot = KitFx.ground(p);
                Vfx.model(VfxKind.FIST, spot.clone().add(0, 6, 0), c).pose(Vfx.facing(fwd), Vfx.scale(1.6))
                        .to(1, 3, v(0, -5.6, 0), Vfx.facing(fwd), Vfx.scale(2)).life(16, 6);
                at(4, () -> Vfx.erupt(VfxKind.SPIKE, spot, r.element.tint, 16, 7, 2.2, 40));
            }
            case TATARFORAS -> {
                Location spot = targetSpot(p, 20);
                Vfx.model(VfxKind.ORB, spot, Color.fromRGB(30, 15, 30)).tone(1).pose(new Quaternionf(), Vfx.scale(.4))
                        .grow(1, 8, Vfx.scale(5)).life(100, 12);
                Vfx.erupt(VfxKind.SPIKE, ground(spot), Color.fromRGB(50, 20, 40), 10, 2.2, 3, 90);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Vfx.glyph(VfxKind.EYE, spot.clone().add(Math.cos(a) * 2.6, 1, Math.sin(a) * 2.6), Color.fromRGB(190, 40, 80), .8, 90);
                }
            }
            case METEOR -> {
                // Gremmy imagines a meteor.
                Location spot = aimPoint(p, 40);
                Location sky = spot.clone().add(-8, 26, -8);
                Vector fall = spot.toVector().subtract(sky.toVector());
                Vfx.model(VfxKind.ROCK, sky, Color.fromRGB(140, 110, 100)).pose(new Vector3f(), new Quaternionf(), Vfx.scale(6))
                        .to(1, 30, fall.toVector3f(), new Quaternionf().rotateXYZ(2, 3, 1), Vfx.scale(7)).life(31, 1);
                every(30, 3, tick -> {
                    Location at = sky.clone().add(fall.clone().multiply(tick / 30.0));
                    Vfx.flame(at.clone().subtract(fall.clone().normalize().multiply(2)), FLAME, 5, 10);
                });
                at(30, () -> {
                    Vfx.model(VfxKind.ORB, spot, EMBER).pose(new Quaternionf(), Vfx.scale(2)).grow(1, 10, Vfx.scale(18)).life(20, 10);
                    Vfx.shockwave(KitFx.plugin(), spot, FLAME, 16, 16);
                    Vfx.crater(spot, 16, 300);
                    Vfx.scatter(VfxKind.ROCK, spot.clone().add(0, 1, 0), Color.fromRGB(120, 90, 80), 20, 4, 1.6, 26);
                });
            }
            case LOVE_RAIN -> rain(VfxKind.HEART, aimPoint(p, 24), c, 50, 6, 1, 60, 10);
            case JILLIEL -> {
                Location spot = aimPoint(p, 30);
                for (int i = 0; i < 14; i++) {
                    Location at = spot.clone().add(rand(6), 0, rand(6));
                    int delay = i * 3;
                    at(delay, () -> {
                        Vfx.fly(VfxKind.BEAM, at.clone().add(0, 20, 0), at, WHITE, Vfx.scale(1.2, 1.2, 4), 4, 4);
                        at(4, () -> Vfx.burst(at.clone().add(0, .5, 0), WHITE, 30, .8, 1.8f));
                    });
                }
                KitFx.heldWings(p, "jilliel:" + p.getUniqueId(), WHITE, 3.6, 0);
            }
            case SPRENGER -> {
                // Sprenger: five Seele Schneider pinned in a pentacle, then the whole star goes off.
                Location spot = aimPoint(p, 24);
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI / 2 + i * Math.PI * 2 / 5;
                    Location pin = spot.clone().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
                    Vfx.fly(VfxKind.BLADE, body.clone().add(0, 1, 0), pin, c, Vfx.scale(1.6), 6, 34);
                }
                at(8, () -> Vfx.sigil(VfxKind.SIGIL, spot, c, 5.5, 30));
                at(36, () -> {
                    column(spot, WHITE, 5.5, 14, 4, 10);
                    Vfx.model(VfxKind.ORB, spot, c).pose(new Quaternionf(), Vfx.scale(1)).grow(1, 8, Vfx.scale(12)).life(14, 8);
                    Vfx.shockwave(KitFx.plugin(), spot, c, 10, 10);
                });
            }
            case GALVANO_JAVELIN -> {
                Location spot = aimPoint(p, 34);
                Vfx.fly(VfxKind.SPEAR, body.clone().add(0, 1.5, 0), spot.clone().add(0, .5, 0), c, Vfx.scale(2, 2, 3.4), 6, 2);
                at(6, () -> {
                    for (int i = 0; i < 10; i++) {
                        Vfx.zap(spot.clone().add(0, 1, 0), spot.clone().add(rand(6), Math.random() * 4, rand(6)), c, .2);
                    }
                    Vfx.bolt(spot, WHITE, 16, .6);
                    Vfx.crater(spot, 8, 200);
                });
            }
            case WIND_STORM -> every(100, 4, tick -> {
                if (KitFx.live(p, r)) {
                    for (int h = 0; h < 3; h++) {
                        Vfx.whirl(p.getLocation().add(0, h * 1.2, 0), c, 2.5 + h, 8);
                    }
                }
            });
            case STAR_ROCKET -> {
                Location spot = targetSpot(p, 20);
                Vfx.fly(VfxKind.STAR, body.clone().add(fwd), spot, GOLD, Vfx.scale(1.6), 8, 2);
                at(8, () -> {
                    radiate(VfxKind.STAR, spot, GOLD, 10, 4, .6, 8, 2);
                    Vfx.burst(spot, GOLD, 60, 1.4, 2f);
                });
            }
            case EXPLODE_BARRAGE -> {
                Location spot = aimPoint(p, 24);
                for (int i = 0; i < 16; i++) {
                    int k = i;
                    at(i * 2, () -> {
                        Location at = spot.clone().add(rand(5), Math.random() * 2, rand(5));
                        Vfx.burst(at, k % 2 == 0 ? c : EMBER, 40, .9, 2f);
                        Vfx.model(VfxKind.RING, at, EMBER).pose(new Quaternionf().rotateX((float) rand(1)), Vfx.scale(.3))
                                .grow(1, 5, Vfx.scale(2.6)).life(7, 3);
                    });
                }
            }
            case GLUTTON_MAW -> {
                Location spot = targetSpot(p, 14);
                Vector in = spot.toVector().subtract(body.toVector());
                Vfx.model(VfxKind.HEAD, body.clone().add(fwd.clone().multiply(1.5)), c).pose(new Vector3f(), Vfx.facing(in), Vfx.scale(1.5))
                        .to(1, 6, in.clone().multiply(.7).toVector3f(), Vfx.facing(in), Vfx.scale(4.6))
                        .grow(10, 4, Vfx.scale(2)).life(18, 4);
            }
            case BISKIEL -> {
                Location spot = targetSpot(p, 20);
                Location g = ground(spot);
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5;
                    column(g.clone().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2), WHITE, .15, 5, 4, 80);
                    chain(g.clone().add(Math.cos(a) * 2.2, 3, Math.sin(a) * 2.2), spot, WHITE, 90);
                }
            }
            case AVARON -> {
                crown(VfxKind.HEX, body, Color.fromRGB(150, 170, 230), 8, 2.2, 1.6, 200, 4, true);
                for (LivingEntity entity : near(p, 16)) {
                    chain(body, entity.getLocation().add(0, entity.getHeight() * .5, 0), c, 40);
                }
            }
            case ZOMBIE_ARMY -> {
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    Location spot = ground(base.clone().add(Math.cos(a) * 3, 0, Math.sin(a) * 3));
                    Vfx.drops(spot.clone().add(0, .3, 0), c, 10, 1.2, 14);
                    Vfx.model(VfxKind.MASK, spot.clone().add(0, -.5, 0), WHITE).pose(Vfx.facing(fwd), Vfx.scale(1))
                            .to(1, 10, v(0, 2.2, 0), Vfx.facing(fwd), Vfx.scale(1.2)).life(30, 8);
                }
            }
            // ------------------------------------------------------------ Fullbringers
            case TRIPLE_SEVEN -> {
                KitFx.release(p, r);
                every(300, 2, tick -> {
                    if (KitFx.live(p, r)) {
                        Vfx.orbit("777:" + p.getUniqueId(), p, VfxKind.CARD, GOLD, 3, 1.2, 1.4, .5, tick * .15, true);
                    }
                });
            }
            case GIGANTE -> every(300, 2, tick -> {
                if (KitFx.live(p, r)) {
                    Vector offset = right(p).multiply(.9).add(new Vector(0, 1.2, 0)).add(forward(p).multiply(.6));
                    Quaternionf pose = Vfx.facing(forward(p)).rotateX(1.57f);
                    Vfx.keep("gigante:" + p.getUniqueId(), 6, () -> Vfx.model(VfxKind.FIST, p.getLocation().add(offset), c)
                            .follow(p, offset).pose(pose, Vfx.scale(1.2))).offset(offset).move(2, new Vector3f(), pose, Vfx.scale(1.2));
                }
            });
            case REWRITE -> {
                swarm(p, VfxKind.PAGE, WHITE, 30, 3, 4, .7, 40);
                Vfx.shockwave(KitFx.plugin(), base, Color.fromRGB(160, 160, 210), 10, 12);
            }
            case CROSS_GETSUGA -> {
                Quaternionf aim = Vfx.facing(dir).rotateZ(.35f);
                Vfx.model(VfxKind.SLASH, ahead(p, 1.5), BLACK).pose(new Vector3f(), aim, Vfx.scale(3.5, 1.6, 3.5))
                        .to(1, 10, dir.clone().multiply(32).toVector3f(), aim, Vfx.scale(4.6, 1.6, 4.6)).life(14, 4);
                cross(ahead(p, 2), dir, Color.fromRGB(120, 230, 160), 2, 6);
            }
            case FILTHY_STOMP -> {
                for (int i = 0; i < 8; i++) {
                    Vector out = fwd.clone().rotateAroundY(i * Math.PI / 4);
                    Vfx.wave(base, out, Color.fromRGB(100, 75, 45), 2.6, 1.8, 7, 16);
                }
            }
            case BRING_IN -> {
                for (LivingEntity entity : near(p, 14)) {
                    Location from = entity.getLocation().add(0, 1, 0);
                    Vfx.fly(VfxKind.HEART, from, body, c, Vfx.scale(.7), 8, 2);
                    chain(body, from, c, 10);
                }
            }
            case FINAL_STAGE -> {
                for (int ring = 0; ring < 2; ring++) {
                    crown(VfxKind.INVADER, body.clone().add(0, 1 + ring * 1.4, 0), ring == 0 ? c : Color.fromRGB(255, 80, 200),
                            12, 4 + ring, 1, 100, ring == 0 ? 3 : -3, false);
                }
                crown(VfxKind.HEX, base.clone().add(0, 1.5, 0), c, 16, 7, 2.4, 100, 0, false);
            }
            case SHITEN -> every(240, 2, tick -> {
                if (KitFx.live(p, r)) {
                    Vfx.orbit("shiten:" + p.getUniqueId(), p, VfxKind.TRIANGLE, Color.fromRGB(255, 200, 120), 4, 1.3, 1.1, 1.3,
                            tick * .06, true);
                }
            });
            case TIME_LIMIT -> {
                Vfx.model(VfxKind.CLOCK, base.clone().add(0, 5, 0), c).pose(new Quaternionf().rotateX(-1.57f), Vfx.scale(.5))
                        .to(1, 10, new Quaternionf().rotateX(-1.57f).rotateZ(2), Vfx.scale(8))
                        .to(11, 100, new Quaternionf().rotateX(-1.57f).rotateZ(-12), Vfx.scale(8)).life(112, 10);
                for (LivingEntity entity : near(p, 16)) {
                    chain(base.clone().add(0, 5, 0), entity.getLocation().add(0, 1, 0), c, 60);
                }
            }
            // ------------------------------------------------------------ the shared ultimates
            case U_CATACLYSM -> head(p, r, FLAME);
            case U_PHANTOM -> Vfx.glyph(VfxKind.EYE, p.getLocation().add(0, 2.6, 0), c, 1, 20);
            case U_STARFALL -> Vfx.sigil(VfxKind.SIGIL, aimPoint(p, 30), c, 5.5, 50);
            case U_TEMPEST -> {
                for (int h = 0; h < 6; h++) {
                    int k = h;
                    at(h * 2, () -> Vfx.whirl(p.getLocation().add(0, k * 1.1, 0), WHITE, 1.6 + k * .5, 60));
                }
            }
            case U_WORLD_BREAKER -> Vfx.erupt(VfxKind.SPIKE, KitFx.ground(p), r.element.tint, 18, 6, 2.6, 50);
            case U_SANCTUM -> Vfx.sigil(VfxKind.SIGIL, KitFx.ground(p), c, 6, 170);
            default -> r.element.rise(base, 1.6);
        }
    }

    private static final Color VENOM = KitFx.VENOM;

    private static void head(Player p, Release r, Color color) {
        KitFx.head(p, r.element == Element.FIRE ? color : r.form.color(), 2, 20, .5, 2);
    }
}
