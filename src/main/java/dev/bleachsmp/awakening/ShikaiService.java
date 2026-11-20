package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Soul Reaper Shikai: a timed released state. Each playstyle awakens a different zanpakutō, and while
 * it is released the starter item's right-click ability becomes that zanpakutō's technique.
 */
public final class ShikaiService implements Listener {
    /** The zanpakutō a Soul Reaper awakens, chosen by the playstyle that unlocked their Shikai. */
    enum Zanpakuto {
        VANGUARD("Akagetsu", "Howl", "Crimson Fang", "Send a crescent wave of red reiatsu tearing forward.",
                Color.fromRGB(220, 30, 45), NamedTextColor.RED, BossBar.Color.RED),
        MIRAGE("Kagerō", "Deceive", "Haze Step", "Vanish into heat haze and strike from behind your target.",
                Color.fromRGB(150, 90, 230), NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE),
        MARKSMAN("Ryūsei", "Pierce", "Falling Star", "Launch a blade of light that bursts on impact.",
                Color.fromRGB(255, 205, 60), NamedTextColor.GOLD, BossBar.Color.YELLOW),
        WAYFARER("Hayate", "Soar", "Gale Passage", "Dash through enemies on a cutting gale.",
                Color.fromRGB(120, 235, 200), NamedTextColor.AQUA, BossBar.Color.GREEN),
        ARTIFICER("Iwagane", "Crush", "Mountain Breaker", "Slam the ground and hurl enemies skyward.",
                Color.fromRGB(165, 120, 75), NamedTextColor.GOLD, BossBar.Color.WHITE),
        KEEPER("Mamorigami", "Shelter", "Shrine Ward", "Heal and shield allies while driving enemies back.",
                Color.fromRGB(110, 190, 255), NamedTextColor.BLUE, BossBar.Color.BLUE);

        final String blade;
        final String command;
        final String technique;
        final String techniqueDescription;
        final Color color;
        final TextColor textColor;
        final BossBar.Color barColor;

        Zanpakuto(String blade, String command, String technique, String techniqueDescription, Color color,
                TextColor textColor, BossBar.Color barColor) {
            this.blade = blade;
            this.command = command;
            this.technique = technique;
            this.techniqueDescription = techniqueDescription;
            this.color = color;
            this.textColor = textColor;
            this.barColor = barColor;
        }

        static Zanpakuto of(Playstyle style) {
            return valueOf(style.name());
        }

        Particle.DustOptions dust(float size) {
            return new Particle.DustOptions(color, size);
        }
    }

    private static final class Release {
        final Zanpakuto blade;
        final long startedAt;
        final long endsAt;
        final BossBar bar;
        BukkitTask task;
        long techniqueReadyAt;

        Release(Zanpakuto blade, long startedAt, long endsAt, BossBar bar) {
            this.blade = blade;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.bar = bar;
        }
    }

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    private final Map<UUID, Release> active = new HashMap<>();

    ShikaiService(BleachSmpPlugin plugin, AwakeningService service) {
        this.plugin = plugin;
        this.service = service;
    }

    static String bladeName(Playstyle style) {
        return Zanpakuto.of(style).blade;
    }

    static String description(Playstyle style) {
        Zanpakuto blade = Zanpakuto.of(style);
        return "\"" + blade.command + ", " + blade.blade + "!\" A timed release; right-click uses "
                + blade.technique + ": " + blade.techniqueDescription;
    }

    public boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    int durationSeconds() {
        return Math.clamp(plugin.getConfig().getInt("awakenings.shikai.duration-seconds", 30), 5, 300);
    }

    private double damage(double base) {
        return base * Math.clamp(plugin.getConfig().getDouble("awakenings.shikai.damage-multiplier", 1.0), 0.0, 5.0);
    }

    /** Releases the Shikai: the call-out, the reiatsu burst, and the timed released state. */
    void release(Player player, Playstyle style) {
        seal(player, false);
        Zanpakuto blade = Zanpakuto.of(style);
        long now = System.currentTimeMillis();
        int seconds = durationSeconds();
        BossBar bar = BossBar.bossBar(Component.text("Shikai — " + blade.blade, blade.textColor, TextDecoration.BOLD),
                1.0f, blade.barColor, BossBar.Overlay.NOTCHED_10);
        Release release = new Release(blade, now, now + seconds * 1000L, bar);
        active.put(player.getUniqueId(), release);

        Location loc = player.getLocation();
        Component callout = Component.text(player.getName() + ": ", NamedTextColor.GRAY)
                .append(Component.text("\"" + blade.command + ", " + blade.blade + "!\"", blade.textColor,
                        TextDecoration.ITALIC));
        for (Player nearby : loc.getNearbyPlayers(32)) {
            nearby.sendMessage(callout);
        }
        player.showTitle(Title.title(Component.text(blade.blade, blade.textColor, TextDecoration.BOLD),
                Component.text("Shikai", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1400), Duration.ofMillis(500))));
        player.showBossBar(bar);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0, false, false, true));

        var world = player.getWorld();
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        world.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.4f);
        world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1, 0), 1);

        // Spiritual pressure: push nearby enemies away without damaging them.
        for (LivingEntity entity : loc.getNearbyLivingEntities(5)) {
            if (service.canStrike(player, entity)) {
                Vector push = entity.getLocation().toVector().subtract(loc.toVector()).setY(0);
                if (push.lengthSquared() < 0.01) {
                    push = new Vector(0, 0, 1);
                }
                entity.setVelocity(push.normalize().multiply(0.9).setY(0.35));
            }
        }

        // A rising helix and an expanding shockwave ring over the first second.
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 20) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                for (int strand = 0; strand < 3; strand++) {
                    double angle = tick * 0.6 + strand * (Math.PI * 2 / 3);
                    double height = tick * 0.15;
                    base.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(angle) * 1.1, height,
                            Math.sin(angle) * 1.1), 2, 0.02, 0.02, 0.02, 0, blade.dust(1.6f));
                }
                if (tick <= 8) {
                    double radius = 0.8 + tick * 0.55;
                    for (int i = 0; i < 28; i++) {
                        double a = i * (Math.PI * 2 / 28);
                        base.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * radius, 0.15,
                                Math.sin(a) * radius), 1, 0, 0, 0, 0, blade.dust(1.3f));
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        release.task = new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                long time = System.currentTimeMillis();
                if (!player.isOnline() || player.isDead() || !AwakeningService.canPlay(player)
                        || time >= release.endsAt) {
                    seal(player, true);
                    return;
                }
                release.bar.progress((float) Math.clamp(
                        (release.endsAt - time) / (double) (release.endsAt - release.startedAt), 0.0, 1.0));
                Location base = player.getLocation();
                for (int i = 0; i < 2; i++) {
                    double angle = tick * 0.35 + i * Math.PI;
                    base.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(angle) * 0.75,
                            1.0 + Math.sin(tick * 0.2 + i) * 0.6, Math.sin(angle) * 0.75), 1, 0, 0, 0, 0,
                            blade.dust(0.9f));
                }
                if (tick % 10 == 0) {
                    base.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 1, 0), 3,
                            0.35, 0.5, 0.35, 0.02);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Ends a released Shikai. Returns whether one was active. */
    boolean seal(Player player, boolean announce) {
        Release release = active.remove(player.getUniqueId());
        if (release == null) {
            return false;
        }
        if (release.task != null) {
            release.task.cancel();
        }
        player.hideBossBar(release.bar);
        if (announce && player.isOnline()) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
            player.sendMessage(Component.text(release.blade.blade + " returns to its sealed form.", NamedTextColor.GRAY));
        }
        return true;
    }

    public void sealAll() {
        for (UUID id : Set.copyOf(active.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                seal(player, false);
            } else {
                Release release = active.remove(id);
                if (release.task != null) {
                    release.task.cancel();
                }
            }
        }
    }

    /**
     * Uses the released zanpakutō's technique. Called by the starter item's right-click ability while
     * Shikai is active, in place of the sealed ability.
     */
    public void useTechnique(Player player) {
        Release release = active.get(player.getUniqueId());
        if (release == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (release.techniqueReadyAt > now) {
            player.sendActionBar(Component.text(release.blade.technique + " ready in "
                    + ((release.techniqueReadyAt - now + 999) / 1000) + "s", NamedTextColor.YELLOW));
            return;
        }
        boolean used = switch (release.blade) {
            case VANGUARD -> crimsonFang(player, release.blade);
            case MIRAGE -> hazeStep(player, release.blade);
            case MARKSMAN -> fallingStar(player, release.blade);
            case WAYFARER -> galePassage(player, release.blade);
            case ARTIFICER -> mountainBreaker(player, release.blade);
            case KEEPER -> shrineWard(player, release.blade);
        };
        if (used) {
            int cooldown = Math.clamp(plugin.getConfig().getInt("awakenings.shikai.technique-cooldown-seconds", 6), 0, 600);
            release.techniqueReadyAt = now + cooldown * 1000L;
            player.sendActionBar(Component.text(release.blade.technique + "!", release.blade.textColor,
                    TextDecoration.BOLD));
        }
    }

    private boolean crimsonFang(Player player, Zanpakuto blade) {
        Location origin = player.getEyeLocation().subtract(0, 0.35, 0);
        Vector forward = origin.getDirection().normalize();
        Vector right = forward.getCrossProduct(new Vector(0, 1, 0));
        right = right.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : right.normalize();
        Vector side = right;
        Set<UUID> hit = new HashSet<>();
        player.getWorld().playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        player.getWorld().playSound(origin, Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.8f);
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                Location center = origin.clone().add(forward.clone().multiply(1.5 + step * 1.5));
                if (step >= 11 || center.getBlock().getType().isSolid()) {
                    center.getWorld().spawnParticle(Particle.DUST, center, 25, 0.6, 0.6, 0.6, 0, blade.dust(1.8f));
                    center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.7f);
                    cancel();
                    return;
                }
                for (int i = -8; i <= 8; i++) {
                    double t = i * Math.toRadians(9);
                    Location point = center.clone().add(side.clone().multiply(Math.sin(t) * 2.0))
                            .subtract(forward.clone().multiply((1 - Math.cos(t)) * 1.2));
                    center.getWorld().spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0,
                            blade.dust(Math.abs(i) < 5 ? 1.8f : 1.2f));
                }
                center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1);
                for (LivingEntity entity : center.getNearbyLivingEntities(2.1)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        entity.damage(damage(8.0), player);
                        entity.setVelocity(forward.clone().multiply(0.6).setY(0.3));
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean hazeStep(Player player, Zanpakuto blade) {
        var result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                12, 0.6, entity -> entity instanceof LivingEntity && !entity.equals(player));
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)
                || !service.canStrike(player, target)) {
            player.sendActionBar(Component.text("Haze Step needs an enemy within 12 blocks.", NamedTextColor.GRAY));
            return false;
        }
        Vector facing = target.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) {
            facing = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        }
        Location behind = target.getLocation().subtract(facing.normalize().multiply(1.4));
        if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) {
            player.sendActionBar(Component.text("No room behind your target.", NamedTextColor.GRAY));
            return false;
        }
        behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
        Location from = player.getLocation();
        from.getWorld().spawnParticle(Particle.DUST, from.clone().add(0, 1, 0), 40, 0.35, 0.8, 0.35, 0, blade.dust(1.5f));
        from.getWorld().spawnParticle(Particle.CLOUD, from.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
        from.getWorld().playSound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        player.teleport(behind);
        target.damage(damage(7.0), player);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2, 0.3, 0.2, 0.3, 0);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        return true;
    }

    private boolean fallingStar(Player player, Zanpakuto blade) {
        Location origin = player.getEyeLocation();
        Vector forward = origin.getDirection().normalize();
        player.getWorld().playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                for (int sub = 0; sub < 3; sub++) {
                    Location point = origin.clone().add(forward.clone().multiply(1 + step * 3 + sub));
                    point.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                    point.getWorld().spawnParticle(Particle.DUST, point, 2, 0.08, 0.08, 0.08, 0, blade.dust(1.4f));
                    LivingEntity struck = null;
                    for (LivingEntity entity : point.getNearbyLivingEntities(1.2)) {
                        if (!entity.equals(player)) {
                            struck = entity;
                            break;
                        }
                    }
                    if (struck != null || point.getBlock().getType().isSolid() || step >= 10) {
                        burst(point, struck);
                        cancel();
                        return;
                    }
                }
                step++;
            }

            private void burst(Location point, LivingEntity struck) {
                point.getWorld().spawnParticle(Particle.END_ROD, point, 40, 0.2, 0.2, 0.2, 0.25);
                point.getWorld().spawnParticle(Particle.DUST, point, 30, 0.9, 0.9, 0.9, 0, blade.dust(1.8f));
                point.getWorld().playSound(point, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
                if (struck != null && service.canStrike(player, struck)) {
                    struck.damage(damage(9.0), player);
                }
                for (LivingEntity entity : point.getNearbyLivingEntities(2.5)) {
                    if (!entity.equals(struck) && service.canStrike(player, entity)) {
                        entity.damage(damage(4.0), player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean galePassage(Player player, Zanpakuto blade) {
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.01) {
            forward = new Vector(0, 0, 1);
        }
        Vector dash = forward.normalize().multiply(2.1).setY(0.15);
        player.setVelocity(dash);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.0f);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick++ >= 9) {
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 1, 0);
                at.getWorld().spawnParticle(Particle.CLOUD, at, 4, 0.3, 0.4, 0.3, 0.02);
                at.getWorld().spawnParticle(Particle.DUST, at, 6, 0.5, 0.6, 0.5, 0, blade.dust(1.3f));
                for (LivingEntity entity : at.getNearbyLivingEntities(1.9)) {
                    if (hit.add(entity.getUniqueId()) && service.canStrike(player, entity)) {
                        entity.damage(damage(6.0), player);
                        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1, 0), 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private boolean mountainBreaker(Player player, Zanpakuto blade) {
        Location center = player.getLocation();
        Material ground = center.clone().subtract(0, 1, 0).getBlock().getType();
        BlockData debris = (ground.isSolid() ? ground : Material.STONE).createBlockData();
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.6f);
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        for (LivingEntity entity : center.getNearbyLivingEntities(5)) {
            if (service.canStrike(player, entity)) {
                entity.damage(damage(6.0), player);
                Vector out = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
                out = out.lengthSquared() < 0.01 ? new Vector() : out.normalize().multiply(0.4);
                entity.setVelocity(out.setY(0.95));
            }
        }
        new BukkitRunnable() {
            int ring;

            @Override
            public void run() {
                if (ring > 6) {
                    cancel();
                    return;
                }
                double radius = 0.6 + ring * 0.75;
                for (int i = 0; i < 24; i++) {
                    double a = i * (Math.PI * 2 / 24);
                    Location point = center.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius);
                    point.getWorld().spawnParticle(Particle.BLOCK, point, 4, 0.1, 0.2, 0.1, 0.1, debris);
                    if (i % 3 == 0) {
                        point.getWorld().spawnParticle(Particle.DUST, point.add(0, 0.4, 0), 1, 0, 0, 0, 0,
                                blade.dust(1.5f));
                    }
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean shrineWard(Player player, Zanpakuto blade) {
        Location center = player.getLocation();
        for (Player ally : center.getNearbyPlayers(6)) {
            if (ally.equals(player) || (AwakeningService.canPlay(ally) && AwakeningPowers.allies(player, ally))) {
                var maxHealth = ally.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                double max = maxHealth == null ? 20.0 : maxHealth.getValue();
                ally.setHealth(Math.min(max, ally.getHealth() + 4.0));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 0));
                ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 2, 0.3, 0.1, 0.3, 0);
            }
        }
        for (LivingEntity entity : center.getNearbyLivingEntities(4)) {
            if (service.canStrike(player, entity)) {
                entity.damage(damage(4.0), player);
                Vector out = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
                out = out.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : out.normalize();
                entity.setVelocity(out.multiply(1.1).setY(0.3));
            }
        }
        center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f);
        center.getWorld().playSound(center, Sound.BLOCK_BELL_USE, 0.7f, 1.5f);
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 36; i++) {
                double a = i * (Math.PI * 2 / 36);
                center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * 4, 0.3 + layer * 0.8,
                        Math.sin(a) * 4), 1, 0, 0, 0, 0, blade.dust(1.3f));
            }
        }
        center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.05);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        seal(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        seal(event.getEntity(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        seal(event.getPlayer(), true);
    }
}
