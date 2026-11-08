package com.mygame.texture;

import com.jme3.texture.Texture;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the procedural texture catalog from the classpath and caches generated textures
 * for the current world seed. The cache is cleared by {@link #setSeed(long)}.
 */
public final class TextureLibrary {
    private final Map<String, TextureDefinition> definitions;
    private final Map<String, Texture> cache = new HashMap<>();
    private long seed;

    private TextureLibrary(Map<String, TextureDefinition> definitions) {
        this.definitions = definitions;
    }

    /**
     * Loads a catalog resource from the classpath.
     *
     * @param resource absolute classpath resource name, such as {@code /textures.xml}
     * @return library backed by the parsed catalog
     * @throws IllegalStateException if the resource is missing or invalid
     */
    public static TextureLibrary fromClasspath(String resource) {
        try (InputStream in = TextureLibrary.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Resource not found: " + resource);
            return new TextureLibrary(TextureDefinitionLoader.load(in, resource));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + resource + ": " + e.getMessage(), e);
        }
    }

    /**
     * Sets the world seed and clears generated texture caches so textures are regenerated on demand.
     *
     * @param seed world seed used by subsequent generation
     */
    public void setSeed(long seed) {
        this.seed = seed;
        cache.clear();
    }

    /**
     * Returns the cached or newly generated diffuse texture for a catalog entry.
     *
     * @param name texture catalog name
     * @return generated texture configured for repeating and mipmapped sampling
     * @throws IllegalArgumentException if the name is not in the catalog
     * @throws IllegalStateException if the definition has no diffuse map
     */
    public Texture diffuse(String name) {
        return cache.computeIfAbsent(name + ":d", k -> configure(ProceduralTextureGenerator.diffuse(def(name), seed)));
    }

    /**
     * Returns the cached or newly generated normal texture for a catalog entry.
     *
     * @param name texture catalog name
     * @return generated texture configured for repeating and mipmapped sampling
     * @throws IllegalArgumentException if the name is not in the catalog
     * @throws IllegalStateException if the definition has no normal map
     */
    public Texture normal(String name) {
        return cache.computeIfAbsent(name + ":n", k -> configure(ProceduralTextureGenerator.normal(def(name), seed)));
    }

    /**
     * Returns the world-space size of one texture repetition.
     *
     * @param name texture catalog name
     * @return tile width and height in metres
     * @throws IllegalArgumentException if the name is not in the catalog
     */
    public float getTileMeters(String name) { return def(name).tileMeters; }

    private TextureDefinition def(String name) {
        TextureDefinition d = definitions.get(name);
        if (d == null) throw new IllegalArgumentException("Unknown texture in textures.xml: " + name);
        return d;
    }

    private static Texture configure(Texture t) {
        t.setWrap(Texture.WrapMode.Repeat);
        t.setMinFilter(Texture.MinFilter.Trilinear);
        t.setMagFilter(Texture.MagFilter.Bilinear);
        t.setAnisotropicFilter(8);
        return t;
    }
}