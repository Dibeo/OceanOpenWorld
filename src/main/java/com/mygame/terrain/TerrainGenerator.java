package com.mygame.terrain;

import com.mygame.noise.PerlinNoise;

/** Générateur de hauteurs. Immuable après construction : utilisable depuis plusieurs threads. */
public final class TerrainGenerator {
    private static final double ISLAND_FREQUENCY = .0035;

    private final long seed;
    private final PerlinNoise continental, islands, details, dunes, warp, tint;
    private final PerlinNoise grass, deep, rockField, rockShape, flowerField, flowerHue;

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
    }

    public float getHeight(double x, double z) {
        double dx = warp.fractal(x*.0018,z*.0018,3,.5,2)*55;
        double dz = warp.fractal((x+8451)*.0018,(z-3421)*.0018,3,.5,2)*55;
        double wx = x+dx, wz = z+dz;
        double continent = continental.fractal(wx*.0015,wz*.0015,4,.5,2);
        double island = islands.fractal(wx*ISLAND_FREQUENCY,wz*ISLAND_FREQUENCY,5,.5,2);
        double h = -5+continent*17+island*19;
        double landFactor = smoothstep(-2,8,h);
        h += details.fractal(wx*.022,wz*.022,4,.48,2)*(1.5+landFactor*3.5);

        // Dunes asymétriques : montée lente d'un côté, chute rapide de l'autre.
        // Fondu par landFactor pour rester lisses sous l'eau.
        double saw = dunes.fractal(x*.012,z*.012,3,.5,2)*6.0;
        double a = saw - Math.floor(saw);
        double profile = a < .8 ? a/.8 : (1-a)/.2;
        h += (profile - .5) * .9 * landFactor;
        // Petites ondulations conservées (les rides fines sont dans la normal map).
        h += dunes.fractal(x*.12,z*.12,3,.5,2)*.2*landFactor;
        return (float)h;
    }

    /** Variation de teinte à grande échelle (-1..1 environ) pour casser l'uniformité du sable. */
    public float getTint(double x, double z) {
        return (float) tint.fractal(x*.004, z*.004, 2, .5, 2);
    }

    /**
     * Probabilité de présence d'une touffe (0..1) pilotée par le Perlin de la seed.
     * Le sol herbeux est densément couvert ; seules de rares clairières laissent voir le sol.
     */
    public float getGrassDensity(double x, double z) {
        double value = grass.fractal(x * .035, z * .035, 3, .5, 2);
        double clearing = smoothstep(-.5, -.15, value);
        return (float) (clearing * (.82 + .18 * (value + 1.0) * .5));
    }

    /**
     * Ton du vert (0..1) : 0 = vert profond, 0,5 = vert de base, 1 = vert jaune. Un seul champ de Perlin
     * continu (grandes plages + variation plus fine) : tout le sol herbeux participe au dégradé.
     */
    public float getGrassTone(double x, double z) {
        double n = .7 * deep.fractal(x * .007, z * .007, 3, .5, 2) + .3 * deep.fractal(x * .03, z * .03, 2, .5, 2);
        return (float) (.5 + .5 * Math.max(-1.0, Math.min(1.0, n * 2.6)));
    }

    /** Part de vert profond (0..1), dérivée du ton. */
    public float getGrassDeepness(double x, double z) {
        return (float) (1.0 - smoothstep(.05, .5, getGrassTone(x, z)));
    }

    /** Part de vert jaune / herbe sèche (0..1), dérivée du ton. */
    public float getGrassDryness(double x, double z) {
        return (float) smoothstep(.5, .95, getGrassTone(x, z));
    }

    /** Prairies fleuries (0..1) : plaques d'une trentaine de mètres ; hors plaque, peu de fleurs. */
    public float getFlowerField(double x, double z) {
        return (float) smoothstep(-.22, .32, flowerField.fractal(x * .03, z * .03, 3, .5, 2));
    }

    /** Couleur dominante des fleurs (0..1) : plaques d'une vingtaine de mètres de même couleur. */
    public float getFlowerHue(double x, double z) {
        double n = flowerHue.fractal(x * .045, z * .045, 2, .5, 2);
        return (float) (.5 + .5 * Math.max(-1.0, Math.min(1.0, n * 2.4)));
    }

    /** Zones rocheuses (0..1) : de larges plaques, la plupart du terrain n'a presque aucun rocher. */
    public float getRockField(double x, double z) {
        return (float) smoothstep(-.05, .45, rockField.fractal(x * .016, z * .016, 3, .5, 2));
    }

    /** Bruit 3D qui donne leur forme aux rochers (dépend de la seed du monde). */
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