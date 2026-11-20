package dev.bleachsmp.hueco;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Scattered Hueco Mundo features. Each sits on a jittered grid cell, and every chunk draws
 * every feature that can reach it, clipped to its own bounds, so features cross chunk borders.
 */
final class HuecoFeatures {
    private interface Placer {
        void place(Canvas canvas, Random random, int x, int z);
    }

    private final long seed;

    HuecoFeatures(long seed) {
        this.seed = seed;
    }

    void decorate(ChunkData data, int chunkX, int chunkZ) {
        Canvas canvas = new Canvas(data, chunkX, chunkZ);
        scatter(canvas, 40, 16, 11, 0.5, this::surfaceTree);
        scatter(canvas, 150, 34, 12, 0.55, this::skeleton);
        scatter(canvas, 120, 24, 13, 0.45, this::spires);
        scatter(canvas, 15, 28, 14, 0.6, this::menosTree);
        scatter(canvas, 11, 2, 15, 0.08, this::debris);
    }

    private void scatter(Canvas canvas, int cell, int reach, int salt, double chance, Placer placer) {
        int minX = Math.floorDiv(canvas.minX - reach, cell);
        int maxX = Math.floorDiv(canvas.minX + 15 + reach, cell);
        int minZ = Math.floorDiv(canvas.minZ - reach, cell);
        int maxZ = Math.floorDiv(canvas.minZ + 15 + reach, cell);
        for (int cellX = minX; cellX <= maxX; cellX++) {
            for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
                Random random = new Random(HuecoMundoLayout.hash(seed, cellX, salt, cellZ));
                if (random.nextDouble() >= chance) {
                    continue;
                }
                int margin = Math.min(reach, cell / 4);
                int x = cellX * cell + margin + random.nextInt(Math.max(1, cell - margin * 2));
                int z = cellZ * cell + margin + random.nextInt(Math.max(1, cell - margin * 2));
                if (HuecoTerrain.landmarkGap(x, z) < reach + 8) {
                    continue;
                }
                placer.place(canvas, random, x, z);
            }
        }
    }

    // ---- Quartz trees on the surface: pale, leafless and crystalline. ----

    private void surfaceTree(Canvas canvas, Random random, int x, int z) {
        var column = HuecoTerrain.column(seed, x, z);
        if (column.hole() || nearSinkhole(x, z) || column.kind() == HuecoTerrain.Kind.MESA && random.nextInt(3) > 0) {
            return;
        }
        int height = 7 + random.nextInt(7);
        tree(canvas, random, x, column.surface() - 2, z, height, height >= 11, false);
    }

    private void menosTree(Canvas canvas, Random random, int x, int z) {
        var column = HuecoTerrain.column(seed, x, z);
        int room = column.caveCeiling() - column.caveFloor();
        if (!column.hasCave() || room < 9 || column.hole()) {
            return;
        }
        int height = Math.max(6, room - 3 - random.nextInt(5));
        tree(canvas, random, x, column.caveFloor() - 1, z, height, room > 15, true);
    }

    private void tree(Canvas canvas, Random random, int x, int y, int z, int height, boolean thick, boolean menos) {
        double leanX = random.nextGaussian() * 0.12 * height;
        double leanZ = random.nextGaussian() * 0.12 * height;
        double topX = x + leanX;
        double topY = y + 2 + height;
        double topZ = z + leanZ;
        line(canvas, x, y, z, topX, topY, topZ, thick ? 0.9 : 0, Material.QUARTZ_BLOCK);
        if (thick) {
            for (int root = 0; root < 4; root++) {
                double angle = root * Math.PI / 2 + random.nextDouble() * 0.8;
                line(canvas, x, y + 3, z, x + Math.cos(angle) * 3, y + 1, z + Math.sin(angle) * 3, 0,
                        Material.QUARTZ_BLOCK);
            }
        }
        int branches = 3 + random.nextInt(3);
        double yaw = random.nextDouble() * Math.PI * 2;
        for (int branch = 0; branch < branches; branch++) {
            double along = 0.45 + 0.5 * branch / branches;
            double bx = x + leanX * along;
            double by = y + 2 + height * along;
            double bz = z + leanZ * along;
            yaw += Math.PI * 2 / branches + random.nextGaussian() * 0.4;
            double pitch = Math.toRadians(20 + random.nextInt(35));
            double length = height * (0.35 + random.nextDouble() * 0.3);
            branch(canvas, random, bx, by, bz, yaw, pitch, length, 2, menos);
        }
        tip(canvas, random, topX, topY + 1, topZ, menos);
    }

    private void branch(Canvas canvas, Random random, double x, double y, double z, double yaw, double pitch,
            double length, int depth, boolean menos) {
        double ex = x + Math.cos(yaw) * Math.cos(pitch) * length;
        double ey = y + Math.sin(pitch) * length;
        double ez = z + Math.sin(yaw) * Math.cos(pitch) * length;
        line(canvas, x, y, z, ex, ey, ez, 0, depth == 2 ? Material.QUARTZ_BLOCK : Material.SMOOTH_QUARTZ);
        if (depth > 1 && length > 3) {
            for (int fork = 0; fork < 2; fork++) {
                branch(canvas, random, ex, ey, ez, yaw + (fork == 0 ? -0.6 : 0.6) + random.nextGaussian() * 0.2,
                        pitch + 0.25, length * 0.55, depth - 1, menos);
            }
        } else {
            tip(canvas, random, ex, ey, ez, menos);
        }
    }

    private void tip(Canvas canvas, Random random, double x, double y, double z, boolean menos) {
        Material crystal = menos && random.nextInt(4) == 0 ? Material.SEA_LANTERN
                : random.nextInt(3) == 0 ? Material.WHITE_STAINED_GLASS : Material.SMOOTH_QUARTZ;
        canvas.set(x, y, z, crystal);
    }

    // ---- Remains of something enormous, half swallowed by the sand. ----

    private void skeleton(Canvas canvas, Random random, int x, int z) {
        var column = HuecoTerrain.column(seed, x, z);
        if (column.hole() || nearSinkhole(x, z) || column.kind() == HuecoTerrain.Kind.MESA) {
            return;
        }
        double yaw = random.nextDouble() * Math.PI * 2;
        double forwardX = Math.cos(yaw);
        double forwardZ = Math.sin(yaw);
        double sideX = -forwardZ;
        double sideZ = forwardX;
        int length = 26 + random.nextInt(14);
        double ground = column.surface() - 1 - random.nextInt(4);
        double spineY = ground + 6 + random.nextInt(4);
        double sink = random.nextDouble() * 0.3;

        double startX = x - forwardX * length / 2.0;
        double startZ = z - forwardZ * length / 2.0;
        double endX = x + forwardX * length / 2.0;
        double endZ = z + forwardZ * length / 2.0;
        // The spine dips toward the tail, where the sand has taken more of it.
        line(canvas, startX, spineY - length * sink, startZ, endX, spineY, endZ, 0.8, Material.BONE_BLOCK);

        for (int rib = 4; rib < length - 6; rib += 4) {
            double t = rib / (double) length;
            double cx = startX + forwardX * rib;
            double cz = startZ + forwardZ * rib;
            double cy = spineY - length * sink * (1 - t);
            double span = 4 + 6 * Math.sin(Math.PI * Math.min(1, t * 1.2));
            for (int side = -1; side <= 1; side += 2) {
                double px = cx;
                double py = cy;
                double pz = cz;
                for (int step = 1; step <= 12; step++) {
                    double angle = step / 12.0 * Math.PI * 0.62;
                    double nx = cx + sideX * side * Math.sin(angle) * span;
                    double ny = cy - (1 - Math.cos(angle)) * span * 1.3;
                    double nz = cz + sideZ * side * Math.sin(angle) * span;
                    line(canvas, px, py, pz, nx, ny, nz, 0, Material.BONE_BLOCK);
                    px = nx;
                    py = ny;
                    pz = nz;
                }
            }
        }

        // A mask-like skull at the head, open enough to walk into.
        double headX = endX + forwardX * 5;
        double headZ = endZ + forwardZ * 5;
        double headY = spineY - 1;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dy = -5; dy <= 6; dy++) {
                for (int dz = -7; dz <= 7; dz++) {
                    double along = dx * forwardX + dz * forwardZ;
                    double across = dx * sideX + dz * sideZ;
                    double shape = along * along / 36.0 + across * across / 25.0 + dy * dy / 20.0;
                    if (shape > 1.05) {
                        continue;
                    }
                    boolean shell = shape > 0.62;
                    boolean socket = along > 2.5 && dy >= 0 && dy <= 2 && Math.abs(Math.abs(across) - 2.2) < 1.3;
                    boolean mouth = along > 3 && dy == -2 && Math.abs(across) <= 2;
                    Material block = !shell || socket || mouth ? Material.AIR : Material.BONE_BLOCK;
                    if (block == Material.AIR && headY + dy <= ground) {
                        continue;
                    }
                    canvas.set(headX + dx, headY + dy, headZ + dz, block);
                }
            }
        }
        for (int fang = -2; fang <= 2; fang += 2) {
            double fx = headX + forwardX * 5.5 + sideX * fang;
            double fz = headZ + forwardZ * 5.5 + sideZ * fang;
            line(canvas, fx, headY - 2, fz, fx, headY - 4, fz, 0, Material.CALCITE);
        }
    }

    // ---- Crystal spires: tall tilted shards, the desert's only landmarks. ----

    private void spires(Canvas canvas, Random random, int x, int z) {
        var column = HuecoTerrain.column(seed, x, z);
        if (column.hole() || nearSinkhole(x, z)) {
            return;
        }
        int shards = 2 + random.nextInt(4);
        for (int shard = 0; shard < shards; shard++) {
            double bx = x + random.nextGaussian() * 3;
            double bz = z + random.nextGaussian() * 3;
            int ground = HuecoTerrain.column(seed, (int) bx, (int) bz).surface() - 3;
            double length = (shard == 0 ? 22 : 9) + random.nextInt(shard == 0 ? 16 : 12);
            double tiltYaw = random.nextDouble() * Math.PI * 2;
            double tilt = Math.toRadians(random.nextInt(shard == 0 ? 14 : 34));
            double radius = shard == 0 ? 2.6 : 1.2 + random.nextDouble();
            double tx = bx + Math.cos(tiltYaw) * Math.sin(tilt) * length;
            double ty = ground + Math.cos(tilt) * length;
            double tz = bz + Math.sin(tiltYaw) * Math.sin(tilt) * length;
            int steps = (int) Math.ceil(length * 2);
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                double r = radius * (1 - t);
                ball(canvas, bx + (tx - bx) * t, ground + (ty - ground) * t, bz + (tz - bz) * t, r,
                        r > 1.6 ? Material.QUARTZ_BLOCK : t > 0.85 ? Material.WHITE_STAINED_GLASS
                                : Material.SMOOTH_QUARTZ);
            }
        }
    }

    // ---- Small scraps: bone shards and faint lights on the dunes. ----

    private void debris(Canvas canvas, Random random, int x, int z) {
        var column = HuecoTerrain.column(seed, x, z);
        if (column.hole() || column.kind() == HuecoTerrain.Kind.LANDMARK) {
            return;
        }
        int y = column.surface() + 1;
        switch (random.nextInt(3)) {
            case 0 -> canvas.set(x, y, z, Material.END_ROD);
            case 1 -> {
                canvas.set(x, y, z, Material.BONE_BLOCK);
                canvas.set(x + random.nextInt(3) - 1, y, z + random.nextInt(3) - 1, Material.BONE_BLOCK);
            }
            default -> line(canvas, x, y - 1, z, x + random.nextInt(5) - 2, y + 1 + random.nextInt(2),
                    z + random.nextInt(5) - 2, 0, Material.BONE_BLOCK);
        }
    }

    private boolean nearSinkhole(int x, int z) {
        return HuecoTerrain.nearestSinkhole(seed, x, z) != null;
    }

    private static void line(Canvas canvas, double x0, double y0, double z0, double x1, double y1, double z1,
            double radius, Material material) {
        double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        int steps = Math.max(1, (int) Math.ceil(length * 2));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            ball(canvas, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t, radius, material);
        }
    }

    private static void ball(Canvas canvas, double x, double y, double z, double radius, Material material) {
        if (radius < 0.5) {
            canvas.set(x, y, z, material);
            return;
        }
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius + 0.3) {
                        canvas.set(x + dx, y + dy, z + dz, material);
                    }
                }
            }
        }
    }

    /** Writes to one chunk, silently dropping blocks that land outside it. */
    static final class Canvas {
        private final ChunkData data;
        final int minX;
        final int minZ;

        Canvas(ChunkData data, int chunkX, int chunkZ) {
            this.data = data;
            this.minX = chunkX * 16;
            this.minZ = chunkZ * 16;
        }

        void set(double x, double y, double z, Material material) {
            int localX = (int) Math.floor(x) - minX;
            int localZ = (int) Math.floor(z) - minZ;
            int blockY = (int) Math.floor(y);
            if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16
                    && blockY > data.getMinHeight() && blockY < data.getMaxHeight()) {
                data.setBlock(localX, blockY, localZ, material);
            }
        }
    }
}
