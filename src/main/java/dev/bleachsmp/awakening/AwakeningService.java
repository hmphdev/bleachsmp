package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.awakening.Playstyle.Habit;
import dev.bleachsmp.faction.Faction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class AwakeningService {
    private final BleachSmpPlugin plugin;
    private final AwakeningPowers powers;
    private final ReleaseService releases;
    private final CrisisService crisis;
    private final BankaiService bankai;
    private final NamespacedKey firstKey;
    private final NamespacedKey advancedKey;
    private final NamespacedKey firstTraitKey;
    private final NamespacedKey advancedTraitKey;
    private final NamespacedKey evidenceKey;
    private final NamespacedKey visitsKey;
    private final NamespacedKey releaseReadyKey;
    private final NamespacedKey concealedUntilKey;
    private final NamespacedKey dormantKey;
    private final NamespacedKey[] habitKeys = new NamespacedKey[Habit.values().length];

    public AwakeningService(BleachSmpPlugin plugin) {
        this.plugin = plugin;
        powers = new AwakeningPowers(this);
        releases = new ReleaseService(plugin, this);
        crisis = new CrisisService(plugin, this);
        bankai = new BankaiService(plugin, this);
        firstKey = new NamespacedKey(plugin, "first_awakening");
        advancedKey = new NamespacedKey(plugin, "advanced_awakening");
        firstTraitKey = new NamespacedKey(plugin, "first_awakening_trait");
        advancedTraitKey = new NamespacedKey(plugin, "advanced_awakening_trait");
        evidenceKey = new NamespacedKey(plugin, "awakening_evidence");
        visitsKey = new NamespacedKey(plugin, "awakening_visited_areas");
        releaseReadyKey = new NamespacedKey(plugin, "awakening_ready_at");
        concealedUntilKey = new NamespacedKey(plugin, "awakening_concealed_until");
        dormantKey = new NamespacedKey(plugin, "awakening_dormant_notified");
        for (Habit habit : Habit.values()) {
            habitKeys[habit.ordinal()] = new NamespacedKey(plugin, "awakening_habit_" + habit.name().toLowerCase(Locale.ROOT));
        }
    }

    public ReleaseService releases() {
        return releases;
    }

    public CrisisService crisis() {
        return crisis;
    }

    public BankaiService bankai() {
        return bankai;
    }

    private boolean crisisEnabled() {
        return plugin.getConfig().getBoolean("awakenings.crisis.enabled", true);
    }

    /** The first form is ready but sleeping: only a crisis in a real fight can wake it. */
    boolean isDormant(Player player) {
        return crisisEnabled() && canProgress(player) && faction(player) != null && unlocked(player, false) == null
                && Playstyle.ready(evidence(player), false, plugin.getRank(player.getUniqueId()), requiredRank());
    }

    boolean canStrike(Player caster, LivingEntity target) {
        return powers.canStrike(caster, target);
    }

    boolean canProgress(Player player) {
        return canPlay(player) && faction(player) != null && unlocked(player, true) == null;
    }

    static boolean canPlay(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    void record(Player player, Habit... habits) {
        if (!canProgress(player)) {
            return;
        }
        int[] evidence = evidence(player);
        boolean changed = false;
        for (Habit habit : habits) {
            changed |= addEvidence(player, evidence, habit);
        }
        if (changed) {
            player.getPersistentDataContainer().set(evidenceKey, PersistentDataType.INTEGER_ARRAY, evidence);
            checkUnlock(player);
        }
    }

    void discover(Player player, String area) {
        if (!canProgress(player)) {
            return;
        }
        var data = player.getPersistentDataContainer();
        var type = PersistentDataType.LIST.strings();
        List<String> visited = data.getOrDefault(visitsKey, type, List.of());
        if (visited.contains(area)) {
            return;
        }
        int[] evidence = evidence(player);
        if (addEvidence(player, evidence, Habit.DISCOVERY)) {
            var updated = new ArrayList<>(visited);
            updated.add(area);
            data.set(visitsKey, type, updated);
            data.set(evidenceKey, PersistentDataType.INTEGER_ARRAY, evidence);
            checkUnlock(player);
        }
    }

    public void recordBossReward(Player player, boolean firstClear, boolean weeklyChallenge) {
        if (firstClear && weeklyChallenge) {
            record(player, Habit.BOSS, Habit.BOSS);
        } else if (firstClear || weeklyChallenge) {
            record(player, Habit.BOSS);
        }
        checkUnlock(player);
    }

    private boolean addEvidence(Player player, int[] evidence, Habit habit) {
        int index = habit.ordinal();
        if (evidence[index] >= habit.cap) {
            return false;
        }
        if (habit.intervalSeconds > 0) {
            var data = player.getPersistentDataContainer();
            long now = System.currentTimeMillis();
            if (data.getOrDefault(habitKeys[index], PersistentDataType.LONG, 0L) > now) {
                return false;
            }
            data.set(habitKeys[index], PersistentDataType.LONG, now + habit.intervalSeconds * 1000L);
        }
        evidence[index] = Math.min(habit.cap, evidence[index] + habit.points);
        return true;
    }

    public void checkUnlock(Player player) {
        if (!canProgress(player)) {
            return;
        }
        boolean advanced = unlocked(player, false) != null;
        int[] evidence = evidence(player);
        if (!Playstyle.ready(evidence, advanced, plugin.getRank(player.getUniqueId()), requiredRank())) {
            return;
        }
        if (!advanced && crisisEnabled()) {
            // The first form never unlocks quietly: it waits for a crisis. Tell the player once.
            var data = player.getPersistentDataContainer();
            if (!data.has(dormantKey)) {
                data.set(dormantKey, PersistentDataType.BYTE, (byte) 1);
                player.sendMessage(Component.text("Your " + formName(faction(player), false)
                        + " is ready, but it sleeps. It will only wake when a true rival pushes you to the brink.",
                        NamedTextColor.DARK_AQUA, net.kyori.adventure.text.format.TextDecoration.ITALIC));
            }
            return;
        }
        if (advanced && bankai.enabled()) {
            bankai.beginMastery(player);
            return;
        }
        Playstyle style = unlock(player, advanced, evidence);
        player.sendMessage(Component.text("Awakening unlocked: " + formName(faction(player), advanced) + " — "
                + powerName(player, faction(player), style, advanced) + "!", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Hold your faction starter item and press swap hands (F) to release it."
                + (advanced ? " While it is out, F fires its ultimate and sneak + F seals it." : ""),
                NamedTextColor.YELLOW));
    }

    private Playstyle unlock(Player player, boolean advanced, int[] evidence) {
        Playstyle style = Playstyle.dominant(evidence);
        unlock(player, advanced, style, Playstyle.secondary(evidence, style));
        return style;
    }

    void unlock(Player player, boolean advanced, Playstyle style, Playstyle trait) {
        var data = player.getPersistentDataContainer();
        var traitKey = advanced ? advancedTraitKey : firstTraitKey;
        data.set(advanced ? advancedKey : firstKey, PersistentDataType.STRING, style.name());
        if (trait == null) {
            data.remove(traitKey);
        } else {
            data.set(traitKey, PersistentDataType.STRING, trait.name());
        }
        data.remove(evidenceKey);
        data.remove(dormantKey);
    }

    void releaseAdvancedAwakened(Player player, Faction faction, Playstyle style, Playstyle trait) {
        releases.release(player, faction, style, trait, true);
        if (trait != null) {
            AwakeningPowers.applyTrait(player, trait);
        }
        player.getPersistentDataContainer().set(releaseReadyKey, PersistentDataType.LONG,
                System.currentTimeMillis() + (releases.durationSeconds(true) + releaseCooldown(true)) * 1000L);
    }

    /** Unlocks a dormant first form at the moment of crisis. */
    boolean awakenFirst(Player player) {
        if (!isDormant(player)) {
            return false;
        }
        unlock(player, false, evidence(player));
        return true;
    }

    /** Bursts straight into the newly awakened first form, ignoring the release cooldown. */
    void releaseAwakened(Player player) {
        Faction faction = faction(player);
        Playstyle style = unlocked(player, false);
        if (faction == null || style == null || !canPlay(player)) {
            return;
        }
        Playstyle trait = storedStyle(player, firstTraitKey);
        releases.release(player, faction, style, trait, false);
        if (trait != null) {
            AwakeningPowers.applyTrait(player, trait);
        }
        player.getPersistentDataContainer().set(releaseReadyKey, PersistentDataType.LONG,
                System.currentTimeMillis() + (releases.durationSeconds(false) + releaseCooldown(false)) * 1000L);
        player.sendMessage(Component.text("Your " + formName(faction, false) + " has awakened: "
                + powerName(player, faction, style, false) + "! From now on, hold your starter item and press F to release it.",
                NamedTextColor.GOLD));
    }

    /**
     * Called every time a release technique, signature or ultimate damages something. Hook for Bankai mastery
     * (see docs/handoff/bankai-awakening.md): count hits on real rival players while a first form is out.
     */
    void techniqueHit(Player caster, LivingEntity target, boolean advanced) {
        bankai.techniqueHit(caster, target, advanced);
    }

    public void showProgress(Player player) {
        Faction faction = faction(player);
        if (faction == null) {
            player.sendMessage(Component.text("Choose a faction with /bleach choose to begin awakening.", NamedTextColor.YELLOW));
            return;
        }
        checkUnlock(player);
        Playstyle first = unlocked(player, false);
        Playstyle advanced = unlocked(player, true);
        player.sendMessage(Component.text("Awakenings — " + faction.displayName(), faction.color()));
        showForm(player, faction, first, false);
        if (first == null && isDormant(player)) {
            player.sendMessage(Component.text("Your " + formName(faction, false)
                    + " is dormant. Only a true rival pushing you to the brink can wake it.", NamedTextColor.DARK_AQUA));
        }
        showForm(player, faction, advanced, true);
        if (advanced == null && !bankai.showProgress(player)) {
            int progress = Playstyle.total(evidence(player));
            int goal = Playstyle.requiredEvidence(first != null);
            player.sendMessage(Component.text("Next form: " + Math.min(progress, goal) + "/" + goal + " playstyle evidence"
                    + (first == null ? " (no rank requirement)" : " and Rank " + requiredRank() + " or better"),
                    NamedTextColor.YELLOW));
        }
        if (first != null) {
            player.sendMessage(Component.text("Hold your starter item and press F, or use /bleach release first|advanced.",
                    NamedTextColor.GRAY));
        }
    }

    private void showForm(Player player, Faction faction, Playstyle style, boolean advanced) {
        Playstyle trait = storedStyle(player, advanced ? advancedTraitKey : firstTraitKey);
        player.sendMessage(Component.text(formName(faction, advanced) + ": " + (style == null ? "Locked"
                : powerName(player, faction, style, advanced) + " — " + powerDescription(player, faction, style, trait, advanced)
                        + (trait == null ? "" : " Trait: " + AwakeningPowers.traitName(trait) + ".")),
                style == null ? NamedTextColor.GRAY : faction.color()));
    }

    public void release(Player player, Boolean advancedRequested) {
        if (crisis.isAwakening(player)) {
            return;
        }
        Faction faction = faction(player);
        if (faction == null || !holdsStarter(player) || !canPlay(player)) {
            player.sendMessage(Component.text("Hold your own faction starter item in Survival or Adventure to release a form.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (releases.isActive(player)) {
            boolean activeAdvanced = releases.isAdvanced(player);
            // While an advanced form is out, release fires its ultimate; sneaking seals it instead.
            if (activeAdvanced && !player.isSneaking()) {
                releases.ultimate(player);
                return;
            }
            // Sealing early starts that tier's release cooldown now.
            releases.seal(player, true);
            player.getPersistentDataContainer().set(releaseReadyKey, PersistentDataType.LONG,
                    System.currentTimeMillis() + releaseCooldown(activeAdvanced) * 1000L);
            return;
        }
        boolean advanced = advancedRequested == null ? unlocked(player, true) != null : advancedRequested;
        Playstyle style = unlocked(player, advanced);
        if (style == null) {
            player.sendMessage(Component.text(formName(faction, advanced) + " is locked. See /bleach awakenings.", NamedTextColor.YELLOW));
            return;
        }
        // A changed rank configuration must not bypass the advanced form's rank gate.
        if (advanced && plugin.getRank(player.getUniqueId()) > requiredRank()) {
            player.sendMessage(Component.text(formName(faction, true) + " requires Rank " + requiredRank() + " or better.",
                    NamedTextColor.YELLOW));
            return;
        }
        var data = player.getPersistentDataContainer();
        long now = System.currentTimeMillis();
        long readyAt = data.getOrDefault(releaseReadyKey, PersistentDataType.LONG, 0L);
        if (readyAt > now) {
            player.sendMessage(Component.text("Awakened ability ready in " + ((readyAt - now + 999) / 1000) + "s.",
                    NamedTextColor.YELLOW));
            return;
        }
        Playstyle trait = storedStyle(player, advanced ? advancedTraitKey : firstTraitKey);
        releases.release(player, faction, style, trait, advanced);
        if (trait != null) {
            AwakeningPowers.applyTrait(player, trait);
        }
        // The release cooldown starts once the form would seal on its own.
        data.set(releaseReadyKey, PersistentDataType.LONG,
                now + (releases.durationSeconds(advanced) + releaseCooldown(advanced)) * 1000L);
    }

    private int releaseCooldown(boolean advanced) {
        return Math.clamp(plugin.getConfig().getInt("awakenings."
                + (advanced ? "advanced" : "first") + "-cooldown-seconds", advanced ? 95 : 40), 0, 3600);
    }

    private static String powerName(Player player, Faction faction, Playstyle style, boolean advanced) {
        return ReleaseService.form(player.getUniqueId(), faction, style, advanced).name();
    }

    private static String powerDescription(Player player, Faction faction, Playstyle style, Playstyle trait,
            boolean advanced) {
        return ReleaseService.description(player.getUniqueId(), faction, style, trait, advanced);
    }

    boolean holdsStarter(Player player) {
        Faction faction = faction(player);
        ItemStack item = player.getInventory().getItemInMainHand();
        if (faction == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        return "starter_weapon".equals(data.get(plugin.itemTypeKey(), PersistentDataType.STRING))
                && faction.name().equals(data.get(plugin.itemFactionKey(), PersistentDataType.STRING));
    }

    boolean isHostile(Entity entity) {
        return entity instanceof Enemy || entity.getPersistentDataContainer().has(plugin.bossIdKey(), PersistentDataType.STRING);
    }

    boolean hasFirstForm(Player player) {
        return unlocked(player, false) != null;
    }

    void conceal(Player player, int seconds) {
        player.getPersistentDataContainer().set(concealedUntilKey, PersistentDataType.LONG,
                System.currentTimeMillis() + seconds * 1000L);
    }

    boolean isConcealed(Player player) {
        return player.getPersistentDataContainer().getOrDefault(concealedUntilKey, PersistentDataType.LONG, 0L)
                > System.currentTimeMillis() && player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    void clearConcealment(Player player) {
        player.getPersistentDataContainer().remove(concealedUntilKey);
    }

    void onConcealedAttack(Player player, Entity target) {
        if (!isConcealed(player)) {
            return;
        }
        clearConcealment(player);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (target instanceof LivingEntity living) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        }
    }

    int[] evidence(Player player) {
        int[] stored = player.getPersistentDataContainer().get(evidenceKey, PersistentDataType.INTEGER_ARRAY);
        var evidence = new int[Habit.values().length];
        if (stored != null) {
            for (Habit habit : Habit.values()) {
                if (habit.ordinal() < stored.length) {
                    evidence[habit.ordinal()] = Math.clamp(stored[habit.ordinal()], 0, habit.cap);
                }
            }
        }
        return evidence;
    }

    private Playstyle unlocked(Player player, boolean advanced) {
        return storedStyle(player, advanced ? advancedKey : firstKey);
    }

    Playstyle storedStyle(Player player, NamespacedKey key) {
        String stored = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (stored != null) {
            try {
                return Playstyle.valueOf(stored);
            } catch (IllegalArgumentException ignored) {
                // Ignore old or invalid style IDs.
            }
        }
        return null;
    }

    Faction faction(Player player) {
        return plugin.getFaction(player.getUniqueId());
    }

    int requiredRank() {
        return Math.clamp(plugin.getConfig().getInt("awakenings.advanced-unlock-rank", 2), 1, 5);
    }

    static String formName(Faction faction, boolean advanced) {
        return switch (faction) {
            case SOUL_REAPER -> advanced ? "Bankai" : "Shikai";
            case HOLLOW -> advanced ? "Segunda Etapa" : "Resurrección";
            case QUINCY -> advanced ? "Vollständig" : "Schrift";
            case FULLBRINGER -> advanced ? "Complete Fullbring" : "Awakened Fullbring";
        };
    }
}
