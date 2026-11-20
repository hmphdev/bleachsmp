package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Owns arena slots, trial isolation, and a write-before-teleport recovery journal. */
final class InnerWorld implements Listener {
    private static final int RADIUS = 22;
    private static final int FLOOR = 96;
    private static final int SPACING = 2000;

    private record ReturnPoint(String world, double x, double y, double z, float yaw, float pitch, boolean interrupted) {
        ReturnPoint(Location at) {
            this(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch(), true);
        }

        ReturnPoint finished() {
            return new ReturnPoint(world, x, y, z, yaw, pitch, false);
        }
    }

    private record Trial(int slot, SpiritFight fight) { }

    private final BleachSmpPlugin plugin;
    private final AwakeningService awakenings;
    private final BankaiService bankai;
    private final File returnFile;
    private final String worldName;
    private final Map<UUID, Trial> trials = new HashMap<>();
    private final Map<UUID, SpiritFight> spirits = new HashMap<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private final Set<Integer> occupied = new HashSet<>();
    private final Set<Integer> built = new HashSet<>();
    private final Map<UUID, Integer> preparing = new HashMap<>();
    private final Set<UUID> transferring = new HashSet<>();
    private World world;
    private BukkitTask recovery;

    InnerWorld(BleachSmpPlugin plugin, AwakeningService awakenings, BankaiService bankai) {
        this.plugin = plugin;
        this.awakenings = awakenings;
        this.bankai = bankai;
        returnFile = new File(plugin.getDataFolder(), "inner-returns.yml");
        worldName = plugin.getConfig().getString("awakenings.bankai.inner-world-name", "bleachsmp_inner");
    }

    void start() {
        var yaml = YamlConfiguration.loadConfiguration(returnFile);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection entry = yaml.getConfigurationSection(key);
            if (entry == null || !entry.isString("world")) {
                continue;
            }
            try {
                returns.put(UUID.fromString(key), new ReturnPoint(entry.getString("world"), entry.getDouble("x"),
                        entry.getDouble("y"), entry.getDouble("z"), (float) entry.getDouble("yaw"),
                        (float) entry.getDouble("pitch"), entry.getBoolean("interrupted", true)));
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid player IDs from a manually edited file.
            }
        }
        recovery = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (returnPending(player) && !trials.containsKey(player.getUniqueId())) {
                    recover(player);
                }
            }
        }, 20L, 20L);
    }

    private boolean initialize() {
        if (world != null) {
            return true;
        }
        World candidate = Bukkit.getWorld(worldName);
        if (candidate == null) {
            candidate = new WorldCreator(worldName).generateStructures(false).generator(new VoidGenerator()).createWorld();
        }
        if (candidate == null || !(candidate.getGenerator() instanceof VoidGenerator)) {
            plugin.getLogger().warning("Bankai requires a dedicated inner world; cannot use '" + worldName + "'.");
            return false;
        }
        world = candidate;
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        return true;
    }

    boolean contains(World candidate) {
        return candidate != null && candidate.equals(world);
    }

    boolean returnPending(Player player) {
        return returns.containsKey(player.getUniqueId());
    }

    boolean preparing(Player player) {
        return preparing.containsKey(player.getUniqueId());
    }

    boolean enter(Player player, BankaiService.Kit kit, Consumer<Boolean> completed) {
        UUID id = player.getUniqueId();
        Faction faction = awakenings.faction(player);
        if (faction == null || player.isDead() || contains(player.getWorld()) || returnPending(player)
                || preparing(player) || !initialize()) {
            return false;
        }
        int slot = faction.ordinal();
        while (occupied.contains(slot)) {
            slot += Faction.values().length;
        }
        final int arena = slot;
        occupied.add(arena);
        preparing.put(id, arena);
        int x = arenaX(arena);
        // Request arena chunks together without making the tick thread wait on generation.
        var chunks = new java.util.ArrayList<CompletableFuture<Chunk>>();
        for (int cx = (x - RADIUS) >> 4; cx <= (x + RADIUS) >> 4; cx++) {
            for (int cz = -RADIUS >> 4; cz <= RADIUS >> 4; cz++) {
                chunks.add(world.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!preparing.remove(id, arena)) {
                        return;
                    }
                    if (!player.isOnline() || player.isDead() || !AwakeningService.canPlay(player) || !bankai.enabled()
                            || awakenings.crisis().isAwakening(player) || error != null) {
                        occupied.remove(arena);
                        completed.accept(false);
                        return;
                    }
                    tickets(arena, true);
                    if (built.add(arena)) {
                        buildArena(arena, faction);
                    }
                    boolean entered = openTrial(player, kit, faction, arena);
                    if (!entered) {
                        occupied.remove(arena);
                        tickets(arena, false);
                    }
                    completed.accept(entered);
                });
            }
        });
        return true;
    }

    private boolean openTrial(Player player, BankaiService.Kit kit, Faction faction, int slot) {
        UUID id = player.getUniqueId();
        returns.put(id, new ReturnPoint(player.getLocation()));
        if (!saveReturns()) {
            returns.remove(id);
            return false;
        }
        Location arrival = new Location(world, arenaX(slot) + 0.5, FLOOR + 1, -9.5, 0, 0);
        if (!transfer(player, arrival)) {
            returns.remove(id);
            saveReturns();
            return false;
        }
        awakenings.crisis().clearFight(player);
        player.setHealth(maxHealth(player));
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setPlayerTime(faction == Faction.HOLLOW ? 18000 : 6000, false);
        var fight = new SpiritFight(plugin, this, player, kit, faction, arrival, bankai.setting("spirit-health", 120, 1, 1024));
        if (!fight.hasSpirit()) {
            plugin.getLogger().warning("Could not spawn the Bankai trial spirit in " + world.getName());
            returns.computeIfPresent(id, (uuid, point) -> point.finished());
            player.resetPlayerTime();
            recover(player);
            return false;
        }
        trials.put(id, new Trial(slot, fight));
        spirits.put(fight.spiritId(), fight);
        player.showTitle(Title.title(Component.text(fight.formName(), NamedTextColor.GRAY, TextDecoration.ITALIC),
                Component.empty(), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1300), Duration.ofMillis(300))));
        fight.start(20 * 60 * bankai.setting("trial-minutes", 5, 1, 60));
        return true;
    }

    /** Keeps an arena's chunks loaded and entity-ticking while a trial uses it, so the spirit can't unload. */
    private void tickets(int slot, boolean add) {
        int x = arenaX(slot);
        for (int cx = (x - RADIUS) >> 4; cx <= (x + RADIUS) >> 4; cx++) {
            for (int cz = -RADIUS >> 4; cz <= RADIUS >> 4; cz++) {
                if (add) {
                    world.addPluginChunkTicket(cx, cz, plugin);
                } else {
                    world.removePluginChunkTicket(cx, cz, plugin);
                }
            }
        }
    }

    private static int arenaX(int slot) {
        // Reserve spawn at x=0. Use distinct slots per faction so arenas can be reused as-is.
        return (slot + 1) * SPACING;
    }

    private void buildArena(int slot, Faction faction) {
        int x = arenaX(slot);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double radius = Math.hypot(dx, z);
                if (radius > RADIUS) {
                    continue;
                }
                world.getBlockAt(x + dx, FLOOR, z).setType(floorMaterial(faction, dx, z), false);
                if (radius > RADIUS - 1.5) {
                    for (int dy = 1; dy <= 9; dy++) {
                        Material wall = dy >= 4 ? Material.BARRIER : switch (faction) {
                            case SOUL_REAPER -> Material.LIGHT_BLUE_STAINED_GLASS;
                            case HOLLOW -> dy == 1 ? Material.SMOOTH_SANDSTONE : Material.BARRIER;
                            case QUINCY -> Material.LIGHT_BLUE_STAINED_GLASS;
                            case FULLBRINGER -> dy == 1 ? Material.STONE_BRICK_WALL : Material.BARRIER;
                        };
                        world.getBlockAt(x + dx, FLOOR + dy, z).setType(wall, false);
                    }
                }
            }
        }
        switch (faction) {
            case SOUL_REAPER -> {
                for (int z : new int[] {-13, 0, 13}) {
                    for (int dx = -14; dx <= -5; dx++) {
                        block(x + dx, 1, z, Material.SMOOTH_STONE);
                        block(x + dx, 2, z, dx % 3 == 0 ? Material.SMOOTH_STONE : Material.BLUE_STAINED_GLASS);
                    }
                }
            }
            case HOLLOW -> {
                for (int dx : new int[] {-12, 12}) {
                    for (int z : new int[] {-8, 8}) {
                        column(x + dx, z, 4, Material.BONE_BLOCK);
                        block(x + dx + 1, 3, z, Material.BONE_BLOCK);
                        block(x + dx - 1, 4, z, Material.BONE_BLOCK);
                    }
                }
            }
            case QUINCY -> {
                for (int dx : new int[] {-13, 13}) {
                    for (int z : new int[] {-10, 0, 10}) {
                        column(x + dx, z, 5, Material.LIGHT_BLUE_STAINED_GLASS);
                        block(x + dx, 1, z, Material.QUARTZ_BLOCK);
                        block(x + dx, 6, z, Material.QUARTZ_BLOCK);
                    }
                }
            }
            case FULLBRINGER -> {
                column(x - 8, 5, 3, Material.OAK_PLANKS);
                column(x - 6, 5, 3, Material.OAK_PLANKS);
                block(x - 7, 3, 5, Material.OAK_PLANKS);
                column(x - 11, -4, 2, Material.BOOKSHELF);
                column(x - 10, -4, 2, Material.BOOKSHELF);
                for (Bed.Part part : Bed.Part.values()) {
                    Bed bed = (Bed) Material.WHITE_BED.createBlockData();
                    bed.setFacing(org.bukkit.block.BlockFace.EAST);
                    bed.setPart(part);
                    world.getBlockAt(x + (part == Bed.Part.HEAD ? 12 : 11), FLOOR + 3, 5).setBlockData(bed, false);
                }
            }
        }
    }

    private static Material floorMaterial(Faction faction, int x, int z) {
        return switch (faction) {
            case SOUL_REAPER -> x % 5 == 0 || z % 5 == 0 ? Material.SMOOTH_STONE : Material.LIGHT_BLUE_STAINED_GLASS;
            case HOLLOW -> (x * 31 + z * 17) % 9 == 0 ? Material.CALCITE : Material.WHITE_CONCRETE;
            case QUINCY -> x % 6 == 0 || z % 6 == 0 ? Material.QUARTZ_BLOCK : Material.PACKED_ICE;
            case FULLBRINGER -> ((x >> 2) + (z >> 2)) % 3 == 0 ? Material.STONE_BRICKS : Material.OAK_PLANKS;
        };
    }

    private void column(int x, int z, int height, Material material) {
        for (int y = 1; y <= height; y++) {
            block(x, y, z, material);
        }
    }

    private void block(int x, int y, int z, Material material) {
        world.getBlockAt(x, FLOOR + y, z).setType(material, false);
    }

    boolean inArena(Player player) {
        Trial trial = trials.get(player.getUniqueId());
        if (trial == null || !contains(player.getWorld())) {
            return false;
        }
        Location at = player.getLocation();
        return at.getY() >= FLOOR && Math.hypot(at.getX() - arenaX(trial.slot()) - 0.5, at.getZ() - 0.5) <= RADIUS;
    }

    void win(Player player, SpiritFight fight) {
        Trial trial = trials.get(player.getUniqueId());
        if (trial != null && trial.fight() == fight) {
            bankai.winTrial(player, fight.kit(), fight.formName());
            finish(player, false, true);
        }
    }

    void lose(Player player) {
        lose(player, "forfeit");
    }

    void lose(Player player, String reason) {
        if (trials.containsKey(player.getUniqueId())) {
            plugin.getLogger().info("Bankai trial for " + player.getName() + " lost: " + reason + ".");
        }
        finish(player, true, true);
    }

    void abort(Player player) {
        cancelPreparation(player);
        finish(player, false, true);
    }

    private void cancelPreparation(Player player) {
        Integer slot = preparing.remove(player.getUniqueId());
        if (slot != null) {
            occupied.remove(slot);
        }
    }

    private void finish(Player player, boolean loss, boolean returnNow) {
        UUID id = player.getUniqueId();
        Trial trial = trials.remove(id);
        if (trial == null) {
            return;
        }
        spirits.remove(trial.fight().spiritId());
        trial.fight().close();
        occupied.remove(trial.slot());
        tickets(trial.slot(), false);
        awakenings.releases().seal(player, false);
        if (loss) {
            bankai.loseTrial(player);
            player.setHealth(Math.min(4.0, maxHealth(player)));
        }
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.resetPlayerTime();
        ReturnPoint point = returns.get(id);
        if (point != null) {
            returns.put(id, point.finished());
            if (returnNow) {
                recover(player);
            } else {
                saveReturns();
            }
        }
    }

    private void recover(Player player) {
        UUID id = player.getUniqueId();
        ReturnPoint point = returns.get(id);
        if (point == null || player.isDead()) {
            return;
        }
        if (point.interrupted()) {
            bankai.loseTrial(player);
            player.setHealth(Math.min(4.0, maxHealth(player)));
            returns.put(id, point.finished());
            saveReturns();
        }
        World destination = Bukkit.getWorld(point.world());
        Location at;
        if (destination == null) {
            // Avoid stranding players if their original world has since been removed.
            at = Bukkit.getWorlds().getFirst().getSpawnLocation();
        } else {
            at = new Location(destination, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        }
        if (!contains(at.getWorld()) && transfer(player, at)) {
            player.saveData();
            returns.remove(id);
            saveReturns();
        }
    }

    private boolean transfer(Player player, Location at) {
        UUID id = player.getUniqueId();
        transferring.add(id);
        try {
            boolean moved = player.teleport(at);
            if (moved) {
                player.setVelocity(new Vector());
            }
            return moved;
        } finally {
            transferring.remove(id);
        }
    }

    private boolean saveReturns() {
        var yaml = new YamlConfiguration();
        returns.forEach((uuid, point) -> {
            var entry = yaml.createSection(uuid.toString());
            entry.set("world", point.world());
            entry.set("x", point.x());
            entry.set("y", point.y());
            entry.set("z", point.z());
            entry.set("yaw", point.yaw());
            entry.set("pitch", point.pitch());
            entry.set("interrupted", point.interrupted());
        });
        try {
            yaml.save(returnFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save inner-returns.yml: " + exception.getMessage());
            return false;
        }
    }

    void shutdown() {
        preparing.clear();
        if (recovery != null) {
            recovery.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (trials.containsKey(player.getUniqueId())) {
                lose(player);
            } else if (returnPending(player)) {
                recover(player);
            }
        }
        if (!returns.isEmpty()) {
            saveReturns();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        recover(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        cancelPreparation(event.getPlayer());
        finish(event.getPlayer(), true, false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (transferring.contains(id) || event.getTo() == null) {
            return;
        }
        if (contains(event.getTo().getWorld()) && !contains(event.getFrom().getWorld())
                || contains(event.getFrom().getWorld()) && event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (contains(event.getFrom().getWorld()) || event.getTo() != null && contains(event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (contains(event.getFrom()) && !transferring.contains(player.getUniqueId())
                && trials.containsKey(player.getUniqueId())) {
            // The external teleport has landed; don't re-enter the teleport pipeline from its own callback.
            finish(player, true, false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    recover(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTrialCombat(EntityDamageByEntityEvent event) {
        if (!contains(event.getEntity().getWorld())) {
            return;
        }
        var attacker = event.getDamageSource().getCausingEntity();
        SpiritFight target = spirits.get(event.getEntity().getUniqueId());
        if (target != null && !target.owner().equals(attacker)) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player player) {
            Trial trial = trials.get(player.getUniqueId());
            if (trial == null || attacker instanceof Player || attacker != null && !trial.fight().spiritId().equals(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!contains(event.getEntity().getWorld())) {
            return;
        }
        SpiritFight spirit = spirits.get(event.getEntity().getUniqueId());
        if (spirit != null) {
            spirit.onDamage(event);
        } else if (event.getEntity() instanceof Player player) {
            Trial trial = trials.get(player.getUniqueId());
            if (trial == null || trial.fight().finished()) {
                event.setCancelled(true);
            } else if (event.getFinalDamage() >= player.getHealth()) {
                event.setCancelled(true);
                trial.fight().fail();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        // Paper's death event also covers deaths not routed through normal damage (e.g. /kill).
        if (contains(event.getPlayer().getWorld())) {
            event.setReviveHealth(Math.min(4.0, maxHealth(event.getPlayer())));
            event.setCancelled(true);
            Trial trial = trials.get(event.getPlayer().getUniqueId());
            if (trial != null) {
                trial.fight().fail();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        SpiritFight spirit = spirits.get(event.getEntity().getUniqueId());
        if (spirit != null && event.getTarget() != null && !spirit.owner().equals(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (contains(event.getLocation().getWorld()) && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (contains(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (contains(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (contains(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (contains(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (contains(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (contains(event.getEntity().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (contains(event.getPlayer().getWorld()) && (event.getMaterial() == Material.ENDER_PEARL
                || event.getMaterial() == Material.CHORUS_FRUIT
                || event.getClickedBlock() != null && event.getClickedBlock().getBlockData() instanceof Bed)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (contains(event.getPlayer().getWorld()) && event.getItem().getType() == Material.CHORUS_FRUIT) {
            event.setCancelled(true);
        }
    }

    static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, FLOOR + 1, 0.5);
        }
    }
}
