package com.mygame.noise;

import com.jayfella.fastnoise.FastNoise;

/**
 * Seeded Perlin noise backed by JME-FastNoise. Configure once, then safely share between
 * generation threads; equal seeds and coordinates produce equal values.
 */
public final class PerlinNoise {
    private static final double TWO_PI = Math.PI * 2.0;

    private final FastNoise noise;

    public PerlinNoise(long seed) {
        int fastNoiseSeed = (int) (seed ^ (seed >>> 32));
        noise = new FastNoise(fastNoiseSeed);
        noise.SetNoiseType(FastNoise.NoiseType.Perlin);
        noise.SetInterp(FastNoise.Interp.Quintic);
        noise.SetFrequency(1f);
    }

    public double noise(double x, double y) {
        return noise.GetNoise((float) x, (float) y);
    }

    /**
     * Periodic Perlin noise for seamless textures. A 2D tile is mapped onto a 3D torus so
     * both value and slope repeat at each edge without changing the FastNoise implementation.
     */
    public double noise(double x, double y, int px, int py) {
        if (px <= 0 || py <= 0) {
            throw new IllegalArgumentException("Noise periods must be positive");
        }

        double wrappedX = x - Math.floor(x / px) * px;
        double wrappedY = y - Math.floor(y / py) * py;
        double angleX = TWO_PI * wrappedX / px;
        double angleY = TWO_PI * wrappedY / py;
        double radiusX = px / TWO_PI;
        double radiusY = py / TWO_PI;
        double ring = radiusX + radiusY * Math.cos(angleY);

        return noise.GetNoise(
                (float) (ring * Math.cos(angleX)),
                (float) (ring * Math.sin(angleX)),
                (float) (radiusY * Math.sin(angleY)));
    }

    /** Bruit de Perlin 3D (volumes : rochers, etc.). Mêmes garanties de déterminisme que la version 2D. */
    public double noise(double x, double y, double z) {
        return noise.GetNoise((float) x, (float) y, (float) z);
    }

    /** fBm 3D normalisé par la somme des amplitudes (valeurs d'environ ±0,6). */
    public double fractal(double x, double y, double z, int octaves, double persistence, double lacunarity) {
        if (octaves < 1) {
            throw new IllegalArgumentException("Octaves must be positive");
        }
        double total = 0, amplitude = 1, frequency = 1, sum = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency, z * frequency) * amplitude;
            sum += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return total / sum;
    }

    public double fractal(double x, double y, int octaves, double persistence, double lacunarity) {
        if (octaves < 1) {
            throw new IllegalArgumentException("Octaves must be positive");
        }
        double total = 0, amplitude = 1, frequency = 1, sum = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            sum += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return total / sum;
    }
}