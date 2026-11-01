package com.mygame.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainTextureDefinitionsTest {
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
}
