package com.mygame.terrain;

import java.util.Random;

/** Bruit de Perlin 2D. Immuable après construction : utilisable depuis plusieurs threads. */
public final class PerlinNoise {
    private final int[] p = new int[512];

    public PerlinNoise(long seed) {
        int[] values = new int[256];
        for (int i = 0; i < 256; i++) values[i] = i;
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = values[i]; values[i] = values[j]; values[j] = temp;
        }
        for (int i = 0; i < 512; i++) p[i] = values[i & 255];
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double fade(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double lerp(double a, double b, double t) { return a+t*(b-a); }
    private static double grad(int h, double x, double y) {
        switch (h & 7) {
            case 0: return x+y;
            case 1: return -x+y;
            case 2: return x-y;
            case 3: return -x-y;
            case 4: return x;
            case 5: return -x;
            case 6: return y;
            default: return -y;
        }
    }

    public double noise(double x, double y) {
        int fx = fastFloor(x), fy = fastFloor(y);
        int xi = fx & 255, yi = fy & 255;
        double xf = x - fx, yf = y - fy;
        double u = fade(xf), v = fade(yf);
        int aa = p[p[xi]+yi], ab = p[p[xi]+yi+1];
        int ba = p[p[xi+1]+yi], bb = p[p[xi+1]+yi+1];
        return lerp(lerp(grad(aa,xf,yf),grad(ba,xf-1,yf),u),
                lerp(grad(ab,xf,yf-1),grad(bb,xf-1,yf-1),u),v);
    }

    /**
     * Bruit qui se répète avec la période (px, py) en unités de grille (1..256) :
     * sert à fabriquer des textures sans couture.
     */
    public double noise(double x, double y, int px, int py) {
        int fx = fastFloor(x), fy = fastFloor(y);
        double xf = x - fx, yf = y - fy;
        double u = fade(xf), v = fade(yf);
        int x0 = Math.floorMod(fx, px), x1 = (x0 + 1) % px;
        int y0 = Math.floorMod(fy, py), y1 = (y0 + 1) % py;
        int aa = p[p[x0]+y0], ab = p[p[x0]+y1];
        int ba = p[p[x1]+y0], bb = p[p[x1]+y1];
        return lerp(lerp(grad(aa,xf,yf),grad(ba,xf-1,yf),u),
                lerp(grad(ab,xf,yf-1),grad(bb,xf-1,yf-1),u),v);
    }

    public double fractal(double x, double y, int octaves, double persistence, double lacunarity) {
        double total = 0, amplitude = 1, frequency = 1, sum = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x*frequency,y*frequency)*amplitude;
            sum += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return total/sum;
    }
}