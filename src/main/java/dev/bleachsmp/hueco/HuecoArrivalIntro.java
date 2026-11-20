package dev.bleachsmp.hueco;

import dev.bleachsmp.BleachSmpPlugin;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

final class HuecoArrivalIntro implements Listener {
    private static final int CAMERA_WARMUP_TICKS = 12;
    private static final int TRACKING_TIMEOUT_TICKS = 100;
    private static final int FRAME_TICKS = 3;
    private static final int SHOT_TICKS = 108;
    private static final Title ARRIVAL_TITLE = Title.title(
            Component.text("HUECO MUNDO", NamedTextColor.WHITE),
            Component.text("The Severed Gate", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(450), Duration.ofMillis(2400), Duration.ofMillis(850)));

    private final BleachSmpPlugin plugin;
    private final NamespacedKey seenKey;
    private final Map<UUID, Shot> active = new HashMap<>();

    HuecoArrivalIntro(BleachSmpPlugin plugin) {
        this.plugin = plugin;
        seenKey = new NamespacedKey(plugin, "hueco_arrival_intro_seen");
    }

    boolean isPlaying(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    void playOnFirstArrival(Player player) {
        if (!plugin.getConfig().getBoolean("hueco-mundo.arrival-cutscene", true) || isPlaying(player)
                || player.getPersistentDataContainer().has(seenKey, PersistentDataType.BOOLEAN)) {
            return;
        }

        Location arrival = player.getLocation();
        Location eye = player.getEyeLocation();
        ItemDisplay camera = arrival.getWorld().spawn(frame(eye, 0), ItemDisplay.class, display -> {
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setTeleportDuration(FRAME_TICKS);
        });
        player.showEntity(plugin, camera);
        Shot shot = new Shot(player, arrival, eye, camera);
        active.put(player.getUniqueId(), shot);
        shot.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> advance(shot), CAMERA_WARMUP_TICKS, FRAME_TICKS);
    }

    private void advance(Shot shot) {
        Player player = shot.player;
        if (!player.isOnline() || player.isDead() || !player.getWorld().equals(shot.arrival.getWorld())
                || !shot.camera.isValid()) {
            finish(shot, false);
            return;
        }
        if (!shot.started) {
            // The client ignores the camera packet until it has received the display entity.
            if (player.getLocation().distanceSquared(shot.arrival) > 9.0) {
                finish(shot, false);
                return;
            }
            if (!shot.camera.isTrackedBy(player)) {
                shot.waited += FRAME_TICKS;
                if (shot.waited >= TRACKING_TIMEOUT_TICKS) {
                    finish(shot, false);
                }
                return;
            }
            shot.mode = player.getGameMode();
            shot.allowedFlight = player.getAllowFlight();
            shot.flying = player.isFlying();
            // Save before taking over the camera so even an interrupted viewing won't replay.
            player.getPersistentDataContainer().set(seenKey, PersistentDataType.BOOLEAN, true);
            player.saveData();
            player.setGameMode(GameMode.SPECTATOR);
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(shot.camera);
            }
            shot.started = true;
            if (!shot.camera.equals(player.getSpectatorTarget())) {
                player.getPersistentDataContainer().remove(seenKey);
                finish(shot, true);
                player.saveData();
                return;
            }
            player.playSound(shot.camera.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD,
                    SoundCategory.AMBIENT, 0.85f, 0.8f);
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR || !shot.camera.equals(player.getSpectatorTarget())
                || shot.elapsed >= SHOT_TICKS) {
            finish(shot, true);
            return;
        }

        shot.elapsed += FRAME_TICKS;
        if (shot.elapsed == 12) {
            player.showTitle(ARRIVAL_TITLE);
            shot.showedTitle = true;
        }
        if (!shot.camera.teleport(frame(shot.eye, (double) shot.elapsed / SHOT_TICKS))) {
            finish(shot, true);
        }
    }

    private static Location frame(Location eye, double progress) {
        // Sweep in from the east above the lanterns, then settle into the normal arrival view.
        double t = progress * progress * (3 - 2 * progress);
        double u = 1 - t;
        Location camera = eye.clone().add(17 * u * u + 18 * u * t, 18 * u * u + 16 * u * t,
                13 * u * u - 8 * u * t);
        var gate = HuecoMundoLayout.GATE;
        Location focus = new Location(eye.getWorld(), gate.x() + 0.5, eye.getY() + 23 * u, gate.z() - 12);
        return camera.setDirection(focus.subtract(camera).toVector());
    }

    private void finish(Shot shot, boolean returnToArrival) {
        Player player = shot.player;
        active.remove(player.getUniqueId());
        shot.task.cancel();
        if (shot.started) {
            if (shot.camera.equals(player.getSpectatorTarget())) {
                player.setSpectatorTarget(null);
            }
            if (returnToArrival && !player.isDead() && player.getWorld().equals(shot.arrival.getWorld())) {
                player.teleport(shot.arrival);
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(shot.mode);
                if (player.getGameMode() == shot.mode) {
                    player.setAllowFlight(shot.allowedFlight);
                    player.setFlying(shot.flying);
                }
            }
            if (shot.showedTitle) {
                player.clearTitle();
            }
        }
        shot.camera.remove();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Shot shot = active.get(event.getPlayer().getUniqueId());
        if (shot != null) {
            finish(shot, true);
        }
    }

    void shutdown() {
        for (Shot shot : List.copyOf(active.values())) {
            finish(shot, true);
        }
    }

    private static final class Shot {
        private final Player player;
        private final Location arrival;
        private final Location eye;
        private final ItemDisplay camera;
        private BukkitTask task;
        private GameMode mode;
        private boolean allowedFlight;
        private boolean flying;
        private boolean started;
        private boolean showedTitle;
        private int waited;
        private int elapsed;

        private Shot(Player player, Location arrival, Location eye, ItemDisplay camera) {
            this.player = player;
            this.arrival = arrival;
            this.eye = eye;
            this.camera = camera;
        }
    }
}
