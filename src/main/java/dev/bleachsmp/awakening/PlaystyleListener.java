package dev.bleachsmp.awakening;

import dev.bleachsmp.awakening.Playstyle.Habit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;

public final class PlaystyleListener implements Listener {
    private final AwakeningService awakenings;

    public PlaystyleListener(AwakeningService awakenings) {
        this.awakenings = awakenings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (AwakeningService.canPlay(player) && awakenings.hasFirstForm(player) && awakenings.holdsStarter(player)) {
            event.setCancelled(true);
            awakenings.release(player, null);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        awakenings.checkUnlock(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        awakenings.clearConcealment(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && awakenings.isConcealed(player)
                && awakenings.isHostile(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity attacker = damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter : damager;
        if (event.getEntity() instanceof Player defender && !defender.equals(attacker) && isOpponent(attacker)
                && event.getDamage(DamageModifier.BLOCKING) < 0) {
            awakenings.record(defender, Habit.GUARD);
        }
        if (!(attacker instanceof Player player) || player.equals(event.getEntity()) || event.getFinalDamage() <= 0) {
            return;
        }
        awakenings.onConcealedAttack(player, event.getEntity());
        if (!isOpponent(event.getEntity()) || !awakenings.canProgress(player)) {
            return;
        }
        if (damager instanceof Projectile && event.getCause() == DamageCause.PROJECTILE) {
            awakenings.record(player, Habit.RANGED);
        } else if (damager.equals(player) && (event.getCause() == DamageCause.ENTITY_ATTACK
                || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK)) {
            if (player.isSprinting()) {
                awakenings.record(player, Habit.MELEE, Habit.RUSH);
            } else if (player.isSneaking()) {
                awakenings.record(player, Habit.MELEE, Habit.AMBUSH);
            } else {
                awakenings.record(player, Habit.MELEE);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        Material type = block.getType();
        if ((Tag.CROPS.isTagged(type) || type == Material.NETHER_WART || type == Material.COCOA)
                && block.getBlockData() instanceof Ageable crop && crop.getAge() == crop.getMaximumAge()) {
            awakenings.record(event.getPlayer(), Habit.HARVEST);
        } else if (Tag.ITEMS_PICKAXES.isTagged(event.getPlayer().getInventory().getItemInMainHand().getType())
                && (Tag.BASE_STONE_OVERWORLD.isTagged(type) || Tag.BASE_STONE_NETHER.isTagged(type)
                || type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS)) {
            awakenings.record(event.getPlayer(), Habit.MINING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuild(BlockPlaceEvent event) {
        if (event.canBuild() && event.getBlockPlaced().getType().isSolid()) {
            awakenings.record(event.getPlayer(), Habit.BUILDING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(PlayerStatisticIncrementEvent event) {
        if (event.getStatistic() == Statistic.CRAFT_ITEM && event.getNewValue() > event.getPreviousValue()) {
            awakenings.record(event.getPlayer(), Habit.CRAFTING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        awakenings.record(event.getEnchanter(), Habit.ENCHANTING);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            awakenings.record(event.getPlayer(), Habit.FISHING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            awakenings.record(player, Habit.HARVEST);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSupport(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player player) || !awakenings.canProgress(player)
                || event.getPotion().getEffects().isEmpty()
                || !event.getPotion().getEffects().stream()
                        .allMatch(effect -> effect.getType().getEffectCategory() == PotionEffectType.Category.BENEFICIAL)) {
            return;
        }
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player other && !other.equals(player) && AwakeningService.canPlay(other)
                    && event.getIntensity(other) >= 0.25) {
                awakenings.record(player, Habit.SUPPORT);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplore(PlayerMoveEvent event) {
        // A physical crossing into a new 64x64 area, not a world change, teleport, or head movement.
        if (event instanceof PlayerTeleportEvent || !event.hasChangedBlock()) {
            return;
        }
        var to = event.getTo();
        int x = to.getBlockX() >> 6;
        int z = to.getBlockZ() >> 6;
        if (x != (event.getFrom().getBlockX() >> 6) || z != (event.getFrom().getBlockZ() >> 6)) {
            awakenings.discover(event.getPlayer(), to.getWorld().getUID() + ":" + x + ":" + z);
        }
    }

    private boolean isOpponent(Entity entity) {
        return entity instanceof Player player ? AwakeningService.canPlay(player) : awakenings.isHostile(entity);
    }
}
