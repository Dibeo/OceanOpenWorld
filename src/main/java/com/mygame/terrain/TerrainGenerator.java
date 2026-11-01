package com.mygame.terrain;

public final class TerrainGenerator {
    private final PerlinNoise continental, islands, details, dunes, warp;

    public TerrainGenerator(long seed) {
        continental = new PerlinNoise(seed);
        islands = new PerlinNoise(seed + 1009L);
        details = new PerlinNoise(seed + 2003L);
        dunes = new PerlinNoise(seed + 3001L);
        warp = new PerlinNoise(seed + 4001L);
    }

    public float getHeight(double x, double z) {
        double dx = warp.fractal(x*.0018,z*.0018,3,.5,2)*55;
        double dz = warp.fractal((x+8451)*.0018,(z-3421)*.0018,3,.5,2)*55;
        double wx = x+dx, wz = z+dz;
        double continent = continental.fractal(wx*.0015,wz*.0015,4,.5,2);
        double island = islands.fractal(wx*.006,wz*.006,5,.5,2);
        double h = -5+continent*17+island*19;
        double landFactor = smoothstep(-2,8,h);
        h += details.fractal(wx*.022,wz*.022,4,.48,2)*(1.5+landFactor*3.5);
        h += dunes.fractal(x*.12,z*.12,3,.5,2)*.35;
        return (float)h;
    }

    private static double smoothstep(double min,double max,double v) {
        double t = Math.max(0,Math.min(1,(v-min)/(max-min)));
        return t*t*(3-2*t);
    }
}
