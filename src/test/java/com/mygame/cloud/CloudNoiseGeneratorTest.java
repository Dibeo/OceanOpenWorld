package com.mygame.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for deterministic, seamless cloud-noise generation and wind derivation. */
class CloudNoiseGeneratorTest {
    private static final int SIZE = 256;

    /** Verifies that generating the same seed twice produces identical texture data. */
    @Test
    void sameSeedProducesIdenticalTexture() {
        assertArrayEquals(CloudNoiseGenerator.generate(42L, SIZE), CloudNoiseGenerator.generate(42L, SIZE));
    }

    /** Verifies that different seeds produce different texture data. */
    @Test
    void differentSeedsProduceDifferentTextures() {
        assertFalse(java.util.Arrays.equals(
                CloudNoiseGenerator.generate(1L, SIZE), CloudNoiseGenerator.generate(2L, SIZE)));
    }

    /** Verifies that every generated channel contains meaningful value variation. */
    @Test
    void everyChannelUsesAWideRange() {
        byte[] data = CloudNoiseGenerator.generate(7L, SIZE);
        for (int c = 0; c < 4; c++) {
            int min = 255, max = 0;
            for (int i = c; i < data.length; i += 4) {
                int v = data[i] & 0xFF;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            assertTrue(max - min > 80, "Channel " + c + " has insufficient range: " + min + ".." + max);
        }
    }

    /** Verifies that opposite texture edges meet without a visible noise discontinuity. */
    @Test
    void textureTilesWithoutSeam() {
        byte[] data = CloudNoiseGenerator.generate(123L, SIZE);
        for (int c = 0; c < 4; c++) {
            double seamX = 0, innerX = 0, seamY = 0, innerY = 0;
            for (int i = 0; i < SIZE; i++) {
                seamX += Math.abs(value(data, 0, i, c) - value(data, SIZE - 1, i, c));
                innerX += Math.abs(value(data, SIZE / 2, i, c) - value(data, SIZE / 2 - 1, i, c));
                seamY += Math.abs(value(data, i, 0, c) - value(data, i, SIZE - 1, c));
                innerY += Math.abs(value(data, i, SIZE / 2, c) - value(data, i, SIZE / 2 - 1, c));
            }
            // The edge behaves like any other pair of neighboring pixels.
            assertTrue(seamX <= innerX * 3 + SIZE, "Seam along X in channel " + c);
            assertTrue(seamY <= innerY * 3 + SIZE, "Seam along Y in channel " + c);
        }
    }

    /** Verifies that wind direction is deterministic, seed-dependent, and normalized to one turn. */
    @Test
    void windDirectionIsDeterministicAndSeedDependent() {
        assertEquals(CloudNoiseGenerator.windAngle(5L), CloudNoiseGenerator.windAngle(5L));
        assertNotEquals(CloudNoiseGenerator.windAngle(5L), CloudNoiseGenerator.windAngle(6L));
        double a = CloudNoiseGenerator.windAngle(-99L);
        assertTrue(a >= 0 && a < Math.PI * 2);
    }

    /** Verifies that unsupported texture dimensions are rejected. */
    @Test
    void invalidSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudNoiseGenerator.generate(1L, 100));
        assertThrows(IllegalArgumentException.class, () -> CloudNoiseGenerator.generate(1L, 128));
        assertThrows(IllegalArgumentException.class, () -> CloudNoiseGenerator.generate(1L, 4096));
    }

    private static int value(byte[] data, int x, int y, int channel) {
        return data[(y * SIZE + x) * 4 + channel] & 0xFF;
    }
}