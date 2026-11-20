package dev.bleachsmp;

import dev.bleachsmp.ability.AbilityListener;
import dev.bleachsmp.awakening.AwakeningService;
import dev.bleachsmp.awakening.PlaystyleListener;
import dev.bleachsmp.command.BleachCommand;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.faction.FactionMenu;
import dev.bleachsmp.item.StarterItems;
import dev.bleachsmp.hueco.HuecoMundoService;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class BleachSmpPlugin extends JavaPlugin {
    private static final Pattern BOSS_ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,48}");

    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<UUID, Integer> rankPoints = new HashMap<>();
    private final Map<UUID, Set<String>> bossClears = new HashMap<>();
    private final Map<String, Long> pvpKillTimes = new HashMap<>();
    private final Map<UUID, Long> weeklyChallengeClaims = new HashMap<>();
    private File playerDataFile;
    private YamlConfiguration playerData;
    private NamespacedKey itemTypeKey;
    private NamespacedKey itemFactionKey;
    private NamespacedKey bossIdKey;
    private NamespacedKey bossDamageKey;
    private NamespacedKey bossOriginalPersistentKey;
    private StarterItems starterItems;
    private FactionMenu factionMenu;
    private HuecoMundoService huecoMundo;
    private AwakeningService awakenings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemTypeKey = new NamespacedKey(this, "item_type");
        itemFactionKey = new NamespacedKey(this, "item_faction");
        bossIdKey = new NamespacedKey(this, "custom_boss_id");
        bossDamageKey = new NamespacedKey(this, "boss_damage");
        bossOriginalPersistentKey = new NamespacedKey(this, "boss_original_persistent");
        starterItems = new StarterItems(this);
        loadPlayerData();

        factionMenu = new FactionMenu(this, starterItems);
        getServer().getPluginManager().registerEvents(factionMenu, this);
        getServer().getPluginManager().registerEvents(new ProgressionListener(this), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        awakenings = new AwakeningService(this);
        getServer().getPluginManager().registerEvents(new PlaystyleListener(awakenings), this);
        getServer().getPluginManager().registerEvents(awakenings.releases(), this);
        getServer().getPluginManager().registerEvents(awakenings.crisis(), this);
        getServer().getPluginManager().registerEvents(awakenings.signatures(), this);
        awakenings.crisis().start();
        huecoMundo = new HuecoMundoService(this);
        getServer().getPluginManager().registerEvents(huecoMundo, this);
        huecoMundo.initialize();

        BleachCommand command = new BleachCommand(this, factionMenu);
        if (getCommand("bleach") == null) {
            throw new IllegalStateException("The bleach command is missing from plugin.yml");
        }
        getCommand("bleach").setExecutor(command);
        getCommand("bleach").setTabCompleter(command);

        getLogger().info("BleachSMP enabled.");
    }

    @Override
    public void onDisable() {
        if (awakenings != null) {
            awakenings.releases().sealAll();
            awakenings.crisis().shutdown();
            awakenings.signatures().shutdown();
        }
        savePlayerData();
        if (huecoMundo != null) {
            huecoMundo.shutdown();
        }
    }

    public HuecoMundoService huecoMundo() {
        return huecoMundo;
    }

    public AwakeningService awakenings() {
        return awakenings;
    }

    public Faction getFaction(UUID playerId) {
        return factions.get(playerId);
    }

    public NamespacedKey itemTypeKey() {
        return itemTypeKey;
    }

    public NamespacedKey itemFactionKey() {
        return itemFactionKey;
    }

    public NamespacedKey bossIdKey() {
        return bossIdKey;
    }

    public NamespacedKey bossDamageKey() {
        return bossDamageKey;
    }

    public boolean markBoss(org.bukkit.entity.LivingEntity entity, String bossId) {
        if (!BOSS_ID_PATTERN.matcher(bossId).matches()) {
            return false;
        }
        var data = entity.getPersistentDataContainer();
        String previousId = data.get(bossIdKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (!data.has(bossOriginalPersistentKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            data.set(bossOriginalPersistentKey, org.bukkit.persistence.PersistentDataType.BYTE,
                    (byte) (entity.isPersistent() ? 1 : 0));
        }
        if (!bossId.equals(previousId)) {
            data.remove(bossDamageKey);
        }
        data.set(bossIdKey, org.bukkit.persistence.PersistentDataType.STRING, bossId);
        entity.setPersistent(true);
        return true;
    }

    public boolean unmarkBoss(org.bukkit.entity.LivingEntity entity) {
        if (!entity.getPersistentDataContainer().has(bossIdKey,
                org.bukkit.persistence.PersistentDataType.STRING)) {
            return false;
        }
        var data = entity.getPersistentDataContainer();
        Byte wasPersistent = data.get(bossOriginalPersistentKey, org.bukkit.persistence.PersistentDataType.BYTE);
        if (wasPersistent != null) {
            entity.setPersistent(wasPersistent == 1);
        }
        data.remove(bossIdKey);
        data.remove(bossDamageKey);
        data.remove(bossOriginalPersistentKey);
        return true;
    }

    public int getRankPoints(UUID playerId) {
        return rankPoints.getOrDefault(playerId, 0);
    }

    public int getRank(UUID playerId) {
        int points = getRankPoints(playerId);
        for (int rank = 1; rank <= 5; rank++) {
            if (points >= getConfig().getInt("progression.ranks." + rank + ".points", defaultPoints(rank))) {
                return rank;
            }
        }
        return 5;
    }

    public String getFactionRankTitle(UUID playerId) {
        Faction faction = getFaction(playerId);
        if (faction == null) {
            return "Unchosen";
        }
        int rank = getRank(playerId);
        return getConfig().getString("progression.ranks." + rank + ".titles." + faction.name(),
                "Rank " + rank);
    }

    public int getPointsToNextRank(UUID playerId) {
        int rank = getRank(playerId);
        if (rank == 1) {
            return 0;
        }
        int nextThreshold = getConfig().getInt("progression.ranks." + (rank - 1) + ".points",
                defaultPoints(rank - 1));
        return Math.max(0, nextThreshold - getRankPoints(playerId));
    }

    public void applyRankStats(Player player) {
        if (getFaction(player.getUniqueId()) == null) {
            return;
        }
        int rank = getRank(player.getUniqueId());
        // Ranks no longer grant extra hearts. Reset any bonus health granted by earlier versions.
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null && attribute.getBaseValue() != attribute.getDefaultValue()) {
            attribute.setBaseValue(attribute.getDefaultValue());
            if (player.getHealth() > attribute.getValue()) {
                player.setHealth(attribute.getValue());
            }
        }
        if (rank == 1) {
            var strength = player.getPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
            int maxAmplifier = Math.max(0, getConfig().getInt("progression.rank-1-max-strength-amplifier", 0));
            if (strength != null && strength.getAmplifier() > maxAmplifier) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.STRENGTH, strength.getDuration(), maxAmplifier,
                        strength.isAmbient(), strength.hasParticles(), strength.hasIcon()), true);
            }
        }
    }

    public boolean assignFaction(Player player, Faction faction) {
        UUID playerId = player.getUniqueId();
        if (factions.containsKey(playerId)) {
            return false;
        }

        factions.put(playerId, faction);
        rankPoints.putIfAbsent(playerId, 0);
        if (!savePlayerData()) {
            factions.remove(playerId);
            rankPoints.remove(playerId);
            playerData.set("players." + playerId, null);
            return false;
        }

        starterItems.give(player, faction);
        applyRankStats(player);
        return true;
    }

    public boolean awardPvpKill(Player killer, Player victim) {
        UUID killerId = killer.getUniqueId();
        if (getFaction(killerId) == null || getFaction(victim.getUniqueId()) == null || killerId.equals(victim.getUniqueId())) {
            return false;
        }

        long now = Instant.now().getEpochSecond();
        long cooldown = Math.max(0L, getConfig().getLong("progression.pvp.repeat-kill-cooldown-hours", 24L)) * 3600L;
        String firstId = killerId.compareTo(victim.getUniqueId()) < 0 ? killerId.toString() : victim.getUniqueId().toString();
        String secondId = killerId.compareTo(victim.getUniqueId()) < 0 ? victim.getUniqueId().toString() : killerId.toString();
        String pairKey = firstId + ":" + secondId;
        long previous = pvpKillTimes.getOrDefault(pairKey, 0L);
        if (now - previous < cooldown) {
            return false;
        }

        int points = Math.max(0, getConfig().getInt("progression.pvp.points-per-kill", 1));
        if (points == 0) {
            return false;
        }
        pvpKillTimes.put(pairKey, now);
        awardPoints(killer, points, "player kill");
        awakenings.checkUnlock(killer);
        return true;
    }

    public boolean awardBossFirstClear(Player player, String bossId) {
        UUID playerId = player.getUniqueId();
        if (getFaction(playerId) == null) {
            return false;
        }
        Set<String> clears = bossClears.computeIfAbsent(playerId, ignored -> new HashSet<>());
        boolean firstClear = clears.add(bossId);
        boolean claimedChallenge = weeklyChallengeClaims.getOrDefault(playerId, Long.MIN_VALUE)
                != currentChallengeWeek() && bossId.equals(activeBossChallenge());
        if (!firstClear && !claimedChallenge) {
            return false;
        }

        if (firstClear) {
            int points = Math.max(0, getConfig().getInt("progression.boss.first-clear-points", 3));
            awardPoints(player, points, "first boss clear: " + bossId);
            player.sendMessage(Component.text("Boss first-clear recorded: " + bossId, NamedTextColor.GOLD));
        }
        if (claimedChallenge) {
            weeklyChallengeClaims.put(playerId, currentChallengeWeek());
            int challengePoints = Math.max(0, getConfig().getInt("progression.boss.weekly-challenge-points", 2));
            awardPoints(player, challengePoints, "weekly boss challenge");
            player.sendMessage(Component.text("Weekly boss challenge complete: " + bossId, NamedTextColor.GOLD));
        }
        awakenings.recordBossReward(player, firstClear, claimedChallenge);
        savePlayerData();
        return true;
    }

    public boolean isBossCleared(UUID playerId, String bossId) {
        return bossClears.getOrDefault(playerId, Set.of()).contains(bossId);
    }

    public String activeBossChallenge() {
        var challenges = getConfig().getStringList("progression.weekly-challenges");
        if (challenges.isEmpty()) {
            return "none";
        }
        long week = currentChallengeWeek();
        int index = (int) Math.floorMod(week, challenges.size());
        return challenges.get(index);
    }

    public long currentChallengeWeek() {
        LocalDate today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        WeekFields iso = WeekFields.ISO;
        return today.get(iso.weekBasedYear()) * 100L + today.get(iso.weekOfWeekBasedYear());
    }

    private void awardPoints(Player player, int points, String reason) {
        if (points <= 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int oldRank = getRank(playerId);
        rankPoints.merge(playerId, points, Integer::sum);
        int newRank = getRank(playerId);
        if (newRank < oldRank) {
            applyRankStats(player);
            player.sendMessage(Component.text("Rank up! You are now " + getFactionRankTitle(playerId)
                    + " (Rank " + newRank + ").", NamedTextColor.GOLD));
        } else {
            player.sendMessage(Component.text("+" + points + " rank progress (" + reason + "). "
                    + getPointsToNextRank(playerId) + " points to the next rank.", NamedTextColor.GRAY));
        }
        savePlayerData();
    }

    private void loadPlayerData() {
        playerDataFile = new File(getDataFolder(), "players.yml");
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory");
        }
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        ConfigurationSection players = playerData.getConfigurationSection("players");
        if (players != null) {
            for (String playerId : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(playerId);
                    String factionName = playerData.getString("players." + playerId + ".faction");
                    if (factionName != null) {
                        factions.put(uuid, Faction.valueOf(factionName));
                    }
                    rankPoints.put(uuid, Math.max(0, playerData.getInt("players." + playerId + ".rank-points", 0)));
                    bossClears.put(uuid,
                            new HashSet<>(playerData.getStringList("players." + playerId + ".boss-clears")));
                    if (playerData.contains("players." + playerId + ".weekly-challenge-week")) {
                        weeklyChallengeClaims.put(uuid,
                                playerData.getLong("players." + playerId + ".weekly-challenge-week"));
                    }
                } catch (IllegalArgumentException exception) {
                    getLogger().warning("Ignoring invalid player data for " + playerId);
                }
            }
        }

        ConfigurationSection pvpSection = playerData.getConfigurationSection("pvp-kill-times");
        if (pvpSection != null) {
            for (String key : pvpSection.getKeys(false)) {
                pvpKillTimes.put(key, pvpSection.getLong(key));
            }
        }
    }

    private boolean savePlayerData() {
        if (playerData == null || playerDataFile == null) {
            return false;
        }
        for (UUID playerId : factions.keySet()) {
            String path = "players." + playerId;
            playerData.set(path + ".faction", factions.get(playerId).name());
            playerData.set(path + ".rank-points", rankPoints.getOrDefault(playerId, 0));
            playerData.set(path + ".boss-clears", bossClears.getOrDefault(playerId, Set.of()).stream().sorted().toList());
            if (weeklyChallengeClaims.containsKey(playerId)) {
                playerData.set(path + ".weekly-challenge-week", weeklyChallengeClaims.get(playerId));
            }
        }
        playerData.set("pvp-kill-times", pvpKillTimes);
        try {
            playerData.save(playerDataFile);
            return true;
        } catch (IOException exception) {
            getLogger().severe("Could not save player data: " + exception.getMessage());
            return false;
        }
    }

    private int defaultPoints(int rank) {
        return switch (rank) {
            case 5 -> 0;
            case 4 -> 3;
            case 3 -> 8;
            case 2 -> 15;
            case 1 -> 25;
            default -> Integer.MAX_VALUE;
        };
    }
}
