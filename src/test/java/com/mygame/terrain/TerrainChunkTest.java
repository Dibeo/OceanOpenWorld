package com.mygame.terrain;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
