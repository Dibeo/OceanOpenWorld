package com.mygame.terrain;

import java.util.Random;

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
        int xi = ((int)Math.floor(x)) & 255;
        int yi = ((int)Math.floor(y)) & 255;
        double xf = x-Math.floor(x), yf = y-Math.floor(y);
        double u = fade(xf), v = fade(yf);
        int aa = p[p[xi]+yi], ab = p[p[xi]+yi+1];
        int ba = p[p[xi+1]+yi], bb = p[p[xi+1]+yi+1];
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
