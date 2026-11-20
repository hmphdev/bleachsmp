package dev.bleachsmp.hueco;

import dev.bleachsmp.BleachSmpPlugin;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * The Vasto Lorde: the apex Hollow. At most one exists at a time. It stirs near a player deep
 * in the desert, announces itself to the whole server, and fights with telegraphed Cero,
 * Sonído and spiritual pressure, then releases its Resurrección at half health.
 */
final class VastoLordeBoss implements Listener {
    static final String TYPE = "vasto_lorde";
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(176, 18, 34), 1.4f);
    private static final Particle.DustOptions DARK = new Particle.DustOptions(Color.fromRGB(40, 6, 12), 1.8f);
    private static final Particle.DustOptions AIM = new Particle.DustOptions(Color.fromRGB(220, 40, 50), 0.6f);
    private static final int ARENA_LEASH = 56;
    private static final int TARGET_RANGE = 40;

    private final BleachSmpPlugin plugin;
    private final HuecoMundoService service;
    private final File stateFile;
    private final NamespacedKey trophyKey;
    private long cooldownUntil;

    private LivingEntity boss;
    private BossBar bar;
    private final Set<UUID> viewers = new HashSet<>();
    private BukkitTask task;
    private BukkitTask omen;
    private Location home;
    private int tick;
    private int nextAbilityAt;
    private int castingUntil;
    private int lastGranRey = -10_000;
    private int idleTicks;
    private boolean resurrected;
    private int summonWaves;

    VastoLordeBoss(BleachSmpPlugin plugin, HuecoMundoService service) {
        this.plugin = plugin;
        this.service = service;
        this.stateFile = new File(plugin.getDataFolder(), "vasto-lorde.yml");
        this.trophyKey = new NamespacedKey(plugin, "vasto_trophy");
        cooldownUntil = YamlConfiguration.loadConfiguration(stateFile).getLong("cooldown-until", 0L);
    }

    // ---- Spawning ----

    /** Called on the Hollow spawn cycle. Rarely, the desert notices someone who has wandered far enough. */
    void consider(World world) {
        if (!setting("enabled", true) || boss != null || omen != null
                || System.currentTimeMillis() < cooldownUntil) {
            return;
        }
        int minDistance = Math.max(0, plugin.getConfig().getInt("hueco-mundo.vasto-lorde.min-distance-from-gate", 250));
        List<Player> candidates = world.getPlayers().stream().filter(this::fighter)
                .filter(player -> Math.hypot(player.getX() - HuecoMundoLayout.GATE.x(),
                        player.getZ() - HuecoMundoLayout.GATE.z()) >= minDistance)
                .filter(player -> HuecoTerrain.landmarkGap(player.getLocation().getBlockX(),
                        player.getLocation().getBlockZ()) > 40)
                .toList();
        double chance = plugin.getConfig().getDouble("hueco-mundo.vasto-lorde.spawn-chance", 0.02);
        if (!candidates.isEmpty() && ThreadLocalRandom.current().nextDouble() < chance) {
            beginOmen(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
        }
    }

    /** Admin summon for testing; skips the omen and the cooldown. */
    boolean summon(Player player) {
        if (boss != null || omen != null || player.getWorld() != service.world()) {
            return false;
        }
        Location location = service.findBossLocation(player.getLocation());
        if (location == null) {
            return false;
        }
        awaken(location);
        return true;
    }

    private void beginOmen(Player chosen) {
        World world = chosen.getWorld();
        UUID chosenId = chosen.getUniqueId();
        omen = new org.bukkit.scheduler.BukkitRunnable() {
            int seconds;
            int attempts;

            @Override
            public void run() {
                Player player = Bukkit.getPlayer(chosenId);
                if (player == null || player.getWorld() != world || !fighter(player)) {
                    cancelOmen();
                    return;
                }
                Location at = player.getLocation();
                for (Player nearby : world.getPlayers()) {
                    if (nearby.getLocation().distanceSquared(at) > 64 * 64) {
                        continue;
                    }
                    nearby.playSound(nearby.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.5f);
                    nearby.spawnParticle(Particle.WHITE_ASH, nearby.getLocation().add(0, 1, 0), 60, 6, 2, 6, 0.02);
                    Component line = switch (seconds) {
                        case 0 -> Component.text("The wind across the dunes stops.", NamedTextColor.GRAY);
                        case 3 -> Component.text("The sand begins to tremble.", NamedTextColor.GRAY);
                        case 6 -> Component.text("Something ancient has noticed you.", NamedTextColor.DARK_RED);
                        default -> null;
                    };
                    if (line != null) {
                        nearby.sendMessage(line.decorate(TextDecoration.ITALIC));
                    }
                    if (seconds == 6) {
                        nearby.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, true, false, false));
                        nearby.playSound(nearby.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.6f);
                    }
                }
                if (seconds++ < 8) {
                    return;
                }
                Location spawn = service.findBossLocation(at);
                if (spawn != null) {
                    omen = null;
                    cancel();
                    awaken(spawn);
                } else if (++attempts > 5) {
                    cancelOmen();
                    cooldownUntil = System.currentTimeMillis() + 5 * 60_000L;
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelOmen() {
        if (omen != null) {
            omen.cancel();
            omen = null;
        }
    }

    private void awaken(Location location) {
        World world = location.getWorld();
        int nearby = (int) world.getPlayers().stream().filter(this::fighter)
                .filter(player -> player.getLocation().distanceSquared(location) < 64 * 64).count();
        double health = Math.min(1024, plugin.getConfig().getDouble("hueco-mundo.vasto-lorde.health", 600)
                + Math.max(0, nearby - 1) * plugin.getConfig().getDouble("hueco-mundo.vasto-lorde.health-per-extra-player", 150));

        boss = service.spawnHollow(location, TYPE);
        attribute(Attribute.MAX_HEALTH, health);
        attribute(Attribute.ATTACK_DAMAGE, plugin.getConfig().getDouble("hueco-mundo.vasto-lorde.damage", 14));
        attribute(Attribute.ARMOR, 14);
        attribute(Attribute.ARMOR_TOUGHNESS, 8);
        attribute(Attribute.KNOCKBACK_RESISTANCE, 1);
        attribute(Attribute.MOVEMENT_SPEED, 0.3);
        attribute(Attribute.FOLLOW_RANGE, 48);
        boss.setHealth(health);
        boss.setPersistent(false);
        boss.setRemoveWhenFarAway(false);
        boss.customName(Component.text("Vasto Lorde", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        home = location.clone();
        tick = 0;
        nextAbilityAt = 60;
        castingUntil = 0;
        idleTicks = 0;
        resurrected = false;
        summonWaves = 0;
        lastGranRey = -10_000;
        bar = BossBar.bossBar(Component.text("Vasto Lorde", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);

        world.spawnParticle(Particle.EXPLOSION_EMITTER, location.clone().add(0, 1, 0), 2);
        world.spawnParticle(Particle.DUST, location.clone().add(0, 1.5, 0), 200, 3, 2, 3, 0, DARK);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) < 220 * 220) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.9f, 0.6f);
            }
        }
        Bukkit.broadcast(Component.text("☠ A Vasto Lorde has awakened in Hueco Mundo", NamedTextColor.DARK_RED,
                TextDecoration.BOLD));
        Component where = Component.text("Its reiatsu flares near " + round(location.getBlockX()) + ", "
                + round(location.getBlockZ()) + ".", NamedTextColor.RED);
        world.getPlayers().forEach(player -> player.sendMessage(where));
        plugin.getLogger().info("A Vasto Lorde awakened at " + location.getBlockX() + ", " + location.getBlockY()
                + ", " + location.getBlockZ() + " with " + (int) health + " health.");

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    // ---- The fight ----

    private void tick() {
        if (boss == null) {
            return;
        }
        if (!boss.isValid()) {
            if (!boss.isDead()) {
                // Its chunk unloaded, or it was removed some other way.
                end("The Vasto Lorde sinks back beneath the sand.", cooldownMillis() / 2);
            }
            return;
        }
        tick++;
        double maxHealth = maxHealth();
        bar.progress((float) Math.max(0, Math.min(1, boss.getHealth() / maxHealth)));
        if (tick % 10 == 0) {
            updateViewers();
        }
        if (tick % 4 == 0) {
            Location body = boss.getLocation().add(0, 1.6, 0);
            boss.getWorld().spawnParticle(Particle.DUST, body, resurrected ? 10 : 5, 0.6, 1.0, 0.6, 0,
                    resurrected ? RED : DARK);
            if (resurrected) {
                boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, body, 2, 0.5, 0.9, 0.5, 0.01);
            }
        }
        if (tick % 100 == 0) {
            removeStrays();
        }

        Player target = target();
        if (boss instanceof Mob mob && castingUntil <= tick) {
            mob.setTarget(target);
        }
        if (target == null) {
            if (++idleTicks > 200 && tick % 20 == 0) {
                boss.setHealth(Math.min(maxHealth, boss.getHealth() + maxHealth * 0.02));
            }
            if (idleTicks > 6000) {
                end("The Vasto Lorde loses interest and sinks back beneath the sand.", cooldownMillis() / 2);
            }
            return;
        }
        idleTicks = 0;

        if (boss.getLocation().distanceSquared(home) > ARENA_LEASH * ARENA_LEASH) {
            sonidoTo(home.clone());
            boss.setHealth(Math.min(maxHealth, boss.getHealth() + maxHealth * 0.05));
        }
        if (tick % 40 == 0) {
            pressure();
        }

        double fraction = boss.getHealth() / maxHealth;
        if (!resurrected && fraction <= 0.5 && castingUntil <= tick) {
            resurreccion();
            return;
        }
        if (summonWaves == 0 && fraction <= 0.7 || summonWaves == 1 && fraction <= 0.35) {
            summonWaves++;
            summonAdjuchas();
        }
        if (tick >= nextAbilityAt && castingUntil <= tick) {
            useAbility(target);
        }
    }

    private void useAbility(Player target) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double distance = boss.getLocation().distance(target.getLocation());
        boolean outOfReach = target.getLocation().getY() > boss.getLocation().getY() + 4 || distance > 14;
        int roll = random.nextInt(100);
        if (resurrected && tick - lastGranRey > 500 && roll < 30) {
            granReyCero(target);
        } else if (outOfReach || roll < 40) {
            cero(target);
        } else if (distance < 9 && roll < 70) {
            crush();
        } else {
            sonido(target);
        }
        nextAbilityAt = castingUntil + (resurrected ? 30 + random.nextInt(30) : 50 + random.nextInt(40));
    }

    /** A red beam: it charges, locks its aim and shows the line, then fires. Move off the line to dodge. */
    private void cero(Player target) {
        int charge = resurrected ? 22 : 30;
        castingUntil = tick + charge + 4;
        hold();
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 1.8f);
        Vector[] aim = new Vector[1];
        repeat(charge + 1, 1, step -> {
            Location mouth = mouth();
            boss.getWorld().spawnParticle(Particle.DUST, mouth, 6, 0.25, 0.25, 0.25, 0, RED);
            if (step == charge - 12) {
                aim[0] = target.getEyeLocation().toVector().subtract(mouth.toVector()).normalize();
                boss.getWorld().playSound(mouth, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 1.6f);
            }
            if (aim[0] != null && step % 2 == 0) {
                face(aim[0]);
                for (double d = 1; d < 34; d += 1.5) {
                    boss.getWorld().spawnParticle(Particle.DUST, mouth.clone().add(aim[0].clone().multiply(d)),
                            1, 0, 0, 0, 0, AIM);
                }
            }
            if (step == charge && aim[0] != null) {
                double damage = resurrected ? 20 : 16;
                beam(mouth, aim[0], damage);
                if (resurrected) {
                    beam(mouth, aim[0].clone().rotateAroundY(Math.toRadians(14)), damage * 0.7);
                    beam(mouth, aim[0].clone().rotateAroundY(Math.toRadians(-14)), damage * 0.7);
                }
            }
        });
    }

    private void beam(Location from, Vector direction, double damage) {
        World world = from.getWorld();
        world.playSound(from, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.6f);
        Set<UUID> struck = new HashSet<>();
        Location point = from.clone();
        for (double d = 0; d < 36; d += 0.5) {
            point = from.clone().add(direction.clone().multiply(d));
            if (point.getBlock().getType().isSolid()) {
                break;
            }
            world.spawnParticle(Particle.DUST, point, 3, 0.15, 0.15, 0.15, 0, RED);
            if ((int) (d * 2) % 3 == 0) {
                world.spawnParticle(Particle.DUST, point, 1, 0.3, 0.3, 0.3, 0, DARK);
            }
            for (Player player : world.getPlayers()) {
                if (fighter(player) && !struck.contains(player.getUniqueId())
                        && player.getBoundingBox().expand(0.6).contains(point.toVector())) {
                    struck.add(player.getUniqueId());
                    player.damage(damage, boss);
                    player.setVelocity(direction.clone().multiply(0.9).setY(0.3));
                }
            }
        }
        world.spawnParticle(Particle.EXPLOSION, point, 2);
    }

    /** Vanishes and reappears behind the target, then strikes a moment later. */
    private void sonido(Player target) {
        castingUntil = tick + 10;
        Vector behind = target.getLocation().getDirection().setY(0);
        if (behind.lengthSquared() < 0.01) {
            behind = new Vector(0, 0, 1);
        }
        Location destination = target.getLocation().subtract(behind.normalize().multiply(2.4));
        if (!passable(destination)) {
            destination = target.getLocation().add(behind.multiply(2.4));
        }
        if (!passable(destination)) {
            cero(target);
            return;
        }
        sonidoTo(destination);
        boss.teleport(boss.getLocation().setDirection(target.getLocation().toVector()
                .subtract(boss.getLocation().toVector())));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (boss == null || !boss.isValid() || !target.isValid()) {
                return;
            }
            boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(0, 1.4, 0), 3, 0.8, 0.3, 0.8);
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
            if (target.getLocation().distanceSquared(boss.getLocation()) < 3.5 * 3.5) {
                target.damage(resurrected ? 16 : 12, boss);
                target.setVelocity(target.getLocation().toVector().subtract(boss.getLocation().toVector())
                        .setY(0).normalize().multiply(1.1).setY(0.35));
            }
        }, 7L);
    }

    private void sonidoTo(Location destination) {
        World world = boss.getWorld();
        world.spawnParticle(Particle.DUST, boss.getLocation().add(0, 1.4, 0), 40, 0.5, 1.0, 0.5, 0, DARK);
        world.playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 0.5f);
        destination.setDirection(boss.getLocation().getDirection());
        boss.teleport(destination);
        world.spawnParticle(Particle.DUST, destination.clone().add(0, 1.4, 0), 40, 0.5, 1.0, 0.5, 0, DARK);
    }

    /** It rises, a ring marks the blast, and it slams down. Get out of the ring. */
    private void crush() {
        castingUntil = tick + 32;
        hold();
        Location center = boss.getLocation();
        double radius = resurrected ? 11 : 9;
        boss.getWorld().playSound(center, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.5f);
        repeat(31, 1, step -> {
            if (step % 4 == 0) {
                for (int i = 0; i < 48; i++) {
                    double angle = Math.PI * 2 * i / 48;
                    boss.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * radius, 0.2,
                            Math.sin(angle) * radius), 1, 0, 0, 0, 0, RED);
                }
            }
            if (step == 18) {
                boss.setVelocity(new Vector(0, 0.9, 0));
            }
            if (step == 30) {
                Location impact = boss.getLocation();
                boss.setVelocity(new Vector(0, -2.5, 0));
                World world = impact.getWorld();
                world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
                world.spawnParticle(Particle.DUST, center.clone().add(0, 0.5, 0), 160, radius / 2, 0.4, radius / 2, 0, DARK);
                world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                for (Player player : world.getPlayers()) {
                    double distance = player.getLocation().distance(center);
                    if (fighter(player) && distance <= radius && Math.abs(player.getY() - center.getY()) < 5) {
                        player.damage((resurrected ? 18 : 14) * (1 - distance / (radius + 4)), boss);
                        player.setVelocity(player.getLocation().toVector().subtract(center.toVector()).setY(0)
                                .normalize().multiply(1.2).setY(0.8));
                    }
                }
            }
        });
    }

    /** Resurrección only. A long charge, then a slow sphere that detonates where the target was standing. */
    private void granReyCero(Player target) {
        lastGranRey = tick;
        castingUntil = tick + 70;
        hold();
        World world = boss.getWorld();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(boss.getLocation()) < 56 * 56) {
                player.showTitle(Title.title(Component.text("Gran Rey Cero", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Component.text("Get away from where you're standing", NamedTextColor.RED),
                        Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1800),
                                java.time.Duration.ofMillis(300))));
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.4f);
            }
        }
        Location[] lock = new Location[1];
        Location[] orb = new Location[1];
        repeat(70, 1, step -> {
            Location mouth = mouth();
            if (step < 45) {
                double size = 0.3 + step / 45.0 * 1.6;
                world.spawnParticle(Particle.DUST, mouth, 12, size, size, size, 0, step % 2 == 0 ? RED : DARK);
                if (step % 10 == 0) {
                    world.playSound(mouth, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.5f + step / 60f);
                }
            }
            if (step == 32) {
                lock[0] = target.getLocation().clone();
            }
            if (lock[0] != null && step < 45 && step % 3 == 0) {
                for (int i = 0; i < 32; i++) {
                    double angle = Math.PI * 2 * i / 32;
                    world.spawnParticle(Particle.DUST, lock[0].clone().add(Math.cos(angle) * 7, 0.2, Math.sin(angle) * 7),
                            1, 0, 0, 0, 0, RED);
                }
            }
            if (step == 45 && lock[0] != null) {
                orb[0] = mouth.clone();
                world.playSound(mouth, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);
            }
            if (orb[0] != null) {
                Vector toward = lock[0].toVector().add(new Vector(0, 1, 0)).subtract(orb[0].toVector());
                boolean arrived = toward.length() < 1.6 || orb[0].getBlock().getType().isSolid() || step == 69;
                if (!arrived) {
                    orb[0].add(toward.normalize().multiply(1.6));
                    world.spawnParticle(Particle.DUST, orb[0], 30, 0.8, 0.8, 0.8, 0, DARK);
                    world.spawnParticle(Particle.DUST, orb[0], 15, 0.5, 0.5, 0.5, 0, RED);
                } else {
                    detonate(orb[0]);
                    orb[0] = null;
                    lock[0] = null;
                }
            }
        });
    }

    private void detonate(Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 3, 2, 1, 2);
        world.spawnParticle(Particle.DUST, at, 300, 4, 2, 4, 0, DARK);
        world.spawnParticle(Particle.DUST, at, 200, 3, 1.5, 3, 0, RED);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.4f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.4f);
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distance(at);
            if (fighter(player) && distance <= 7) {
                player.damage(30 * (1 - distance / 9), boss);
                player.setVelocity(player.getLocation().toVector().subtract(at.toVector()).normalize().multiply(1.4)
                        .setY(0.9));
            }
        }
    }

    /** Spiritual pressure: standing near it weighs you down, more so after its release. */
    private void pressure() {
        double radius = resurrected ? 24 : 16;
        for (Player player : boss.getWorld().getPlayers()) {
            if (fighter(player) && player.getLocation().distanceSquared(boss.getLocation()) < radius * radius) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, true, false, true));
                if (resurrected) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 50, 0, true, false, true));
                }
            }
        }
    }

    private void resurreccion() {
        resurrected = true;
        castingUntil = tick + 60;
        hold();
        boss.setInvulnerable(true);
        World world = boss.getWorld();
        Component announce = Component.text("The Vasto Lorde releases its Resurrección!", NamedTextColor.DARK_RED,
                TextDecoration.BOLD);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(boss.getLocation()) < 96 * 96) {
                player.sendMessage(announce);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.5f);
            }
        }
        repeat(61, 1, step -> {
            Location body = boss.getLocation().add(0, 1.5, 0);
            double spin = step * 0.35;
            for (int arm = 0; arm < 3; arm++) {
                double angle = spin + arm * Math.PI * 2 / 3;
                double r = 3.5 - step / 61.0 * 2.5;
                world.spawnParticle(Particle.DUST, body.clone().add(Math.cos(angle) * r, (step % 20) / 10.0 - 1,
                        Math.sin(angle) * r), 4, 0.1, 0.1, 0.1, 0, step % 2 == 0 ? RED : DARK);
            }
            if (step == 60) {
                boss.setInvulnerable(false);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, body, 2);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, body, 120, 1, 1.5, 1, 0.2);
                world.playSound(body, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.4f);
                attribute(Attribute.MOVEMENT_SPEED, 0.36);
                attribute(Attribute.ATTACK_DAMAGE, plugin.getConfig().getDouble("hueco-mundo.vasto-lorde.damage", 14) * 1.4);
                bar.name(Component.text("Vasto Lorde — Resurrección", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                bar.overlay(BossBar.Overlay.NOTCHED_20);
                for (Player player : world.getPlayers()) {
                    double distance = player.getLocation().distance(body);
                    if (fighter(player) && distance < 9) {
                        player.damage(8, boss);
                        player.setVelocity(player.getLocation().toVector().subtract(body.toVector()).setY(0)
                                .normalize().multiply(1.5).setY(0.6));
                    }
                }
            }
        });
    }

    private void summonAdjuchas() {
        World world = boss.getWorld();
        world.playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.6f, 0.5f);
        for (int i = 0; i < 2; i++) {
            Location spot = service.findBossLocation(boss.getLocation());
            if (spot != null) {
                world.spawnParticle(Particle.DUST, spot.clone().add(0, 1, 0), 40, 0.5, 1, 0.5, 0, DARK);
                service.spawnHollow(spot, "adjuchas");
            }
        }
    }

    // ---- Damage rules ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageEvent event) {
        if (boss == null || !event.getEntity().equals(boss)) {
            return;
        }
        if (boss.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }
        // Hierro: the environment can't wear it down, so players have to fight it.
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            if (event.getCause() != EntityDamageEvent.DamageCause.KILL
                    && event.getCause() != EntityDamageEvent.DamageCause.WORLD_BORDER) {
                event.setCancelled(true);
            }
            return;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile) {
            if (!(projectile.getShooter() instanceof Player)) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(event.getDamage() * 0.6);
        } else if (!(damager instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDeath(EntityDeathEvent event) {
        if (boss == null || !event.getEntity().equals(boss)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        double threshold = maxHealth() * 0.05;
        var contributions = boss.getPersistentDataContainer().get(plugin.bossDamageKey(), PersistentDataType.TAG_CONTAINER);
        List<Player> slayers = new ArrayList<>();
        if (contributions != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                NamespacedKey key = new NamespacedKey(plugin, player.getUniqueId().toString());
                if (contributions.getOrDefault(key, PersistentDataType.DOUBLE, 0.0) >= threshold) {
                    slayers.add(player);
                }
            }
        }
        List<String> names = slayers.stream().map(Player::getName).toList();
        for (Player player : slayers) {
            player.giveExp(1400);
            var leftover = player.getInventory().addItem(trophy(names));
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            plugin.awakenings().bankai().recordBossVictory(player);
        }
        Location at = boss.getLocation();
        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 1, 0), 3, 1, 1, 1);
        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1.5, 0), 300, 2, 2, 2, 0, DARK);
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_DEATH, 2.0f, 0.6f);
        end(names.isEmpty() ? "The Vasto Lorde has fallen."
                : "The Vasto Lorde has been slain by " + String.join(", ", names) + ".", cooldownMillis());
    }

    private ItemStack trophy(List<String> names) {
        ItemStack mask = new ItemStack(Material.SKELETON_SKULL);
        mask.editMeta(meta -> {
            meta.displayName(Component.text("Vasto Lorde Mask", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Torn from the apex Hollow of Hueco Mundo.", NamedTextColor.GRAY),
                    Component.text("Slain " + LocalDate.now() + " by " + String.join(", ", names), NamedTextColor.DARK_GRAY)));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(trophyKey, PersistentDataType.BYTE, (byte) 1);
        });
        return mask;
    }

    // ---- Lifecycle ----

    private void end(String message, long cooldown) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            for (UUID id : viewers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.hideBossBar(bar);
                }
            }
        }
        viewers.clear();
        bar = null;
        if (boss != null && boss.isValid() && !boss.isDead()) {
            boss.remove();
        }
        boss = null;
        cooldownUntil = System.currentTimeMillis() + cooldown;
        saveState();
        if (message != null) {
            Bukkit.broadcast(Component.text(message, NamedTextColor.DARK_RED));
        }
    }

    void shutdown() {
        cancelOmen();
        if (boss != null) {
            end(null, cooldownMillis() / 2);
        }
    }

    private void saveState() {
        var data = new YamlConfiguration();
        data.set("cooldown-until", cooldownUntil);
        try {
            data.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save the Vasto Lorde cooldown: " + exception.getMessage());
        }
    }

    /** Vasto Lordes from older saves, or extra copies, are not part of the encounter. */
    private void removeStrays() {
        for (LivingEntity entity : boss.getWorld().getLivingEntities()) {
            if (!entity.equals(boss) && TYPE.equals(entity.getPersistentDataContainer()
                    .get(service.hollowTypeKey(), PersistentDataType.STRING))) {
                entity.remove();
            }
        }
    }

    private void updateViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean near = player.getWorld() == boss.getWorld()
                    && player.getLocation().distanceSquared(boss.getLocation()) < 72 * 72;
            if (near && viewers.add(player.getUniqueId())) {
                player.showBossBar(bar);
            } else if (!near && viewers.remove(player.getUniqueId())) {
                player.hideBossBar(bar);
            }
        }
    }

    // ---- Helpers ----

    private Player target() {
        Player best = null;
        double bestDistance = TARGET_RANGE * TARGET_RANGE;
        for (Player player : boss.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (fighter(player) && distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean fighter(Player player) {
        return player.isValid() && !player.isDead()
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    private void hold() {
        if (boss instanceof Mob mob) {
            mob.setTarget(null);
            mob.getPathfinder().stopPathfinding();
        }
    }

    private void face(Vector direction) {
        Location location = boss.getLocation();
        location.setDirection(direction);
        boss.setRotation(location.getYaw(), location.getPitch());
    }

    private Location mouth() {
        return boss.getEyeLocation().add(boss.getLocation().getDirection().multiply(0.8));
    }

    private boolean passable(Location location) {
        return location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable()
                && location.clone().add(0, 2, 0).getBlock().isPassable()
                && location.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }

    private void repeat(int times, long period, java.util.function.IntConsumer body) {
        LivingEntity owner = boss;
        new org.bukkit.scheduler.BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (boss != owner || !owner.isValid()) {
                    cancel();
                    return;
                }
                body.accept(step);
                if (++step >= times) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    private void attribute(Attribute attribute, double value) {
        var instance = boss.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private double maxHealth() {
        var instance = boss.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 600 : instance.getValue();
    }

    private long cooldownMillis() {
        return Math.max(1, plugin.getConfig().getLong("hueco-mundo.vasto-lorde.cooldown-minutes", 90)) * 60_000L;
    }

    private boolean setting(String key, boolean fallback) {
        return plugin.getConfig().getBoolean("hueco-mundo.vasto-lorde." + key, fallback);
    }

    private static int round(int value) {
        return Math.round(value / 50f) * 50;
    }
}
