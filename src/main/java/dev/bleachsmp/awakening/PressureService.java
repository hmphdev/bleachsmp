package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Spiritual pressure. Every player carries a level from rank and release. It mostly gives
 * information: strong reiatsu nearby is felt. In a real fight, a much stronger opponent gives
 * a small, invisible edge (less damage dealt to them, slower cooldowns) that fades as the
 * weaker player adapts. It never slows, blinds or otherwise takes control away.
 */
public final class PressureService implements Listener {
    private static final long FIGHT_TIMEOUT = 15_000L;

    private record Fight(long start, long lastHit) {
    }

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    /** Keyed "low|high" by UUID order. */
    private final Map<String, Fight> fights = new HashMap<>();
    /** Keyed "observer>source": when the observer last felt that source, and at what level. */
    private final Map<String, long[]> sensed = new HashMap<>();
    /** Keyed "weaker>stronger": which fight-start the weaker player was last told about, and if they adapted. */
    private final Map<String, long[]> told = new HashMap<>();
    private final Map<UUID, Long> sneakingSince = new HashMap<>();
    private BukkitTask task;

    PressureService(BleachSmpPlugin plugin, AwakeningService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
    }

    /** 1 (fresh, unreleased) to 9 (rank 1 in Bankai). */
    public int level(Player player) {
        int rank = plugin.getFaction(player.getUniqueId()) == null ? 5 : plugin.getRank(player.getUniqueId());
        int level = 6 - Math.max(1, Math.min(5, rank));
        var releases = service.releases();
        if (releases.isActive(player)) {
            level += releases.isAdvanced(player) ? 4 : 2;
        }
        return level;
    }

    /** Suppressed players have hidden their reiatsu by staying crouched, unreleased. */
    public boolean suppressed(Player player) {
        Long since = sneakingSince.get(player.getUniqueId());
        return since != null && player.isSneaking() && !service.releases().isActive(player)
                && System.currentTimeMillis() - since >= setting("suppress-seconds", 3) * 1000L;
    }

    /** Multiplier on technique and signature cooldowns for a caster fighting a much stronger opponent. */
    double cooldownFactor(Player caster) {
        if (!enabled()) {
            return 1;
        }
        double worst = 0;
        long now = System.currentTimeMillis();
        for (Player other : caster.getWorld().getPlayers()) {
            if (other != caster) {
                worst = Math.max(worst, weight(caster, other, now));
            }
        }
        return 1 + worst * plugin.getConfig().getDouble("pressure.cooldown-penalty", 0.15);
    }

    /**
     * How strongly {@code stronger}'s pressure weighs on {@code weaker} right now: 1 at the
     * start of a fight, fading to 0 as they adapt. 0 when they are not fighting or the gap is small.
     */
    private double weight(Player weaker, Player stronger, long now) {
        Fight fight = fights.get(key(weaker, stronger));
        if (fight == null || now - fight.lastHit() > FIGHT_TIMEOUT
                || level(stronger) - level(weaker) < setting("edge-gap", 3)
                || service.crisis().friends(weaker, stronger)) {
            return 0;
        }
        double adapt = setting("adapt-seconds", 10) * 1000.0;
        double elapsed = now - fight.start();
        // Full weight while adapting, then a five-second fade.
        return elapsed <= adapt ? 1 : Math.max(0, 1 - (elapsed - adapt) / 5000.0);
    }

    // ---- The fight edge ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker == victim) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = key(attacker, victim);
        Fight fight = fights.get(key);
        fights.put(key, fight == null || now - fight.lastHit() > FIGHT_TIMEOUT
                ? new Fight(now, now) : new Fight(fight.start(), now));
        double weight = weight(attacker, victim, now);
        if (weight > 0) {
            event.setDamage(event.getDamage() * (1 - weight * plugin.getConfig().getDouble("pressure.damage-penalty", 0.10)));
        }
        announce(attacker, victim);
        announce(victim, attacker);
    }

    /** Tells the weaker player once when the weight lands, and once when they have adapted. */
    private void announce(Player weaker, Player stronger) {
        Fight fight = fights.get(key(weaker, stronger));
        if (fight == null || level(stronger) - level(weaker) < setting("edge-gap", 3)
                || service.crisis().friends(weaker, stronger)) {
            return;
        }
        long[] state = told.computeIfAbsent(weaker.getUniqueId() + ">" + stronger.getUniqueId(), ignored -> new long[2]);
        if (state[0] != fight.start()) {
            state[0] = fight.start();
            state[1] = 0;
            weaker.sendMessage(Component.text(stronger.getName() + "'s reiatsu weighs on you. You'll adapt in "
                    + setting("adapt-seconds", 10) + "s.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            weaker.playSound(weaker.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.8f, 0.5f);
        }
    }

    private void tick() {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        fights.values().removeIf(fight -> now - fight.lastHit() > FIGHT_TIMEOUT * 4);
        for (var entry : told.entrySet()) {
            long[] state = entry.getValue();
            if (state[1] != 0) {
                continue;
            }
            String[] ids = entry.getKey().split(">");
            Player weaker = plugin.getServer().getPlayer(UUID.fromString(ids[0]));
            Player stronger = plugin.getServer().getPlayer(UUID.fromString(ids[1]));
            Fight fight = weaker == null || stronger == null ? null : fights.get(key(weaker, stronger));
            if (fight != null && fight.start() == state[0] && now - fight.lastHit() <= FIGHT_TIMEOUT
                    && now - fight.start() >= setting("adapt-seconds", 10) * 1000L) {
                state[1] = 1;
                weaker.sendMessage(Component.text("You've adapted to " + stronger.getName() + "'s reiatsu.",
                        NamedTextColor.GRAY, TextDecoration.ITALIC));
            }
        }
        sense(now);
    }

    // ---- Sensing ----

    private void sense(long now) {
        int radius = setting("sense-radius", 40);
        int gap = setting("sense-gap", 2);
        long cooldown = setting("sense-cooldown-seconds", 90) * 1000L;
        for (Player observer : plugin.getServer().getOnlinePlayers()) {
            if (!active(observer)) {
                continue;
            }
            int own = level(observer);
            for (Player source : observer.getWorld().getPlayers()) {
                if (source == observer || !active(source) || suppressed(source) || !observer.canSee(source)
                        || source.hasPotionEffect(PotionEffectType.INVISIBILITY)
                        || source.getLocation().distanceSquared(observer.getLocation()) > (double) radius * radius
                        || service.crisis().friends(observer, source)) {
                    continue;
                }
                int level = level(source);
                int difference = level - own;
                if (difference < gap) {
                    continue;
                }
                long[] last = sensed.computeIfAbsent(observer.getUniqueId() + ">" + source.getUniqueId(),
                        ignored -> new long[] {0, 0});
                // A sudden spike, such as a Bankai release, is felt immediately.
                if (now - last[0] < cooldown && level <= last[1]) {
                    continue;
                }
                last[0] = now;
                last[1] = level;
                feel(observer, source, difference);
            }
        }
    }

    private void feel(Player observer, Player source, int difference) {
        String strength = difference >= 5 ? "An overwhelming reiatsu is close"
                : difference >= 3 ? "A heavy reiatsu presses down on you" : "You sense a strong reiatsu nearby";
        Component message = Component.text(strength + ", to the " + direction(observer, source) + ".",
                difference >= 5 ? NamedTextColor.DARK_RED : difference >= 3 ? NamedTextColor.RED : NamedTextColor.GRAY,
                TextDecoration.ITALIC);
        // Released players' action bar shows their cooldowns, so they get it in chat instead.
        if (service.releases().isActive(observer)) {
            observer.sendMessage(message);
        } else {
            observer.sendActionBar(message);
        }
        observer.playSound(observer.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f + difference * 0.1f, 0.5f);
        if (difference >= 5) {
            observer.playSound(observer.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.6f);
        }
    }

    private static String direction(Player observer, Player source) {
        double dx = source.getX() - observer.getX();
        double dz = source.getZ() - observer.getZ();
        String[] names = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return names[Math.floorMod((int) Math.round(angle / 45), 8)];
    }

    // ---- Suppression ----

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) {
            Long since = sneakingSince.remove(player.getUniqueId());
            if (since != null && !service.releases().isActive(player)
                    && System.currentTimeMillis() - since >= setting("suppress-seconds", 3) * 1000L) {
                player.sendActionBar(Component.text("Your reiatsu returns.", NamedTextColor.GRAY));
            }
            return;
        }
        long start = System.currentTimeMillis();
        sneakingSince.put(player.getUniqueId(), start);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (enabled() && sneakingSince.getOrDefault(player.getUniqueId(), 0L) == start && suppressed(player)) {
                player.sendActionBar(Component.text("Reiatsu suppressed.", NamedTextColor.DARK_GRAY,
                        TextDecoration.ITALIC));
            }
        }, setting("suppress-seconds", 3) * 20L + 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sneakingSince.remove(id);
        String prefix = id.toString();
        sensed.keySet().removeIf(key -> key.contains(prefix));
        told.keySet().removeIf(key -> key.contains(prefix));
    }

    // ---- Helpers ----

    private boolean active(Player player) {
        return (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && !player.isDead() && !service.bankai().inInnerWorld(player);
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static String key(Player a, Player b) {
        return a.getUniqueId().compareTo(b.getUniqueId()) < 0
                ? a.getUniqueId() + "|" + b.getUniqueId() : b.getUniqueId() + "|" + a.getUniqueId();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("pressure.enabled", true);
    }

    private int setting(String name, int fallback) {
        return Math.max(0, plugin.getConfig().getInt("pressure." + name, fallback));
    }
}
