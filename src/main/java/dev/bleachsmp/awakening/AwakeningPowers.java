package dev.bleachsmp.awakening;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Shared awakening rules: secondary-habit traits and who a power may strike or help. */
final class AwakeningPowers {
    private final AwakeningService service;

    AwakeningPowers(AwakeningService service) {
        this.service = service;
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

    /** A short bonus on release from the secondary habit. */
    static void applyTrait(Player player, Playstyle style) {
        PotionEffectType effect = switch (style) {
            case VANGUARD -> PotionEffectType.SPEED;
            case MIRAGE -> PotionEffectType.INVISIBILITY;
            case MARKSMAN -> PotionEffectType.JUMP_BOOST;
            case WAYFARER -> PotionEffectType.SLOW_FALLING;
            case ARTIFICER -> PotionEffectType.HASTE;
            case KEEPER -> PotionEffectType.ABSORPTION;
        };
        player.addPotionEffect(new PotionEffect(effect, 5 * 20, 0));
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
}
