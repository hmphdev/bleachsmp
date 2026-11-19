package dev.bleachsmp.hueco;

import java.util.List;

/** Shared, seed-independent landmark bounds keep generation and protection in agreement. */
public final class HuecoMundoLayout {
    public record Landmark(String id, String title, int x, int z, int radius, int floorY) {
        public boolean contains(int blockX, int blockZ) {
            return Math.abs((long) blockX - x) <= radius && Math.abs((long) blockZ - z) <= radius;
        }

        public boolean intersectsChunk(int chunkX, int chunkZ) {
            return chunkX * 16L <= x + radius && chunkX * 16L + 15 >= x - radius
                    && chunkZ * 16L <= z + radius && chunkZ * 16L + 15 >= z - radius;
        }
    }

    public static final Landmark GATE = new Landmark("gate", "The Severed Gate", 0, 0, 18, 72);
    public static final Landmark BONES = new Landmark("bones", "Cathedral of Ribs", -176, -160, 28, 72);
    public static final Landmark WATCH = new Landmark("watch", "The Broken Watch", 160, -144, 24, 74);
    public static final Landmark PALACE = new Landmark("palace", "Las Noches Outskirts", 0, -384, 66, 76);
    public static final List<Landmark> LANDMARKS = List.of(GATE, BONES, WATCH, PALACE);
    public static final int ARENA_RADIUS = 29;
    public static final int ALTAR_X = PALACE.x();
    public static final int ALTAR_Z = PALACE.z() + 18;

    private HuecoMundoLayout() {
    }

    public static Landmark landmarkAt(int x, int z) {
        for (Landmark landmark : LANDMARKS) {
            if (landmark.contains(x, z)) {
                return landmark;
            }
        }
        return null;
    }

    public static boolean inArena(double x, double z) {
        return Math.hypot(x - PALACE.x() - 0.5, z - PALACE.z() - 0.5) <= ARENA_RADIUS;
    }

    public static boolean nearLandmark(int x, int z, int margin) {
        return LANDMARKS.stream().anyMatch(landmark -> Math.abs((long) x - landmark.x()) <= landmark.radius() + margin
                && Math.abs((long) z - landmark.z()) <= landmark.radius() + margin);
    }

    public static int height(long seed, int x, int z) {
        double dunes = 70 + noise(seed, x / 120.0, z / 120.0) * 15
                + noise(seed + 41, x / 37.0, z / 37.0) * 6
                + Math.sin(x * 0.055 + z * 0.024 + noise(seed + 9, x / 90.0, z / 90.0) * 3) * 2.5;
        for (Landmark landmark : LANDMARKS) {
            double distance = Math.max(Math.abs((double) x - landmark.x()), Math.abs((double) z - landmark.z()));
            if (distance <= landmark.radius()) {
                return landmark.floorY();
            }
            if (distance < landmark.radius() + 16) {
                double blend = smooth((distance - landmark.radius()) / 16.0);
                return (int) Math.round(landmark.floorY() * (1 - blend) + dunes * blend);
            }
        }
        return (int) Math.round(dunes);
    }

    public static double noise(long seed, double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double tx = smooth(x - ix);
        double tz = smooth(z - iz);
        double north = sample(seed, ix, iz) * (1 - tx) + sample(seed, ix + 1, iz) * tx;
        double south = sample(seed, ix, iz + 1) * (1 - tx) + sample(seed, ix + 1, iz + 1) * tx;
        return north * (1 - tz) + south * tz;
    }

    public static long hash(long seed, int x, int y, int z) {
        long value = seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (y * 42317861L);
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static double sample(long seed, int x, int z) {
        return (hash(seed, x, 0, z) >>> 11) * 0x1.0p-52 - 1;
    }

    private static double smooth(double value) {
        return value * value * (3 - 2 * value);
    }
}
