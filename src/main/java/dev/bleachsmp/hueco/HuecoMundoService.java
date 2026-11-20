package dev.bleachsmp.hueco;

import dev.bleachsmp.BleachSmpPlugin;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.HeightMap;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class HuecoMundoService implements Listener {
    private static final String DEFAULT_WORLD_NAME = "HuecoMundo";
    private static final String GARGANTA_ITEM_ID = "garganta";
    private static final int GILLIAN_CLEARANCE_RADIUS = 2;
    private static final int GILLIAN_HEADROOM = (int) Math.ceil(
            HollowModel.forType(HollowModel.GILLIAN_TYPE).height() * HollowModel.GILLIAN_MAGNIFICATION) + 1;
    private final BleachSmpPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;
    private final NamespacedKey hollowTypeKey;
    private VastoLordeBoss vastoLorde;
    private GargantaTears tears;
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final File returnFile;
    private final HuecoArrivalIntro arrivalIntro;
    private World world;
    private HollowVisuals hollowVisuals;

    public HuecoMundoService(BleachSmpPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "dimension_item");
        recipeKey = new NamespacedKey(plugin, "garganta_recipe");
        hollowTypeKey = new NamespacedKey(plugin, "hollow_type");
        returnFile = new File(plugin.getDataFolder(), "garganta-returns.yml");
        arrivalIntro = new HuecoArrivalIntro(plugin);
    }

    public void initialize() {
        String worldName = plugin.getConfig().getString("hueco-mundo.world-name", DEFAULT_WORLD_NAME);
        world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .generateStructures(false)
                    .generator(new HuecoMundoGenerator())
                    .createWorld();
        } else if (!(world.getGenerator() instanceof HuecoMundoGenerator)) {
            throw new IllegalStateException("World '" + worldName
                    + "' is already loaded with a different generator. Choose a dedicated Hueco Mundo world name.");
        }
        if (world == null) {
            throw new IllegalStateException("Could not create the Hueco Mundo world");
        }

        world.setDifficulty(Difficulty.NORMAL);
        world.setSpawnFlags(false, false);
        world.setMonsterSpawnLimit(0);
        world.setAnimalSpawnLimit(0);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(18000L);
        world.setStorm(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
        world.setSpawnLocation(HuecoMundoLayout.GATE.x(), HuecoMundoLayout.GATE.floorY() + 1,
                HuecoMundoLayout.GATE.z() + HuecoMundoLayout.GATE_ARRIVAL_Z_OFFSET, 180.0f);

        loadReturnLocations();
        registerGargantaRecipe();
        Bukkit.getPluginManager().registerEvents(arrivalIntro, plugin);
        hollowVisuals = new HollowVisuals(plugin, world, hollowTypeKey);
        vastoLorde = new VastoLordeBoss(plugin, this);
        tears = new GargantaTears(plugin, this);
        Bukkit.getPluginManager().registerEvents(tears, plugin);
        Bukkit.getPluginManager().registerEvents(vastoLorde, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnHollows, 200L, 200L);
    }

    /** Admin test summon near the player. Returns false if one is already active or no spot was found. */
    public boolean summonVastoLorde(Player player) {
        return vastoLorde != null && vastoLorde.summon(player);
    }

    /** Admin test: opens a Garganta tear near the player in the overworld. */
    public boolean summonTear(Player player) {
        return tears != null && tears.summon(player);
    }

    public World world() {
        return world;
    }

    public void shutdown() {
        arrivalIntro.shutdown();
        if (vastoLorde != null) {
            vastoLorde.shutdown();
        }
        if (tears != null) {
            tears.shutdown();
        }
        if (hollowVisuals != null) {
            hollowVisuals.shutdown();
        }
        if (world != null && world.getGenerator() instanceof HuecoMundoGenerator) {
            saveReturnLocations();
            Bukkit.removeRecipe(recipeKey);
        }
    }

    public boolean isGarganta(ItemStack item) {
        return item != null && item.hasItemMeta()
                && GARGANTA_ITEM_ID.equals(item.getItemMeta().getPersistentDataContainer()
                        .get(itemKey, PersistentDataType.STRING));
    }

    public void activateGarganta(Player player) {
        if (arrivalIntro.isPlaying(player)) {
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.8f, 0.7f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 45,
                0.45, 0.8, 0.45, 0.5);

        if (player.getWorld().equals(world)) {
            returnToOrigin(player);
            return;
        }

        UUID playerId = player.getUniqueId();
        Location previousReturn = returnLocations.put(playerId, player.getLocation().clone());
        saveReturnLocations();
        var gate = HuecoMundoLayout.GATE;
        int arrivalZ = gate.z() + HuecoMundoLayout.GATE_ARRIVAL_Z_OFFSET;
        int groundY = world.getHighestBlockYAt(gate.x(), arrivalZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Location destination = new Location(world, gate.x() + 0.5, groundY + 1, arrivalZ + 0.5, 180.0f, 0.0f);
        if (!player.teleport(destination)) {
            if (previousReturn == null) {
                returnLocations.remove(playerId);
            } else {
                returnLocations.put(playerId, previousReturn);
            }
            saveReturnLocations();
            player.sendMessage(Component.text("The Garganta could not open. Try again.", NamedTextColor.RED));
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 0.8f);
        player.sendMessage(Component.text("The Garganta opens into Hueco Mundo.", NamedTextColor.DARK_PURPLE));
        if (player.getWorld().equals(world) && player.getLocation().distanceSquared(destination) < 1.0) {
            arrivalIntro.playOnFirstArrival(player);
        }
    }

    @EventHandler
    public void onGargantaUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || (event.getAction() == Action.RIGHT_CLICK_BLOCK
                        && event.useInteractedBlock() == Event.Result.DENY)
                || !isGarganta(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        activateGarganta(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHuecoNetherPortal(PlayerPortalEvent event) {
        if (event.getFrom().getWorld().equals(world) && event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Use your Garganta to return from Hueco Mundo.",
                    NamedTextColor.YELLOW));
        }
    }

    private void returnToOrigin(Player player) {
        UUID playerId = player.getUniqueId();
        Location destination = returnLocations.get(playerId);
        if (destination == null || destination.getWorld() == null) {
            World fallback = Bukkit.getWorlds().stream().filter(candidate -> !candidate.equals(world))
                    .findFirst().orElse(null);
            if (fallback == null) {
                player.sendMessage(Component.text("No return world is available.", NamedTextColor.RED));
                return;
            }
            destination = fallback.getSpawnLocation();
        }
        if (!player.teleport(destination)) {
            player.sendMessage(Component.text("The Garganta could not return you. Try again.", NamedTextColor.RED));
            return;
        }
        returnLocations.remove(playerId);
        saveReturnLocations();
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.2f);
        player.sendMessage(Component.text("You return through the Garganta.", NamedTextColor.GRAY));
    }

    private void registerGargantaRecipe() {
        ItemStack garganta = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = garganta.getItemMeta();
        meta.customName(Component.text("Garganta", NamedTextColor.DARK_PURPLE));
        meta.lore(java.util.List.of(
                Component.text("Right-click to travel to or from Hueco Mundo.", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, GARGANTA_ITEM_ID);
        garganta.setItemMeta(meta);

        Bukkit.removeRecipe(recipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, garganta);
        recipe.addIngredient(Material.ECHO_SHARD);
        recipe.addIngredient(Material.ENDER_EYE);
        recipe.addIngredient(Material.GHAST_TEAR);
        Bukkit.addRecipe(recipe);
    }

    private void spawnHollows() {
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        vastoLorde.consider(world);
        int maxTotal = Math.max(0, plugin.getConfig().getInt("hueco-mundo.hollows.max-loaded", 24));
        int maxNearby = Math.max(0, plugin.getConfig().getInt("hueco-mundo.hollows.max-near-player", 4));
        int total = countTaggedHollows(world, null, 0);
        if (total >= maxTotal) {
            return;
        }

        for (Player player : world.getPlayers()) {
            if (total >= maxTotal) {
                break;
            }
            if (arrivalIntro.isPlaying(player) || countTaggedHollows(world, player.getLocation(), 48) >= maxNearby) {
                continue;
            }
            String type = rollHollowType();
            Location spawnLocation = findSpawnLocation(player.getLocation(), type);
            if (spawnLocation == null) {
                continue;
            }
            spawnHollow(spawnLocation, type);
            total++;
        }
    }

    /** Trees, spires and bones: hollows should stand on the sand, not on top of these. */
    private static final java.util.Set<Material> FEATURE_BLOCKS = java.util.EnumSet.of(Material.QUARTZ_BLOCK,
            Material.SMOOTH_QUARTZ, Material.WHITE_STAINED_GLASS, Material.BONE_BLOCK, Material.END_ROD);

    private Location findSpawnLocation(Location center, String type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            int distance = random.nextInt(20, 37);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (HuecoMundoLayout.nearLandmark(x, z, 12) || !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material ground = world.getBlockAt(x, y, z).getType();
            if (y <= world.getMinHeight() || !ground.isSolid() || FEATURE_BLOCKS.contains(ground)) {
                continue;
            }
            Location feet = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (HollowModel.GILLIAN_TYPE.equals(type) ? hasGillianClearance(feet)
                    : hasClearColumn(world, x, y + 1, z, 5)) {
                return feet;
            }
        }
        return null;
    }

    static boolean hasGillianClearance(Location feet) {
        World world = feet.getWorld();
        int minX = (int) Math.floor(feet.getX() - GILLIAN_CLEARANCE_RADIUS);
        int maxX = (int) Math.floor(feet.getX() + GILLIAN_CLEARANCE_RADIUS);
        int minZ = (int) Math.floor(feet.getZ() - GILLIAN_CLEARANCE_RADIUS);
        int maxZ = (int) Math.floor(feet.getZ() + GILLIAN_CLEARANCE_RADIUS);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!hasClearColumn(world, x, feet.getBlockY(), z, GILLIAN_HEADROOM)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasClearColumn(World world, int x, int y, int z, int height) {
        if (y < world.getMinHeight() || y + height > world.getMaxHeight()
                || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        for (int offset = 0; offset < height; offset++) {
            if (!world.getBlockAt(x, y + offset, z).getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private String rollHollowType() {
        // Vasto Lordes never spawn as ordinary Hollows; VastoLordeBoss runs that encounter.
        int roll = ThreadLocalRandom.current().nextInt(100);
        int adjuchasWeight = Math.min(100,
                Math.max(0, plugin.getConfig().getInt("hueco-mundo.hollows.adjuchas-weight", 22)));
        return roll < adjuchasWeight ? "adjuchas" : HollowModel.GILLIAN_TYPE;
    }

    VastoLordeBoss vastoLorde() {
        return vastoLorde;
    }

    NamespacedKey hollowTypeKey() {
        return hollowTypeKey;
    }

    /** A clear spot on open ground 20–36 blocks from {@code center}, or null. */
    Location findBossLocation(Location center) {
        return findSpawnLocation(center, VastoLordeBoss.TYPE);
    }

    LivingEntity spawnHollow(Location location, String type) {
        LivingEntity hollow;
        if (type.equals("vasto_lorde")) {
            hollow = location.getWorld().spawn(location, WitherSkeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                    entity -> configureHollow(entity, type));
        } else {
            hollow = location.getWorld().spawn(location, Husk.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                    entity -> configureHollow(entity, type));
        }
        hollow.getPersistentDataContainer().set(plugin.bossIdKey(), PersistentDataType.STRING, type);
        hollow.getPersistentDataContainer().set(hollowTypeKey, PersistentDataType.STRING, type);
        hollowVisuals.track(hollow, type);
        return hollow;
    }

    private void configureHollow(LivingEntity hollow, String type) {
        String name = switch (type) {
            case "vasto_lorde" -> "Vasto Lorde";
            case "adjuchas" -> "Adjuchas";
            default -> "Gillian";
        };
        double health = switch (type) {
            case "vasto_lorde" -> 100.0;
            case "adjuchas" -> 42.0;
            default -> 70.0;
        };
        double damage = switch (type) {
            case "vasto_lorde" -> 12.0;
            case "adjuchas" -> 7.0;
            default -> 9.0;
        };
        double scale = switch (type) {
            case "vasto_lorde" -> 1.35;
            case "adjuchas" -> HollowModel.ADJUCHAS_MOB_SCALE;
            default -> HollowModel.GILLIAN_MOB_SCALE;
        };
        // Husks can roll as babies: tiny and frantic. A Hollow never is.
        if (hollow instanceof Husk husk) {
            husk.setAdult();
        }
        setAttribute(hollow, Attribute.MAX_HEALTH, health);
        setAttribute(hollow, Attribute.ATTACK_DAMAGE, damage);
        setAttribute(hollow, Attribute.SCALE, scale);
        setAttribute(hollow, Attribute.FOLLOW_RANGE, 36.0);
        // Giants lumber; Adjuchas lope. Speed is per block, so the Gillian's long stride still covers ground.
        setAttribute(hollow, Attribute.MOVEMENT_SPEED, switch (type) {
            case "adjuchas" -> 0.27;
            case "vasto_lorde" -> 0.23;
            default -> 0.17;
        });
        hollow.setHealth(health);
        hollow.setCanPickupItems(false);
        hollow.customName(Component.text(name, type.equals("vasto_lorde")
                ? NamedTextColor.DARK_RED : NamedTextColor.GRAY));
        hollow.setCustomNameVisible(true);
        hollow.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0,
                true, false, false));
        hollow.getWorld().spawnParticle(Particle.ASH, hollow.getLocation().add(0, 1, 0), 10,
                0.5, 0.8, 0.5, 0.01);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private int countTaggedHollows(World targetWorld, Location center, double radius) {
        int count = 0;
        for (LivingEntity entity : targetWorld.getLivingEntities()) {
            if (!entity.getPersistentDataContainer().has(hollowTypeKey, PersistentDataType.STRING)) {
                continue;
            }
            if (center == null || entity.getLocation().distanceSquared(center) <= radius * radius) {
                count++;
            }
        }
        return count;
    }

    private void loadReturnLocations() {
        if (!returnFile.exists()) {
            return;
        }
        var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(returnFile);
        var players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String playerId : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(playerId);
                String path = "players." + playerId;
                World returnWorld = Bukkit.getWorld(data.getString(path + ".world", ""));
                if (returnWorld == null) {
                    continue;
                }
                returnLocations.put(uuid, new Location(returnWorld,
                        data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                        (float) data.getDouble(path + ".yaw"), (float) data.getDouble(path + ".pitch")));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid Garganta return location for " + playerId);
            }
        }
    }

    private void saveReturnLocations() {
        var data = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, Location> entry : returnLocations.entrySet()) {
            Location location = entry.getValue();
            if (location.getWorld() == null) {
                continue;
            }
            String path = "players." + entry.getKey();
            data.set(path + ".world", location.getWorld().getName());
            data.set(path + ".x", location.getX());
            data.set(path + ".y", location.getY());
            data.set(path + ".z", location.getZ());
            data.set(path + ".yaw", location.getYaw());
            data.set(path + ".pitch", location.getPitch());
        }
        try {
            data.save(returnFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save Garganta return locations: " + exception.getMessage());
        }
    }
}
