package com.mygame.texture;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies texture-catalog parsing, defaults, ordering, and validation rules. */
final class TextureDefinitionLoaderTest {
    /** Verifies that valid definitions are parsed in document order with documented defaults. */
    @Test
    void parsesDefinitionsAndAppliesDefaults() {
        Map<String, TextureDefinition> definitions = load("""
                <textures>
                  <texture name="first">
                    <diffuse><layer type="perlin"/></diffuse>
                  </texture>
                  <texture name="second">
                    <normal><layer type="grain" amplitude="0.25"/></normal>
                  </texture>
                </textures>
                """);

        assertEquals(java.util.List.of("first", "second"), new ArrayList<>(definitions.keySet()));
        TextureDefinition first = definitions.get("first");
        assertEquals(256, first.size);
        assertEquals(4f, first.tileMeters);
        assertEquals(TextureDefinition.LayerType.PERLIN, first.diffuse.layers.getFirst().type);
        assertEquals(0.9f, first.diffuse.base);

        TextureDefinition second = definitions.get("second");
        assertEquals(TextureDefinition.LayerType.GRAIN, second.normal.layers.getFirst().type);
        assertEquals(0.25f, second.normal.layers.getFirst().amplitude);
    }

    /** Verifies that the layer list cannot be modified after parsing. */
    @Test
    void exposesParsedLayersAsAnUnmodifiableList() {
        TextureDefinition definition = load("""
                <textures>
                  <texture name="stable" size="16">
                    <diffuse><layer type="grain"/></diffuse>
                  </texture>
                </textures>
                """).get("stable");

        assertThrows(UnsupportedOperationException.class, () -> definition.diffuse.layers.clear());
    }

    /** Verifies that invalid roots, duplicate names, and definitions without maps are rejected. */
    @Test
    void rejectsInvalidCatalogStructure() {
        assertThrows(IllegalStateException.class, () -> load("<wrong/>"));
        assertThrows(IllegalStateException.class, () -> load("""
                <textures>
                  <texture name="duplicate"><diffuse><layer type="grain"/></diffuse></texture>
                  <texture name="duplicate"><diffuse><layer type="grain"/></diffuse></texture>
                </textures>
                """));
        assertThrows(IllegalStateException.class,
                () -> load("<textures><texture name=\"empty\"/></textures>"));
    }

    /** Verifies that invalid dimensions, empty maps, and unknown layer types are rejected. */
    @Test
    void rejectsInvalidTextureSettings() {
        assertThrows(IllegalStateException.class, () -> load("""
                <textures><texture name="bad-size" size="15">
                  <diffuse><layer type="grain"/></diffuse>
                </texture></textures>
                """));
        assertThrows(IllegalStateException.class, () -> load("""
                <textures><texture name="empty-map" size="16"><diffuse/></texture></textures>
                """));
        assertThrows(IllegalStateException.class, () -> load("""
                <textures><texture name="unknown-layer" size="16">
                  <diffuse><layer type="unknown"/></diffuse>
                </texture></textures>
                """));
    }

    private static Map<String, TextureDefinition> load(String xml) {
        return TextureDefinitionLoader.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "test catalog");
    }
}
