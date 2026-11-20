package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

/** The advanced awakening scene; fight eligibility and meter remain in CrisisService. */
final class BankaiCrisis {
    private record Scene(BukkitTask task, Set<UUID> invulnerable) { }
    private record Night(BukkitTask task, World world) { }

    private final BleachSmpPlugin plugin;
    private final AwakeningService awakenings;
    private final Map<UUID, Scene> scenes = new HashMap<>();
    private final Map<UUID, Night> nights = new HashMap<>();

    BankaiCrisis(BleachSmpPlugin plugin, AwakeningService awakenings) {
        this.plugin = plugin;
        this.awakenings = awakenings;
    }

    boolean start(Player player, Set<UUID> invulnerable) {
        UUID id = player.getUniqueId();
        var kit = awakenings.bankai().pendingKit(player);
        Faction faction = awakenings.faction(player);
        if (kit == null || faction == null || !invulnerable.add(id)) {
            return false;
        }
        var form = ReleaseService.form(id, faction, kit.style(), true);
        Location at = player.getLocation();
        World world = at.getWorld();
        awakenings.releases().seal(player, false);
        player.setVelocity(new Vector());
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        for (Player nearby : at.getNearbyPlayers(96)) {
            if (nearby.getLocation().distanceSquared(at) <= 96 * 96) {
                setNight(nearby);
            }
        }
        String[] lines = SpiritFight.voice(faction, 4);
        SpiritFight.subtitle(player, lines[0]);
        BukkitTask task = new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !world.equals(player.getWorld())) {
                    end(id);
                    return;
                }
                if (tick == 30) {
                    SpiritFight.subtitle(player, lines[1]);
                    Vfx.sound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.2f, 0.55f);
                }
                if (tick % 4 == 0) {
                    column(player.getLocation(), form);
                }
                if (tick >= 80) {
                    if (awakenings.bankai().awaken(player, kit)) {
                        player.setHealth(InnerWorld.maxHealth(player) * 0.6);
                        awakenings.releaseAdvancedAwakened(player, faction, kit.style(), kit.trait());
                        Bukkit.broadcast(Component.text("⚔ " + player.getName() + " has achieved "
                                + AwakeningService.formName(faction, true) + ": " + form.name() + ".", form.text()));
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.9f);
                        }
                    }
                    end(id);
                    return;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 1, 1);
        scenes.put(id, new Scene(task, invulnerable));
        return true;
    }

    private void setNight(Player player) {
        UUID id = player.getUniqueId();
        Night previous = nights.remove(id);
        if (previous != null) {
            previous.task().cancel();
        }
        player.setPlayerTime(18000, false);
        BukkitTask reset = Bukkit.getScheduler().runTaskLater(plugin, () -> clearNight(player), 120);
        nights.put(id, new Night(reset, player.getWorld()));
    }

    private void clearNight(Player player) {
        Night night = nights.remove(player.getUniqueId());
        if (night != null) {
            night.task().cancel();
            if (night.world().equals(player.getWorld())) {
                player.resetPlayerTime();
            }
        }
    }

    private static void column(Location at, ReleaseService.Form form) {
        var dust = Vfx.dust(form.color(), 2.2f);
        var world = at.getWorld();
        for (int y = 0; y <= 40; y += 2) {
            Location point = at.clone().add(0, y, 0);
            world.spawnParticle(Particle.DUST, point, 6, 0.45, 0.85, 0.45, 0, dust, true);
            world.spawnParticle(Particle.END_ROD, point, 2, 0.23, 0.9, 0.23, 0.02, null, true);
        }
    }

    private void end(UUID id) {
        Scene scene = scenes.remove(id);
        if (scene != null) {
            scene.task().cancel();
            scene.invulnerable().remove(id);
        }
    }

    void onQuit(Player player) {
        end(player.getUniqueId());
        clearNight(player);
    }

    void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            onQuit(player);
        }
        for (UUID id : Set.copyOf(scenes.keySet())) {
            end(id);
        }
        nights.values().forEach(night -> night.task().cancel());
        nights.clear();
    }
}
