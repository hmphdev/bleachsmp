package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/** Progression and meditation. The world and each spirit fight own their respective lifecycles. */
public final class BankaiService implements Listener {
    static final long HOUR = 3_600_000L;
    private static final long COMBAT_LOCK = 15_000L;
    private static final double STILL = 0.15 * 0.15;
    private static final String CONFIG = "awakenings.bankai.";

    private enum Stage { NONE, MASTERING, MASTERED, SUBJUGATED }

    record Kit(Playstyle style, Playstyle trait) { }
    private record Pair(UUID player, UUID rival) { }
    private record PvPHit(int tick, EntityDamageByEntityEvent event) { }
    private record Meditation(Location origin, int slot, int began, int duration, Kit kit) { }

    private final BleachSmpPlugin plugin;
    private final AwakeningService awakenings;
    private final InnerWorld inner;
    private final NamespacedKey stageKey, hitsKey, winsKey, pendingStyleKey, pendingTraitKey, trialKey;
    private final File victoriesFile;
    private final Map<Pair, Long> victories = new HashMap<>();
    private final Map<Pair, Long> lastCountedHit = new HashMap<>();
    private final Map<Pair, PvPHit> acceptedHits = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    private final Map<UUID, Meditation> meditations = new HashMap<>();
    private BukkitTask pulse;
    private boolean victoriesDirty;

    BankaiService(BleachSmpPlugin plugin, AwakeningService awakenings) {
        this.plugin = plugin;
        this.awakenings = awakenings;
        stageKey = key("stage");
        hitsKey = key("mastery_hits");
        winsKey = key("mastery_wins");
        pendingStyleKey = key("pending_style");
        pendingTraitKey = key("pending_trait");
        trialKey = key("trial_ready_at");
        victoriesFile = new File(plugin.getDataFolder(), "bankai-victories.yml");
        inner = new InnerWorld(plugin, awakenings, this);
    }

    public void start() {
        var stored = YamlConfiguration.loadConfiguration(victoriesFile);
        long now = System.currentTimeMillis();
        for (String entry : stored.getKeys(false)) {
            String[] ids = entry.split(">", -1);
            long until = stored.getLong(entry);
            if (ids.length == 2 && until > now) {
                try {
                    victories.put(new Pair(UUID.fromString(ids[0]), UUID.fromString(ids[1])), until);
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid entries from a manually edited file.
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(inner, plugin);
        inner.start();
        pulse = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
    }

    public void shutdown() {
        if (pulse != null) {
            pulse.cancel();
        }
        inner.shutdown();
        meditations.clear();
        saveVictories();
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean(CONFIG + "enabled", true);
    }

    boolean inInnerWorld(Player player) {
        return inner.contains(player.getWorld());
    }

    int setting(String name, int fallback, int min, int max) {
        return Math.clamp(plugin.getConfig().getInt(CONFIG + name, fallback), min, max);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, "bankai_" + name);
    }

    private Stage stage(Player player) {
        String value = player.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.STRING, "NONE");
        try {
            return Stage.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Stage.NONE;
        }
    }

    private void setStage(Player player, Stage stage) {
        var pdc = player.getPersistentDataContainer();
        if (stage == Stage.NONE) {
            pdc.remove(stageKey);
        } else {
            pdc.set(stageKey, PersistentDataType.STRING, stage.name());
        }
        if (stage != Stage.SUBJUGATED) {
            pdc.remove(pendingStyleKey);
            pdc.remove(pendingTraitKey);
        }
    }

    private boolean eligible(Player player) {
        return enabled() && awakenings.canProgress(player) && awakenings.hasFirstForm(player);
    }

    void beginMastery(Player player) {
        if (stage(player) == Stage.NONE) {
            setStage(player, Stage.MASTERING);
            lore(player, "Your " + firstName(player) + " must prove itself against true rivals.");
        }
        checkMastery(player);
    }

    private boolean mastering(Player player) {
        return eligible(player) && !inInnerWorld(player) && stage(player) == Stage.MASTERING;
    }

    void techniqueHit(Player caster, LivingEntity target, boolean advanced) {
        if (advanced || !(target instanceof Player rival) || !mastering(caster)
                || !awakenings.crisis().validOpponent(caster, rival)) {
            return;
        }
        Pair pair = new Pair(caster.getUniqueId(), rival.getUniqueId());
        // The release hook also runs on blocked damage. Match it to the actual, uncancelled PvP event.
        PvPHit hit = acceptedHits.remove(pair);
        if (hit == null || hit.tick() != Bukkit.getCurrentTick() || hit.event().isCancelled()
                || rival.getLastDamageCause() != hit.event()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastCountedHit.getOrDefault(pair, 0L) <= now && count(caster, hitsKey) < hitGoal()) {
            lastCountedHit.put(pair, now + 1000L);
            increment(caster, hitsKey);
        }
    }

    /** Helping slay a Vasto Lorde in a first-form release counts as one mastery victory. */
    public void recordBossVictory(Player player) {
        if (mastering(player) && awakenings.releases().isActive(player) && !awakenings.releases().isAdvanced(player)
                && count(player, winsKey) < winGoal()) {
            increment(player, winsKey);
            player.sendMessage(Component.text("The Vasto Lorde's fall counts toward your mastery: "
                    + count(player, winsKey) + "/" + winGoal() + " victories.", NamedTextColor.GOLD));
            checkMastery(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player rival = event.getPlayer();
        Player killer = event.getDamageSource().getCausingEntity() instanceof Player source ? source : rival.getKiller();
        if (killer == null || !mastering(killer) || inInnerWorld(rival) || !awakenings.releases().isActive(killer)
                || awakenings.releases().isAdvanced(killer) || !awakenings.crisis().validOpponent(killer, rival)
                || count(killer, winsKey) >= winGoal()) {
            return;
        }
        Pair pair = new Pair(killer.getUniqueId(), rival.getUniqueId());
        long now = System.currentTimeMillis();
        if (victories.getOrDefault(pair, 0L) <= now) {
            victories.put(pair, now + 24 * HOUR);
            victoriesDirty = true;
            saveVictories();
            increment(killer, winsKey);
        }
    }

    private int count(Player player, NamespacedKey counter) {
        return player.getPersistentDataContainer().getOrDefault(counter, PersistentDataType.INTEGER, 0);
    }

    private void increment(Player player, NamespacedKey counter) {
        player.getPersistentDataContainer().set(counter, PersistentDataType.INTEGER, count(player, counter) + 1);
        checkMastery(player);
    }

    private int hitGoal() {
        return setting("mastery-hits", 40, 1, 100_000);
    }

    private int winGoal() {
        return setting("mastery-wins", 5, 1, 100_000);
    }

    private void checkMastery(Player player) {
        if (mastering(player) && count(player, hitsKey) >= hitGoal() && count(player, winsKey) >= winGoal()) {
            setStage(player, Stage.MASTERED);
            lore(player, "Your " + firstName(player) + " has nothing left to teach you. Your spirit is waiting.");
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.35f, 1.0f);
        }
    }

    boolean showProgress(Player player) {
        if (!enabled()) {
            return false;
        }
        String line = switch (stage(player)) {
            case NONE -> null;
            case MASTERING -> "Your " + firstName(player) + " must still prove itself: "
                    + Math.min(count(player, hitsKey), hitGoal()) + "/" + hitGoal() + " technique hits on rivals, "
                    + Math.min(count(player, winsKey), winGoal()) + "/" + winGoal() + " victories while released.";
            case MASTERED -> "Your spirit is waiting. Sneak, still and alone, with your starter in hand.";
            case SUBJUGATED -> "Your " + AwakeningService.formName(awakenings.faction(player), true)
                    + " sleeps. Only a desperate fight can wake it.";
        };
        if (line != null) {
            lore(player, line);
        }
        return line != null;
    }

    private String firstName(Player player) {
        return AwakeningService.formName(awakenings.faction(player), false);
    }

    void lore(Player player, String line) {
        Faction faction = awakenings.faction(player);
        player.sendMessage(Component.text(line, faction == null ? NamedTextColor.GRAY : faction.color(),
                TextDecoration.ITALIC));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0
                || !(event.getDamageSource().getCausingEntity() instanceof Player attacker) || attacker.equals(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        lastCombat.put(victim.getUniqueId(), now);
        lastCombat.put(attacker.getUniqueId(), now);
        breakFocus(attacker);
        if (mastering(attacker)) {
            acceptedHits.put(new Pair(attacker.getUniqueId(), victim.getUniqueId()), new PvPHit(Bukkit.getCurrentTick(), event));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            breakFocus(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Meditation meditation = meditations.get(event.getPlayer().getUniqueId());
        if (meditation != null && !still(meditation, event.getTo())) {
            breakFocus(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnsneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            breakFocus(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHotbar(PlayerItemHeldEvent event) {
        breakFocus(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        meditations.remove(event.getPlayer().getUniqueId());
    }

    private boolean mayMeditate(Player player, long now) {
        return eligible(player) && stage(player) == Stage.MASTERED && player.isSneaking() && !player.isDead()
                && !inInnerWorld(player) && !inner.returnPending(player) && !inner.preparing(player)
                && awakenings.holdsStarter(player)
                && !awakenings.releases().isActive(player) && !awakenings.crisis().isAwakening(player)
                && now - lastCombat.getOrDefault(player.getUniqueId(), 0L) >= COMBAT_LOCK
                && player.getPersistentDataContainer().getOrDefault(trialKey, PersistentDataType.LONG, 0L) <= now
                && alone(player);
    }

    private static boolean alone(Player player) {
        Location at = player.getLocation();
        return at.getNearbyPlayers(24, other -> !other.equals(player)
                && other.getLocation().distanceSquared(at) <= 24 * 24).isEmpty();
    }

    private static boolean still(Meditation meditation, Location at) {
        return meditation.origin().getWorld().equals(at.getWorld()) && meditation.origin().distanceSquared(at) <= STILL;
    }

    private void breakFocus(Player player) {
        if (meditations.remove(player.getUniqueId()) != null) {
            player.sendActionBar(Component.empty());
            lore(player, "Your focus breaks.");
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        int tick = Bukkit.getCurrentTick();
        acceptedHits.values().removeIf(hit -> hit.tick() != tick);
        lastCountedHit.values().removeIf(until -> until <= now);
        lastCombat.values().removeIf(time -> now - time >= COMBAT_LOCK);
        if (tick % 1200 < 5) {
            victoriesDirty |= victories.values().removeIf(until -> until <= now);
            saveVictories();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Meditation meditation = meditations.get(player.getUniqueId());
            if (!mayMeditate(player, now)) {
                breakFocus(player);
                continue;
            }
            Location at = player.getLocation();
            if (meditation == null) {
                meditations.put(player.getUniqueId(), new Meditation(at, player.getInventory().getHeldItemSlot(),
                        tick, 20 * setting("meditation-seconds", 30, 1, 600), chooseKit(player)));
                continue;
            }
            if (!still(meditation, at) || meditation.slot() != player.getInventory().getHeldItemSlot()) {
                breakFocus(player);
                continue;
            }
            int elapsed = tick - meditation.began();
            int remaining = meditation.duration() - elapsed;
            if (remaining <= 0) {
                meditations.remove(player.getUniqueId());
                player.sendActionBar(Component.empty());
                if (!inner.enter(player, meditation.kit(), entered -> {
                    if (entered) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
                    } else {
                        trialUnavailable(player);
                    }
                })) {
                    trialUnavailable(player);
                }
                continue;
            }
            player.sendActionBar(Component.text("Meditating… " + ((remaining + 19) / 20) + "s", NamedTextColor.GRAY));
            var faction = awakenings.faction(player);
            var kit = meditation.kit();
            var form = ReleaseService.form(player.getUniqueId(), faction, kit.style(), true);
            double radius = 2.2 - 1.8 * elapsed / meditation.duration();
            for (int i = 0; i < 4; i++) {
                double angle = elapsed * 0.09 + i * Math.PI / 2;
                var point = at.clone().add(Math.cos(angle) * radius, 0.25 + i * 0.26, Math.sin(angle) * radius);
                at.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, Vfx.dust(form.color(), 0.9f));
            }
            if (elapsed >= 400 && elapsed % 40 < 5) {
                player.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 0.35f, 0.9f);
                ReleaseService.element(player.getUniqueId(), faction, kit.style(), true).burst(at, 0.35);
            }
        }
    }

    private void trialUnavailable(Player player) {
        player.setSneaking(false);
        player.sendMessage(Component.text("The inner world could not open.", NamedTextColor.RED));
    }

    private Kit chooseKit(Player player) {
        int[] evidence = awakenings.evidence(player);
        // The Bankai is the same weapon as the Shikai, so it keeps the first form's playstyle.
        Playstyle style = awakenings.weaponStyle(player, Playstyle.dominant(evidence));
        return new Kit(style, Playstyle.secondary(evidence, style));
    }

    void winTrial(Player player, Kit kit, String formName) {
        setSubjugated(player, kit);
        player.getPersistentDataContainer().remove(trialKey);
        lore(player, formName + " acknowledges you.");
    }

    void loseTrial(Player player) {
        player.getPersistentDataContainer().set(trialKey, PersistentDataType.LONG,
                System.currentTimeMillis() + HOUR * setting("trial-cooldown-hours", 24, 0, 720));
        lore(player, "You are not ready.");
    }

    private void setSubjugated(Player player, Kit kit) {
        setStage(player, Stage.SUBJUGATED);
        var pdc = player.getPersistentDataContainer();
        pdc.set(pendingStyleKey, PersistentDataType.STRING, kit.style().name());
        if (kit.trait() == null) {
            pdc.remove(pendingTraitKey);
        } else {
            pdc.set(pendingTraitKey, PersistentDataType.STRING, kit.trait().name());
        }
    }

    boolean isDormant(Player player) {
        return eligible(player) && stage(player) == Stage.SUBJUGATED && !inInnerWorld(player) && !inner.returnPending(player)
                && plugin.getRank(player.getUniqueId()) <= awakenings.requiredRank() && pendingKit(player) != null;
    }

    Kit pendingKit(Player player) {
        Playstyle style = awakenings.storedStyle(player, pendingStyleKey);
        return style == null ? null : new Kit(style, awakenings.storedStyle(player, pendingTraitKey));
    }

    boolean awaken(Player player, Kit expected) {
        if (!isDormant(player) || !expected.equals(pendingKit(player))) {
            return false;
        }
        awakenings.unlock(player, true, expected.style(), expected.trait());
        setStage(player, Stage.NONE);
        player.getPersistentDataContainer().remove(hitsKey);
        player.getPersistentDataContainer().remove(winsKey);
        return true;
    }

    public void command(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bleachsmp.admin")) {
            sender.sendMessage(Component.text("You do not have permission to manage Bankai trials.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3 || args.length > 4) {
            usage(sender);
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("The target player must be online.");
            return;
        }
        if (!eligible(player) || awakenings.crisis().isAwakening(player)) {
            sender.sendMessage("The player needs a first form, no advanced form, and enabled Bankai trials, in Survival or Adventure.");
            return;
        }
        if (args.length == 3 && args[2].equalsIgnoreCase("trial")) {
            if (inner.enter(player, chooseKit(player), entered -> {
                if (entered) {
                    setStage(player, Stage.MASTERED);
                    sender.sendMessage("Trial started for " + player.getName() + ".");
                } else {
                    sender.sendMessage("Could not start the trial.");
                }
            })) {
                breakFocus(player);
            } else {
                sender.sendMessage("Could not start the trial.");
            }
            return;
        }
        Stage requested;
        boolean reset = args.length == 3 && args[2].equalsIgnoreCase("reset");
        if (reset) {
            requested = Stage.NONE;
        } else if (args.length == 4 && args[2].equalsIgnoreCase("stage")) {
            try {
                requested = Stage.valueOf(args[3].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                usage(sender);
                return;
            }
        } else {
            usage(sender);
            return;
        }
        inner.abort(player);
        breakFocus(player);
        awakenings.crisis().clearFight(player);
        if (reset) {
            var pdc = player.getPersistentDataContainer();
            pdc.remove(hitsKey);
            pdc.remove(winsKey);
            pdc.remove(trialKey);
            victoriesDirty |= victories.keySet().removeIf(pair -> pair.player().equals(player.getUniqueId()));
            saveVictories();
        }
        if (requested == Stage.SUBJUGATED) {
            setSubjugated(player, chooseKit(player));
        } else {
            setStage(player, requested);
        }
        sender.sendMessage("Set " + player.getName() + "'s Bankai stage to " + requested + ".");
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("Usage: /bleach bankai <player> stage <NONE|MASTERING|MASTERED|SUBJUGATED> | trial | reset");
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bleachsmp.admin") || args.length < 2) {
            return List.of();
        }
        List<String> choices = switch (args.length) {
            case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case 3 -> List.of("stage", "trial", "reset");
            case 4 -> args[2].equalsIgnoreCase("stage") ? List.of("NONE", "MASTERING", "MASTERED", "SUBJUGATED") : List.of();
            default -> List.of();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private void saveVictories() {
        if (!victoriesDirty) {
            return;
        }
        var yaml = new YamlConfiguration();
        victories.forEach((pair, until) -> yaml.set(pair.player() + ">" + pair.rival(), until));
        try {
            yaml.save(victoriesFile);
            victoriesDirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save bankai-victories.yml: " + exception.getMessage());
        }
    }
}
