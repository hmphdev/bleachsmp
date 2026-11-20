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

    public static final Landmark GATE = new Landmark("gate", "The Severed Gate", 0, 0, 46, 72);
    public static final int GATE_ARRIVAL_Z_OFFSET = 40;
    public static final Landmark BONES = new Landmark("bones", "Cathedral of Ribs", -176, -160, 28, 72);
    public static final Landmark WATCH = new Landmark("watch", "The Broken Watch", 160, -144, 24, 74);
    public static final Landmark PALACE = new Landmark("palace", "Las Noches Outskirts", 0, -384, 78, 76);
    public static final List<Landmark> LANDMARKS = List.of(GATE, BONES, WATCH, PALACE);
    public static final int ARENA_RADIUS = 21;
    public static final int ARENA_Z_OFFSET = 17;
    public static final int ALTAR_X = PALACE.x();
    public static final int ALTAR_Z = PALACE.z() + 3;

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
        return Math.hypot(x - PALACE.x() - 0.5, z - PALACE.z() - ARENA_Z_OFFSET - 0.5) <= ARENA_RADIUS;
    }

    public static boolean nearLandmark(int x, int z, int margin) {
        return LANDMARKS.stream().anyMatch(landmark -> Math.abs((long) x - landmark.x()) <= landmark.radius() + margin
                && Math.abs((long) z - landmark.z()) <= landmark.radius() + margin);
    }

    public static long hash(long seed, int x, int y, int z) {
        long value = seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (y * 42317861L);
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
