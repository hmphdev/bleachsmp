package dev.bleachsmp.hueco;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Builds the Hollows' resource pack from {@link HollowModel}: one item model per joint and one painted
 * texture per Hollow. Everything is generated from the same geometry the block-display fallback uses,
 * so the two never drift apart, and the output is deterministic so the pack's hash is stable.
 */
public final class HollowPackAssets {
    public static final String NAMESPACE = "bleachsmp";
    static final int PACK_FORMAT = 75;
    /** Texels per block of unmagnified model. */
    private static final int DENSITY = 32;
    private static final int PADDING = 1;
    // Item displays render their model turned half a turn about Y, so the models are written pre-turned.
    private static final Matrix3f FLIP = new Matrix3f().scaling(-1, 1, -1);

    private HollowPackAssets() {
    }

    static NamespacedKey modelKey(String type, HollowModel.Joint joint) {
        return new NamespacedKey(NAMESPACE, "hollow/" + type + "/" + joint.name().toLowerCase(Locale.ROOT));
    }

    /** Pack-relative path to file contents. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "BleachSMP: Hollow models and ability effects");
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("min_format", PACK_FORMAT);
        pack.addProperty("max_format", PACK_FORMAT);
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        files.put("pack.mcmeta", json(meta));
        for (String type : HollowModel.TYPES) {
            HollowModel model = HollowModel.forType(type);
            Atlas atlas = new Atlas(model);
            String texture = NAMESPACE + ":item/hollow/" + type;
            for (HollowModel.Part part : model.parts()) {
                String path = "hollow/" + type + "/" + part.joint().name().toLowerCase(Locale.ROOT);
                files.put("assets/" + NAMESPACE + "/models/" + path + ".json", json(itemModel(part, atlas, texture)));
                JsonObject reference = new JsonObject();
                reference.addProperty("type", "minecraft:model");
                reference.addProperty("model", NAMESPACE + ":" + path);
                JsonObject definition = new JsonObject();
                definition.add("model", reference);
                files.put("assets/" + NAMESPACE + "/items/" + path + ".json", json(definition));
            }
            files.put("assets/" + NAMESPACE + "/textures/item/hollow/" + type + ".png", png(atlas.paint()));
        }
        return files;
    }

    private static JsonObject itemModel(HollowModel.Part part, Atlas atlas, String texture) {
        JsonObject textures = new JsonObject();
        textures.addProperty("0", texture);
        textures.addProperty("particle", texture);
        JsonArray elements = new JsonArray();
        float unit = 16 / part.scale();
        for (HollowModel.Piece piece : part.pieces()) {
            Vector3f center = new Vector3f(piece.center()).sub(part.pivot()).mul(FLIP).mul(unit);
            Vector3f half = new Vector3f(piece.size()).mul(unit * .5f);
            JsonObject element = new JsonObject();
            element.add("from", vector(new Vector3f(center).sub(half).add(8, 8, 8)));
            element.add("to", vector(new Vector3f(center).add(half).add(8, 8, 8)));
            Quaternionf rotation = flipped(piece.rotation());
            Vector3f euler = rotation.getEulerAnglesZYX(new Vector3f());
            if (euler.lengthSquared() > 1e-8f) {
                JsonObject rotate = new JsonObject();
                rotate.add("origin", vector(new Vector3f(center).add(8, 8, 8)));
                rotate.addProperty("x", round((float) Math.toDegrees(euler.x)));
                rotate.addProperty("y", round((float) Math.toDegrees(euler.y)));
                rotate.addProperty("z", round((float) Math.toDegrees(euler.z)));
                element.add("rotation", rotate);
            }
            if (piece.luminous()) {
                element.addProperty("light_emission", 15);
            }
            JsonObject faces = new JsonObject();
            for (Face face : Face.values()) {
                Atlas.Region region = atlas.region(piece, face);
                JsonObject entry = new JsonObject();
                JsonArray uv = new JsonArray();
                uv.add(round(region.x() * 16f / atlas.width));
                uv.add(round(region.y() * 16f / atlas.height));
                uv.add(round((region.x() + region.w()) * 16f / atlas.width));
                uv.add(round((region.y() + region.h()) * 16f / atlas.height));
                entry.add("uv", uv);
                entry.addProperty("texture", "#0");
                faces.add(face.name().toLowerCase(Locale.ROOT), entry);
            }
            element.add("faces", faces);
            elements.add(element);
        }
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static Quaternionf flipped(Quaternionf rotation) {
        // Conjugating by a half turn about Y negates the X and Z components.
        return new Quaternionf(-rotation.x, rotation.y, -rotation.z, rotation.w);
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

    /**
     * Faces in model space before the piece's rotation, with how a texture's u (left to right) and v (top
     * to bottom) run across each, matching Minecraft's own face orientation.
     */
    enum Face {
        NORTH(new Vector3f(0, 0, -1)), SOUTH(new Vector3f(0, 0, 1)), EAST(new Vector3f(1, 0, 0)),
        WEST(new Vector3f(-1, 0, 0)), UP(new Vector3f(0, 1, 0)), DOWN(new Vector3f(0, -1, 0));

        final Vector3f normal;

        Face(Vector3f normal) {
            this.normal = normal;
        }

        /** Width and height of this face in blocks. */
        float[] extent(Vector3f size) {
            return switch (this) {
                case NORTH, SOUTH -> new float[] {size.x, size.y};
                case EAST, WEST -> new float[] {size.z, size.y};
                case UP, DOWN -> new float[] {size.x, size.z};
            };
        }

        /** The point at texture coordinates (u, v) in 0..1 on a box of {@code size} centred on the origin. */
        Vector3f point(Vector3f size, float u, float v) {
            float x = size.x * .5f;
            float y = size.y * .5f;
            float z = size.z * .5f;
            return switch (this) {
                case NORTH -> new Vector3f(x - u * size.x, y - v * size.y, -z);
                case SOUTH -> new Vector3f(-x + u * size.x, y - v * size.y, z);
                case EAST -> new Vector3f(x, y - v * size.y, z - u * size.z);
                case WEST -> new Vector3f(-x, y - v * size.y, -z + u * size.z);
                case UP -> new Vector3f(-x + u * size.x, y, -z + v * size.z);
                case DOWN -> new Vector3f(-x + u * size.x, -y, z - v * size.z);
            };
        }
    }

    /** Lays every face of every piece out on one texture and paints it. */
    private static final class Atlas {
        record Region(HollowModel.Piece piece, Face face, int x, int y, int w, int h) {
        }

        private final HollowModel model;
        private final Map<HollowModel.Piece, Region[]> regions = new LinkedHashMap<>();
        private final int width;
        private final int height;

        Atlas(HollowModel model) {
            this.model = model;
            List<Region> wanted = new ArrayList<>();
            long area = 0;
            for (HollowModel.Piece piece : model.modelPieces()) {
                for (Face face : Face.values()) {
                    float[] extent = face.extent(piece.size());
                    int w = Math.max(1, Math.round(extent[0] * DENSITY));
                    int h = Math.max(1, Math.round(extent[1] * DENSITY));
                    wanted.add(new Region(piece, face, 0, 0, w, h));
                    area += (long) (w + 2 * PADDING) * (h + 2 * PADDING);
                }
            }
            wanted.sort(Comparator.comparingInt(Region::h).reversed().thenComparingInt(Region::w));
            int size = 64;
            while ((long) size * size < area * 1.15) {
                size *= 2;
            }
            width = size;
            int x = 0;
            int y = 0;
            int row = 0;
            for (Region region : wanted) {
                int w = region.w() + 2 * PADDING;
                int h = region.h() + 2 * PADDING;
                if (x + w > width) {
                    x = 0;
                    y += row;
                    row = 0;
                }
                Region placed = new Region(region.piece(), region.face(), x + PADDING, y + PADDING, region.w(),
                        region.h());
                regions.computeIfAbsent(region.piece(), piece -> new Region[Face.values().length])
                        [region.face().ordinal()] = placed;
                x += w;
                row = Math.max(row, h);
            }
            int used = y + row;
            int tall = 16;
            while (tall < used) {
                tall *= 2;
            }
            height = tall;
        }

        Region region(HollowModel.Piece piece, Face face) {
            return regions.get(piece)[face.ordinal()];
        }

        BufferedImage paint() {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Painter painter = new Painter(model);
            for (Region[] faces : regions.values()) {
                for (Region region : faces) {
                    HollowModel.Piece piece = region.piece();
                    // Body-space position of a model-space point on the piece.
                    Matrix3f toBody = new Matrix3f().rotation(piece.rotation());
                    Vector3f normal = new Vector3f(region.face().normal).mul(FLIP).mul(toBody);
                    for (int py = -PADDING; py < region.h() + PADDING; py++) {
                        for (int px = -PADDING; px < region.w() + PADDING; px++) {
                            // Padding repeats the edge texel so mipmaps don't bleed neighbours in.
                            int cx = Math.clamp(px, 0, region.w() - 1);
                            int cy = Math.clamp(py, 0, region.h() - 1);
                            float u = (cx + .5f) / region.w();
                            float v = (cy + .5f) / region.h();
                            Vector3f local = region.face().point(piece.size(), u, v).mul(FLIP).mul(toBody)
                                    .add(piece.center());
                            int edge = Math.min(Math.min(cx, region.w() - 1 - cx), Math.min(cy, region.h() - 1 - cy));
                            image.setRGB(region.x() + px, region.y() + py,
                                    painter.color(piece, region.face(), local, normal, edge, u, v));
                        }
                    }
                }
            }
            return image;
        }
    }

    /** Procedural surface colour, sampled in body space so patterns run continuously across pieces. */
    private static final class Painter {
        private final HollowModel model;
        private final int seed;

        Painter(HollowModel model) {
            this.model = model;
            this.seed = model.type().hashCode();
        }

        int color(HollowModel.Piece piece, Face face, Vector3f p, Vector3f normal, int edge, float u, float v) {
            Material material = piece.material();
            float n = fbm(p.x * 6, p.y * 6, p.z * 6, 4);
            float fine = noise(p.x * 40, p.y * 40, p.z * 40);
            float r;
            float g;
            float b;
            float bevel = 0;
            switch (material) {
                case BLACK_CONCRETE -> {
                    // Cloth and hide: long vertical folds over a faint violet-black.
                    float folds = (float) Math.sin(Math.atan2(p.x, p.z) * 9 + fbm(p.x * 2, p.y * .6f, p.z * 2, 3) * 5);
                    float streak = fbm(p.x * 14, p.y * 1.5f, p.z * 14, 3);
                    float shade = .78f + folds * .16f + streak * .22f + fine * .06f;
                    r = 27 * shade;
                    g = 24 * shade;
                    b = 33 * shade;
                    bevel = .10f;
                }
                case POLISHED_BLACKSTONE -> {
                    float shade = .85f + n * .3f + fine * .1f;
                    r = 41 * shade;
                    g = 38 * shade;
                    b = 46 * shade;
                    bevel = .2f;
                }
                case DEEPSLATE_TILES -> {
                    float folds = (float) Math.sin(p.y * 22 + n * 4);
                    float shade = .82f + n * .25f + folds * .08f;
                    r = 58 * shade;
                    g = 55 * shade;
                    b = 64 * shade;
                    bevel = .18f;
                }
                case BONE_BLOCK, SMOOTH_QUARTZ, WHITE_CONCRETE -> {
                    boolean mask = material == Material.WHITE_CONCRETE;
                    boolean ivory = material == Material.SMOOTH_QUARTZ;
                    float[] base = mask ? new float[] {238, 237, 231} : ivory ? new float[] {228, 222, 204}
                            : new float[] {209, 199, 172};
                    // Broad, gentle tonal variation; bone is a little rougher than the polished mask.
                    float broad = fbm(p.x * 2.5f, p.y * 2.5f, p.z * 2.5f, 3);
                    float mottle = 1 + broad * (mask ? .07f : .1f) + n * (mask ? .02f : .04f) + fine * .012f;
                    // Sparse hairline cracks where a noise field crosses zero.
                    float crack = Math.abs(fbm(p.x * 4 + 17, p.y * 4, p.z * 4, 2));
                    float crackShade = crack < .012f ? .7f : crack < .024f ? .9f : 1;
                    // Dust collects on upward faces; grime collects low down.
                    float dust = Math.max(0, normal.y) * (.5f + .5f * noise(p.x * 11, 3, p.z * 11));
                    r = base[0] * mottle * crackShade - dust * 6;
                    g = base[1] * mottle * crackShade - dust * 10;
                    b = base[2] * mottle * crackShade - dust * 22;
                    bevel = mask ? .14f : .2f;
                }
                case RED_CONCRETE -> {
                    float shade = .92f + n * .12f;
                    r = 176 * shade;
                    g = 28 * shade;
                    b = 40 * shade;
                    bevel = .2f;
                }
                case NETHER_WART_BLOCK -> {
                    float shade = .8f + n * .3f + fine * .1f;
                    r = 98 * shade;
                    g = 17 * shade;
                    b = 30 * shade;
                    bevel = .25f;
                }
                case REDSTONE_BLOCK, OCHRE_FROGLIGHT, PEARLESCENT_FROGLIGHT -> {
                    float[] glow = material == Material.REDSTONE_BLOCK ? new float[] {255, 52, 44}
                            : material == Material.OCHRE_FROGLIGHT ? new float[] {255, 204, 72}
                            : new float[] {150, 214, 255};
                    float centre = 1 - Math.min(1, (Math.abs(u - .5f) + Math.abs(v - .5f)) * 1.2f);
                    r = glow[0] * (.75f + .25f * centre) + 50 * centre;
                    g = glow[1] * (.75f + .25f * centre) + 50 * centre;
                    b = glow[2] * (.75f + .25f * centre) + 50 * centre;
                }
                default -> {
                    r = 128;
                    g = 128;
                    b = 128;
                }
            }
            // A soft bevel darkens the outermost texels so each piece reads as a solid form.
            if (edge == 0) {
                float d = 1 - bevel;
                r *= d;
                g *= d;
                b *= d;
            }
            // Ground occlusion: the lowest quarter of the body darkens toward the feet.
            float rise = Math.min(1, Math.max(0, p.y / (model.height() * .25f)));
            float occlusion = piece.luminous() ? 1 : .72f + .28f * rise;
            return 0xFF000000 | channel(r * occlusion) << 16 | channel(g * occlusion) << 8 | channel(b * occlusion);
        }

        private static int channel(float value) {
            return Math.clamp(Math.round(value), 0, 255);
        }

        private float fbm(float x, float y, float z, int octaves) {
            float total = 0;
            float amplitude = .5f;
            for (int i = 0; i < octaves; i++) {
                total += noise(x, y, z) * amplitude;
                x *= 2.03f;
                y *= 2.03f;
                z *= 2.03f;
                amplitude *= .5f;
            }
            return total;
        }

        /** Smooth value noise in -1..1. */
        private float noise(float x, float y, float z) {
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            int iz = (int) Math.floor(z);
            float fx = smooth(x - ix);
            float fy = smooth(y - iy);
            float fz = smooth(z - iz);
            float x00 = lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx);
            float x10 = lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx);
            float x01 = lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx);
            float x11 = lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx);
            return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
        }

        private float hash(int x, int y, int z) {
            int h = seed ^ x * 374761393 ^ y * 668265263 ^ z * 1274126177;
            h = (h ^ h >>> 13) * 1274126177;
            h ^= h >>> 16;
            return (h & 0xFFFF) / 32767.5f - 1;
        }

        private static float smooth(float t) {
            return t * t * (3 - 2 * t);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }
}
