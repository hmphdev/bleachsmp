package dev.bleachsmp.awakening;

import dev.bleachsmp.fx.Vfx;
import dev.bleachsmp.fx.VfxKind;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What spiritual pressure looks like. Sensing and suppression are private cues, drawn only for the one player
 * they concern, so they never give away who is hiding or who has been noticed. The fight edge is public: anyone
 * watching a fight can see one fighter's reiatsu bearing down on the other.
 */
final class PressureFx {
    private static final Color GREY = Color.fromRGB(150, 150, 160);
    private static final Color PALE = Color.fromRGB(225, 235, 255);

    private PressureFx() {
    }

    // ---- Sensing (only the observer sees these) ----

    /** A wisp of the source's reiatsu reaches toward the observer from its direction, and the air presses in. */
    static void sensed(Player observer, Player source, Color color, int difference) {
        Location feet = observer.getLocation();
        Location chest = feet.clone().add(0, 1.2, 0);
        Vector toward = source.getLocation().toVector().subtract(feet.toVector()).setY(0);
        if (toward.lengthSquared() < 1e-4) {
            toward = new Vector(0, 0, 1);
        }
        toward.normalize();

        // The wisp: a thin streak a little way off in the source's direction, drifting toward the observer.
        double length = 1.6 + difference * .25;
        Location far = chest.clone().add(toward.clone().multiply(2.2 + length));
        Location near = chest.clone().add(toward.clone().multiply(1.4));
        Quaternionf aim = Vfx.facing(toward.clone().multiply(-1));
        Vfx.modelFor(observer, VfxKind.BEAM, far, color).tone(1)
                .pose(new Vector3f(), aim, Vfx.scale(.05, .05, .2))
                .to(1, 6, new Vector3f(), aim, Vfx.scale(.16, .16, length))
                .to(7, 12, toward.clone().multiply(-.8).toVector3f(), aim, Vfx.scale(.07, .07, length * .6))
                .life(22, 10);
        for (int i = 0; i <= 8; i++) {
            Location point = far.clone().add(near.toVector().subtract(far.toVector()).multiply(i / 8.0));
            Vfx.particleFor(observer, Particle.DUST, point, 1, .03, .03, .03, 0, Vfx.dust(color, .9f));
        }

        // The weight: a ring of the source's colour closes in on the observer's feet.
        Location ground = feet.clone().add(0, .1, 0);
        double ring = 1.8 + difference * .3;
        Vfx.modelFor(observer, VfxKind.RING, ground, color).tone(difference >= 3 ? 1 : 2)
                .pose(new Quaternionf(), Vfx.scale(ring, 1, ring))
                .to(1, 12, new Quaternionf().rotateY(1.2f), Vfx.scale(.5, .8, .5))
                .life(16, 5);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            Vfx.particleFor(observer, Particle.DUST, ground.clone().add(Math.cos(a) * ring * .7, 0, Math.sin(a) * ring * .7),
                    1, 0, 0, 0, 0, Vfx.dust(color, 1f));
        }

        if (difference >= 3) {
            // Heavy: grit sifts down around them, as though the air had weight.
            Vfx.particleFor(observer, Particle.FALLING_DUST, feet.clone().add(0, 2.6, 0), 6 + difference * 2,
                    1.2, .3, 1.2, 0, Material.GRAY_CONCRETE_POWDER.createBlockData());
        }
        if (difference >= 5) {
            // Overwhelming: a glimpse of the source's whole aura, towering where they stand.
            double body = KitFx.size(source);
            Vfx.modelFor(observer, VfxKind.AURA, source.getLocation(), color).tone(2)
                    .follow(source, new Vector())
                    .pose(new Quaternionf(), Vfx.scale(.6 * body, 1.5 * body, .6 * body))
                    .to(1, 8, new Quaternionf().rotateY(.8f), Vfx.scale(1.4 * body, 4.5 * body, 1.4 * body))
                    .to(9, 20, new Quaternionf().rotateY(2f), Vfx.scale(1.2 * body, 5 * body, 1.2 * body))
                    .life(32, 14);
            Location top = source.getLocation();
            for (int i = 0; i < 12; i++) {
                Vfx.particleFor(observer, Particle.DUST, top.clone().add(0, i * .4, 0), 2, .35, .1, .35, 0,
                        Vfx.dust(color, 1.4f));
            }
        }
    }

    // ---- The fight edge (anyone nearby sees these) ----

    /** The stronger fighter's reiatsu flares and comes down on the weaker one. */
    static void edgeLands(Plugin plugin, Player weaker, Player stronger, Color color) {
        Location source = stronger.getLocation();
        double body = KitFx.size(stronger);
        Vfx.shockwave(plugin, source, color, 4.5, 10);
        Vfx.model(VfxKind.AURA, source, color).follow(stronger, new Vector())
                .pose(new Quaternionf(), Vfx.scale(.5 * body, 1.2 * body, .5 * body))
                .to(1, 4, new Quaternionf().rotateY(.7f), Vfx.scale(1.5 * body, 3.4 * body, 1.5 * body))
                .to(5, 10, new Quaternionf().rotateY(1.6f), Vfx.scale(1.1 * body, 2.6 * body, 1.1 * body))
                .life(18, 8);
        Vfx.particle(Particle.DUST, source.clone().add(0, 1, 0), 30, .5, .9, .5, 0, Vfx.dust(color, 1.5f));

        Location ground = weaker.getLocation();
        Vfx.crater(ground, 1.8, 50);
        Vfx.particle(Particle.FALLING_DUST, ground.clone().add(0, 2.4, 0), 18, .6, .2, .6, 0,
                Material.GRAY_CONCRETE_POWDER.createBlockData());
        Vfx.particle(Particle.BLOCK, ground.clone().add(0, .1, 0), 14, .5, 0, .5, 0, ground.clone().add(0, -.5, 0)
                .getBlock().getBlockData());
        Vfx.sound(source, Sound.ENTITY_WARDEN_SONIC_CHARGE, .5f, 1.6f);
    }

    /** Each second the edge holds: grit sifting down on the weaker fighter, and a faint ring under them. */
    static void edgeHolds(Player weaker, Color color, double weight) {
        Location feet = weaker.getLocation();
        Vfx.particle(Particle.FALLING_DUST, feet.clone().add(0, 2.4, 0), (int) Math.round(4 + weight * 6), .5, .15, .5, 0,
                Material.GRAY_CONCRETE_POWDER.createBlockData());
        Vfx.model(VfxKind.RING, feet.clone().add(0, .08, 0), color).tone(weight > .5 ? 2 : 3)
                .pose(new Quaternionf(), Vfx.scale(1.3, .7, 1.3))
                .to(1, 16, new Quaternionf().rotateY(.9f), Vfx.scale(.7, .5, .7))
                .life(20, 8);
    }

    /** The weaker fighter shrugs the weight off: it breaks around them like glass. */
    static void adapted(Player weaker, Color own) {
        Location chest = weaker.getLocation().add(0, 1.1, 0);
        Vfx.scatter(VfxKind.SHARD, chest, PALE, 7, 1.3, .3, 14);
        Vfx.burst(chest, own, 18, .4, 1.1f);
        Vfx.particle(Particle.DUST, chest, 20, .5, .6, .5, 0, Vfx.dust(PALE, 1.2f));
        weaker.playSound(chest, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, .9f, .8f);
    }

    // ---- Suppression (only the player sees these) ----

    /** Their reiatsu pulls in and goes dark. */
    static void suppressed(Player player, Color color) {
        Location feet = player.getLocation();
        double body = KitFx.size(player);
        Vfx.modelFor(player, VfxKind.AURA, feet, color).tone(2).follow(player, new Vector())
                .pose(new Quaternionf(), Vfx.scale(1.2 * body, 2.2 * body, 1.2 * body))
                .to(1, 10, new Quaternionf().rotateY(-1.4f), Vfx.scale(.2 * body, .4 * body, .2 * body))
                .life(12, 6);
        Vfx.modelFor(player, VfxKind.RING, feet.clone().add(0, .08, 0), GREY).tone(2)
                .pose(new Quaternionf(), Vfx.scale(1.6, .8, 1.6))
                .to(1, 10, new Quaternionf().rotateY(-1f), Vfx.scale(.2, .5, .2))
                .life(12, 5);
        Vfx.particleFor(player, Particle.SMOKE, feet.clone().add(0, .6, 0), 10, .4, .4, .4, .01, null);
    }

    /** Their reiatsu breathes out again. */
    static void returned(Player player, Color color) {
        Location feet = player.getLocation();
        Vfx.modelFor(player, VfxKind.RING, feet.clone().add(0, .08, 0), color).tone(1)
                .pose(new Quaternionf(), Vfx.scale(.3, .6, .3))
                .to(1, 8, new Quaternionf().rotateY(1f), Vfx.scale(1.8, .9, 1.8))
                .life(12, 6);
        Vfx.particleFor(player, Particle.DUST, feet.clone().add(0, 1, 0), 12, .4, .6, .4, 0, Vfx.dust(color, 1f));
    }
}
