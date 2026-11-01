package com.mygame.texture;

import java.util.Collections;
import java.util.List;

/** Description immuable d'une texture procédurale, lue depuis textures.xml. */
public final class TextureDefinition {

    public enum LayerType { PERLIN, RIPPLE, GRAIN }

    public static final class Layer {
        public final LayerType type;
        public final float amplitude;
        // perlin
        public final int cells, octaves;
        public final float persistence;
        // ripple
        public final int frequency, warpCells;
        public final float warpAmount;

        Layer(LayerType type, float amplitude, int cells, int octaves, float persistence,
              int frequency, int warpCells, float warpAmount) {
            this.type = type; this.amplitude = amplitude; this.cells = cells; this.octaves = octaves;
            this.persistence = persistence; this.frequency = frequency;
            this.warpCells = warpCells; this.warpAmount = warpAmount;
        }
    }

    /** Paramètres d'une carte (diffuse ou normal) : couches + réglages. */
    public static final class MapSpec {
        public final List<Layer> layers;
        public final float base, contrast, strength, r, g, b;

        MapSpec(List<Layer> layers, float base, float contrast, float strength, float r, float g, float b) {
            this.layers = Collections.unmodifiableList(layers);
            this.base = base; this.contrast = contrast; this.strength = strength;
            this.r = r; this.g = g; this.b = b;
        }
    }

    public final String name;
    public final int size;
    public final float tileMeters;
    public final MapSpec diffuse; // peut être null
    public final MapSpec normal;  // peut être null

    TextureDefinition(String name, int size, float tileMeters, MapSpec diffuse, MapSpec normal) {
        this.name = name; this.size = size; this.tileMeters = tileMeters;
        this.diffuse = diffuse; this.normal = normal;
    }
}