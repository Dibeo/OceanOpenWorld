package com.mygame.texture;

import com.jme3.texture.Texture;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Façade : charge textures.xml (classpath/resources) et fournit les textures générées
 * pour la seed courante. Les textures sont mises en cache jusqu'au prochain {@link #setSeed}.
 */
public final class TextureLibrary {
    private final Map<String, TextureDefinition> definitions;
    private final Map<String, Texture> cache = new HashMap<>();
    private long seed;

    private TextureLibrary(Map<String, TextureDefinition> definitions) {
        this.definitions = definitions;
    }

    /** @param resource chemin classpath, ex. "/textures.xml" (src/main/resources/textures.xml) */
    public static TextureLibrary fromClasspath(String resource) {
        try (InputStream in = TextureLibrary.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Ressource introuvable : " + resource);
            return new TextureLibrary(TextureDefinitionLoader.load(in, resource));
        } catch (IOException e) {
            throw new IllegalStateException("Lecture de " + resource + " impossible : " + e.getMessage(), e);
        }
    }

    /** Change la seed du monde : les textures seront régénérées à la prochaine demande. */
    public void setSeed(long seed) {
        this.seed = seed;
        cache.clear();
    }

    public Texture diffuse(String name) {
        return cache.computeIfAbsent(name + ":d", k -> configure(ProceduralTextureGenerator.diffuse(def(name), seed)));
    }

    public Texture normal(String name) {
        return cache.computeIfAbsent(name + ":n", k -> configure(ProceduralTextureGenerator.normal(def(name), seed)));
    }

    public float getTileMeters(String name) { return def(name).tileMeters; }

    private TextureDefinition def(String name) {
        TextureDefinition d = definitions.get(name);
        if (d == null) throw new IllegalArgumentException("Texture inconnue dans textures.xml : " + name);
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