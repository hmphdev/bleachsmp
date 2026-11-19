package dev.bleachsmp.hueco;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

final class LasNochesGenerator {
    private static final int WALL_X = 54;
    private static final int WALL_Z = 51;
    private static final int WALL_HEIGHT = 22;
    private static final int GATE_HALF_WIDTH = 19;
    private static final int GATE_FRONT = WALL_Z + 16;
    private static final int GATE_BACK = WALL_Z - 9;
    private static final int KEEP_Z = -26;
    private static final int KEEP_RADIUS = 25;
    private static final int KEEP_WALL_HEIGHT = 26;

    private LasNochesGenerator() {
    }

    static void generate(ChunkData data, int chunkX, int chunkZ) {
        var palace = HuecoMundoLayout.PALACE;
        int floor = palace.floorY();
        for (int localX = 0; localX < 16; localX++) {
            int x = chunkX * 16 + localX - palace.x();
            int across = Math.abs(x);
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = chunkZ * 16 + localZ - palace.z();
                if (across <= WALL_X + 11 && Math.abs(z) <= WALL_Z + 11) {
                    var column = new Column(data, localX, floor, localZ);
                    buildCourt(column, x, z);
                    buildWall(column, across, z);
                    buildTower(column, across - WALL_X, Math.abs(z) - WALL_Z);
                    buildKeep(column, x, z - KEEP_Z);
                }
                if (z > GATE_FRONT && z <= palace.radius() && across <= 11) {
                    buildApproach(new Column(data, localX, floor, localZ), across, z);
                }
                if (z >= GATE_BACK && z <= GATE_FRONT && across <= GATE_HALF_WIDTH) {
                    buildGatehouse(new Column(data, localX, floor, localZ), across, z);
                }
            }
        }
    }

    private static void buildApproach(Column column, int x, int z) {
        if (x <= 9) {
            column.set(0, x == 9 ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_QUARTZ);
        }
        if (x == 11 && z % 8 == 0) {
            buildLamp(column);
        }
    }

    private static void buildCourt(Column column, int x, int z) {
        int across = Math.abs(x);
        if (across > WALL_X + 2 || Math.abs(z) > WALL_Z + 2) {
            return;
        }
        int arenaZ = z - HuecoMundoLayout.ARENA_Z_OFFSET;
        int arenaDistance = x * x + arenaZ * arenaZ;
        int radius = HuecoMundoLayout.ARENA_RADIUS;
        Material paving = across <= 9 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS;
        if (arenaDistance <= radius * radius) {
            paving = arenaDistance >= (radius - 1) * (radius - 1) ? Material.POLISHED_BLACKSTONE
                    : (x + z) % 8 == 0 ? Material.CALCITE : Material.SMOOTH_QUARTZ;
        }
        column.set(0, paving);
        int altarZ = z - (HuecoMundoLayout.ALTAR_Z - HuecoMundoLayout.PALACE.z());
        if (across <= 3 && Math.abs(altarZ) <= 2) {
            column.set(0, across == 0 && altarZ == 0 ? Material.SEA_LANTERN : Material.CHISELED_QUARTZ_BLOCK);
        }
        if (across == 26 && z >= 8 && z <= 40 && z % 8 == 0) {
            buildLamp(column);
        }
    }

    private static void buildLamp(Column column) {
        column.set(1, Material.POLISHED_BLACKSTONE);
        column.set(2, Material.QUARTZ_PILLAR);
        column.set(3, Material.SOUL_LANTERN);
    }

    private static void buildWall(Column column, int x, int z) {
        int depth = Math.abs(z);
        int endOffset = depth - WALL_Z;
        int sideOffset = x - WALL_X;
        boolean southGate = z > 0 && x < GATE_HALF_WIDTH - 1;
        if (x <= WALL_X && Math.abs(endOffset) <= 7 && !southGate) {
            buildCurtain(column, x, endOffset);
        }
        if (depth <= WALL_Z && Math.abs(sideOffset) <= 7) {
            buildCurtain(column, depth, sideOffset);
        }
    }

    private static void buildGatehouse(Column column, int x, int z) {
        int setback = GATE_FRONT - z;
        boolean outerFrame = setback <= 1;
        boolean rib = setback == 6 || setback == 12 || setback == 18 || z == GATE_BACK;
        int archWidth = outerFrame ? 13 : setback <= 4 ? 12 : rib ? 10 : 11;
        int opening = archRise(x, archWidth, 10);
        int top = outerFrame ? 27 : 30;

        if (x <= 13) {
            Material paving = x == 9 ? Material.POLISHED_BLACKSTONE
                    : rib || outerFrame ? Material.CHISELED_QUARTZ_BLOCK : Material.SMOOTH_QUARTZ;
            column.set(0, paving);
        }
        for (int y = opening + 1; y <= top; y++) {
            // Only the arch molding and the piers reach the front edge of the porch.
            boolean archTrim = (opening > 0 && y <= opening + 2) || (x == archWidth + 1 && y <= 10);
            boolean frontPier = x >= 14 && x <= GATE_HALF_WIDTH - (y > 5 ? 1 : 0);
            if (outerFrame && !archTrim && !frontPier && y != top) {
                continue;
            }
            if (x == GATE_HALF_WIDTH && y > 5 && y != 28 && y != 30) {
                continue;
            }

            Material masonry;
            if (archTrim) {
                masonry = outerFrame ? Material.SMOOTH_QUARTZ
                        : rib ? Material.POLISHED_DIORITE : Material.POLISHED_BLACKSTONE_BRICKS;
            } else if (y == 5 || y == 28 || y == top) {
                masonry = Material.CHISELED_QUARTZ_BLOCK;
            } else if (frontPier && (outerFrame || x >= 17)) {
                masonry = Material.QUARTZ_PILLAR;
            } else {
                masonry = Material.QUARTZ_BRICKS;
            }
            if (rib && x == 0 && y == opening + 1) {
                masonry = Material.SEA_LANTERN;
            }
            column.set(y, masonry);
        }

        // Recessed lights sit in the jambs between the ribs of the vault.
        if (!rib && setback >= 7 && setback % 6 == 1 && x == archWidth + 1) {
            column.set(7, Material.POLISHED_BLACKSTONE);
            column.set(8, Material.SEA_LANTERN);
            column.set(9, Material.POLISHED_BLACKSTONE);
        }
        if (setback == 4 && x <= 2) {
            column.set(27, x == 0 ? Material.SEA_LANTERN : Material.CHISELED_QUARTZ_BLOCK);
        }
        if (setback >= 3 && z >= WALL_Z - 4 && x <= 17 && x % 6 < 3
                && (setback == 3 || x >= 15 || z == WALL_Z - 4)) {
            column.set(31, Material.QUARTZ_BRICKS);
            column.set(32, Material.SMOOTH_QUARTZ);
        }
    }

    private static void buildCurtain(Column column, int along, int offset) {
        int distance = Math.abs(offset);
        int bay = along % 14;
        int fromPier = Math.min(bay, 14 - bay);
        int fromWindow = Math.abs(bay - 7);
        int windowTop = archRise(fromWindow, 1, 15);
        int frameTop = archRise(fromWindow, 2, 17);

        for (int y = 1; y <= WALL_HEIGHT; y++) {
            if (distance <= 4 && y <= 3) {
                column.set(y, y == 3 ? Material.POLISHED_DIORITE : Material.QUARTZ_BRICKS);
            } else if (fromPier <= 1 && distance > 2 && distance <= (offset > 0 ? buttressDepth(y) : 3)) {
                column.set(y, y == 6 || y == 12 || y == 18 ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_PILLAR);
            } else if (distance <= 3 && (y == 20 || y == WALL_HEIGHT)) {
                column.set(y, Material.SMOOTH_QUARTZ);
            } else if (distance <= 2) {
                if (y >= 8 && y <= windowTop) {
                    if (offset == 0) {
                        column.set(y, fromWindow == 0 && y == 12 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE);
                    }
                } else if (distance == 2 && y >= 7 && y <= frameTop) {
                    column.set(y, Material.POLISHED_DIORITE);
                } else {
                    column.set(y, Material.QUARTZ_BRICKS);
                }
            }
        }
        if (distance <= 2 && along % 6 < 3) {
            column.fill(WALL_HEIGHT + 1, WALL_HEIGHT + 3, Material.QUARTZ_BRICKS);
            column.set(WALL_HEIGHT + 3, Material.SMOOTH_QUARTZ);
        }
    }

    private static int buttressDepth(int height) {
        if (height <= 6) {
            return 7;
        }
        if (height <= 12) {
            return 6;
        }
        return height <= 18 ? 5 : 4;
    }

    private static int archRise(int fromCenter, int halfWidth, int spring) {
        if (fromCenter > halfWidth) {
            return 0;
        }
        return spring + (int) Math.sqrt(halfWidth * halfWidth - fromCenter * fromCenter);
    }

    private static void buildTower(Column column, int x, int z) {
        int distance = x * x + z * z;
        if (distance > 121) {
            return;
        }
        for (int y = 1; y <= 54; y++) {
            int shaftRadius = y <= 5 ? 10 : y <= 36 ? 9 : y <= 45 ? 7 : y <= 51 ? 5 : 3;
            boolean cornice = y == 5 || y == 8 || y == 34 || y == 36 || y == 45 || y == 51 || y == 54;
            int radius = shaftRadius + (cornice ? 1 : 0);
            if (distance > radius * radius) {
                continue;
            }
            int along = Math.min(Math.abs(x), Math.abs(z));
            int out = Math.max(Math.abs(x), Math.abs(z));
            boolean slit = along <= 1 && ((y >= 14 && y <= 23) || (y >= 39 && y <= 43));
            if (slit && out > radius - 3) {
                if (out == radius - 2) {
                    column.set(y, along == 0 && y % 5 == 0 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE);
                }
                continue;
            }
            boolean pilaster = Math.abs(Math.abs(x) - Math.abs(z)) <= 1 && out >= radius / 2;
            column.set(y, cornice ? Material.CHISELED_QUARTZ_BLOCK
                    : pilaster ? Material.QUARTZ_PILLAR : Material.QUARTZ_BRICKS);
        }
    }

    private static void buildKeep(Column column, int x, int z) {
        int distance = x * x + z * z;
        int outerCollar = KEEP_RADIUS + 3;
        if (distance > outerCollar * outerCollar) {
            return;
        }
        int insideRadius = KEEP_RADIUS - 4;
        if (distance >= insideRadius * insideRadius) {
            buildKeepWall(column, x, z, distance);
        } else {
            column.set(0, Math.abs(x) <= 3 ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_QUARTZ);
            if (Math.abs(x) == 11 && Math.abs(z) <= 12 && z % 8 == 0) {
                column.fill(1, 10, Material.QUARTZ_PILLAR);
                column.set(10, Material.SEA_LANTERN);
            }
        }
        if (distance > KEEP_RADIUS * KEEP_RADIUS) {
            return;
        }
        // An elliptical roof with a hollow interior and stepped quartz bands.
        int outer = domeRise(distance, KEEP_RADIUS, 24);
        int inner = domeRise(distance, insideRadius, 21);
        for (int rise = Math.max(1, inner + 1); rise <= outer; rise++) {
            column.set(KEEP_WALL_HEIGHT + rise, rise % 6 == 0 ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ);
        }
        if (distance <= 4) {
            column.fill(KEEP_WALL_HEIGHT + outer + 1, 54, Material.CHISELED_QUARTZ_BLOCK);
        }
    }

    private static void buildKeepWall(Column column, int x, int z, int distance) {
        int across = Math.abs(x);
        int depth = Math.abs(z);
        int fromAxis = Math.min(across, depth);
        int fromDiagonal = Math.abs(across - depth);
        boolean entrance = z > 0 && across <= 10;
        boolean rib = !entrance && (fromAxis <= 1 || fromDiagonal <= 1);
        int windowOffset = Math.abs(fromAxis - 9);
        int windowTop = entrance ? 0 : archRise(windowOffset, 1, 17);

        for (int y = 1; y <= KEEP_WALL_HEIGHT; y++) {
            boolean collar = y <= 3 || y >= KEEP_WALL_HEIGHT - 2;
            boolean doorFrame = entrance && (y <= 20 || collar);
            int outerRadius = collar ? KEEP_RADIUS + 3 : rib || doorFrame ? KEEP_RADIUS + 2 : KEEP_RADIUS;
            if (distance > outerRadius * outerRadius) {
                continue;
            }
            int opening = entrance ? archRise(across, distance > KEEP_RADIUS * KEEP_RADIUS ? 8 : 7, 10) : 0;
            if (y <= opening) {
                continue;
            }
            if (y >= 9 && y <= windowTop && distance >= (KEEP_RADIUS - 2) * (KEEP_RADIUS - 2)) {
                if (distance < (KEEP_RADIUS - 1) * (KEEP_RADIUS - 1)) {
                    column.set(y, windowOffset == 0 && y == 13 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE);
                }
                continue;
            }
            boolean jamb = entrance && ((opening > 0 && y <= opening + 2) || (across == 9 && y <= 11));
            column.set(y, jamb ? Material.POLISHED_DIORITE : collar ? Material.SMOOTH_QUARTZ
                    : rib ? Material.QUARTZ_PILLAR : Material.QUARTZ_BRICKS);
        }
    }

    private static int domeRise(int distance, int radius, int height) {
        return (int) (height * Math.sqrt(Math.max(0, 1 - distance / (double) (radius * radius))));
    }

    private record Column(ChunkData data, int x, int floor, int z) {
        void set(int height, Material material) {
            data.setBlock(x, floor + height, z, material);
        }

        void fill(int from, int to, Material material) {
            data.setRegion(x, floor + from, z, x + 1, floor + to, z + 1, material);
        }
    }
}
