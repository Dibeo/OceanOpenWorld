package com.mygame.terrain;

import com.mygame.noise.PerlinNoise;

/**
 * Generates deterministic terrain heights, surface weights, and vegetation-placement fields
 * from a single world seed. Instances are immutable after construction and can be shared by
 * background chunk-generation tasks.
 */
public final class TerrainGenerator {
    private static final double ISLAND_FREQUENCY = .0035;

    private final long seed;
    private final PerlinNoise continental, islands, details, dunes, warp, tint;
    private final PerlinNoise grass, deep, rockField, rockShape, flowerField, flowerHue;
    private final PerlinNoise treeField, treeTone, treeVigor, treeShape;

    /**
     * Creates all terrain noise fields from the supplied world seed.
     *
     * @param seed world seed
     */
    public TerrainGenerator(long seed) {
        this.seed = seed;
        continental = new PerlinNoise(seed);
        islands = new PerlinNoise(seed + 1009L);
        details = new PerlinNoise(seed + 2003L);
        dunes = new PerlinNoise(seed + 3001L);
        warp = new PerlinNoise(seed + 4001L);
        tint = new PerlinNoise(seed + 5003L);
        grass = new PerlinNoise(seed + 6007L);
        deep = new PerlinNoise(seed + 7001L);
        rockField = new PerlinNoise(seed + 8009L);
        rockShape = new PerlinNoise(seed + 9011L);
        flowerField = new PerlinNoise(seed + 10007L);
        flowerHue = new PerlinNoise(seed + 11003L);
        treeField = new PerlinNoise(seed + 12007L);
        treeTone = new PerlinNoise(seed + 13001L);
        treeVigor = new PerlinNoise(seed + 14009L);
        treeShape = new PerlinNoise(seed + 15013L);
    }

    /**
     * Samples the world-space terrain height at horizontal world coordinates.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return height on the world Y axis
     */
    public float getHeight(double x, double z) {
        double dx = warp.fractal(x*.0018,z*.0018,3,.5,2)*55;
        double dz = warp.fractal((x+8451)*.0018,(z-3421)*.0018,3,.5,2)*55;
        double wx = x+dx, wz = z+dz;
        double continent = continental.fractal(wx*.0015,wz*.0015,4,.5,2);
        double island = islands.fractal(wx*ISLAND_FREQUENCY,wz*ISLAND_FREQUENCY,5,.5,2);
        double h = -5+continent*17+island*19;
        double landFactor = smoothstep(-2,8,h);
        h += details.fractal(wx*.022,wz*.022,4,.48,2)*(1.5+landFactor*3.5);

        // Asymmetric dunes rise slowly on one side and fall quickly on the other.
        // Blend them with landFactor to keep the underwater terrain smooth.
        double saw = dunes.fractal(x*.012,z*.012,3,.5,2)*6.0;
        double a = saw - Math.floor(saw);
        double profile = a < .8 ? a/.8 : (1-a)/.2;
        h += (profile - .5) * .9 * landFactor;
        // Preserve small ripples; finer ridges are added by the normal map.
        h += dunes.fractal(x*.12,z*.12,3,.5,2)*.2*landFactor;
        return (float)h;
    }

    /**
     * Samples broad-scale terrain tint variation used to break up uniform sand.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return value approximately in [-1, 1]
     */
    public float getTint(double x, double z) {
        return (float) tint.fractal(x*.004, z*.004, 2, .5, 2);
    }

    /**
     * Samples the seed-driven grass-clump placement probability at world coordinates.
     * Grass-covered areas are dense, with only occasional clearings exposing the ground.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return probability-like density in [0, 1]
     */
    public float getGrassDensity(double x, double z) {
        double value = grass.fractal(x * .035, z * .035, 3, .5, 2);
        double clearing = smoothstep(-.5, -.15, value);
        return (float) (clearing * (.82 + .18 * (value + 1.0) * .5));
    }

    /**
     * Samples a continuous grass color tone: 0 is deep green, 0.5 is base green, and 1 is yellow-green.
     * Broad regions and finer variation contribute across all grass-covered ground.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return grass tone in [0, 1]
     */
    public float getGrassTone(double x, double z) {
        double n = .7 * deep.fractal(x * .007, z * .007, 3, .5, 2) + .3 * deep.fractal(x * .03, z * .03, 2, .5, 2);
        return (float) (.5 + .5 * Math.max(-1.0, Math.min(1.0, n * 2.6)));
    }

    /**
     * Returns the derived deep-green blend weight at world coordinates.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return deep-green blend weight in [0, 1]
     */
    public float getGrassDeepness(double x, double z) {
        return (float) (1.0 - smoothstep(.05, .5, getGrassTone(x, z)));
    }

    /**
     * Returns the derived dry/yellow-green blend weight at world coordinates.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return dry/yellow-green blend weight in [0, 1]
     */
    public float getGrassDryness(double x, double z) {
        return (float) smoothstep(.5, .95, getGrassTone(x, z));
    }

    /**
     * Samples the broad-scale flower-field weight at world coordinates. Fields form patches
     * roughly thirty meters across, with few flowers outside them.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return flower-field weight in [0, 1]
     */
    public float getFlowerField(double x, double z) {
        return (float) smoothstep(-.22, .32, flowerField.fractal(x * .03, z * .03, 3, .5, 2));
    }

    /**
     * Samples the dominant flower-palette position at world coordinates. Patches of one color
     * are roughly twenty meters across.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return flower-palette position in [0, 1]
     */
    public float getFlowerHue(double x, double z) {
        double n = flowerHue.fractal(x * .045, z * .045, 2, .5, 2);
        return (float) (.5 + .5 * Math.max(-1.0, Math.min(1.0, n * 2.4)));
    }

    /**
     * Samples the broad-scale rocky-area weight at world coordinates. Rocks are concentrated
     * in broad patches, leaving most of the terrain nearly rock-free.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return rocky-area weight in [0, 1]
     */
    public float getRockField(double x, double z) {
        return (float) smoothstep(-.05, .45, rockField.fractal(x * .016, z * .016, 3, .5, 2));
    }

    /**
     * Samples the broad-scale forest coverage field at world coordinates.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return tree-placement weight in [0, 1]
     */
    public float getTreeField(double x, double z) {
        return (float) smoothstep(-.38, .38, treeField.fractal(x * .012, z * .012, 3, .5, 2));
    }

    /**
     * Samples a world-consistent foliage color tone.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return foliage tone in [0, 1]
     */
    public float getTreeTone(double x, double z) {
        double value = treeTone.fractal(x * .006, z * .006, 3, .5, 2);
        return (float) (.5 + .5 * Math.max(-1.0, Math.min(1.0, value * 2.2)));
    }

    /**
     * Samples world-consistent tree vigor used to vary height within each species.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return vigor in [0, 1]
     */
    public float getTreeVigor(double x, double z) {
        double value = treeVigor.fractal(x * .018, z * .018, 2, .5, 2);
        return (float) (.5 + .5 * Math.max(-1.0, Math.min(1.0, value * 2.0)));
    }

    /** Returns the shared seed-driven 3D noise used to bend tree trunks and branches. */
    PerlinNoise getTreeShapeNoise() {
        return treeShape;
    }

    /** Returns the world-seeded 3D noise used to shape rocks. */
    PerlinNoise getRockShapeNoise() {
        return rockShape;
    }

    long getRockChunkSeed(int chunkX, int chunkZ) {
        long value = seed ^ 0xD1B54A32D192ED03L;
        value = (value ^ chunkX) * 0xBF58476D1CE4E5B9L;
        value = (value ^ chunkZ) * 0x94D049BB133111EBL;
        return value ^ (value >>> 29);
    }

    long getGrassChunkSeed(int chunkX, int chunkZ) {
        return grassSeed(chunkX, chunkZ);
    }

    long getTreeChunkSeed(int chunkX, int chunkZ) {
        long value = seed ^ 0xA0761D6478BD642FL;
        value = (value ^ chunkX) * 0xE7037ED1A0B428DBL;
        value = (value ^ chunkZ) * 0x8EBC6AF09C88C6E3L;
        return value ^ (value >>> 32);
    }

    private long grassSeed(int chunkX, int chunkZ) {
        long value = seed ^ 0x9E3779B97F4A7C15L;
        value = (value ^ chunkX) * 0xBF58476D1CE4E5B9L;
        value = (value ^ chunkZ) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    static double islandFrequency() {
        return ISLAND_FREQUENCY;
    }

    private static double smoothstep(double min,double max,double v) {
        double t = Math.max(0,Math.min(1,(v-min)/(max-min)));
        return t*t*(3-2*t);
    }
}