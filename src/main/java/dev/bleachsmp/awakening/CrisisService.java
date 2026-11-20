package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Crisis awakening for first forms. Once a player's playstyle evidence is full their first form is dormant, and it
 * only wakes when a real rival pushes them to the brink. A hidden breaking-point meter fills during genuine player
 * fights; when it is full, the blow that would have killed them awakens the form instead.
 *
 * <p>Nobody moderates the SMP, so the plugin decides who counts as a rival: players who spend peaceful time near each
 * other (or share a team) are friends and cannot push each other's meter until one of them really kills the other.
 */
public final class CrisisService implements Listener {
    private static final long COMBAT_WINDOW = 10_000L;
    private static final long MUTUAL_WINDOW = 12_000L;
    private static final long FIGHT_END = 8_000L;
    private static final int BOND_TICK_SECONDS = 10;
    private static final long DAY = 86_400_000L;

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    private final File bondsFile;
    /** Sorted pair key → seconds spent near each other without fighting. */
    private final Map<String, Integer> peaceful = new HashMap<>();
    /** Sorted pair keys of players who have shared a team. */
    private final Set<String> teamed = new HashSet<>();
    /** "victim>opponent" → when that opponent may push the victim's meter again. */
    private final Map<String, Long> rematch = new HashMap<>();
    /** Sorted pair key → last time either player hit the other. */
    private final Map<String, Long> lastClash = new HashMap<>();
    private final Map<UUID, Fight> fights = new HashMap<>();
    /** Players mid-awakening: they cannot be damaged until the form bursts out. */
    private final Set<UUID> awakening = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean dirty;

    private static final class Fight {
        double meter;
        long lastCombat;
        long lastLowGain;
        long lastCounterGain;
        int peakAttackers = 1;
        boolean stirred;
        boolean brink;
        final Map<UUID, Long> hitBy = new HashMap<>();
        final Map<UUID, Long> hitOut = new HashMap<>();
        final Map<UUID, Double> contribution = new HashMap<>();
        final Set<String> once = new HashSet<>();
    }

    CrisisService(BleachSmpPlugin plugin, AwakeningService service) {
        this.plugin = plugin;
        this.service = service;
        bondsFile = new File(plugin.getDataFolder(), "bonds.yml");
    }

    public void start() {
        load();
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                trackBonds();
            }
        }.runTaskTimer(plugin, BOND_TICK_SECONDS * 20L, BOND_TICK_SECONDS * 20L));
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                drainFights();
            }
        }.runTaskTimer(plugin, 20L, 20L));
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                if (dirty) {
                    save();
                }
            }
        }.runTaskTimer(plugin, 6000L, 6000L));
    }

    public void shutdown() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        save();
    }

    // ---------------------------------------------------------------- settings

    private double setting(String key, double fallback, double min, double max) {
        return Math.clamp(plugin.getConfig().getDouble("awakenings.crisis." + key, fallback), min, max);
    }

    private double goal() {
        return setting("meter-goal", 100, 10, 1000);
    }

    private double lowHealth() {
        return setting("low-health", 6.0, 1.0, 20.0);
    }

    // ---------------------------------------------------------------- bonds

    private static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    boolean friends(Player a, Player b) {
        String key = pair(a.getUniqueId(), b.getUniqueId());
        return AwakeningPowers.allies(a, b) || teamed.contains(key)
                || peaceful.getOrDefault(key, 0) >= setting("friend-minutes", 60, 0, 10_000) * 60;
    }

    /** Every few seconds, players near each other who are not fighting grow closer. */
    private void trackBonds() {
        long now = System.currentTimeMillis();
        double radius = setting("friend-radius", 32, 4, 128);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < players.size(); i++) {
            Player a = players.get(i);
            if (!AwakeningService.canPlay(a)) {
                continue;
            }
            for (int j = i + 1; j < players.size(); j++) {
                Player b = players.get(j);
                if (!AwakeningService.canPlay(b) || !a.getWorld().equals(b.getWorld())
                        || a.getLocation().distanceSquared(b.getLocation()) > radius * radius) {
                    continue;
                }
                String key = pair(a.getUniqueId(), b.getUniqueId());
                if (AwakeningPowers.allies(a, b) && teamed.add(key)) {
                    dirty = true;
                }
                if (now - lastClash.getOrDefault(key, 0L) > 60_000L) {
                    peaceful.merge(key, BOND_TICK_SECONDS, Integer::sum);
                    dirty = true;
                }
            }
        }
    }

    /** A real kill ends a friendship: the pair starts again as strangers. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getPlayer();
        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) {
            String key = pair(dead.getUniqueId(), killer.getUniqueId());
            if (peaceful.remove(key) != null | teamed.remove(key)) {
                dirty = true;
            }
        }
        long now = System.currentTimeMillis();
        // Watching a comrade fall mid-fight pushes a dormant player closer to the edge.
        for (Player nearby : dead.getLocation().getNearbyPlayers(16)) {
            Fight fight = fights.get(nearby.getUniqueId());
            if (nearby.equals(dead) || fight == null || now - fight.lastCombat > COMBAT_WINDOW
                    || !friends(nearby, dead) || !service.isDormant(nearby)) {
                continue;
            }
            if (fight.once.add("fallen:" + dead.getUniqueId())) {
                gain(nearby, fight, null, 20);
            }
        }
        fights.remove(dead.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fights.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- the meter

    private boolean validOpponent(Player victim, Player attacker) {
        if (victim.equals(attacker) || !AwakeningService.canPlay(attacker) || friends(victim, attacker)) {
            return false;
        }
        long minTicks = (long) setting("opponent-min-playtime-minutes", 120, 0, 100_000) * 1200L;
        if (attacker.getStatistic(Statistic.PLAY_ONE_MINUTE) < minTicks) {
            return false;
        }
        return rematch.getOrDefault(victim.getUniqueId() + ">" + attacker.getUniqueId(), 0L)
                <= System.currentTimeMillis();
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /** Nothing can hurt a player in the middle of awakening. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAwakeningDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && awakening.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        lastClash.put(pair(victim.getUniqueId(), attacker.getUniqueId()), now);

        // The attacker fighting back while on the brink counts for their own meter.
        if (validOpponent(attacker, victim) && service.isDormant(attacker)) {
            Fight own = fights.computeIfAbsent(attacker.getUniqueId(), id -> new Fight());
            own.hitOut.put(victim.getUniqueId(), now);
            own.lastCombat = now;
            if (attacker.getHealth() <= lowHealth()
                    && now - own.hitBy.getOrDefault(victim.getUniqueId(), 0L) <= COMBAT_WINDOW
                    && now - own.lastCounterGain >= 700) {
                own.lastCounterGain = now;
                gain(attacker, own, victim.getUniqueId(), 10);
            }
        }

        if (!validOpponent(victim, attacker) || !service.isDormant(victim)) {
            return;
        }
        Fight fight = fights.computeIfAbsent(victim.getUniqueId(), id -> new Fight());
        fight.hitBy.put(attacker.getUniqueId(), now);
        fight.lastCombat = now;
        UUID foe = attacker.getUniqueId();
        // Only a real exchange counts: the victim must have been hitting this opponent too.
        if (now - fight.hitOut.getOrDefault(foe, 0L) <= MUTUAL_WINDOW) {
            double after = victim.getHealth() - event.getFinalDamage();
            if (after <= lowHealth() && now - fight.lastLowGain >= 4000) {
                fight.lastLowGain = now;
                gain(victim, fight, foe, 20);
            }
            ReleaseService releases = service.releases();
            if (releases.isActive(attacker) && !releases.isActive(victim) && fight.once.add("outclassed:" + foe)) {
                gain(victim, fight, foe, 25);
            }
            if (plugin.getRank(foe) < plugin.getRank(victim.getUniqueId()) && fight.once.add("outranked:" + foe)) {
                gain(victim, fight, foe, 15);
            }
        }
        fight.hitBy.values().removeIf(time -> now - time > COMBAT_WINDOW);
        int attackers = fight.hitBy.size();
        if (attackers > fight.peakAttackers) {
            gain(victim, fight, foe, 15.0 * (attackers - fight.peakAttackers));
            fight.peakAttackers = attackers;
        }

        // The blow that would kill them awakens the form instead.
        boolean lethal = event.getFinalDamage() >= victim.getHealth() + victim.getAbsorptionAmount();
        if (lethal && fight.meter >= goal()
                && ThreadLocalRandom.current().nextDouble() < setting("trigger-chance", 1.0, 0.0, 1.0)) {
            event.setCancelled(true);
            awaken(victim, attacker);
        }
    }

    private void gain(Player player, Fight fight, UUID from, double amount) {
        fight.meter += amount;
        if (from != null) {
            fight.contribution.merge(from, amount, Double::sum);
        }
        double goal = goal();
        // Faint hints that something is stirring; the number itself stays hidden.
        if (!fight.stirred && fight.meter >= goal / 2) {
            fight.stirred = true;
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.8f);
            player.sendActionBar(Component.text("Something inside you stirs…", NamedTextColor.DARK_GRAY,
                    TextDecoration.ITALIC));
        }
        if (!fight.brink && fight.meter >= goal) {
            fight.brink = true;
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 1.2f);
            player.sendActionBar(Component.text("Something is waiting for you to fall.", NamedTextColor.GRAY,
                    TextDecoration.ITALIC));
        }
    }

    /** Fights cool off fast; an opponent who pushed hard without an awakening must wait for a rematch. */
    private void drainFights() {
        long now = System.currentTimeMillis();
        var iterator = fights.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Fight fight = entry.getValue();
            if (now - fight.lastCombat <= FIGHT_END) {
                continue;
            }
            fight.meter -= 15;
            if (fight.meter > 0) {
                continue;
            }
            long until = now + (long) (setting("rematch-cooldown-days", 7, 0, 365) * DAY);
            fight.contribution.forEach((foe, amount) -> {
                if (amount >= 30) {
                    rematch.put(entry.getKey() + ">" + foe, until);
                    dirty = true;
                }
            });
            iterator.remove();
        }
    }

    // ---------------------------------------------------------------- the awakening

    private void awaken(Player player, Player rival) {
        UUID id = player.getUniqueId();
        Faction faction = plugin.getFaction(id);
        // Unlock straight away so a disconnect mid-scene cannot lose it.
        if (faction == null || !service.awakenFirst(player)) {
            return;
        }
        fights.remove(id);
        awakening.add(id);
        Color color = color(faction);
        String[] voice = voice(faction);

        player.setHealth(Math.max(player.getHealth(), 1.0));
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 6, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
        player.showTitle(Title.title(Component.empty(),
                Component.text(voice[0], NamedTextColor.GRAY, TextDecoration.ITALIC),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(900), Duration.ofMillis(200))));
        // The world seems to stop: nearby enemies are frozen in place for the moment.
        for (Player nearby : player.getLocation().getNearbyPlayers(10)) {
            if (!nearby.equals(player) && service.canStrike(player, nearby)) {
                nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 4, false, false, true));
                nearby.playSound(nearby.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);
            }
        }

        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    awakening.remove(id);
                    cancel();
                    return;
                }
                Location at = player.getLocation();
                if (tick % 10 == 0 && tick < 40) {
                    player.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.7f + tick * 0.01f);
                }
                if (tick < 40) {
                    // Reiatsu gathers inward, faster and denser as the voice speaks.
                    double radius = 3.0 - tick * 0.065;
                    for (int i = 0; i < 6; i++) {
                        double a = tick * 0.35 + i * Math.PI / 3;
                        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * radius,
                                0.2 + tick * 0.04, Math.sin(a) * radius), 1, 0, 0, 0, 0, Vfx.dust(color, 1.4f));
                    }
                }
                if (tick == 20) {
                    player.showTitle(Title.title(Component.empty(),
                            Component.text(voice[1], NamedTextColor.WHITE, TextDecoration.ITALIC),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(100))));
                    player.playSound(at, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.6f);
                }
                if (tick == 40) {
                    burst(player, rival, faction, color);
                }
                if (tick >= 50) {
                    awakening.remove(id);
                    cancel();
                    return;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void burst(Player player, Player rival, Faction faction, Color color) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.max(player.getHealth(), max * 0.5));
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        Location at = player.getLocation();
        Vfx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        Vfx.sound(at, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.3f);
        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        Vfx.burst(at.clone().add(0, 1, 0), color, 80, 1.2, 2.0f);
        Vfx.shockwave(plugin, at, color, 8.0, 12);
        Vfx.helix(plugin, player, color, 30, 3, 1.1);
        if (rival.isValid() && rival.getWorld().equals(player.getWorld())
                && rival.getLocation().distanceSquared(at) < 144) {
            rival.setVelocity(rival.getLocation().toVector().subtract(at.toVector()).setY(0).normalize()
                    .multiply(1.4).setY(0.5));
        }
        service.releaseAwakened(player);

        Bukkit.broadcast(Component.text(announcement(faction), faction.color(), TextDecoration.ITALIC));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.35f, 1.4f);
        }
    }

    private static Color color(Faction faction) {
        return switch (faction) {
            case SOUL_REAPER -> Color.fromRGB(120, 200, 255);
            case HOLLOW -> Color.fromRGB(200, 30, 40);
            case QUINCY -> Color.fromRGB(170, 230, 255);
            case FULLBRINGER -> Color.fromRGB(240, 190, 70);
        };
    }

    private static String[] voice(Faction faction) {
        return switch (faction) {
            case SOUL_REAPER -> new String[] {"…Why have you never asked my name?", "Then hear it. Call me."};
            case HOLLOW -> new String[] {"You're too weak to die here.", "So let ME out."};
            case QUINCY -> new String[] {"Your blood remembers every Quincy before you.", "Draw, and do not miss."};
            case FULLBRINGER -> new String[] {"Everything you've held onto…", "…is holding on to you now."};
        };
    }

    private static String announcement(Faction faction) {
        return switch (faction) {
            case SOUL_REAPER -> "Somewhere, a zanpakutō has spoken its name.";
            case HOLLOW -> "Somewhere, a Hollow has torn free of its limits.";
            case QUINCY -> "Somewhere, a Quincy's Schrift has been written.";
            case FULLBRINGER -> "Somewhere, a Fullbring has awakened.";
        };
    }

    // ---------------------------------------------------------------- persistence

    private void load() {
        if (!bondsFile.exists()) {
            return;
        }
        var yaml = YamlConfiguration.loadConfiguration(bondsFile);
        var pairs = yaml.getConfigurationSection("peaceful");
        if (pairs != null) {
            for (String key : pairs.getKeys(false)) {
                peaceful.put(key, pairs.getInt(key));
            }
        }
        teamed.addAll(yaml.getStringList("teamed"));
        var matches = yaml.getConfigurationSection("rematch");
        long now = System.currentTimeMillis();
        if (matches != null) {
            for (String key : matches.getKeys(false)) {
                long until = matches.getLong(key);
                if (until > now) {
                    rematch.put(key, until);
                }
            }
        }
    }

    private void save() {
        var yaml = new YamlConfiguration();
        peaceful.forEach((key, seconds) -> yaml.set("peaceful." + key, seconds));
        yaml.set("teamed", new ArrayList<>(teamed));
        long now = System.currentTimeMillis();
        rematch.entrySet().removeIf(entry -> entry.getValue() <= now);
        rematch.forEach((key, until) -> yaml.set("rematch." + key, until));
        try {
            yaml.save(bondsFile);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save bonds.yml: " + exception.getMessage());
        }
    }
}
