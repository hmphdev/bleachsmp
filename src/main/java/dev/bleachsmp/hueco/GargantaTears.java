package dev.bleachsmp.hueco;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Garganta tears: Hueco Mundo breaking into the overworld. A rift opens in the sky near a
 * player, the server is told where, and three waves of Hollows pour out, ending with a Menos
 * Grande. Sealing it drops a loot pile at the rift that everyone nearby can fight over.
 */
final class GargantaTears implements Listener {
    private enum Phase { OPENING, WAVES, BREATHER }

    private record Loot(Material material, int min, int max, int weight) {
    }

    private static final List<Loot> LOOT = List.of(
            new Loot(Material.DIAMOND, 2, 5, 16),
            new Loot(Material.EMERALD, 4, 10, 14),
            new Loot(Material.GOLDEN_APPLE, 1, 2, 12),
            new Loot(Material.ENDER_PEARL, 3, 8, 12),
            new Loot(Material.EXPERIENCE_BOTTLE, 8, 16, 12),
            new Loot(Material.ECHO_SHARD, 1, 2, 9),
            new Loot(Material.GHAST_TEAR, 1, 2, 9),
            new Loot(Material.TOTEM_OF_UNDYING, 1, 1, 3),
            new Loot(Material.NETHERITE_SCRAP, 1, 2, 4),
            new Loot(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 1));
    private static final Particle.DustOptions RIFT = new Particle.DustOptions(Color.fromRGB(12, 8, 18), 2.2f);
    private static final Particle.DustOptions CERO = new Particle.DustOptions(Color.fromRGB(196, 30, 46), 1.5f);
    private static final Particle.DustOptions AIM = new Particle.DustOptions(Color.fromRGB(220, 40, 50), 0.6f);
    private static final int RIFT_HEIGHT = 16;
    private static final int LEASH = 40;
    private static final int WAVES = 3;

    private final BleachSmpPlugin plugin;
    private final HuecoMundoService service;
    private long nextTearAt;
    private Tear tear;

    GargantaTears(BleachSmpPlugin plugin, HuecoMundoService service) {
        this.plugin = plugin;
        this.service = service;
        scheduleNext();
        Bukkit.getScheduler().runTaskTimer(plugin, this::consider, 600L, 600L);
    }

    // ---- Scheduling ----

    private void consider() {
        if (tear != null || !setting("enabled", true) || System.currentTimeMillis() < nextTearAt) {
            return;
        }
        int minimum = Math.max(1, plugin.getConfig().getInt("tears.min-players", 2));
        List<Player> candidates = new ArrayList<>();
        for (World world : tearWorlds()) {
            world.getPlayers().stream().filter(GargantaTears::fighter).forEach(candidates::add);
        }
        if (candidates.size() < minimum) {
            return;
        }
        Player chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Location site = findSite(chosen.getLocation(), 50, 110);
        if (site != null) {
            open(site, 60);
        } else {
            nextTearAt = System.currentTimeMillis() + 60_000L;
        }
    }

    private void scheduleNext() {
        int min = Math.max(1, plugin.getConfig().getInt("tears.min-interval-minutes", 45));
        int max = Math.max(min, plugin.getConfig().getInt("tears.max-interval-minutes", 90));
        nextTearAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(min, max + 1L) * 60_000L;
    }

    private List<World> tearWorlds() {
        List<String> names = plugin.getConfig().getStringList("tears.worlds");
        if (names.isEmpty()) {
            return List.of(Bukkit.getWorlds().get(0));
        }
        return names.stream().map(Bukkit::getWorld).filter(world -> world != null && world != service.world()).toList();
    }

    /** Admin test: opens a tear near the player after a short countdown. */
    boolean summon(Player player) {
        if (tear != null || player.getWorld() == service.world()) {
            return false;
        }
        Location site = findSite(player.getLocation(), 12, 24);
        if (site == null) {
            return false;
        }
        open(site, 10);
        return true;
    }

    private Location findSite(Location around, int minDistance, int maxDistance) {
        World world = around.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            int distance = random.nextInt(minDistance, maxDistance + 1);
            int x = around.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = around.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            var ground = world.getBlockAt(x, y, z);
            // Open sky, solid dry ground: no tears inside caves, under trees or over the sea.
            if (!ground.getType().isSolid() || world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) > y
                    || y + RIFT_HEIGHT + 6 >= world.getMaxHeight()) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    // ---- The tear ----

    private void open(Location center, int countdownSeconds) {
        tear = new Tear(center, countdownSeconds);
        String where = round(center.getBlockX()) + ", " + round(center.getBlockZ());
        Bukkit.broadcast(Component.text("☠ A Garganta is tearing open near " + where + " in "
                + center.getWorld().getName() + ".", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("Hollows will pour through in " + countdownSeconds
                + " seconds. Seal it for the spoils.", NamedTextColor.RED));
        plugin.getLogger().info("Garganta tear opening at " + center.getBlockX() + ", " + center.getBlockY() + ", "
                + center.getBlockZ() + " in " + center.getWorld().getName() + ".");
    }

    void shutdown() {
        if (tear != null) {
            tear.collapse(null);
        }
    }

    private final class Tear {
        private final Location center;
        private final Location rift;
        private final World world;
        private final BossBar bar;
        private final Set<UUID> viewers = new HashSet<>();
        private final List<BlockDisplay> pieces = new ArrayList<>();
        private final Set<UUID> mobs = new HashSet<>();
        private final Map<UUID, Double> damage = new HashMap<>();
        private final Map<UUID, Integer> arrowKills = new HashMap<>();
        private final int countdown;
        private final BukkitTask task;
        private final long openedAt = System.currentTimeMillis();
        private Phase phase = Phase.OPENING;
        private int tick;
        private int wave;
        private int waveSize;
        private int phaseUntil;
        private int lonelyTicks;
        private LivingEntity menos;
        private int menosCeroAt;

        private Tear(Location center, int countdownSeconds) {
            this.center = center;
            this.world = center.getWorld();
            this.rift = center.clone().add(0, RIFT_HEIGHT, 0);
            this.countdown = countdownSeconds * 20;
            this.phaseUntil = countdown;
            this.bar = BossBar.bossBar(Component.text("Garganta — opening", NamedTextColor.DARK_RED),
                    0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            buildRift();
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }

        private void tick() {
            tick++;
            if (tick % 10 == 0) {
                updateViewers();
            }
            if (tick % 3 == 0) {
                riftParticles();
            }
            if (tick % 20 == 0 && !checkAlive()) {
                return;
            }
            switch (phase) {
                case OPENING -> {
                    bar.progress(Math.min(1f, tick / (float) countdown));
                    if (tick % 20 == 0) {
                        growRift(tick / (float) countdown);
                        world.playSound(rift, Sound.BLOCK_PORTAL_AMBIENT, 4f, 0.5f);
                    }
                    if (countdown - tick == 200) {
                        nearby(240).forEach(player -> player.sendMessage(Component.text(
                                "The Garganta is almost open…", NamedTextColor.RED, TextDecoration.ITALIC)));
                    }
                    if (tick >= phaseUntil) {
                        world.playSound(rift, Sound.ENTITY_WITHER_SPAWN, 3f, 0.7f);
                        growRift(1);
                        startWave();
                    }
                }
                case WAVES -> {
                    mobs.removeIf(id -> {
                        Entity entity = Bukkit.getEntity(id);
                        return entity == null || !entity.isValid() || entity.isDead();
                    });
                    bar.progress(waveSize == 0 ? 0 : Math.min(1f, mobs.size() / (float) waveSize));
                    if (tick % 40 == 0) {
                        leash();
                    }
                    if (menos != null && menos.isValid()) {
                        menosTick();
                    }
                    if (mobs.isEmpty() && tick > phaseUntil) {
                        if (wave >= WAVES) {
                            seal();
                        } else {
                            phase = Phase.BREATHER;
                            phaseUntil = tick + 100;
                            bar.name(Component.text("Garganta — something larger stirs…", NamedTextColor.DARK_RED));
                            nearby(96).forEach(player -> player.sendActionBar(Component.text(
                                    "The rift shudders. More are coming.", NamedTextColor.RED)));
                        }
                    }
                }
                case BREATHER -> {
                    bar.progress(1f - (phaseUntil - tick) / 100f);
                    if (tick >= phaseUntil) {
                        startWave();
                    }
                }
            }
        }

        /** Collapses the tear if it timed out or everyone left. Returns false if it collapsed. */
        private boolean checkAlive() {
            long limit = Math.max(1, plugin.getConfig().getInt("tears.time-limit-minutes", 12)) * 60_000L;
            if (System.currentTimeMillis() - openedAt > limit) {
                collapse("The Garganta near " + round(center.getBlockX()) + ", " + round(center.getBlockZ())
                        + " closes on its own, and its Hollows are pulled back through.");
                return false;
            }
            lonelyTicks = nearby(96).isEmpty() ? lonelyTicks + 20 : 0;
            if (phase != Phase.OPENING && lonelyTicks > 2400) {
                collapse("With no one left to hunt, the Garganta near " + round(center.getBlockX()) + ", "
                        + round(center.getBlockZ()) + " closes.");
                return false;
            }
            return true;
        }

        private void startWave() {
            wave++;
            phase = Phase.WAVES;
            int players = Math.max(1, Math.min(5, nearby(64).size()));
            List<String> types = new ArrayList<>();
            if (wave < WAVES) {
                for (int i = 0; i < 2 + wave + players; i++) {
                    types.add("adjuchas");
                }
            } else {
                types.add("menos");
                for (int i = 0; i < 1 + players / 2; i++) {
                    types.add("adjuchas");
                }
            }
            waveSize = types.size();
            phaseUntil = tick + types.size() * 12 + 20;
            bar.name(Component.text(wave == WAVES ? "Garganta — Menos Grande" : "Garganta — wave " + wave + "/" + WAVES,
                    NamedTextColor.DARK_RED, TextDecoration.BOLD));
            for (int i = 0; i < types.size(); i++) {
                String type = types.get(i);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (tear == this) {
                        emerge(type);
                    }
                }, i * 12L);
            }
            if (wave == WAVES) {
                nearby(160).forEach(player -> {
                    player.sendMessage(Component.text("A Menos Grande forces its way through the Garganta!",
                            NamedTextColor.DARK_RED, TextDecoration.BOLD));
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.5f);
                });
            }
        }

        private void emerge(String type) {
            Location spot = groundNear(type.equals("menos") ? 4 : 10);
            if (spot == null) {
                spot = center.clone();
            }
            for (double y = rift.getY(); y > spot.getY(); y -= 0.6) {
                world.spawnParticle(Particle.DUST, new Location(world, spot.getX(), y, spot.getZ()), 2, 0.2, 0.1, 0.2, 0, RIFT);
            }
            world.spawnParticle(Particle.SQUID_INK, spot.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);
            world.playSound(spot, Sound.ENTITY_WARDEN_EMERGE, 1.4f, 0.8f);
            LivingEntity mob = service.spawnHollow(spot, type.equals("menos") ? HollowModel.GILLIAN_TYPE : type);
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);
            if (type.equals("menos")) {
                makeMenos(mob);
            }
            mobs.add(mob.getUniqueId());
            Player target = nearestFighter(mob.getLocation(), 48);
            if (target != null && mob instanceof Mob ai) {
                ai.setTarget(target);
            }
        }

        private void makeMenos(LivingEntity mob) {
            int players = Math.max(1, nearby(64).size());
            double health = Math.min(1024, plugin.getConfig().getDouble("tears.menos-health", 300)
                    + (players - 1) * plugin.getConfig().getDouble("tears.menos-health-per-extra-player", 80));
            attribute(mob, Attribute.MAX_HEALTH, health);
            attribute(mob, Attribute.ATTACK_DAMAGE, 12);
            attribute(mob, Attribute.KNOCKBACK_RESISTANCE, 1);
            attribute(mob, Attribute.ARMOR, 10);
            mob.setHealth(health);
            mob.customName(Component.text("Menos Grande", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            mob.getPersistentDataContainer().set(plugin.bossIdKey(), PersistentDataType.STRING, "menos_grande");
            menos = mob;
            menosCeroAt = tick + 100;
        }

        /** The Menos Grande fires a telegraphed Cero: it charges, locks and shows its aim, then fires. */
        private void menosTick() {
            if (tick < menosCeroAt) {
                return;
            }
            Player target = nearestFighter(menos.getLocation(), 40);
            if (target == null) {
                menosCeroAt = tick + 40;
                return;
            }
            menosCeroAt = tick + 160 + ThreadLocalRandom.current().nextInt(80);
            LivingEntity owner = menos;
            world.playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 3f, 0.6f);
            Vector[] aim = new Vector[1];
            new org.bukkit.scheduler.BukkitRunnable() {
                int step;

                @Override
                public void run() {
                    if (tear != Tear.this || !owner.isValid()) {
                        cancel();
                        return;
                    }
                    Location mouth = owner.getEyeLocation();
                    world.spawnParticle(Particle.DUST, mouth, 10, 0.5, 0.5, 0.5, 0, CERO);
                    if (step == 22 && target.isValid()) {
                        aim[0] = target.getEyeLocation().toVector().subtract(mouth.toVector()).normalize();
                        world.playSound(mouth, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 3f, 0.6f);
                    }
                    if (aim[0] != null && step % 2 == 0) {
                        for (double d = 2; d < 44; d += 2) {
                            world.spawnParticle(Particle.DUST, mouth.clone().add(aim[0].clone().multiply(d)), 1, 0, 0, 0, 0, AIM);
                        }
                    }
                    if (step++ >= 40) {
                        cancel();
                        if (aim[0] != null) {
                            cero(owner, mouth, aim[0]);
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        private void cero(LivingEntity source, Location from, Vector direction) {
            world.playSound(from, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.5f);
            Set<UUID> struck = new HashSet<>();
            Location point = from.clone();
            for (double d = 0; d < 46; d += 0.5) {
                point = from.clone().add(direction.clone().multiply(d));
                if (point.getBlock().getType().isSolid()) {
                    break;
                }
                world.spawnParticle(Particle.DUST, point, 4, 0.3, 0.3, 0.3, 0, CERO);
                for (Player player : world.getPlayers()) {
                    if (fighter(player) && !struck.contains(player.getUniqueId())
                            && player.getBoundingBox().expand(0.9).contains(point.toVector())) {
                        struck.add(player.getUniqueId());
                        player.damage(14, source);
                        player.setVelocity(direction.clone().setY(0.35));
                    }
                }
            }
            world.spawnParticle(Particle.EXPLOSION, point, 3, 0.5, 0.5, 0.5);
        }

        private void leash() {
            for (UUID id : mobs) {
                if (Bukkit.getEntity(id) instanceof LivingEntity mob
                        && mob.getLocation().distanceSquared(center) > LEASH * LEASH) {
                    Location back = groundNear(8);
                    if (back != null) {
                        world.spawnParticle(Particle.DUST, mob.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0, RIFT);
                        mob.teleport(back);
                    }
                }
            }
        }

        private void seal() {
            List<Player> contributors = new ArrayList<>();
            double threshold = plugin.getConfig().getDouble("tears.min-contribution", 20);
            for (var entry : damage.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && entry.getValue() >= threshold && player.getWorld() == world) {
                    contributors.add(player);
                }
            }
            contributors.sort((a, b) -> Double.compare(damage.get(b.getUniqueId()), damage.get(a.getUniqueId())));

            // The shared pile drops at the rift for anyone who can hold the ground.
            int rolls = 4 + contributors.size();
            Location pile = center.clone().add(0, 0.5, 0);
            for (int i = 0; i < rolls; i++) {
                world.dropItemNaturally(pile, roll());
            }
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, pile, 80, 1, 1, 1, 0.4);
            for (Player player : contributors) {
                reward(player);
            }

            world.spawnParticle(Particle.EXPLOSION_EMITTER, rift, 2);
            world.playSound(rift, Sound.BLOCK_END_PORTAL_SPAWN, 3f, 1.4f);
            List<String> names = contributors.stream().limit(5).map(Player::getName).toList();
            String where = round(center.getBlockX()) + ", " + round(center.getBlockZ());
            finish(names.isEmpty() ? "The Garganta near " + where + " has been sealed."
                    : "The Garganta near " + where + " was sealed by " + String.join(", ", names)
                            + ". Its spoils lie where it stood.");
        }

        private void reward(Player player) {
            Faction faction = plugin.getFaction(player.getUniqueId());
            List<ItemStack> items = new ArrayList<>();
            items.add(roll());
            int xp = 250;
            String bonus = null;
            if (faction == Faction.SOUL_REAPER) {
                // Closing a Garganta is a Soul Reaper's duty.
                items.add(roll());
                xp *= 2;
                bonus = "Duty fulfilled: a Soul Reaper's share is doubled.";
            } else if (faction == Faction.QUINCY) {
                int kills = Math.min(3, arrowKills.getOrDefault(player.getUniqueId(), 0));
                for (int i = 0; i < kills; i++) {
                    items.add(roll());
                }
                if (kills > 0) {
                    bonus = "Your arrows earned " + kills + " extra spoil" + (kills == 1 ? "" : "s") + ".";
                }
            } else if (faction == Faction.FULLBRINGER) {
                xp += 350;
                bonus = "You draw strength from the rift's residue.";
            }
            player.giveExp(xp);
            for (ItemStack item : items) {
                player.getInventory().addItem(item).values()
                        .forEach(left -> world.dropItemNaturally(player.getLocation(), left));
            }
            player.sendMessage(Component.text("You helped seal the Garganta and received your share.", NamedTextColor.GOLD));
            if (bonus != null) {
                player.sendMessage(Component.text(bonus, NamedTextColor.GRAY));
            }
        }

        private void collapse(String message) {
            for (UUID id : mobs) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    world.spawnParticle(Particle.DUST, entity.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0, RIFT);
                    entity.remove();
                }
            }
            finish(message);
        }

        private void finish(String message) {
            task.cancel();
            mobs.clear();
            pieces.forEach(BlockDisplay::remove);
            pieces.clear();
            for (UUID id : viewers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.hideBossBar(bar);
                }
            }
            viewers.clear();
            tear = null;
            scheduleNext();
            if (message != null) {
                Bukkit.broadcast(Component.text(message, NamedTextColor.DARK_RED));
            }
        }

        // ---- The rift itself ----

        /** A jagged black slit in the sky that always faces the viewer, built from flat displays. */
        private void buildRift() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int segments = 14;
            for (int i = 0; i < segments; i++) {
                float t = (i + 0.5f) / segments;
                float y = (t - 0.5f) * 13f;
                float width = (float) Math.sin(Math.PI * t) * (4.2f + random.nextFloat() * 1.4f);
                float shift = (random.nextFloat() - 0.5f) * 0.8f;
                float roll = (random.nextFloat() - 0.5f) * 0.5f;
                pieces.add(world.spawn(rift, BlockDisplay.class, display -> {
                    display.setBlock(Material.BLACK_CONCRETE.createBlockData());
                    display.setPersistent(false);
                    display.setBillboard(Display.Billboard.VERTICAL);
                    display.setBrightness(new Display.Brightness(0, 0));
                    display.setViewRange(4);
                    display.setInterpolationDuration(20);
                    display.setTransformation(riftPiece(y, 0.05f, shift, roll));
                }));
                widths.add(width);
                shifts.add(shift);
                rolls.add(roll);
                heights.add(y);
            }
        }

        private final List<Float> widths = new ArrayList<>();
        private final List<Float> shifts = new ArrayList<>();
        private final List<Float> rolls = new ArrayList<>();
        private final List<Float> heights = new ArrayList<>();

        private void growRift(float progress) {
            float open = 0.15f + 0.85f * Math.min(1, progress);
            for (int i = 0; i < pieces.size(); i++) {
                BlockDisplay display = pieces.get(i);
                if (display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setTransformation(riftPiece(heights.get(i), widths.get(i) * open, shifts.get(i), rolls.get(i)));
                }
            }
        }

        private Transformation riftPiece(float y, float width, float shift, float roll) {
            float height = 13f / 14 + 0.25f;
            return new Transformation(new Vector3f(shift - width / 2, y - height / 2, 0),
                    new AxisAngle4f(roll, 0, 0, 1), new Vector3f(Math.max(0.05f, width), height, 0.05f),
                    new AxisAngle4f());
        }

        private void riftParticles() {
            world.spawnParticle(Particle.REVERSE_PORTAL, rift, 12, 1.6, 5, 1.6, 0.05);
            world.spawnParticle(Particle.DUST, rift, 6, 1.2, 5, 1.2, 0, RIFT);
            if (phase != Phase.OPENING && tick % 9 == 0) {
                world.spawnParticle(Particle.SQUID_INK, rift.clone().add(0, -6, 0), 8, 0.6, 0.3, 0.6, 0.02);
            }
        }

        private void updateViewers() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean near = player.getWorld() == world && player.getLocation().distanceSquared(center) < 120 * 120;
                if (near && viewers.add(player.getUniqueId())) {
                    player.showBossBar(bar);
                } else if (!near && viewers.remove(player.getUniqueId())) {
                    player.hideBossBar(bar);
                }
            }
        }

        private Location groundNear(int radius) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
                int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (Math.abs(y - center.getBlockY()) <= 8 && world.getBlockAt(x, y, z).getType().isSolid()
                        && world.getBlockAt(x, y + 1, z).isPassable() && world.getBlockAt(x, y + 2, z).isPassable()) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
            return null;
        }

        private List<Player> nearby(double radius) {
            return world.getPlayers().stream().filter(GargantaTears::fighter)
                    .filter(player -> player.getLocation().distanceSquared(center) < radius * radius).toList();
        }
    }

    // ---- Events ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTearMobDamaged(EntityDamageEvent event) {
        if (tear == null || !tear.mobs.contains(event.getEntity().getUniqueId())) {
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            // Lava moats, drowning pits and falls can't do the fighting for anyone.
            if (event.getCause() != EntityDamageEvent.DamageCause.KILL
                    && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
            }
            return;
        }
        Player attacker = attacker(byEntity.getDamager());
        if (attacker == null) {
            if (event.getEntity().equals(tear.menos)) {
                event.setCancelled(true);
            }
            return;
        }
        tear.damage.merge(attacker.getUniqueId(), event.getFinalDamage(), Double::sum);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTearMobDeath(EntityDeathEvent event) {
        if (tear == null || !tear.mobs.contains(event.getEntity().getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(event.getEntity().equals(tear.menos) ? 120 : 15);
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Faction faction = plugin.getFaction(killer.getUniqueId());
        if (faction == Faction.HOLLOW) {
            // Hollows devour what comes through.
            var max = killer.getAttribute(Attribute.MAX_HEALTH);
            killer.setHealth(Math.min(max == null ? 20 : max.getValue(), killer.getHealth() + 4));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0, true, false, true));
            killer.getWorld().spawnParticle(Particle.DUST, event.getEntity().getLocation().add(0, 1, 0), 25,
                    0.4, 0.6, 0.4, 0, RIFT);
            killer.sendActionBar(Component.text("Devoured.", NamedTextColor.DARK_RED));
        } else if (faction == Faction.QUINCY && event.getDamageSource().getDirectEntity() instanceof AbstractArrow) {
            tear.arrowKills.merge(killer.getUniqueId(), 1, Integer::sum);
        } else if (faction == Faction.FULLBRINGER) {
            event.setDroppedExp(event.getDroppedExp() * 2);
        }
    }

    // ---- Helpers ----

    private static ItemStack roll() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int total = LOOT.stream().mapToInt(Loot::weight).sum();
        int pick = random.nextInt(total);
        for (Loot loot : LOOT) {
            pick -= loot.weight();
            if (pick < 0) {
                return new ItemStack(loot.material(), random.nextInt(loot.min(), loot.max() + 1));
            }
        }
        return new ItemStack(Material.DIAMOND);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static Player nearestFighter(Location at, double radius) {
        Player best = null;
        double bestDistance = radius * radius;
        for (Player player : at.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(at);
            if (fighter(player) && distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean fighter(Player player) {
        return player.isValid() && !player.isDead()
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    private static void attribute(LivingEntity entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private boolean setting(String key, boolean fallback) {
        return plugin.getConfig().getBoolean("tears." + key, fallback);
    }

    private static int round(int value) {
        return Math.round(value / 50f) * 50;
    }
}
