package dev.bleachsmp.fx;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Shared particle and sound helpers for abilities. Everything here is vanilla-client friendly. */
public final class Vfx {
    private Vfx() {
    }

    public static Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    public static void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, volume, pitch);
    }

    /** A horizontal ring of coloured dust. */
    public static void ring(Location center, double radius, int points, Color color, float size) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2 / points);
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0,
                    Math.sin(a) * radius), 1, 0, 0, 0, 0, dust(color, size));
        }
    }

    /** A horizontal ring of a data-less particle. */
    public static void ring(Location center, double radius, int points, Particle particle) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2 / points);
            center.getWorld().spawnParticle(particle, center.clone().add(Math.cos(a) * radius, 0,
                    Math.sin(a) * radius), 1, 0, 0, 0, 0);
        }
    }

    public static void burst(Location at, Color color, int count, double spread, float size) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, dust(color, size));
    }

    /** A straight dotted line of dust between two points. */
    public static void line(Location from, Location to, Color color, float size, double spacing) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.01) {
            return;
        }
        step.normalize().multiply(spacing);
        Location point = from.clone();
        for (double d = 0; d <= length; d += spacing) {
            from.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust(color, size));
            point.add(step);
        }
    }

    /**
     * A crescent arc facing {@code forward}, centred on {@code center}. The tips curve back toward the caster.
     */
    public static void arc(Location center, Vector forward, double radius, Color color, float size, double yOffset) {
        Vector dir = forward.clone().normalize();
        Vector side = dir.getCrossProduct(new Vector(0, 1, 0));
        side = side.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : side.normalize();
        for (int i = -9; i <= 9; i++) {
            double t = i * Math.toRadians(8);
            Location point = center.clone().add(side.clone().multiply(Math.sin(t) * radius))
                    .subtract(dir.clone().multiply((1 - Math.cos(t)) * radius * 0.6)).add(0, yOffset, 0);
            center.getWorld().spawnParticle(Particle.DUST, point, 1, 0.02, 0.02, 0.02, 0,
                    dust(color, Math.abs(i) < 6 ? size : size * 0.7f));
        }
    }

    /** A rising multi-strand helix around an entity. */
    public static void helix(Plugin plugin, Entity around, Color color, int ticks, int strands, double radius) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!around.isValid() || tick > ticks) {
                    cancel();
                    return;
                }
                Location base = around.getLocation();
                for (int s = 0; s < strands; s++) {
                    double a = tick * 0.6 + s * (Math.PI * 2 / strands);
                    double h = 2.4 * tick / ticks;
                    base.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * radius, h,
                            Math.sin(a) * radius), 2, 0.02, 0.02, 0.02, 0, dust(color, 1.5f));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A shockwave ring that grows outward along the ground. */
    public static void shockwave(Plugin plugin, Location center, Color color, double maxRadius, int ticks) {
        Location origin = center.clone().add(0, 0.15, 0);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick > ticks) {
                    cancel();
                    return;
                }
                double radius = 0.6 + (maxRadius - 0.6) * tick / ticks;
                ring(origin, radius, (int) Math.max(16, radius * 7), color, 1.3f);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Follows a moving entity (a projectile or a dashing player) with a particle trail. */
    public static void trail(Plugin plugin, Entity entity, Color color, Particle accent, int maxTicks) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!entity.isValid() || tick++ > maxTicks
                        || (entity instanceof AbstractArrow arrow && arrow.isInBlock())) {
                    cancel();
                    return;
                }
                Location at = entity.getLocation().add(0, entity.getHeight() / 2, 0);
                at.getWorld().spawnParticle(Particle.DUST, at, 3, 0.08, 0.08, 0.08, 0, dust(color, 1.2f));
                if (accent != null && tick % 2 == 0) {
                    at.getWorld().spawnParticle(accent, at, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
