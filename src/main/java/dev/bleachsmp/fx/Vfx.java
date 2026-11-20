package dev.bleachsmp.fx;

import dev.bleachsmp.pack.ResourcePackService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared effect helpers for abilities. Players with the resource pack see glowing, animated effect models
 * ({@link Effect}); everyone else sees the same shapes drawn in particles.
 */
public final class Vfx {
    private static final int MAX_EFFECTS = 700;
    private static final double VIEW_SQUARED = 96 * 96;

    private static Plugin plugin;
    private static ResourcePackService packs;
    private static BukkitTask ticker;
    private static final List<Effect> effects = new ArrayList<>();
    private static final Map<String, Effect> kept = new HashMap<>();

    private Vfx() {
    }

    /** Starts the effect engine. Without it every helper falls back to particles alone. */
    public static void init(Plugin owner, ResourcePackService resourcePacks) {
        plugin = owner;
        packs = resourcePacks;
        ticker = owner.getServer().getScheduler().runTaskTimer(owner, Vfx::tick, 1, 1);
    }

    public static void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        effects.forEach(Effect::remove);
        effects.clear();
        kept.clear();
    }

    private static void tick() {
        effects.removeIf(effect -> !effect.tick());
        kept.values().removeIf(effect -> !effect.alive());
    }

    // ------------------------------------------------------------------ effect models

    /** Whether anyone near {@code at} would see effect models. */
    public static boolean modelsVisible(Location at) {
        if (packs == null || !packs.enabled() || at.getWorld() == null) {
            return false;
        }
        for (Player player : at.getWorld().getPlayers()) {
            if (packs.hasPack(player) && player.getLocation().distanceSquared(at) < VIEW_SQUARED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spawns an effect model at {@code at}, visible only to players with the resource pack. Returns an inert
     * effect when nobody nearby has the pack. Chain {@link Effect#pose}, {@link Effect#to} and {@link Effect#life}.
     */
    public static Effect model(VfxKind kind, Location at, Color color) {
        if (plugin == null || effects.size() >= MAX_EFFECTS || !modelsVisible(at)) {
            return new Effect(null, kind, color);
        }
        Location spot = at.clone();
        spot.setYaw(0);
        spot.setPitch(0);
        ItemDisplay display = spot.getWorld().spawn(spot, ItemDisplay.class, entity -> {
            entity.setItemStack(Effect.item(kind, color, 0));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(kind.billboard);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setVisibleByDefault(false);
            entity.setViewRange(2);
            entity.setShadowRadius(0);
            if (kind.glows) {
                entity.setBrightness(new Display.Brightness(15, 15));
            }
        });
        for (Player player : spot.getWorld().getPlayers()) {
            if (packs.hasPack(player) && player.getLocation().distanceSquared(spot) < VIEW_SQUARED) {
                player.showEntity(plugin, display);
            }
        }
        Effect effect = new Effect(display, kind, color);
        effects.add(effect);
        return effect;
    }

    /**
     * Returns the living effect stored under {@code key}, or makes one with {@code create}. Either way it lives
     * {@code ticks} more ticks, so an effect refreshed by a repeating aura lasts exactly as long as the aura.
     */
    public static Effect keep(String key, int ticks, java.util.function.Supplier<Effect> create) {
        Effect effect = kept.get(key);
        if (effect == null || !effect.alive()) {
            effect = create.get();
            if (effect.display == null) {
                return effect;
            }
            kept.put(key, effect);
        }
        return effect.life(ticks, Math.min(ticks, 4));
    }

    /** Rotation that turns +Z toward {@code direction}, keeping the model's X axis level. */
    public static Quaternionf facing(Vector direction) {
        Vector dir = direction.clone();
        if (dir.lengthSquared() < 1e-6) {
            return new Quaternionf();
        }
        dir.normalize();
        float yaw = (float) Math.atan2(dir.getX(), dir.getZ());
        float pitch = (float) -Math.asin(Math.max(-1, Math.min(1, dir.getY())));
        return new Quaternionf().rotateY(yaw).rotateX(pitch);
    }

    public static Vector3f scale(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    public static Vector3f scale(double all) {
        return new Vector3f((float) all);
    }

    // ------------------------------------------------------------------ particle routing

    /** Spawns particles only for players who are not seeing the effect models. */
    private static void fallback(Location at, Consumer<Player> spawn) {
        boolean models = packs != null && packs.enabled() && plugin != null;
        for (Player player : at.getWorld().getPlayers()) {
            if ((!models || !packs.hasPack(player)) && player.getLocation().distanceSquared(at) < VIEW_SQUARED) {
                spawn.accept(player);
            }
        }
    }

    /** Particles for players who can't see effect models, standing in for a model drawn with {@link #model}. */
    public static void fallbackDust(Location at, Particle.DustOptions dust) {
        fallback(at, player -> player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust));
    }

    /** Any particle for players who can't see effect models. */
    public static void fallbackParticle(Location at, Particle particle, int count, double spread, double speed) {
        fallback(at, player -> player.spawnParticle(particle, at, count, spread, spread, spread, speed));
    }

    /** A dotted particle line for players who can't see effect models. */
    public static void fallbackLine(Location from, Location to, Color color, float size, double spacing) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.01) {
            return;
        }
        step.normalize().multiply(spacing);
        Location point = from.clone();
        for (double d = 0; d <= length; d += spacing) {
            dustFor(point.clone(), color, size, 1, 0);
            point.add(step);
        }
    }

    private static void dustFor(Location at, Color color, float size, int count, double spread) {
        Particle.DustOptions dust = dust(color, size);
        fallback(at, player -> player.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, dust));
    }

    // ------------------------------------------------------------------ helpers

    public static Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    public static void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, volume, pitch);
    }

    /** A horizontal ring of coloured light. Called again at the same spot, it refreshes instead of stacking. */
    public static void ring(Location center, double radius, int points, Color color, float size) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2 / points);
            dustFor(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), color, size, 1, 0);
        }
        String key = "ring:" + center.getWorld().getName() + ":" + Math.round(center.getX() * 2) + ":"
                + Math.round(center.getY() * 2) + ":" + Math.round(center.getZ() * 2) + ":"
                + Math.round(radius * 4) + ":" + color.asRGB();
        float thick = .6f + size * .5f;
        Vector3f fullSize = scale(radius, thick, radius);
        Effect existing = kept.get(key);
        if (existing != null && existing.alive()) {
            existing.life(5, 3);
            return;
        }
        keep(key, 5, () -> model(VfxKind.RING, center, color)
                .pose(new Quaternionf(), scale(radius * .9, thick, radius * .9))
                .grow(1, 3, fullSize));
    }

    /** A horizontal ring of a data-less particle. */
    public static void ring(Location center, double radius, int points, Particle particle) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2 / points);
            center.getWorld().spawnParticle(particle, center.clone().add(Math.cos(a) * radius, 0,
                    Math.sin(a) * radius), 1, 0, 0, 0, 0);
        }
    }

    /** A flash of light with a spray of sparks; big bursts also swell an orb of energy. */
    public static void burst(Location at, Color color, int count, double spread, float size) {
        at.getWorld().spawnParticle(Particle.DUST, at, Math.max(1, count / (modelsVisible(at) ? 3 : 1)), spread,
                spread, spread, 0, dust(color, size));
        double flash = .7 + spread * 2.2 + size * .25;
        model(VfxKind.FLARE, at, color)
                .pose(new Quaternionf(), scale(flash * .4))
                .grow(1, 3, scale(flash))
                .grow(4, 4, scale(flash * 1.2))
                .life(8, 4);
        if (count >= 30) {
            double orb = .4 + spread * 1.6;
            model(VfxKind.ORB, at, color)
                    .pose(new Quaternionf().rotateY((float) Math.random() * 3), scale(orb * .3))
                    .to(1, 4, new Quaternionf().rotateY((float) Math.random() * 3).rotateX(.8f), scale(orb))
                    .life(7, 4);
        }
    }

    /** A beam of light between two points that thins and fades. */
    public static void line(Location from, Location to, Color color, float size, double spacing) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.01) {
            return;
        }
        Vector dir = step.clone().normalize();
        Vector stride = dir.clone().multiply(spacing);
        Location point = from.clone();
        for (double d = 0; d <= length; d += spacing) {
            dustFor(point.clone(), color, size, 1, 0);
            point.add(stride);
        }
        double width = .1 + size * .14;
        Quaternionf aim = facing(dir);
        model(VfxKind.BEAM, from, color)
                .pose(aim, scale(width * 1.5, width * 1.5, length))
                .grow(1, 5, scale(width * .35, width * .35, length))
                .life(6, 4);
    }

    /**
     * A crescent slash facing {@code forward}, centred on {@code center}. The tips curve back toward the caster.
     * Called every tick along a path, the fading crescents leave a trail behind the travelling one.
     */
    public static void arc(Location center, Vector forward, double radius, Color color, float size, double yOffset) {
        Vector dir = forward.clone().normalize();
        Vector side = dir.getCrossProduct(new Vector(0, 1, 0));
        side = side.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : side.normalize();
        for (int i = -9; i <= 9; i++) {
            double t = i * Math.toRadians(8);
            Location point = center.clone().add(side.clone().multiply(Math.sin(t) * radius))
                    .subtract(dir.clone().multiply((1 - Math.cos(t)) * radius * 0.6)).add(0, yOffset, 0);
            dustFor(point, color, Math.abs(i) < 6 ? size : size * 0.7f, 1, 0.02);
        }
        // Slashes cut on a diagonal, which reads from any height; one path always leans the same way.
        long seed = Math.round(dir.getX() * 50) * 31 + Math.round(dir.getZ() * 50);
        float lean = (float) Math.toRadians(seed % 2 == 0 ? 24 : -24);
        Quaternionf aim = facing(dir).rotateZ(lean);
        float thick = .7f + size * .35f;
        model(VfxKind.SLASH, center.clone().add(0, yOffset, 0), color)
                .pose(aim, scale(radius * .8, thick, radius * .8))
                .grow(1, 3, scale(radius * 1.08, thick, radius * 1.15))
                .life(6, 4);
    }

    /** A rising multi-strand helix around an entity. */
    public static void helix(Plugin owner, Entity around, Color color, int ticks, int strands, double radius) {
        Effect spiral = model(VfxKind.SPIRAL, around.getLocation(), color)
                .follow(around, new Vector())
                .pose(new Quaternionf(), scale(radius * 2, .2, radius * 2))
                .life(ticks + 6, 6);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!around.isValid() || tick > ticks) {
                    cancel();
                    return;
                }
                if (tick % 5 == 0) {
                    spiral.move(5, new Vector3f(), new Quaternionf().rotateY(-(tick + 5) * .6f),
                            scale(radius * 2, .2 + 2.4 * Math.min(1, (tick + 5) / (double) ticks), radius * 2));
                }
                Location base = around.getLocation();
                for (int s = 0; s < strands; s++) {
                    double a = tick * 0.6 + s * (Math.PI * 2 / strands);
                    double h = 2.4 * tick / ticks;
                    dustFor(base.clone().add(Math.cos(a) * radius, h, Math.sin(a) * radius), color, 1.5f, 2, 0.02);
                }
                tick++;
            }
        }.runTaskTimer(owner, 0L, 1L);
    }

    /** A shockwave ring that races outward along the ground, leaving a flash at its centre. */
    public static void shockwave(Plugin owner, Location center, Color color, double maxRadius, int ticks) {
        Location origin = center.clone().add(0, 0.15, 0);
        model(VfxKind.RING, origin, color)
                .pose(new Quaternionf(), scale(.6, 1.6, .6))
                .grow(1, ticks, scale(maxRadius, 1.0, maxRadius))
                .life(ticks + 4, Math.max(4, ticks / 2));
        model(VfxKind.RING, origin.clone().add(0, .05, 0), color)
                .pose(new Quaternionf().rotateY(.4f), scale(.4, 1.0, .4))
                .grow(2, ticks + 2, scale(maxRadius * .72, .6, maxRadius * .72))
                .life(ticks + 5, Math.max(4, ticks / 2));
        model(VfxKind.FLARE, origin.clone().add(0, .4, 0), color)
                .pose(new Quaternionf(), scale(maxRadius * .3))
                .grow(1, 3, scale(maxRadius * .6))
                .life(7, 4);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick > ticks) {
                    cancel();
                    return;
                }
                double radius = 0.6 + (maxRadius - 0.6) * tick / ticks;
                int points = (int) Math.max(16, radius * 7);
                for (int i = 0; i < points; i++) {
                    double a = i * (Math.PI * 2 / points);
                    dustFor(origin.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), color, 1.3f, 1, 0);
                }
                tick++;
            }
        }.runTaskTimer(owner, 0L, 1L);
    }

    /** Follows a moving entity (a projectile or a dashing player) with a glowing streak. */
    public static void trail(Plugin owner, Entity entity, Color color, Particle accent, int maxTicks) {
        new BukkitRunnable() {
            int tick;
            Location last;

            @Override
            public void run() {
                if (!entity.isValid() || tick++ > maxTicks
                        || (entity instanceof AbstractArrow arrow && arrow.isInBlock())) {
                    cancel();
                    return;
                }
                Location at = entity.getLocation().add(0, entity.getHeight() / 2, 0);
                dustFor(at, color, 1.2f, 3, 0.08);
                if (accent != null && tick % 2 == 0) {
                    at.getWorld().spawnParticle(accent, at, 1, 0.05, 0.05, 0.05, 0.01);
                }
                if (last != null && last.getWorld() == at.getWorld()) {
                    Vector step = at.toVector().subtract(last.toVector());
                    double width = Math.min(.5, .18 + entity.getWidth() * .3);
                    if (step.lengthSquared() > 1e-4) {
                        model(VfxKind.BEAM, last, color)
                                .pose(facing(step), scale(width, width, step.length()))
                                .grow(1, 4, scale(width * .2, width * .2, step.length()))
                                .life(5, 3);
                    }
                }
                last = at;
            }
        }.runTaskTimer(owner, 1L, 1L);
    }

    // ------------------------------------------------------------------ composite effects

    /** A pillar of reiatsu that rises to {@code height} over {@code rise} ticks, spinning, then fades. */
    public static void pillar(Location base, Color color, double radius, double height, int rise, int hold) {
        int life = rise + hold + 8;
        model(VfxKind.PILLAR, base, color)
                .pose(new Quaternionf(), scale(radius, .1, radius))
                .to(1, rise, new Quaternionf().rotateY(2f), scale(radius, height, radius))
                .to(rise + 1, hold + 8, new Quaternionf().rotateY(4f), scale(radius * 1.25, height * 1.1, radius * 1.25))
                .life(life, 8);
        model(VfxKind.PILLAR, base, Color.WHITE)
                .pose(new Quaternionf().rotateY(1f), scale(radius * .35, .1, radius * .35))
                .to(1, rise, new Quaternionf().rotateY(-1.5f), scale(radius * .35, height * .95, radius * .35))
                .to(rise + 1, hold + 8, new Quaternionf().rotateY(-3f), scale(radius * .1, height, radius * .1))
                .life(life, 8);
    }

    /** A bolt of light striking down from {@code height} blocks above {@code ground}. */
    public static void bolt(Location ground, Color color, double height, double width) {
        Location top = ground.clone().add(0, height, 0);
        Location point = top.clone();
        // A jagged path down, a few blocks per segment.
        int segments = Math.max(3, (int) (height / 3));
        for (int i = 1; i <= segments; i++) {
            Location next = i == segments ? ground.clone()
                    : top.clone().add((Math.random() - .5) * 1.4, -height * i / segments, (Math.random() - .5) * 1.4);
            Vector step = next.toVector().subtract(point.toVector());
            model(VfxKind.BEAM, point, color)
                    .pose(facing(step), scale(width * 1.6, width * 1.6, step.length() + .1))
                    .grow(2, 6, scale(width * .3, width * .3, step.length() + .1))
                    .life(9, 6);
            point = next;
        }
        model(VfxKind.BEAM, top, Color.WHITE)
                .pose(facing(new Vector(0, -1, 0)), scale(width * .6, width * .6, height))
                .grow(1, 3, scale(width * .15, width * .15, height))
                .life(5, 3);
    }

    /** Scorched, cracked ground that lingers for {@code ticks}. */
    public static void crater(Location ground, double size, int ticks) {
        model(VfxKind.CRACK, ground.clone().add(0, .03, 0), Color.WHITE)
                .pose(new Quaternionf().rotateY((float) (Math.random() * Math.PI * 2)), scale(size * .5, 1, size * .5))
                .grow(1, 3, scale(size, 1, size))
                .life(ticks, Math.min(20, ticks / 3));
    }

    /** A rising tongue of flame. */
    public static void flame(Location at, Color color, double height, int ticks) {
        model(VfxKind.FLAME, at, color)
                .pose(new Quaternionf(), scale(height * .6, height * .3, height * .6))
                .to(1, ticks / 2, new Vector3f(0, (float) height * .2f, 0), new Quaternionf(),
                        scale(height * .8, height, height * .8))
                .to(ticks / 2 + 1, ticks / 2, new Vector3f(0, (float) height * .6f, 0), new Quaternionf(),
                        scale(height * .3, height * .6, height * .3))
                .life(ticks + 1, ticks / 2);
    }
}
