package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
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
    private final BleachSmpPlugin plugin;
    private final AwakeningService service;

    AwakeningPowers(BleachSmpPlugin plugin, AwakeningService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** Signature colour of each faction's awakened power. */
    static Color color(Faction faction, boolean advanced) {
        return switch (faction) {
            case SOUL_REAPER -> advanced ? Color.fromRGB(25, 10, 20) : Color.fromRGB(200, 220, 255);
            case HOLLOW -> advanced ? Color.fromRGB(150, 0, 20) : Color.fromRGB(230, 230, 215);
            case QUINCY -> advanced ? Color.fromRGB(240, 250, 255) : Color.fromRGB(110, 170, 255);
            case FULLBRINGER -> advanced ? Color.fromRGB(255, 200, 60) : Color.fromRGB(255, 140, 70);
        };
    }

    private static Particle accent(Faction faction) {
        return switch (faction) {
            case SOUL_REAPER -> Particle.ELECTRIC_SPARK;
            case HOLLOW -> Particle.SOUL_FIRE_FLAME;
            case QUINCY -> Particle.END_ROD;
            case FULLBRINGER -> Particle.ENCHANT;
        };
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

    boolean cast(Player player, Faction faction, Playstyle style, boolean advanced) {
        Color color = color(faction, advanced);
        switch (style) {
            case VANGUARD -> {
                if (!vanguard(player, advanced, color)) {
                    return false;
                }
            }
            case MIRAGE -> mirage(player, advanced);
            case MARKSMAN -> marksman(player, advanced, color, accent(faction));
            case WAYFARER -> {
                Vfx.trail(plugin, player, color, Particle.CLOUD, advanced ? 20 : 12);
                Vfx.sound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, advanced ? 0.8f : 1.2f);
                dash(player, advanced ? 1.8 : 1.15, advanced ? 0.7 : 0.42);
                effect(player, PotionEffectType.SLOW_FALLING, advanced ? 9 : 4, 0);
                if (advanced) {
                    effect(player, PotionEffectType.SPEED, 10, 1);
                }
            }
            case ARTIFICER -> {
                Vfx.sound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, advanced ? 0.6f : 1.0f);
                Vfx.sound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
                Vfx.ring(player.getLocation().add(0, 0.1, 0), 1.2, 18, Particle.WAX_ON);
                effect(player, PotionEffectType.HASTE, advanced ? 40 : 22, advanced ? 1 : 0);
                effect(player, PotionEffectType.NIGHT_VISION, advanced ? 45 : 25, 0);
                if (advanced) {
                    effect(player, PotionEffectType.ABSORPTION, 20, 1);
                }
            }
            case KEEPER -> keeper(player, advanced, color);
        }
        flourish(player, faction, advanced, color);
        return true;
    }

    /** The faction's signature release flash. Advanced forms add a pillar, shockwave, and thunder. */
    private void flourish(Player player, Faction faction, boolean advanced, Color color) {
        Location loc = player.getLocation();
        Location chest = loc.clone().add(0, 1, 0);
        Vfx.burst(chest, color, advanced ? 60 : 25, 0.6, 1.6f);
        chest.getWorld().spawnParticle(accent(faction), chest, advanced ? 40 : 15, 0.6, 0.8, 0.6, 0.08);
        switch (faction) {
            case SOUL_REAPER -> {
                Vfx.sound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.7f, advanced ? 0.7f : 1.4f);
                Vfx.sound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
            }
            case HOLLOW -> {
                Vfx.sound(loc, Sound.ENTITY_WARDEN_ROAR, 0.6f, advanced ? 0.9f : 1.4f);
                chest.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 15, 0.6, 0.3, 0.6, 0.02);
            }
            case QUINCY -> {
                Vfx.sound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, advanced ? 1.0f : 1.6f);
                Vfx.sound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                Vfx.ring(loc.clone().add(0, 2.4, 0), advanced ? 0.8 : 0.45, 16, color, 1.1f);
            }
            case FULLBRINGER -> {
                Vfx.sound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, advanced ? 0.8f : 1.3f);
                Vfx.sound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
            }
        }
        if (advanced) {
            Vfx.helix(plugin, player, color, 24, 4, 1.3);
            Vfx.shockwave(plugin, loc, color, 7.0, 10);
            Vfx.sound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.3f);
            player.getWorld().spawnParticle(Particle.SONIC_BOOM, chest, 1);
        }
    }

    private boolean vanguard(Player player, boolean advanced, Color color) {
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
        Vfx.arc(hit, player.getLocation().getDirection().setY(0), advanced ? 2.4 : 1.6, color, 1.6f, 0);
        if (advanced) {
            Vfx.arc(hit, player.getLocation().getDirection().setY(0), 2.4, color, 1.6f, 0.6);
            Vfx.sound(hit, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        }
        Vfx.sound(hit, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
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
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 15, 0.4, 0.7, 0.4, 0.02);
        Vfx.sound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, advanced ? 0.8f : 1.2f);
        Vfx.sound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7f, 1.4f);
    }

    private void marksman(Player player, boolean advanced, Color color, Particle accent) {
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
            Vfx.trail(plugin, arrow, color, accent, 60);
        }
        Vfx.burst(eye.clone().add(eye.getDirection()), color, 12, 0.15, 1.4f);
        Vfx.sound(eye, Sound.ENTITY_ARROW_SHOOT, 1.0f, advanced ? 0.8f : 1.4f);
        Vfx.sound(eye, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
    }

    private static void keeper(Player player, boolean advanced, Color color) {
        ward(player, advanced);
        Vfx.ring(player.getLocation().add(0, 0.2, 0), 0.8, 14, color, 1.1f);
        for (Player other : player.getLocation().getNearbyPlayers(advanced ? 8 : 5)) {
            if (!player.equals(other) && AwakeningService.canPlay(other) && allies(player, other)) {
                ward(other, advanced);
                Vfx.ring(other.getLocation().add(0, 0.2, 0), 0.8, 14, color, 1.1f);
                other.getWorld().spawnParticle(Particle.HEART, other.getLocation().add(0, 2, 0), 2, 0.3, 0.1, 0.3, 0);
            }
        }
        Vfx.ring(player.getLocation().add(0, 0.3, 0), advanced ? 8 : 5, advanced ? 48 : 32, color, 1.2f);
        Vfx.sound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 1.6f);
    }

    private static void ward(Player player, boolean advanced) {
        if (advanced) {
            effect(player, PotionEffectType.REGENERATION, 9, 1);
            effect(player, PotionEffectType.RESISTANCE, 9, 0);
        } else {
            effect(player, PotionEffectType.ABSORPTION, 12, 0);
        }
    }

    boolean canStrike(Player caster, LivingEntity target) {
        if (target instanceof Player other) {
            return !caster.equals(other) && caster.getWorld().getPVP() && !allies(caster, other)
                    && AwakeningService.canPlay(other);
        }
        return service.isHostile(target);
    }

    static boolean allies(Player first, Player second) {
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
