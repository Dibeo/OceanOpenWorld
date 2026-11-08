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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests deterministic procedural diffuse-texture generation from a seed and definition. */
final class ProceduralTextureGeneratorTest {
    /** Verifies that identical definitions and seeds generate identical pixels. */
    @Test
    void sameSeedProducesIdenticalTexturePixels() {
        TextureDefinition definition = definition();

        byte[] first = pixels(ProceduralTextureGenerator.diffuse(definition, 78123L));
        byte[] second = pixels(ProceduralTextureGenerator.diffuse(definition, 78123L));
        byte[] differentSeed = pixels(ProceduralTextureGenerator.diffuse(definition, 78124L));

        assertArrayEquals(first, second);
        assertFalse(Arrays.equals(first, differentSeed));
    }

    /** Verifies that diffuse and normal maps have RGB8 images with complete mip chains. */
    @Test
    void generatesRgbImagesWithMipChains() {
        TextureDefinition definition = definitionWithBothMaps();
        Texture2D diffuse = ProceduralTextureGenerator.diffuse(definition, 9L);
        Texture2D normal = ProceduralTextureGenerator.normal(definition, 9L);

        for (Texture2D texture : new Texture2D[] { diffuse, normal }) {
            assertEquals(16, texture.getImage().getWidth());
            assertEquals(16, texture.getImage().getHeight());
            assertEquals(com.jme3.texture.Image.Format.RGB8, texture.getImage().getFormat());
            assertEquals(5, texture.getImage().getMipMapSizes().length);
        }
    }

    /** Verifies that generated normal-map texels decode to near-unit vectors. */
    @Test
    void generatedNormalMapContainsNormalizedVectors() {
        byte[] pixels = pixels(ProceduralTextureGenerator.normal(definitionWithBothMaps(), 99L));

        for (int i = 0; i < pixels.length; i += 3) {
            double x = ((pixels[i] & 0xFF) / 255.0) * 2.0 - 1.0;
            double y = ((pixels[i + 1] & 0xFF) / 255.0) * 2.0 - 1.0;
            double z = ((pixels[i + 2] & 0xFF) / 255.0) * 2.0 - 1.0;
            double length = Math.sqrt(x * x + y * y + z * z);
            assertEquals(1.0, length, 0.02);
        }
    }

    /** Verifies that requesting a map absent from a texture definition fails explicitly. */
    @Test
    void rejectsDefinitionsWithoutRequestedMap() {
        TextureDefinition definition = definition();

        assertThrows(IllegalStateException.class, () -> ProceduralTextureGenerator.normal(definition, 1L));
    }

    private static TextureDefinition definition() {
        List<Layer> layers = Arrays.asList(
                new Layer(LayerType.PERLIN, 0.6f, 4, 2, 0.5f, 1, 4, 0f),
                new Layer(LayerType.RIPPLE, 0.2f, 1, 1, 0.5f, 6, 8, 0.35f),
                new Layer(LayerType.GRAIN, 0.1f, 1, 1, 0.5f, 1, 1, 0f));
        MapSpec diffuse = new MapSpec(layers, 0.7f, 0.5f, 1f, 1f, 1f, 1f);
        return new TextureDefinition("seed-check", 16, 1f, diffuse, null);
    }

    private static TextureDefinition definitionWithBothMaps() {
        TextureDefinition diffuseOnly = definition();
        MapSpec normal = new MapSpec(
                List.of(new Layer(LayerType.PERLIN, 0.5f, 4, 2, 0.5f, 1, 4, 0f)),
                0f, 1f, 6f, 1f, 1f, 1f);
        return new TextureDefinition(diffuseOnly.name, diffuseOnly.size, diffuseOnly.tileMeters,
                diffuseOnly.diffuse, normal);
    }

    private static byte[] pixels(Texture2D texture) {
        ByteBuffer data = texture.getImage().getData(0).duplicate();
        byte[] pixels = new byte[data.remaining()];
        data.get(pixels);
        return pixels;
    }
}
