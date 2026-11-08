package com.mygame.terrain;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests terrain blend weights, adjacent chunk continuity, and deterministic grass generation. */
final class TerrainChunkTest {
    @Test
    void smoothlyBlendsSurfaceWeightsAcrossTransitions() {
        float waterHeight = 12f;

        float[] deep = TerrainChunk.surfaceWeights(7f, waterHeight);
        float[] gravelTransition = TerrainChunk.surfaceWeights(9.5f, waterHeight);
        float[] shoreline = TerrainChunk.surfaceWeights(12f, waterHeight);
        float[] grassTransition = TerrainChunk.surfaceWeights(13.75f, waterHeight);
        float[] grass = TerrainChunk.surfaceWeights(16f, waterHeight);

        assertEquals(1f, deep[2]);
        assertEquals(0.5f, gravelTransition[2], 0.001f);
        assertEquals(1f, shoreline[1]);
        assertEquals(0.5f, grassTransition[0], 0.001f);
        assertEquals(1f, grass[0]);
        for (float[] weights : new float[][] {
                deep, gravelTransition, shoreline, grassTransition, grass
        }) {
            assertEquals(1f, weights[0] + weights[1] + weights[2], 0.001f);
        }
    }

    @Test
    void generatedBlendMapContainsAnRgbWeightForEveryTerrainVertex() {
        int subdivisions = 32;
        TerrainChunk.Data data = TerrainChunk.generate(
                0, 0, new TerrainGenerator(42L), 64, subdivisions, 0f);

        assertEquals(subdivisions + 1, data.blendMapSize);
        assertEquals((subdivisions + 1) * (subdivisions + 1) * 3, data.blendMap.remaining());
    }

    @Test
    void adjacentChunksHaveMatchingBlendWeightsAtTheirSharedEdge() {
        int subdivisions = 32;
        TerrainGenerator generator = new TerrainGenerator(42L);
        TerrainChunk.Data west = TerrainChunk.generate(0, 0, generator, 64, subdivisions, 0f);
        TerrainChunk.Data east = TerrainChunk.generate(1, 0, generator, 64, subdivisions, 0f);
        ByteBuffer westMap = west.blendMap.duplicate();
        ByteBuffer eastMap = east.blendMap.duplicate();

        for (int z = 0; z <= subdivisions; z++) {
            int westEdge = (z * west.blendMapSize + subdivisions) * 3;
            int eastEdge = z * east.blendMapSize * 3;
            for (int channel = 0; channel < 3; channel++) {
                assertEquals(westMap.get(westEdge + channel), eastMap.get(eastEdge + channel));
            }
        }
    }

    @Test
    void grassPlacementIsSeededAndOnlyUsesGrassTerrain() {
        TerrainGenerator generator = new TerrainGenerator(42L);
        TerrainChunk.Data first = null;
        int grassChunkX = 0, grassChunkZ = 0;
        for (int z = -4; z <= 4 && first == null; z++) {
            for (int x = -4; x <= 4; x++) {
                TerrainChunk.Data candidate = TerrainChunk.generate(x, z, generator, 64, 32, 0f);
                if (candidate.grass.indexCount > 0) {
                    first = candidate;
                    grassChunkX = x;
                    grassChunkZ = z;
                    break;
                }
            }
        }
        assertNotNull(first, "The sampled world area should contain grassy terrain");
        TerrainChunk.Data second = TerrainChunk.generate(
                grassChunkX, grassChunkZ, new TerrainGenerator(42L), 64, 32, 0f);

        assertEquals(first.grass.positions, second.grass.positions);
        assertEquals(first.grass.uvs, second.grass.uvs);

        FloatBuffer positions = first.grass.positions.duplicate();
        while (positions.hasRemaining()) {
            float y = positions.get(positions.position() + 1);
            assertTrue(y > 0.5f, "Grass bases must lie on the grassy side of the shoreline");
            positions.position(positions.position() + 3);
        }
    }

    @Test
    void grassIsOmittedWhenTheChunkHasNoGrassSurface() {
        TerrainChunk.Data data = TerrainChunk.generate(
                0, 0, new TerrainGenerator(42L), 64, 32, 1000f);

        assertEquals(0, data.grass.indexCount);
    }
}
