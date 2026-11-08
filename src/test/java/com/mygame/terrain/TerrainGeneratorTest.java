package com.mygame.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests world-scale terrain noise configuration. */
final class TerrainGeneratorTest {
    /** Verifies that island noise uses a positive frequency appropriate for broad terrain features. */
    @Test
    void islandNoiseUsesBroaderScaleThanOriginalGeneration() {
        assertTrue(TerrainGenerator.islandFrequency() < 0.006,
                "A lower noise frequency creates larger islands");
        assertTrue(TerrainGenerator.islandFrequency() > 0,
                "Island noise frequency must remain positive");
    }

    /** Verifies that terrain fields are deterministic and their documented weights stay bounded. */
    @Test
    void generatedFieldsAreDeterministicAndBounded() {
        TerrainGenerator first = new TerrainGenerator(42L);
        TerrainGenerator second = new TerrainGenerator(42L);
        double x = -123.5, z = 456.75;

        assertTrue(Float.isFinite(first.getHeight(x, z)));
        assertEquals(first.getHeight(x, z), second.getHeight(x, z));
        assertEquals(first.getTint(x, z), second.getTint(x, z));
        assertTrue(first.getGrassDensity(x, z) >= 0f && first.getGrassDensity(x, z) <= 1f);
        assertTrue(first.getGrassTone(x, z) >= 0f && first.getGrassTone(x, z) <= 1f);
        assertTrue(first.getGrassDeepness(x, z) >= 0f && first.getGrassDeepness(x, z) <= 1f);
        assertTrue(first.getGrassDryness(x, z) >= 0f && first.getGrassDryness(x, z) <= 1f);
        assertTrue(first.getFlowerField(x, z) >= 0f && first.getFlowerField(x, z) <= 1f);
        assertTrue(first.getFlowerHue(x, z) >= 0f && first.getFlowerHue(x, z) <= 1f);
        assertTrue(first.getRockField(x, z) >= 0f && first.getRockField(x, z) <= 1f);
    }
}
