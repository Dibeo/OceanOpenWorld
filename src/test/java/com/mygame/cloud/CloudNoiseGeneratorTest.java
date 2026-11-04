package com.mygame.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloudNoiseGeneratorTest {
    private static final int SIZE = 256;

    @Test
    void sameSeedProducesIdenticalTexture() {
        assertArrayEquals(CloudNoiseGenerator.generate(42L, SIZE), CloudNoiseGenerator.generate(42L, SIZE));
    }

    @Test
    void differentSeedsProduceDifferentTextures() {
        assertFalse(java.util.Arrays.equals(
                CloudNoiseGenerator.generate(1L, SIZE), CloudNoiseGenerator.generate(2L, SIZE)));
    }

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
            assertTrue(max - min > 80, "canal " + c + " trop plat : " + min + ".." + max);
        }
    }

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
            // Le bord se comporte comme n'importe quelle paire de pixels voisins.
            assertTrue(seamX <= innerX * 3 + SIZE, "couture en X, canal " + c);
            assertTrue(seamY <= innerY * 3 + SIZE, "couture en Y, canal " + c);
        }
    }

    @Test
    void windDirectionIsDeterministicAndSeedDependent() {
        assertEquals(CloudNoiseGenerator.windAngle(5L), CloudNoiseGenerator.windAngle(5L));
        assertNotEquals(CloudNoiseGenerator.windAngle(5L), CloudNoiseGenerator.windAngle(6L));
        double a = CloudNoiseGenerator.windAngle(-99L);
        assertTrue(a >= 0 && a < Math.PI * 2);
    }

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