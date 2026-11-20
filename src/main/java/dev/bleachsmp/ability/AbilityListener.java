package dev.bleachsmp.ability;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
    private final NamespacedKey cooldownReadyAtKey;
    private static final Color CRESCENT = Color.fromRGB(190, 215, 255);
    private static final Color CERO = Color.fromRGB(210, 20, 35);
    private static final Color REISHI = Color.fromRGB(120, 190, 255);
    private static final Color GUARD = Color.fromRGB(255, 205, 70);

    public AbilityListener(BleachSmpPlugin plugin) {
        this.plugin = plugin;
        this.cooldownReadyAtKey = new NamespacedKey(plugin, "starter_ability_ready_at");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStarterItemUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (event.getHand() != EquipmentSlot.HAND
                || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        // Paper initially denies block use on right-clicks in air even when item use is allowed.
        if (event.useItemInHand() == Event.Result.DENY
                || (action == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY)) {
            return;
        }

        Player player = event.getPlayer();
        Faction faction = plugin.getFaction(player.getUniqueId());
        if (faction == null || !isFactionStarter(player, faction)) {
            return;
        }

        // A released first form (Shikai, Resurrección, Schrift, Fullbring) replaces the sealed ability
        // with its technique, at any rank.
        var releases = plugin.awakenings().releases();
        if (releases.isActive(player)) {
            event.setCancelled(true);
            releases.useTechnique(player);
            return;
        }

        int requiredRank = Math.max(1, plugin.getConfig().getInt("abilities." + faction.name() + ".unlock-rank", 4));
        int currentRank = plugin.getRank(player.getUniqueId());
        if (currentRank > requiredRank) {
            player.sendMessage(Component.text("Your faction ability unlocks at Rank " + requiredRank + ".",
                    NamedTextColor.YELLOW));
            return;
        }

        var data = player.getPersistentDataContainer();
        long now = System.currentTimeMillis();
        long readyAt = data.getOrDefault(cooldownReadyAtKey, PersistentDataType.LONG, 0L);
        if (readyAt > now) {
            long seconds = (readyAt - now + 999L) / 1000L;
            player.sendMessage(Component.text("Ability ready in " + seconds + "s.", NamedTextColor.YELLOW));
            return;
        }
        data.remove(cooldownReadyAtKey);

        if (!activate(player, faction)) {
            return;
        }
        event.setCancelled(true);
        long cooldownSeconds = Math.max(0L,
                plugin.getConfig().getLong("abilities." + faction.name() + ".cooldown-seconds", 30L));
        if (cooldownSeconds > 0) {
            data.set(cooldownReadyAtKey, PersistentDataType.LONG, now + cooldownSeconds * 1000L);
        }
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
        Location hit = living.getLocation().add(0, living.getHeight() * 0.6, 0);
        Vector facing = player.getLocation().getDirection().setY(0);
        // A pale crescent drawn through the target, with a spark of reiatsu on impact.
        Vfx.arc(hit, facing, 1.4, CRESCENT, 1.4f, 0.25);
        Vfx.arc(hit, facing, 1.1, Color.WHITE, 0.9f, 0.25);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, hit, 1);
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, hit, 10, 0.3, 0.3, 0.3, 0.2);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hit, 6, 0.2, 0.2, 0.2, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
        player.getWorld().playSound(hit, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.1f);
        player.getWorld().playSound(hit, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 1.6f);
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
        Location start = player.getLocation();
        // A red cero flash at the mask, a burst ring at the feet, and a trail of soul fire behind the lunge.
        Vfx.burst(player.getEyeLocation().add(direction.clone().normalize().multiply(0.6)), CERO, 18, 0.15, 1.8f);
        Vfx.ring(start.clone().add(0, 0.1, 0), 1.0, 16, CERO, 1.2f);
        player.getWorld().spawnParticle(Particle.CRIT, start.clone().add(0, 1, 0), 12, 0.35, 0.35, 0.35, 0.1);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, start.clone().add(0, 0.3, 0), 6, 0.3, 0.1, 0.3, 0.02);
        Vfx.trail(plugin, player, CERO, Particle.SOUL_FIRE_FLAME, 10);
        player.getWorld().playSound(start, Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.7f);
        player.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.5f, 2.0f);
        player.getWorld().playSound(start, Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 0.8f);
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
        Location eye = player.getEyeLocation();
        // A reishi bow flash in front of the archer and a blue light trail behind the arrow.
        Vector side = direction.clone().getCrossProduct(new Vector(0, 1, 0));
        side = side.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : side.normalize();
        Location bow = eye.clone().add(direction.clone().normalize().multiply(0.8));
        Vfx.line(bow.clone().add(side.clone().multiply(-0.6)).add(0, 0.5, 0),
                bow.clone().add(side.clone().multiply(0.6)).add(0, -0.5, 0), REISHI, 0.8f, 0.15);
        player.getWorld().spawnParticle(Particle.END_ROD, eye, 8, 0.1, 0.1, 0.1, 0.02);
        Vfx.trail(plugin, arrow, REISHI, Particle.END_ROD, 80);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
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
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 1.2f);
        // Three gold guard plates orbit the player for the length of the Resistance.
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || tick > duration * 20) {
                    cancel();
                    return;
                }
                Location base = player.getLocation().add(0, 1.0, 0);
                for (int plate = 0; plate < 3; plate++) {
                    double a = tick * 0.18 + plate * (Math.PI * 2 / 3);
                    for (double h = -0.4; h <= 0.41; h += 0.4) {
                        base.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 1.0, h,
                                Math.sin(a) * 1.0), 1, 0, 0, 0, 0, Vfx.dust(GUARD, 1.1f));
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        player.sendMessage(Component.text("Bringer Guard!", NamedTextColor.LIGHT_PURPLE));
        return true;
    }
}
