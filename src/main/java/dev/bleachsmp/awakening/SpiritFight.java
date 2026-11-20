package dev.bleachsmp.awakening;

import dev.bleachsmp.BleachSmpPlugin;
import dev.bleachsmp.awakening.ReleaseService.Mechanic;
import dev.bleachsmp.faction.Faction;
import dev.bleachsmp.fx.Vfx;
import dev.bleachsmp.item.StarterItems;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.TriState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** A single mirror duel. A cast's geometry is fixed when its warning appears, so it can be dodged. */
final class SpiritFight {
    private static final int WARNING_TICKS = 15;
    private static final int ENDING_TICKS = 60;
    private static final int GRACE_TICKS = 40;

    private record Cast(Mechanic move, Location origin, Location target, Vector direction, int resolveAt) {
        Location along(double distance) {
            return origin.clone().add(direction.clone().multiply(distance));
        }
    }

    private final BleachSmpPlugin plugin;
    private final InnerWorld arena;
    private final Player owner;
    private final BankaiService.Kit kit;
    private final Faction faction;
    private final ReleaseService.Form form;
    private final Element element;
    private final Mechanic main, secondary;
    private final Zombie spirit;
    private final double maxHealth;
    private BukkitTask task;
    private int tick, limit, nextCast, secondLineAt = -1, endingAt = -1, lossAt = -1;
    private boolean second, half, quarter;
    private int unstable;
    private Cast cast;
    private String followup;

    SpiritFight(BleachSmpPlugin plugin, InnerWorld arena, Player owner, BankaiService.Kit kit, Faction faction,
            Location arrival, double health) {
        this.plugin = plugin;
        this.arena = arena;
        this.owner = owner;
        this.kit = kit;
        this.faction = faction;
        UUID id = owner.getUniqueId();
        form = ReleaseService.form(id, faction, kit.style(), true);
        element = ReleaseService.element(id, faction, kit.style(), true);
        main = ReleaseService.primary(id, kit.style(), true);
        secondary = ReleaseService.secondary(id, kit.style(), kit.trait(), true);
        maxHealth = health;
        // A zombie is player-shaped and has vanilla melee pathfinding; mannequins have no mob AI.
        Location spawn = arrival.clone().add(0, 0, 19).setDirection(new Vector(0, 0, -1));
        spirit = spawn.getWorld().spawn(spawn, Zombie.class, false, zombie -> {
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setCanPickupItems(false);
            zombie.setPersistent(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setDespawnInPeacefulOverride(TriState.FALSE);
            zombie.getAttribute(Attribute.SPAWN_REINFORCEMENTS).setBaseValue(0);
            zombie.customName(Component.text(form.name(), form.text()));
            zombie.setCustomNameVisible(true);
            zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            zombie.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(6.0);
            zombie.setHealth(health);
            var gear = zombie.getEquipment();
            gear.setHelmet(armor(Material.LEATHER_HELMET));
            gear.setChestplate(armor(Material.LEATHER_CHESTPLATE));
            gear.setLeggings(armor(Material.LEATHER_LEGGINGS));
            gear.setBoots(armor(Material.LEATHER_BOOTS));
            gear.setItemInMainHand(new StarterItems(plugin).create(faction));
            gear.setHelmetDropChance(0);
            gear.setChestplateDropChance(0);
            gear.setLeggingsDropChance(0);
            gear.setBootsDropChance(0);
            gear.setItemInMainHandDropChance(0);
        });
        spirit.setTarget(owner);
    }

    private ItemStack armor(Material material) {
        ItemStack item = new ItemStack(material);
        var meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(form.color());
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    Player owner() { return owner; }
    UUID spiritId() { return spirit.getUniqueId(); }
    boolean hasSpirit() { return !spirit.isDead(); }
    String formName() { return form.name(); }
    BankaiService.Kit kit() { return kit; }
    boolean finished() { return endingAt >= 0 || lossAt >= 0; }

    void start(int limit) {
        this.limit = limit;
        scheduleCast();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    void close() {
        if (task != null) {
            task.cancel();
        }
        spirit.remove();
    }

    void fail() {
        if (lossAt < 0) {
            lossAt = tick + 1;
        }
    }

    void onDamage(EntityDamageEvent event) {
        if (finished()) {
            event.setCancelled(true);
        } else if (event.getFinalDamage() >= spirit.getHealth()) {
            event.setCancelled(true);
            endingAt = tick + ENDING_TICKS;
            cast = null;
            spirit.setAI(false);
            spirit.setVelocity(new Vector());
            spirit.setPose(org.bukkit.entity.Pose.SNEAKING, true);
            speak(3);
        }
    }

    private void tick() {
        tick++;
        if (lossAt >= 0 && tick >= lossAt) {
            arena.lose(owner, "defeated");
            return;
        }
        if (spirit.isDead()) {
            // Killed outside the normal damage path (e.g. /kill): never a free win, never a stranding.
            arena.lose(owner, "spirit removed");
            return;
        }
        // Right after the cross-world teleport the arena chunks may not be entity-ticking yet, so the player's
        // position and the spirit's validity can briefly read wrong. Only end the trial if it persists.
        if (!arena.inArena(owner) || !spirit.isValid()) {
            // Readings during the grace period don't count toward the one-second limit.
            unstable = tick > GRACE_TICKS ? unstable + 1 : 0;
            if (unstable >= 20) {
                arena.lose(owner, !spirit.isValid() ? "spirit not valid (chunk unloaded?)" : "left the arena");
                return;
            }
            if (!spirit.isValid()) {
                return;
            }
        } else {
            unstable = 0;
        }
        if (tick == secondLineAt) {
            subtitle(owner, followup);
        }
        if (endingAt >= 0) {
            if (tick >= endingAt) {
                element.eruption(spirit.getLocation(), true);
                arena.win(owner, this);
            }
            return;
        }
        if (tick >= limit) {
            arena.lose(owner, "time limit");
            return;
        }
        if (spirit.getTarget() != owner) {
            spirit.setTarget(owner);
        }
        if (tick == 36 && !half) {
            speak(0);
        }
        if (!half && spirit.getHealth() <= maxHealth * 0.5) {
            half = true;
            speak(1);
            spirit.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, limit - tick, 0));
            nextCast = Math.min(nextCast, tick + 60);
        }
        if (!quarter && spirit.getHealth() <= maxHealth * 0.25) {
            quarter = true;
            speak(2);
            element.eruption(spirit.getLocation(), true);
            if (owner.getLocation().distanceSquared(spirit.getLocation()) <= 100) {
                push(spirit.getLocation(), 1.15, 0.48);
            }
        }
        if (cast == null && tick >= nextCast) {
            beginCast(second ? secondary : main);
            second = !second;
        }
        if (cast != null && tick >= cast.resolveAt()) {
            resolve(cast);
            cast = null;
            spirit.setAI(true);
            spirit.setTarget(owner);
        } else if (cast != null && (cast.resolveAt() - tick) % 3 == 0) {
            telegraph(cast);
        }
    }

    private void scheduleCast() {
        nextCast = tick + ThreadLocalRandom.current().nextInt(half ? 60 : 80, half ? 81 : 121);
    }

    private void beginCast(Mechanic move) {
        Location origin = spirit.getLocation();
        Location target = owner.getLocation();
        Vector direction = target.toVector().subtract(origin.toVector()).setY(0);
        direction = direction.lengthSquared() > 0.01 ? direction.normalize() : new Vector(0, 0, 1);
        cast = new Cast(move, origin, target, direction, tick + WARNING_TICKS);
        scheduleCast();
        spirit.lookAt(owner);
        spirit.setAI(false);
        spirit.setVelocity(new Vector());
        element.castSound(origin, true);
    }

    private void telegraph(Cast warning) {
        Location from = warning.origin().clone().add(0, 0.15, 0);
        Location target = warning.target().clone().add(0, 0.15, 0);
        switch (warning.move()) {
            case WAVE, DASH, PILLARS -> Vfx.line(from, warning.along(lineRange(warning.move())).add(0, 0.15, 0),
                    form.color(), 1.45f, 0.65);
            case BEAM -> Vfx.line(from.clone().add(0, 1, 0), warning.along(28).add(0, 1.15, 0), form.color(), 1.3f, 0.85);
            case BLINK, FLURRY, SCATTER, RAIN, SKYFALL -> Vfx.ring(target, radius(warning.move()), 20, form.color(), 1.4f);
            case SLAM, WARD, CHAINS -> Vfx.ring(from, radius(warning.move()), 24, form.color(), 1.4f);
        }
    }

    private void resolve(Cast attack) {
        Location from = attack.origin();
        Location target = attack.target();
        switch (attack.move()) {
            case WAVE -> {
                drawLine(attack, 0.8);
                if (inLine(attack, 1.8)) {
                    strike(6);
                    push(from, 0.6, 0.2);
                }
            }
            case BEAM -> {
                drawLine(attack, 1.4);
                if (inLine(attack, 1.1)) {
                    strike(8);
                }
            }
            case DASH -> {
                drawLine(attack, 0.6);
                dashTo(attack);
                if (inLine(attack, 1.55)) {
                    strike(6.5);
                }
            }
            case PILLARS -> {
                for (int d = 2; d <= lineRange(Mechanic.PILLARS); d += 2) {
                    Location pillar = attack.along(d);
                    element.burst(pillar, 0.8);
                    Vfx.line(pillar, pillar.clone().add(0, 4, 0), form.color(), 1.8f, 0.4);
                }
                if (inLine(attack, 1.6)) {
                    strike(7);
                    push(from, 0.25, 0.7);
                }
            }
            case BLINK -> {
                Location behind = target.clone().add(attack.direction().clone().multiply(1.8));
                stepTo(from, behind, 14);
                element.burst(target, 0.9);
                if (inside(target, attack.move(), 2)) {
                    strike(7);
                    effect(PotionEffectType.BLINDNESS, 35);
                }
            }
            case FLURRY -> {
                stepTo(from, target.clone().subtract(attack.direction().clone().multiply(1.5)), 13);
                Vfx.arc(target, attack.direction(), 2.2, form.color(), 1.5f, 0.7);
                Vfx.arc(target, attack.direction().clone().multiply(-1), 1.7, form.color(), 1.2f, 1.3);
                if (inside(target, attack.move(), 2)) {
                    strike(9);
                }
            }
            case SCATTER -> {
                for (int i = 0; i < 5; i++) {
                    double angle = i * Math.PI * 2 / 5;
                    element.burst(target.clone().add(Math.cos(angle) * 3, 0.6, Math.sin(angle) * 3), 0.8);
                }
                stepTo(from, target.clone().add(-3 * attack.direction().getZ(), 0, 3 * attack.direction().getX()), 16);
                if (inside(target, attack.move(), 2.5)) {
                    strike(5);
                    effect(PotionEffectType.BLINDNESS, 45);
                }
            }
            case RAIN -> {
                for (int i = 0; i < 7; i++) {
                    double angle = i * Math.PI * 2 / 7;
                    Location bolt = target.clone().add(Math.cos(angle) * 2.6, 0, Math.sin(angle) * 2.6);
                    Vfx.line(bolt.clone().add(0, 8, 0), bolt, form.color(), 1.5f, 0.6);
                    element.burst(bolt, 0.5);
                }
                if (inside(target, attack.move(), 12)) {
                    strike(7.5);
                }
            }
            case SKYFALL -> {
                Vfx.line(from.clone().add(0, 5, 0), target, form.color(), 1.65f, 0.55);
                stepTo(from, target, 18);
                impact(target, attack.move());
                if (inside(target, attack.move(), 3)) {
                    strike(8);
                    push(target, 0.65, 0.55);
                }
            }
            case SLAM -> {
                impact(from, attack.move());
                if (inside(from, attack.move(), 2.4)) {
                    strike(7);
                    push(from, 0.6, 0.6);
                }
            }
            case WARD -> {
                impact(from, attack.move());
                spirit.setHealth(Math.min(maxHealth, spirit.getHealth() + 7));
                spirit.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 0));
                if (inside(from, attack.move(), 3)) {
                    push(from, 1.2, 0.4);
                }
            }
            case CHAINS -> {
                impact(from, attack.move());
                if (inside(from, attack.move(), 3)) {
                    Vfx.line(from.clone().add(0, 1, 0), owner.getLocation().add(0, 1, 0), form.color(), 1.65f, 0.25);
                    strike(5);
                    push(from, -0.8, 0.15);
                    effect(PotionEffectType.SLOWNESS, 45);
                }
            }
        }
    }

    private static double lineRange(Mechanic move) {
        return switch (move) {
            case WAVE -> 18;
            case DASH -> 10;
            case PILLARS -> 14;
            case BEAM -> 28;
            default -> throw new IllegalArgumentException("Not a line cast: " + move);
        };
    }

    private static double radius(Mechanic move) {
        return switch (move) {
            case BLINK -> 2.5;
            case FLURRY -> 3;
            case SCATTER -> 4;
            case WARD -> 4.5;
            case RAIN, SKYFALL -> 4.8;
            case SLAM -> 5;
            case CHAINS -> 7;
            default -> throw new IllegalArgumentException("Not a radial cast: " + move);
        };
    }

    private boolean inside(Location center, Mechanic move, double height) {
        Vector delta = owner.getLocation().toVector().subtract(center.toVector());
        return Math.abs(delta.getY()) <= height && delta.setY(0).lengthSquared() <= Math.pow(radius(move), 2);
    }

    private boolean inLine(Cast attack, double width) {
        Vector delta = owner.getLocation().toVector().subtract(attack.origin().toVector());
        if (Math.abs(delta.getY()) > 2.4 || !spirit.hasLineOfSight(owner)) {
            return false;
        }
        delta.setY(0);
        double along = delta.dot(attack.direction());
        return along >= 0 && along <= lineRange(attack.move())
                && delta.subtract(attack.direction().clone().multiply(along)).lengthSquared() <= width * width;
    }

    private void drawLine(Cast attack, double y) {
        Location origin = attack.origin().clone().add(0, y, 0);
        Location end = attack.along(lineRange(attack.move())).add(0, y, 0);
        Vfx.line(origin, end, form.color(), 1.9f, 0.35);
        element.burst(origin, 0.85);
    }

    private void dashTo(Cast attack) {
        for (int i = 1; i <= lineRange(attack.move()); i++) {
            if (!stepTo(attack.origin(), attack.along(i), 10)) {
                break;
            }
        }
    }

    private boolean stepTo(Location from, Location point, double range) {
        if (from.distanceSquared(point) > range * range || !point.getBlock().isPassable()
                || !point.clone().add(0, 1, 0).getBlock().isPassable()
                || !point.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            return false;
        }
        spirit.teleport(point.setDirection(from.getDirection()));
        return true;
    }

    private void impact(Location at, Mechanic move) {
        Vfx.ring(at.clone().add(0, 0.2, 0), radius(move), 34, form.color(), 1.7f);
        element.burst(at, 1.3);
        element.impactSound(at);
    }

    private void strike(double damage) {
        owner.damage(damage, spirit);
    }

    private void push(Location from, double strength, double height) {
        Vector direction = owner.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (lossAt < 0) {
            owner.setVelocity((direction.lengthSquared() < 0.01 ? direction : direction.normalize().multiply(strength))
                    .setY(height));
        }
    }

    private void effect(PotionEffectType type, int duration) {
        if (lossAt < 0) {
            owner.addPotionEffect(new PotionEffect(type, duration, 0));
        }
    }

    private void speak(int phase) {
        String[] lines = voice(faction, phase);
        subtitle(owner, lines[0]);
        followup = lines[1];
        secondLineAt = tick + 27;
    }

    static void subtitle(Player player, String line) {
        player.showTitle(Title.title(Component.empty(), Component.text(line, NamedTextColor.GRAY, TextDecoration.ITALIC),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1250), Duration.ofMillis(150))));
    }

    static String[] voice(Faction faction, int phase) {
        return switch (faction) {
            case SOUL_REAPER -> switch (phase) {
                case 0 -> new String[] {"You learned my name. Now learn what I am.", "Draw your blade."};
                case 1 -> new String[] {"Is that all your resolve is worth?", "Show me why I should follow you."};
                case 2 -> new String[] {"Feel the weight of it.", "Don't let it break your stance."};
                case 3 -> new String[] {"You finally heard me.", "I'll be there when you call."};
                default -> new String[] {"You finally fight like you mean it.", "Then say it. Say my name."};
            };
            case HOLLOW -> switch (phase) {
                case 0 -> new String[] {"A king rides the horse. Which of us is the horse?", "Come take the reins."};
                case 1 -> new String[] {"There's that hunger.", "Stop pretending it isn't yours."};
                case 2 -> new String[] {"The reins are slipping.", "Hold on tighter!"};
                case 3 -> new String[] {"Fine. You're the king.", "For now."};
                default -> new String[] {"Don't you dare die in the dirt.", "Bare your fangs. Mine are yours."};
            };
            case QUINCY -> switch (phase) {
                case 0 -> new String[] {"Every Quincy before you stands behind me.", "Show them how you draw."};
                case 1 -> new String[] {"Your hand is shaking.", "Breathe, then take the shot."};
                case 2 -> new String[] {"Keep your feet beneath you.", "The next shot is yours."};
                case 3 -> new String[] {"I see you clearly now.", "Carry our name with you."};
                default -> new String[] {"You have one shot left.", "Let all of us draw it with you."};
            };
            case FULLBRINGER -> switch (phase) {
                case 0 -> new String[] {"This was always yours.", "Take it, if you can."};
                case 1 -> new String[] {"A few cracks don't make it worthless.", "Why do you think you're any different?"};
                case 2 -> new String[] {"The room might break.", "That doesn't mean you have to."};
                case 3 -> new String[] {"You held on.", "So will I."};
                default -> new String[] {"Remember what you refused to let go of.", "Now reach for it."};
            };
        };
    }
}
