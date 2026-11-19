package dev.bleachsmp.hueco;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

public final class HuecoMundoGenerator extends ChunkGenerator {
    private static final int TERRAIN_BASE_Y = 40;
    private static final int GATE_PLINTH_HEIGHT = 6;
    private final BlockData gateStep = Material.QUARTZ_SLAB.createBlockData();
    private static final Material[] PALETTE = {
            Material.WHITE_TERRACOTTA, Material.CALCITE, Material.WHITE_CONCRETE,
            Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE_POWDER
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = data.getMinHeight();
        data.setRegion(0, minY, 0, 16, minY + 1, 16, Material.BEDROCK);
        data.setRegion(0, minY + 1, 0, 16, TERRAIN_BASE_Y, 16, Material.WHITE_TERRACOTTA);

        long seed = worldInfo.getSeed();
        for (int localX = 0; localX < 16; localX++) {
            int blockX = chunkX * 16 + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int blockZ = chunkZ * 16 + localZ;
                int height = HuecoMundoLayout.height(seed, blockX, blockZ);
                for (int y = TERRAIN_BASE_Y; y <= height; y++) {
                    int depth = height - y;
                    Material material = depth == 0 ? PALETTE[4]
                            : depth <= 2 ? PALETTE[1]
                            : depth <= 6 ? PALETTE[2]
                            : depth <= 10 ? PALETTE[3]
                            : PALETTE[0];
                    data.setBlock(localX, y, localZ, material);
                }
            }
        }

        generateLandmarks(data, chunkX, chunkZ);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new PaleDesertBiomeProvider();
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean canSpawn(org.bukkit.World world, int x, int z) {
        return true;
    }

    @Override
    public org.bukkit.Location getFixedSpawnLocation(org.bukkit.World world, Random random) {
        var gate = HuecoMundoLayout.GATE;
        return new org.bukkit.Location(world, gate.x() + 0.5, gate.floorY() + 1,
                gate.z() + HuecoMundoLayout.GATE_ARRIVAL_Z_OFFSET + 0.5, 180.0f, 0.0f);
    }

    private void generateLandmarks(ChunkData data, int chunkX, int chunkZ) {
        if (HuecoMundoLayout.GATE.intersectsChunk(chunkX, chunkZ)) {
            generateGate(data, chunkX, chunkZ);
        }
        if (HuecoMundoLayout.BONES.intersectsChunk(chunkX, chunkZ)) {
            generateCathedral(data, chunkX, chunkZ);
        }
        if (HuecoMundoLayout.WATCH.intersectsChunk(chunkX, chunkZ)) {
            generateWatchtower(data, chunkX, chunkZ);
        }
        if (HuecoMundoLayout.PALACE.intersectsChunk(chunkX, chunkZ)) {
            LasNochesGenerator.generate(data, chunkX, chunkZ);
        }
    }

    private void generateGate(ChunkData data, int chunkX, int chunkZ) {
        var gate = HuecoMundoLayout.GATE;
        int courtY = gate.floorY();
        int deckY = courtY + GATE_PLINTH_HEIGHT;
        for (int localX = 0; localX < 16; localX++) {
            int x = Math.abs(chunkX * 16 + localX - gate.x());
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = chunkZ * 16 + localZ - gate.z();
                if (z < -46 || z > 44 || x > 36) {
                    continue;
                }

                boolean onPlinth = z >= -35 && z <= -8;
                int courtEdge = z <= 14 ? 36 : Math.max(15, 50 - z);
                if (x <= courtEdge) {
                    Material tile = x == courtEdge ? Material.POLISHED_BLACKSTONE
                            : x <= 10 && (x == 10 || z % 8 == 0) ? Material.SMOOTH_QUARTZ
                            : x <= 10 ? Material.QUARTZ_BLOCK : Material.QUARTZ_BRICKS;
                    data.setBlock(localX, courtY, localZ, tile);
                }
                if (onPlinth) {
                    data.setRegion(localX, courtY + 1, localZ, localX + 1, deckY, localZ + 1,
                            z == -8 || x == 36 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.CALCITE);
                    data.setBlock(localX, deckY, localZ, Material.QUARTZ_BRICKS);
                } else if (x <= 16 && z >= -7 && z <= 4) {
                    buildGateStairs(data, localX, localZ, x, 5 - z, courtY);
                } else if (x <= 16 && z < -35) {
                    buildGateStairs(data, localX, localZ, x, z + 47, courtY);
                }

                if (x >= 18 && z >= -28 && z <= -9) {
                    buildGateTower(data, localX, localZ, x, z, deckY);
                } else if (x < 18 && z >= -25 && z <= -11) {
                    buildGateArch(data, localX, localZ, x, z, deckY);
                }
                if (x == 12 && (z == 10 || z == 20 || z == 30 || z == 40)) {
                    data.setBlock(localX, courtY + 1, localZ, Material.POLISHED_BLACKSTONE);
                    data.setBlock(localX, courtY + 2, localZ, Material.QUARTZ_PILLAR);
                    data.setBlock(localX, courtY + 3, localZ, Material.SOUL_LANTERN);
                }
            }
        }
    }

    private void buildGateStairs(ChunkData data, int localX, int localZ, int x, int halfSteps, int courtY) {
        int stepY = courtY + halfSteps / 2;
        if (x >= 15) {
            data.setRegion(localX, courtY + 1, localZ, localX + 1, stepY + 2, localZ + 1,
                    Material.POLISHED_BLACKSTONE_BRICKS);
            data.setBlock(localX, stepY + 2, localZ, Material.SMOOTH_QUARTZ);
        } else {
            if (stepY > courtY) {
                data.setRegion(localX, courtY + 1, localZ, localX + 1, stepY + 1, localZ + 1,
                        Material.SMOOTH_QUARTZ);
            }
            if (halfSteps % 2 != 0) {
                data.setBlock(localX, stepY + 1, localZ, gateStep);
            }
        }
    }

    private void buildGateTower(ChunkData data, int localX, int localZ, int x, int z, int deckY) {
        int fromCenter = Math.abs(x - 26);
        int depth = Math.abs(2 * z + 37);
        for (int rise = 1; rise <= 48; rise++) {
            int inset = rise <= 4 ? -1 : rise <= 30 ? 0 : rise <= 39 ? 2 : rise <= 45 ? 4 : 6;
            if (fromCenter > 8 - inset || depth > 17 - inset * 2) {
                continue;
            }
            boolean front = z == -10 - inset;
            boolean band = rise == 4 || rise == 29 || rise == 38 || rise == 44 || rise == 48;
            Material block = band ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_BRICKS;
            if (front && !band && (fromCenter == 5 || fromCenter == 7)) {
                block = Material.QUARTZ_PILLAR;
            }
            if (front && fromCenter <= 2 && (rise >= 10 && rise <= 24 || rise >= 32 && rise <= 36)) {
                block = fromCenter == 0 && rise % 4 == 0 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE;
            }
            data.setBlock(localX, deckY + rise, localZ, block);
        }
    }

    private void buildGateArch(ChunkData data, int localX, int localZ, int x, int z, int deckY) {
        // The same profile runs through the wall, so the opening is a walkable passage.
        int opening = x > 13 ? 0 : 18 + (int) Math.sqrt(196 - x * x);
        int crown = x <= 3 ? 42 : x <= 8 ? 39 : 36;
        for (int rise = opening + 1; rise <= crown; rise++) {
            boolean rim = x >= 14 && x <= 15 && rise <= 24 || x <= 13 && rise <= opening + 2;
            Material block = rise == crown || rise == 35 || rim ? Material.SMOOTH_QUARTZ
                    : Material.POLISHED_BLACKSTONE_BRICKS;
            if (z == -11 && rise > 34 && rise < crown && x + rise - 34 <= 6) {
                block = x == 0 && rise == 38 ? Material.SEA_LANTERN : Material.CHISELED_QUARTZ_BLOCK;
            }
            data.setBlock(localX, deckY + rise, localZ, block);
        }
    }

    private void generateCathedral(ChunkData data, int chunkX, int chunkZ) {
        int cx = HuecoMundoLayout.BONES.x();
        int cz = HuecoMundoLayout.BONES.z();
        int floor = HuecoMundoLayout.BONES.floorY();
        for (int zOffset = -18; zOffset <= 18; zOffset += 6) {
            for (int xOffset = -13; xOffset <= 13; xOffset++) {
                int rise = 2 + (13 - Math.abs(xOffset)) / 2;
                for (int thickness = 0; thickness < 2; thickness++) {
                    put(data, chunkX, chunkZ, cx + xOffset, floor + rise + thickness,
                            cz + zOffset, Material.BONE_BLOCK);
                }
            }
        }
        for (int xOffset : new int[] {-13, 13}) {
            for (int zOffset = -18; zOffset <= 18; zOffset += 6) {
                for (int y = floor + 1; y <= floor + 7; y++) {
                    put(data, chunkX, chunkZ, cx + xOffset, y, cz + zOffset, Material.BONE_BLOCK);
                }
            }
        }
        for (int zOffset = -22; zOffset <= 22; zOffset += 4) {
            put(data, chunkX, chunkZ, cx, floor + 1, cz + zOffset, Material.QUARTZ_BLOCK);
        }
    }

    private void generateWatchtower(ChunkData data, int chunkX, int chunkZ) {
        int cx = HuecoMundoLayout.WATCH.x();
        int cz = HuecoMundoLayout.WATCH.z();
        int floor = HuecoMundoLayout.WATCH.floorY();
        for (int y = floor + 1; y <= floor + 17; y++) {
            int inset = y > floor + 12 ? (y - floor - 12) / 3 : 0;
            for (int x = -5 + inset; x <= 5 - inset; x++) {
                for (int z = -5 + inset; z <= 5 - inset; z++) {
                    boolean wall = Math.abs(x) == 5 - inset || Math.abs(z) == 5 - inset;
                    if (wall && !(z == 5 - inset && Math.abs(x) <= 1 && y < floor + 5)) {
                        put(data, chunkX, chunkZ, cx + x, y, cz + z,
                                y % 4 == 0 ? Material.QUARTZ_BLOCK : Material.CALCITE);
                    }
                }
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int y = floor + 8; y <= floor + 14; y++) {
                put(data, chunkX, chunkZ, cx + x, y, cz - 6, Material.BLACK_CONCRETE);
            }
        }
        for (int x = -2; x <= 2; x++) {
            put(data, chunkX, chunkZ, cx + x, floor + 11, cz - 7, Material.YELLOW_CONCRETE);
        }
        for (int y = floor + 9; y <= floor + 13; y++) {
            put(data, chunkX, chunkZ, cx, y, cz - 7, Material.YELLOW_CONCRETE);
        }
        put(data, chunkX, chunkZ, cx + 4, floor + 18, cz, Material.QUARTZ_PILLAR);
        put(data, chunkX, chunkZ, cx + 5, floor + 19, cz, Material.QUARTZ_PILLAR);
        put(data, chunkX, chunkZ, cx + 6, floor + 20, cz, Material.QUARTZ_PILLAR);
    }

    private void put(ChunkData data, int chunkX, int chunkZ, int blockX, int y, int blockZ, Material material) {
        int localX = blockX - chunkX * 16;
        int localZ = blockZ - chunkZ * 16;
        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16
                && y >= data.getMinHeight() && y < data.getMaxHeight()) {
            data.setBlock(localX, y, localZ, material);
        }
    }

    private static final class PaleDesertBiomeProvider extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.DESERT;
        }

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z,
                org.bukkit.generator.BiomeParameterPoint biomeParameterPoint) {
            return Biome.DESERT;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.DESERT);
        }
    }
}
