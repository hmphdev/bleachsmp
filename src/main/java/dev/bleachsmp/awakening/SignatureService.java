package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.awakening.ReleaseService.Release;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Signature moves: one per form, 48 in all, used with Q (drop) on the starter item while released. Unlike techniques,
 * no two forms share a signature. Advanced forms use an empowered version (longer, wider, harder).
 */
public final class SignatureService implements Listener {
    // ---------------------------------------------------------------- names

    /** [faction][variant][playstyle]: name and one-line description. */
    private static final String[][][][] TABLE = {
        { // SOUL_REAPER
            {
                {"Blood Moon", "mark an enemy; your hits on it heal you, and the mark bursts when it fades"},
                {"Heat Haze Double", "leave a decoy of yourself and vanish; the decoy explodes in darkness when struck"},
                {"Meteor Lance", "charge, then fire a piercing lance that hits harder the farther it flies"},
                {"Updraft", "a wind column flings nearby enemies up and lifts you; your next plunging hit lands hard"},
                {"Iron Wall", "raise a wall that destroys incoming projectiles and shoves enemies back"},
                {"Frozen Shrine", "encase yourself in ice, invulnerable and healing, then shatter it to freeze enemies"}},
            {
                {"Inferno Blade", "your melee hits explode in fire for a few seconds"},
                {"Thousand Petals", "steer a cloud of blade petals with your crosshair"},
                {"Rain Prison", "a raincloud traps an area, slowing enemies and striking them with water bolts"},
                {"Thunder Step", "three lightning blinks; press Q again to step, cutting everything you pass"},
                {"Toxic Spire", "plant a spire that pulses poison and drags enemies toward it"},
                {"Bell of Binding", "a bell silences nearby enemies' techniques and signatures"}}},
        { // HOLLOW
            {
                {"Feral Hunt", "pay health to frenzy: speed, and every melee hit heals and stacks strength"},
                {"Mirage Swap", "swap places with an enemy and blind it"},
                {"Black Talon Cero", "root yourself and fire a huge piercing cero"},
                {"Sonido Storm", "flash around a target, striking it from every side"},
                {"Magma Fist", "a line of magma eruptions that launches and burns"},
                {"Shell Reflect", "a shell that reflects projectiles and punishes melee attackers"}},
            {
                {"Shark Dive", "dive into the ground and burst up under a target"},
                {"Fading Shade", "vanish from enemy sight; your first hit out of it lands hard"},
                {"Stinger", "shoot a stinger; the venom detonates, stronger for every hit you land meanwhile"},
                {"Eagle Dive", "soar and steer through the air, then crash down with lightning"},
                {"Bone Cage", "cage an enemy in bone; it cannot leave"},
                {"Web of Frost", "throw a frozen web that slows enemies and shields allies"}}},
        { // QUINCY
            {
                {"Blitz Volley", "a rapid stream of reishi bolts that follows your aim"},
                {"Mirror Feint", "the next hit on you misses, and you strike back from behind the attacker"},
                {"Sniper Star", "charge a 60-block shot; headshots deal extra damage"},
                {"Hirenkyaku Glide", "fly on reishi platforms for a few seconds"},
                {"Resonant Bastion", "a dome that destroys projectiles and wears down enemies inside"},
                {"Blossom Haven", "a blossom field that heals allies and pushes enemies out"}},
            {
                {"Blood Debt", "store the damage you take, then release it in a blast that heals you"},
                {"Mirror Theft", "steal an enemy's buffs"},
                {"Tidal Wave", "a wall of water that sweeps enemies away"},
                {"Sunburst Halo", "orbiting suns that explode on enemies who come close"},
                {"Pillar Vault", "ride a rising pillar, then topple it onto the enemies in front"},
                {"Soul Tether", "chain an enemy: it cannot run, and shares the damage you take"}}},
        { // FULLBRINGER
            {
                {"Charge Punch", "charge a punch (Q again to throw it); more charge, more damage"},
                {"Mirror Coat", "melee attackers take back most of the damage they deal you"},
                {"Echo Mark", "your next three hits echo a second later"},
                {"Sky Steps", "jump again and again in mid-air"},
                {"Iron Construct", "build an iron construct that fights for you"},
                {"Heart Link", "link to an ally: you share damage and both regenerate"}},
            {
                {"Rewind", "mark this moment; Q again (or wait) to return to it, with your health from then"},
                {"Shuffle", "every enemy around you, and you, swap places at random"},
                {"Echo Turret", "a floating turret that fires at nearby enemies"},
                {"Meteor Kick", "dash-kick an enemy into the air and stomp it back down"},
                {"Molten Floor", "melt the ground: enemies burn, allies get speed and fire resistance"},
                {"Last Line", "the next lethal hit on you or a nearby ally is stopped"}}}};

    static String name(Faction faction, Playstyle style, int variant) {
        return TABLE[faction.ordinal()][variant & 1][style.ordinal()][0];
    }

    static String description(Faction faction, Playstyle style, int variant) {
        return TABLE[faction.ordinal()][variant & 1][style.ordinal()][1];
    }

    // ---------------------------------------------------------------- state

    private record Cast(Player p, Release r, double amp, Element el, Color color) {
    }

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    private final ReleaseService releases;
    private final NamespacedKey markerKey;
    /** Timed flags per player: buff name → expiry. */
    private final Map<UUID, Map<String, Long>> buffs = new HashMap<>();
    /** A pending "press Q again" action and when it expires. */
    private final Map<UUID, Runnable> recasts = new HashMap<>();
    private final Map<UUID, Long> recastUntil = new HashMap<>();
    private final Map<UUID, Long> silencedUntil = new HashMap<>();
    private final Map<UUID, Integer> counters = new HashMap<>();
    private final Map<UUID, Double> stored = new HashMap<>();
    /** Marked or tethered targets: caster → target. */
    private final Map<UUID, UUID> targets = new HashMap<>();
    /** Linked partner for Heart Link (both directions). */
    private final Map<UUID, UUID> links = new HashMap<>();
    /** Venom: target → caster, and the hits landed while it lasts. */
    private final Map<UUID, UUID> venom = new HashMap<>();
    private final Map<UUID, Integer> venomHits = new HashMap<>();
    private final Map<UUID, UUID> golems = new HashMap<>();
    private final Map<UUID, UUID> decoys = new HashMap<>();
    private final Map<UUID, Set<UUID>> hiddenFrom = new HashMap<>();
    private final Set<UUID> flying = new HashSet<>();
    private final Set<Entity> spawned = new HashSet<>();
    private boolean echoing;
    private boolean sharing;

    SignatureService(BleachSmpPlugin plugin, AwakeningService service, ReleaseService releases) {
        this.plugin = plugin;
        this.service = service;
        this.releases = releases;
        markerKey = new NamespacedKey(plugin, "signature_entity");
    }

    private Map<String, Long> buffs(UUID id) {
        return buffs.computeIfAbsent(id, key -> new HashMap<>());
    }

    private void give(Player player, String buff, long millis) {
        buffs(player.getUniqueId()).put(buff, System.currentTimeMillis() + millis);
    }

    private boolean has(Entity entity, String buff) {
        Map<String, Long> map = buffs.get(entity.getUniqueId());
        return map != null && map.getOrDefault(buff, 0L) > System.currentTimeMillis();
    }

    private void clear(Entity entity, String buff) {
        Map<String, Long> map = buffs.get(entity.getUniqueId());
        if (map != null) {
            map.remove(buff);
        }
    }

    boolean silenced(Player player) {
        return silencedUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- input

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Release release = releases.get(player);
        if (release == null || !service.isOwnStarter(player, event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        use(player, release);
    }

    private void use(Player player, Release release) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Runnable recast = recasts.remove(id);
        if (recast != null && recastUntil.getOrDefault(id, 0L) > now) {
            recastUntil.remove(id);
            recast.run();
            return;
        }
        String name = name(release.faction, release.style, release.variant);
        if (silenced(player)) {
            player.sendActionBar(Component.text("Silenced!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            return;
        }
        if (release.signatureReadyAt > now) {
            player.sendActionBar(Component.text(name + " ready in " + ((release.signatureReadyAt - now + 999) / 1000)
                    + "s", NamedTextColor.YELLOW));
            return;
        }
        Cast cast = new Cast(player, release, release.advanced ? 1.5 : 1.0, release.element, release.form.color());
        if (!cast(cast)) {
            return;
        }
        int cooldown = Math.clamp(plugin.getConfig().getInt("awakenings." + (release.advanced ? "advanced-release" : "release")
                + ".signature-cooldown-seconds", release.advanced ? 14 : 20), 0, 600);
        release.signatureReadyAt = now + Math.round(cooldown * 1000L * service.pressure().cooldownFactor(player));
        player.sendActionBar(Component.text(name + "!", release.form.text(), TextDecoration.BOLD));
        release.element.castSound(player.getLocation(), release.advanced);
        Vfx.sound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.4f, 1.6f);
    }

    private boolean cast(Cast c) {
        Release r = c.r;
        int key = r.faction.ordinal() * 100 + r.variant * 10 + r.style.ordinal();
        return switch (key) {
            case 0 -> bloodMoon(c);
            case 1 -> heatHaze(c);
            case 2 -> meteorLance(c);
            case 3 -> updraft(c);
            case 4 -> ironWall(c);
            case 5 -> frozenShrine(c);
            case 10 -> infernoBlade(c);
            case 11 -> thousandPetals(c);
            case 12 -> rainPrison(c);
            case 13 -> thunderStep(c);
            case 14 -> toxicSpire(c);
            case 15 -> bellOfBinding(c);
            case 100 -> feralHunt(c);
            case 101 -> mirageSwap(c);
            case 102 -> blackTalon(c);
            case 103 -> sonidoStorm(c);
            case 104 -> magmaFist(c);
            case 105 -> shellReflect(c);
            case 110 -> sharkDive(c);
            case 111 -> fadingShade(c);
            case 112 -> stinger(c);
            case 113 -> eagleDive(c);
            case 114 -> boneCage(c);
            case 115 -> webOfFrost(c);
            case 200 -> blitzVolley(c);
            case 201 -> mirrorFeint(c);
            case 202 -> sniperStar(c);
            case 203 -> glide(c);
            case 204 -> bastion(c);
            case 205 -> blossomHaven(c);
            case 210 -> bloodDebt(c);
            case 211 -> mirrorTheft(c);
            case 212 -> tidalWave(c);
            case 213 -> sunburstHalo(c);
            case 214 -> pillarVault(c);
            case 215 -> soulTether(c);
            case 300 -> chargePunch(c);
            case 301 -> mirrorCoat(c);
            case 302 -> echoMark(c);
            case 303 -> skySteps(c);
            case 304 -> ironConstruct(c);
            case 305 -> heartLink(c);
            case 310 -> rewind(c);
            case 311 -> shuffle(c);
            case 312 -> echoTurret(c);
            case 313 -> meteorKick(c);
            case 314 -> moltenFloor(c);
            case 315 -> lastLine(c);
            default -> false;
        };
    }

    // ---------------------------------------------------------------- helpers

    private void strike(Cast c, LivingEntity target, double amount) {
        if (target.isValid() && !target.isDead()) {
            releases.strike(c.p, target, amount, c.r);
        }
    }

    private boolean enemy(Cast c, Entity entity) {
        return entity instanceof LivingEntity living && service.canStrike(c.p, living);
    }

    private List<LivingEntity> enemies(Cast c, Location at, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity entity : at.getNearbyLivingEntities(radius)) {
            if (service.canStrike(c.p, entity)) {
                list.add(entity);
            }
        }
        return list;
    }

    private List<Player> allies(Player player, Location at, double radius) {
        List<Player> list = new ArrayList<>();
        for (Player other : at.getNearbyPlayers(radius)) {
            if (other.equals(player) || AwakeningPowers.allies(player, other)) {
                list.add(other);
            }
        }
        return list;
    }

    private LivingEntity aimed(Cast c, double range) {
        RayTraceResult result = c.p.getWorld().rayTraceEntities(c.p.getEyeLocation(), c.p.getEyeLocation().getDirection(),
                range, 0.7, entity -> entity instanceof LivingEntity && !entity.equals(c.p));
        if (result != null && result.getHitEntity() instanceof LivingEntity target && service.canStrike(c.p, target)) {
            return target;
        }
        c.p.sendActionBar(Component.text("No enemy in sight.", NamedTextColor.GRAY));
        return null;
    }

    /** The block the player is aiming at, or the point at full range. */
    private static Location aimPoint(Player player, double range) {
        RayTraceResult result = player.rayTraceBlocks(range);
        Location point = result != null ? result.getHitPosition().toLocation(player.getWorld())
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(range));
        Location ground = ReleaseService.groundAt(point);
        return ground != null ? ground : point;
    }

    private static Vector flat(Player player) {
        Vector v = player.getLocation().getDirection().setY(0);
        return v.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : v.normalize();
    }

    private static boolean standable(Location at) {
        return at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable();
    }

    /** Runs {@code body} every {@code period} ticks for {@code ticks} ticks while the caster stays online. */
    private void every(Player caster, int ticks, int period, IntConsumer body) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                // The body always sees a final call at exactly {@code ticks}, which is where effects clean up.
                int current = caster.isOnline() ? Math.min(tick, ticks) : ticks;
                try {
                    body.accept(current);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Signature effect failed: " + exception);
                    cancel();
                    return;
                }
                if (current >= ticks) {
                    cancel();
                    return;
                }
                tick += period;
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    private void later(int ticks, Runnable action) {
        plugin.getServer().getScheduler().runTaskLater(plugin, action, ticks);
    }

    private void recast(Player player, long millis, Runnable action) {
        recasts.put(player.getUniqueId(), action);
        recastUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    private <T extends Entity> T track(T entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        spawned.add(entity);
        return entity;
    }

    private void remove(Entity entity) {
        spawned.remove(entity);
        if (entity.isValid()) {
            entity.remove();
        }
    }

    private BlockDisplay block(Location at, Material material, float x, float y, float z) {
        return track(at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(-x / 2, 0, -z / 2), new Quaternionf(),
                    new Vector3f(x, y, z), new Quaternionf()));
            display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        }));
    }

    private static ItemStack plain(ItemStack item) {
        return item == null || item.isEmpty() ? null : new ItemStack(item.getType());
    }

    /** Decoys and constructs never drop anything: no duplicated gear, no free iron. */
    @EventHandler
    public void onSpawnedDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(markerKey)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            spawned.remove(event.getEntity());
            decoys.remove(event.getEntity().getUniqueId());
            golems.remove(event.getEntity().getUniqueId());
        }
    }

    private static void heal(Player player, double amount) {
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(max == null ? 20.0 : max.getValue(), Math.max(0, player.getHealth()) + amount));
    }

    /** Hides a player from every non-allied player and drops mobs' aggro on them. */
    private void hide(Player player, int ticks) {
        Set<UUID> from = hiddenFrom.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player) && !AwakeningPowers.allies(player, other)) {
                other.hidePlayer(plugin, player);
                from.add(other.getUniqueId());
            }
        }
        for (Entity entity : player.getNearbyEntities(24, 12, 24)) {
            if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
        later(ticks, () -> reveal(player));
    }

    private void reveal(Player player) {
        Set<UUID> from = hiddenFrom.remove(player.getUniqueId());
        if (from == null) {
            return;
        }
        for (UUID id : from) {
            Player other = plugin.getServer().getPlayer(id);
            if (other != null) {
                other.showPlayer(plugin, player);
            }
        }
    }

    private static void ring(Location at, double radius, Color color) {
        Vfx.ring(at, radius, (int) Math.max(12, radius * 8), color, 1.2f);
    }

    // ================================================================ SOUL REAPER

    private boolean bloodMoon(Cast c) {
        LivingEntity target = aimed(c, 20);
        if (target == null) {
            return false;
        }
        UUID id = c.p.getUniqueId();
        targets.put(id, target.getUniqueId());
        counters.put(id, 0);
        give(c.p, "blood_moon", (long) (8000 * c.amp));
        Vfx.sound(target.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 1.6f);
        int ticks = (int) (160 * c.amp);
        every(c.p, ticks, 5, tick -> {
            if (!target.isValid() || target.isDead() || tick >= ticks) {
                if (targets.remove(id) != null) {
                    int hits = counters.getOrDefault(id, 0);
                    Location at = target.getLocation().add(0, 1, 0);
                    Vfx.burst(at, Color.fromRGB(150, 0, 20), 60, 0.8, 2.0f);
                    Vfx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
                    if (target.isValid() && !target.isDead()) {
                        strike(c, target, Math.min(12, 2 + 1.5 * hits));
                    }
                }
                return;
            }
            Location head = target.getLocation().add(0, target.getHeight() + 0.8, 0);
            Vfx.ring(head, 0.5, 14, Color.fromRGB(200, 10, 30), 1.2f);
            head.getWorld().spawnParticle(Particle.DUST, head, 3, 0.15, 0.15, 0.15, 0,
                    Vfx.dust(Color.fromRGB(255, 40, 40), 1.6f));
        });
        return true;
    }

    private boolean heatHaze(Cast c) {
        Player p = c.p;
        Location at = p.getLocation();
        ArmorStand decoy = track(at.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.customName(Component.text(p.getName()));
            stand.setCustomNameVisible(true);
            EntityEquipment from = p.getEquipment();
            EntityEquipment to = stand.getEquipment();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(p);
                head.setItemMeta(skull);
            }
            // Plain copies (type only, no enchantments or data); drops are cleared on death anyway.
            to.setHelmet(head);
            to.setChestplate(plain(from.getChestplate()));
            to.setLeggings(plain(from.getLeggings()));
            to.setBoots(plain(from.getBoots()));
            to.setItemInMainHand(plain(from.getItemInMainHand()));
            for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                try {
                    stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                } catch (IllegalArgumentException ignored) {
                    // Body and saddle slots don't apply to armour stands.
                }
            }
        }));
        decoys.put(decoy.getUniqueId(), p.getUniqueId());
        int ticks = (int) (60 * c.amp);
        hide(p, ticks);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false, true));
        c.el.burst(at.clone().add(0, 1, 0), 1.5);
        later((int) (120 * c.amp), () -> {
            decoys.remove(decoy.getUniqueId());
            remove(decoy);
        });
        return true;
    }

    private void explodeDecoy(ArmorStand decoy, Player owner) {
        decoys.remove(decoy.getUniqueId());
        Release release = releases.get(owner);
        Location at = decoy.getLocation().add(0, 1, 0);
        remove(decoy);
        at.getWorld().spawnParticle(Particle.SQUID_INK, at, 60, 1.2, 1.0, 1.2, 0.05);
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 30, 1.0, 0.8, 1.0, 0.02);
        Vfx.sound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.6f);
        if (release == null) {
            return;
        }
        Cast c = new Cast(owner, release, release.advanced ? 1.5 : 1.0, release.element, release.form.color());
        for (LivingEntity entity : enemies(c, at, 3.5)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
            strike(c, entity, 4 * c.amp);
        }
    }

    private boolean meteorLance(Cast c) {
        Player p = c.p;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 4, false, false, false));
        Vfx.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.6f);
        every(p, 20, 2, tick -> {
            Location hand = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.2));
            Vfx.ring(hand, 1.2 - tick * 0.05, 12, c.color, 1.0f);
            c.el.main(hand, 2, 0.1, 0.1, 0.1, 0);
            if (tick < 20) {
                return;
            }
            Location origin = p.getEyeLocation();
            Vector dir = origin.getDirection().normalize();
            Vfx.sound(origin, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.6f);
            Vfx.sound(origin, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.4f);
            Set<UUID> hit = new HashSet<>();
            int length = (int) (40 * c.amp);
            for (int d = 1; d <= length; d++) {
                Location point = origin.clone().add(dir.clone().multiply(d));
                if (!c.el.phases() && point.getBlock().getType().isSolid()) {
                    c.el.burst(point, 1.5);
                    break;
                }
                point.getWorld().spawnParticle(Particle.DUST, point, 3, 0.12, 0.12, 0.12, 0, Vfx.dust(c.color, 1.8f));
                point.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                for (LivingEntity entity : point.getNearbyLivingEntities(1.3)) {
                    if (hit.add(entity.getUniqueId()) && enemy(c, entity)) {
                        strike(c, entity, Math.min(14, 5 + d * 0.25));
                    }
                }
            }
        });
        return true;
    }

    private boolean updraft(Cast c) {
        Player p = c.p;
        Location at = p.getLocation();
        for (LivingEntity entity : enemies(c, at, 5 * c.amp)) {
            Vector pull = at.toVector().subtract(entity.getLocation().toVector()).multiply(0.15);
            entity.setVelocity(pull.setY(1.15));
            strike(c, entity, 3);
        }
        p.setVelocity(new Vector(0, 1.4, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, true));
        give(p, "plunge", 4500);
        p.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, at.clone().add(0, 1, 0), 1);
        every(p, 20, 1, tick -> {
            for (int i = 0; i < 4; i++) {
                double a = tick * 0.6 + i * Math.PI / 2;
                double r = 1.2 + tick * 0.08;
                at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(Math.cos(a) * r, tick * 0.4, Math.sin(a) * r),
                        1, 0, 0, 0, 0);
            }
        });
        return true;
    }

    private boolean ironWall(Cast c) {
        Player p = c.p;
        Vector forward = flat(p);
        Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        Location center = p.getLocation().add(forward.clone().multiply(3));
        int width = c.amp > 1 ? 7 : 5;
        List<BlockDisplay> wall = new ArrayList<>();
        for (int i = -width / 2; i <= width / 2; i++) {
            Location base = ReleaseService.groundAt(center.clone().add(side.clone().multiply(i)));
            if (base == null) {
                base = center.clone().add(side.clone().multiply(i));
            }
            BlockDisplay column = block(base, Material.IRON_BLOCK, 1f, 0.1f, 0.4f);
            column.setRotation(p.getLocation().getYaw(), 0);
            column.setInterpolationDuration(6);
            column.setInterpolationDelay(-1);
            later(1, () -> column.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.2f), new Quaternionf(),
                    new Vector3f(1f, 3f, 0.4f), new Quaternionf())));
            wall.add(column);
            base.getWorld().spawnParticle(Particle.BLOCK, base, 10, 0.4, 0.2, 0.4, 0, Material.IRON_BLOCK.createBlockData());
        }
        Vfx.sound(center, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
        int ticks = (int) (100 * c.amp);
        every(p, ticks, 2, tick -> {
            if (tick >= ticks) {
                wall.forEach(this::remove);
                return;
            }
            for (Entity entity : center.getNearbyEntities(width / 2.0 + 0.5, 3, width / 2.0 + 0.5)) {
                Vector offset = entity.getLocation().toVector().subtract(center.toVector());
                if (Math.abs(offset.dot(forward)) > 1.2) {
                    continue;
                }
                if (entity instanceof Projectile projectile && !(projectile.getShooter() instanceof Player shooter
                        && (shooter.equals(p) || AwakeningPowers.allies(p, shooter)))) {
                    entity.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, entity.getLocation(), 8, 0.1, 0.1, 0.1, 0.1);
                    Vfx.sound(entity.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.4f, 1.8f);
                    entity.remove();
                } else if (enemy(c, entity)) {
                    entity.setVelocity(forward.clone().multiply(0.9).setY(0.3));
                }
            }
        });
        return true;
    }

    private boolean frozenShrine(Cast c) {
        Player p = c.p;
        Location at = p.getLocation();
        BlockDisplay ice = block(at, Material.ICE, 1.4f, 2.3f, 1.4f);
        int ticks = (int) (40 * c.amp);
        give(p, "invulnerable", ticks * 50L);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10, false, false, false));
        Vfx.sound(at, Sound.BLOCK_GLASS_PLACE, 1.0f, 0.5f);
        every(p, ticks, 5, tick -> {
            heal(p, 6.0 * c.amp * 5 / ticks);
            c.el.main(at.clone().add(0, 1, 0), 3, 0.6, 1.0, 0.6, 0);
            if (tick >= ticks) {
                remove(ice);
                Vfx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
                at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 1, 0), 60, 1.0, 1.0, 1.0, 0,
                        Material.ICE.createBlockData());
                Vfx.shockwave(plugin, at, c.color, 5 * c.amp, 8);
                for (LivingEntity entity : enemies(c, at, 5 * c.amp)) {
                    entity.setFreezeTicks(entity.getMaxFreezeTicks() + 100);
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    strike(c, entity, 4);
                }
            }
        });
        return true;
    }

    private boolean infernoBlade(Cast c) {
        Player p = c.p;
        int ticks = (int) (120 * c.amp);
        give(p, "inferno", ticks * 50L);
        Vfx.sound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.6f);
        every(p, ticks, 2, tick -> {
            Location hand = p.getLocation().add(0, 1.1, 0).add(flat(p).multiply(0.5))
                    .add(new Vector(-flat(p).getZ(), 0, flat(p).getX()).multiply(-0.35));
            p.getWorld().spawnParticle(Particle.FLAME, hand, 3, 0.1, 0.25, 0.1, 0.01);
        });
        return true;
    }

    private boolean thousandPetals(Cast c) {
        Player p = c.p;
        Location cloud = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(2));
        int ticks = (int) (80 * c.amp);
        Vfx.sound(cloud, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.5f);
        every(p, ticks, 1, tick -> {
            Location goal = aimPoint(p, 25).add(0, 1, 0);
            Vector move = goal.toVector().subtract(cloud.toVector());
            if (move.lengthSquared() > 0.36) {
                move.normalize().multiply(0.6);
            }
            cloud.add(move);
            double radius = 2.5 * (c.amp > 1 ? 1.3 : 1.0);
            p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, cloud, 14, radius * 0.5, 0.8, radius * 0.5, 0.02);
            p.getWorld().spawnParticle(Particle.DUST, cloud, 4, radius * 0.4, 0.6, radius * 0.4, 0, Vfx.dust(c.color, 1.0f));
            if (tick % 8 == 0) {
                Vfx.sound(cloud, Sound.BLOCK_CHERRY_LEAVES_STEP, 0.8f, 1.6f);
                for (LivingEntity entity : enemies(c, cloud, radius)) {
                    strike(c, entity, 2);
                }
            }
        });
        return true;
    }

    private boolean rainPrison(Cast c) {
        Player p = c.p;
        Location center = aimPoint(p, 30);
        double radius = 5 * c.amp;
        int ticks = (int) (120 * c.amp);
        Vfx.sound(center, Sound.WEATHER_RAIN_ABOVE, 1.5f, 0.8f);
        every(p, ticks, 2, tick -> {
            Location sky = center.clone().add(0, 6, 0);
            p.getWorld().spawnParticle(Particle.CLOUD, sky, 10, radius * 0.6, 0.3, radius * 0.6, 0);
            p.getWorld().spawnParticle(Particle.FALLING_WATER, sky, 30, radius * 0.6, 0.2, radius * 0.6, 0);
            if (tick % 10 == 0) {
                ring(center.clone().add(0, 0.1, 0), radius, c.color);
            }
            if (tick % 20 == 0) {
                for (LivingEntity entity : enemies(c, center, radius)) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                    Location top = entity.getLocation().add(0, 6, 0);
                    Vfx.line(top, entity.getLocation().add(0, 1, 0), Color.fromRGB(60, 140, 255), 1.1f, 0.3);
                    strike(c, entity, 2.5);
                }
            }
        });
        return true;
    }

    private boolean thunderStep(Cast c) {
        Player p = c.p;
        int charges = c.amp > 1 ? 4 : 3;
        counters.put(p.getUniqueId(), charges);
        step(c);
        return true;
    }

    private void step(Cast c) {
        Player p = c.p;
        UUID id = p.getUniqueId();
        int left = counters.getOrDefault(id, 0) - 1;
        counters.put(id, left);
        Location from = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location to = from.clone();
        for (double d = 0.5; d <= 9; d += 0.5) {
            Location next = from.clone().add(dir.clone().multiply(d));
            if (!standable(next)) {
                break;
            }
            to = next;
        }
        to.setDirection(dir);
        Location a = from.clone().add(0, 1, 0);
        Location b = to.clone().add(0, 1, 0);
        Vfx.line(a, b, Color.fromRGB(170, 230, 255), 1.4f, 0.25);
        a.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, a, 20, 0.3, 0.6, 0.3, 0.1);
        Vfx.sound(a, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
        p.teleport(to);
        p.setFallDistance(0);
        Vector path = b.toVector().subtract(a.toVector());
        double length = path.length();
        Set<UUID> hit = new HashSet<>();
        for (double d = 0; d <= length; d += 0.7) {
            Location point = a.clone().add(path.clone().normalize().multiply(d));
            for (LivingEntity entity : point.getNearbyLivingEntities(1.6)) {
                if (hit.add(entity.getUniqueId()) && enemy(c, entity)) {
                    strike(c, entity, 4);
                }
            }
        }
        if (left > 0) {
            recast(p, 4000, () -> {
                Release release = releases.get(p);
                if (release != null) {
                    step(new Cast(p, release, c.amp, c.el, c.color));
                }
            });
        }
    }

    private boolean toxicSpire(Cast c) {
        Player p = c.p;
        Location center = aimPoint(p, 20);
        BlockDisplay spire = block(center, Material.MOSS_BLOCK, 0.6f, 3.5f, 0.6f);
        BlockDisplay tip = block(center.clone().add(0, 3.5, 0), Material.SLIME_BLOCK, 0.4f, 0.8f, 0.4f);
        int ticks = (int) (160 * c.amp);
        double radius = 4 * c.amp;
        Vfx.sound(center, Sound.BLOCK_ROOTED_DIRT_PLACE, 1.0f, 0.5f);
        every(p, ticks, 5, tick -> {
            if (tick >= ticks) {
                remove(spire);
                remove(tip);
                return;
            }
            p.getWorld().spawnParticle(Particle.SNEEZE, center.clone().add(0, 0.4, 0), 8, radius * 0.4, 0.2, radius * 0.4, 0.01);
            if (tick % 20 == 0) {
                ring(center.clone().add(0, 0.2, 0), radius, Color.fromRGB(110, 200, 40));
                Vfx.sound(center, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 0.6f);
                for (LivingEntity entity : enemies(c, center, radius)) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                    Vector pull = center.toVector().subtract(entity.getLocation().toVector());
                    if (pull.lengthSquared() > 1) {
                        entity.setVelocity(entity.getVelocity().add(pull.normalize().multiply(0.35)));
                    }
                    strike(c, entity, 1.5);
                }
            }
        });
        return true;
    }

    private boolean bellOfBinding(Cast c) {
        Player p = c.p;
        Location at = p.getLocation();
        double radius = 8 * c.amp;
        Vfx.sound(at, Sound.BLOCK_BELL_USE, 2.0f, 0.5f);
        Vfx.sound(at, Sound.BLOCK_BELL_RESONATE, 1.5f, 0.8f);
        every(p, 12, 2, tick -> ring(at.clone().add(0, 1, 0), radius * tick / 12.0 + 0.5, c.color));
        long until = System.currentTimeMillis() + (long) (3000 * c.amp);
        for (LivingEntity entity : enemies(c, at, radius)) {
            silencedUntil.put(entity.getUniqueId(), until);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
            entity.getWorld().spawnParticle(Particle.NOTE, entity.getLocation().add(0, 2.2, 0), 5, 0.3, 0.2, 0.3, 1);
            strike(c, entity, 3);
            if (entity instanceof Player player) {
                player.sendActionBar(Component.text("Silenced!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            }
        }
        return true;
    }

    // ================================================================ HOLLOW

    private boolean feralHunt(Cast c) {
        Player p = c.p;
        p.setHealth(Math.max(1.0, p.getHealth() - 2.0));
        int ticks = (int) (120 * c.amp);
        give(p, "feral", ticks * 50L);
        counters.put(p.getUniqueId(), 0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false, true));
        Vfx.sound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.2f, 0.6f);
        Vfx.sound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.3f);
        every(p, ticks, 4, tick -> p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 3,
                0.4, 0.6, 0.4, 0, Vfx.dust(Color.fromRGB(170, 0, 20), 1.3f)));
        return true;
    }

    private boolean mirageSwap(Cast c) {
        LivingEntity target = aimed(c, 20);
        if (target == null) {
            return false;
        }
        Player p = c.p;
        Location mine = p.getLocation();
        Location theirs = target.getLocation();
        Vfx.burst(mine.clone().add(0, 1, 0), c.color, 30, 0.4, 1.5f);
        Vfx.burst(theirs.clone().add(0, 1, 0), c.color, 30, 0.4, 1.5f);
        target.teleport(mine.clone().setDirection(theirs.toVector().subtract(mine.toVector())));
        p.teleport(theirs.clone().setDirection(mine.toVector().subtract(theirs.toVector())));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        Vfx.sound(mine, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
        strike(c, target, 3);
        return true;
    }

    private boolean blackTalon(Cast c) {
        Player p = c.p;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 36, 10, false, false, false));
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
        double width = c.amp > 1 ? 3.0 : 2.0;
        every(p, 36, 2, tick -> {
            Location mouth = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.5));
            p.getWorld().spawnParticle(Particle.DUST, mouth, 10, 0.4 - tick * 0.008, 0.4 - tick * 0.008,
                    0.4 - tick * 0.008, 0, Vfx.dust(c.color, 2.0f));
            p.getWorld().spawnParticle(Particle.DUST, mouth, 4, 0.2, 0.2, 0.2, 0, Vfx.dust(Color.fromRGB(15, 10, 20), 1.8f));
            if (tick < 30) {
                return;
            }
            Location origin = p.getEyeLocation();
            Vector dir = origin.getDirection().normalize();
            if (tick == 30) {
                Vfx.sound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.7f);
                Vfx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
            }
            Set<UUID> hit = new HashSet<>();
            for (int d = 1; d <= 30; d++) {
                Location point = origin.clone().add(dir.clone().multiply(d));
                p.getWorld().spawnParticle(Particle.DUST, point, 8, width * 0.4, width * 0.4, width * 0.4, 0,
                        Vfx.dust(c.color, 2.4f));
                p.getWorld().spawnParticle(Particle.DUST, point, 3, width * 0.2, width * 0.2, width * 0.2, 0,
                        Vfx.dust(Color.fromRGB(15, 10, 20), 2.0f));
                if (tick == 30) {
                    for (LivingEntity entity : point.getNearbyLivingEntities(width)) {
                        if (hit.add(entity.getUniqueId()) && enemy(c, entity)) {
                            strike(c, entity, 10);
                        }
                    }
                }
            }
        });
        return true;
    }

    private boolean sonidoStorm(Cast c) {
        LivingEntity target = aimed(c, 16);
        if (target == null) {
            return false;
        }
        Player p = c.p;
        int hops = c.amp > 1 ? 7 : 5;
        every(p, hops * 4, 4, tick -> {
            if (!target.isValid() || target.isDead()) {
                return;
            }
            double a = Math.random() * Math.PI * 2;
            Location spot = target.getLocation().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            if (!standable(spot)) {
                spot = target.getLocation().add(0, 0, 0);
            }
            Location from = p.getLocation().add(0, 1, 0);
            spot.setDirection(target.getLocation().toVector().subtract(spot.toVector()));
            Vfx.line(from, spot.clone().add(0, 1, 0), c.color, 0.8f, 0.5);
            p.teleport(spot);
            p.setFallDistance(0);
            Vfx.sound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.8f);
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
            int noDamage = target.getNoDamageTicks();
            target.setNoDamageTicks(0);
            strike(c, target, 2.5);
            target.setNoDamageTicks(Math.min(noDamage, 2));
        });
        return true;
    }

    private boolean magmaFist(Cast c) {
        Player p = c.p;
        Vector forward = flat(p);
        Location start = p.getLocation();
        int count = c.amp > 1 ? 12 : 8;
        Set<UUID> hit = new HashSet<>();
        Vfx.sound(start, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.5f);
        every(p, count * 2, 2, tick -> {
            int i = tick / 2 + 1;
            Location point = ReleaseService.groundAt(start.clone().add(forward.clone().multiply(i * 1.5)));
            if (point == null) {
                return;
            }
            p.getWorld().spawnParticle(Particle.LAVA, point, 8, 0.4, 0.3, 0.4, 0);
            p.getWorld().spawnParticle(Particle.FLAME, point, 20, 0.3, 0.8, 0.3, 0.08);
            p.getWorld().spawnParticle(Particle.BLOCK, point, 12, 0.4, 0.2, 0.4, 0, Material.MAGMA_BLOCK.createBlockData());
            Vfx.sound(point, Sound.BLOCK_LAVA_POP, 1.0f, 0.6f);
            for (LivingEntity entity : point.getNearbyLivingEntities(1.7)) {
                if (hit.add(entity.getUniqueId()) && enemy(c, entity)) {
                    strike(c, entity, 4);
                    entity.setFireTicks(60);
                    later(1, () -> entity.setVelocity(new Vector(0, 0.75, 0)));
                }
            }
        });
        return true;
    }

    private boolean shellReflect(Cast c) {
        Player p = c.p;
        int ticks = (int) (60 * c.amp);
        give(p, "shell", ticks * 50L);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0, false, false, false));
        Vfx.sound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1.2f, 0.6f);
        every(p, ticks, 4, tick -> {
            Location at = p.getLocation();
            for (double h = 0.2; h <= 2.0; h += 0.6) {
                Vfx.ring(at.clone().add(0, h, 0), 1.1 - Math.abs(h - 1.1) * 0.4, 14, c.color, 1.0f);
            }
        });
        return true;
    }

    private boolean sharkDive(Cast c) {
        LivingEntity target = aimed(c, 20);
        if (target == null) {
            return false;
        }
        Player p = c.p;
        give(p, "invulnerable", 1300);
        hide(p, 24);
        Location start = p.getLocation();
        Vfx.sound(start, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.6f);
        start.getWorld().spawnParticle(Particle.SPLASH, start, 40, 0.6, 0.2, 0.6, 0.2);
        every(p, 22, 1, tick -> {
            if (!target.isValid()) {
                return;
            }
            Location goal = target.getLocation();
            double t = Math.min(1.0, tick / 20.0);
            Location fin = start.clone().add(goal.toVector().subtract(start.toVector()).multiply(t));
            Location ground = ReleaseService.groundAt(fin);
            Location at = ground != null ? ground : fin;
            at.getWorld().spawnParticle(Particle.SPLASH, at, 8, 0.3, 0.1, 0.3, 0.1);
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.4, 0), 4, 0.05, 0.25, 0.05, 0,
                    Vfx.dust(c.color, 1.5f));
            if (tick == 20) {
                p.teleport(goal.clone().setDirection(p.getLocation().getDirection()));
                p.setVelocity(new Vector(0, 0.8, 0));
                target.setVelocity(new Vector(0, 1.1, 0));
                Vfx.sound(goal, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.5f, 0.6f);
                Vfx.sound(goal, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.8f);
                goal.getWorld().spawnParticle(Particle.SPLASH, goal, 80, 0.8, 1.0, 0.8, 0.4);
                strike(c, target, 9);
            }
        });
        return true;
    }

    private boolean fadingShade(Cast c) {
        Player p = c.p;
        int ticks = (int) (80 * c.amp);
        hide(p, ticks);
        give(p, "shade", ticks * 50L);
        p.getWorld().spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 1, 0), 60, 0.5, 0.9, 0.5, 0.02);
        p.getWorld().spawnParticle(Particle.PALE_OAK_LEAVES, p.getLocation().add(0, 1, 0), 30, 0.5, 0.9, 0.5, 0.02);
        Vfx.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.5f);
        return true;
    }

    private boolean stinger(Cast c) {
        Player p = c.p;
        Arrow arrow = p.launchProjectile(Arrow.class, p.getEyeLocation().getDirection().multiply(3.2));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setDamage(2.0);
        arrow.setColor(Color.fromRGB(110, 200, 40));
        track(arrow);
        arrow.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 2);
        Vfx.trail(plugin, arrow, Color.fromRGB(110, 200, 40), Particle.SNEEZE, 60);
        Vfx.sound(p.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.2f, 0.6f);
        later(100, () -> remove(arrow));
        return true;
    }

    private void injectVenom(Player caster, LivingEntity target) {
        Release release = releases.get(caster);
        if (release == null) {
            return;
        }
        Cast c = new Cast(caster, release, release.advanced ? 1.5 : 1.0, release.element, release.form.color());
        venom.put(target.getUniqueId(), caster.getUniqueId());
        venomHits.put(target.getUniqueId(), 0);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
        Vfx.sound(target.getLocation(), Sound.ENTITY_SPIDER_HURT, 1.0f, 1.4f);
        int ticks = (int) (60 * c.amp);
        every(caster, ticks, 4, tick -> {
            target.getWorld().spawnParticle(Particle.SNEEZE, target.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0);
            if (tick >= ticks) {
                int hits = venomHits.getOrDefault(target.getUniqueId(), 0);
                venom.remove(target.getUniqueId());
                venomHits.remove(target.getUniqueId());
                if (target.isValid() && !target.isDead()) {
                    target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1, 0), 40, 0.5, 0.6, 0.5, 0.1);
                    Vfx.sound(target.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.5f);
                    strike(c, target, Math.min(12, 3 + 1.5 * hits));
                }
            }
        });
    }

    private boolean eagleDive(Cast c) {
        Player p = c.p;
        p.setVelocity(p.getEyeLocation().getDirection().multiply(1.2).setY(1.0));
        Vfx.sound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.2f, 0.6f);
        int ticks = (int) (60 * c.amp);
        boolean[] landed = {false};
        every(p, ticks, 1, tick -> {
            if (landed[0]) {
                return;
            }
            p.setFallDistance(0);
            if (tick > 6 && (((Entity) p).isOnGround() || tick >= ticks)) {
                landed[0] = true;
                Location at = p.getLocation();
                Location sky = at.clone().add(0, 14, 0);
                Location point = sky.clone();
                while (point.getY() > at.getY()) {
                    Location next = point.clone().add((Math.random() - 0.5) * 1.2, -1.4, (Math.random() - 0.5) * 1.2);
                    Vfx.line(point, next, Color.fromRGB(170, 230, 255), 1.5f, 0.25);
                    point = next;
                }
                Vfx.sound(at, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.2f);
                Vfx.shockwave(plugin, at, c.color, 3.5 * c.amp, 8);
                for (LivingEntity entity : enemies(c, at, 3.5 * c.amp)) {
                    strike(c, entity, 6);
                }
                return;
            }
            Vector look = p.getEyeLocation().getDirection().multiply(0.9);
            look.setY(Math.max(-0.35, look.getY()));
            p.setVelocity(look);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.3, 0.1, 0.3, 0);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 2, 0.4, 0.2, 0.4, 0);
        });
        return true;
    }

    private boolean boneCage(Cast c) {
        LivingEntity target = aimed(c, 16);
        if (target == null) {
            return false;
        }
        Player p = c.p;
        Location center = target.getLocation();
        List<BlockDisplay> bars = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            bars.add(block(center.clone().add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6), Material.BONE_BLOCK, 0.35f, 3f, 0.35f));
        }
        Vfx.sound(center, Sound.ENTITY_SKELETON_HURT, 1.2f, 0.5f);
        Vfx.sound(center, Sound.BLOCK_BONE_BLOCK_PLACE, 1.2f, 0.6f);
        int ticks = (int) (60 * c.amp);
        every(p, ticks, 2, tick -> {
            if (tick >= ticks || !target.isValid()) {
                bars.forEach(this::remove);
                return;
            }
            if (target.getLocation().distanceSquared(center) > 1.4 * 1.4) {
                Location back = center.clone().setDirection(target.getLocation().getDirection());
                target.teleport(back);
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0,
                        Material.BONE_BLOCK.createBlockData());
                if (tick % 10 == 0) {
                    strike(c, target, 1);
                }
            }
        });
        return true;
    }

    private boolean webOfFrost(Cast c) {
        Player p = c.p;
        Location center = aimPoint(p, 20);
        double radius = 3.5 * c.amp;
        int ticks = (int) (120 * c.amp);
        Vfx.sound(center, Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 0.6f);
        Vfx.sound(center, Sound.BLOCK_POWDER_SNOW_PLACE, 1.2f, 0.8f);
        every(p, ticks, 4, tick -> {
            Location floor = center.clone().add(0, 0.15, 0);
            for (int spoke = 0; spoke < 8; spoke++) {
                double a = spoke * Math.PI / 4;
                Vfx.line(floor, floor.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                        Color.fromRGB(230, 240, 255), 0.8f, 0.6);
            }
            if (tick % 8 == 0) {
                Vfx.ring(floor, radius * 0.5, 16, Color.fromRGB(230, 240, 255), 0.8f);
                Vfx.ring(floor, radius, 24, Color.fromRGB(170, 230, 255), 0.9f);
            }
            for (LivingEntity entity : center.getNearbyLivingEntities(radius, 2)) {
                if (service.canStrike(p, entity)) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3));
                    entity.setFreezeTicks(Math.min(entity.getMaxFreezeTicks() + 40, entity.getFreezeTicks() + 12));
                } else if (entity instanceof Player ally && (ally.equals(p) || AwakeningPowers.allies(p, ally))) {
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 0, false, false, true));
                }
            }
        });
        return true;
    }

    // ================================================================ QUINCY

    private boolean blitzVolley(Cast c) {
        Player p = c.p;
        int shots = c.amp > 1 ? 15 : 10;
        every(p, shots * 3, 3, tick -> {
            Location origin = p.getEyeLocation();
            Vector dir = origin.getDirection().normalize();
            Vfx.sound(origin, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.6f + (float) Math.random() * 0.3f);
            RayTraceResult result = p.getWorld().rayTrace(origin, dir, 30, org.bukkit.FluidCollisionMode.NEVER, true, 0.4,
                    entity -> entity instanceof LivingEntity && !entity.equals(p));
            Location end = result != null ? result.getHitPosition().toLocation(p.getWorld())
                    : origin.clone().add(dir.clone().multiply(30));
            Vfx.line(origin.clone().add(dir), end, c.color, 0.9f, 0.5);
            p.getWorld().spawnParticle(Particle.END_ROD, end, 3, 0.05, 0.05, 0.05, 0.05);
            if (result != null && result.getHitEntity() instanceof LivingEntity target && enemy(c, target)) {
                int noDamage = target.getNoDamageTicks();
                target.setNoDamageTicks(0);
                strike(c, target, 2.5);
                target.setNoDamageTicks(Math.min(noDamage, 2));
            }
        });
        return true;
    }

    private boolean mirrorFeint(Cast c) {
        give(c.p, "feint", (long) (4000 * c.amp));
        Vfx.sound(c.p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.6f);
        every(c.p, (int) (80 * c.amp), 4, tick -> {
            if (has(c.p, "feint")) {
                Vfx.ring(c.p.getLocation().add(0, 1, 0), 0.9, 12, c.color, 0.9f);
            }
        });
        return true;
    }

    private boolean sniperStar(Cast c) {
        Player p = c.p;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 24, 5, false, false, false));
        Vfx.sound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.2f, 0.6f);
        every(p, 24, 2, tick -> {
            Location front = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(2));
            Vfx.ring(front, 1.4 - tick * 0.05, 14, c.color, 0.8f);
            if (tick < 24) {
                return;
            }
            Location origin = p.getEyeLocation();
            Vector dir = origin.getDirection().normalize();
            Vfx.sound(origin, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.5f, 1.2f);
            Vfx.sound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
            RayTraceResult result = p.getWorld().rayTrace(origin, dir, 60, org.bukkit.FluidCollisionMode.NEVER, true, 0.2,
                    entity -> entity instanceof LivingEntity && !entity.equals(p));
            Location end = result != null ? result.getHitPosition().toLocation(p.getWorld())
                    : origin.clone().add(dir.clone().multiply(60));
            Vfx.line(origin.clone().add(dir), end, Color.WHITE, 1.2f, 0.4);
            Vfx.line(origin.clone().add(dir), end, c.color, 1.6f, 0.9);
            if (result != null && result.getHitEntity() instanceof LivingEntity target && enemy(c, target)) {
                boolean head = result.getHitPosition().getY() > target.getEyeLocation().getY() - 0.35;
                strike(c, target, head ? 16 : 12);
                if (head) {
                    target.getWorld().spawnParticle(Particle.END_ROD, target.getEyeLocation(), 30, 0.1, 0.1, 0.1, 0.2);
                    Vfx.sound(target.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 1.8f);
                    p.sendActionBar(Component.text("Headshot!", c.r.form.text(), TextDecoration.BOLD));
                }
            }
        });
        return true;
    }

    private boolean glide(Cast c) {
        Player p = c.p;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        int ticks = (int) (100 * c.amp);
        flying.add(p.getUniqueId());
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setFlySpeed(0.07f);
        Vfx.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 2.0f);
        every(p, ticks, 1, tick -> {
            if (!flying.contains(p.getUniqueId())) {
                return;
            }
            if (tick >= ticks) {
                endFlight(p);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, true));
                return;
            }
            Location feet = p.getLocation().subtract(0, 0.1, 0);
            Vfx.ring(feet, 0.6, 10, c.color, 0.8f);
            if (tick % 4 == 0) {
                p.getWorld().spawnParticle(Particle.END_ROD, feet, 2, 0.3, 0, 0.3, 0);
            }
        });
        return true;
    }

    private void endFlight(Player player) {
        if (!flying.remove(player.getUniqueId())) {
            return;
        }
        player.setFlySpeed(0.1f);
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.setFallDistance(0);
    }

    private boolean bastion(Cast c) {
        Player p = c.p;
        Location center = p.getLocation();
        double radius = 4 * c.amp;
        int ticks = 120;
        Vfx.sound(center, Sound.BLOCK_BELL_RESONATE, 1.2f, 1.4f);
        every(p, ticks, 2, tick -> {
            if (tick % 6 == 0) {
                for (double h = 0; h <= radius; h += radius / 4) {
                    double r = Math.sqrt(Math.max(0, radius * radius - h * h));
                    Vfx.ring(center.clone().add(0, h, 0), r, (int) Math.max(8, r * 6), c.color, 0.9f);
                }
            }
            for (Entity entity : center.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Projectile projectile && entity.getLocation().distanceSquared(center) <= radius * radius
                        && !(projectile.getShooter() instanceof Player shooter
                        && (shooter.equals(p) || AwakeningPowers.allies(p, shooter)))) {
                    entity.getWorld().spawnParticle(Particle.NOTE, entity.getLocation(), 3, 0.2, 0.2, 0.2, 1);
                    Vfx.sound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 2.0f);
                    entity.remove();
                }
            }
            if (tick % 20 == 0) {
                for (LivingEntity entity : enemies(c, center, radius)) {
                    strike(c, entity, 1.5);
                    Vector out = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if (out.lengthSquared() > 0.01) {
                        entity.setVelocity(out.normalize().multiply(0.4).setY(0.15));
                    }
                }
            }
        });
        return true;
    }

    private boolean blossomHaven(Cast c) {
        Player p = c.p;
        Location center = p.getLocation();
        double radius = 5 * c.amp;
        Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.2f);
        every(p, 120, 4, tick -> {
            p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, center.clone().add(0, 2.5, 0), 12, radius * 0.5, 0.5,
                    radius * 0.5, 0);
            if (tick % 8 == 0) {
                ring(center.clone().add(0, 0.1, 0), radius, Color.fromRGB(255, 190, 215));
            }
            for (Player ally : allies(p, center, radius)) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 1, false, false, true));
            }
            if (tick % 10 == 0) {
                for (LivingEntity entity : enemies(c, center, radius)) {
                    Vector out = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if (out.lengthSquared() > 0.01) {
                        entity.setVelocity(out.normalize().multiply(0.6).setY(0.2));
                    }
                }
            }
        });
        return true;
    }

    private boolean bloodDebt(Cast c) {
        Player p = c.p;
        int ticks = 80;
        give(p, "debt", ticks * 50L);
        stored.put(p.getUniqueId(), 0.0);
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 1.0f);
        every(p, ticks, 4, tick -> {
            double debt = stored.getOrDefault(p.getUniqueId(), 0.0);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 2 + (int) debt, 0.5, 0.7, 0.5, 0,
                    Vfx.dust(Color.fromRGB(150, 0, 20), 1.4f));
            if (tick >= ticks) {
                stored.remove(p.getUniqueId());
                Location at = p.getLocation();
                Vfx.burst(at.clone().add(0, 1, 0), Color.fromRGB(170, 0, 20), 80, 1.5, 2.0f);
                Vfx.shockwave(plugin, at, Color.fromRGB(170, 0, 20), 4 * c.amp, 8);
                Vfx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
                for (LivingEntity entity : enemies(c, at, 4 * c.amp)) {
                    strike(c, entity, Math.min(16, 3 + debt * 0.8));
                }
                heal(p, debt * 0.3);
            }
        });
        return true;
    }

    private static final Set<PotionEffectType> BUFFS = Set.of(PotionEffectType.SPEED, PotionEffectType.STRENGTH,
            PotionEffectType.RESISTANCE, PotionEffectType.REGENERATION, PotionEffectType.ABSORPTION,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.JUMP_BOOST, PotionEffectType.HASTE,
            PotionEffectType.INVISIBILITY, PotionEffectType.HEALTH_BOOST, PotionEffectType.SLOW_FALLING);

    private boolean mirrorTheft(Cast c) {
        LivingEntity target = aimed(c, 20);
        if (target == null) {
            return false;
        }
        Player p = c.p;
        int stolen = 0;
        for (PotionEffect effect : List.copyOf(target.getActivePotionEffects())) {
            if (BUFFS.contains(effect.getType())) {
                target.removePotionEffect(effect.getType());
                p.addPotionEffect(new PotionEffect(effect.getType(), Math.min(effect.getDuration(), (int) (200 * c.amp)),
                        effect.getAmplifier()));
                stolen++;
            }
        }
        if (stolen == 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
        Vfx.line(target.getLocation().add(0, 1, 0), p.getLocation().add(0, 1, 0), Color.fromRGB(220, 240, 255), 1.2f, 0.3);
        target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0,
                Material.GLASS.createBlockData());
        Vfx.sound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.4f);
        strike(c, target, 3);
        p.sendActionBar(Component.text(stolen > 0 ? "Stole " + stolen + " effect" + (stolen == 1 ? "" : "s") + "!"
                : "Nothing to steal — slowed instead.", c.r.form.text()));
        return true;
    }

    private boolean tidalWave(Cast c) {
        Player p = c.p;
        Vector forward = flat(p);
        Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        Location start = p.getLocation().add(forward.clone().multiply(1.5));
        int half = c.amp > 1 ? 4 : 3;
        Set<UUID> hit = new HashSet<>();
        Vfx.sound(start, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.5f);
        every(p, 15, 1, tick -> {
            Location row = start.clone().add(forward.clone().multiply(tick));
            for (int i = -half; i <= half; i++) {
                Location column = row.clone().add(side.clone().multiply(i));
                p.getWorld().spawnParticle(Particle.SPLASH, column.clone().add(0, 1, 0), 6, 0.2, 0.8, 0.2, 0.1);
                p.getWorld().spawnParticle(Particle.DUST, column.clone().add(0, 1.2, 0), 2, 0.2, 0.7, 0.2, 0,
                        Vfx.dust(Color.fromRGB(40, 120, 230), 1.6f));
            }
            for (LivingEntity entity : row.getNearbyLivingEntities(half + 0.5, 2.5, half + 0.5)) {
                if (Math.abs(entity.getLocation().toVector().subtract(row.toVector()).dot(forward)) < 1.5
                        && enemy(c, entity)) {
                    entity.setVelocity(forward.clone().multiply(1.1).setY(0.25));
                    if (hit.add(entity.getUniqueId())) {
                        strike(c, entity, 2.5);
                    }
                }
            }
        });
        return true;
    }

    private boolean sunburstHalo(Cast c) {
        Player p = c.p;
        int orbs = c.amp > 1 ? 8 : 6;
        boolean[] alive = new boolean[orbs];
        java.util.Arrays.fill(alive, true);
        int ticks = (int) (120 * c.amp);
        Vfx.sound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        every(p, ticks, 1, tick -> {
            Location center = p.getLocation().add(0, 1.1, 0);
            for (int i = 0; i < orbs; i++) {
                if (!alive[i]) {
                    continue;
                }
                double a = tick * 0.15 + i * Math.PI * 2 / orbs;
                Location orb = center.clone().add(Math.cos(a) * 1.9, Math.sin(tick * 0.1 + i) * 0.3, Math.sin(a) * 1.9);
                p.getWorld().spawnParticle(Particle.FLAME, orb, 2, 0.05, 0.05, 0.05, 0);
                p.getWorld().spawnParticle(Particle.DUST, orb, 1, 0, 0, 0, 0, Vfx.dust(Color.fromRGB(255, 220, 100), 1.6f));
                for (LivingEntity entity : orb.getNearbyLivingEntities(1.1)) {
                    if (enemy(c, entity)) {
                        alive[i] = false;
                        p.getWorld().spawnParticle(Particle.EXPLOSION, orb, 1);
                        p.getWorld().spawnParticle(Particle.FLAME, orb, 30, 0.4, 0.4, 0.4, 0.1);
                        Vfx.sound(orb, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
                        entity.setFireTicks(60);
                        strike(c, entity, 3);
                        break;
                    }
                }
            }
        });
        return true;
    }

    private boolean pillarVault(Cast c) {
        Player p = c.p;
        Location base = p.getLocation();
        Vector forward = flat(p);
        BlockDisplay pillar = block(base, Material.QUARTZ_PILLAR, 1.0f, 0.1f, 1.0f);
        pillar.setInterpolationDuration(8);
        pillar.setInterpolationDelay(-1);
        later(1, () -> pillar.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new Quaternionf(),
                new Vector3f(1f, 5f, 1f), new Quaternionf())));
        p.setVelocity(new Vector(0, 1.25, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, true));
        Vfx.sound(base, Sound.BLOCK_STONE_PLACE, 1.2f, 0.5f);
        later(22, () -> {
            // Topple: the pillar swings down along the look direction.
            float yaw = (float) Math.toRadians(-base.getYaw());
            Quaternionf tilt = new Quaternionf().rotateY(yaw).rotateX((float) Math.toRadians(90));
            pillar.setInterpolationDuration(6);
            pillar.setInterpolationDelay(-1);
            pillar.setTransformation(new Transformation(new Vector3f(-0.5f, 0.5f, -0.5f), tilt, new Vector3f(1f, 5f, 1f),
                    new Quaternionf()));
            Vfx.sound(base, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
            Set<UUID> hit = new HashSet<>();
            int length = c.amp > 1 ? 11 : 8;
            for (int d = 1; d <= length; d++) {
                Location point = base.clone().add(forward.clone().multiply(d));
                point.getWorld().spawnParticle(Particle.BLOCK, point.clone().add(0, 0.3, 0), 10, 0.4, 0.2, 0.4, 0,
                        Material.QUARTZ_BLOCK.createBlockData());
                for (LivingEntity entity : point.getNearbyLivingEntities(1.4)) {
                    if (hit.add(entity.getUniqueId()) && enemy(c, entity)) {
                        strike(c, entity, 6);
                        later(1, () -> entity.setVelocity(new Vector(0, 0.7, 0)));
                    }
                }
            }
            later(30, () -> remove(pillar));
        });
        return true;
    }

    private boolean soulTether(Cast c) {
        LivingEntity target = aimed(c, 20);
        if (target == null) {
            return false;
        }
        Player p = c.p;
        UUID id = p.getUniqueId();
        targets.put(id, target.getUniqueId());
        give(p, "tether", (long) (6000 * c.amp));
        Vfx.sound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.6f);
        int ticks = (int) (120 * c.amp);
        every(p, ticks, 2, tick -> {
            if (!target.isValid() || target.isDead() || tick >= ticks || !has(p, "tether")) {
                if (target.getUniqueId().equals(targets.get(id))) {
                    targets.remove(id);
                }
                return;
            }
            Vfx.line(p.getLocation().add(0, 1, 0), target.getLocation().add(0, 1, 0), Color.fromRGB(130, 255, 190), 0.8f, 0.6);
            if (target.getLocation().distanceSquared(p.getLocation()) > 64) {
                Vector pull = p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
                target.setVelocity(pull.multiply(1.2).setY(0.3));
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                Vfx.sound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.8f);
            }
        });
        return true;
    }

    // ================================================================ FULLBRINGER

    private boolean chargePunch(Cast c) {
        Player p = c.p;
        long start = System.currentTimeMillis();
        boolean[] thrown = {false};
        Runnable punch = () -> {
            if (thrown[0]) {
                return;
            }
            thrown[0] = true;
            recasts.remove(p.getUniqueId());
            Release release = releases.get(p);
            if (release == null) {
                return;
            }
            Cast now = new Cast(p, release, c.amp, c.el, c.color);
            double charge = Math.min(1.0, (System.currentTimeMillis() - start) / 3000.0);
            Location origin = p.getEyeLocation();
            Vector dir = origin.getDirection().normalize();
            Location fist = origin.clone().add(dir.clone().multiply(2));
            Vfx.burst(fist, c.color, (int) (30 + 60 * charge), 0.5 + charge, 2.0f);
            p.getWorld().spawnParticle(Particle.EXPLOSION, fist, 1);
            Vfx.sound(fist, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f - (float) charge * 0.6f);
            Vfx.sound(fist, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.8f);
            for (LivingEntity entity : fist.getNearbyLivingEntities(1.8 + charge)) {
                if (enemy(now, entity)) {
                    strike(now, entity, 4 + 10 * charge);
                    later(1, () -> entity.setVelocity(dir.clone().multiply(0.6 + 1.6 * charge).setY(0.35)));
                }
            }
        };
        recast(p, 3000, punch);
        Vfx.sound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.8f);
        every(p, 60, 2, tick -> {
            if (thrown[0]) {
                return;
            }
            Location hand = p.getLocation().add(0, 1.1, 0).add(flat(p).multiply(0.6));
            Vfx.ring(hand, 0.3 + tick * 0.012, 10, c.color, 1.2f);
            if (tick % 20 == 0 && tick > 0) {
                Vfx.sound(hand, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.8f + tick * 0.01f);
            }
            if (tick >= 60) {
                punch.run();
            }
        });
        return true;
    }

    private boolean mirrorCoat(Cast c) {
        Player p = c.p;
        int ticks = (int) (60 * c.amp);
        give(p, "coat", ticks * 50L);
        Vfx.sound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.2f, 1.4f);
        every(p, ticks, 3, tick -> p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 4, 0.35, 0.6,
                0.35, 0, Vfx.dust(Color.fromRGB(230, 180, 255), 1.0f)));
        return true;
    }

    private boolean echoMark(Cast c) {
        give(c.p, "echo", (long) (6000 * c.amp));
        counters.put(c.p.getUniqueId(), c.amp > 1 ? 5 : 3);
        Vfx.sound(c.p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 0.8f);
        return true;
    }

    private boolean skySteps(Cast c) {
        Player p = c.p;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        int ticks = (int) (120 * c.amp);
        counters.put(p.getUniqueId(), c.amp > 1 ? 5 : 3);
        give(p, "steps", ticks * 50L);
        p.setAllowFlight(true);
        Vfx.sound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.6f);
        later(ticks, () -> {
            if (!flying.contains(p.getUniqueId()) && p.isOnline()
                    && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
        });
        return true;
    }

    private boolean ironConstruct(Cast c) {
        Player p = c.p;
        Location at = p.getLocation().add(flat(p).multiply(2));
        if (!standable(at)) {
            at = p.getLocation();
        }
        IronGolem golem = track(at.getWorld().spawn(at, IronGolem.class, g -> {
            g.setPlayerCreated(true);
            g.customName(Component.text(p.getName() + "'s Construct", c.r.form.text()));
            g.setCustomNameVisible(true);
        }));
        golems.put(golem.getUniqueId(), p.getUniqueId());
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 1, 0), 60, 0.6, 1.2, 0.6, 0,
                Material.IRON_BLOCK.createBlockData());
        Vfx.sound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.2f, 0.6f);
        Vfx.sound(at, Sound.BLOCK_ANVIL_USE, 1.0f, 0.8f);
        int ticks = (int) (200 * c.amp);
        every(p, ticks, 10, tick -> {
            if (!golem.isValid()) {
                return;
            }
            if (tick >= ticks) {
                golems.remove(golem.getUniqueId());
                golem.getWorld().spawnParticle(Particle.BLOCK, golem.getLocation().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0,
                        Material.IRON_BLOCK.createBlockData());
                remove(golem);
                return;
            }
            if (golem.getTarget() == null || !golem.getTarget().isValid() || golem.getTarget().isDead()) {
                LivingEntity best = null;
                double distance = 16 * 16;
                for (LivingEntity entity : enemies(c, golem.getLocation(), 16)) {
                    double d = entity.getLocation().distanceSquared(golem.getLocation());
                    if (d < distance && !(entity instanceof IronGolem)) {
                        distance = d;
                        best = entity;
                    }
                }
                golem.setTarget(best);
            }
        });
        return true;
    }

    private boolean heartLink(Cast c) {
        Player p = c.p;
        Player partner = null;
        double best = 12 * 12;
        for (Player ally : allies(p, p.getLocation(), 12)) {
            double d = ally.getLocation().distanceSquared(p.getLocation());
            if (!ally.equals(p) && d < best) {
                best = d;
                partner = ally;
            }
        }
        int ticks = (int) (160 * c.amp);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 0, false, false, true));
        Vfx.sound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.8f);
        if (partner == null) {
            p.sendActionBar(Component.text("No ally nearby — you regenerate alone.", NamedTextColor.GRAY));
            return true;
        }
        Player linked = partner;
        linked.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 0, false, false, true));
        links.put(p.getUniqueId(), linked.getUniqueId());
        links.put(linked.getUniqueId(), p.getUniqueId());
        linked.sendMessage(Component.text(p.getName() + " linked hearts with you.", c.r.form.text()));
        every(p, ticks, 4, tick -> {
            if (tick >= ticks || !linked.isOnline()) {
                links.remove(p.getUniqueId());
                links.remove(linked.getUniqueId());
                return;
            }
            Vfx.line(p.getLocation().add(0, 1.2, 0), linked.getLocation().add(0, 1.2, 0), Color.fromRGB(255, 140, 185),
                    0.8f, 0.8);
            if (tick % 12 == 0) {
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.2, 0), 1);
                linked.getWorld().spawnParticle(Particle.HEART, linked.getLocation().add(0, 2.2, 0), 1);
            }
        });
        return true;
    }

    private boolean rewind(Cast c) {
        Player p = c.p;
        Location mark = p.getLocation();
        double health = p.getHealth();
        boolean[] done = {false};
        Runnable back = () -> {
            if (done[0] || !p.isOnline()) {
                return;
            }
            done[0] = true;
            recasts.remove(p.getUniqueId());
            Location from = p.getLocation().add(0, 1, 0);
            Vfx.line(from, mark.clone().add(0, 1, 0), Color.fromRGB(255, 250, 200), 1.0f, 0.4);
            if (mark.getWorld().equals(p.getWorld())) {
                p.teleport(mark);
            }
            p.setFallDistance(0);
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(Math.min(max == null ? 20.0 : max.getValue(), Math.max(p.getHealth(), health)));
            Vfx.sound(mark, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f);
            Vfx.sound(mark, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 0.6f);
            Release release = releases.get(p);
            if (release != null) {
                Cast now = new Cast(p, release, c.amp, c.el, c.color);
                for (LivingEntity entity : enemies(now, mark, 2.5 * c.amp)) {
                    strike(now, entity, 3);
                }
            }
        };
        long window = (long) (5000 * c.amp);
        recast(p, window, back);
        Vfx.sound(mark, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);
        every(p, (int) (window / 50), 4, tick -> {
            if (done[0]) {
                return;
            }
            // A clock face where the moment was marked.
            Location face = mark.clone().add(0, 1, 0);
            Vfx.ring(face, 0.8, 16, Color.fromRGB(255, 250, 200), 0.9f);
            double hand = -tick * 0.12;
            Vfx.line(face, face.clone().add(Math.cos(hand) * 0.7, 0, Math.sin(hand) * 0.7), Color.fromRGB(255, 70, 90), 0.8f, 0.15);
            if (tick >= window / 50) {
                back.run();
            }
        });
        return true;
    }

    private boolean shuffle(Cast c) {
        Player p = c.p;
        List<LivingEntity> group = new ArrayList<>(enemies(c, p.getLocation(), 8 * c.amp));
        if (group.isEmpty()) {
            p.sendActionBar(Component.text("No enemies to shuffle.", NamedTextColor.GRAY));
            return false;
        }
        Collections.shuffle(group);
        group = new ArrayList<>(group.subList(0, Math.min(group.size(), 6)));
        group.add(p);
        List<Location> spots = new ArrayList<>();
        for (LivingEntity entity : group) {
            spots.add(entity.getLocation());
        }
        Collections.rotate(spots, 1 + (int) (Math.random() * (spots.size() - 1)));
        Color[] cards = {Color.RED, Color.WHITE, Color.fromRGB(120, 255, 140), Color.BLACK};
        for (int i = 0; i < group.size(); i++) {
            LivingEntity entity = group.get(i);
            Location to = spots.get(i).clone().setDirection(entity.getLocation().getDirection());
            Vfx.burst(entity.getLocation().add(0, 1, 0), cards[i % cards.length], 25, 0.4, 1.5f);
            entity.teleport(to);
            Vfx.burst(to.clone().add(0, 1, 0), cards[(i + 1) % cards.length], 25, 0.4, 1.5f);
            if (!entity.equals(p)) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0));
                strike(c, entity, 2);
            }
        }
        Vfx.sound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 2.0f, 0.6f);
        Vfx.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 1.2f);
        return true;
    }

    private boolean echoTurret(Cast c) {
        Player p = c.p;
        Location at = p.getLocation().add(0, 2.4, 0);
        BlockDisplay orb = block(at, Material.LIGHT_BLUE_STAINED_GLASS, 0.6f, 0.6f, 0.6f);
        int ticks = (int) (120 * c.amp);
        Vfx.sound(at, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.6f);
        every(p, ticks, 2, tick -> {
            if (tick >= ticks) {
                remove(orb);
                return;
            }
            at.getWorld().spawnParticle(Particle.SPLASH, at.clone().add(0, 0.3, 0), 3, 0.3, 0.3, 0.3, 0);
            if (tick % 16 != 0) {
                return;
            }
            LivingEntity best = null;
            double distance = 14 * 14;
            for (LivingEntity entity : enemies(c, at, 14)) {
                double d = entity.getLocation().distanceSquared(at);
                if (d < distance && p.hasLineOfSight(entity)) {
                    distance = d;
                    best = entity;
                }
            }
            if (best != null) {
                Location from = at.clone().add(0, 0.3, 0);
                Vfx.line(from, best.getLocation().add(0, 1, 0), Color.fromRGB(60, 150, 255), 1.1f, 0.35);
                Vfx.sound(from, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.6f);
                strike(c, best, 2.5);
            }
        });
        return true;
    }

    private boolean meteorKick(Cast c) {
        Player p = c.p;
        Vector forward = flat(p);
        boolean[] kicked = {false};
        Vfx.sound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1.2f, 0.8f);
        every(p, 10, 1, tick -> {
            if (kicked[0]) {
                return;
            }
            p.setVelocity(forward.clone().multiply(1.6).setY(0.05));
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 0.3, 0), 4, 0.2, 0.1, 0.2, 0.02);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.05);
            for (LivingEntity entity : enemies(c, p.getLocation().add(0, 1, 0), 1.7)) {
                kicked[0] = true;
                strike(c, entity, 5);
                later(1, () -> entity.setVelocity(new Vector(0, 1.25, 0)));
                Vfx.sound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
                later(10, () -> {
                    if (!p.isOnline() || !entity.isValid()) {
                        return;
                    }
                    Location above = entity.getLocation().add(0, 1.5, 0);
                    if (standable(above)) {
                        p.teleport(above.setDirection(new Vector(0, -1, 0)));
                    }
                    p.setFallDistance(0);
                    later(4, () -> {
                        Location at = entity.getLocation();
                        Location point = at.clone().add(0, 10, 0);
                        while (point.getY() > at.getY()) {
                            Location next = point.clone().add((Math.random() - 0.5) * 0.8, -1.2, (Math.random() - 0.5) * 0.8);
                            Vfx.line(point, next, Color.fromRGB(170, 230, 255), 1.4f, 0.25);
                            point = next;
                        }
                        Vfx.sound(at, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
                        Vfx.shockwave(plugin, at, c.color, 3 * c.amp, 6);
                        Release release = releases.get(p);
                        if (release != null) {
                            Cast now = new Cast(p, release, c.amp, c.el, c.color);
                            entity.setNoDamageTicks(0);
                            strike(now, entity, 5);
                            entity.setVelocity(new Vector(0, -1.5, 0));
                            for (LivingEntity other : enemies(now, at, 3 * c.amp)) {
                                if (!other.equals(entity)) {
                                    strike(now, other, 2);
                                }
                            }
                        }
                    });
                });
                break;
            }
        });
        return true;
    }

    private boolean moltenFloor(Cast c) {
        Player p = c.p;
        Location center = p.getLocation();
        double radius = 6 * c.amp;
        Vfx.sound(center, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 0.6f);
        Vfx.sound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.5f);
        every(p, 120, 4, tick -> {
            p.getWorld().spawnParticle(Particle.LAVA, center, 4, radius * 0.5, 0.05, radius * 0.5, 0);
            p.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 0.1, 0), 12, radius * 0.5, 0.02, radius * 0.5, 0.01);
            p.getWorld().spawnParticle(Particle.DUST, center.clone().add(0, 0.1, 0), 10, radius * 0.5, 0.02, radius * 0.5, 0,
                    Vfx.dust(Color.fromRGB(255, 90, 20), 1.5f));
            if (tick % 8 == 0) {
                ring(center.clone().add(0, 0.1, 0), radius, Color.fromRGB(255, 120, 30));
            }
            for (Player ally : allies(p, center, radius)) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, false, false, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, false, false, true));
            }
            for (LivingEntity entity : enemies(c, center, radius)) {
                entity.setFireTicks(Math.max(entity.getFireTicks(), 40));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0));
                if (tick % 20 == 0) {
                    strike(c, entity, 1);
                }
            }
        });
        return true;
    }

    private boolean lastLine(Cast c) {
        Player p = c.p;
        int ticks = (int) (160 * c.amp);
        give(p, "last_line", ticks * 50L);
        Vfx.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        every(p, ticks, 6, tick -> {
            if (has(p, "last_line")) {
                ring(p.getLocation().add(0, 0.1, 0), 10, Color.fromRGB(170, 230, 255));
            }
        });
        return true;
    }

    private void encase(Player saved, Player keeper) {
        Location at = saved.getLocation();
        BlockDisplay ice = block(at, Material.PACKED_ICE, 1.4f, 2.3f, 1.4f);
        give(saved, "invulnerable", 1500);
        saved.setHealth(Math.max(saved.getHealth(), 1.0));
        heal(saved, 6);
        saved.setVelocity(new Vector());
        saved.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 10, false, false, false));
        Vfx.sound(at, Sound.BLOCK_GLASS_PLACE, 1.4f, 0.5f);
        Vfx.sound(at, Sound.ITEM_TOTEM_USE, 0.6f, 1.6f);
        saved.sendMessage(Component.text((saved.equals(keeper) ? "Your" : keeper.getName() + "'s")
                + " Last Line holds!", NamedTextColor.AQUA));
        later(30, () -> {
            remove(ice);
            Vfx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.7f);
            at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 1, 0), 40, 0.6, 1.0, 0.6, 0,
                    Material.PACKED_ICE.createBlockData());
        });
    }

    // ================================================================ events

    private static Player attackingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private Cast castOf(Player player) {
        Release release = releases.get(player);
        return release == null ? null
                : new Cast(player, release, release.advanced ? 1.5 : 1.0, release.element, release.form.color());
    }

    /** Invulnerability windows and lethal-save effects run first. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (has(victim, "invulnerable")) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() >= victim.getHealth() + victim.getAbsorptionAmount()) {
            for (Player keeper : victim.getLocation().getNearbyPlayers(10)) {
                if (has(keeper, "last_line") && (keeper.equals(victim) || AwakeningPowers.allies(keeper, victim))) {
                    clear(keeper, "last_line");
                    event.setCancelled(true);
                    encase(victim, keeper);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Player attacker = attackingPlayer(event);
        boolean melee = event.getDamager() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK;

        // Decoys explode when an enemy strikes them; the owner can't pop their own.
        if (victim instanceof ArmorStand stand && decoys.containsKey(stand.getUniqueId())) {
            event.setCancelled(true);
            Player owner = plugin.getServer().getPlayer(decoys.get(stand.getUniqueId()));
            if (owner != null && !owner.equals(attacker)) {
                explodeDecoy(stand, owner);
            }
            return;
        }

        // Defensive effects on a signature user.
        if (victim instanceof Player defender
                && (event.getDamager() instanceof LivingEntity || event.getDamager() instanceof Projectile)) {
            Entity damager = event.getDamager();
            LivingEntity source = damager instanceof LivingEntity living ? living
                    : damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter
                            ? shooter : null;
            if (has(defender, "feint") && source != null && !source.equals(defender)) {
                clear(defender, "feint");
                event.setCancelled(true);
                Vector facing = source.getLocation().getDirection().setY(0);
                Location behind = source.getLocation().subtract(facing.lengthSquared() < 0.01 ? new Vector(0, 0, 1)
                        : facing.normalize().multiply(1.3));
                Vfx.burst(defender.getLocation().add(0, 1, 0), Color.fromRGB(170, 140, 255), 30, 0.4, 1.4f);
                if (standable(behind)) {
                    defender.teleport(behind.setDirection(source.getLocation().toVector().subtract(behind.toVector())));
                }
                Vfx.sound(defender.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 1.6f);
                Cast c = castOf(defender);
                if (c != null && service.canStrike(defender, source)) {
                    source.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                    later(1, () -> strike(c, source, 4 * c.amp));
                }
                return;
            }
            if (has(defender, "shell") && damager instanceof Projectile projectile && source != null) {
                event.setCancelled(true);
                Vfx.line(defender.getLocation().add(0, 1, 0), source.getLocation().add(0, 1, 0),
                        Color.fromRGB(60, 160, 115), 1.0f, 0.4);
                Vfx.sound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 1.2f);
                projectile.remove();
                Cast c = castOf(defender);
                if (c != null && service.canStrike(defender, source)) {
                    double amount = event.getDamage();
                    later(1, () -> strike(c, source, Math.max(2, amount)));
                }
                return;
            }
            if (has(defender, "shell") && damager instanceof LivingEntity && source != null
                    && service.canStrike(defender, source)) {
                Vector out = source.getLocation().toVector().subtract(defender.getLocation().toVector()).setY(0);
                if (out.lengthSquared() > 0.01) {
                    later(1, () -> source.setVelocity(out.normalize().multiply(1.2).setY(0.35)));
                }
                Cast c = castOf(defender);
                if (c != null) {
                    later(1, () -> strike(c, source, 2));
                }
            }
            if (has(defender, "coat") && damager instanceof LivingEntity && source != null && !source.equals(defender)) {
                double reflected = event.getDamage() * 0.6;
                event.setDamage(event.getDamage() * 0.4);
                Vfx.burst(defender.getLocation().add(0, 1, 0), Color.fromRGB(230, 180, 255), 20, 0.4, 1.3f);
                Vfx.sound(defender.getLocation(), Sound.BLOCK_GLASS_HIT, 1.2f, 1.6f);
                if (service.canStrike(defender, source)) {
                    later(1, () -> source.damage(reflected, defender));
                }
            }
        }

        if (attacker == null || !(victim instanceof LivingEntity target) || attacker.equals(victim)) {
            return;
        }
        UUID id = attacker.getUniqueId();

        // Blood Moon: hits on the marked target heal the caster.
        if (target.getUniqueId().equals(targets.get(id)) && has(attacker, "blood_moon")) {
            counters.merge(id, 1, Integer::sum);
            heal(attacker, event.getFinalDamage() * 0.25);
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0,
                    Vfx.dust(Color.fromRGB(200, 10, 30), 1.3f));
        }
        // Venom counts every hit the stinger's owner lands.
        if (id.equals(venom.get(target.getUniqueId()))) {
            venomHits.merge(target.getUniqueId(), 1, Integer::sum);
        }
        if (!service.canStrike(attacker, target)) {
            return;
        }
        Cast c = castOf(attacker);
        if (c == null) {
            return;
        }
        if (melee && has(attacker, "plunge")) {
            clear(attacker, "plunge");
            event.setDamage(event.getDamage() + 6 * c.amp);
            Vfx.shockwave(plugin, target.getLocation(), c.color, 3, 6);
            Vfx.sound(target.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 1.0f);
        }
        if (melee && has(attacker, "inferno")) {
            Location at = target.getLocation().add(0, 1, 0);
            at.getWorld().spawnParticle(Particle.FLAME, at, 30, 0.6, 0.6, 0.6, 0.08);
            at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
            Vfx.sound(at, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
            target.setFireTicks(80);
            for (LivingEntity entity : enemies(c, at, 2.2 * (c.amp > 1 ? 1.3 : 1.0))) {
                if (!entity.equals(target)) {
                    entity.setFireTicks(60);
                    later(1, () -> strike(c, entity, 3));
                }
            }
        }
        if (melee && has(attacker, "feral")) {
            int stacks = Math.min(3, counters.merge(id, 1, Integer::sum));
            heal(attacker, 1.5);
            long left = buffs(id).getOrDefault("feral", 0L) - System.currentTimeMillis();
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int) Math.max(20, left / 50), stacks - 1,
                    false, false, true));
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0.1);
        }
        if (melee && has(attacker, "shade")) {
            clear(attacker, "shade");
            reveal(attacker);
            event.setDamage(event.getDamage() + 5 * c.amp);
            target.getWorld().spawnParticle(Particle.PALE_OAK_LEAVES, target.getLocation().add(0, 1, 0), 40, 0.5, 0.6, 0.5, 0.05);
            Vfx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
        }
        if (!echoing && has(attacker, "echo") && counters.getOrDefault(id, 0) > 0) {
            int left = counters.merge(id, -1, Integer::sum);
            if (left <= 0) {
                clear(attacker, "echo");
            }
            double echo = event.getFinalDamage() * 0.5;
            later(20, () -> {
                if (!target.isValid() || target.isDead()) {
                    return;
                }
                echoing = true;
                try {
                    target.setNoDamageTicks(0);
                    target.damage(echo, attacker);
                } finally {
                    echoing = false;
                }
                Location at = target.getLocation().add(0, 1, 0);
                Vfx.ring(at, 0.8, 14, Color.fromRGB(255, 220, 80), 1.2f);
                target.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, 1, 0), 3, 0.3, 0.2, 0.3, 1);
                Vfx.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.4f);
            });
        }
    }

    /** Damage taken while a debt, link or tether is active. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || sharing) {
            return;
        }
        UUID id = victim.getUniqueId();
        double damage = event.getFinalDamage();
        if (has(victim, "debt")) {
            stored.merge(id, damage, Double::sum);
        }
        if (has(victim, "tether")) {
            Entity tethered = plugin.getServer().getEntity(targets.getOrDefault(id, new UUID(0, 0)));
            if (tethered instanceof LivingEntity living && living.isValid()) {
                sharing = true;
                try {
                    living.damage(damage * 0.3, victim);
                } finally {
                    sharing = false;
                }
            }
        }
    }

    /** Heart Link halves damage and passes the other half to the partner (never killing them). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLinkedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || sharing) {
            return;
        }
        UUID partnerId = links.get(victim.getUniqueId());
        Player partner = partnerId == null ? null : plugin.getServer().getPlayer(partnerId);
        if (partner == null || !partner.isOnline() || partner.isDead()) {
            return;
        }
        double half = event.getDamage() / 2;
        event.setDamage(half);
        partner.setHealth(Math.max(1.0, partner.getHealth() - half));
        partner.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, partner.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0);
    }

    @EventHandler
    public void onStingerHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || arrow.getPersistentDataContainer()
                .getOrDefault(markerKey, PersistentDataType.BYTE, (byte) 0) != 2) {
            return;
        }
        if (event.getHitEntity() instanceof LivingEntity target && arrow.getShooter() instanceof Player shooter
                && service.canStrike(shooter, target)) {
            injectVenom(shooter, target);
        }
        later(1, () -> remove(arrow));
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!has(player, "steps") || flying.contains(player.getUniqueId()) || !event.isFlying()) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        int left = counters.getOrDefault(player.getUniqueId(), 0);
        if (left <= 0) {
            player.setAllowFlight(false);
            return;
        }
        counters.put(player.getUniqueId(), left - 1);
        Vector boost = player.getLocation().getDirection().setY(0);
        boost = boost.lengthSquared() < 0.01 ? new Vector() : boost.normalize().multiply(0.6);
        player.setVelocity(boost.setY(0.85));
        player.setFallDistance(0);
        Location feet = player.getLocation();
        Release release = releases.get(player);
        Vfx.ring(feet.clone().add(0, 0.05, 0), 0.7, 14, release == null ? Color.WHITE : release.form.color(), 1.2f);
        player.getWorld().spawnParticle(Particle.CLOUD, feet, 8, 0.3, 0.05, 0.3, 0.02);
        Vfx.sound(feet, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.4f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGolemTarget(EntityTargetLivingEntityEvent event) {
        UUID ownerId = golems.get(event.getEntity().getUniqueId());
        if (ownerId == null || event.getTarget() == null) {
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner == null || !service.canStrike(owner, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cleanup(player);
        for (Set<UUID> from : hiddenFrom.values()) {
            from.remove(player.getUniqueId());
        }
    }

    /** Ends anything that must not outlive the release (flight, invisibility, tethers). */
    void onSeal(Player player) {
        if (player.isOnline()) {
            endFlight(player);
            if (has(player, "steps")) {
                clear(player, "steps");
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                    player.setAllowFlight(false);
                }
            }
            reveal(player);
        }
        recasts.remove(player.getUniqueId());
        links.remove(player.getUniqueId());
    }

    private void cleanup(Player player) {
        onSeal(player);
        UUID id = player.getUniqueId();
        buffs.remove(id);
        targets.remove(id);
        counters.remove(id);
        stored.remove(id);
        silencedUntil.remove(id);
    }

    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cleanup(player);
        }
        for (Entity entity : List.copyOf(spawned)) {
            entity.remove();
        }
        spawned.clear();
    }
}
