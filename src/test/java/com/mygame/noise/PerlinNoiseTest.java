package com.mygame.noise;

import com.mygame.terrain.TerrainGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PerlinNoiseTest {
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

    @Test
    void periodicNoiseRepeatsOnBothAxes() {
        PerlinNoise noise = new PerlinNoise(42L);
        double value = noise.noise(3.25, 7.5, 16, 12);

        assertEquals(value, noise.noise(19.25, 7.5, 16, 12), 1e-6);
        assertEquals(value, noise.noise(3.25, 19.5, 16, 12), 1e-6);
        assertEquals(value, noise.noise(19.25, 19.5, 16, 12), 1e-6);
    }

    @Test
    void differentSeedsProduceDifferentNoise() {
        assertNotEquals(new PerlinNoise(1L).noise(1.25, 3.5),
                new PerlinNoise(2L).noise(1.25, 3.5));
    }

    @Test
    void periodicNoiseRequiresPositivePeriods() {
        PerlinNoise noise = new PerlinNoise(1L);
        assertThrows(IllegalArgumentException.class, () -> noise.noise(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> noise.noise(0, 0, 1, -1));
    }
}
