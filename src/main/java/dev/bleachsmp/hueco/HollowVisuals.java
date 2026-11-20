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
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
            if (!rig.mob.isValid() || rig.mob.isDead()) {
                iterator.remove();
                rig.removePieces();
                continue;
            }
            // Vanilla refreshes the invisible flag when potion effects change.
            if (!rig.mob.isInvisible()) {
                rig.mob.setInvisible(true);
            }
            Location feet = rig.mob.getLocation();
            // Tears bring Hollows into other worlds, so check the rig's own world.
            List<Player> viewers = rig.mob.getWorld() == world ? players : rig.mob.getWorld().getPlayers();
            if (viewers.stream().anyMatch(player -> player.getLocation().distanceSquared(feet) < VIEW_DISTANCE_SQUARED)) {
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
            center.getWorld().spawnParticle(Particle.DUST, center, 11, .27, .29, .27, 0, HIT_DUST);
            center.getWorld().playSound(center, Sound.BLOCK_BONE_BLOCK_HIT, .9f, .63f);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Rig rig = rigs.remove(event.getEntity().getUniqueId());
        if (rig != null) {
            rig.removePieces();
            Location center = rig.mob.getLocation().add(0, rig.model.height() * rig.magnification * .48, 0);
            center.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 16, .4, .6, .4, .015);
            center.getWorld().playSound(center, Sound.ENTITY_WARDEN_DEATH, .8f, 1.25f);
        }
    }

    private final class Rig {
        private final LivingEntity mob;
        private final HollowModel model;
        private final boolean gillian;
        private final boolean beast;
        private final List<BlockDisplay> pieces = new ArrayList<>();
        private Location previous;
        private float stride;
        /** 0 standing still … 1 walking, eased so gaits blend instead of snapping. */
        private float gait;
        private float yaw = Float.NaN;
        private float lastStep;
        private float magnification = 1;
        private int hitUntil;

        private Rig(LivingEntity mob, HollowModel model, boolean gillian) {
            this.mob = mob;
            this.model = model;
            this.gillian = gillian;
            this.beast = model == HollowModel.forType("adjuchas");
        }

        private void updateSize() {
            if (beast && magnification != HollowModel.ADJUCHAS_MAGNIFICATION) {
                // Older Adjuchas were smaller, and some were babies; bring their hitbox up to the model.
                if (mob instanceof Husk husk) {
                    husk.setAdult();
                    var size = husk.getAttribute(Attribute.SCALE);
                    if (size != null) {
                        size.setBaseValue(HollowModel.ADJUCHAS_MOB_SCALE);
                    }
                }
                magnification = HollowModel.ADJUCHAS_MAGNIFICATION;
                removePieces();
                return;
            }
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
            // Turn like something heavy: giants swing round slowly, beasts snap but not instantly.
            float target = mob.getBodyYaw();
            if (Float.isNaN(yaw)) {
                yaw = target;
            }
            float maxTurn = gillian ? 5f : beast ? 16f : 28f;
            float delta = ((target - yaw) % 360 + 540) % 360 - 180;
            yaw += Math.max(-maxTurn, Math.min(maxTurn, delta));
            feet.setYaw(yaw);
            feet.setPitch(0);
            float movement = previous == null ? 0 : (float) Math.hypot(feet.getX() - previous.getX(),
                    feet.getZ() - previous.getZ());
            boolean moving = movement > .018f;
            gait += ((moving ? 1f : 0f) - gait) * (gillian ? .08f : .2f);
            // Stride length grows with size, so big bodies take slow, long steps instead of scurrying.
            float perBlock = gillian ? 1.6f : beast ? 3.1f : 6.5f / magnification;
            stride += Math.min(movement, .8f) * perBlock;
            float id = mob.getEntityId();
            float breath = (float) Math.sin(tick * (gillian ? .045 : .09) + id);
            float step = (float) Math.sin(stride);
            float lastStep = this.lastStep;
            this.lastStep = step;
            boolean hit = tick < hitUntil;
            float scale = magnification * (hit ? 1.025f : 1f);

            float lift;
            float bodyPitch;
            float bodyRoll;
            Vector3f bodyPivot;
            if (gillian) {
                // No legs under the shroud: it heaves forward with each step, rolls side to side and leans in.
                lift = (float) (1 - Math.cos(stride * 2)) * .05f * gait * magnification + breath * .012f * magnification;
                bodyPitch = .07f * gait + breath * .012f + (hit ? -.04f : 0);
                bodyRoll = step * .07f * gait;
                // Each footfall lands with a thud and a puff of dust: the ground feels the weight.
                if (gait > .5f && Math.signum(step) != Math.signum(lastStep) && lastStep != 0) {
                    footfall(feet, step > 0 ? 1 : -1);
                }
                bodyPivot = new Vector3f(0, 0, 0);
            } else if (beast) {
                // A loping run: the back bobs twice per stride and dips forward while it hunts.
                lift = Math.abs(step) * .05f * gait * magnification + breath * .01f * magnification;
                bodyPitch = (float) Math.sin(stride * 2) * .035f * gait + .05f * gait + (hit ? -.06f : 0);
                bodyRoll = step * .02f * gait;
                bodyPivot = new Vector3f(0, 1.0f, 0);
            } else {
                lift = Math.abs(step) * .048f * gait * magnification + breath * .022f * magnification * (1 - gait);
                bodyPitch = hit ? -.04f : 0;
                bodyRoll = 0;
                bodyPivot = new Vector3f(0, 1.2f, 0);
            }
            Quaternionf body = new Quaternionf().rotateZ(bodyRoll).rotateX(bodyPitch);

            if (pieces.isEmpty() || pieces.stream().anyMatch(piece -> !piece.isValid())) {
                removePieces();
                for (HollowModel.Piece piece : model.pieces()) {
                    pieces.add(feet.getWorld().spawn(feet, BlockDisplay.class, display -> {
                        display.setBlock(piece.material().createBlockData());
                        display.setPersistent(false);
                        display.setGravity(false);
                        display.setTeleportDuration(gillian ? 3 : 2);
                        display.setInterpolationDuration(gillian ? 3 : 2);
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
                float swing;
                if (gillian) {
                    swing = switch (piece.joint()) {
                        // Long arms hang and drift a beat behind the body, like weight on a rope.
                        case LEFT_ARM -> (float) Math.sin(stride - .7) * .10f * gait + breath * .035f;
                        case RIGHT_ARM -> -(float) Math.sin(stride - .7) * .10f * gait - breath * .035f;
                        // The feet stride out from under the shroud.
                        case LEFT_LEG -> step * .32f * gait;
                        case RIGHT_LEG -> -step * .32f * gait;
                        // The robe's back panel and hem trail behind.
                        case TAIL -> (float) Math.sin(stride - 1.1) * .06f * gait + breath * .02f;
                        case HEAD -> breath * .03f - .05f * gait;
                        default -> 0;
                    };
                } else if (beast) {
                    swing = switch (piece.joint()) {
                        // Diagonal pairs move together: front-left with back-right.
                        case LEFT_ARM, RIGHT_LEG -> step * .42f * gait;
                        case RIGHT_ARM, LEFT_LEG -> -step * .42f * gait;
                        case TAIL -> (float) Math.sin(stride * .5 - 1.2) * .2f * gait + breath * .1f;
                        // Keep the skull level and fixed on its prey while the back bobs.
                        case HEAD -> -bodyPitch * .8f + breath * .04f;
                        default -> 0;
                    };
                } else {
                    swing = switch (piece.joint()) {
                        case BODY -> 0;
                        case LEFT_ARM, RIGHT_LEG -> step * .30f * gait;
                        case RIGHT_ARM, LEFT_LEG -> -step * .30f * gait;
                        case TAIL -> breath * .11f + step * .075f * gait;
                        case HEAD -> breath * .05f;
                    };
                }
                display.setTransformation(piece.pose(scale, lift, swing, body, bodyPivot));
                display.setInterpolationDelay(0);
            }
            previous = feet;
        }

        private void footfall(Location feet, int side) {
            Vector3f offset = new Vector3f(side * .24f * magnification, 0, .3f * magnification)
                    .rotateY((float) Math.toRadians(-feet.getYaw()));
            Location at = feet.clone().add(offset.x, .1, offset.z);
            var ground = at.clone().subtract(0, .5, 0).getBlock().getBlockData();
            if (!ground.getMaterial().isAir()) {
                at.getWorld().spawnParticle(Particle.BLOCK, at, 18, .5, .05, .5, 0, ground);
            }
            at.getWorld().spawnParticle(Particle.DUST_PLUME, at, 4, .4, .02, .4, .01);
            at.getWorld().playSound(at, Sound.ENTITY_RAVAGER_STEP, 1.4f, .45f);
        }

        private void removePieces() {
            pieces.forEach(BlockDisplay::remove);
            pieces.clear();
            previous = null;
        }
    }
}
