package com.mygame.noise;

import com.mygame.terrain.TerrainGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests seed determinism, periodic sampling, and terrain repeatability for Perlin noise. */
final class PerlinNoiseTest {
    /** Verifies that equal seeds reproduce noise samples and terrain values. */
    @Test
    void sameSeedProducesSameNoiseAndTerrain() {
        PerlinNoise first = new PerlinNoise(123456789012345L);
        PerlinNoise second = new PerlinNoise(123456789012345L);
        for (double[] point : new double[][] {{0, 0}, {12.5, -91.25}, {10000.125, 45.75}}) {
            assertEquals(first.noise(point[0], point[1]), second.noise(point[0], point[1]));
        }

        TerrainGenerator firstWorld = new TerrainGenerator(987654321L);
        TerrainGenerator secondWorld = new TerrainGenerator(987654321L);
        assertEquals(firstWorld.getHeight(123.5, -456.25), secondWorld.getHeight(123.5, -456.25));
        assertEquals(firstWorld.getTint(123.5, -456.25), secondWorld.getTint(123.5, -456.25));
    }

    /** Verifies that periodic noise repeats after either or both configured periods. */
    @Test
    void periodicNoiseRepeatsOnBothAxes() {
        PerlinNoise noise = new PerlinNoise(42L);
        double value = noise.noise(3.25, 7.5, 16, 12);

        assertEquals(value, noise.noise(19.25, 7.5, 16, 12), 1e-6);
        assertEquals(value, noise.noise(3.25, 19.5, 16, 12), 1e-6);
        assertEquals(value, noise.noise(19.25, 19.5, 16, 12), 1e-6);
    }

    /** Verifies that different seeds can produce different noise samples. */
    @Test
    void differentSeedsProduceDifferentNoise() {
        assertNotEquals(new PerlinNoise(1L).noise(1.25, 3.5),
                new PerlinNoise(2L).noise(1.25, 3.5));
    }

    /** Verifies that periodic sampling rejects zero and negative periods. */
    @Test
    void periodicNoiseRequiresPositivePeriods() {
        PerlinNoise noise = new PerlinNoise(1L);
        assertThrows(IllegalArgumentException.class, () -> noise.noise(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> noise.noise(0, 0, 1, -1));
    }

    /** Verifies that fractal sampling is deterministic and remains finite in two and three dimensions. */
    @Test
    void fractalSamplingIsDeterministicAndFinite() {
        PerlinNoise first = new PerlinNoise(987L);
        PerlinNoise second = new PerlinNoise(987L);

        assertEquals(first.fractal(1.25, -9.5, 4, 0.5, 2.0),
                second.fractal(1.25, -9.5, 4, 0.5, 2.0));
        assertEquals(first.fractal(1.25, -9.5, 3.75, 4, 0.5, 2.0),
                second.fractal(1.25, -9.5, 3.75, 4, 0.5, 2.0));
        assertTrue(Double.isFinite(first.fractal(1.25, -9.5, 3.75, 4, 0.5, 2.0)));
    }

    /** Verifies that fractal sampling rejects a non-positive octave count in both dimensions. */
    @Test
    void fractalSamplingRequiresAtLeastOneOctave() {
        PerlinNoise noise = new PerlinNoise(1L);

        assertThrows(IllegalArgumentException.class, () -> noise.fractal(0, 0, 0, 0.5, 2));
        assertThrows(IllegalArgumentException.class, () -> noise.fractal(0, 0, 0, 0, 0.5, 2));
    }
}
