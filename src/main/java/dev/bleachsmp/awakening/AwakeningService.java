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
    private final NamespacedKey firstKey;
    private final NamespacedKey advancedKey;
    private final NamespacedKey firstTraitKey;
    private final NamespacedKey advancedTraitKey;
    private final NamespacedKey evidenceKey;
    private final NamespacedKey visitsKey;
    private final NamespacedKey releaseReadyKey;
    private final NamespacedKey concealedUntilKey;
    private final NamespacedKey[] habitKeys = new NamespacedKey[Habit.values().length];

    public AwakeningService(BleachSmpPlugin plugin) {
        this.plugin = plugin;
        powers = new AwakeningPowers(plugin, this);
        releases = new ReleaseService(plugin, this);
        firstKey = new NamespacedKey(plugin, "first_awakening");
        advancedKey = new NamespacedKey(plugin, "advanced_awakening");
        firstTraitKey = new NamespacedKey(plugin, "first_awakening_trait");
        advancedTraitKey = new NamespacedKey(plugin, "advanced_awakening_trait");
        evidenceKey = new NamespacedKey(plugin, "awakening_evidence");
        visitsKey = new NamespacedKey(plugin, "awakening_visited_areas");
        releaseReadyKey = new NamespacedKey(plugin, "awakening_ready_at");
        concealedUntilKey = new NamespacedKey(plugin, "awakening_concealed_until");
        for (Habit habit : Habit.values()) {
            habitKeys[habit.ordinal()] = new NamespacedKey(plugin, "awakening_habit_" + habit.name().toLowerCase(Locale.ROOT));
        }
    }

    public ReleaseService releases() {
        return releases;
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
        Playstyle style = Playstyle.dominant(evidence);
        Playstyle trait = Playstyle.secondary(evidence, style);
        var data = player.getPersistentDataContainer();
        data.set(advanced ? advancedKey : firstKey, PersistentDataType.STRING, style.name());
        if (trait != null) {
            data.set(advanced ? advancedTraitKey : firstTraitKey, PersistentDataType.STRING, trait.name());
        }
        data.remove(evidenceKey);
        player.sendMessage(Component.text("Awakening unlocked: " + formName(faction(player), advanced) + " — "
                + powerName(faction(player), style, advanced) + "!", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Hold your faction starter item and press swap hands (F) to release it.",
                NamedTextColor.YELLOW));
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
        showForm(player, faction, advanced, true);
        if (advanced == null) {
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
                : powerName(faction, style, advanced) + " — " + powerDescription(faction, style, advanced)
                        + (trait == null ? "" : " Trait: " + AwakeningPowers.traitName(trait) + ".")),
                style == null ? NamedTextColor.GRAY : faction.color()));
    }

    public void release(Player player, Boolean advancedRequested) {
        Faction faction = faction(player);
        if (faction == null || !holdsStarter(player) || !canPlay(player)) {
            player.sendMessage(Component.text("Hold your own faction starter item in Survival or Adventure to release a form.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (releases.seal(player, true)) {
            // Pressing release again while a first form is out seals it early; the release cooldown starts now.
            player.getPersistentDataContainer().set(releaseReadyKey, PersistentDataType.LONG,
                    System.currentTimeMillis() + releaseCooldown(false) * 1000L);
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
        if (!advanced) {
            releases.release(player, faction, style);
            if (trait != null) {
                AwakeningPowers.applyTrait(player, trait);
            }
            // The release cooldown starts once the form would seal on its own.
            data.set(releaseReadyKey, PersistentDataType.LONG,
                    now + (releases.durationSeconds() + releaseCooldown(false)) * 1000L);
            return;
        }
        if (!powers.cast(player, faction, style, advanced)) {
            return;
        }
        if (trait != null) {
            AwakeningPowers.applyTrait(player, trait);
        }
        data.set(releaseReadyKey, PersistentDataType.LONG, now + releaseCooldown(advanced) * 1000L);
        player.sendMessage(Component.text(formName(faction, advanced) + " — " + powerName(faction, style, advanced) + "!",
                faction.color()));
    }

    private int releaseCooldown(boolean advanced) {
        return Math.clamp(plugin.getConfig().getInt("awakenings."
                + (advanced ? "advanced" : "first") + "-cooldown-seconds", advanced ? 95 : 40), 0, 3600);
    }

    private static String powerName(Faction faction, Playstyle style, boolean advanced) {
        return !advanced ? ReleaseService.form(faction, style).name() : AwakeningPowers.name(style, advanced);
    }

    private static String powerDescription(Faction faction, Playstyle style, boolean advanced) {
        return !advanced ? ReleaseService.description(faction, style) : AwakeningPowers.description(style, advanced);
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

    private int[] evidence(Player player) {
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

    private Playstyle storedStyle(Player player, NamespacedKey key) {
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

    private Faction faction(Player player) {
        return plugin.getFaction(player.getUniqueId());
    }

    private int requiredRank() {
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
