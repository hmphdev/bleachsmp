package dev.bleachsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ProgressionListener implements Listener {
    private final BleachSmpPlugin plugin;

    public ProgressionListener(BleachSmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.applyRankStats(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (event.getDamageSource().getCausingEntity() instanceof Player killer
                && plugin.awardPvpKill(killer, victim)) {
            killer.sendMessage(Component.text("PvP rank credit awarded.", NamedTextColor.GRAY));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !target.getPersistentDataContainer().has(plugin.bossIdKey(), PersistentDataType.STRING)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker == null || event.getFinalDamage() <= 0.0) {
            return;
        }

        var data = target.getPersistentDataContainer();
        var contributions = data.get(plugin.bossDamageKey(), PersistentDataType.TAG_CONTAINER);
        if (contributions == null) {
            contributions = data.getAdapterContext().newPersistentDataContainer();
        }
        NamespacedKey attackerKey = contributionKey(attacker);
        double totalDamage = contributions.getOrDefault(attackerKey, PersistentDataType.DOUBLE, 0.0);
        contributions.set(attackerKey, PersistentDataType.DOUBLE, totalDamage + event.getFinalDamage());
        data.set(plugin.bossDamageKey(), PersistentDataType.TAG_CONTAINER, contributions);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBossDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) {
            return;
        }
        String bossId = boss.getPersistentDataContainer().get(plugin.bossIdKey(), PersistentDataType.STRING);
        if (bossId == null) {
            return;
        }

        var contributions = boss.getPersistentDataContainer().get(plugin.bossDamageKey(),
                PersistentDataType.TAG_CONTAINER);
        if (contributions == null) {
            return;
        }

        var maxHealth = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double contributionThreshold = Math.max(0.0,
                (maxHealth == null ? 20.0 : maxHealth.getValue())
                        * plugin.getConfig().getDouble("progression.boss.minimum-contribution-percent", 0.05));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            double damage = contributions.getOrDefault(contributionKey(player), PersistentDataType.DOUBLE, 0.0);
            if (damage > 0.0 && damage >= contributionThreshold) {
                plugin.awardBossFirstClear(player, bossId);
            }
        }
    }

    private NamespacedKey contributionKey(Player player) {
        return new NamespacedKey(plugin, player.getUniqueId().toString());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onRankOneStrength(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || plugin.getFaction(player.getUniqueId()) == null
                || plugin.getRank(player.getUniqueId()) != 1
                || event.getModifiedType() != PotionEffectType.STRENGTH) {
            return;
        }

        PotionEffect incoming = event.getNewEffect();
        int maxAmplifier = Math.max(0, plugin.getConfig().getInt("progression.rank-1-max-strength-amplifier", 0));
        if (incoming != null && incoming.getAmplifier() > maxAmplifier) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.getRank(player.getUniqueId()) == 1) {
                    PotionEffect current = player.getPotionEffect(PotionEffectType.STRENGTH);
                    int duration = current == null ? incoming.getDuration() : current.getDuration();
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, maxAmplifier,
                            incoming.isAmbient(), incoming.hasParticles(), incoming.hasIcon()), true);
                }
            });
            return;
        }
        if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED
                || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.getRank(player.getUniqueId()) == 1) {
                PotionEffect current = player.getPotionEffect(PotionEffectType.STRENGTH);
                if (current != null && current.getAmplifier() > maxAmplifier) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, current.getDuration(),
                            maxAmplifier, current.isAmbient(), current.hasParticles(), current.hasIcon()), true);
                }
            }
        });
    }
}
