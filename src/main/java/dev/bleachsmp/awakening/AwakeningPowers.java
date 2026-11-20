package dev.bleachsmp.awakening;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

final class AwakeningPowers {
    private final AwakeningService service;

    AwakeningPowers(AwakeningService service) {
        this.service = service;
    }

    static String name(Playstyle style, boolean advanced) {
        return switch (style) {
            case VANGUARD -> advanced ? "Heaven-Splitting Charge" : "Crimson Rush";
            case MIRAGE -> advanced ? "Mirror Labyrinth" : "Shattered Reflection";
            case MARKSMAN -> advanced ? "Astral Volley" : "Spirit Lance";
            case WAYFARER -> advanced ? "Windwalk" : "Riftstep";
            case ARTIFICER -> advanced ? "Living Forge" : "Stone Resonance";
            case KEEPER -> advanced ? "Sanctuary" : "Warding Pulse";
        };
    }

    static String description(Playstyle style, boolean advanced) {
        return switch (style) {
            case VANGUARD -> advanced ? "Rush forward, strike nearby enemies, and gain Speed."
                    : "Rush forward and slash the enemy you are aiming at.";
            case MIRAGE -> advanced ? "Vanish longer, fool hostile mobs, and gain Speed. Your next hit blinds."
                    : "Vanish briefly and fool hostile mobs. Your next hit blinds.";
            case MARKSMAN -> advanced ? "Fire a spread of piercing spirit arrows." : "Fire a fast spirit arrow.";
            case WAYFARER -> advanced ? "Launch forward with Speed and Slow Falling."
                    : "Make a quick leap with Slow Falling.";
            case ARTIFICER -> advanced ? "Gain Haste II, Night Vision, and Absorption." : "Gain Haste and Night Vision.";
            case KEEPER -> advanced ? "Grant nearby teammates Regeneration and Resistance."
                    : "Grant yourself and nearby teammates Absorption.";
        };
    }

    static String traitName(Playstyle style) {
        return switch (style) {
            case VANGUARD -> "Speed";
            case MIRAGE -> "Invisibility";
            case MARKSMAN -> "Jump Boost";
            case WAYFARER -> "Slow Falling";
            case ARTIFICER -> "Haste";
            case KEEPER -> "Absorption";
        };
    }

    static void applyTrait(Player player, Playstyle style) {
        PotionEffectType effect = switch (style) {
            case VANGUARD -> PotionEffectType.SPEED;
            case MIRAGE -> PotionEffectType.INVISIBILITY;
            case MARKSMAN -> PotionEffectType.JUMP_BOOST;
            case WAYFARER -> PotionEffectType.SLOW_FALLING;
            case ARTIFICER -> PotionEffectType.HASTE;
            case KEEPER -> PotionEffectType.ABSORPTION;
        };
        effect(player, effect, 5, 0);
    }

    boolean cast(Player player, Playstyle style, boolean advanced) {
        switch (style) {
            case VANGUARD -> {
                if (!vanguard(player, advanced)) {
                    return false;
                }
            }
            case MIRAGE -> mirage(player, advanced);
            case MARKSMAN -> marksman(player, advanced);
            case WAYFARER -> {
                dash(player, advanced ? 1.8 : 1.15, advanced ? 0.7 : 0.42);
                effect(player, PotionEffectType.SLOW_FALLING, advanced ? 9 : 4, 0);
                if (advanced) {
                    effect(player, PotionEffectType.SPEED, 10, 1);
                }
            }
            case ARTIFICER -> {
                effect(player, PotionEffectType.HASTE, advanced ? 40 : 22, advanced ? 1 : 0);
                effect(player, PotionEffectType.NIGHT_VISION, advanced ? 45 : 25, 0);
                if (advanced) {
                    effect(player, PotionEffectType.ABSORPTION, 20, 1);
                }
            }
            case KEEPER -> keeper(player, advanced);
        }
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0),
                advanced ? 50 : 24, 0.6, 0.65, 0.6, 0.25);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.9f, advanced ? 0.8f : 1.3f);
        return true;
    }

    private boolean vanguard(Player player, boolean advanced) {
        Entity target = player.getTargetEntity(advanced ? 8 : 5);
        if (!(target instanceof LivingEntity living) || !canStrike(player, living)) {
            player.sendMessage(Component.text("Aim at an enemy within reach.", NamedTextColor.GRAY));
            return false;
        }
        living.damage(advanced ? 12.0 : 8.0, player);
        if (advanced) {
            for (LivingEntity nearby : living.getLocation().getNearbyLivingEntities(3)) {
                if (!nearby.equals(living) && canStrike(player, nearby) && living.hasLineOfSight(nearby)
                        && player.hasLineOfSight(nearby)) {
                    nearby.damage(6.0, player);
                }
            }
            effect(player, PotionEffectType.SPEED, 8, 1);
        }
        dash(player, advanced ? 1.4 : 0.95, 0.2);
        var hit = living.getLocation().add(0, 1, 0);
        hit.getWorld().spawnParticle(Particle.SWEEP_ATTACK, hit, advanced ? 8 : 3, 0.55, 0.3, 0.55, 0.0);
        return true;
    }

    private void mirage(Player player, boolean advanced) {
        int seconds = advanced ? 10 : 5;
        effect(player, PotionEffectType.INVISIBILITY, seconds, 0);
        service.conceal(player, seconds);
        for (LivingEntity entity : player.getLocation().getNearbyLivingEntities(advanced ? 18 : 10)) {
            if (entity instanceof Mob mob && service.isHostile(mob) && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
        if (advanced) {
            effect(player, PotionEffectType.SPEED, seconds, 1);
        }
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0),
                35, 0.7, 0.6, 0.7, 0.2);
    }

    private static void marksman(Player player, boolean advanced) {
        Location eye = player.getEyeLocation();
        for (int spread = advanced ? -1 : 0; spread <= (advanced ? 1 : 0); spread++) {
            Vector direction = eye.getDirection().rotateAroundY(spread * Math.toRadians(9));
            Arrow arrow = player.getWorld().spawnArrow(eye, direction, advanced ? 2.7f : 2.5f, 0.0f);
            arrow.setShooter(player);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(advanced ? 3.0 : 4.0);
            if (advanced) {
                arrow.setPierceLevel(1);
            }
        }
    }

    private static void keeper(Player player, boolean advanced) {
        ward(player, advanced);
        for (Player other : player.getLocation().getNearbyPlayers(advanced ? 8 : 5)) {
            if (!player.equals(other) && AwakeningService.canPlay(other) && allies(player, other)) {
                ward(other, advanced);
            }
        }
    }

    private static void ward(Player player, boolean advanced) {
        if (advanced) {
            effect(player, PotionEffectType.REGENERATION, 9, 1);
            effect(player, PotionEffectType.RESISTANCE, 9, 0);
        } else {
            effect(player, PotionEffectType.ABSORPTION, 12, 0);
        }
    }

    private boolean canStrike(Player caster, LivingEntity target) {
        if (target instanceof Player other) {
            return !caster.equals(other) && caster.getWorld().getPVP() && !allies(caster, other)
                    && AwakeningService.canPlay(other);
        }
        return service.isHostile(target);
    }

    private static boolean allies(Player first, Player second) {
        var team = first.getScoreboard().getEntryTeam(first.getName());
        return team != null && team.hasEntry(second.getName());
    }

    private static void effect(Player player, PotionEffectType type, int seconds, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier));
    }

    private static void dash(Player player, double horizontal, double vertical) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        player.setVelocity(new Vector(-Math.sin(yaw) * horizontal, vertical, Math.cos(yaw) * horizontal));
    }
}
