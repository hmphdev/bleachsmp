package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.awakening.Canon.Move;
import dev.bleachsmp.awakening.ReleaseService.Release;
import dev.bleachsmp.fx.Vfx;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
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
 * The real abilities: each kit's signature (Q) and advanced-form ability (F) from {@link Canon}. Many of them change
 * the rules of a fight rather than just dealing damage (hypnosis, inverted senses, death marks, shared wounds, borrowed
 * misfortune), so a single damage listener applies every active rule.
 */
public final class CanonMoves implements Listener {
    /** How a charmed enemy's attacks are bent. */
    private enum Charm { SPARE, REDIRECT, MISS, MEND }

    private record Bend(UUID caster, Charm kind, long until) {
    }

    private record Mark(UUID caster, int stacks, long until) {
    }

    private record Bet(UUID target, int hits, long until) {
    }

    private record Line(List<LivingEntity> hits, Location end) {
    }

    private static final Set<PotionEffectType> GOOD = Set.of(PotionEffectType.SPEED, PotionEffectType.STRENGTH,
            PotionEffectType.RESISTANCE, PotionEffectType.REGENERATION, PotionEffectType.ABSORPTION,
            PotionEffectType.HASTE, PotionEffectType.JUMP_BOOST, PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.INVISIBILITY, PotionEffectType.HEALTH_BOOST, PotionEffectType.SLOW_FALLING);
    private static final Set<PotionEffectType> BAD = Set.of(PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS,
            PotionEffectType.NAUSEA, PotionEffectType.MINING_FATIGUE, PotionEffectType.HUNGER, PotionEffectType.LEVITATION);

    private final BleachSmpPlugin plugin;
    private final AwakeningService service;
    private final ReleaseService releases;
    private final NamespacedKey markerKey;
    private final NamespacedKey scaleKey;
    private final NamespacedKey anchorKey;

    private final Map<UUID, EnumMap<Move, Long>> modes = new HashMap<>();
    private final Map<UUID, Bend> bends = new HashMap<>();
    /** Per-target marks, keyed "target:move". */
    private final Map<String, Mark> marks = new HashMap<>();
    /** Per-caster counters, keyed "caster:name". */
    private final Map<String, Integer> counters = new HashMap<>();
    /** Kyōka Suigetsu: everyone who saw the release, and who is currently under the illusion. */
    private final Map<UUID, Set<UUID>> hypnotized = new HashMap<>();
    private final Map<UUID, Set<UUID>> illusion = new HashMap<>();
    /** Irooni: caster → (target, multiplier, until). */
    private final Map<UUID, Object[]> games = new HashMap<>();
    /** Karamatsu Shinjū act one: caster → target that shares the caster's wounds. */
    private final Map<UUID, UUID> sharedWounds = new HashMap<>();
    /** Sōgyo no Kotowari and Cero Doble: absorbed damage. */
    private final Map<UUID, Double> absorbed = new HashMap<>();
    private final Map<UUID, Bet> bets = new HashMap<>();
    private final Map<UUID, Runnable> recasts = new HashMap<>();
    private final Map<UUID, Long> recastUntil = new HashMap<>();
    /** Summoned allies (zombies): mob → owner. */
    private final Map<UUID, UUID> minions = new HashMap<>();
    private final Set<Entity> spawned = new HashSet<>();
    private final Set<UUID> scaled = new HashSet<>();
    private boolean dealing;

    CanonMoves(BleachSmpPlugin plugin, AwakeningService service, ReleaseService releases) {
        this.plugin = plugin;
        this.service = service;
        this.releases = releases;
        markerKey = new NamespacedKey(plugin, "canon_entity");
        scaleKey = new NamespacedKey(plugin, "canon_scale");
        anchorKey = new NamespacedKey(plugin, "canon_anchor");
    }

    // ================================================================ entry points

    /** Called on every release. Kyōka Suigetsu hypnotises everyone who sees it. */
    void released(Player player, Release release) {
        UUID id = player.getUniqueId();
        absorbed.remove(id);
        counters.remove(id + ":dirt");
        counters.remove(id + ":overkill");
        if (release.kit.signature() != Move.KANZEN_SAIMIN) {
            return;
        }
        Set<UUID> seen = hypnotized.computeIfAbsent(id, key -> new HashSet<>());
        for (Player other : player.getLocation().getNearbyPlayers(40)) {
            if (!other.equals(player) && service.canStrike(player, other) && other.hasLineOfSight(player)) {
                seen.add(other.getUniqueId());
            }
        }
    }

    /** A pending "press Q again" for this player, used before the signature cooldown is checked. */
    boolean recast(Player player) {
        UUID id = player.getUniqueId();
        Runnable action = recasts.remove(id);
        Long until = recastUntil.remove(id);
        if (action != null && until != null && until > System.currentTimeMillis()) {
            action.run();
            return true;
        }
        return false;
    }

    boolean signature(Player p, Release r, double amp) {
        return switch (r.kit.signature()) {
            case JUJISHO -> jujisho(p, r, amp);
            case JOKAKU_ENJO -> jokakuEnjo(p, r, amp);
            case KANZEN_SAIMIN -> kanzenSaimin(p, r, amp);
            case IROONI -> irooni(p, r, amp);
            case SAKANADE -> sakanade(p, r, amp);
            case HYOTEN -> hyoten(p, r, amp);
            case TSUKISHIRO -> tsukishiro(p, r, amp);
            case HIGA_ZEKKO -> higaZekko(p, r, amp);
            case HAINEKO -> haineko(p, r, amp);
            case NIGEKI -> mode(p, Move.NIGEKI, (int) (200 * amp), "Hōmonka: sting the same spot twice.");
            case TENKEN -> tenken(p, r, amp);
            case WABISUKE -> mode(p, Move.WABISUKE, (int) (200 * amp), "Every cut doubles their weight.");
            case TACHIKAZE -> tachikaze(p, r, amp);
            case MINAZUKI -> minazuki(p, r, amp);
            case SOGYO -> absorb(p, r, amp, false);
            case ASHISOGI -> mode(p, Move.ASHISOGI, (int) (200 * amp), "Your cuts paralyse.");
            case IRA -> ira(p, r, amp);
            case HIERRO -> hierro(p, amp);
            case AMOR -> amor(p, r, amp);
            case DEVOUR -> devour(p, r, amp);
            case VOODOO -> voodoo(p, r, amp);
            case LUZ_LANZA -> luzLanza(p, r, amp);
            case LOBOS -> lobos(p, r, amp);
            case CASCADA -> cascada(p, r, amp);
            case REMOLINO -> remolino(p, r, amp);
            case GOLONDRINA -> golondrina(p, r, amp);
            case RESPIRA -> respira(p, r, amp);
            case DRAGON_RUGIR -> dragonRugir(p, r, amp);
            case BULL_CHARGE -> bullCharge(p, r, amp);
            case CERO_DOBLE -> absorb(p, r, amp, true);
            case TREPADORA -> trepadora(p, r, amp);
            case BURNER3 -> burner3(p, r, amp);
            case OVERKILL -> mode(p, Move.OVERKILL, (int) (200 * amp), "Every hit you land makes the next one stronger.");
            case POWER_PUNCH -> powerPunch(p, r, amp);
            case FEAR -> fear(p, r, amp);
            case VISIONARY -> visionary(p, r, amp);
            case LOVE_KISS -> loveKiss(p, r, amp);
            case X_AXIS -> xAxis(p, r, amp);
            case ANTITHESIS -> antithesis(p, r, amp);
            case GALVANO_BLAST -> galvanoBlast(p, r, amp);
            case WIND -> wind(p, amp, 3);
            case STAR_CHEER -> starCheer(p, amp);
            case EXPLODE -> mode(p, Move.EXPLODE, (int) (160 * amp), "Everything you hit becomes a bomb.");
            case EISERN -> eisern(p, amp);
            case MIRACLE -> mode(p, Move.MIRACLE, (int) (200 * amp), "Every wound makes you greater.");
            case BALANCE -> mode(p, Move.BALANCE, (int) (160 * amp), "Your misfortune is passed on.");
            case ZOMBIE -> zombie(p, r, amp);
            case JACKPOT -> jackpot(p, r, amp);
            case EL_DIRECTO -> elDirecto(p, r, amp);
            case INSERT -> insert(p, r, amp);
            case LA_MUERTE -> laMuerte(p, r, amp);
            case CROSS_STEAL -> crossSteal(p, r, amp);
            case DIRTY_BOOTS -> dirtyBoots(p, amp);
            case DOLLHOUSE -> dollhouse(p, r, amp);
            case INVADERS -> invaders(p, r, amp);
            case SOTEN -> soten(p, amp);
            case TIME_BET -> timeBet(p, r, amp);
            default -> false;
        };
    }

    boolean special(Player p, Release r) {
        return switch (r.kit.special()) {
            case TENSA -> tensa(p, r);
            case ZANKA -> zanka(p, r);
            case BERSERKER -> berserker(p);
            case MUGEN_KYOKA -> mugenKyoka(p);
            case KARAMATSU -> karamatsu(p, r);
            case HAPPO_FUSAGARI -> happoFusagari(p);
            case DAIGUREN -> daiguren(p, r);
            case KAGEYOSHI -> kageyoshi(p, r);
            case KAMISHINI -> kamishini(p, r);
            case HAKKA -> hakka(p, r);
            case HIKOTSU -> hikotsu(p, r);
            case ASH_STORM -> ashStorm(p, r);
            case JAKUHO -> jakuho(p, r);
            case MYOO -> myoo(p, r);
            case WEIGHT_FIELD -> weightField(p, r);
            case TEKKEN -> mode(p, Move.TEKKEN, 300, "Every blow explodes.");
            case MINAZUKI_BANKAI -> minazukiBankai(p, r);
            case KAMIKAKE -> kamikake(p);
            case KONJIKI -> konjiki(p, r);
            case DESGARRON -> desgarron(p, r);
            case FURIA -> grow(p, 2.4, 300, true);
            case SIX_ARMS -> mode(p, Move.SIX_ARMS, 300, "Six arms: every hit strikes three times.");
            case AMOR_MASS -> amorMass(p);
            case BANQUET -> banquet(p, r);
            case GABRIEL -> gabriel(p, r);
            case LANZA_RELAMPAGO -> lanzaRelampago(p, r);
            case METRALLETA -> metralleta(p, r);
            case GRAN_CAIDA -> granCaida(p, r);
            case SENESCENCIA -> senescencia(p, r);
            case LANZADOR -> lanzador(p, r);
            case TENTACLE_STORM -> tentacleStorm(p, r);
            case TRES_BESTIAS -> tresBestias(p, r);
            case BURNER4 -> burner4(p, r);
            case KILL_COUNT -> killCount(p);
            case POWER_QUAKE -> quake(p, r, 9, 10, Material.PINK_CONCRETE);
            case TATARFORAS -> tatarforas(p, r);
            case METEOR -> meteor(p, r);
            case LOVE_RAIN -> loveRain(p);
            case JILLIEL -> jilliel(p, r);
            case SPRENGER -> sprenger(p, r);
            case GALVANO_JAVELIN -> galvanoJavelin(p, r);
            case WIND_STORM -> windStorm(p, r);
            case STAR_ROCKET -> starRocket(p, r);
            case EXPLODE_BARRAGE -> explodeBarrage(p, r);
            case HOHEIT -> grow(p, 3.0, 300, true);
            case GLUTTON_MAW -> gluttonMaw(p, r);
            case BISKIEL -> biskiel(p, r);
            case AVARON -> mode(p, Move.AVARON, 200, "Every wound dealt to you is shared among your enemies.");
            case ZOMBIE_ARMY -> zombieArmy(p, 5);
            case TRIPLE_SEVEN -> mode(p, Move.TRIPLE_SEVEN, 300, "Rigged: every punch is a jackpot.");
            case GIGANTE -> gigante(p);
            case REWRITE -> rewrite(p);
            case CROSS_GETSUGA -> getsuga(p, r, Color.fromRGB(20, 20, 30), 14);
            case FILTHY_STOMP -> quake(p, r, 8, 10, Material.MUD);
            case BRING_IN -> bringIn(p);
            case FINAL_STAGE -> finalStage(p, r);
            case SHITEN -> mode(p, Move.SHITEN, 240, "I reject: attacks on you and your allies are sent back.");
            case TIME_LIMIT -> timeLimit(p);
            default -> false;
        };
    }

    // ================================================================ helpers

    private void strike(Player caster, Release r, LivingEntity target, double amount) {
        if (!target.isValid() || target.isDead()) {
            return;
        }
        boolean was = dealing;
        dealing = true;
        try {
            releases.strike(caster, target, amount, r);
        } finally {
            dealing = was;
        }
    }

    /** Plain damage that skips the faction and element extras, for rules that move damage around. */
    private void raw(LivingEntity target, double amount, Entity source) {
        if (!target.isValid() || target.isDead() || amount <= 0) {
            return;
        }
        boolean was = dealing;
        dealing = true;
        try {
            target.damage(amount, source);
        } finally {
            dealing = was;
        }
    }

    private List<LivingEntity> enemies(Player p, Location at, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity entity : at.getNearbyLivingEntities(radius)) {
            if (!minions.containsKey(entity.getUniqueId()) && service.canStrike(p, entity)) {
                list.add(entity);
            }
        }
        return list;
    }

    private List<Player> allies(Player p, double radius) {
        List<Player> list = new ArrayList<>();
        for (Player other : p.getLocation().getNearbyPlayers(radius)) {
            if (other.equals(p) || AwakeningPowers.allies(p, other)) {
                list.add(other);
            }
        }
        return list;
    }

    private LivingEntity nearest(Player p, Location at, double radius, Set<UUID> skip) {
        LivingEntity best = null;
        double distance = radius * radius;
        for (LivingEntity entity : enemies(p, at, radius)) {
            double d = entity.getLocation().distanceSquared(at);
            if (d < distance && (skip == null || !skip.contains(entity.getUniqueId()))) {
                distance = d;
                best = entity;
            }
        }
        return best;
    }

    private LivingEntity aimed(Player p, double range) {
        RayTraceResult result = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                range, 0.8, entity -> entity instanceof LivingEntity && !entity.equals(p));
        if (result != null && result.getHitEntity() instanceof LivingEntity target && service.canStrike(p, target)) {
            RayTraceResult wall = p.rayTraceBlocks(p.getEyeLocation().distance(target.getEyeLocation()));
            if (wall == null || wall.getHitBlock() == null) {
                return target;
            }
        }
        p.sendActionBar(Component.text("No enemy in sight.", NamedTextColor.GRAY));
        return null;
    }

    private static Location aimPoint(Player p, double range) {
        RayTraceResult result = p.rayTraceBlocks(range);
        Location point = result != null ? result.getHitPosition().toLocation(p.getWorld())
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));
        Location ground = ReleaseService.groundAt(point);
        return ground != null ? ground : point;
    }

    private static Vector flat(Player p) {
        Vector v = p.getLocation().getDirection().setY(0);
        return v.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : v.normalize();
    }

    private static Location center(LivingEntity entity) {
        return entity.getLocation().add(0, entity.getHeight() / 2, 0);
    }

    private static boolean inCone(Player p, LivingEntity target, double range, double degrees) {
        Vector to = center(target).toVector().subtract(p.getEyeLocation().toVector());
        double length = to.length();
        if (length > range || length < 0.01) {
            return length < 0.01;
        }
        return Math.toDegrees(to.normalize().angle(p.getEyeLocation().getDirection())) <= degrees;
    }

    /** Runs {@code body} every {@code period} ticks for {@code ticks} ticks; the last call is always at {@code ticks}. */
    private void every(Player caster, int ticks, int period, IntConsumer body) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                int current = caster.isOnline() ? Math.min(tick, ticks) : ticks;
                try {
                    body.accept(current);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Ability effect failed: " + exception);
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
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Ability effect failed: " + exception);
            }
        }, ticks);
    }

    private void recastLater(Player p, long millis, Runnable action) {
        recasts.put(p.getUniqueId(), action);
        recastUntil.put(p.getUniqueId(), System.currentTimeMillis() + millis);
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
        return track(at.getWorld().spawn(at.clone().setDirection(new Vector(0, 0, 1)), BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(-x / 2, 0, -z / 2), new Quaternionf(),
                    new Vector3f(x, y, z), new Quaternionf()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(2);
        }));
    }

    private void temporary(Location at, Material material, float x, float y, float z, int ticks) {
        BlockDisplay display = block(at, material, x, y, z);
        later(ticks, () -> remove(display));
    }

    private void mode(Player p, Move move, int ticks) {
        modes.computeIfAbsent(p.getUniqueId(), id -> new EnumMap<>(Move.class))
                .put(move, System.currentTimeMillis() + ticks * 50L);
    }

    private boolean mode(Player p, Move move, int ticks, String text) {
        mode(p, move, ticks);
        p.sendActionBar(Component.text(text, NamedTextColor.GOLD));
        return true;
    }

    private boolean inMode(Entity entity, Move move) {
        EnumMap<Move, Long> map = modes.get(entity.getUniqueId());
        return map != null && map.getOrDefault(move, 0L) > System.currentTimeMillis();
    }

    private void endMode(Entity entity, Move move) {
        EnumMap<Move, Long> map = modes.get(entity.getUniqueId());
        if (map != null) {
            map.remove(move);
        }
    }

    private Mark mark(Entity target, Move move) {
        Mark mark = marks.get(target.getUniqueId() + ":" + move);
        return mark != null && mark.until() > System.currentTimeMillis() ? mark : null;
    }

    private void setMark(Entity target, Move move, UUID caster, int stacks, int ticks) {
        marks.put(target.getUniqueId() + ":" + move, new Mark(caster, stacks, System.currentTimeMillis() + ticks * 50L));
    }

    private void clearMark(Entity target, Move move) {
        marks.remove(target.getUniqueId() + ":" + move);
    }

    private int counter(UUID id, String name) {
        return counters.getOrDefault(id + ":" + name, 0);
    }

    private int bump(UUID id, String name, int by) {
        return counters.merge(id + ":" + name, by, Integer::sum);
    }

    private void bend(LivingEntity enemy, Player caster, Charm kind, int ticks) {
        bends.put(enemy.getUniqueId(), new Bend(caster.getUniqueId(), kind, System.currentTimeMillis() + ticks * 50L));
        if (kind == Charm.SPARE && enemy instanceof Mob mob && caster.equals(mob.getTarget())) {
            mob.setTarget(null);
        }
    }

    private Bend bendOf(Entity enemy) {
        Bend bend = bends.get(enemy.getUniqueId());
        return bend != null && bend.until() > System.currentTimeMillis() ? bend : null;
    }

    private static void effect(LivingEntity entity, PotionEffectType type, int ticks, int amplifier) {
        entity.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    /** Holds an entity in place: no walking, no jumping. */
    private static void root(LivingEntity entity, int ticks) {
        effect(entity, PotionEffectType.SLOWNESS, ticks, 9);
        effect(entity, PotionEffectType.JUMP_BOOST, ticks, 128);
        entity.setVelocity(new Vector(0, Math.min(0, entity.getVelocity().getY()), 0));
    }

    private static void heal(LivingEntity entity, double amount) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        entity.setHealth(Math.min(max == null ? 20.0 : max.getValue(), Math.max(0, entity.getHealth()) + amount));
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20.0 : max.getValue();
    }

    private static void cleanse(LivingEntity entity) {
        for (PotionEffect active : entity.getActivePotionEffects()) {
            if (BAD.contains(active.getType())) {
                entity.removePotionEffect(active.getType());
            }
        }
    }

    /** Moves the target's good effects to the thief. */
    private static int steal(LivingEntity from, LivingEntity thief) {
        int stolen = 0;
        for (PotionEffect active : from.getActivePotionEffects()) {
            if (GOOD.contains(active.getType())) {
                from.removePotionEffect(active.getType());
                thief.addPotionEffect(new PotionEffect(active.getType(), Math.min(active.getDuration(), 400),
                        active.getAmplifier()));
                stolen++;
            }
        }
        return stolen;
    }

    /** Scales an entity up or down for a while; the modifier is transient so it never sticks across restarts. */
    private void scale(LivingEntity entity, double multiplier, int ticks) {
        AttributeInstance attribute = entity.getAttribute(Attribute.SCALE);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(scaleKey);
        attribute.addTransientModifier(new AttributeModifier(scaleKey, multiplier - 1,
                AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        scaled.add(entity.getUniqueId());
        long token = System.nanoTime();
        counters.put(entity.getUniqueId() + ":scale", (int) token);
        later(ticks, () -> {
            if (counters.getOrDefault(entity.getUniqueId() + ":scale", 0) == (int) token) {
                unscale(entity);
            }
        });
    }

    private void unscale(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.SCALE);
        if (attribute != null) {
            attribute.removeModifier(scaleKey);
        }
        scaled.remove(entity.getUniqueId());
        counters.remove(entity.getUniqueId() + ":scale");
    }

    private double currentScale(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.SCALE);
        return attribute == null ? 1.0 : attribute.getValue();
    }

    private static void anchoredResistance(LivingEntity entity, boolean on, NamespacedKey key) {
        AttributeInstance attribute = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(key);
        if (on) {
            attribute.addTransientModifier(new AttributeModifier(key, 1.0, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY));
        }
    }

    /** A straight line out from {@code from}: every enemy within {@code width}, stopping at walls unless told not to. */
    private Line line(Player p, Location from, Vector direction, double range, double width, boolean walls) {
        Vector step = direction.clone().normalize().multiply(0.5);
        Location point = from.clone();
        Set<UUID> seen = new HashSet<>();
        List<LivingEntity> hits = new ArrayList<>();
        for (double travelled = 0; travelled < range; travelled += 0.5) {
            point.add(step);
            if (!walls && !point.getBlock().isPassable()) {
                break;
            }
            for (LivingEntity entity : enemies(p, point, width)) {
                if (seen.add(entity.getUniqueId())) {
                    hits.add(entity);
                }
            }
        }
        return new Line(hits, point);
    }

    private static void beam(Location from, Location to, Color color, float size, double spacing) {
        Vfx.line(from, to, color, size, spacing);
    }

    /**
     * A travelling projectile. It moves {@code speed} blocks a tick, stops at walls, and calls {@code onHit} for each
     * enemy it touches; with {@code pierce} it keeps going. {@code onEnd} gets where it stopped.
     */
    private void projectile(Player p, Location start, Vector direction, double speed, double range, double radius,
            boolean pierce, Consumer<Location> trail, BiConsumer<LivingEntity, Location> onHit, Consumer<Location> onEnd) {
        Vector step = direction.clone().normalize().multiply(0.5);
        int steps = Math.max(1, (int) Math.round(speed / 0.5));
        Location point = start.clone();
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double travelled;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                for (int i = 0; i < steps; i++) {
                    point.add(step);
                    travelled += 0.5;
                    boolean wall = !point.getBlock().isPassable();
                    boolean stop = wall || travelled >= range;
                    for (LivingEntity entity : enemies(p, point, radius)) {
                        if (hit.add(entity.getUniqueId())) {
                            onHit.accept(entity, point.clone());
                            if (!pierce) {
                                stop = true;
                                break;
                            }
                        }
                    }
                    if (i % 2 == 0) {
                        trail.accept(point.clone());
                    }
                    if (stop) {
                        if (onEnd != null) {
                            onEnd.accept(point.clone());
                        }
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** An effect over an area that may move, applied to every enemy inside each period. */
    private void zone(Player p, Supplier<Location> where, double radius, int ticks, int period,
            Consumer<Location> visual, BiConsumer<LivingEntity, Integer> perEnemy) {
        every(p, ticks, period, tick -> {
            Location at = where.get();
            if (at == null || tick >= ticks) {
                return;
            }
            visual.accept(at);
            for (LivingEntity entity : enemies(p, at, radius)) {
                perEnemy.accept(entity, tick);
            }
        });
    }

    private void explosion(Player p, Release r, Location at, double radius, double damage, double knock) {
        at.getWorld().spawnParticle(radius > 4 ? Particle.EXPLOSION_EMITTER : Particle.EXPLOSION, at, 1);
        Vfx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, radius > 4 ? 0.6f : 1.0f);
        for (LivingEntity entity : enemies(p, at, radius)) {
            strike(p, r, entity, damage);
            if (knock > 0) {
                Vector away = entity.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (away.lengthSquared() < 0.01) {
                    away = new Vector(0, 0, 0.1);
                }
                entity.setVelocity(away.normalize().multiply(knock).setY(0.45));
            }
        }
    }

    private void title(Player p, String text, Color color) {
        p.sendActionBar(Component.text(text, net.kyori.adventure.text.format.TextColor.color(color.asRGB()),
                TextDecoration.BOLD));
    }

    private boolean active(Player p, Release r) {
        return p.isOnline() && releases.get(p) == r;
    }

    // ================================================================ rule listener

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (dealing || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        LivingEntity attacker = event.getDamager() instanceof LivingEntity living ? living
                : event.getDamager() instanceof Projectile projectile
                        && projectile.getShooter() instanceof LivingEntity shooter ? shooter : null;
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        boolean melee = event.getDamager() == attacker && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK;

        // Bent enemies: hypnosis, Amor, charm, inserted memories, inverted senses.
        Bend bend = bendOf(attacker);
        if (bend != null) {
            boolean atCaster = victim.getUniqueId().equals(bend.caster());
            switch (bend.kind()) {
                case SPARE -> {
                    if (atCaster) {
                        event.setCancelled(true);
                        Vfx.sound(victim.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.5f, 1.6f);
                        return;
                    }
                }
                case MISS -> {
                    if (atCaster && ThreadLocalRandom.current().nextBoolean()) {
                        event.setCancelled(true);
                        victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center(victim), 1);
                        return;
                    }
                }
                case MEND -> {
                    if (atCaster) {
                        event.setCancelled(true);
                        heal(victim, event.getDamage() * 0.5);
                        victim.getWorld().spawnParticle(Particle.CRIMSON_SPORE, center(victim), 20, 0.4, 0.6, 0.4, 0);
                        return;
                    }
                }
                case REDIRECT -> {
                    LivingEntity other = null;
                    double best = 36;
                    for (LivingEntity entity : attacker.getLocation().getNearbyLivingEntities(6)) {
                        double d = entity.getLocation().distanceSquared(attacker.getLocation());
                        if (!entity.equals(attacker) && !entity.getUniqueId().equals(bend.caster())
                                && !(entity instanceof ArmorStand) && d < best) {
                            best = d;
                            other = entity;
                        }
                    }
                    event.setCancelled(true);
                    if (other != null) {
                        raw(other, event.getDamage(), attacker);
                        beam(center(attacker), center(other), Color.fromRGB(150, 110, 220), 0.8f, 0.4);
                    }
                    return;
                }
            }
        }

        if (victim instanceof Player defender) {
            defend(event, defender, attacker, melee);
            if (event.isCancelled()) {
                return;
            }
        }
        // Allies shielded by Shiten Kōshun.
        for (Player guard : victim.getLocation().getNearbyPlayers(8)) {
            if (inMode(guard, Move.SHITEN) && (guard.equals(victim)
                    || victim instanceof Player ally && AwakeningPowers.allies(guard, ally))
                    && !(attacker instanceof Player a && (a.equals(guard) || AwakeningPowers.allies(guard, a)))) {
                event.setCancelled(true);
                raw(attacker, event.getDamage(), guard);
                Vfx.ring(center(victim), 0.9, 16, Color.fromRGB(255, 190, 120), 1.2f);
                Vfx.sound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.4f);
                return;
            }
        }
        if (attacker instanceof Player caster && service.canStrike(caster, victim)) {
            attack(event, caster, victim, melee);
        }
    }

    /** Rules that protect the one being hit. */
    private void defend(EntityDamageByEntityEvent event, Player p, LivingEntity attacker, boolean melee) {
        UUID id = p.getUniqueId();
        Release release = releases.get(p);
        if (release != null && release.kit.signature() == Move.DIRTY_BOOTS) {
            bump(id, "dirt", 1);
        }
        if (inMode(p, Move.WIND) && counter(id, "wind") > 0) {
            bump(id, "wind", -1);
            event.setCancelled(true);
            p.getWorld().spawnParticle(Particle.SMALL_GUST, center(p), 8, 0.4, 0.6, 0.4, 0.02);
            Vfx.sound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.6f);
            return;
        }
        if (inMode(p, Move.SOGYO) || inMode(p, Move.CERO_DOBLE)) {
            absorbed.merge(id, event.getDamage(), Double::sum);
            event.setCancelled(true);
            p.getWorld().spawnParticle(inMode(p, Move.SOGYO) ? Particle.ELECTRIC_SPARK : Particle.GLOW, center(p),
                    14, 0.5, 0.6, 0.5, 0.05);
            Vfx.sound(p.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.4f, 1.8f);
            return;
        }
        UUID karma = sharedWounds.get(id);
        if (karma != null && plugin.getServer().getEntity(karma) instanceof LivingEntity bound) {
            raw(bound, event.getDamage(), p);
            beam(center(p), center(bound), Color.fromRGB(230, 120, 170), 0.8f, 0.4);
        }
        if (inMode(p, Move.AVARON)) {
            List<LivingEntity> share = enemies(p, p.getLocation(), 12);
            if (!share.isEmpty()) {
                double part = event.getDamage() / share.size();
                for (LivingEntity enemy : share) {
                    raw(enemy, part, p);
                    beam(center(p), center(enemy), Color.fromRGB(40, 50, 70), 0.7f, 0.5);
                }
                event.setCancelled(true);
                return;
            }
        }
        if (inMode(p, Move.BALANCE)) {
            LivingEntity enemy = nearest(p, p.getLocation(), 12, null);
            if (enemy != null) {
                double half = event.getDamage() / 2;
                event.setDamage(half);
                raw(enemy, half, p);
                beam(center(p), center(enemy), Color.fromRGB(200, 200, 210), 0.7f, 0.5);
            }
        }
        if (inMode(p, Move.HIERRO)) {
            event.setDamage(event.getDamage() * 0.4);
            p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center(p), 8, 0.3, 0.5, 0.3, 0);
        }
        if (inMode(p, Move.EISERN) || inMode(p, Move.GIGANTE)) {
            boolean iron = inMode(p, Move.EISERN);
            event.setDamage(event.getDamage() * (iron ? 0.5 : 0.6));
            if (melee) {
                raw(attacker, iron ? 2 : 3, p);
            }
            Vfx.sound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.5f, 1.4f);
        }
        if (inMode(p, Move.MIRACLE) && currentScale(p) < 1.6) {
            scale(p, Math.min(1.6, currentScale(p) + 0.08), 200);
            effect(p, PotionEffectType.STRENGTH, 100, currentScale(p) > 1.3 ? 1 : 0);
        }
        Object[] game = games.get(id);
        if (game != null && (long) game[2] > System.currentTimeMillis() && attacker.getUniqueId().equals(game[0])) {
            event.setDamage(event.getDamage() * (double) game[1]);
        }
    }

    /** Rules that change the attacker's hits. */
    private void attack(EntityDamageByEntityEvent event, Player p, LivingEntity victim, boolean melee) {
        UUID id = p.getUniqueId();
        Release r = releases.get(p);
        Object[] game = games.get(id);
        if (game != null && (long) game[2] > System.currentTimeMillis() && victim.getUniqueId().equals(game[0])) {
            event.setDamage(event.getDamage() * (double) game[1]);
        }
        Set<UUID> fooled = illusion.get(id);
        if (fooled != null && fooled.remove(victim.getUniqueId())) {
            event.setDamage(event.getDamage() * 1.8);
            if (victim instanceof Player viewer) {
                viewer.showPlayer(plugin, p);
            }
            victim.getWorld().spawnParticle(Particle.FALLING_WATER, center(victim), 30, 0.4, 0.8, 0.4, 0);
            Vfx.sound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
        }
        Mark frozen = mark(victim, Move.HAKKA);
        if (frozen != null && frozen.caster().equals(id)) {
            clearMark(victim, Move.HAKKA);
            event.setDamage(event.getDamage() * 1.8);
            victim.getWorld().spawnParticle(Particle.BLOCK, center(victim), 40, 0.4, 0.6, 0.4, 0,
                    Material.PACKED_ICE.createBlockData());
            Vfx.sound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        }
        Bet bet = bets.get(id);
        if (bet != null && bet.until() > System.currentTimeMillis() && bet.target().equals(victim.getUniqueId())) {
            bets.put(id, new Bet(bet.target(), bet.hits() + 1, bet.until()));
        }
        if (inMode(p, Move.OVERKILL)) {
            int stacks = Math.min(12, bump(id, "overkill", 1));
            event.setDamage(event.getDamage() * (1 + 0.05 * stacks));
        }
        if (!melee || r == null) {
            return;
        }
        if (inMode(p, Move.MINAZUKI_BANKAI)) {
            heal(p, event.getDamage() * 0.3);
        }
        if (inMode(p, Move.TRIPLE_SEVEN)) {
            event.setDamage(event.getDamage() * 2.2);
            victim.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center(victim), 16, 0.3, 0.4, 0.3, 0.3);
            Vfx.sound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);
        }
        if (inMode(p, Move.BERSERKER)) {
            int stacks = Math.min(8, bump(id, "berserk", 1));
            event.setDamage(event.getDamage() * (1 + 0.1 * stacks));
            for (LivingEntity other : enemies(p, victim.getLocation(), 3)) {
                if (!other.equals(victim)) {
                    later(1, () -> strike(p, r, other, event.getDamage() * 0.5));
                }
            }
            Vfx.arc(p.getLocation().add(0, 1.1, 0), flat(p), 2.6, Color.fromRGB(200, 10, 20), 1.6f, 0);
        }
        if (inMode(p, Move.TENSA)) {
            Location from = center(victim);
            Line cut = line(p, from, flat(p), 8, 1.4, false);
            beam(from, cut.end(), Color.fromRGB(20, 10, 20), 1.6f, 0.3);
            from.getWorld().spawnParticle(Particle.SQUID_INK, from, 12, 0.3, 0.3, 0.3, 0.05);
            for (LivingEntity other : cut.hits()) {
                if (!other.equals(victim)) {
                    later(1, () -> strike(p, r, other, 4));
                }
            }
        }
        if (inMode(p, Move.NIGEKI)) {
            Mark mark = mark(victim, Move.NIGEKI);
            if (mark != null && mark.caster().equals(id)) {
                clearMark(victim, Move.NIGEKI);
                double death = Math.min(16, Math.max(8, maxHealth(victim) * 0.35));
                later(1, () -> strike(p, r, victim, death));
                victim.getWorld().spawnParticle(Particle.DUST, center(victim), 50, 0.5, 0.7, 0.5, 0,
                        Vfx.dust(Color.fromRGB(255, 200, 40), 1.8f));
                Vfx.sound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
                Vfx.sound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
                title(p, "Nigeki Kessatsu!", Color.fromRGB(255, 200, 40));
            } else {
                setMark(victim, Move.NIGEKI, id, 1, 160);
                homonka(p, victim);
            }
        }
        if (inMode(p, Move.WABISUKE)) {
            weigh(p, r, victim, 1, event);
        }
        if (inMode(p, Move.ASHISOGI)) {
            Mark mark = mark(victim, Move.ASHISOGI);
            int stacks = mark == null ? 1 : mark.stacks() + 1;
            switch (stacks) {
                case 1 -> {
                    effect(victim, PotionEffectType.SLOWNESS, 100, 2);
                    p.sendActionBar(Component.text("Legs paralysed.", NamedTextColor.LIGHT_PURPLE));
                }
                case 2 -> {
                    effect(victim, PotionEffectType.WEAKNESS, 100, 1);
                    effect(victim, PotionEffectType.MINING_FATIGUE, 100, 2);
                    p.sendActionBar(Component.text("Arms paralysed.", NamedTextColor.LIGHT_PURPLE));
                }
                default -> {
                    root(victim, 50);
                    effect(victim, PotionEffectType.WEAKNESS, 60, 3);
                    p.sendActionBar(Component.text("Fully paralysed!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                    stacks = 0;
                }
            }
            victim.getWorld().spawnParticle(Particle.WITCH, center(victim), 12, 0.3, 0.5, 0.3, 0);
            if (stacks == 0) {
                clearMark(victim, Move.ASHISOGI);
            } else {
                setMark(victim, Move.ASHISOGI, id, stacks, 120);
            }
        }
        if (inMode(p, Move.TEKKEN) || inMode(p, Move.FURIA)) {
            Location at = victim.getLocation().add(0, 0.5, 0);
            boolean tekken = inMode(p, Move.TEKKEN);
            later(1, () -> explosion(p, r, at, tekken ? 3 : 3.5, 4, tekken ? 1.4 : 0.9));
        }
        if (inMode(p, Move.SIX_ARMS)) {
            double extra = event.getDamage() * 0.4;
            later(4, () -> {
                strike(p, r, victim, extra);
                Vfx.arc(center(victim), flat(p), 1.6, Color.fromRGB(220, 200, 90), 1.2f, 0.3);
            });
            later(8, () -> strike(p, r, victim, extra));
        }
        if (inMode(p, Move.EXPLODE)) {
            Location at = victim.getLocation().add(0, 0.6, 0);
            at.getWorld().spawnParticle(Particle.FLAME, at, 10, 0.3, 0.4, 0.3, 0.01);
            later(20, () -> explosion(p, r, victim.isValid() ? victim.getLocation().add(0, 0.6, 0) : at, 3, 5, 0.6));
        }
    }

    /** Suzumebachi's butterfly crest over a marked target. */
    private void homonka(Player p, LivingEntity victim) {
        every(p, 160, 10, tick -> {
            if (tick >= 160 || mark(victim, Move.NIGEKI) == null || !victim.isValid()) {
                return;
            }
            Location at = center(victim);
            Vfx.ring(at, 0.45, 10, Color.fromRGB(255, 210, 60), 1.0f);
            at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.1, 0.1, 0.1, 0, Vfx.dust(Color.BLACK, 1.0f));
        });
    }

    /** Wabisuke: each cut doubles the weight. Four stacks bow the head for the final cut. */
    private void weigh(Player p, Release r, LivingEntity victim, int add, EntityDamageByEntityEvent event) {
        Mark mark = mark(victim, Move.WABISUKE);
        int stacks = Math.min(4, (mark == null ? 0 : mark.stacks()) + add);
        effect(victim, PotionEffectType.SLOWNESS, 120, Math.min(4, stacks));
        if (stacks >= 3) {
            effect(victim, PotionEffectType.JUMP_BOOST, 120, 128);
            effect(victim, PotionEffectType.MINING_FATIGUE, 120, 1);
            victim.setVelocity(new Vector(0, -1, 0));
        }
        victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation(), 8 * stacks, 0.4, 0.1, 0.4, 0,
                Material.STONE.createBlockData());
        Vfx.sound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.25f + stacks * 0.1f, 1.6f - stacks * 0.2f);
        if (stacks >= 4 && event != null) {
            event.setDamage(event.getDamage() * 1.8);
            Location bowed = victim.getLocation();
            bowed.setPitch(80);
            victim.teleport(bowed);
            title(p, "Raise your head... no. Bow it.", Color.fromRGB(150, 150, 180));
            clearMark(victim, Move.WABISUKE);
            return;
        }
        setMark(victim, Move.WABISUKE, p.getUniqueId(), stacks, 160);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Entity mob = event.getEntity();
        if (event.getTarget() == null) {
            return;
        }
        UUID owner = minions.get(mob.getUniqueId());
        if (owner != null) {
            Player master = plugin.getServer().getPlayer(owner);
            if (master == null || !(event.getTarget() instanceof LivingEntity target) || !service.canStrike(master, target)
                    || minions.containsKey(target.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        Bend bend = bendOf(mob);
        if (bend != null && bend.kind() != Charm.MISS && event.getTarget().getUniqueId().equals(bend.caster())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.getPersistentDataContainer().has(markerKey)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            minions.remove(dead.getUniqueId());
            spawned.remove(dead);
        }
        for (Player p : dead.getLocation().getNearbyPlayers(12)) {
            if (inMode(p, Move.KILL_COUNT) && service.canStrike(p, dead)) {
                heal(p, maxHealth(p));
                int kills = bump(p.getUniqueId(), "kills", 1);
                effect(p, PotionEffectType.STRENGTH, 200, Math.min(2, kills));
                title(p, "Kill count: " + kills, Color.fromRGB(160, 30, 40));
                p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, center(p), 20, 0.4, 0.6, 0.4, 0.1);
            }
        }
        Mark blood = mark(dead, Move.ZOMBIE);
        if (blood != null && !(dead instanceof Player)) {
            Player owner = plugin.getServer().getPlayer(blood.caster());
            if (owner != null) {
                Location at = dead.getLocation();
                later(10, () -> minion(owner, at));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        unscale(p);
        anchoredResistance(p, false, anchorKey);
        modes.remove(id);
        bends.remove(id);
        hypnotized.remove(id);
        reveal(p);
        games.remove(id);
        sharedWounds.remove(id);
        absorbed.remove(id);
        bets.remove(id);
        recasts.remove(id);
        recastUntil.remove(id);
    }

    public void shutdown() {
        for (Entity entity : new ArrayList<>(spawned)) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        spawned.clear();
        for (UUID id : new ArrayList<>(scaled)) {
            if (plugin.getServer().getEntity(id) instanceof LivingEntity living) {
                unscale(living);
            }
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            anchoredResistance(p, false, anchorKey);
            reveal(p);
        }
    }

    private void reveal(Player p) {
        Set<UUID> fooled = illusion.remove(p.getUniqueId());
        if (fooled == null) {
            return;
        }
        for (UUID id : fooled) {
            Player other = plugin.getServer().getPlayer(id);
            if (other != null) {
                other.showPlayer(plugin, p);
            }
        }
    }

    // ================================================================ Soul Reaper

    private boolean jujisho(Player p, Release r, double amp) {
        Location eye = p.getEyeLocation();
        Line cut = line(p, eye, eye.getDirection(), 26 * Math.sqrt(amp), 2.0, false);
        Vector side = flat(p).crossProduct(new Vector(0, 1, 0)).normalize();
        Location end = cut.end();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                double t = Math.min(1, tick / 8.0);
                Location at = eye.clone().add(end.toVector().subtract(eye.toVector()).multiply(t));
                for (double s = -1.8; s <= 1.8; s += 0.3) {
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(side.clone().multiply(s)), 1, 0, 0, 0, 0,
                            Vfx.dust(Color.fromRGB(150, 210, 255), 1.6f));
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, s, 0), 1, 0, 0, 0, 0,
                            Vfx.dust(Color.fromRGB(150, 210, 255), 1.6f));
                }
                if (++tick > 8) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        Vfx.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        for (LivingEntity entity : cut.hits()) {
            strike(p, r, entity, 9 * amp);
        }
        return true;
    }

    private boolean getsuga(Player p, Release r, Color color, double damage) {
        Location eye = p.getEyeLocation();
        Line cut = line(p, eye, eye.getDirection(), 35, 3.0, false);
        Vector side = flat(p).crossProduct(new Vector(0, 1, 0)).normalize();
        Location end = cut.end();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                double t = Math.min(1, tick / 10.0);
                Location at = eye.clone().add(end.toVector().subtract(eye.toVector()).multiply(t));
                for (double s = -3; s <= 3; s += 0.35) {
                    Location point = at.clone().add(side.clone().multiply(s)).add(0, -Math.abs(s) * 0.35, 0);
                    at.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, Vfx.dust(color, 2.2f));
                    at.getWorld().spawnParticle(Particle.DUST, point, 1, 0.05, 0.05, 0.05, 0,
                            Vfx.dust(Color.fromRGB(200, 20, 30), 1.2f));
                }
                if (++tick > 10) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        Vfx.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
        for (LivingEntity entity : cut.hits()) {
            strike(p, r, entity, damage);
        }
        return true;
    }

    private boolean tensa(Player p, Release r) {
        getsuga(p, r, Color.fromRGB(15, 10, 15), 14);
        effect(p, PotionEffectType.SPEED, 400, 2);
        mode(p, Move.TENSA, 400);
        Vfx.helix(plugin, p, Color.fromRGB(20, 10, 20), 40, 2, 0.8);
        return true;
    }

    private boolean jokakuEnjo(Player p, Release r, double amp) {
        Location center = p.getLocation();
        double radius = 7 * Math.sqrt(amp);
        int ticks = (int) (160 * amp);
        Map<UUID, Long> burnt = new HashMap<>();
        Vfx.sound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.5f);
        every(p, ticks, 2, tick -> {
            if (tick >= ticks) {
                return;
            }
            for (int i = 0; i < 18; i++) {
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                Location at = center.clone().add(Math.cos(angle) * radius, ThreadLocalRandom.current().nextDouble(4),
                        Math.sin(angle) * radius);
                at.getWorld().spawnParticle(Particle.FLAME, at, 1, 0, 0.1, 0, 0.01);
            }
            for (LivingEntity entity : enemies(p, center, radius + 2)) {
                Vector out = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
                double distance = out.length();
                if (distance > radius - 1.2 && distance < radius + 1.5) {
                    entity.setVelocity(out.normalize().multiply(distance > radius ? 0.6 : -0.6).setY(0.2));
                    if (burnt.getOrDefault(entity.getUniqueId(), 0L) < System.currentTimeMillis()) {
                        burnt.put(entity.getUniqueId(), System.currentTimeMillis() + 500);
                        entity.setFireTicks(80);
                        strike(p, r, entity, 3);
                    }
                }
            }
        });
        return true;
    }

    private boolean zanka(Player p, Release r) {
        // Higashi: Kyokujitsujin — a single cut that erases everything along it.
        Location eye = p.getEyeLocation();
        Line cut = line(p, eye, eye.getDirection(), 40, 1.6, true);
        beam(eye, cut.end(), Color.fromRGB(255, 240, 200), 1.4f, 0.25);
        Vfx.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.4f);
        for (LivingEntity entity : cut.hits()) {
            strike(p, r, entity, 16);
        }
        // Nishi: Zanjitsu Gokui — the flames gather into the blade; the air itself burns.
        effect(p, PotionEffectType.FIRE_RESISTANCE, 260, 0);
        zone(p, p::getLocation, 6, 240, 10, at -> {
            at.getWorld().spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 16, 2.5, 1.0, 2.5, 0.01);
            at.getWorld().spawnParticle(Particle.LAVA, at.clone().add(0, 1, 0), 2, 1.5, 0.5, 1.5, 0);
        }, (entity, tick) -> {
            entity.setFireTicks(60);
            if (tick % 20 == 0) {
                strike(p, r, entity, 2);
            }
        });
        return true;
    }

    private boolean berserker(Player p) {
        counters.remove(p.getUniqueId() + ":berserk");
        mode(p, Move.BERSERKER, 400);
        effect(p, PotionEffectType.STRENGTH, 400, 0);
        anchoredResistance(p, true, anchorKey);
        later(400, () -> anchoredResistance(p, false, anchorKey));
        Vfx.sound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.7f);
        p.getWorld().spawnParticle(Particle.DUST, center(p), 80, 0.6, 1.0, 0.6, 0, Vfx.dust(Color.fromRGB(200, 0, 20), 2f));
        return true;
    }

    private boolean kanzenSaimin(Player p, Release r, double amp) {
        UUID id = p.getUniqueId();
        Set<UUID> seen = hypnotized.computeIfAbsent(id, key -> new HashSet<>());
        // Anyone looking at you right now also falls under it.
        for (Player other : p.getLocation().getNearbyPlayers(16)) {
            if (!other.equals(p) && service.canStrike(p, other) && other.hasLineOfSight(p)) {
                seen.add(other.getUniqueId());
            }
        }
        int ticks = (int) (160 * amp);
        Set<UUID> fooled = illusion.computeIfAbsent(id, key -> new HashSet<>());
        ArmorStand decoy = decoy(p);
        for (UUID viewer : seen) {
            Player other = plugin.getServer().getPlayer(viewer);
            if (other != null && other.getWorld().equals(p.getWorld())) {
                other.hidePlayer(plugin, p);
                other.showEntity(plugin, decoy);
                fooled.add(viewer);
            }
        }
        for (Entity entity : p.getNearbyEntities(24, 12, 24)) {
            if (entity instanceof Mob mob && p.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
        p.getWorld().spawnParticle(Particle.FALLING_WATER, center(p), 30, 0.5, 0.8, 0.5, 0);
        p.sendActionBar(Component.text("Kanzen Saimin: " + fooled.size() + " hypnotised.", NamedTextColor.LIGHT_PURPLE));
        later(ticks, () -> {
            remove(decoy);
            reveal(p);
        });
        return true;
    }

    private ArmorStand decoy(Player p) {
        Location at = p.getLocation();
        ArmorStand decoy = track(at.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setInvulnerable(true);
            // Only the hypnotised see it.
            stand.setVisibleByDefault(false);
            stand.customName(Component.text(p.getName()));
            stand.setCustomNameVisible(true);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(p);
                head.setItemMeta(skull);
            }
            stand.getEquipment().setHelmet(head);
            var armour = p.getEquipment();
            stand.getEquipment().setChestplate(plainCopy(armour.getChestplate()));
            stand.getEquipment().setLeggings(plainCopy(armour.getLeggings()));
            stand.getEquipment().setBoots(plainCopy(armour.getBoots()));
            stand.getEquipment().setItemInMainHand(plainCopy(armour.getItemInMainHand()));
            for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                try {
                    stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                } catch (IllegalArgumentException ignored) {
                    // Body and saddle slots don't apply to armour stands.
                }
            }
        }));
        return decoy;
    }

    private static ItemStack plainCopy(ItemStack item) {
        return item == null || item.isEmpty() ? null : new ItemStack(item.getType());
    }

    private boolean mugenKyoka(Player p) {
        UUID id = p.getUniqueId();
        Set<UUID> seen = hypnotized.computeIfAbsent(id, key -> new HashSet<>());
        List<LivingEntity> victims = new ArrayList<>();
        for (LivingEntity entity : enemies(p, p.getLocation(), 24)) {
            if (seen.contains(entity.getUniqueId()) || entity.hasLineOfSight(p)) {
                victims.add(entity);
                seen.add(entity.getUniqueId());
            }
        }
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("Nobody near you is under the illusion.", NamedTextColor.GRAY));
            return false;
        }
        Set<UUID> fooled = illusion.computeIfAbsent(id, key -> new HashSet<>());
        for (LivingEntity victim : victims) {
            bend(victim, p, Charm.REDIRECT, 240);
            effect(victim, PotionEffectType.NAUSEA, 200, 0);
            if (victim instanceof Player viewer) {
                viewer.hidePlayer(plugin, p);
                fooled.add(viewer.getUniqueId());
            }
            victim.getWorld().spawnParticle(Particle.FALLING_WATER, center(victim), 20, 0.4, 0.8, 0.4, 0);
        }
        later(240, () -> reveal(p));
        title(p, "Mugen Kyōka: " + victims.size() + " lost in the illusion", Color.fromRGB(150, 100, 220));
        return true;
    }

    private boolean irooni(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 16);
        if (target == null) {
            return false;
        }
        String[] names = {"Shiro (white)", "Aka (red)", "Kuro (black)"};
        double[] values = {1.15, 1.4, 1.8};
        Color[] colors = {Color.WHITE, Color.fromRGB(220, 30, 40), Color.fromRGB(20, 20, 20)};
        int pick = ThreadLocalRandom.current().nextInt(3);
        long until = System.currentTimeMillis() + (long) (8000 * amp);
        games.put(p.getUniqueId(), new Object[] {target.getUniqueId(), values[pick], until});
        games.put(target.getUniqueId(), new Object[] {p.getUniqueId(), values[pick], until});
        Component call = Component.text("Irooni: " + names[pick] + "! Both of you take x" + values[pick]
                + " damage from each other.", NamedTextColor.LIGHT_PURPLE);
        p.sendMessage(call);
        target.sendMessage(call);
        Vfx.sound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.2f);
        every(p, (int) (160 * amp), 10, tick -> {
            if (target.isValid()) {
                Vfx.ring(target.getLocation().add(0, 0.1, 0), 0.9, 14, colors[pick], 1.4f);
                Vfx.ring(p.getLocation().add(0, 0.1, 0), 0.9, 14, colors[pick], 1.4f);
            }
        });
        return true;
    }

    private boolean karamatsu(Player p, Release r) {
        LivingEntity target = aimed(p, 20);
        if (target == null) {
            return false;
        }
        UUID id = p.getUniqueId();
        Color pink = Color.fromRGB(230, 120, 170);
        sharedWounds.put(id, target.getUniqueId());
        p.sendMessage(Component.text("Ichidanme: Sōkan no Kizu — the wounds you take, they share.", NamedTextColor.LIGHT_PURPLE));
        every(p, 240, 5, tick -> {
            if (!target.isValid() || target.isDead()) {
                sharedWounds.remove(id);
                return;
            }
            beam(center(p), center(target), pink, 0.6f, 0.6);
            switch (tick) {
                case 60 -> {
                    sharedWounds.remove(id);
                    p.sendMessage(Component.text("Nidanme: Zaishō no Fuchi — sink in the pool of shame.", NamedTextColor.LIGHT_PURPLE));
                    effect(target, PotionEffectType.SLOWNESS, 60, 3);
                    effect(target, PotionEffectType.DARKNESS, 80, 0);
                    effect(p, PotionEffectType.SLOWNESS, 60, 1);
                }
                case 120 -> {
                    p.sendMessage(Component.text("Sandanme: Itomaki no Chigiri — the binding thread.", NamedTextColor.LIGHT_PURPLE));
                    root(target, 60);
                    effect(target, PotionEffectType.WITHER, 60, 1);
                }
                case 180 -> p.sendMessage(Component.text("Shimai: Itosabaki no Kubikiri.", NamedTextColor.LIGHT_PURPLE,
                        TextDecoration.BOLD));
                case 200 -> {
                    strike(p, r, target, 14);
                    target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getEyeLocation(), 4, 0.3, 0.1, 0.3, 0);
                    Vfx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
                }
                default -> {
                    if (tick > 60 && tick < 120 && tick % 20 == 0) {
                        strike(p, r, target, 2);
                    }
                }
            }
        });
        return true;
    }

    private boolean sakanade(Player p, Release r, double amp) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 10);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        int ticks = (int) (120 * amp);
        for (LivingEntity victim : victims) {
            bend(victim, p, Charm.MISS, ticks);
            effect(victim, PotionEffectType.NAUSEA, ticks + 40, 0);
        }
        Vfx.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.6f);
        every(p, ticks, 30, tick -> {
            for (LivingEntity victim : victims) {
                if (victim.isValid() && !victim.isDead() && tick < ticks) {
                    Location turned = victim.getLocation();
                    turned.setYaw(turned.getYaw() + 180);
                    turned.setPitch(-turned.getPitch());
                    victim.teleport(turned);
                    victim.getWorld().spawnParticle(Particle.NOTE, victim.getEyeLocation().add(0, 0.6, 0), 3, 0.3, 0.1, 0.3, 1);
                }
            }
        });
        return true;
    }

    private boolean happoFusagari(Player p) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 20);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        for (LivingEntity victim : victims) {
            bend(victim, p, Charm.REDIRECT, 240);
            effect(victim, PotionEffectType.NAUSEA, 260, 0);
        }
        Location center = p.getLocation();
        every(p, 240, 10, tick -> Vfx.ring(center.clone().add(0, 0.2, 0), 20, 80, Color.fromRGB(240, 230, 140), 1.4f));
        Vfx.sound(center, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.6f);
        return true;
    }

    private boolean hyoten(Player p, Release r, double amp) {
        Location at = aimPoint(p, 30);
        double radius = 5 * Math.sqrt(amp);
        Set<UUID> flowered = new HashSet<>();
        Vfx.sound(at, Sound.BLOCK_POWDER_SNOW_PLACE, 1.0f, 0.5f);
        zone(p, () -> at, radius, 60, 5, center -> {
            center.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 8, 0), 40, radius / 2, 0.5,
                    radius / 2, 0.02);
            Vfx.ring(center.clone().add(0, 8.5, 0), radius, 24, Color.fromRGB(200, 230, 255), 1.4f);
        }, (entity, tick) -> {
            if (tick >= 20 && flowered.add(entity.getUniqueId())) {
                strike(p, r, entity, 6 * amp);
                root(entity, 60);
                temporary(entity.getLocation(), Material.PACKED_ICE, 1.1f, 2.0f, 1.1f, 60);
                Vfx.sound(entity.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.0f, 1.4f);
            }
        });
        return true;
    }

    private boolean daiguren(Player p, Release r) {
        effect(p, PotionEffectType.SLOW_FALLING, 600, 0);
        effect(p, PotionEffectType.SPEED, 600, 1);
        Location at = aimPoint(p, 40);
        int count = 10;
        List<BlockDisplay> pillars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            pillars.add(block(at.clone().add(Math.cos(angle) * 7, 0, Math.sin(angle) * 7), Material.BLUE_ICE, 1.6f, 7f, 1.6f));
        }
        Vfx.sound(at, Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.5f);
        every(p, 30, 2, tick -> {
            double radius = 7 * (1 - tick / 30.0) + 0.6;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count;
                pillars.get(i).teleport(at.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
            }
            if (tick >= 30) {
                Vfx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 2, 0), 120, 2, 2, 2, 0,
                        Material.BLUE_ICE.createBlockData());
                for (LivingEntity entity : enemies(p, at, 7)) {
                    strike(p, r, entity, 12);
                    root(entity, 60);
                }
                later(20, () -> pillars.forEach(this::remove));
            }
        });
        return true;
    }

    private boolean kageyoshi(Player p, Release r) {
        Location center = p.getLocation();
        double radius = 10;
        List<BlockDisplay> swords = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            double angle = Math.PI * 2 * i / 28;
            swords.add(block(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    Material.IRON_BLOCK, 0.18f, 3.2f, 0.18f));
        }
        effect(p, PotionEffectType.STRENGTH, 200, 0);
        Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
        every(p, 200, 2, tick -> {
            if (tick >= 200) {
                swords.forEach(this::remove);
                for (LivingEntity entity : enemies(p, center, radius)) {
                    strike(p, r, entity, 5);
                }
                center.getWorld().spawnParticle(Particle.CHERRY_LEAVES, center.clone().add(0, 1.5, 0), 200, radius / 2, 1.5,
                        radius / 2, 0.1);
                return;
            }
            center.getWorld().spawnParticle(Particle.CHERRY_LEAVES, center.clone().add(0, 1.5, 0), 20, radius / 2, 1.2,
                    radius / 2, 0.05);
            for (LivingEntity entity : enemies(p, center, radius + 3)) {
                Vector out = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (out.length() > radius - 1) {
                    entity.setVelocity(out.normalize().multiply(-0.7).setY(0.1));
                }
                if (tick % 20 == 0 && out.length() <= radius + 0.5) {
                    strike(p, r, entity, 3);
                }
            }
        });
        return true;
    }

    private boolean kamishini(Player p, Release r) {
        Location eye = p.getEyeLocation();
        Line thrust = line(p, eye, eye.getDirection(), 60, 0.9, false);
        beam(eye, thrust.end(), Color.fromRGB(220, 220, 235), 1.0f, 0.3);
        Vfx.sound(eye, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.4f);
        if (thrust.hits().isEmpty()) {
            return true;
        }
        for (LivingEntity entity : thrust.hits()) {
            strike(p, r, entity, 6);
        }
        LivingEntity target = thrust.hits().get(0);
        p.sendMessage(Component.text("A sliver of Kamishini no Yari stays inside " + target.getName() + ".",
                NamedTextColor.GRAY));
        every(p, 60, 10, tick -> {
            if (!target.isValid() || target.isDead()) {
                return;
            }
            target.getWorld().spawnParticle(Particle.DUST, center(target), 3, 0.2, 0.3, 0.2, 0,
                    Vfx.dust(Color.fromRGB(110, 200, 40), 1.0f));
            if (tick >= 60) {
                title(p, "Korose.", Color.fromRGB(200, 200, 215));
                strike(p, r, target, 14);
                effect(target, PotionEffectType.WITHER, 100, 1);
                target.getWorld().spawnParticle(Particle.SNEEZE, center(target), 60, 0.4, 0.7, 0.4, 0.05);
                Vfx.sound(target.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.6f);
            }
        });
        return true;
    }

    private boolean tsukishiro(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 22);
        Location at = target != null ? target.getLocation() : aimPoint(p, 22);
        double radius = 3.5 * Math.sqrt(amp);
        Vfx.ring(at.clone().add(0, 0.1, 0), radius, 40, Color.WHITE, 1.4f);
        Vfx.sound(at, Sound.BLOCK_POWDER_SNOW_STEP, 1.0f, 0.6f);
        later(15, () -> {
            for (double y = 0; y < 14; y += 0.5) {
                at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, y, 0), 6, radius / 2, 0.2, radius / 2, 0);
            }
            Vfx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.6f);
            for (LivingEntity entity : enemies(p, at, radius)) {
                strike(p, r, entity, 7 * amp);
                root(entity, 60);
                setMark(entity, Move.HAKKA, p.getUniqueId(), 1, 80);
                temporary(entity.getLocation(), Material.ICE, 1.2f, 2.2f, 1.2f, 60);
            }
        });
        return true;
    }

    private boolean hakka(Player p, Release r) {
        effect(p, PotionEffectType.RESISTANCE, 200, 0);
        effect(p, PotionEffectType.SLOWNESS, 200, 0);
        Map<UUID, Integer> exposure = new HashMap<>();
        Vfx.sound(p.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.4f);
        zone(p, p::getLocation, 7, 200, 5, at -> {
            at.getWorld().spawnParticle(Particle.WHITE_ASH, at.clone().add(0, 1, 0), 50, 3.5, 1, 3.5, 0);
            at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 12, 3.5, 1, 3.5, 0);
        }, (entity, tick) -> {
            effect(entity, PotionEffectType.SLOWNESS, 20, 2);
            entity.setFreezeTicks(Math.min(entity.getMaxFreezeTicks(), entity.getFreezeTicks() + 20));
            int time = exposure.merge(entity.getUniqueId(), 5, Integer::sum);
            if (time >= 40) {
                exposure.put(entity.getUniqueId(), 0);
                root(entity, 40);
                setMark(entity, Move.HAKKA, p.getUniqueId(), 1, 60);
                temporary(entity.getLocation(), Material.PACKED_ICE, 1.2f, 2.2f, 1.2f, 40);
                Vfx.sound(entity.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.0f, 1.2f);
            }
        });
        return true;
    }

    private boolean higaZekko(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 20);
        if (target == null) {
            return false;
        }
        List<BlockDisplay> fangs = new ArrayList<>();
        int count = (int) (8 * amp);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            fangs.add(block(target.getLocation().add(Math.cos(angle) * 3, 1 + (i % 2) * 0.8, Math.sin(angle) * 3),
                    Material.BONE_BLOCK, 0.5f, 0.5f, 0.5f));
        }
        Vfx.sound(target.getLocation(), Sound.BLOCK_BONE_BLOCK_PLACE, 1.0f, 0.6f);
        every(p, 14, 2, tick -> {
            Location to = center(target);
            for (int i = 0; i < fangs.size(); i++) {
                double angle = Math.PI * 2 * i / fangs.size() + tick * 0.1;
                double radius = 3 * (1 - tick / 14.0);
                fangs.get(i).teleport(to.clone().add(Math.cos(angle) * radius, (i % 2) * 0.6 - 0.3, Math.sin(angle) * radius));
            }
            if (tick >= 14) {
                fangs.forEach(this::remove);
                strike(p, r, target, 8 * amp);
                for (LivingEntity other : enemies(p, target.getLocation(), 2.5)) {
                    if (!other.equals(target)) {
                        strike(p, r, other, 4 * amp);
                    }
                }
                Vfx.sound(target.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.0f, 0.8f);
            }
        });
        return true;
    }

    private boolean hikotsu(Player p, Release r) {
        root(p, 15);
        Location head = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(2.5));
        BlockDisplay skull = block(head, Material.BONE_BLOCK, 2.2f, 1.6f, 2.2f);
        Vfx.sound(head, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.6f);
        later(15, () -> {
            remove(skull);
            Location eye = p.getEyeLocation();
            Line blast = line(p, eye, eye.getDirection(), 40, 2.6, false);
            for (double s = 0; s < 1; s += 0.02) {
                Location at = eye.clone().add(blast.end().toVector().subtract(eye.toVector()).multiply(s));
                at.getWorld().spawnParticle(Particle.FLAME, at, 4, 0.8, 0.8, 0.8, 0.02);
                at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.6, 0.6, 0.6, 0, Vfx.dust(Color.fromRGB(220, 40, 40), 2f));
            }
            Vfx.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.6f);
            for (LivingEntity entity : blast.hits()) {
                strike(p, r, entity, 14);
                entity.setFireTicks(80);
            }
        });
        return true;
    }

    private boolean haineko(Player p, Release r, double amp) {
        int ticks = (int) (100 * amp);
        UUID id = p.getUniqueId();
        bump(id, "ash", 1);
        int token = counter(id, "ash");
        Color ash = Color.fromRGB(200, 190, 170);
        zone(p, () -> counter(id, "ash") == token ? p.getLocation() : null, 3.5, ticks, 5, at -> {
            at.getWorld().spawnParticle(Particle.ASH, at.clone().add(0, 1, 0), 40, 2, 1, 2, 0);
            Vfx.ring(at.clone().add(0, 1, 0), 3, 14, ash, 1.0f);
        }, (entity, tick) -> {
            if (tick % 10 == 0) {
                strike(p, r, entity, 1.5 * amp);
            }
        });
        recastLater(p, ticks * 50L, () -> {
            bump(id, "ash", 1);
            Location target = aimPoint(p, 25);
            beam(p.getEyeLocation(), target, ash, 1.4f, 0.4);
            zone(p, () -> target, 3.5, 40, 5, at -> at.getWorld().spawnParticle(Particle.ASH, at.clone().add(0, 1, 0),
                    60, 2, 1, 2, 0), (entity, tick) -> {
                        if (tick % 10 == 0) {
                            strike(p, r, entity, 2.5 * amp);
                        }
                    });
            p.sendActionBar(Component.text("Haineko!", NamedTextColor.GOLD));
        });
        p.sendActionBar(Component.text("Press Q again to fling the ash.", NamedTextColor.GOLD));
        return true;
    }

    private boolean ashStorm(Player p, Release r) {
        zone(p, p::getLocation, 6, 240, 5, at -> at.getWorld().spawnParticle(Particle.ASH, at.clone().add(0, 1, 0), 90,
                3, 1.5, 3, 0), (entity, tick) -> {
                    if (tick % 10 == 0) {
                        strike(p, r, entity, 2);
                        effect(entity, PotionEffectType.BLINDNESS, 30, 0);
                    }
                });
        Vfx.sound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.6f);
        return true;
    }

    private boolean jakuho(Player p, Release r) {
        root(p, 20);
        p.sendActionBar(Component.text("Jakuhō Raikōben — bracing...", NamedTextColor.YELLOW));
        Vfx.sound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.0f, 0.5f);
        BlockDisplay launcher = block(p.getLocation().add(flat(p).multiply(0.6)).add(0, 1.0, 0), Material.GOLD_BLOCK,
                0.6f, 0.6f, 2.0f);
        later(20, () -> {
            remove(launcher);
            if (!p.isOnline()) {
                return;
            }
            Vector direction = p.getEyeLocation().getDirection();
            p.setVelocity(direction.clone().multiply(-1.6).setY(0.5));
            Vfx.sound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.5f);
            projectile(p, p.getEyeLocation(), direction, 2.5, 60, 1.2, false,
                    at -> {
                        at.getWorld().spawnParticle(Particle.FLAME, at, 3, 0.1, 0.1, 0.1, 0.01);
                        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 2, 0.1, 0.1, 0.1, 0.01);
                    }, (entity, at) -> {
                    }, at -> explosion(p, r, at, 6, 16, 1.2));
        });
        return true;
    }

    private boolean tenken(Player p, Release r, double amp) {
        Location at = aimPoint(p, 25);
        BlockDisplay arm = block(at.clone().add(0, 12, 0), Material.NETHERITE_BLOCK, 2.2f, 5.5f, 2.2f);
        BlockDisplay fist = block(at.clone().add(0, 11, 0), Material.IRON_BLOCK, 2.8f, 1.8f, 2.8f);
        Vfx.ring(at.clone().add(0, 0.1, 0), 4, 30, Color.fromRGB(170, 110, 60), 1.4f);
        Vfx.sound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.5f);
        every(p, 10, 1, tick -> {
            double y = 12 * (1 - tick / 10.0);
            arm.teleport(at.clone().add(0, y + 1.6, 0));
            fist.teleport(at.clone().add(0, y, 0));
            if (tick >= 10) {
                Vfx.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.6f);
                at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 80, 2, 0.4, 2, 0,
                        Material.DIRT.createBlockData());
                for (LivingEntity entity : enemies(p, at, 4)) {
                    strike(p, r, entity, 9 * amp);
                    entity.setVelocity(new Vector(0, 1.0, 0));
                }
                later(10, () -> {
                    remove(arm);
                    remove(fist);
                });
            }
        });
        return true;
    }

    private boolean myoo(Player p, Release r) {
        int ticks = (int) Math.max(100, Math.min(600, (r.endsAt - System.currentTimeMillis()) / 50));
        BlockDisplay body = block(p.getLocation(), Material.NETHERITE_BLOCK, 3.2f, 5.0f, 2.0f);
        BlockDisplay head = block(p.getLocation(), Material.IRON_BLOCK, 1.6f, 1.6f, 1.6f);
        BlockDisplay blade = block(p.getLocation(), Material.IRON_BLOCK, 0.4f, 7f, 0.4f);
        Vfx.sound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.4f);
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.8f);
        every(p, ticks, 2, tick -> {
            if (tick >= ticks || !active(p, r)) {
                remove(body);
                remove(head);
                remove(blade);
                return;
            }
            Location base = p.getLocation().subtract(flat(p).multiply(3)).add(0, 0.5, 0);
            body.teleport(base);
            head.teleport(base.clone().add(0, 5.0, 0));
            blade.teleport(base.clone().add(flat(p).crossProduct(new Vector(0, 1, 0)).normalize().multiply(2.2)));
            if (tick % 40 == 0) {
                LivingEntity target = nearest(p, p.getLocation(), 12, null);
                if (target != null) {
                    strike(p, r, target, 7);
                    Vfx.arc(center(target), target.getLocation().toVector().subtract(p.getLocation().toVector())
                            .setY(0).normalize(), 2.6, Color.fromRGB(170, 110, 60), 1.8f, 0);
                    Vfx.sound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
                }
            }
        });
        return true;
    }

    private boolean weightField(Player p, Release r) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 9);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        for (LivingEntity victim : victims) {
            weigh(p, r, victim, 2, null);
        }
        every(p, 160, 20, tick -> {
            if (tick > 0 && tick < 160) {
                for (LivingEntity victim : enemies(p, p.getLocation(), 9)) {
                    weigh(p, r, victim, 1, null);
                    strike(p, r, victim, 2);
                }
            }
        });
        return true;
    }

    private boolean tachikaze(Player p, Release r, double amp) {
        projectile(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), 1.4, 25, 1.0, false,
                at -> at.getWorld().spawnParticle(Particle.SMALL_GUST, at, 2, 0.2, 0.2, 0.2, 0),
                (entity, at) -> {
                }, at -> {
                    at.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, at, 1);
                    explosion(p, r, at, 3, 7 * amp, 1.5);
                });
        Vfx.sound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 0.8f);
        return true;
    }

    private boolean minazuki(Player p, Release r, double amp) {
        Location at = p.getLocation();
        for (Player ally : allies(p, 8)) {
            heal(ally, 6 * amp);
            cleanse(ally);
            effect(ally, PotionEffectType.REGENERATION, 100, 1);
            ally.getWorld().spawnParticle(Particle.HEART, ally.getEyeLocation().add(0, 0.4, 0), 3, 0.3, 0.2, 0.3, 0);
        }
        for (int i = 0; i < 3; i++) {
            Vfx.ring(at.clone().add(0, 2.5 + i * 0.3, 0), 3 + i, 30, Color.fromRGB(110, 200, 170), 1.6f);
        }
        Vfx.sound(at, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
        return true;
    }

    private boolean minazukiBankai(Player p, Release r) {
        mode(p, Move.MINAZUKI_BANKAI, 200);
        Color blood = Color.fromRGB(120, 0, 20);
        zone(p, p::getLocation, 6, 200, 5, at -> {
            Vfx.ring(at.clone().add(0, 0.1, 0), 6, 36, blood, 1.6f);
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.2, 0), 20, 3, 0.05, 3, 0, Vfx.dust(blood, 1.4f));
            for (Player ally : allies(p, 6)) {
                if (at.getWorld().getGameTime() % 20 == 0) {
                    heal(ally, 1.5);
                }
            }
        }, (entity, tick) -> {
            effect(entity, PotionEffectType.WITHER, 30, 1);
            if (tick % 20 == 0) {
                strike(p, r, entity, 1.5);
            }
        });
        return true;
    }

    /** Sōgyo no Kotowari and Cero Doble: absorb incoming damage, then return it. */
    private boolean absorb(Player p, Release r, double amp, boolean cero) {
        UUID id = p.getUniqueId();
        Move move = cero ? Move.CERO_DOBLE : Move.SOGYO;
        absorbed.put(id, 0.0);
        int ticks = (int) (80 * amp);
        mode(p, move, ticks);
        bump(id, "absorb", 1);
        int token = counter(id, "absorb");
        Runnable release = () -> {
            if (counter(id, "absorb") != token || !p.isOnline()) {
                return;
            }
            bump(id, "absorb", 1);
            endMode(p, move);
            double stored = absorbed.getOrDefault(id, 0.0);
            absorbed.remove(id);
            if (cero) {
                Location eye = p.getEyeLocation();
                Line blast = line(p, eye, eye.getDirection(), 30, 1.6, false);
                beam(eye, blast.end(), Color.fromRGB(120, 210, 140), 2.0f, 0.25);
                Vfx.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.2f);
                for (LivingEntity entity : blast.hits()) {
                    strike(p, r, entity, Math.min(18, 3 + stored * 2));
                }
            } else {
                Location at = p.getLocation();
                Vfx.sound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
                for (LivingEntity entity : enemies(p, at, 6)) {
                    beam(center(p), center(entity), Color.fromRGB(200, 230, 255), 1.2f, 0.3);
                    strike(p, r, entity, Math.min(16, 2 + stored * 1.2));
                }
            }
            title(p, (cero ? "Cero Doble: " : "Sōgyo no Kotowari: ") + Math.round(stored) + " returned",
                    r.form.color());
        };
        recastLater(p, ticks * 50L, release);
        later(ticks, release);
        p.sendActionBar(Component.text("Absorbing... press Q to return it.", NamedTextColor.AQUA));
        return true;
    }

    private boolean kamikake(Player p) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 12);
        for (LivingEntity victim : victims) {
            for (PotionEffect active : victim.getActivePotionEffects()) {
                if (GOOD.contains(active.getType())) {
                    victim.removePotionEffect(active.getType());
                }
            }
            effect(victim, PotionEffectType.SLOWNESS, 160, 2);
            effect(victim, PotionEffectType.MINING_FATIGUE, 160, 1);
        }
        for (Player ally : allies(p, 12)) {
            effect(ally, PotionEffectType.REGENERATION, 160, 1);
        }
        Location at = p.getLocation();
        for (int i = 0; i < 4; i++) {
            Vfx.ring(at.clone().add(0, 0.5 + i, 0), 12 - i * 2, 60, Color.fromRGB(40, 40, 60), 1.6f);
        }
        Vfx.sound(at, Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.4f);
        return true;
    }

    private boolean konjiki(Player p, Release r) {
        BlockDisplay giant = block(p.getLocation(), Material.GOLD_BLOCK, 2.4f, 2.4f, 4f);
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1.0f, 1.4f);
        every(p, 200, 5, tick -> {
            if (tick >= 200) {
                remove(giant);
                return;
            }
            p.removePotionEffect(PotionEffectType.POISON);
            giant.teleport(p.getLocation().add(flat(p).multiply(2.5)).add(0, 0.2, 0));
            Location mouth = p.getEyeLocation().add(flat(p).multiply(4));
            mouth.getWorld().spawnParticle(Particle.SNEEZE, mouth, 30, 1.5, 0.8, 1.5, 0.05);
            mouth.getWorld().spawnParticle(Particle.DUST, mouth.clone().add(flat(p).multiply(3)), 20, 2.5, 1, 2.5, 0,
                    Vfx.dust(Color.fromRGB(170, 80, 200), 1.6f));
            for (LivingEntity entity : enemies(p, p.getLocation(), 11)) {
                if (inCone(p, entity, 11, 40)) {
                    effect(entity, PotionEffectType.POISON, 60, 1);
                    effect(entity, PotionEffectType.WITHER, 40, 0);
                    if (tick % 10 == 0) {
                        strike(p, r, entity, 1.5);
                    }
                }
            }
        });
        return true;
    }

    // ================================================================ Hollow

    private boolean desgarron(Player p, Release r) {
        Vector forward = flat(p);
        Vector side = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Map<UUID, Integer> times = new HashMap<>();
        for (int volley = 0; volley < 2; volley++) {
            int delay = volley * 8;
            double tilt = volley == 0 ? 0.6 : -0.6;
            later(delay, () -> {
                Vfx.sound(p.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 1.4f);
                for (int i = 0; i < 5; i++) {
                    Location from = p.getLocation().add(0, 1.0 + (i - 2) * 0.35, 0)
                            .add(side.clone().multiply((i - 2) * 0.9));
                    Vector direction = forward.clone().add(side.clone().multiply(tilt * 0.15)).normalize();
                    Line claw = line(p, from, direction, 18, 1.2, false);
                    beam(from, claw.end(), Color.fromRGB(80, 160, 255), 1.6f, 0.3);
                    for (LivingEntity entity : claw.hits()) {
                        if (times.merge(entity.getUniqueId(), 1, Integer::sum) <= 3) {
                            strike(p, r, entity, 4);
                        }
                    }
                }
            });
        }
        return true;
    }

    private boolean ira(Player p, Release r, double amp) {
        int ticks = (int) (200 * amp);
        scale(p, 1.5, ticks);
        effect(p, PotionEffectType.STRENGTH, ticks, 0);
        effect(p, PotionEffectType.RESISTANCE, ticks, 0);
        Vfx.sound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.5f);
        return true;
    }

    private boolean grow(Player p, double size, int ticks, boolean quakes) {
        scale(p, size, ticks);
        effect(p, PotionEffectType.STRENGTH, ticks, 1);
        effect(p, PotionEffectType.RESISTANCE, ticks, 1);
        if (quakes) {
            mode(p, Move.FURIA, ticks);
        }
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.6f);
        p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
        return true;
    }

    private boolean hierro(Player p, double amp) {
        mode(p, Move.HIERRO, (int) (100 * amp));
        p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center(p), 30, 0.4, 0.8, 0.4, 0.1);
        Vfx.sound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
        p.sendActionBar(Component.text("Hierro Más Fuerte.", NamedTextColor.YELLOW));
        return true;
    }

    private void control(Player p, LivingEntity victim, int ticks, Charm kind, Color color, Particle particle) {
        bend(victim, p, kind, ticks);
        every(p, ticks, 10, tick -> {
            if (!victim.isValid() || victim.isDead() || bendOf(victim) == null || tick >= ticks) {
                return;
            }
            victim.getWorld().spawnParticle(particle, victim.getEyeLocation().add(0, 0.7, 0), 2, 0.2, 0.1, 0.2, 0);
            Vfx.ring(victim.getEyeLocation().add(0, 0.7, 0), 0.3, 8, color, 1.0f);
            if (kind == Charm.SPARE && particle == Particle.HEART) {
                // Charmed: walk toward the one who charmed you.
                Vector to = p.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
                if (to.lengthSquared() > 4) {
                    victim.setVelocity(to.normalize().multiply(0.35).setY(victim.getVelocity().getY()));
                }
            } else if (kind == Charm.SPARE) {
                // Amor: stagger toward your other enemies.
                LivingEntity other = nearest(p, victim.getLocation(), 10, Set.of(victim.getUniqueId()));
                if (other != null) {
                    Vector to = other.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() > 1) {
                        victim.setVelocity(to.normalize().multiply(0.4).setY(victim.getVelocity().getY()));
                    }
                    if (victim instanceof Mob mob) {
                        mob.setTarget(other);
                    }
                }
            }
        });
    }

    private boolean amor(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 20);
        if (target == null) {
            return false;
        }
        control(p, target, (int) (120 * amp), Charm.SPARE, Color.fromRGB(90, 200, 120), Particle.GLOW);
        target.sendMessage(Component.text("An eye opens on your body. Amor.", NamedTextColor.GREEN));
        Vfx.sound(target.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
        return true;
    }

    private boolean amorMass(Player p) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 14);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        for (LivingEntity victim : victims) {
            control(p, victim, 160, Charm.SPARE, Color.fromRGB(90, 200, 120), Particle.GLOW);
        }
        Vfx.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0f, 0.6f);
        return true;
    }

    private boolean devour(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 5);
        if (target == null) {
            return false;
        }
        strike(p, r, target, 8 * amp);
        heal(p, 6 * amp);
        int stolen = steal(target, p);
        Location at = center(target);
        at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at, 16, 0.4, 0.4, 0.4, 0.1);
        Vfx.sound(at, Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.5f);
        Vfx.sound(at, Sound.ENTITY_RAVAGER_ATTACK, 0.8f, 0.8f);
        if (stolen > 0) {
            p.sendActionBar(Component.text("Devoured " + stolen + " of their powers.", NamedTextColor.DARK_PURPLE));
        }
        return true;
    }

    private boolean banquet(Player p, Release r) {
        zone(p, p::getLocation, 7, 240, 20, at -> Vfx.ring(at.clone().add(0, 0.3, 0), 7, 40, Color.fromRGB(90, 50, 130), 1.4f),
                (entity, tick) -> {
                    if (tick >= 240) {
                        return;
                    }
                    beam(center(p), center(entity), Color.fromRGB(90, 50, 130), 1.2f, 0.3);
                    strike(p, r, entity, 3);
                    heal(p, 1);
                    Vector pull = p.getLocation().toVector().subtract(entity.getLocation().toVector());
                    if (pull.lengthSquared() > 4) {
                        entity.setVelocity(pull.normalize().multiply(0.6).setY(0.2));
                    }
                });
        return true;
    }

    private void crushOrgan(Player p, Release r, LivingEntity target, double damage) {
        strike(p, r, target, damage);
        PotionEffectType[] organs = {PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS,
                PotionEffectType.WEAKNESS, PotionEffectType.HUNGER};
        String[] names = {"stomach", "eyes", "legs", "arms", "lungs"};
        int pick = ThreadLocalRandom.current().nextInt(organs.length);
        effect(target, organs[pick], 100, 1);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, center(target), 12, 0.3, 0.4, 0.3, 0.1);
        Vfx.sound(target.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.5f);
        p.sendActionBar(Component.text("Crushed: " + names[pick], NamedTextColor.LIGHT_PURPLE));
    }

    private boolean voodoo(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 16);
        if (target == null) {
            return false;
        }
        BlockDisplay doll = block(p.getLocation().add(flat(p)).add(0, 1.2, 0), Material.PINK_WOOL, 0.3f, 0.5f, 0.3f);
        UUID id = p.getUniqueId();
        bump(id, "doll", 1);
        int token = counter(id, "doll");
        int ticks = (int) (160 * amp);
        Runnable crush = () -> {
            if (counter(id, "doll") != token) {
                return;
            }
            bump(id, "doll", 1);
            remove(doll);
            if (target.isValid() && !target.isDead()) {
                crushOrgan(p, r, target, 8 * amp);
            }
        };
        every(p, ticks, 2, tick -> {
            if (doll.isValid()) {
                doll.teleport(p.getLocation().add(flat(p)).add(0, 1.2, 0));
            }
        });
        recastLater(p, ticks * 50L, crush);
        later(ticks, crush);
        p.sendActionBar(Component.text("Doll made. Press Q to crush an organ.", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    private boolean gabriel(Player p, Release r) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 12);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        for (int i = 0; i < Math.min(6, victims.size()); i++) {
            LivingEntity victim = victims.get(i);
            later(i * 10, () -> {
                if (victim.isValid() && !victim.isDead()) {
                    crushOrgan(p, r, victim, 6);
                }
            });
        }
        return true;
    }

    private boolean luzLanza(Player p, Release r, double amp) {
        projectile(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), 1.8, 35, 1.0, false,
                at -> at.getWorld().spawnParticle(Particle.DUST, at, 3, 0.05, 0.05, 0.05, 0,
                        Vfx.dust(Color.fromRGB(80, 230, 130), 1.6f)),
                (entity, at) -> {
                }, at -> explosion(p, r, at, 2.5, 7 * amp, 0.6));
        Vfx.sound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
        return true;
    }

    private boolean lanzaRelampago(Player p, Release r) {
        root(p, 20);
        BlockDisplay lance = block(p.getLocation().add(0, 2.4, 0), Material.LIME_STAINED_GLASS, 0.3f, 3.0f, 0.3f);
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.4f);
        later(20, () -> {
            remove(lance);
            if (!p.isOnline()) {
                return;
            }
            projectile(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), 2.2, 50, 1.2, false,
                    at -> {
                        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.2, 0.2, 0.2, 0.05);
                        at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.1, 0.1, 0.1, 0,
                                Vfx.dust(Color.fromRGB(110, 255, 150), 2f));
                    }, (entity, at) -> {
                    }, at -> {
                        at.getWorld().strikeLightningEffect(at);
                        explosion(p, r, at, 7, 18, 1.4);
                    });
        });
        return true;
    }

    private boolean lobos(Player p, Release r, double amp) {
        List<LivingEntity> targets = enemies(p, p.getLocation(), 20);
        if (targets.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        Vfx.sound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.0f, 0.8f);
        int wolves = (int) Math.min(targets.size(), 4 * amp);
        for (int i = 0; i < wolves; i++) {
            LivingEntity target = targets.get(i);
            Location start = p.getLocation().add(0, 0.6, 0);
            Vector direction = target.getLocation().toVector().subtract(start.toVector());
            projectile(p, start, direction, 1.0, direction.length() + 2, 1.0, false,
                    at -> at.getWorld().spawnParticle(Particle.DUST, at, 6, 0.3, 0.2, 0.3, 0,
                            Vfx.dust(Color.fromRGB(150, 190, 255), 1.6f)),
                    (entity, at) -> {
                    }, at -> explosion(p, r, at, 2.2, 5 * amp, 0.5));
        }
        return true;
    }

    private boolean metralleta(Player p, Release r) {
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 2.0f);
        every(p, 60, 2, tick -> {
            if (tick >= 60) {
                return;
            }
            Location eye = p.getEyeLocation();
            Vector spread = eye.getDirection().add(new Vector(ThreadLocalRandom.current().nextGaussian() * 0.04,
                    ThreadLocalRandom.current().nextGaussian() * 0.04, ThreadLocalRandom.current().nextGaussian() * 0.04));
            Line shot = line(p, eye, spread, 30, 1.2, false);
            beam(eye, shot.end(), Color.fromRGB(140, 180, 255), 1.0f, 0.6);
            if (tick % 4 == 0) {
                Vfx.sound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.8f);
            }
            for (LivingEntity entity : shot.hits()) {
                strike(p, r, entity, 1.8);
            }
        });
        return true;
    }

    private boolean cascada(Player p, Release r, double amp) {
        Location at = aimPoint(p, 30);
        double radius = 4 * Math.sqrt(amp);
        Vfx.sound(at, Sound.WEATHER_RAIN_ABOVE, 1.0f, 0.5f);
        every(p, 20, 2, tick -> {
            at.getWorld().spawnParticle(Particle.FALLING_WATER, at.clone().add(0, 8, 0), 120, radius / 2, 3, radius / 2, 0);
            at.getWorld().spawnParticle(Particle.SPLASH, at.clone().add(0, 0.3, 0), 60, radius / 2, 0.2, radius / 2, 0.2);
            if (tick >= 20) {
                Vfx.sound(at, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.5f);
                for (LivingEntity entity : enemies(p, at, radius)) {
                    strike(p, r, entity, 8 * amp);
                    entity.setVelocity(new Vector(0, -1.2, 0));
                    effect(entity, PotionEffectType.SLOWNESS, 60, 1);
                }
            }
        });
        return true;
    }

    private boolean granCaida(Player p, Release r) {
        getsuga(p, r, Color.fromRGB(240, 210, 90), 14);
        zone(p, p::getLocation, 5, 160, 10, at -> at.getWorld().spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.6, 0),
                40, 2.5, 0.5, 2.5, 0.05), (entity, tick) -> {
                    effect(entity, PotionEffectType.SLOWNESS, 30, 0);
                    if (tick % 20 == 0) {
                        strike(p, r, entity, 2);
                    }
                });
        return true;
    }

    private boolean remolino(Player p, Release r, double amp) {
        for (int side = -1; side <= 1; side += 2) {
            Vector direction = flat(p).rotateAroundY(Math.toRadians(14 * side));
            projectile(p, p.getLocation().add(0, 0.5, 0), direction, 0.8, 20, 1.6, true,
                    at -> {
                        for (double y = 0; y < 3; y += 0.5) {
                            double angle = at.getWorld().getGameTime() * 0.8 + y * 2;
                            at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(Math.cos(angle) * y * 0.4, y,
                                    Math.sin(angle) * y * 0.4), 1, 0, 0, 0, 0);
                        }
                    }, (entity, at) -> {
                        strike(p, r, entity, 4 * amp);
                        entity.setVelocity(new Vector(0, 1.0, 0));
                    }, null);
        }
        Vfx.sound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.8f);
        return true;
    }

    private boolean golondrina(Player p, Release r, double amp) {
        effect(p, PotionEffectType.LEVITATION, 15, 2);
        effect(p, PotionEffectType.SLOW_FALLING, 80, 0);
        Vfx.sound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.2f);
        int ticks = (int) (60 * amp);
        every(p, ticks, 5, tick -> {
            if (tick < 15 || tick >= ticks) {
                return;
            }
            for (LivingEntity entity : enemies(p, p.getLocation(), 9)) {
                if (entity.getLocation().getY() <= p.getLocation().getY() + 1) {
                    beam(p.getLocation().add(0, 0.5, 0), center(entity), Color.fromRGB(180, 120, 220), 0.8f, 0.5);
                    strike(p, r, entity, 1.5);
                }
            }
        });
        return true;
    }

    private boolean respira(Player p, Release r, double amp) {
        Location eye = p.getEyeLocation();
        double range = 10 * Math.sqrt(amp);
        for (double d = 1; d < range; d += 0.8) {
            Location at = eye.clone().add(eye.getDirection().multiply(d));
            at.getWorld().spawnParticle(Particle.SQUID_INK, at, 4, d * 0.15, d * 0.15, d * 0.15, 0.01);
            at.getWorld().spawnParticle(Particle.ASH, at, 4, d * 0.2, d * 0.2, d * 0.2, 0);
        }
        Vfx.sound(eye, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
        for (LivingEntity entity : enemies(p, p.getLocation(), range + 1)) {
            if (inCone(p, entity, range, 30)) {
                strike(p, r, entity, 5 * amp);
                effect(entity, PotionEffectType.WITHER, 80, 2);
                effect(entity, PotionEffectType.WEAKNESS, 100, 0);
            }
        }
        for (Entity entity : p.getNearbyEntities(range, range, range)) {
            if (entity instanceof Projectile projectile && !(projectile.getShooter() instanceof Player shooter
                    && shooter.equals(p))) {
                projectile.getWorld().spawnParticle(Particle.ASH, projectile.getLocation(), 10, 0.2, 0.2, 0.2, 0);
                projectile.remove();
            }
        }
        return true;
    }

    private boolean senescencia(Player p, Release r) {
        zone(p, p::getLocation, 6, 240, 2, at -> {
            if (at.getWorld().getGameTime() % 4 == 0) {
                at.getWorld().spawnParticle(Particle.SQUID_INK, at.clone().add(0, 1, 0), 10, 3, 1, 3, 0);
            }
            for (Entity entity : at.getNearbyEntities(6, 6, 6)) {
                if (entity instanceof Projectile projectile && !(projectile.getShooter() instanceof Player shooter
                        && shooter.equals(p))) {
                    projectile.getWorld().spawnParticle(Particle.ASH, projectile.getLocation(), 10, 0.2, 0.2, 0.2, 0);
                    projectile.remove();
                }
            }
        }, (entity, tick) -> {
            effect(entity, PotionEffectType.WITHER, 40, 1);
            if (tick % 20 == 0) {
                strike(p, r, entity, 1.5);
            }
        });
        Vfx.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        return true;
    }

    private boolean dragonRugir(Player p, Release r, double amp) {
        Location eye = p.getEyeLocation();
        Line drill = line(p, eye, eye.getDirection(), 20, 1.6, false);
        Vector direction = eye.getDirection();
        Vector up = new Vector(0, 1, 0);
        Vector side = direction.clone().crossProduct(up).normalize();
        Vector ortho = side.clone().crossProduct(direction).normalize();
        double length = eye.distance(drill.end());
        for (double d = 0; d < length; d += 0.3) {
            double angle = d * 1.4;
            Location at = eye.clone().add(direction.clone().multiply(d))
                    .add(side.clone().multiply(Math.cos(angle) * 0.7)).add(ortho.clone().multiply(Math.sin(angle) * 0.7));
            at.getWorld().spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0);
        }
        Vfx.sound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        for (LivingEntity entity : drill.hits()) {
            strike(p, r, entity, 9 * amp);
            entity.setFireTicks(60);
        }
        return true;
    }

    private boolean bullCharge(Player p, Release r, double amp) {
        Vector direction = flat(p);
        Set<LivingEntity> carried = new HashSet<>();
        Vfx.sound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
        every(p, 12, 1, tick -> {
            if (tick >= 12) {
                for (LivingEntity entity : carried) {
                    strike(p, r, entity, 8 * amp);
                    entity.setVelocity(new Vector(0, 0.9, 0));
                }
                p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 40, 1, 0.3, 1, 0,
                        Material.DIRT.createBlockData());
                Vfx.sound(p.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.8f);
                return;
            }
            p.setVelocity(direction.clone().multiply(1.1).setY(p.getVelocity().getY()));
            for (LivingEntity entity : enemies(p, p.getLocation(), 2)) {
                carried.add(entity);
            }
            Location front = p.getLocation().add(direction.clone().multiply(1.4));
            for (LivingEntity entity : carried) {
                if (entity.isValid()) {
                    entity.teleport(front.clone().setDirection(entity.getLocation().getDirection()));
                }
            }
        });
        return true;
    }

    private boolean lanzador(Player p, Release r) {
        Location eye = p.getEyeLocation();
        Line lance = line(p, eye, eye.getDirection(), 45, 1.8, false);
        Vector direction = eye.getDirection();
        Vector side = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector ortho = side.clone().crossProduct(direction).normalize();
        double length = eye.distance(lance.end());
        for (double d = 0; d < length; d += 0.25) {
            double angle = d * 2.2;
            Location at = eye.clone().add(direction.clone().multiply(d));
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + strand * Math.PI;
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(side.clone().multiply(Math.cos(a) * 0.8))
                        .add(ortho.clone().multiply(Math.sin(a) * 0.8)), 1, 0, 0, 0, 0,
                        Vfx.dust(Color.fromRGB(120, 230, 140), 1.6f));
            }
        }
        Vfx.sound(eye, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.6f);
        Vfx.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        for (LivingEntity entity : lance.hits()) {
            strike(p, r, entity, 15);
        }
        return true;
    }

    private boolean trepadora(Player p, Release r, double amp) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 10);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        List<LivingEntity> held = victims.subList(0, Math.min(8, victims.size()));
        int ticks = (int) (60 * amp);
        for (LivingEntity entity : held) {
            root(entity, ticks);
        }
        every(p, ticks, 5, tick -> {
            for (LivingEntity entity : held) {
                if (entity.isValid() && !entity.isDead() && tick < ticks) {
                    beam(center(p), center(entity), Color.fromRGB(140, 90, 200), 0.9f, 0.4);
                    if (tick % 20 == 0) {
                        strike(p, r, entity, 2);
                    }
                }
            }
        });
        Vfx.sound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.5f);
        return true;
    }

    private boolean tentacleStorm(Player p, Release r) {
        zone(p, p::getLocation, 9, 160, 10, at -> {
        }, (entity, tick) -> {
            if (tick < 160) {
                beam(center(p), center(entity), Color.fromRGB(140, 90, 200), 1.1f, 0.35);
                strike(p, r, entity, 2.5);
            }
        });
        Vfx.sound(p.getLocation(), Sound.ENTITY_SPIDER_HURT, 1.0f, 0.5f);
        return true;
    }

    private boolean tresBestias(Player p, Release r) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 16);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        victims.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(p.getLocation()),
                b.getLocation().distanceSquared(p.getLocation())));
        String[] names = {"Cierva", "León", "Anaconda"};
        for (int i = 0; i < 3; i++) {
            LivingEntity target = victims.get(i % victims.size());
            int beast = i;
            later(i * 8, () -> {
                if (!target.isValid() || target.isDead()) {
                    return;
                }
                beam(center(p), center(target), Color.fromRGB(210, 180, 100), 1.4f, 0.4);
                switch (beast) {
                    case 0 -> {
                        strike(p, r, target, 6);
                        target.setVelocity(new Vector(0, 1.2, 0));
                    }
                    case 1 -> {
                        strike(p, r, target, 9);
                        target.setVelocity(target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0)
                                .normalize().multiply(1.6).setY(0.4));
                    }
                    default -> {
                        strike(p, r, target, 4);
                        root(target, 60);
                        effect(target, PotionEffectType.WITHER, 60, 1);
                    }
                }
                p.sendActionBar(Component.text(names[beast] + "!", NamedTextColor.GOLD));
                Vfx.sound(target.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 1.0f + beast * 0.2f);
            });
        }
        return true;
    }

    // ================================================================ Quincy

    private boolean burner3(Player p, Release r, double amp) {
        Location eye = p.getEyeLocation();
        double range = 9 * Math.sqrt(amp);
        for (double d = 1; d < range; d += 0.6) {
            eye.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(eye.getDirection().multiply(d)), 8, d * 0.2,
                    d * 0.2, d * 0.2, 0.02);
        }
        Vfx.sound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        for (LivingEntity entity : enemies(p, p.getLocation(), range + 1)) {
            if (inCone(p, entity, range, 28)) {
                strike(p, r, entity, 8 * amp);
                entity.setFireTicks(100);
            }
        }
        return true;
    }

    private boolean burner4(Player p, Release r) {
        Location center = p.getLocation().add(0, 1, 0);
        Vfx.sound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.4f);
        Set<UUID> hit = new HashSet<>();
        every(p, 10, 1, tick -> {
            double radius = 9 * tick / 10.0;
            for (int i = 0; i < 40; i++) {
                double angle = Math.PI * 2 * i / 40;
                center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(Math.cos(angle) * radius, 0,
                        Math.sin(angle) * radius), 1, 0, 0.1, 0, 0);
            }
            for (LivingEntity entity : enemies(p, center, radius + 0.5)) {
                if (hit.add(entity.getUniqueId())) {
                    strike(p, r, entity, 14);
                    entity.setFireTicks(120);
                }
            }
        });
        return true;
    }

    private boolean killCount(Player p) {
        counters.remove(p.getUniqueId() + ":kills");
        mode(p, Move.KILL_COUNT, 400);
        effect(p, PotionEffectType.STRENGTH, 400, 0);
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.0f);
        return true;
    }

    private boolean powerPunch(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 5);
        if (target == null) {
            return false;
        }
        strike(p, r, target, 8 * amp);
        target.setVelocity(p.getEyeLocation().getDirection().multiply(3.2).setY(0.8));
        target.getWorld().spawnParticle(Particle.EXPLOSION, center(target), 1);
        Vfx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
        return true;
    }

    private boolean quake(Player p, Release r, double radius, double damage, Material debris) {
        Location at = p.getLocation();
        Vfx.shockwave(plugin, at, r.form.color(), radius, 10);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 150, radius / 2, 0.4, radius / 2, 0, debris.createBlockData());
        Vfx.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.6f);
        for (LivingEntity entity : enemies(p, at, radius)) {
            strike(p, r, entity, damage);
            Vector away = entity.getLocation().toVector().subtract(at.toVector()).setY(0);
            entity.setVelocity((away.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : away.normalize().multiply(0.8))
                    .setY(1.4));
        }
        return true;
    }

    private boolean fear(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 20);
        if (target == null) {
            return false;
        }
        beam(p.getEyeLocation(), center(target), Color.fromRGB(60, 20, 50), 1.2f, 0.25);
        int ticks = (int) (120 * amp);
        strike(p, r, target, 4 * amp);
        effect(target, PotionEffectType.DARKNESS, ticks, 0);
        effect(target, PotionEffectType.BLINDNESS, 40, 0);
        effect(target, PotionEffectType.SLOWNESS, ticks, 1);
        effect(target, PotionEffectType.WEAKNESS, ticks, 1);
        effect(target, PotionEffectType.NAUSEA, ticks, 0);
        target.sendMessage(Component.text("Fear crawls through your body.", NamedTextColor.DARK_PURPLE));
        Vfx.sound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.6f);
        return true;
    }

    private boolean tatarforas(Player p, Release r) {
        Location center = p.getLocation();
        Vfx.sound(center, Sound.ENTITY_WARDEN_ROAR, 0.8f, 0.6f);
        zone(p, () -> center, 10, 200, 10, at -> {
            Vfx.ring(at.clone().add(0, 0.2, 0), 10, 60, Color.fromRGB(40, 15, 35), 1.6f);
            Vfx.ring(at.clone().add(0, 4, 0), 10, 60, Color.fromRGB(40, 15, 35), 1.6f);
        }, (entity, tick) -> {
            effect(entity, PotionEffectType.DARKNESS, 40, 0);
            effect(entity, PotionEffectType.WEAKNESS, 20, 1);
            if (entity instanceof Player victim) {
                service.signatures().silence(victim, 700);
            }
            if (tick % 20 == 0) {
                strike(p, r, entity, 1.5);
            }
        });
        return true;
    }

    private boolean visionary(Player p, Release r, double amp) {
        Location at = aimPoint(p, 25);
        int pick = ThreadLocalRandom.current().nextInt(3);
        switch (pick) {
            case 0 -> {
                p.sendActionBar(Component.text("I imagine... bombs.", NamedTextColor.AQUA));
                for (int i = 0; i < 6; i++) {
                    Location bomb = at.clone().add(ThreadLocalRandom.current().nextGaussian() * 2.5, 0.5,
                            ThreadLocalRandom.current().nextGaussian() * 2.5);
                    later(i * 4, () -> explosion(p, r, bomb, 2.5, 3.5 * amp, 0.5));
                }
            }
            case 1 -> {
                p.sendActionBar(Component.text("I imagine... the ground is water.", NamedTextColor.AQUA));
                zone(p, () -> at, 4.5, (int) (80 * amp), 5, center -> center.getWorld().spawnParticle(Particle.SPLASH,
                        center.clone().add(0, 0.2, 0), 30, 2.5, 0.1, 2.5, 0), (entity, tick) -> {
                            effect(entity, PotionEffectType.SLOWNESS, 20, 4);
                            entity.setVelocity(new Vector(0, -0.5, 0));
                            if (tick % 20 == 0) {
                                strike(p, r, entity, 1.5 * amp);
                            }
                        });
            }
            default -> {
                p.sendActionBar(Component.text("I imagine... a rain of blades.", NamedTextColor.AQUA));
                for (int i = 0; i < 8; i++) {
                    Location blade = at.clone().add(ThreadLocalRandom.current().nextGaussian() * 2.5, 0,
                            ThreadLocalRandom.current().nextGaussian() * 2.5);
                    later(i * 3, () -> {
                        beam(blade.clone().add(0, 8, 0), blade, Color.fromRGB(220, 230, 255), 1.2f, 0.4);
                        for (LivingEntity entity : enemies(p, blade, 1.6)) {
                            strike(p, r, entity, 3 * amp);
                        }
                    });
                }
            }
        }
        return true;
    }

    private boolean meteor(Player p, Release r) {
        Location at = aimPoint(p, 45);
        Vfx.ring(at.clone().add(0, 0.1, 0), 8, 60, Color.fromRGB(255, 120, 40), 1.6f);
        BlockDisplay rock = block(at.clone().add(0, 30, 0), Material.MAGMA_BLOCK, 4f, 4f, 4f);
        Vfx.sound(at, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
        every(p, 40, 2, tick -> {
            rock.teleport(at.clone().add(0, 30 * (1 - tick / 40.0), 0));
            rock.getWorld().spawnParticle(Particle.FLAME, rock.getLocation().add(0, 2, 0), 20, 1.5, 1.5, 1.5, 0.02);
            if (tick % 10 == 0) {
                Vfx.ring(at.clone().add(0, 0.1, 0), 8, 60, Color.fromRGB(255, 120, 40), 1.6f);
            }
            if (tick >= 40) {
                remove(rock);
                explosion(p, r, at.clone().add(0, 0.5, 0), 8, 18, 1.5);
            }
        });
        return true;
    }

    private boolean loveKiss(Player p, Release r, double amp) {
        projectile(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), 1.2, 25, 1.0, false,
                at -> at.getWorld().spawnParticle(Particle.HEART, at, 1, 0, 0, 0, 0),
                (entity, at) -> {
                    strike(p, r, entity, 2);
                    control(p, entity, (int) (120 * amp), Charm.SPARE, Color.fromRGB(255, 130, 180), Particle.HEART);
                    entity.sendMessage(Component.text("Love Kiss! You can't bring yourself to hurt " + p.getName() + ".",
                            NamedTextColor.LIGHT_PURPLE));
                }, null);
        Vfx.sound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
        return true;
    }

    private boolean loveRain(Player p) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 12);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 5, 0), 80, 8, 1, 8, 0);
        for (LivingEntity victim : victims) {
            control(p, victim, 160, Charm.SPARE, Color.fromRGB(255, 130, 180), Particle.HEART);
        }
        Vfx.sound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
        return true;
    }

    private boolean xAxis(Player p, Release r, double amp) {
        Location eye = p.getEyeLocation();
        Line shot = line(p, eye, eye.getDirection(), 60, 1.0, true);
        beam(eye, shot.end(), Color.fromRGB(245, 245, 255), 1.4f, 0.2);
        Vfx.sound(eye, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.0f, 0.6f);
        for (LivingEntity entity : shot.hits()) {
            strike(p, r, entity, 9 * amp);
        }
        return true;
    }

    private boolean jilliel(Player p, Release r) {
        Location at = aimPoint(p, 40);
        every(p, 60, 2, tick -> {
            if (tick >= 60) {
                return;
            }
            Location ray = at.clone().add(ThreadLocalRandom.current().nextDouble(-10, 10), 0,
                    ThreadLocalRandom.current().nextDouble(-10, 10));
            beam(ray.clone().add(0, 18, 0), ray, Color.fromRGB(245, 245, 255), 1.2f, 0.5);
            ray.getWorld().spawnParticle(Particle.END_ROD, ray, 6, 0.3, 0.1, 0.3, 0.05);
            for (LivingEntity entity : enemies(p, ray, 1.8)) {
                strike(p, r, entity, 4);
            }
        });
        Vfx.sound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        return true;
    }

    private boolean antithesis(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 20);
        if (target == null) {
            return false;
        }
        double missing = maxHealth(p) - p.getHealth();
        if (missing < 1) {
            p.sendActionBar(Component.text("You have no wounds to reverse.", NamedTextColor.GRAY));
            return false;
        }
        double amount = Math.min(10 * amp, missing);
        heal(p, amount);
        raw(target, amount, p);
        beam(center(p), center(target), Color.fromRGB(120, 170, 255), 1.4f, 0.25);
        p.getWorld().spawnParticle(Particle.END_ROD, center(p), 20, 0.4, 0.6, 0.4, 0.05);
        Vfx.sound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.6f);
        title(p, "The Antithesis: " + Math.round(amount) + " reversed", Color.fromRGB(120, 170, 255));
        return true;
    }

    private boolean sprenger(Player p, Release r) {
        Location at = aimPoint(p, 30);
        List<Location> points = new ArrayList<>();
        List<BlockDisplay> blades = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * 2 * i / 5 - Math.PI / 2;
            Location point = at.clone().add(Math.cos(angle) * 6, 0, Math.sin(angle) * 6);
            points.add(point);
            blades.add(block(point, Material.LIGHT_BLUE_STAINED_GLASS, 0.2f, 2.4f, 0.2f));
        }
        Vfx.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
        every(p, 30, 5, tick -> {
            for (int i = 0; i < 5; i++) {
                beam(points.get(i).clone().add(0, 0.2, 0), points.get((i + 2) % 5).clone().add(0, 0.2, 0),
                        Color.fromRGB(150, 200, 255), 1.0f, 0.4);
            }
            if (tick >= 30) {
                blades.forEach(this::remove);
                at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 120, 3, 1.5, 3, 0.2);
                explosion(p, r, at.clone().add(0, 0.5, 0), 6.5, 15, 1.0);
            }
        });
        return true;
    }

    private boolean galvanoBlast(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 30);
        if (target == null) {
            return false;
        }
        target.getWorld().strikeLightningEffect(target.getLocation());
        strike(p, r, target, 9 * amp);
        effect(target, PotionEffectType.SLOWNESS, 20, 3);
        return true;
    }

    private boolean galvanoJavelin(Player p, Release r) {
        projectile(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), 2.0, 40, 1.2, false,
                at -> at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.2, 0.2, 0.2, 0.1),
                (entity, at) -> {
                }, at -> {
                    at.getWorld().strikeLightningEffect(at);
                    explosion(p, r, at, 5, 13, 0.8);
                    for (int i = 1; i <= 4; i++) {
                        later(i * 10, () -> {
                            Location bolt = at.clone().add(ThreadLocalRandom.current().nextGaussian() * 3, 0,
                                    ThreadLocalRandom.current().nextGaussian() * 3);
                            bolt.getWorld().strikeLightningEffect(bolt);
                            for (LivingEntity entity : enemies(p, bolt, 2.5)) {
                                strike(p, r, entity, 4);
                            }
                        });
                    }
                });
        Vfx.sound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.0f);
        return true;
    }

    private boolean wind(Player p, double amp, int dodges) {
        counters.put(p.getUniqueId() + ":wind", dodges);
        mode(p, Move.WIND, (int) (160 * amp));
        p.getWorld().spawnParticle(Particle.SMALL_GUST, center(p), 20, 0.5, 0.8, 0.5, 0.02);
        p.sendActionBar(Component.text("The next " + dodges + " attacks pass through you.", NamedTextColor.AQUA));
        return true;
    }

    private boolean windStorm(Player p, Release r) {
        Location at = p.getLocation();
        at.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, at, 1);
        Vfx.sound(at, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.5f);
        for (LivingEntity entity : enemies(p, at, 10)) {
            strike(p, r, entity, 6);
            Vector away = entity.getLocation().toVector().subtract(at.toVector()).setY(0);
            entity.setVelocity((away.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : away.normalize()).multiply(2.2).setY(0.6));
        }
        return wind(p, 0.75, 5);
    }

    private boolean starCheer(Player p, double amp) {
        heal(p, 8 * amp);
        effect(p, PotionEffectType.STRENGTH, (int) (120 * amp), 0);
        effect(p, PotionEffectType.ABSORPTION, (int) (200 * amp), 1);
        p.getWorld().spawnParticle(Particle.FIREWORK, center(p), 40, 0.6, 1.0, 0.6, 0.1);
        Vfx.sound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
        title(p, "Star Hero!", Color.fromRGB(255, 210, 60));
        return true;
    }

    private boolean starRocket(Player p, Release r) {
        Vector direction = p.getEyeLocation().getDirection();
        Set<UUID> hit = new HashSet<>();
        Vfx.sound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f);
        every(p, 10, 1, tick -> {
            if (tick >= 10) {
                explosion(p, r, p.getLocation().add(0, 1, 0), 4, 10, 1.2);
                p.setFallDistance(0);
                return;
            }
            p.setVelocity(direction.clone().multiply(2.0));
            p.setFallDistance(0);
            p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 6, 0.2, 0.2, 0.2, 0.05);
            for (LivingEntity entity : enemies(p, p.getLocation(), 2.2)) {
                if (hit.add(entity.getUniqueId())) {
                    strike(p, r, entity, 6);
                    entity.setVelocity(direction.clone().multiply(1.2).setY(0.6));
                }
            }
        });
        return true;
    }

    private boolean explodeBarrage(Player p, Release r) {
        effect(p, PotionEffectType.LEVITATION, 15, 3);
        effect(p, PotionEffectType.SLOW_FALLING, 100, 0);
        Vfx.sound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
        every(p, 75, 3, tick -> {
            if (tick < 15 || tick >= 75) {
                return;
            }
            LivingEntity target = nearest(p, p.getLocation(), 14, null);
            Location bomb = target != null && ThreadLocalRandom.current().nextBoolean() ? target.getLocation()
                    : p.getLocation().add(ThreadLocalRandom.current().nextDouble(-10, 10), -6,
                            ThreadLocalRandom.current().nextDouble(-10, 10));
            Location ground = ReleaseService.groundAt(bomb);
            Location at = ground != null ? ground.add(0, 0.5, 0) : bomb;
            beam(p.getLocation(), at, Color.fromRGB(255, 140, 60), 0.8f, 0.6);
            explosion(p, r, at, 2.5, 4, 0.4);
        });
        return true;
    }

    private boolean eisern(Player p, double amp) {
        int ticks = (int) (120 * amp);
        mode(p, Move.EISERN, ticks);
        anchoredResistance(p, true, anchorKey);
        later(ticks, () -> anchoredResistance(p, false, anchorKey));
        p.getWorld().spawnParticle(Particle.BLOCK, center(p), 30, 0.4, 0.8, 0.4, 0, Material.IRON_BLOCK.createBlockData());
        Vfx.sound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
        p.sendActionBar(Component.text("Eisern.", NamedTextColor.GRAY));
        return true;
    }

    private boolean gluttonMaw(Player p, Release r) {
        List<LivingEntity> victims = new ArrayList<>();
        for (LivingEntity entity : enemies(p, p.getLocation(), 9)) {
            if (inCone(p, entity, 9, 40)) {
                victims.add(entity);
            }
        }
        Location mouth = p.getEyeLocation().add(flat(p).multiply(2));
        BlockDisplay top = block(mouth.clone().add(0, 1.2, 0), Material.PINK_CONCRETE, 3f, 0.6f, 3f);
        BlockDisplay bottom = block(mouth.clone().add(0, -1.6, 0), Material.PINK_CONCRETE, 3f, 0.6f, 3f);
        Vfx.sound(mouth, Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.4f);
        every(p, 15, 1, tick -> {
            for (LivingEntity entity : victims) {
                Vector pull = mouth.toVector().subtract(entity.getLocation().toVector());
                if (pull.lengthSquared() > 2 && tick < 15) {
                    entity.setVelocity(pull.normalize().multiply(0.8));
                }
            }
            if (tick >= 15) {
                remove(top);
                remove(bottom);
                Vfx.sound(mouth, Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.6f);
                for (LivingEntity entity : victims) {
                    strike(p, r, entity, 12);
                    heal(p, 3);
                }
            }
        });
        return true;
    }

    private boolean biskiel(Player p, Release r) {
        zone(p, p::getLocation, 10, 200, 20, at -> Vfx.ring(at.clone().add(0, 0.2, 0), 10, 60,
                Color.fromRGB(230, 235, 255), 1.4f), (entity, tick) -> {
                    if (tick >= 200) {
                        return;
                    }
                    beam(center(entity), center(p), Color.fromRGB(200, 220, 255), 0.9f, 0.4);
                    strike(p, r, entity, 2);
                    for (PotionEffect active : entity.getActivePotionEffects()) {
                        if (GOOD.contains(active.getType())) {
                            entity.removePotionEffect(active.getType());
                            break;
                        }
                    }
                    heal(p, 1.5);
                });
        Vfx.sound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.6f);
        return true;
    }

    private boolean zombie(Player p, Release r, double amp) {
        int ticks = (int) (160 * amp);
        Location eye = p.getEyeLocation();
        for (double d = 1; d < 7; d += 0.6) {
            eye.getWorld().spawnParticle(Particle.DUST, eye.clone().add(eye.getDirection().multiply(d)), 6, d * 0.15,
                    d * 0.15, d * 0.15, 0, Vfx.dust(Color.fromRGB(140, 0, 40), 1.6f));
        }
        int hit = 0;
        for (LivingEntity entity : enemies(p, p.getLocation(), 8)) {
            if (inCone(p, entity, 7, 35)) {
                strike(p, r, entity, 3 * amp);
                bend(entity, p, Charm.MEND, ticks);
                setMark(entity, Move.ZOMBIE, p.getUniqueId(), 1, ticks);
                hit++;
            }
        }
        Vfx.sound(eye, Sound.ENTITY_LLAMA_SPIT, 1.0f, 0.5f);
        p.sendActionBar(Component.text(hit == 0 ? "Your blood touched no one." : "Zombie blood on " + hit + ".",
                NamedTextColor.DARK_RED));
        return true;
    }

    private void minion(Player owner, Location at) {
        Husk husk = track(at.getWorld().spawn(at, Husk.class, mob -> {
            mob.customName(Component.text(owner.getName() + "'s zombie", NamedTextColor.DARK_RED));
            mob.setCustomNameVisible(true);
            mob.setShouldBurnInDay(false);
            mob.getEquipment().clear();
        }));
        minions.put(husk.getUniqueId(), owner.getUniqueId());
        at.getWorld().spawnParticle(Particle.CRIMSON_SPORE, at.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0);
        every(owner, 400, 20, tick -> {
            if (tick >= 400 || !husk.isValid()) {
                minions.remove(husk.getUniqueId());
                remove(husk);
                return;
            }
            LivingEntity target = nearest(owner, husk.getLocation(), 16, null);
            if (target != null && !target.equals(husk.getTarget())) {
                husk.setTarget(target);
            }
        });
    }

    private boolean zombieArmy(Player p, int count) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location at = p.getLocation().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
            Location ground = ReleaseService.groundAt(at);
            minion(p, ground != null ? ground : at);
        }
        Vfx.sound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1.0f, 1.2f);
        return true;
    }

    // ================================================================ Fullbringer

    private boolean jackpot(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 5);
        if (target == null) {
            return false;
        }
        String[] reels = {"7", "BAR", "♦", "♣", "★"};
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String a = reels[random.nextInt(reels.length)];
        String b = random.nextInt(3) == 0 ? a : reels[random.nextInt(reels.length)];
        String c = random.nextInt(3) == 0 ? b : reels[random.nextInt(reels.length)];
        String spin = "[" + a + "|" + b + "|" + c + "] ";
        if (a.equals(b) && b.equals(c)) {
            strike(p, r, target, 14 * amp);
            target.setVelocity(p.getEyeLocation().getDirection().multiply(2.0).setY(0.6));
            target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center(target), 60, 0.4, 0.6, 0.4, 0.5);
            Vfx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
            title(p, spin + "JACKPOT!", Color.fromRGB(255, 200, 50));
        } else if (a.equals(b) || b.equals(c) || a.equals(c)) {
            strike(p, r, target, 7 * amp);
            title(p, spin + "Win.", Color.fromRGB(255, 220, 120));
        } else {
            strike(p, r, target, 2);
            raw(p, 3, p);
            title(p, spin + "Bust...", Color.fromRGB(160, 160, 160));
        }
        Vfx.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.8f);
        return true;
    }

    private boolean elDirecto(Player p, Release r, double amp) {
        Location eye = p.getEyeLocation();
        Line blast = line(p, eye, eye.getDirection(), 14 * Math.sqrt(amp), 1.8, false);
        for (double s = 0; s < 1; s += 0.05) {
            Location at = eye.clone().add(blast.end().toVector().subtract(eye.toVector()).multiply(s));
            at.getWorld().spawnParticle(Particle.DUST, at, 4, 0.4, 0.4, 0.4, 0, Vfx.dust(r.form.color(), 1.8f));
        }
        Vfx.sound(eye, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
        for (LivingEntity entity : blast.hits()) {
            strike(p, r, entity, 8 * amp);
            entity.setVelocity(eye.getDirection().multiply(1.5).setY(0.4));
        }
        return true;
    }

    private boolean gigante(Player p) {
        mode(p, Move.GIGANTE, 300);
        anchoredResistance(p, true, anchorKey);
        later(300, () -> anchoredResistance(p, false, anchorKey));
        p.getWorld().spawnParticle(Particle.DUST, center(p), 60, 0.5, 0.8, 0.5, 0, Vfx.dust(Color.fromRGB(80, 40, 120), 2f));
        Vfx.sound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.6f);
        return true;
    }

    private boolean insert(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 6);
        if (target == null) {
            return false;
        }
        strike(p, r, target, 5 * amp);
        bend(target, p, Charm.SPARE, (int) (200 * amp));
        target.getWorld().spawnParticle(Particle.ENCHANT, center(target), 40, 0.4, 0.6, 0.4, 0.5);
        Vfx.sound(target.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);
        target.sendMessage(Component.text("You remember " + p.getName() + "... an old friend. You could never hurt them.",
                NamedTextColor.GRAY, TextDecoration.ITALIC));
        return true;
    }

    private boolean rewrite(Player p) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 12);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        for (LivingEntity victim : victims) {
            bend(victim, p, Charm.SPARE, 200);
            effect(victim, PotionEffectType.GLOWING, 200, 0);
            victim.getWorld().spawnParticle(Particle.ENCHANT, center(victim), 30, 0.4, 0.6, 0.4, 0.5);
            victim.sendMessage(Component.text("Your memories of " + p.getName() + " have been rewritten.",
                    NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        Vfx.sound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.5f);
        return true;
    }

    private boolean laMuerte(Player p, Release r, double amp) {
        projectile(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), 1.3, 30, 1.6, false,
                at -> {
                    at.getWorld().spawnParticle(Particle.DUST, at, 10, 0.5, 0.5, 0.5, 0, Vfx.dust(r.form.color(), 2f));
                    at.getWorld().spawnParticle(Particle.SOUL, at, 2, 0.3, 0.3, 0.3, 0.01);
                }, (entity, at) -> {
                }, at -> explosion(p, r, at, 3.5, 10 * amp, 0.9));
        Vfx.sound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
        return true;
    }

    private boolean crossSteal(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 12);
        if (target == null) {
            return false;
        }
        int stolen = steal(target, p);
        strike(p, r, target, 5 * amp);
        heal(p, 5 * amp);
        beam(center(target), center(p), Color.fromRGB(90, 110, 140), 1.4f, 0.25);
        Vfx.sound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.4f);
        p.sendActionBar(Component.text("Stolen: " + stolen + " buffs and their strength.", NamedTextColor.BLUE));
        return true;
    }

    private boolean dirtyBoots(Player p, double amp) {
        int dirt = counter(p.getUniqueId(), "dirt");
        counters.remove(p.getUniqueId() + ":dirt");
        int level = Math.min(2, dirt / 4);
        effect(p, PotionEffectType.STRENGTH, (int) (200 * amp), level);
        effect(p, PotionEffectType.SPEED, (int) (200 * amp), 0);
        p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 40 + dirt * 4, 0.4, 0.2, 0.4, 0,
                Material.MUD.createBlockData());
        title(p, "Dirty Boots: dirt " + dirt + " → Strength " + (level + 1), Color.fromRGB(120, 90, 60));
        return true;
    }

    private void shrink(Player p, LivingEntity victim, int ticks) {
        scale(victim, 0.35, ticks);
        effect(victim, PotionEffectType.WEAKNESS, ticks, 4);
        effect(victim, PotionEffectType.SLOWNESS, ticks, 1);
        victim.getWorld().spawnParticle(Particle.HEART, victim.getEyeLocation(), 6, 0.3, 0.3, 0.3, 0);
        if (victim instanceof Player shrunk) {
            shrunk.sendMessage(Component.text("You've been brought into the Dollhouse!", NamedTextColor.LIGHT_PURPLE));
        }
    }

    private boolean dollhouse(Player p, Release r, double amp) {
        projectile(p, p.getEyeLocation(), p.getEyeLocation().getDirection(), 1.4, 25, 1.0, false,
                at -> at.getWorld().spawnParticle(Particle.HEART, at, 1, 0, 0, 0, 0),
                (entity, at) -> {
                    strike(p, r, entity, 2);
                    shrink(p, entity, (int) (100 * amp));
                }, null);
        Vfx.sound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.8f);
        return true;
    }

    private boolean bringIn(Player p) {
        List<LivingEntity> victims = enemies(p, p.getLocation(), 9);
        if (victims.isEmpty()) {
            p.sendActionBar(Component.text("No enemy near you.", NamedTextColor.GRAY));
            return false;
        }
        for (LivingEntity victim : victims) {
            shrink(p, victim, 160);
        }
        Vfx.sound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 2.0f);
        return true;
    }

    private boolean invaders(Player p, Release r, double amp) {
        Location at = aimPoint(p, 25);
        double half = 3.5;
        int ticks = (int) (160 * amp);
        List<BlockDisplay> frame = new ArrayList<>();
        for (int corner = 0; corner < 4; corner++) {
            double x = (corner & 1) == 0 ? -half : half;
            double z = (corner & 2) == 0 ? -half : half;
            frame.add(block(at.clone().add(x, 0, z), Material.LIGHT_BLUE_STAINED_GLASS, 0.3f, 5f, 0.3f));
        }
        Vfx.sound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.6f);
        every(p, ticks, 2, tick -> {
            if (tick >= ticks) {
                frame.forEach(this::remove);
                return;
            }
            if (tick % 10 == 0) {
                for (int e = 0; e < 4; e++) {
                    Location a = at.clone().add((e & 1) == 0 ? -half : half, 5, (e & 2) == 0 ? -half : half);
                    Location b = at.clone().add((e & 1) == 0 ? half : -half, 5, (e & 2) == 0 ? -half : half);
                    beam(a, b, Color.fromRGB(80, 220, 255), 0.9f, 0.6);
                }
            }
            for (LivingEntity entity : enemies(p, at, half * 1.8)) {
                Vector offset = entity.getLocation().toVector().subtract(at.toVector());
                if (Math.abs(offset.getX()) > half - 0.4 || Math.abs(offset.getZ()) > half - 0.4) {
                    entity.setVelocity(offset.setY(0).normalize().multiply(-0.6).setY(0.1));
                }
                if (Math.abs(offset.getX()) <= half + 0.6 && Math.abs(offset.getZ()) <= half + 0.6 && tick % 20 == 0) {
                    beam(at.clone().add(offset.getX(), 5, offset.getZ()), center(entity), Color.fromRGB(120, 255, 120),
                            1.0f, 0.4);
                    strike(p, r, entity, 1.5 * amp);
                    Vfx.sound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 2.0f);
                }
            }
        });
        return true;
    }

    private boolean finalStage(Player p, Release r) {
        List<BlockDisplay> drones = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            drones.add(block(p.getLocation().add(0, 3, 0), i % 2 == 0 ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_CONCRETE,
                    0.6f, 0.4f, 0.6f));
        }
        Vfx.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.5f);
        every(p, 300, 2, tick -> {
            if (tick >= 300 || !p.isOnline()) {
                drones.forEach(this::remove);
                return;
            }
            for (int i = 0; i < drones.size(); i++) {
                double angle = Math.PI * 2 * i / drones.size() + tick * 0.06;
                drones.get(i).teleport(p.getLocation().add(Math.cos(angle) * 2.5, 3 + Math.sin(tick * 0.1 + i) * 0.3,
                        Math.sin(angle) * 2.5));
            }
            if (tick % 20 == 0) {
                int pick = (tick / 20) % drones.size();
                LivingEntity target = nearest(p, p.getLocation(), 16, null);
                if (target != null) {
                    beam(drones.get(pick).getLocation(), center(target), Color.fromRGB(120, 255, 120), 1.2f, 0.4);
                    strike(p, r, target, 3);
                    Vfx.sound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
                }
            }
        });
        return true;
    }

    private boolean soten(Player p, double amp) {
        Location center = p.getLocation();
        int ticks = (int) (120 * amp);
        Vfx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
        every(p, ticks, 10, tick -> {
            if (tick >= ticks) {
                return;
            }
            for (int ring = 0; ring < 3; ring++) {
                double y = ring * 1.6;
                Vfx.ring(center.clone().add(0, y + 0.2, 0), Math.sqrt(25 - y * y), 36, Color.fromRGB(255, 190, 120), 1.2f);
            }
            for (Player ally : center.getNearbyPlayers(5)) {
                if (ally.equals(p) || AwakeningPowers.allies(p, ally)) {
                    if (tick % 20 == 0) {
                        heal(ally, 1.5 * amp);
                        cleanse(ally);
                    }
                    effect(ally, PotionEffectType.REGENERATION, 30, 1);
                }
            }
        });
        return true;
    }

    private boolean timeBet(Player p, Release r, double amp) {
        LivingEntity target = aimed(p, 20);
        if (target == null) {
            return false;
        }
        UUID id = p.getUniqueId();
        long until = System.currentTimeMillis() + 10_000;
        bets.put(id, new Bet(target.getUniqueId(), 0, until));
        p.sendMessage(Component.text("Contract: land 3 hits on " + target.getName()
                + " within 10 seconds. Fail, and you pay.", NamedTextColor.GOLD));
        Vfx.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 1.2f);
        every(p, 200, 10, tick -> {
            Bet bet = bets.get(id);
            if (bet == null || !bet.target().equals(target.getUniqueId()) || bet.until() != until) {
                return;
            }
            if (target.isValid()) {
                Vfx.ring(target.getEyeLocation().add(0, 0.8, 0), 0.4, 12, Color.fromRGB(200, 170, 120), 1.2f);
            }
            if (tick % 20 == 0 && tick < 200) {
                Vfx.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.4f, 2.0f);
            }
            if (bet.hits() >= 3 || tick >= 200) {
                bets.remove(id);
                if (bet.hits() >= 3 && target.isValid() && !target.isDead()) {
                    explosion(p, r, center(target), 2.5, 14 * amp, 0.6);
                    title(p, "Time tells no lies. Contract fulfilled.", Color.fromRGB(200, 170, 120));
                } else {
                    raw(p, 6, p);
                    title(p, "Contract broken. You pay.", Color.fromRGB(160, 160, 160));
                }
            }
        });
        return true;
    }

    private boolean timeLimit(Player p) {
        effect(p, PotionEffectType.STRENGTH, 300, 1);
        effect(p, PotionEffectType.SPEED, 300, 1);
        effect(p, PotionEffectType.RESISTANCE, 300, 0);
        title(p, "Fifteen seconds. Starting now.", Color.fromRGB(200, 170, 120));
        Vfx.sound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
        later(300, () -> {
            if (p.isOnline()) {
                effect(p, PotionEffectType.WEAKNESS, 200, 1);
                effect(p, PotionEffectType.SLOWNESS, 200, 0);
                p.sendActionBar(Component.text("Time's up. The contract takes its due.", NamedTextColor.GRAY));
            }
        });
        return true;
    }

    /** All kits, for listings. */
    static List<Canon.Kit> roster() {
        return Canon.all();
    }
}
