package com.mygame.texture;

import com.jme3.texture.Texture2D;
import com.mygame.texture.TextureDefinition.Layer;
import com.mygame.texture.TextureDefinition.LayerType;
import com.mygame.texture.TextureDefinition.MapSpec;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Tests deterministic procedural diffuse-texture generation from a seed and definition. */
final class ProceduralTextureGeneratorTest {
    @Test
    void sameSeedProducesIdenticalTexturePixels() {
        TextureDefinition definition = definition();

        byte[] first = pixels(ProceduralTextureGenerator.diffuse(definition, 78123L));
        byte[] second = pixels(ProceduralTextureGenerator.diffuse(definition, 78123L));
        byte[] differentSeed = pixels(ProceduralTextureGenerator.diffuse(definition, 78124L));

        assertArrayEquals(first, second);
        assertFalse(Arrays.equals(first, differentSeed));
    }

    private static TextureDefinition definition() {
        List<Layer> layers = Arrays.asList(
                new Layer(LayerType.PERLIN, 0.6f, 4, 2, 0.5f, 1, 4, 0f),
                new Layer(LayerType.RIPPLE, 0.2f, 1, 1, 0.5f, 6, 8, 0.35f),
                new Layer(LayerType.GRAIN, 0.1f, 1, 1, 0.5f, 1, 1, 0f));
        MapSpec diffuse = new MapSpec(layers, 0.7f, 0.5f, 1f, 1f, 1f, 1f);
        return new TextureDefinition("seed-check", 16, 1f, diffuse, null);
    }

    private static byte[] pixels(Texture2D texture) {
        ByteBuffer data = texture.getImage().getData(0).duplicate();
        byte[] pixels = new byte[data.remaining()];
        data.get(pixels);
        return pixels;
    }
}
