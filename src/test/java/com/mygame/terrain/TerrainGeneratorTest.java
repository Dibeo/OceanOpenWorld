package com.mygame.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests world-scale terrain noise configuration. */
final class TerrainGeneratorTest {
    @Test
    void islandNoiseUsesBroaderScaleThanOriginalGeneration() {
        assertTrue(TerrainGenerator.islandFrequency() < 0.006,
                "A lower noise frequency creates larger islands");
        assertTrue(TerrainGenerator.islandFrequency() > 0,
                "Island noise frequency must remain positive");
    }
}
