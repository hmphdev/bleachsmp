package dev.bleachsmp.hueco;

import dev.bleachsmp.BleachSmpPlugin;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** The server mob remains the sole hitbox and owner of AI, loot and progression. */
final class HollowVisuals implements Listener {
    private static final ItemStack AIR = new ItemStack(Material.AIR);
    private static final List<EquipmentSlot> VISIBLE_SLOTS = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final Particle.DustOptions HIT_DUST = new Particle.DustOptions(Color.fromRGB(166, 24, 51), .9f);
    private static final double VIEW_DISTANCE_SQUARED = 48 * 48;

    private final BleachSmpPlugin plugin;
    private final World world;
    private final NamespacedKey typeKey;
    private final Map<UUID, Rig> rigs = new HashMap<>();
    private int tick;

    HollowVisuals(BleachSmpPlugin plugin, World world, NamespacedKey typeKey) {
        this.plugin = plugin;
        this.world = world;
        this.typeKey = typeKey;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, 1, 2);
    }

    void track(LivingEntity mob, String type) {
        HollowModel model = HollowModel.forType(type);
        if (model != null && mob.isValid() && !rigs.containsKey(mob.getUniqueId())) {
            Rig rig = new Rig(mob, model, HollowModel.GILLIAN_TYPE.equals(type));
            rig.updateSize();
            rigs.put(mob.getUniqueId(), rig);
            mob.setInvisible(true);
            mob.setCustomNameVisible(false);
            mob.setSilent(true);
            sendEquipment(mob, mob.getTrackedBy(), false);
        }
    }

    void shutdown() {
        for (Rig rig : rigs.values()) {
            rig.removePieces();
            if (rig.mob.isValid()) {
                rig.mob.setInvisible(false);
                rig.mob.setCustomNameVisible(true);
                rig.mob.setSilent(false);
                sendEquipment(rig.mob, rig.mob.getTrackedBy(), true);
            }
        }
        rigs.clear();
    }

    private void update() {
        // Also finds existing bosses restored from the world save or loaded with a chunk.
        List<Player> players = world.getPlayers();
        if (tick % 20 == 0 && !players.isEmpty()) {
            for (LivingEntity mob : world.getLivingEntities()) {
                String type = mob.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
                if (type != null) {
                    track(mob, type);
                }
            }
        }
        tick += 2;
        var iterator = rigs.values().iterator();
        while (iterator.hasNext()) {
            Rig rig = iterator.next();
            if (!rig.mob.isValid() || rig.mob.isDead() || rig.mob.getWorld() != world) {
                iterator.remove();
                rig.removePieces();
                continue;
            }
            // Vanilla refreshes the invisible flag when potion effects change.
            if (!rig.mob.isInvisible()) {
                rig.mob.setInvisible(true);
            }
            Location feet = rig.mob.getLocation();
            if (players.stream().anyMatch(player -> player.getLocation().distanceSquared(feet) < VIEW_DISTANCE_SQUARED)) {
                if (tick % 20 == 0) {
                    rig.updateSize();
                }
                rig.update(feet, tick);
            } else {
                rig.removePieces();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTrack(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity mob && rigs.containsKey(mob.getUniqueId())) {
            // The vanilla equipment packet is sent after the tracking event.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline() && mob.isValid() && rigs.containsKey(mob.getUniqueId())) {
                    sendEquipment(mob, List.of(event.getPlayer()), false);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        LivingEntity mob = event.getEntity();
        if (rigs.containsKey(mob.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (mob.isValid() && rigs.containsKey(mob.getUniqueId())) {
                    sendEquipment(mob, mob.getTrackedBy(), false);
                }
            });
        }
    }

    private void sendEquipment(LivingEntity mob, Iterable<Player> players, boolean restore) {
        var gear = mob.getEquipment();
        if (gear == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : VISIBLE_SLOTS) {
            ItemStack item = gear.getItem(slot);
            if (!item.getType().isAir()) {
                items.put(slot, restore ? item : AIR);
            }
        }
        if (!items.isEmpty()) {
            players.forEach(player -> player.sendEquipmentChange(mob, items));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHurt(EntityDamageEvent event) {
        Rig rig = rigs.get(event.getEntity().getUniqueId());
        if (rig != null && event.getFinalDamage() > 0) {
            rig.hitUntil = tick + 8;
            Location center = rig.mob.getLocation().add(0, rig.model.height() * rig.magnification * .48, 0);
            world.spawnParticle(Particle.DUST, center, 11, .27, .29, .27, 0, HIT_DUST);
            world.playSound(center, Sound.BLOCK_BONE_BLOCK_HIT, .9f, .63f);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Rig rig = rigs.remove(event.getEntity().getUniqueId());
        if (rig != null) {
            rig.removePieces();
            Location center = rig.mob.getLocation().add(0, rig.model.height() * rig.magnification * .48, 0);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 16, .4, .6, .4, .015);
            world.playSound(center, Sound.ENTITY_WARDEN_DEATH, .8f, 1.25f);
        }
    }

    private final class Rig {
        private final LivingEntity mob;
        private final HollowModel model;
        private final boolean gillian;
        private final List<BlockDisplay> pieces = new ArrayList<>();
        private Location previous;
        private float stride;
        private float magnification = 1;
        private int hitUntil;

        private Rig(LivingEntity mob, HollowModel model, boolean gillian) {
            this.mob = mob;
            this.model = model;
            this.gillian = gillian;
        }

        private void updateSize() {
            if (!gillian || magnification == HollowModel.GILLIAN_MAGNIFICATION
                    || !(mob instanceof Husk husk)) {
                return;
            }
            var scale = husk.getAttribute(Attribute.SCALE);
            if (scale == null) {
                return;
            }
            // Let older, smaller Gillians grow only once there is enough space around them.
            boolean fullSize = husk.isAdult() && scale.getBaseValue() == HollowModel.GILLIAN_MOB_SCALE;
            if (!fullSize && HuecoMundoService.hasGillianClearance(husk.getLocation())) {
                husk.setAdult();
                scale.setBaseValue(HollowModel.GILLIAN_MOB_SCALE);
                fullSize = true;
            }
            float size = fullSize ? HollowModel.GILLIAN_MAGNIFICATION : (float) (husk.getHeight() / model.height());
            if (magnification != size) {
                magnification = size;
                removePieces();
            }
        }

        private void update(Location feet, int tick) {
            feet.setYaw(mob.getBodyYaw());
            feet.setPitch(0);
            float movement = previous == null ? 0 : (float) Math.hypot(feet.getX() - previous.getX(),
                    feet.getZ() - previous.getZ());
            boolean moving = movement > .018f;
            if (moving) {
                stride += Math.min(movement, .8f) * 6.5f;
            }
            float pulse = (float) Math.sin(tick * .10 + mob.getEntityId());
            float step = moving ? (float) Math.sin(stride) : pulse * .12f;
            float lift = (moving ? Math.abs(step) * .048f : pulse * .022f) * magnification;
            float scale = magnification * (tick < hitUntil ? 1.025f : 1f);
            if (pieces.isEmpty() || pieces.stream().anyMatch(piece -> !piece.isValid())) {
                removePieces();
                for (HollowModel.Piece piece : model.pieces()) {
                    pieces.add(world.spawn(feet, BlockDisplay.class, display -> {
                        display.setBlock(piece.material().createBlockData());
                        display.setPersistent(false);
                        display.setGravity(false);
                        display.setTeleportDuration(2);
                        display.setInterpolationDuration(2);
                        display.setViewRange(1);
                        display.setDisplayWidth(4 * magnification);
                        display.setDisplayHeight((model.height() + .4f) * magnification);
                        if (piece.luminous()) {
                            display.setBrightness(new Display.Brightness(15, 15));
                        }
                        display.setTransformation(piece.pose(magnification, 0, 0));
                    }));
                }
                previous = null;
            }
            boolean moved = !feet.equals(previous);
            for (int i = 0; i < pieces.size(); i++) {
                BlockDisplay display = pieces.get(i);
                HollowModel.Piece piece = model.pieces().get(i);
                if (moved) {
                    display.teleport(feet);
                }
                float swing = switch (piece.joint()) {
                    case BODY -> 0;
                    case LEFT_ARM, RIGHT_LEG -> step * .30f;
                    case RIGHT_ARM, LEFT_LEG -> -step * .30f;
                    case TAIL -> pulse * .11f + step * .075f;
                };
                display.setTransformation(piece.pose(scale, lift, swing));
                display.setInterpolationDelay(0);
            }
            previous = feet;
        }

        private void removePieces() {
            pieces.forEach(BlockDisplay::remove);
            pieces.clear();
            previous = null;
        }
    }
}
