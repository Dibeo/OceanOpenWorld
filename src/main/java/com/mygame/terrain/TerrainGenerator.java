package com.mygame.terrain;

import com.mygame.noise.PerlinNoise;

/** Générateur de hauteurs. Immuable après construction : utilisable depuis plusieurs threads. */
public final class TerrainGenerator {
    private static final double ISLAND_FREQUENCY = .0035;

    private final long seed;
    private final PerlinNoise continental, islands, details, dunes, warp, tint;
    private final PerlinNoise grass;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        continental = new PerlinNoise(seed);
        islands = new PerlinNoise(seed + 1009L);
        details = new PerlinNoise(seed + 2003L);
        dunes = new PerlinNoise(seed + 3001L);
        warp = new PerlinNoise(seed + 4001L);
        tint = new PerlinNoise(seed + 5003L);
        grass = new PerlinNoise(seed + 6007L);
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

    /** Sécheresse de l'herbe (0 = verte, 1 = jaunie) : grandes zones + petites taches. */
    public float getGrassDryness(double x, double z) {
        double large = smoothstep(.12, .55, getTint(x, z));
        double patches = smoothstep(.15, .7, grass.fractal(x * .09 + 311, z * .09 - 97, 2, .5, 2));
        return (float) Math.min(1.0, large * .75 + patches * .35);
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