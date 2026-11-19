package dev.bleachsmp.hueco;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

public final class HuecoMundoGenerator extends ChunkGenerator {
    private static final int TERRAIN_BASE_Y = 40;
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
        return new org.bukkit.Location(world, 0.5, HuecoMundoLayout.GATE.floorY() + 1, 10.5, 180.0f, 0.0f);
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
            generatePalace(data, chunkX, chunkZ);
        }
    }

    private void generateGate(ChunkData data, int chunkX, int chunkZ) {
        int floor = HuecoMundoLayout.GATE.floorY();
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                if (x * x + z * z <= 196) {
                    Material tile = (Math.abs(x) == Math.abs(z) && Math.abs(x) % 4 == 0)
                            ? Material.CALCITE : Material.QUARTZ_BLOCK;
                    put(data, chunkX, chunkZ, x, floor, z, tile);
                }
            }
        }
        for (int side : new int[] {-1, 1}) {
            for (int dx = 0; dx < 2; dx++) {
                int x = side * (5 + dx);
                for (int y = floor + 1; y <= floor + 10; y++) {
                    for (int z = -2; z <= 2; z++) {
                        put(data, chunkX, chunkZ, x, y, z,
                                y % 3 == 0 ? Material.CALCITE : Material.POLISHED_BLACKSTONE);
                    }
                }
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int y = floor + 9; y <= floor + 11; y++) {
                for (int z = -2; z <= 2; z++) {
                    put(data, chunkX, chunkZ, x, y, z,
                            (x + y) % 4 == 0 ? Material.CALCITE : Material.POLISHED_BLACKSTONE);
                }
            }
        }
        for (int x : new int[] {-8, 8}) {
            for (int y = floor + 1; y <= floor + 14; y++) {
                put(data, chunkX, chunkZ, x, y, -2, Material.QUARTZ_PILLAR);
                put(data, chunkX, chunkZ, x, y, 2, Material.QUARTZ_PILLAR);
            }
            put(data, chunkX, chunkZ, x, floor + 14, 0, Material.SOUL_LANTERN);
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

    private void generatePalace(ChunkData data, int chunkX, int chunkZ) {
        int cx = HuecoMundoLayout.PALACE.x();
        int cz = HuecoMundoLayout.PALACE.z();
        int floor = HuecoMundoLayout.PALACE.floorY();
        for (int x = -48; x <= 48; x++) {
            boolean gate = Math.abs(x) <= 8;
            for (int y = floor + 1; y <= floor + 14; y++) {
                if (!gate || y > floor + 8) {
                    put(data, chunkX, chunkZ, cx + x, y, cz - 45,
                            y % 5 == 0 ? Material.QUARTZ_BLOCK : Material.WHITE_CONCRETE);
                }
            }
        }
        for (int[] tower : new int[][] {{-48, -45}, {48, -45}, {-48, 45}, {48, 45}}) {
            int tx = cx + tower[0];
            int tz = cz + tower[1];
            for (int y = floor + 1; y <= floor + 31; y++) {
                int inset = y > floor + 24 ? 1 : 0;
                for (int x = -4 + inset; x <= 4 - inset; x++) {
                    for (int z = -4 + inset; z <= 4 - inset; z++) {
                        if (Math.abs(x) >= 3 - inset || Math.abs(z) >= 3 - inset) {
                            put(data, chunkX, chunkZ, tx + x, y, tz + z,
                                    y % 5 == 0 ? Material.QUARTZ_BLOCK : Material.CALCITE);
                        }
                    }
                }
            }
        }
        for (int dx = -HuecoMundoLayout.ARENA_RADIUS; dx <= HuecoMundoLayout.ARENA_RADIUS; dx++) {
            for (int dz = -HuecoMundoLayout.ARENA_RADIUS; dz <= HuecoMundoLayout.ARENA_RADIUS; dz++) {
                double radius = Math.hypot(dx, dz);
                if (radius <= HuecoMundoLayout.ARENA_RADIUS) {
                    Material tile = Math.abs(radius - 27) < 1.5 ? Material.POLISHED_BLACKSTONE
                            : (dx + dz) % 9 == 0 ? Material.CALCITE : Material.QUARTZ_BLOCK;
                    put(data, chunkX, chunkZ, cx + dx, floor, cz + dz, tile);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    put(data, chunkX, chunkZ, HuecoMundoLayout.ALTAR_X + dx, floor + 1,
                            HuecoMundoLayout.ALTAR_Z + dz, Material.BLACKSTONE);
                }
            }
        }
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
