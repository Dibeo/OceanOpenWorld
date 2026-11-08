package com.mygame.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that the resource catalog defines the terrain textures and both map types. */
final class TerrainTextureDefinitionsTest {
    /** Verifies that every terrain surface has valid diffuse and normal maps. */
    @Test
    void xmlDefinesDiffuseAndNormalMapsForEveryTerrainSurface() {
        TextureLibrary textures = TextureLibrary.fromClasspath("/textures.xml");
        textures.setSeed(1234L);

        for (String surface : new String[] { "grass", "sand", "gravel" }) {
            assertTrue(textures.getTileMeters(surface) > 0f);
            assertNotNull(textures.diffuse(surface));
            assertNotNull(textures.normal(surface));
        }
    }

    /** Verifies that generated maps are cached until the world seed changes. */
    @Test
    void cachesGeneratedTexturesAndClearsCacheWhenSeedChanges() {
        TextureLibrary textures = TextureLibrary.fromClasspath("/textures.xml");
        textures.setSeed(1234L);

        var first = textures.diffuse("sand");
        assertSame(first, textures.diffuse("sand"));

        textures.setSeed(5678L);

        assertNotSame(first, textures.diffuse("sand"));
    }

    /** Verifies that requests for texture names outside the catalog fail explicitly. */
    @Test
    void rejectsUnknownSurfaceNames() {
        TextureLibrary textures = TextureLibrary.fromClasspath("/textures.xml");

        assertThrows(IllegalArgumentException.class, () -> textures.getTileMeters("unknown"));
    }
}
