package dev.bleachsmp.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One animated effect model: an item display only resource-pack players can see. Methods chain and are safe to
 * call on an effect that was never spawned (nobody nearby had the pack), so callers never need to check.
 *
 * <p>Poses are relative to the effect's location, in world axes: {@code translation} offsets it, {@code rotation}
 * turns the model, and {@code scale} stretches it along the model's own axes.
 */
public final class Effect {
    private static final Quaternionf UNFLIP = new Quaternionf().rotateY((float) Math.PI);

    private record Keyframe(int at, int duration, Vector3f translation, Quaternionf rotation, Vector3f scale) {
    }

    final ItemDisplay display;
    private final VfxKind kind;
    private final Color color;
    private final List<Keyframe> keyframes = new ArrayList<>();
    private Vector3f translation = new Vector3f();
    private Quaternionf rotation = new Quaternionf();
    private Vector3f scale = new Vector3f(1);
    private int age;
    private int life = 20;
    private int fade = 4;
    private int stage;
    private int tone;
    private Entity follow;
    private Vector offset;

    Effect(ItemDisplay display, VfxKind kind, Color color) {
        this.display = display;
        this.kind = kind;
        this.color = color;
    }

    static ItemStack item(VfxKind kind, Color color, int stage) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey(VfxAssets.NAMESPACE, "vfx/" + kind.name().toLowerCase(Locale.ROOT)));
            var data = meta.getCustomModelDataComponent();
            data.setFloats(List.of((float) stage));
            data.setColors(List.of(color));
            meta.setCustomModelDataComponent(data);
        });
        return item;
    }

    public boolean alive() {
        return display != null && display.isValid() && age < life;
    }

    /** Sets the pose immediately, without animating. Use this before the effect's first tick. */
    public Effect pose(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        this.translation = new Vector3f(translation);
        this.rotation = new Quaternionf(rotation);
        this.scale = new Vector3f(scale);
        apply(0);
        return this;
    }

    public Effect pose(Quaternionf rotation, Vector3f scale) {
        return pose(new Vector3f(), rotation, scale);
    }

    /** Animates to a pose, starting {@code delay} ticks from now (at least one) and taking {@code duration}. */
    public Effect to(int delay, int duration, Vector3f translation, Quaternionf rotation, Vector3f scale) {
        if (display != null) {
            keyframes.add(new Keyframe(age + Math.max(1, delay), duration, new Vector3f(translation),
                    new Quaternionf(rotation), new Vector3f(scale)));
        }
        return this;
    }

    public Effect to(int delay, int duration, Quaternionf rotation, Vector3f scale) {
        return to(delay, duration, translation, rotation, scale);
    }

    /** Animates the scale only. */
    public Effect grow(int delay, int duration, Vector3f scale) {
        return to(delay, duration, translation, rotation, scale);
    }

    /** Animates from the current pose right now, for effects updated every tick or so. */
    public Effect move(int duration, Vector3f translation, Quaternionf rotation, Vector3f scale) {
        this.translation = new Vector3f(translation);
        this.rotation = new Quaternionf(rotation);
        this.scale = new Vector3f(scale);
        apply(duration);
        return this;
    }

    /** Total ticks the effect lives from now, the last {@code fade} of them fading out. */
    public Effect life(int ticks, int fade) {
        this.life = age + ticks;
        this.fade = Math.max(1, Math.min(fade, ticks));
        return this;
    }

    /** Keeps the effect's location on an entity, plus a world-space offset, every tick. */
    public Effect follow(Entity entity, Vector offset) {
        this.follow = entity;
        this.offset = offset.clone();
        if (display != null) {
            display.setTeleportDuration(1);
        }
        return this;
    }

    /** Holds the effect at a fainter fade stage (0 = full, up to {@code VfxAssets.STAGES - 1}) until it fades. */
    public Effect tone(int stage) {
        tone = Math.clamp(stage, 0, VfxAssets.STAGES - 1);
        return this;
    }

    public Effect offset(Vector offset) {
        this.offset = offset.clone();
        return this;
    }

    public Location location() {
        return display == null ? null : display.getLocation();
    }

    /** Advances one tick. Returns false once the effect is finished and removed. */
    boolean tick() {
        if (display == null) {
            return false;
        }
        if (!display.isValid() || age >= life || (follow != null && !follow.isValid())) {
            display.remove();
            return false;
        }
        for (var iterator = keyframes.iterator(); iterator.hasNext(); ) {
            Keyframe frame = iterator.next();
            if (frame.at() <= age) {
                iterator.remove();
                translation = frame.translation();
                rotation = frame.rotation();
                scale = frame.scale();
                apply(frame.duration());
            }
        }
        if (follow != null) {
            Location at = follow.getLocation().add(offset);
            at.setYaw(0);
            at.setPitch(0);
            display.teleport(at);
        }
        int fadeStart = life - fade;
        int wanted = age < fadeStart ? tone
                : Math.max(tone, Math.min(VfxAssets.STAGES - 1, 1 + (age - fadeStart) * (VfxAssets.STAGES - 1) / fade));
        if (wanted != stage) {
            stage = wanted;
            display.setItemStack(item(kind, color, stage));
        }
        age++;
        return true;
    }

    void remove() {
        if (display != null) {
            display.remove();
        }
    }

    private void apply(int duration) {
        if (display == null) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        // Item displays draw their model half a turn about Y; turning it back first keeps models as authored.
        display.setTransformation(new Transformation(translation, rotation, scale, UNFLIP));
        float reach = Math.max(scale.x, Math.max(scale.y, scale.z)) * 1.5f + translation.length();
        display.setDisplayWidth(reach * 2 + 1);
        display.setDisplayHeight(reach * 2 + 1);
    }
}
