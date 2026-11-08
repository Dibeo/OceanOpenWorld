package com.mygame.terrain;

import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

class RockGenerationTest {
    private static final int SIZE = 64, SUB = 32;
    private static final float WATER = 0f;

    private static RockShape.RockData rocks(long seed, int cx, int cz, float density) {
        TerrainGenerator generator = new TerrainGenerator(seed);
        return TerrainChunk.generate(cx, cz, generator, SIZE, SUB, WATER, density, 1f).rocks;
    }

    /** Premier chunk rocheux de la seed, cherché dans une petite zone. */
    private static int[] findRockyChunk(long seed) {
        for (int cz = -10; cz <= 10; cz++) {
            for (int cx = -10; cx <= 10; cx++) {
                if (rocks(seed, cx, cz, 2.5f) != null) return new int[] {cx, cz};
            }
        }
        throw new AssertionError("aucun rocher trouvé");
    }

    private static boolean same(FloatBuffer a, FloatBuffer b) {
        if (a.limit() != b.limit()) return false;
        for (int i = 0; i < a.limit(); i++) if (Float.floatToIntBits(a.get(i)) != Float.floatToIntBits(b.get(i))) return false;
        return true;
    }

    @Test
    void sameSeedAndChunkGiveIdenticalRocks() {
        int[] c = findRockyChunk(1L);
        RockShape.RockData a = rocks(1L, c[0], c[1], 2.5f), b = rocks(1L, c[0], c[1], 2.5f);
        assertTrue(same(a.positions, b.positions), "positions");
        assertTrue(same(a.normals, b.normals), "normales");
        assertTrue(same(a.colors, b.colors), "couleurs");
        assertEquals(a.indexCount, b.indexCount);
    }

    @Test
    void differentSeedsGiveDifferentRocks() {
        int[] c = findRockyChunk(1L);
        RockShape.RockData a = rocks(1L, c[0], c[1], 2.5f), b = rocks(2L, c[0], c[1], 2.5f);
        assertTrue(b == null || !same(a.positions, b.positions));
    }

    @Test
    void zeroDensityDisablesRocks() {
        int[] c = findRockyChunk(1L);
        assertTrue(rocks(1L, c[0], c[1], 0f) == null);
    }

    @Test
    void geometryIsValid() {
        int[] c = findRockyChunk(3L);
        RockShape.RockData r = rocks(3L, c[0], c[1], 2.5f);
        int vertices = r.positions.limit() / 3;
        for (int i = 0; i < r.positions.limit(); i++) assertTrue(Float.isFinite(r.positions.get(i)), "position finie");
        for (int i = 0; i < vertices; i++) {
            float x = r.normals.get(i * 3), y = r.normals.get(i * 3 + 1), z = r.normals.get(i * 3 + 2);
            assertTrue(Math.abs(Math.sqrt(x * x + y * y + z * z) - 1.0) < 1e-3, "normale unitaire");
            float ao = r.colors.get(i * 4 + 3);
            assertTrue(ao >= 0f && ao <= 1.1f, "occlusion dans [0,1]");
        }
        assertTrue(r.indexCount % 3 == 0);
        // Les rochers restent près de leur chunk (taille max ~2,2 m + cailloux).
        assertTrue(r.minX > -6f && r.maxX < SIZE + 6f && r.minZ > -6f && r.maxZ < SIZE + 6f);
    }

    @Test
    void rockShapeDependsOnlyOnItsSeed() {
        com.mygame.noise.PerlinNoise noise = new TerrainGenerator(5L).getRockShapeNoise();
        RockShape.Buffers a = new RockShape.Buffers(), b = new RockShape.Buffers();
        RockShape.add(a, noise, 12345L, 0f, 0f, 0f, 1.2f, 0.5f);
        RockShape.add(b, noise, 12345L, 0f, 0f, 0f, 1.2f, 0.5f);
        assertTrue(same(a.build().positions, b.build().positions));
        RockShape.Buffers c = new RockShape.Buffers();
        RockShape.add(c, noise, 54321L, 0f, 0f, 0f, 1.2f, 0.5f);
        assertFalse(same(a.build().positions, c.build().positions));
    }

    @Test
    void rockTextureIsDeterministic() {
        byte[] a = bytes(RockTexture.create(9L)), b = bytes(RockTexture.create(9L)), c = bytes(RockTexture.create(10L));
        assertArrayEquals(a, b);
        assertFalse(java.util.Arrays.equals(a, c));
    }

    private static byte[] bytes(com.jme3.texture.Texture2D t) {
        java.nio.ByteBuffer bb = t.getImage().getData(0).duplicate();
        bb.rewind();
        byte[] d = new byte[bb.remaining()];
        bb.get(d);
        return d;
    }
}