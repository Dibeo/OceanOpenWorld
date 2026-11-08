package com.mygame.noise;

import com.jayfella.fastnoise.FastNoise;

/**
 * Seeded Perlin noise backed by JME-FastNoise. Configure once, then safely share between
 * generation threads; equal seeds and coordinates produce equal values.
 */
public final class PerlinNoise {
    private static final double TWO_PI = Math.PI * 2.0;

    private final FastNoise noise;

    /**
     * Creates a deterministic Perlin noise source for the supplied seed.
     *
     * @param seed seed folded to the noise implementation's integer seed
     */
    public PerlinNoise(long seed) {
        int fastNoiseSeed = Long.hashCode(seed);
        noise = new FastNoise(fastNoiseSeed);
        noise.SetNoiseType(FastNoise.NoiseType.Perlin);
        noise.SetInterp(FastNoise.Interp.Quintic);
        noise.SetFrequency(1f);
    }

    /**
     * Samples non-periodic 2D Perlin noise.
     *
     * @param x noise-space X coordinate
     * @param y noise-space Y coordinate
     * @return deterministic noise value
     */
    public double noise(double x, double y) {
        return noise.GetNoise((float) x, (float) y);
    }

    /**
     * Samples 2D Perlin noise with seamless periods on both axes. The tile is mapped onto
     * a 3D torus so both value and slope repeat at each edge without changing FastNoise.
     *
     * @param x noise-space X coordinate
     * @param y noise-space Y coordinate
     * @param px positive period along X
     * @param py positive period along Y
     * @return deterministic periodic noise value
     * @throws IllegalArgumentException if either period is not positive
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

    /**
     * Samples deterministic non-periodic 3D Perlin noise for volumes such as rocks.
     *
     * @param x noise-space X coordinate
     * @param y noise-space Y coordinate
     * @param z noise-space Z coordinate
     * @return noise value
     */
    public double noise(double x, double y, double z) {
        return noise.GetNoise((float) x, (float) y, (float) z);
    }

    /**
     * Samples normalized three-dimensional fractal Brownian motion (fBm), divided by the
     * sum of octave amplitudes (typically around ±0.6).
     *
     * @param x noise-space X coordinate
     * @param y noise-space Y coordinate
     * @param z noise-space Z coordinate
     * @param octaves number of noise octaves; must be positive
     * @param persistence multiplier applied to amplitude after each octave
     * @param lacunarity multiplier applied to frequency after each octave
     * @return weighted sum divided by the sum of octave amplitudes
     * @throws IllegalArgumentException if {@code octaves} is less than one
     */
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

    /**
     * Samples normalized two-dimensional fractal Brownian motion (fBm).
     *
     * @param x noise-space X coordinate
     * @param y noise-space Y coordinate
     * @param octaves number of noise octaves; must be positive
     * @param persistence multiplier applied to amplitude after each octave
     * @param lacunarity multiplier applied to frequency after each octave
     * @return weighted sum divided by the sum of octave amplitudes
     * @throws IllegalArgumentException if {@code octaves} is less than one
     */
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