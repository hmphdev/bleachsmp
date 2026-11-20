package dev.bleachsmp.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Generates the resource pack's effect models: glowing, tintable meshes (slashes, rings, beams, pillars, orbs,
 * flares, flames, spirals, wings) plus a few solid props (horns, plates, ground cracks).
 *
 * <p>Glow layers use greyscale textures tinted by the item's custom model data colour, so one model serves every
 * form's colour; core layers are left untinted and stay white-hot. Each model comes in {@link #STAGES} fade stages
 * with progressively more transparent textures, picked by the item's first custom model data float.
 */
public final class VfxAssets {
    static final String NAMESPACE = "bleachsmp";
    static final int STAGES = 4;
    private static final float[] STAGE_ALPHA = {1f, .62f, .36f, .16f};

    private VfxAssets() {
    }

    /** Models are written in blocks: one block is sixteen model units, centred on the model's origin. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, BufferedImage> textures = new LinkedHashMap<>();
        textures.put("strip", strip(.30f));
        textures.put("strip_core", strip(.11f));
        textures.put("edge", edge());
        textures.put("shell", shell(false));
        textures.put("fade_up", shell(true));
        textures.put("solid", solid(1f));
        textures.put("flare", flare(false));
        textures.put("flare_core", flare(true));
        textures.put("flame", flame(false));
        textures.put("flame_core", flame(true));
        textures.put("feather", feather());
        textures.put("crack", crack());
        textures.put("bone", bone());
        for (Map.Entry<String, BufferedImage> texture : textures.entrySet()) {
            boolean fades = !texture.getKey().equals("bone");
            for (int stage = 0; stage < (fades ? STAGES : 1); stage++) {
                files.put("assets/" + NAMESPACE + "/textures/item/vfx/" + texture.getKey() + "_" + stage + ".png",
                        png(fade(texture.getValue(), STAGE_ALPHA[stage])));
            }
        }
        for (VfxKind kind : VfxKind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT);
            JsonObject[] stages = new JsonObject[STAGES];
            for (int stage = 0; stage < STAGES; stage++) {
                Mesh mesh = new Mesh(stage);
                build(kind, mesh);
                String path = "vfx/" + name + "_" + stage;
                files.put("assets/" + NAMESPACE + "/models/" + path + ".json", json(mesh.model()));
                JsonObject model = new JsonObject();
                model.addProperty("type", "minecraft:model");
                model.addProperty("model", NAMESPACE + ":" + path);
                JsonArray tints = new JsonArray();
                JsonObject tint = new JsonObject();
                tint.addProperty("type", "minecraft:custom_model_data");
                tint.addProperty("index", 0);
                tint.addProperty("default", 0xFFFFFF);
                tints.add(tint);
                model.add("tints", tints);
                stages[stage] = model;
            }
            JsonObject dispatch = new JsonObject();
            dispatch.addProperty("type", "minecraft:range_dispatch");
            dispatch.addProperty("property", "minecraft:custom_model_data");
            dispatch.addProperty("index", 0);
            JsonArray entries = new JsonArray();
            for (int stage = 1; stage < STAGES; stage++) {
                JsonObject entry = new JsonObject();
                entry.addProperty("threshold", stage);
                entry.add("model", stages[stage]);
                entries.add(entry);
            }
            dispatch.add("entries", entries);
            dispatch.add("fallback", stages[0]);
            JsonObject definition = new JsonObject();
            definition.add("model", dispatch);
            files.put("assets/" + NAMESPACE + "/items/vfx/" + name + ".json", json(definition));
        }
        return files;
    }

    // ------------------------------------------------------------------ geometry

    private static void build(VfxKind kind, Mesh m) {
        switch (kind) {
            case SLASH -> {
                // A crescent lying flat: its middle at the origin, its tips sweeping back toward -Z.
                int segments = 26;
                double span = Math.toRadians(72);
                for (int i = 0; i < segments; i++) {
                    double t0 = -span + 2 * span * i / segments;
                    double t1 = -span + 2 * span * (i + 1) / segments;
                    double t = (t0 + t1) / 2;
                    Vector3f a = crescent(t0);
                    Vector3f b = crescent(t1);
                    Vector3f mid = new Vector3f(a).add(b).mul(.5f);
                    float length = a.distance(b) * 1.18f;
                    float taper = (float) Math.pow(Math.cos(t / span * Math.PI / 2), 1.3);
                    float yaw = (float) Math.atan2(b.x - a.x, b.z - a.z) - (float) Math.PI / 2;
                    Quaternionf rot = new Quaternionf().rotateY(yaw);
                    // Leading edge glow sits a little ahead of the white-hot core.
                    m.box(mid, new Vector3f(length, .02f, .05f + .3f * taper), rot, "strip", true);
                    m.box(new Vector3f(mid).add(0, .012f, 0).add(new Vector3f(0, 0, .04f * taper).rotate(rot)),
                            new Vector3f(length, .02f, .02f + .09f * taper), rot, "strip_core", false);
                }
            }
            case RING -> {
                // A flat annulus of radius one, brightest at its outer, leading edge.
                int segments = 40;
                for (int i = 0; i < segments; i++) {
                    double a = Math.PI * 2 * i / segments;
                    Quaternionf rot = new Quaternionf().rotateY((float) a);
                    float length = (float) (2 * Math.PI * .86 / segments * 1.12);
                    Vector3f at = new Vector3f(0, 0, .86f).rotate(rot);
                    m.box(at, new Vector3f(length, .02f, .28f), rot, "edge", true);
                    m.box(new Vector3f(0, .012f, .975f).rotate(rot), new Vector3f(length * 1.13f, .02f, .05f), rot,
                            "strip_core", false);
                }
            }
            case BEAM -> {
                // A cylinder one block long along +Z from the origin, radius half a block.
                for (int i = 0; i < 4; i++) {
                    Quaternionf rot = new Quaternionf().rotateZ((float) Math.toRadians(i * 22.5));
                    m.box(new Vector3f(0, 0, .5f), new Vector3f(.72f, .72f, 1f), rot, "shell", true);
                }
                for (int i = 0; i < 2; i++) {
                    Quaternionf rot = new Quaternionf().rotateZ((float) Math.toRadians(i * 45));
                    m.box(new Vector3f(0, 0, .5f), new Vector3f(.3f, .3f, 1.002f), rot, "solid", false);
                }
            }
            case PILLAR -> {
                // A column one block tall from the origin up, fading out toward its top.
                for (int i = 0; i < 4; i++) {
                    Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(i * 22.5));
                    m.box(new Vector3f(0, .5f, 0), new Vector3f(.72f, 1f, .72f), rot, "fade_up", true);
                }
                for (int i = 0; i < 2; i++) {
                    Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(i * 45 + 11));
                    m.box(new Vector3f(0, .5f, 0), new Vector3f(.28f, 1.002f, .28f), rot, "fade_up", false);
                }
            }
            case AURA -> {
                for (int i = 0; i < 4; i++) {
                    Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(i * 22.5 + 7));
                    m.box(new Vector3f(0, .5f, 0), new Vector3f(.72f, 1f, .72f), rot, "fade_up", true);
                }
            }
            case ORB -> {
                // Overlapping turned cubes round off into a glowing ball one block across.
                Quaternionf[] turns = {new Quaternionf(), new Quaternionf().rotateY((float) Math.PI / 4),
                        new Quaternionf().rotateX((float) Math.PI / 4), new Quaternionf().rotateZ((float) Math.PI / 4)};
                for (Quaternionf turn : turns) {
                    m.box(new Vector3f(), new Vector3f(.74f), turn, "shell", true);
                }
                for (int i = 0; i < 3; i++) {
                    m.box(new Vector3f(), new Vector3f(.42f), new Quaternionf(turns[i]).rotateY(.3f), "solid", false);
                }
            }
            case FLARE -> {
                // A camera-facing burst of light: a tinted halo behind a white star.
                m.quad(new Vector3f(), 1f, 1f, "flare", true);
                m.quad(new Vector3f(0, 0, .01f), .55f, .55f, "flare_core", false);
            }
            case FLAME -> {
                // A tongue of fire standing on the origin, one block tall.
                m.quad(new Vector3f(0, .5f, 0), .62f, 1f, "flame", true);
                m.quad(new Vector3f(0, .38f, .01f), .3f, .7f, "flame_core", false);
            }
            case SPIRAL -> {
                // Two turns of ribbon climbing one block around a half-block radius.
                int segments = 36;
                for (int i = 0; i < segments; i++) {
                    double t = i / (double) segments;
                    double a = t * Math.PI * 4;
                    Vector3f at = new Vector3f((float) (Math.sin(a) * .5), (float) t, (float) (Math.cos(a) * .5));
                    Quaternionf rot = new Quaternionf().rotateY((float) a).rotateZ(-.32f);
                    float length = (float) (Math.PI * 2 * .5 * 2 / segments * 1.25);
                    m.box(at, new Vector3f(length, .14f, .02f), rot, "strip", true);
                    m.box(new Vector3f(at).mul(1.01f, 1, 1.01f), new Vector3f(length, .05f, .022f), rot, "strip_core",
                            false);
                }
            }
            case WING -> {
                // Two blocks across at scale one: too big for a model's bounds, so it is authored at half size.
                m.unit = .5f;
                // A wing of light reaching out to the right (+X): a curved arm of light along the top, long flight
                // feathers hanging from it and sweeping outward, and a row of short coverts over their roots.
                int feathers = 7;
                for (int i = 0; i < feathers; i++) {
                    float t = i / (feathers - 1f);
                    Vector3f root = new Vector3f(t * 1.05f, (float) Math.sin(t * Math.PI * .85) * .42f, 0);
                    double angle = Math.toRadians(-78 + t * 66);
                    float length = .5f + t * .55f;
                    Quaternionf rot = new Quaternionf().rotateZ((float) angle);
                    Vector3f along = new Vector3f(1, 0, 0).rotate(rot);
                    m.box(new Vector3f(root).add(new Vector3f(along).mul(length / 2)).add(0, 0, -i * .004f),
                            new Vector3f(length, .2f + t * .04f, .012f), rot, "feather", true);
                    Quaternionf cover = new Quaternionf().rotateZ((float) angle + .5f);
                    Vector3f coverAlong = new Vector3f(1, 0, 0).rotate(cover);
                    m.box(new Vector3f(root).add(new Vector3f(coverAlong).mul(.16f)).add(0, 0, .015f),
                            new Vector3f(.34f, .14f, .012f), cover, "feather", true);
                    if (i < feathers - 1) {
                        float t2 = (i + 1) / (feathers - 1f);
                        Vector3f next = new Vector3f(t2 * 1.05f, (float) Math.sin(t2 * Math.PI * .85) * .42f, 0);
                        Vector3f span = new Vector3f(next).sub(root);
                        Quaternionf arm = new Quaternionf().rotateZ((float) Math.atan2(span.y, span.x));
                        m.box(new Vector3f(root).add(next).mul(.5f).add(0, 0, .022f),
                                new Vector3f(span.length() * 1.15f, .07f, .014f), arm, "strip_core", false);
                    }
                }
            }
            case HORN -> {
                // A bone horn curving up and forward (+Z) from its root.
                Vector3f at = new Vector3f();
                double tilt = Math.toRadians(-10);
                int segments = 8;
                for (int i = 0; i < segments; i++) {
                    float step = .07f;
                    float width = .11f - i * .011f;
                    Quaternionf rot = new Quaternionf().rotateX((float) tilt);
                    Vector3f dir = new Vector3f(0, 1, 0).rotate(rot);
                    m.box(new Vector3f(at).add(new Vector3f(dir).mul(step / 2)), new Vector3f(width, step * 1.2f, width),
                            rot, "bone", false);
                    at.add(dir.mul(step));
                    tilt += Math.toRadians(9);
                }
            }
            case PLATE -> {
                // A curved armour plate, its glowing seams tinted.
                for (int i = -2; i <= 2; i++) {
                    Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(i * 14));
                    Vector3f at = new Vector3f(0, 0, .45f).rotate(rot).sub(0, 0, .45f);
                    m.box(at, new Vector3f(.16f, .62f, .04f), rot, "shell", true);
                    m.box(new Vector3f(at).add(new Vector3f(0, 0, .022f).rotate(rot)), new Vector3f(.03f, .58f, .01f),
                            rot, "strip_core", false);
                }
            }
            case CRACK -> m.flat(new Vector3f(), 1f, "crack");
        }
    }

    /** The crescent's centre line for an angle {@code t}, matching the particle arc in {@link Vfx#arc}. */
    private static Vector3f crescent(double t) {
        return new Vector3f((float) Math.sin(t), 0, (float) (-(1 - Math.cos(t)) * .6));
    }

    private static final class Mesh {
        private final int stage;
        /** Blocks per model block: shapes larger than a model's bounds are authored smaller and scaled up. */
        private float unit = 1;
        private final JsonArray elements = new JsonArray();
        private final Map<String, String> used = new LinkedHashMap<>();

        Mesh(int stage) {
            this.stage = stage;
        }

        private String texture(String name) {
            String key = String.valueOf(used.size());
            for (Map.Entry<String, String> entry : used.entrySet()) {
                if (entry.getValue().equals(name)) {
                    return entry.getKey();
                }
            }
            used.put(key, name);
            return key;
        }

        /** A box centred on {@code center} (blocks), every face showing the whole texture. */
        void box(Vector3f center, Vector3f size, Quaternionf rotation, String texture, boolean tinted) {
            String key = texture(texture);
            Vector3f c = new Vector3f(center).mul(16 * unit).add(8, 8, 8);
            Vector3f half = new Vector3f(size).mul(8 * unit);
            for (float value : new float[] {c.x - half.x, c.y - half.y, c.z - half.z, c.x + half.x, c.y + half.y,
                    c.z + half.z}) {
                // Minecraft rejects any element outside -16..32 and shows the whole model as missing.
                if (value < -16 || value > 32) {
                    throw new IllegalStateException("Effect model element outside model bounds: " + value);
                }
            }
            JsonObject element = new JsonObject();
            element.add("from", vector(new Vector3f(c).sub(half)));
            element.add("to", vector(new Vector3f(c).add(half)));
            Vector3f euler = rotation.getEulerAnglesZYX(new Vector3f());
            if (euler.lengthSquared() > 1e-8f) {
                JsonObject rotate = new JsonObject();
                rotate.add("origin", vector(c));
                rotate.addProperty("x", round((float) Math.toDegrees(euler.x)));
                rotate.addProperty("y", round((float) Math.toDegrees(euler.y)));
                rotate.addProperty("z", round((float) Math.toDegrees(euler.z)));
                element.add("rotation", rotate);
            }
            // Energy glows at full brightness; the solid props (bone, scorch marks) take the world's light.
            boolean solidProp = texture.equals("bone") || texture.equals("crack");
            element.addProperty("shade", solidProp);
            if (!solidProp) {
                element.addProperty("light_emission", 15);
            }
            JsonObject faces = new JsonObject();
            for (String face : new String[] {"north", "south", "east", "west", "up", "down"}) {
                JsonObject entry = new JsonObject();
                JsonArray uv = new JsonArray();
                uv.add(0);
                uv.add(0);
                uv.add(16);
                uv.add(16);
                entry.add("uv", uv);
                entry.addProperty("texture", "#" + key);
                if (tinted) {
                    entry.addProperty("tintindex", 0);
                }
                faces.add(face, entry);
            }
            element.add("faces", faces);
            elements.add(element);
        }

        /** A paper-thin quad in the XY plane, seen from both sides. */
        void quad(Vector3f center, float width, float height, String texture, boolean tinted) {
            box(center, new Vector3f(width, height, 0), new Quaternionf(), texture, tinted);
        }

        /** A paper-thin quad lying in the XZ plane, for ground decals. */
        void flat(Vector3f center, float size, String texture) {
            box(center, new Vector3f(size, 0, size), new Quaternionf(), texture, false);
        }

        JsonObject model() {
            JsonObject textures = new JsonObject();
            for (Map.Entry<String, String> entry : used.entrySet()) {
                String name = entry.getValue();
                textures.addProperty(entry.getKey(), NAMESPACE + ":item/vfx/" + name + "_"
                        + (name.equals("bone") ? 0 : stage));
            }
            if (!used.isEmpty()) {
                textures.addProperty("particle", NAMESPACE + ":item/vfx/" + used.values().iterator().next() + "_0");
            }
            JsonObject model = new JsonObject();
            model.add("textures", textures);
            model.add("elements", elements);
            return model;
        }
    }

    // ------------------------------------------------------------------ textures

    /** A white strip whose opacity peaks along its middle (v = .5), falling off with the given width. */
    private static BufferedImage strip(float width) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            float v = (y + .5f) / 32 - .5f;
            float alpha = (float) Math.exp(-(v * v) / (2 * width * width));
            for (int x = 0; x < 32; x++) {
                float u = (x + .5f) / 32;
                float shimmer = .9f + .1f * (float) Math.sin(u * 19 + y * .7);
                image.setRGB(x, y, argb(alpha * shimmer, 1, 1, 1));
            }
        }
        return image;
    }

    /** Opacity rising toward v = 1, so a ring's outer, leading edge burns brightest. */
    private static BufferedImage edge() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            float v = (y + .5f) / 32;
            float alpha = (float) Math.pow(v, 2.4) * (v > .9f ? 1 - (v - .9f) * 4 : 1);
            for (int x = 0; x < 32; x++) {
                image.setRGB(x, y, argb(alpha, 1, 1, 1));
            }
        }
        return image;
    }

    /** A translucent energy skin with soft lengthwise streaks, optionally fading out toward its top (v = 0). */
    private static BufferedImage shell(boolean fadeUp) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(11);
        float[] streaks = new float[32];
        for (int x = 0; x < 32; x++) {
            streaks[x] = .75f + random.nextFloat() * .25f;
        }
        for (int y = 0; y < 32; y++) {
            float v = (y + .5f) / 32;
            for (int x = 0; x < 32; x++) {
                float alpha = .5f * streaks[x];
                if (fadeUp) {
                    alpha = (float) (alpha * 1.6 * Math.pow(v, 1.4));
                }
                image.setRGB(x, y, argb(alpha, 1, 1, 1));
            }
        }
        return image;
    }

    private static BufferedImage solid(float alpha) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                image.setRGB(x, y, argb(alpha, 1, 1, 1));
            }
        }
        return image;
    }

    /** A radial glow with a four-point star; the core version is smaller and sharper. */
    private static BufferedImage flare(boolean core) {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + .5f) / size * 2 - 1;
                float dy = (y + .5f) / size * 2 - 1;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float glow = (float) Math.exp(-r * r * (core ? 14 : 5));
                float rays = (float) (Math.exp(-Math.abs(dx) * 30) * Math.exp(-Math.abs(dy) * 2.5)
                        + Math.exp(-Math.abs(dy) * 30) * Math.exp(-Math.abs(dx) * 2.5));
                float diagonal = (float) (Math.exp(-Math.abs(dx - dy) * 40) + Math.exp(-Math.abs(dx + dy) * 40))
                        * (float) Math.exp(-r * 3.5) * .5f;
                float alpha = Math.min(1, glow + (core ? rays * .9f : rays * .45f + diagonal));
                image.setRGB(x, y, argb(r > 1 ? 0 : alpha * (1 - r * r * .3f), 1, 1, 1));
            }
        }
        return image;
    }

    /** A flickering tongue of flame: wide at the base, licking up to a point. */
    private static BufferedImage flame(boolean core) {
        int w = 32;
        int h = 64;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            float v = (y + .5f) / h; // 0 at the tip, 1 at the base
            for (int x = 0; x < w; x++) {
                float u = (x + .5f) / w * 2 - 1;
                float wobble = (float) (Math.sin(v * 13 + u * 2) * .08 + Math.sin(v * 29) * .04) * (1 - v);
                float half = (float) (Math.pow(v, .7) * (core ? .55 : .9)) * (1 - (float) Math.pow(v, 6) * .3f);
                float d = Math.abs(u + wobble) / Math.max(.01f, half);
                float alpha = d > 1 ? 0 : (float) Math.pow(1 - d, core ? 1.2 : .8) * Math.min(1, v * 3);
                image.setRGB(x, y, argb(alpha, 1, 1, 1));
            }
        }
        return image;
    }

    /** A long feather of light: a bright shaft and translucent vanes that fray at the tip. */
    private static BufferedImage feather() {
        BufferedImage image = new BufferedImage(64, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            float v = Math.abs((y + .5f) / 16 * 2 - 1);
            for (int x = 0; x < 64; x++) {
                float u = (x + .5f) / 64;
                float barbs = .7f + .3f * (float) Math.sin(u * 90 + v * 6);
                float width = 1 - (float) Math.pow(u, 3) * .8f;
                float alpha = v > width ? 0 : (.35f + .65f * (float) Math.exp(-v * v * 30)) * barbs;
                image.setRGB(x, y, argb(alpha, 1, 1, 1));
            }
        }
        return image;
    }

    /** Dark radiating fissures for scorched ground, transparent between the cracks. */
    private static BufferedImage crack() {
        int size = 128;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);
        float[][] alpha = new float[size][size];
        for (int arm = 0; arm < 11; arm++) {
            double angle = arm * Math.PI * 2 / 11 + random.nextDouble() * .4;
            double x = size / 2.0;
            double y = size / 2.0;
            double length = size * (.3 + random.nextDouble() * .2);
            for (double d = 0; d < length; d += .5) {
                angle += (random.nextDouble() - .5) * .25;
                x += Math.cos(angle) * .5;
                y += Math.sin(angle) * .5;
                float width = (float) (2.2 * (1 - d / length)) + .6f;
                for (int oy = -3; oy <= 3; oy++) {
                    for (int ox = -3; ox <= 3; ox++) {
                        int px = (int) x + ox;
                        int py = (int) y + oy;
                        if (px >= 0 && py >= 0 && px < size && py < size) {
                            float dist = (float) Math.hypot(px - x, py - y);
                            alpha[py][px] = Math.max(alpha[py][px], Math.max(0, 1 - dist / width));
                        }
                    }
                }
            }
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float r = (float) Math.hypot(x - size / 2.0, y - size / 2.0) / (size / 2f);
                // A soft scorch fading out to the rim, with the fissures darker inside it.
                float scorch = Math.max(0, .42f - r * .5f) * (r > 1 ? 0 : 1);
                float crack = (float) Math.pow(alpha[y][x], 1.5) * Math.max(0, 1 - r * .6f);
                float a = Math.min(.8f, crack * .7f + scorch);
                float shade = .1f + .08f * (1 - crack);
                image.setRGB(x, y, argb(a, shade, shade * .85f, shade * .9f));
            }
        }
        return image;
    }

    private static BufferedImage bone() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(3);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                float n = .92f + random.nextFloat() * .08f - (y > 13 ? .08f : 0);
                image.setRGB(x, y, argb(1, .93f * n, .9f * n, .82f * n));
            }
        }
        return image;
    }

    private static BufferedImage fade(BufferedImage source, float factor) {
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                int alpha = Math.round((pixel >>> 24) * factor);
                image.setRGB(x, y, alpha << 24 | pixel & 0xFFFFFF);
            }
        }
        return image;
    }

    private static int argb(float a, float r, float g, float b) {
        return channel(a) << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float value) {
        return Math.clamp(Math.round(value * 255), 0, 255);
    }

    private static JsonArray vector(Vector3f value) {
        JsonArray array = new JsonArray();
        array.add(round(value.x));
        array.add(round(value.y));
        array.add(round(value.z));
        return array;
    }

    private static float round(float value) {
        return Math.round(value * 10000f) / 10000f;
    }

    private static byte[] json(JsonObject object) {
        return object.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
