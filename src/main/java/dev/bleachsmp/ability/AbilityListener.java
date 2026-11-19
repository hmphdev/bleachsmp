package dev.bleachsmp.ability;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class AbilityListener implements Listener {
    private final BleachSmpPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public AbilityListener(BleachSmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onStarterItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        Player player = event.getPlayer();
        Faction faction = plugin.getFaction(player.getUniqueId());
        if (faction == null || !isFactionStarter(player, faction)) {
            return;
        }

        int requiredRank = Math.max(1, plugin.getConfig().getInt("abilities." + faction.name() + ".unlock-rank", 4));
        int currentRank = plugin.getRank(player.getUniqueId());
        if (currentRank > requiredRank) {
            player.sendMessage(Component.text("Your faction ability unlocks at Rank " + requiredRank + ".",
                    NamedTextColor.YELLOW));
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            long seconds = (readyAt - now + 999L) / 1000L;
            player.sendMessage(Component.text("Ability ready in " + seconds + "s.", NamedTextColor.YELLOW));
            return;
        }

        if (!activate(player, faction)) {
            return;
        }
        event.setCancelled(true);
        long cooldownSeconds = Math.max(0L,
                plugin.getConfig().getLong("abilities." + faction.name() + ".cooldown-seconds", 30L));
        cooldowns.put(player.getUniqueId(), now + cooldownSeconds * 1000L);
    }

    private boolean isFactionStarter(Player player, Faction faction) {
        var item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        return "starter_weapon".equals(data.get(plugin.itemTypeKey(), PersistentDataType.STRING))
                && faction.name().equals(data.get(plugin.itemFactionKey(), PersistentDataType.STRING));
    }

    private boolean activate(Player player, Faction faction) {
        return switch (faction) {
            case SOUL_REAPER -> soulReaperSlash(player);
            case HOLLOW -> hollowLunge(player);
            case QUINCY -> quincyArrow(player);
            case FULLBRINGER -> fullbringerGuard(player);
        };
    }

    private boolean soulReaperSlash(Player player) {
        RayTraceResult result = player.rayTraceEntities(4);
        Entity target = result == null ? null : result.getHitEntity();
        if (!(target instanceof LivingEntity living) || target == player) {
            player.sendMessage(Component.text("No target in range for Crescent Cut.", NamedTextColor.GRAY));
            return false;
        }
        living.damage(plugin.getConfig().getDouble("abilities.SOUL_REAPER.damage", 6.0), player);
        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true, true));
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, living.getLocation().add(0, 1, 0), 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
        player.sendMessage(Component.text("Crescent Cut!", NamedTextColor.WHITE));
        return true;
    }

    private boolean hollowLunge(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        if (direction.lengthSquared() < 0.01) {
            direction = new Vector(0, 0, 1);
        }
        double strength = Math.max(0.1, plugin.getConfig().getDouble("abilities.HOLLOW.lunge-strength", 1.35));
        player.setVelocity(direction.multiply(strength).setY(0.25));
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 12, 0.35, 0.35, 0.35, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.7f);
        player.sendMessage(Component.text("Cero Rush!", NamedTextColor.RED));
        return true;
    }

    private boolean quincyArrow(Player player) {
        Vector direction = player.getLocation().getDirection();
        Arrow arrow = player.getWorld().spawnArrow(player.getEyeLocation(), direction,
                (float) Math.max(0.1, plugin.getConfig().getDouble("abilities.QUINCY.arrow-speed", 2.8)), 0.0f);
        arrow.setShooter(player);
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        arrow.setDamage(Math.max(0.0, plugin.getConfig().getDouble("abilities.QUINCY.arrow-damage", 7.0)));
        arrow.setCritical(true);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getEyeLocation(), 8, 0.1, 0.1, 0.1, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.5f);
        player.sendMessage(Component.text("Heilig Pfeil!", NamedTextColor.AQUA));
        return true;
    }

    private boolean fullbringerGuard(Player player) {
        int duration = Math.max(1, plugin.getConfig().getInt("abilities.FULLBRINGER.duration-seconds", 5));
        int amplifier = Math.max(0, plugin.getConfig().getInt("abilities.FULLBRINGER.resistance-amplifier", 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration * 20, amplifier,
                false, true, true));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                18, 0.5, 0.7, 0.5, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.6f);
        player.sendMessage(Component.text("Bringer Guard!", NamedTextColor.LIGHT_PURPLE));
        return true;
    }
}
