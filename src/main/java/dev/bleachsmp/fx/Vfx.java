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
    private static final int MAX_EFFECTS = 1200;
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
            // Energy glows; solid props (rock, bone, armour) stay readable in the dark without glowing.
            entity.setBrightness(kind.glows ? new Display.Brightness(15, 15) : new Display.Brightness(11, 15));
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

    /**
     * Particles seen by everyone, but thinned out to a quarter for players who also see effect models, so the
     * models are not buried under them.
     */
    public static <T> void particle(Particle particle, Location at, int count, double dx, double dy, double dz,
            double speed, T data) {
        boolean models = packs != null && packs.enabled() && plugin != null;
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) >= VIEW_SQUARED) {
                continue;
            }
            int n = count;
            if (models && packs.hasPack(player)) {
                n = count / 4 + (Math.random() < (count % 4) / 4.0 ? 1 : 0);
            }
            if (n > 0) {
                player.spawnParticle(particle, at, n, dx, dy, dz, speed, data);
            }
        }
    }

    /** Hides a stand-in entity (block-display shapes, say) from players who see an effect model in its place. */
    public static void hideFromModelViewers(Entity entity) {
        if (packs == null || !packs.enabled() || plugin == null) {
            return;
        }
        for (Player player : entity.getWorld().getPlayers()) {
            if (packs.hasPack(player)) {
                player.hideEntity(plugin, entity);
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
        particle(Particle.DUST, at, Math.max(1, count), spread, spread, spread, 0, dust(color, size));
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
        arcParticles(center, forward, radius, color, size, yOffset);
        Vector dir = forward.clone().normalize();
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

    /** Only the particle half of {@link #arc}, for players without the resource pack. */
    public static void arcParticles(Location center, Vector forward, double radius, Color color, float size,
            double yOffset) {
        Vector dir = forward.clone().normalize();
        Vector side = dir.getCrossProduct(new Vector(0, 1, 0));
        side = side.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : side.normalize();
        for (int i = -9; i <= 9; i++) {
            double t = i * Math.toRadians(8);
            Location point = center.clone().add(side.clone().multiply(Math.sin(t) * radius))
                    .subtract(dir.clone().multiply((1 - Math.cos(t)) * radius * 0.6)).add(0, yOffset, 0);
            dustFor(point, color, Math.abs(i) < 6 ? size : size * 0.7f, 1, 0.02);
        }
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

    // ------------------------------------------------------------------ shaped effects

    private static double rand(double spread) {
        return (Math.random() * 2 - 1) * spread;
    }

    /** A model that flies from {@code from} to {@code to} over {@code ticks}, facing its travel, then fades. */
    public static Effect fly(VfxKind kind, Location from, Location to, Color color, Vector3f size, int ticks, int linger) {
        Vector span = to.toVector().subtract(from.toVector());
        // Camera-facing glyphs stay upright; everything else points along its flight.
        Quaternionf aim = kind.billboard == Display.Billboard.FIXED ? facing(span) : new Quaternionf();
        return model(kind, from, color)
                .pose(new Vector3f(), aim, size)
                .to(1, Math.max(1, ticks), span.toVector3f(), aim, size)
                .life(ticks + linger + 1, Math.max(2, linger));
    }

    /** Models launched from {@code at} in every direction, arcing out and falling: debris, drops, embers. */
    public static void scatter(VfxKind kind, Location at, Color color, int count, double speed, double size, int ticks) {
        for (int i = 0; i < count; i++) {
            Vector out = new Vector(rand(1), Math.random() * .8 + .2, rand(1)).normalize().multiply(speed);
            Quaternionf spin = new Quaternionf().rotateXYZ((float) rand(3), (float) rand(3), (float) rand(3));
            Vector3f mid = out.toVector3f().mul(.6f).add(0, (float) speed * .35f, 0);
            Vector3f end = out.toVector3f().add(0, (float) -speed * .5f, 0);
            Vector3f scale = scale(size * (.6 + Math.random() * .6));
            model(kind, at, color)
                    .pose(new Vector3f(), spin, scale)
                    .to(1, ticks / 2, mid, new Quaternionf(spin).rotateX(1.5f), scale)
                    .to(ticks / 2 + 1, ticks / 2, end, new Quaternionf(spin).rotateX(3f).rotateY(1f), scale)
                    .life(ticks + 2, Math.max(3, ticks / 3));
        }
    }

    /**
     * Crystals, spikes or blades bursting up out of the ground in a ring, leaning outward, then sinking back.
     * {@code kind} is usually {@link VfxKind#SHARD}, {@link VfxKind#SPIKE} or {@link VfxKind#BLADE}.
     */
    public static void erupt(VfxKind kind, Location ground, Color color, int count, double radius, double height,
            int ticks) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + rand(.25);
            double r = radius * (count > 1 ? .75 + Math.random() * .4 : 0);
            Location at = ground.clone().add(Math.cos(a) * r, -.1, Math.sin(a) * r);
            float lean = (float) (count > 1 ? .25 + Math.random() * .3 : rand(.15));
            Quaternionf tilt = new Quaternionf().rotateY((float) -a).rotateX(0).rotateZ(-lean);
            double h = height * (.7 + Math.random() * .5);
            double w = Math.max(.6, h * .45);
            int rise = 2 + (int) (Math.random() * 3);
            model(kind, at, color)
                    .pose(new Vector3f(), tilt, scale(w * .4, .05, w * .4))
                    .to(1 + i % 3, rise, tilt, scale(w, h, w))
                    .to(ticks - 6, 6, new Vector3f(0, (float) -h * .6f, 0), tilt, scale(w * .8, h * .5, w * .8))
                    .life(ticks + 1, 6);
        }
    }

    /** One crystal, spike or blade thrusting out of the ground at a point. */
    public static void spike(VfxKind kind, Location ground, Color color, double height, int ticks) {
        erupt(kind, ground, color, 1, 0, height, ticks);
    }

    /** Jagged lightning between two points, re-forking each call. */
    public static void zap(Location from, Location to, Color color, double width) {
        Vector span = to.toVector().subtract(from.toVector());
        double length = span.length();
        if (length < .05) {
            return;
        }
        fallbackLine(from, to, color, (float) (.6 + width), .4);
        int pieces = Math.max(1, (int) Math.ceil(length / 2.5));
        Location point = from.clone();
        for (int i = 1; i <= pieces; i++) {
            Location next = i == pieces ? to.clone()
                    : from.clone().add(span.clone().multiply(i / (double) pieces)).add(rand(.5), rand(.5), rand(.5));
            Vector step = next.toVector().subtract(point.toVector());
            Quaternionf aim = facing(step).rotateZ((float) rand(Math.PI));
            double w = width * (2.5 + Math.random());
            model(VfxKind.ZAP, point, color)
                    .pose(aim, scale(w, w, step.length()))
                    .to(2, 3, aim, scale(w * .4, w * .4, step.length()))
                    .life(5, 3);
            point = next;
        }
    }

    /** A spinning gust of wind, a sweep of air facing {@code forward}. */
    public static void gust(Location at, Vector forward, Color color, double size, int ticks) {
        Quaternionf aim = facing(forward.clone().setY(forward.getY() * .3)).rotateZ((float) rand(.5));
        model(VfxKind.GUST, at, color)
                .pose(aim, scale(size * .5, 1, size * .5))
                .to(1, ticks, new Vector3f(forward.toVector3f()).normalize().mul((float) size * .6f),
                        new Quaternionf(aim).rotateY((float) rand(1.2)), scale(size * 1.3, 1, size * 1.3))
                .life(ticks + 1, Math.max(3, ticks / 2));
    }

    /** A swirl of gusts turning about a vertical axis, for whirlwinds. */
    public static void whirl(Location at, Color color, double radius, int ticks) {
        for (int i = 0; i < 3; i++) {
            float turn = (float) (i * Math.PI * 2 / 3);
            Location spot = at.clone().add(0, i * .5, 0);
            model(VfxKind.GUST, spot, color)
                    .pose(new Quaternionf().rotateY(turn), scale(radius * .6, 1, radius * .6))
                    .to(1, ticks, new Quaternionf().rotateY(turn + 4f), scale(radius * 1.4, 1, radius * 1.4))
                    .life(ticks + 1, Math.max(3, ticks / 2));
        }
    }

    /** Billowing puffs of smoke, ash, gas or mist that swell and drift up. */
    public static void smoke(Location at, Color color, int count, double radius, double size, int ticks) {
        for (int i = 0; i < count; i++) {
            Location spot = at.clone().add(rand(radius), rand(radius * .4), rand(radius));
            Quaternionf spin = new Quaternionf().rotateXYZ((float) rand(3), (float) rand(3), (float) rand(3));
            double s = size * (.7 + Math.random() * .6);
            model(VfxKind.SMOKE, spot, color)
                    .pose(new Vector3f(), spin, scale(s * .4))
                    .to(1, ticks, new Vector3f((float) rand(.4), (float) (.3 + Math.random() * .7), (float) rand(.4)),
                            new Quaternionf(spin).rotateY(1.2f), scale(s * 1.3))
                    .life(ticks + 1, Math.max(4, ticks / 2));
        }
    }

    /** A crest of water rolling forward {@code distance} blocks. */
    public static void wave(Location at, Vector forward, Color color, double width, double height, double distance,
            int ticks) {
        Vector dir = forward.clone().setY(0);
        if (dir.lengthSquared() < 1e-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Quaternionf aim = facing(dir);
        model(VfxKind.WAVE, at, color)
                .pose(new Vector3f(), aim, scale(width * .7, height * .3, height * .6))
                .to(1, ticks, dir.clone().multiply(distance).toVector3f(), aim, scale(width, height, height))
                .life(ticks + 3, 5);
    }

    /** Drops flung out from {@code at}: water, blood or venom. */
    public static void drops(Location at, Color color, int count, double speed, int ticks) {
        scatter(VfxKind.DROP, at, color, count, speed, 1.2, ticks);
    }

    /** Petals or leaves fluttering out and around from {@code at}. */
    public static void petals(Location at, Color color, int count, double radius, int ticks) {
        for (int i = 0; i < count; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = radius * (.3 + Math.random() * .7);
            Vector3f end = new Vector3f((float) (Math.cos(a) * r), (float) rand(radius * .5), (float) (Math.sin(a) * r));
            Vector3f mid = new Vector3f(end).mul(.5f).add((float) rand(.5), (float) (.3 + Math.random() * .5), (float) rand(.5));
            Quaternionf spin = new Quaternionf().rotateXYZ((float) rand(3), (float) rand(3), (float) rand(3));
            Vector3f size = scale(.7 + Math.random() * .6);
            model(VfxKind.PETAL, at, color)
                    .pose(new Vector3f(), spin, size)
                    .to(1, ticks / 2, mid, new Quaternionf(spin).rotateX(2.5f).rotateZ(1.5f), size)
                    .to(ticks / 2 + 1, ticks / 2, end, new Quaternionf(spin).rotateX(5f).rotateY(2f), size)
                    .life(ticks + 2, Math.max(4, ticks / 3));
        }
    }

    /** A glyph (heart, note, eye, star, butterfly, ...) that pops into view, drifts up and fades. */
    public static Effect glyph(VfxKind kind, Location at, Color color, double size, int ticks) {
        return model(kind, at, color)
                .pose(new Vector3f(), new Quaternionf(), scale(size * .3))
                .to(1, 3, new Vector3f(), new Quaternionf(), scale(size * 1.15))
                .to(4, 3, new Vector3f(0, .1f, 0), new Quaternionf(), scale(size))
                .to(7, Math.max(1, ticks - 7), new Vector3f(0, (float) size * .6f, 0), new Quaternionf(), scale(size * .9))
                .life(ticks, Math.max(3, ticks / 3));
    }

    /** A sigil drawn flat on the ground, turning slowly. */
    public static Effect sigil(VfxKind kind, Location ground, Color color, double radius, int ticks) {
        float turn = (float) (Math.random() * 6);
        return model(kind, ground.clone().add(0, .06, 0), color)
                .pose(new Quaternionf().rotateY(turn), scale(radius * .2, 1, radius * .2))
                .to(1, 5, new Quaternionf().rotateY(turn + .6f), scale(radius * 2.1, 1, radius * 2.1))
                .to(6, Math.max(1, ticks - 6), new Quaternionf().rotateY(turn + 3f), scale(radius * 2, 1, radius * 2))
                .life(ticks, Math.max(4, ticks / 4));
    }

    /**
     * Keeps {@code count} models circling an entity, called every tick or two from an aura. Each lives a few
     * ticks past the last call, so they vanish when the aura stops.
     */
    public static void orbit(String key, Entity around, VfxKind kind, Color color, int count, double radius,
            double height, double size, double angle, boolean faceOut) {
        for (int i = 0; i < count; i++) {
            double a = angle + i * Math.PI * 2 / count;
            Vector offset = new Vector(Math.cos(a) * radius, height + Math.sin(angle * 1.7 + i) * .15, Math.sin(a) * radius);
            Quaternionf pose = faceOut ? facing(new Vector(Math.cos(a), 0, Math.sin(a)))
                    : new Quaternionf().rotateY((float) -a).rotateX((float) (angle * 2 + i));
            Vector3f scale = scale(size);
            keep(key + ":" + i, 6, () -> model(kind, around.getLocation().add(offset), color)
                    .follow(around, offset).pose(pose, scale))
                    .offset(offset).move(2, new Vector3f(), pose, scale);
        }
    }

    /** A model that appears at a fixed spot, swells in and fades, with an optional slow turn. */
    public static Effect flash(VfxKind kind, Location at, Color color, Quaternionf rotation, Vector3f size, int ticks) {
        return model(kind, at, color)
                .pose(new Vector3f(), rotation, new Vector3f(size).mul(.4f))
                .to(1, 3, new Vector3f(), rotation, size)
                .life(ticks, Math.max(3, ticks / 2));
    }
}
