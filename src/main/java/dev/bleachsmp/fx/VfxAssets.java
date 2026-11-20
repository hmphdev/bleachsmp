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
    /** Textures for solid props, which take the world's light instead of glowing. */
    private static final java.util.Set<String> PROPS = java.util.Set.of("bone", "crack", "stone", "armor", "mask");

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
        textures.put("crystal", crystal());
        textures.put("stone", stone());
        textures.put("armor", armor());
        textures.put("puff", puff());
        textures.put("water", water());
        textures.put("mask", mask());
        for (Map.Entry<String, Shape> glyph : GLYPHS.entrySet()) {
            textures.put(glyph.getKey(), glyph(glyph.getValue(), false));
            textures.put(glyph.getKey() + "_core", glyph(glyph.getValue(), true));
        }
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
                // A few more turns between those round off the corners seen on a big orb.
                for (int i = 0; i < 4; i++) {
                    m.box(new Vector3f(), new Vector3f(.72f), new Quaternionf().rotateY((float) (Math.PI / 8 + i * Math.PI / 4))
                            .rotateX((float) Math.PI / 5).rotateZ((float) Math.PI / 7), "edge", true);
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
            case SHARD -> {
                // A six-sided crystal: three turned prisms for the body, a tapering tip, and a white-hot heart.
                for (int k = 0; k < 3; k++) {
                    Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(k * 60 + 15));
                    m.box(new Vector3f(0, .33f, 0), new Vector3f(.2f, .66f, .2f), rot, "crystal", true);
                    float[] widths = {.16f, .11f, .06f, .025f};
                    for (int i = 0; i < widths.length; i++) {
                        m.box(new Vector3f(0, .72f + i * .075f, 0), new Vector3f(widths[i], .09f, widths[i]), rot,
                                "crystal", true);
                    }
                }
                m.box(new Vector3f(0, .45f, 0), new Vector3f(.045f, .8f, .045f), new Quaternionf().rotateY(.4f),
                        "solid", false);
            }
            case ROCK -> {
                m.box(new Vector3f(), new Vector3f(.46f, .4f, .44f), new Quaternionf().rotateXYZ(.2f, .5f, .1f),
                        "stone", true);
                m.box(new Vector3f(.08f, .06f, -.05f), new Vector3f(.36f, .38f, .34f),
                        new Quaternionf().rotateXYZ(-.4f, .1f, .5f), "stone", true);
                m.box(new Vector3f(-.07f, -.04f, .07f), new Vector3f(.32f, .3f, .36f),
                        new Quaternionf().rotateXYZ(.6f, -.7f, -.2f), "stone", true);
            }
            case SPIKE -> {
                Vector3f at = new Vector3f();
                int segments = 7;
                for (int i = 0; i < segments; i++) {
                    float t = i / (float) segments;
                    float width = .42f * (1 - t) + .04f;
                    float step = 1f / segments;
                    Quaternionf rot = new Quaternionf().rotateY(i * .5f).rotateZ((float) Math.sin(i * 1.7) * .06f);
                    m.box(new Vector3f(at).add(0, step / 2, 0), new Vector3f(width, step * 1.25f, width * .9f), rot,
                            "stone", true);
                    at.add((float) Math.sin(i * 2.1) * .015f, step, (float) Math.cos(i * 1.3) * .015f);
                }
            }
            case ZAP -> {
                Random random = new Random(5);
                Vector3f previous = new Vector3f();
                int segments = 7;
                for (int i = 1; i <= segments; i++) {
                    Vector3f next = i == segments ? new Vector3f(0, 0, 1)
                            : new Vector3f((random.nextFloat() - .5f) * .26f, (random.nextFloat() - .5f) * .26f,
                                    i / (float) segments);
                    Vector3f span = new Vector3f(next).sub(previous);
                    Quaternionf rot = aim(span);
                    Vector3f mid = new Vector3f(previous).add(next).mul(.5f);
                    m.box(mid, new Vector3f(.1f, .1f, span.length() + .03f), rot, "shell", true);
                    m.box(mid, new Vector3f(.028f, .028f, span.length() + .02f), rot, "solid", false);
                    if (i == 3 || i == 5) {
                        // A short fork off the main channel.
                        Vector3f fork = new Vector3f(next).add((random.nextFloat() - .5f) * .3f, (random.nextFloat() - .5f) * .3f,
                                .12f);
                        Vector3f forkSpan = new Vector3f(fork).sub(next);
                        m.box(new Vector3f(next).add(fork).mul(.5f), new Vector3f(.05f, .05f, forkSpan.length()),
                                aim(forkSpan), "shell", true);
                    }
                    previous = next;
                }
            }
            case GUST -> {
                band(m, .5f, 60, -10, .04f);
                band(m, .72f, 50, 12, .05f);
                band(m, .95f, 44, -4, .035f);
            }
            case SMOKE -> {
                Quaternionf[] turns = {new Quaternionf().rotateXYZ(.3f, .2f, .1f), new Quaternionf().rotateXYZ(-.4f, .9f, .3f),
                        new Quaternionf().rotateXYZ(.8f, -.3f, .6f), new Quaternionf().rotateXYZ(.1f, .5f, -.7f)};
                Vector3f[] offsets = {new Vector3f(), new Vector3f(.14f, .1f, -.06f), new Vector3f(-.12f, .05f, .1f),
                        new Vector3f(.02f, -.1f, .12f)};
                float[] sizes = {.68f, .5f, .52f, .44f};
                for (int i = 0; i < 4; i++) {
                    m.box(offsets[i], new Vector3f(sizes[i]), turns[i], "puff", true);
                }
            }
            case WAVE -> {
                float[][] crest = {{-.55f, 0}, {-.38f, .1f}, {-.22f, .27f}, {-.1f, .48f}, {.04f, .64f}, {.18f, .7f},
                        {.3f, .64f}, {.37f, .52f}, {.34f, .4f}};
                for (int i = 0; i < crest.length - 1; i++) {
                    float dz = crest[i + 1][0] - crest[i][0];
                    float dy = crest[i + 1][1] - crest[i][1];
                    float length = (float) Math.hypot(dz, dy) * 1.12f;
                    Quaternionf rot = new Quaternionf().rotateX((float) -Math.atan2(dy, dz));
                    Vector3f mid = new Vector3f(0, (crest[i][1] + crest[i + 1][1]) / 2, (crest[i][0] + crest[i + 1][0]) / 2);
                    m.box(mid, new Vector3f(1f, .04f, length), rot, "water", true);
                    if (i >= 3) {
                        m.box(new Vector3f(mid).add(0, .025f, 0), new Vector3f(.98f, .012f, length * .9f), rot, "puff", false);
                    }
                }
            }
            case DROP -> {
                m.box(new Vector3f(0, .07f, 0), new Vector3f(.14f), new Quaternionf().rotateY(.785f), "shell", true);
                m.box(new Vector3f(0, .07f, 0), new Vector3f(.14f), new Quaternionf().rotateX(.785f), "shell", true);
                m.box(new Vector3f(0, .17f, 0), new Vector3f(.07f, .12f, .07f), new Quaternionf().rotateY(.785f), "shell",
                        true);
                m.box(new Vector3f(0, .08f, 0), new Vector3f(.06f), new Quaternionf(), "solid", false);
            }
            case PETAL -> glyphQuad(m, "petal", .3f);
            case FEATHER -> m.quad(new Vector3f(), 1f, .2f, "feather", true);
            case BLADE -> {
                m.box(new Vector3f(0, .55f, 0), new Vector3f(.075f, .74f, .014f), new Quaternionf(), "solid", true);
                m.box(new Vector3f(0, .93f, 0), new Vector3f(.053f, .053f, .013f), new Quaternionf().rotateZ(.785f),
                        "solid", true);
                m.box(new Vector3f(.026f, .56f, 0), new Vector3f(.016f, .74f, .02f), new Quaternionf(), "solid", false);
                m.box(new Vector3f(0, .55f, 0), new Vector3f(.16f, .8f, .05f), new Quaternionf(), "shell", true);
                m.box(new Vector3f(0, .16f, 0), new Vector3f(.2f, .03f, .06f), new Quaternionf(), "solid", true);
                m.box(new Vector3f(0, .07f, 0), new Vector3f(.04f, .16f, .04f), new Quaternionf(), "shell", true);
            }
            case ARROW -> {
                m.box(new Vector3f(0, 0, -.06f), new Vector3f(.035f, .035f, .82f), new Quaternionf(), "solid", true);
                m.box(new Vector3f(0, 0, -.04f), new Vector3f(.014f, .014f, .84f), new Quaternionf(), "solid", false);
                m.box(new Vector3f(0, 0, -.04f), new Vector3f(.12f, .12f, .86f), new Quaternionf(), "shell", true);
                chevron(m, new Vector3f(0, 0, .5f), .22f, .55f, .03f, "solid", true);
                chevron(m, new Vector3f(0, 0, -.28f), .2f, .7f, .05f, "shell", true);
                chevron(m, new Vector3f(0, 0, -.4f), .2f, .7f, .05f, "shell", true);
            }
            case SPEAR -> {
                m.box(new Vector3f(0, 0, -.12f), new Vector3f(.035f, .035f, .76f), new Quaternionf(), "solid", true);
                m.box(new Vector3f(0, 0, -.1f), new Vector3f(.1f, .1f, .8f), new Quaternionf(), "shell", true);
                float[] widths = {.05f, .12f, .15f, .12f, .08f, .035f};
                for (int i = 0; i < widths.length; i++) {
                    float z = .27f + i * .045f;
                    m.box(new Vector3f(0, 0, z), new Vector3f(widths[i], .018f, .05f), new Quaternionf(), "solid", true);
                    m.box(new Vector3f(0, 0, z), new Vector3f(.018f, widths[i], .05f), new Quaternionf(), "solid", true);
                }
                m.box(new Vector3f(0, 0, .38f), new Vector3f(.01f, .01f, .26f), new Quaternionf(), "solid", false);
            }
            case CHAIN -> {
                for (int i = 0; i < 5; i++) {
                    float z = -.4f + i * .2f;
                    Quaternionf rot = new Quaternionf().rotateZ(i % 2 == 0 ? 0 : (float) Math.PI / 2);
                    for (int side = -1; side <= 1; side += 2) {
                        m.box(new Vector3f(side * .05f, 0, 0).rotate(rot).add(0, 0, z), new Vector3f(.025f, .025f, .24f),
                                rot, "solid", true);
                        m.box(new Vector3f(0, 0, side * .11f).rotate(rot).add(0, 0, z), new Vector3f(.125f, .025f, .025f),
                                rot, "solid", true);
                    }
                }
            }
            case FANG -> {
                m.box(new Vector3f(0, 0, 0), new Vector3f(.2f, .14f, .3f), new Quaternionf().rotateX(.05f), "bone", false);
                m.box(new Vector3f(0, .05f, .02f), new Vector3f(.16f, .08f, .26f), new Quaternionf().rotateX(-.1f), "bone",
                        false);
                Vector3f at = new Vector3f(.1f, 0, .08f);
                double angle = .9;
                for (int i = 0; i < 4; i++) {
                    float width = .07f - i * .014f;
                    Quaternionf rot = new Quaternionf().rotateY((float) angle);
                    Vector3f dir = new Vector3f(0, 0, 1).rotate(rot);
                    m.box(new Vector3f(at).add(new Vector3f(dir).mul(.04f)), new Vector3f(width, width, .1f), rot, "bone",
                            false);
                    at.add(dir.mul(.08f));
                    angle -= .35;
                }
            }
            case CLAW -> {
                for (int mark = -1; mark <= 1; mark++) {
                    int segments = 8;
                    for (int i = 0; i < segments; i++) {
                        float t0 = i / (float) segments;
                        float t1 = (i + 1) / (float) segments;
                        Vector3f a = clawPoint(mark, t0);
                        Vector3f b = clawPoint(mark, t1);
                        Vector3f span = new Vector3f(b).sub(a);
                        float taper = (float) Math.sin((t0 + t1) / 2 * Math.PI);
                        Quaternionf rot = new Quaternionf().rotateZ((float) Math.atan2(span.y, span.x));
                        Vector3f mid = new Vector3f(a).add(b).mul(.5f);
                        m.box(mid, new Vector3f(span.length() * 1.15f, .03f + .09f * taper, .01f), rot, "strip", true);
                        m.box(new Vector3f(mid).add(0, 0, .008f), new Vector3f(span.length() * 1.15f, .01f + .03f * taper,
                                .01f), rot, "strip_core", false);
                    }
                }
            }
            case HEAD -> {
                m.unit = .5f;
                m.box(new Vector3f(0, .25f, -.35f), new Vector3f(.9f, .72f, .85f), new Quaternionf(), "shell", true);
                m.box(new Vector3f(0, .32f, .45f), new Vector3f(.62f, .3f, 1.05f), new Quaternionf().rotateX(.28f), "shell",
                        true);
                m.box(new Vector3f(0, -.18f, .38f), new Vector3f(.56f, .2f, .95f), new Quaternionf().rotateX(-.36f),
                        "shell", true);
                for (int side = -1; side <= 1; side += 2) {
                    for (int tooth = 0; tooth < 4; tooth++) {
                        float z = .2f + tooth * .2f;
                        m.box(new Vector3f(side * .24f, .14f + tooth * .055f, z), new Vector3f(.05f, .16f, .05f),
                                new Quaternionf().rotateX(.28f), "solid", false);
                        m.box(new Vector3f(side * .22f, -.03f - tooth * .07f, z - .03f), new Vector3f(.05f, .14f, .05f),
                                new Quaternionf().rotateX(-.36f), "solid", false);
                    }
                    m.box(new Vector3f(side * .3f, .48f, .08f), new Vector3f(.16f, .07f, .06f),
                            new Quaternionf().rotateZ(side * -.3f), "solid", false);
                    m.box(new Vector3f(side * .34f, .82f, -.75f), new Vector3f(.12f, .12f, .95f),
                            new Quaternionf().rotateY(side * .3f).rotateX(.55f), "shell", true);
                    m.box(new Vector3f(side * .34f, .82f, -.75f), new Vector3f(.04f, .04f, .9f),
                            new Quaternionf().rotateY(side * .3f).rotateX(.55f), "solid", true);
                    m.box(new Vector3f(side * .5f, .1f, -.3f), new Vector3f(.08f, .4f, .5f),
                            new Quaternionf().rotateY(side * .4f), "shell", true);
                }
                for (int ridge = 0; ridge < 4; ridge++) {
                    m.box(new Vector3f(0, .66f, -.15f - ridge * .22f), new Vector3f(.06f, .18f - ridge * .02f, .14f),
                            new Quaternionf().rotateX(.5f), "solid", true);
                }
            }
            case FIST -> {
                m.unit = .5f;
                m.box(new Vector3f(0, 0, 0), new Vector3f(1.1f, .9f, 1f), new Quaternionf(), "armor", true);
                for (int i = 0; i < 4; i++) {
                    m.box(new Vector3f(-.4f + i * .27f, -.48f, .12f), new Vector3f(.24f, .22f, .62f), new Quaternionf(),
                            "armor", true);
                }
                m.box(new Vector3f(.62f, .05f, .3f), new Vector3f(.24f, .5f, .3f), new Quaternionf().rotateZ(-.2f), "armor",
                        true);
                m.box(new Vector3f(0, .6f, 0), new Vector3f(1.02f, .32f, .96f), new Quaternionf(), "armor", true);
                m.box(new Vector3f(0, 1.85f, 0), new Vector3f(.84f, 2.2f, .8f), new Quaternionf(), "armor", true);
                for (int band = 0; band < 3; band++) {
                    float y = 1.05f + band * .75f;
                    m.box(new Vector3f(0, y, 0), new Vector3f(.96f, .14f, .92f), new Quaternionf(), "armor", true);
                    m.box(new Vector3f(0, y + .1f, .465f), new Vector3f(.8f, .025f, .01f), new Quaternionf(), "solid", true);
                }
                m.box(new Vector3f(0, -.1f, .505f), new Vector3f(.9f, .025f, .01f), new Quaternionf(), "solid", true);
                m.box(new Vector3f(0, .78f, .49f), new Vector3f(.025f, .3f, .01f), new Quaternionf(), "solid", true);
            }
            case GIANT -> {
                m.unit = .25f;
                m.box(new Vector3f(0, .5f, 0), new Vector3f(2.4f, 1f, 1.6f), new Quaternionf(), "armor", true);
                m.box(new Vector3f(0, 2.1f, 0), new Vector3f(3.4f, 2.2f, 2f), new Quaternionf(), "armor", true);
                m.box(new Vector3f(0, 2.1f, 1.01f), new Vector3f(2.6f, .06f, .02f), new Quaternionf(), "solid", true);
                m.box(new Vector3f(0, 1.5f, 1.01f), new Vector3f(2.2f, .06f, .02f), new Quaternionf(), "solid", true);
                for (int side = -1; side <= 1; side += 2) {
                    m.box(new Vector3f(side * 2.25f, 3f, 0), new Vector3f(1.6f, .9f, 2.1f),
                            new Quaternionf().rotateZ(side * -.28f), "armor", true);
                    m.box(new Vector3f(side * 2.35f, 2.5f, 0), new Vector3f(1.4f, .5f, 1.9f),
                            new Quaternionf().rotateZ(side * -.38f), "armor", true);
                    m.box(new Vector3f(side * 2.2f, 1.3f, .1f), new Vector3f(.95f, 2.4f, .95f), new Quaternionf(), "armor",
                            true);
                    m.box(new Vector3f(side * .55f, 5.1f, .55f), new Vector3f(.16f, 1.5f, .16f),
                            new Quaternionf().rotateZ(side * -.45f), "solid", true);
                }
                m.box(new Vector3f(0, 3.95f, 0), new Vector3f(1.4f, 1.4f, 1.45f), new Quaternionf(), "armor", true);
                m.box(new Vector3f(0, 4.75f, -.1f), new Vector3f(.22f, .5f, 1.4f), new Quaternionf(), "armor", true);
                m.box(new Vector3f(0, 4.0f, .74f), new Vector3f(1f, .14f, .04f), new Quaternionf(), "solid", true);
                m.box(new Vector3f(0, 3.5f, .76f), new Vector3f(1.1f, .5f, .06f), new Quaternionf(), "armor", true);
            }
            case MASK -> {
                m.box(new Vector3f(0, 0, 0), new Vector3f(.5f, .58f, .02f), new Quaternionf(), "mask", false);
                m.box(new Vector3f(0, -.02f, -.06f), new Vector3f(.46f, .5f, .1f), new Quaternionf(), "bone", false);
                m.box(new Vector3f(0, .26f, -.02f), new Vector3f(.42f, .08f, .08f), new Quaternionf(), "bone", false);
                for (int side = -1; side <= 1; side += 2) {
                    m.box(new Vector3f(side * .13f, -.02f, .015f), new Vector3f(.03f, .3f, .01f),
                            new Quaternionf().rotateZ(side * .1f), "solid", true);
                    m.box(new Vector3f(side * .2f, .3f, -.04f), new Vector3f(.06f, .14f, .06f),
                            new Quaternionf().rotateZ(side * -.6f), "bone", false);
                }
            }
            case TENTACLE -> {
                Vector3f at = new Vector3f();
                double tilt = 0;
                int segments = 12;
                for (int i = 0; i < segments; i++) {
                    float step = .085f;
                    float width = .15f * (1 - i / (float) segments) + .02f;
                    Quaternionf rot = new Quaternionf().rotateX((float) tilt);
                    Vector3f dir = new Vector3f(0, 1, 0).rotate(rot);
                    m.box(new Vector3f(at).add(new Vector3f(dir).mul(step / 2)), new Vector3f(width, step * 1.3f, width),
                            rot, "solid", true);
                    if (i % 3 == 1) {
                        m.box(new Vector3f(at).add(new Vector3f(dir).mul(step / 2)).add(0, 0, -width * .55f),
                                new Vector3f(width * .3f, step * .8f, width * .3f), rot, "puff", false);
                    }
                    at.add(dir.mul(step));
                    tilt += Math.toRadians(5 + i * 1.6);
                }
            }
            case HEART, NOTE, EYE, STAR, BUTTERFLY, INVADER, CARD, CLOCK, TRIANGLE, HEX, PAGE ->
                    glyphQuad(m, kind.name().toLowerCase(Locale.ROOT), 1f);
            case SIGIL -> {
                m.box(new Vector3f(), new Vector3f(1f, 0, 1f), new Quaternionf(), "sigil", true);
                m.box(new Vector3f(0, .01f, 0), new Vector3f(1f, 0, 1f), new Quaternionf(), "sigil_core", false);
            }
        }
    }

    /** A glowing tinted glyph with an untinted white heart just in front of it. */
    private static void glyphQuad(Mesh m, String name, float size) {
        m.quad(new Vector3f(), size, size, name, true);
        m.quad(new Vector3f(0, 0, .01f), size, size, name + "_core", false);
    }

    /** Rotation turning +Z toward {@code direction}. */
    private static Quaternionf aim(Vector3f direction) {
        Vector3f dir = new Vector3f(direction).normalize();
        float yaw = (float) Math.atan2(dir.x, dir.z);
        float pitch = (float) -Math.asin(Math.max(-1, Math.min(1, dir.y)));
        return new Quaternionf().rotateY(yaw).rotateX(pitch);
    }

    /** Four arms meeting at {@code tip}, swept back at {@code angle}: an arrowhead or fletching. */
    private static void chevron(Mesh m, Vector3f tip, float length, float angle, float width, String texture,
            boolean tinted) {
        for (int side = -1; side <= 1; side += 2) {
            Vector3f flat = new Vector3f(side * (float) Math.sin(angle), 0, (float) Math.cos(angle));
            m.box(new Vector3f(tip).sub(new Vector3f(flat).mul(length / 2)), new Vector3f(width, width * .6f, length),
                    aim(flat), texture, tinted);
            Vector3f up = new Vector3f(0, side * (float) Math.sin(angle), (float) Math.cos(angle));
            m.box(new Vector3f(tip).sub(new Vector3f(up).mul(length / 2)), new Vector3f(width * .6f, width, length),
                    aim(up), texture, tinted);
        }
    }

    /** One streak of a gust: a thin arc of {@code radius}, spanning {@code degrees}, turned {@code offset}. */
    private static void band(Mesh m, float radius, double degrees, double offset, float width) {
        int segments = 14;
        double span = Math.toRadians(degrees);
        for (int i = 0; i < segments; i++) {
            double t0 = Math.toRadians(offset) - span + 2 * span * i / segments;
            double t1 = Math.toRadians(offset) - span + 2 * span * (i + 1) / segments;
            Vector3f a = new Vector3f((float) Math.sin(t0) * radius, 0, (float) (-(1 - Math.cos(t0)) * radius * .7));
            Vector3f b = new Vector3f((float) Math.sin(t1) * radius, 0, (float) (-(1 - Math.cos(t1)) * radius * .7));
            float taper = (float) Math.sin((i + .5) / segments * Math.PI);
            Quaternionf rot = new Quaternionf().rotateY((float) Math.atan2(b.x - a.x, b.z - a.z) - (float) Math.PI / 2);
            Vector3f mid = new Vector3f(a).add(b).mul(.5f).add(0, 0, radius * .4f);
            m.box(mid, new Vector3f(a.distance(b) * 1.15f, .015f, width * taper + .01f), rot, "strip", true);
            m.box(new Vector3f(mid).add(0, .01f, 0), new Vector3f(a.distance(b) * 1.15f, .015f, width * .3f * taper + .004f),
                    rot, "strip_core", false);
        }
    }

    private static Vector3f clawPoint(int mark, float t) {
        float x = mark * .22f + (t - .5f) * .55f;
        float y = (.5f - t) * .95f + (float) Math.sin(t * Math.PI) * .06f * mark;
        return new Vector3f(x + (float) Math.sin(t * Math.PI) * .07f, y, 0);
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
            boolean solidProp = PROPS.contains(texture);
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

    /** Faceted ice: bright edges and a soft sheen down each face. */
    private static BufferedImage crystal() {
        BufferedImage image = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            float v = (y + .5f) / 32;
            for (int x = 0; x < 16; x++) {
                float u = (x + .5f) / 16;
                float edge = (float) Math.pow(Math.abs(u * 2 - 1), 5);
                float sheen = (float) Math.exp(-Math.pow((u - .3f - v * .25f) * 7, 2)) * .5f;
                float alpha = Math.min(1, .38f + edge * .6f + sheen);
                float light = .8f + .2f * (1 - v) + sheen * .2f;
                image.setRGB(x, y, argb(alpha, light, light, light));
            }
        }
        return image;
    }

    /** Weathered rock: mottled grey, darker in its cracks, tinted by the earth's colour. */
    private static BufferedImage stone() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(19);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                float n = .62f + random.nextFloat() * .22f + (float) Math.sin(x * 1.3 + y * .7) * .05f;
                if ((x * 7 + y * 3) % 23 == 0 || (x + y * 5) % 29 == 0) {
                    n *= .55f;
                }
                image.setRGB(x, y, argb(1, n, n * .97f, n * .93f));
            }
        }
        return image;
    }

    /** Dark lacquered armour plate with a lighter rim and rivets. */
    private static BufferedImage armor() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(23);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean rim = x == 0 || y == 0 || x == 15 || y == 15;
                boolean rivet = (x == 2 || x == 13) && (y == 2 || y == 13);
                float n = .36f + random.nextFloat() * .05f + (1 - y / 16f) * .08f;
                if (rim) {
                    n = .62f;
                } else if (rivet) {
                    n = .8f;
                }
                image.setRGB(x, y, argb(1, n, n, n * 1.04f));
            }
        }
        return image;
    }

    /** A soft billow, thick in the middle and frayed at the rim. */
    private static BufferedImage puff() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(29);
        float[] lumps = new float[size * size];
        for (int i = 0; i < lumps.length; i++) {
            lumps[i] = random.nextFloat();
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + .5f) / size * 2 - 1;
                float dy = (y + .5f) / size * 2 - 1;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float lump = (lumps[y * size + x] + lumps[(y / 4) * size + x / 4] * 2) / 3;
                float alpha = Math.max(0, 1 - r * (1.05f + lump * .35f)) * .8f;
                image.setRGB(x, y, argb(alpha, .9f + lump * .1f, .9f + lump * .1f, .9f + lump * .1f));
            }
        }
        return image;
    }

    /** Moving water: translucent, with flowing streaks and foam toward its crest (v = 0). */
    private static BufferedImage water() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            float v = (y + .5f) / 32;
            for (int x = 0; x < 32; x++) {
                float u = (x + .5f) / 32;
                float streak = .5f + .5f * (float) Math.sin(u * 40 + Math.sin(v * 9) * 2);
                float alpha = .42f + streak * .2f;
                float light = .75f + streak * .25f;
                image.setRGB(x, y, argb(alpha, light, light, light));
            }
        }
        return image;
    }

    /** A Hollow's mask face: bone, with two eye holes and a seam for the teeth. */
    private static BufferedImage mask() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(31);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = (x + .5f) / size * 2 - 1;
                float v = 1 - (y + .5f) / size * 2;
                boolean eye = false;
                for (int side = -1; side <= 1; side += 2) {
                    float ex = (u - side * .38f) / .22f;
                    float ey = (v - .25f + Math.abs(u) * .15f) / .14f;
                    eye |= ex * ex + ey * ey < 1;
                }
                float face = (u * u) / .95f + (float) Math.pow(Math.max(0, -v), 2) / .9f;
                if (eye || face > 1) {
                    image.setRGB(x, y, 0);
                    continue;
                }
                float n = .9f + random.nextFloat() * .08f;
                boolean teeth = v < -.45f && v > -.62f && Math.abs(u) < .55f && (x % 4 == 0 || Math.abs(v + .53f) < .03f);
                if (teeth) {
                    n *= .45f;
                }
                image.setRGB(x, y, argb(1, .95f * n, .92f * n, .84f * n));
            }
        }
        return image;
    }

    /** A signed distance in glyph space ([-1, 1] each way, y up): negative inside the shape. */
    private interface Shape {
        float d(float x, float y);
    }

    private static final Map<String, Shape> GLYPHS = new LinkedHashMap<>();

    static {
        GLYPHS.put("petal", (x, y) -> {
            float body = (float) (Math.sqrt(x * x / .3 + (y + .05) * (y + .05) / .8) - 1) * .5f;
            float notch = .22f - (float) Math.hypot(x, y - .92);
            return Math.max(body, notch);
        });
        GLYPHS.put("heart", (x, y) -> {
            float hx = x * 1.25f;
            float hy = y * 1.25f + .2f;
            float f = (float) (Math.pow(hx * hx + hy * hy - 1, 3) - hx * hx * hy * hy * hy);
            return (float) Math.cbrt(f) * .45f;
        });
        GLYPHS.put("note", (x, y) -> {
            float head = (float) (Math.hypot((x + .28) / 1.35, (y + .52)) - .22);
            float stem = segment(x, y, -.02f, -.45f, -.02f, .7f) - .055f;
            float flag = segment(x, y, -.02f, .7f, .36f, .3f) - .065f;
            return Math.min(head, Math.min(stem, flag));
        });
        GLYPHS.put("eye", (x, y) -> {
            float almond = Math.max((float) Math.hypot(x, y + .62) - 1.05f, (float) Math.hypot(x, y - .62) - 1.05f);
            float lid = Math.abs(almond) - .05f;
            float pupil = (float) Math.hypot(x, y) - .27f;
            return Math.min(lid, pupil);
        });
        GLYPHS.put("star", (x, y) -> star(x, y, .95f, .4f));
        GLYPHS.put("butterfly", (x, y) -> {
            float ax = Math.abs(x);
            float upper = ellipse(ax - .42f, y - .28f, .4f, .38f, -.5f);
            float lower = ellipse(ax - .3f, y + .36f, .28f, .26f, .6f);
            float body = segment(x, y, 0, -.55f, 0, .45f) - .06f;
            float feelers = segment(ax, y, 0, .45f, .25f, .85f) - .025f;
            return Math.min(Math.min(upper, lower), Math.min(body, feelers));
        });
        GLYPHS.put("invader", (x, y) -> {
            String[] rows = {"...XXXXX...", ".XXXXXXXXX.", "XX..XXX..XX", "XXXXXXXXXXX", "..XX.X.XX..",
                    ".XX.....XX.", "X..X...X..X", ".X.......X."};
            int col = (int) Math.floor((x + 1) / 2 * 13) - 1;
            int row = (int) Math.floor((1 - y) / 2 * 13) - 2;
            boolean on = row >= 0 && row < rows.length && col >= 0 && col < 11 && rows[row].charAt(col) == 'X';
            return on ? -.1f : .1f;
        });
        GLYPHS.put("card", (x, y) -> {
            float box = Math.max(Math.abs(x) - .6f, Math.abs(y) - .9f);
            float border = Math.abs(box + .07f) - .03f;
            float pip = (Math.abs(x) + Math.abs(y) * .8f) - .28f;
            return Math.min(Math.min(border, pip), Math.max(box, -box - .1f) + .02f);
        });
        GLYPHS.put("clock", (x, y) -> {
            float r = (float) Math.hypot(x, y);
            float ring = Math.abs(r - .86f) - .05f;
            float ticks = 1;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                float c = (float) Math.cos(a);
                float s = (float) Math.sin(a);
                ticks = Math.min(ticks, segment(x, y, c * .62f, s * .62f, c * .74f, s * .74f) - (i % 3 == 0 ? .05f : .03f));
            }
            float hands = Math.min(segment(x, y, 0, 0, 0, .58f) - .04f, segment(x, y, 0, 0, .38f, -.18f) - .05f);
            return Math.min(ring, Math.min(ticks, hands));
        });
        GLYPHS.put("sigil", (x, y) -> {
            float r = (float) Math.hypot(x, y);
            float outer = Math.abs(r - .92f) - .035f;
            float inner = Math.abs(r - .8f) - .018f;
            float lines = 1;
            for (int i = 0; i < 5; i++) {
                double a = Math.PI / 2 + i * Math.PI * 2 / 5;
                double b = Math.PI / 2 + (i + 2) * Math.PI * 2 / 5;
                lines = Math.min(lines, segment(x, y, (float) Math.cos(a) * .8f, (float) Math.sin(a) * .8f,
                        (float) Math.cos(b) * .8f, (float) Math.sin(b) * .8f) - .03f);
            }
            return Math.min(Math.min(outer, inner), lines);
        });
        GLYPHS.put("triangle", (x, y) -> {
            float d = triangle(x, y);
            return Math.min(Math.abs(d) - .05f, d + .25f > 0 ? Math.max(d, -.3f) + .25f : 1);
        });
        GLYPHS.put("hex", (x, y) -> {
            float ax = Math.abs(x);
            float ay = Math.abs(y);
            float d = Math.max(ax * .866f + ay * .5f, ay) - .9f;
            float cells = (float) Math.abs(Math.sin(x * 9) * Math.sin(y * 9)) * .02f;
            return Math.min(Math.abs(d) - .045f, d < 0 ? .06f + cells - Math.abs(d) * .02f : 1);
        });
        GLYPHS.put("page", (x, y) -> {
            float box = Math.max(Math.abs(x) - .68f, Math.abs(y) - .9f);
            float lines = Math.abs(((y + 10) * 6.5f) % 1 - .5f) / 6.5f - .018f;
            return box < 0 && Math.abs(x) < .55f && y < .75f && y > -.8f ? Math.min(lines, -.005f) : box;
        });
    }

    private static float segment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float t = Math.clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy), 0f, 1f);
        return (float) Math.hypot(px - ax - dx * t, py - ay - dy * t);
    }

    private static float ellipse(float x, float y, float a, float b, float turn) {
        float c = (float) Math.cos(turn);
        float s = (float) Math.sin(turn);
        float rx = x * c - y * s;
        float ry = x * s + y * c;
        return (float) (Math.sqrt(rx * rx / (a * a) + ry * ry / (b * b)) - 1) * Math.min(a, b);
    }

    private static float star(float x, float y, float outer, float inner) {
        double angle = Math.atan2(x, y);
        double sector = Math.PI * 2 / 5;
        double local = Math.abs(((angle % sector) + sector) % sector - sector / 2) / (sector / 2);
        float edge = (float) (inner + (outer - inner) * Math.pow(local, 1.6));
        return (float) Math.hypot(x, y) - edge;
    }

    /** A point-down triangle's distance (negative inside). */
    private static float triangle(float x, float y) {
        float top = y - .7f;
        float left = (float) ((-x * .866 - y * .5) - .45);
        float right = (float) ((x * .866 - y * .5) - .45);
        return Math.max(top, Math.max(left, right));
    }

    /** A glyph's glow (soft halo, brighter rim) or its crisp white core. */
    private static BufferedImage glyph(Shape shape, boolean core) {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float gx = (x + .5f) / size * 2 - 1;
                float gy = 1 - (y + .5f) / size * 2;
                float d = shape.d(gx, gy);
                float alpha;
                if (core) {
                    alpha = Math.clamp((-d - .008f) / .022f, 0f, 1f) * .7f * (float) Math.exp(d * 5);
                } else if (d < 0) {
                    alpha = .7f + .3f * (float) Math.exp(d * 22);
                } else {
                    alpha = .55f * (float) Math.exp(-d * 16);
                }
                float edge = Math.max(Math.abs(gx), Math.abs(gy));
                if (edge > .94f) {
                    alpha *= Math.max(0, (1 - edge) / .06f);
                }
                image.setRGB(x, y, argb(alpha, 1, 1, 1));
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
