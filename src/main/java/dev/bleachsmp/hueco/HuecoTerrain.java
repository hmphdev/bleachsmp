package dev.bleachsmp.hueco;

/**
 * Deterministic terrain shape for Hueco Mundo. Everything is a pure function of the seed and
 * block position, so the generator, feature placement and hollow spawning all agree.
 */
final class HuecoTerrain {
    enum Kind { DUNE, FLAT, MESA, LANDMARK }

    /**
     * One column of terrain. {@code caveFloor}/{@code caveCeiling} are both 0 when the column
     * has no Forest of Menos cavern below it; {@code hole} marks a sinkhole shaft into it.
     */
    record Column(int surface, Kind kind, int caveFloor, int caveCeiling, double cave, boolean hole) {
        boolean hasCave() {
            return caveCeiling > caveFloor;
        }
    }

    record Sinkhole(int x, int z, int radius) {
    }

    static final int SINKHOLE_CELL = 176;
    static final int SINKHOLE_FUNNEL = 13;
    private static final double WIND = 0.62;
    private static final double WIND_COS = Math.cos(WIND);
    private static final double WIND_SIN = Math.sin(WIND);

    private HuecoTerrain() {
    }

    static Column column(long seed, int x, int z) {
        double landmarkGap = landmarkGap(x, z);
        if (landmarkGap <= 0) {
            var landmark = HuecoMundoLayout.landmarkAt(x, z);
            return new Column(landmark.floorY(), Kind.LANDMARK, 0, 0, 0, false);
        }

        double base = 68 + fbm(seed + 4, x / 260.0, z / 260.0, 2) * 7;

        // Dune sea: long asymmetric ridges across the wind, gentle windward slope, steep slip face.
        double field = smoothstep(-0.25, 0.3, perlin(seed + 1, x / 520.0, z / 520.0));
        double flat = smoothstep(0.18, 0.42, perlin(seed + 3, x / 360.0, z / 360.0));
        double u = x * WIND_COS + z * WIND_SIN + perlin(seed + 6, x / 140.0, z / 140.0) * 34;
        double v = -x * WIND_SIN + z * WIND_COS;
        double wavelength = 48 + perlin(seed + 7, x / 600.0, z / 600.0) * 18;
        double phase = u / wavelength - Math.floor(u / wavelength);
        double profile = phase < 0.78 ? smooth(phase / 0.78) : smooth((1 - phase) / 0.22);
        double crests = clamp(0.55 + perlin(seed + 8, v / 70.0, u / 260.0) * 0.9, 0, 1);
        double dunes = (4 + 15 * field) * profile * crests
                + perlin(seed + 9, x / 23.0, z / 23.0) * 1.5;
        double height = base + dunes * (1 - flat);
        height = height * (1 - flat) + (63.5 + perlin(seed + 9, x / 90.0, z / 90.0)) * flat;
        Kind kind = flat > 0.7 ? Kind.FLAT : Kind.DUNE;

        // Bleached mesas: terraced plateaus with near-vertical cliffs, kept away from landmarks.
        double mesa = fbm(seed + 2, x / 230.0, z / 230.0, 3) - (1 - smoothstep(30, 90, landmarkGap)) * 0.6;
        if (mesa > 0.3) {
            double tiers = Math.min(2.999, (mesa - 0.3) / 0.055);
            int tier = (int) tiers;
            double edge = smoothstep(0, 0.12, tiers - tier);
            double top = base + 11 + tier * 8 + perlin(seed + 11, x / 31.0, z / 31.0) * 1.2;
            double lower = tier == 0 ? height : base + 11 + (tier - 1) * 8;
            double mesaHeight = lower + (top - lower) * edge;
            if (mesaHeight > height) {
                height = mesaHeight;
                kind = Kind.MESA;
            }
        }

        // Landmarks sit on flat ground; the desert eases down to their floor.
        if (landmarkGap < 28) {
            var nearest = nearestLandmark(x, z);
            double blend = smooth(landmarkGap / 28);
            height = nearest.floorY() * (1 - blend) + height * blend;
        }

        int caveFloor = 0;
        int caveCeiling = 0;
        double cave = fbm(seed + 10, x / 210.0, z / 210.0, 2) - 0.12
                - (1 - smoothstep(20, 70, landmarkGap)) * 0.6;
        double strength = clamp(cave / 0.16, 0, 1);
        if (strength > 0) {
            caveFloor = 16 + (int) Math.round(perlin(seed + 12, x / 44.0, z / 44.0) * 3);
            caveCeiling = caveFloor + (int) Math.round(3 + 22 * strength
                    + perlin(seed + 13, x / 7.0, z / 7.0) * 3 * strength);
            if (caveCeiling - caveFloor < 4) {
                caveFloor = 0;
                caveCeiling = 0;
            }
        }

        boolean hole = false;
        Sinkhole sinkhole = nearestSinkhole(seed, x, z);
        if (sinkhole != null) {
            double distance = Math.hypot(x - sinkhole.x(), z - sinkhole.z());
            if (distance < sinkhole.radius()) {
                hole = true;
                if (caveCeiling <= caveFloor) {
                    caveFloor = 16;
                    caveCeiling = 22;
                }
            } else if (distance < sinkhole.radius() + SINKHOLE_FUNNEL) {
                double t = smooth((distance - sinkhole.radius()) / SINKHOLE_FUNNEL);
                height = height - 9 * (1 - t);
            }
        }

        int surface = (int) Math.round(height);
        if (caveCeiling > 0) {
            caveCeiling = Math.min(caveCeiling, surface - 6);
        }
        return new Column(surface, kind, caveFloor, caveCeiling, strength, hole);
    }

    /** Sinkholes sit on a coarse grid, one at most per cell, only over a deep part of the cavern. */
    static Sinkhole sinkhole(long seed, int cellX, int cellZ) {
        long hash = HuecoMundoLayout.hash(seed, cellX, 77, cellZ);
        if ((hash & 0xff) > 150) {
            return null;
        }
        int x = cellX * SINKHOLE_CELL + 30 + (int) ((hash >>> 8) & 0x7f) % (SINKHOLE_CELL - 60);
        int z = cellZ * SINKHOLE_CELL + 30 + (int) ((hash >>> 16) & 0x7f) % (SINKHOLE_CELL - 60);
        if (landmarkGap(x, z) < 70) {
            return null;
        }
        double cave = fbm(seed + 10, x / 210.0, z / 210.0, 2) - 0.12;
        if (cave < 0.1) {
            return null;
        }
        return new Sinkhole(x, z, 4 + (int) ((hash >>> 24) & 3));
    }

    static Sinkhole nearestSinkhole(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, SINKHOLE_CELL);
        int cellZ = Math.floorDiv(z, SINKHOLE_CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Sinkhole sinkhole = sinkhole(seed, cellX + dx, cellZ + dz);
                if (sinkhole != null && Math.hypot(x - sinkhole.x(), z - sinkhole.z())
                        < sinkhole.radius() + SINKHOLE_FUNNEL) {
                    return sinkhole;
                }
            }
        }
        return null;
    }

    /** Chebyshev distance outside the nearest landmark square; 0 or less is inside one. */
    static double landmarkGap(int x, int z) {
        double gap = Double.MAX_VALUE;
        for (var landmark : HuecoMundoLayout.LANDMARKS) {
            double distance = Math.max(Math.abs((double) x - landmark.x()), Math.abs((double) z - landmark.z()))
                    - landmark.radius();
            gap = Math.min(gap, distance);
        }
        return gap;
    }

    private static HuecoMundoLayout.Landmark nearestLandmark(int x, int z) {
        HuecoMundoLayout.Landmark best = null;
        double gap = Double.MAX_VALUE;
        for (var landmark : HuecoMundoLayout.LANDMARKS) {
            double distance = Math.max(Math.abs((double) x - landmark.x()), Math.abs((double) z - landmark.z()))
                    - landmark.radius();
            if (distance < gap) {
                gap = distance;
                best = landmark;
            }
        }
        return best;
    }

    /** Distance to the nearest edge between two cells of a jittered grid; small values draw cracks. */
    static double crackDistance(long seed, double x, double z) {
        int cellX = (int) Math.floor(x);
        int cellZ = (int) Math.floor(z);
        double first = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long hash = HuecoMundoLayout.hash(seed, cellX + dx, 5, cellZ + dz);
                double px = cellX + dx + ((hash >>> 8) & 0xffff) / 65536.0;
                double pz = cellZ + dz + ((hash >>> 24) & 0xffff) / 65536.0;
                double distance = Math.hypot(px - x, pz - z);
                if (distance < first) {
                    second = first;
                    first = distance;
                } else if (distance < second) {
                    second = distance;
                }
            }
        }
        return second - first;
    }

    /** Gradient noise, roughly in [-1, 1]. */
    static double perlin(long seed, double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double u = fade(fx);
        double w = fade(fz);
        double a = gradient(seed, ix, iz, fx, fz);
        double b = gradient(seed, ix + 1, iz, fx - 1, fz);
        double c = gradient(seed, ix, iz + 1, fx, fz - 1);
        double d = gradient(seed, ix + 1, iz + 1, fx - 1, fz - 1);
        double top = a + (b - a) * u;
        double bottom = c + (d - c) * u;
        return (top + (bottom - top) * w) * 1.41;
    }

    static double fbm(long seed, double x, double z, int octaves) {
        double total = 0;
        double amplitude = 1;
        double norm = 0;
        for (int octave = 0; octave < octaves; octave++) {
            total += perlin(seed + octave * 131L, x, z) * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            x *= 2.03;
            z *= 2.03;
        }
        return total / norm;
    }

    private static double gradient(long seed, int ix, int iz, double dx, double dz) {
        double angle = (HuecoMundoLayout.hash(seed, ix, 0, iz) >>> 40) / (double) (1L << 24) * Math.PI * 2;
        return Math.cos(angle) * dx + Math.sin(angle) * dz;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    static double smooth(double value) {
        value = clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }

    static double smoothstep(double from, double to, double value) {
        return smooth((value - from) / (to - from));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
