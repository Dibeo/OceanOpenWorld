package com.mygame.texture;

import java.util.Collections;
import java.util.List;

/**
 * Immutable description of a procedural terrain texture loaded from {@code textures.xml}.
 *
 * <p>Definitions are created by the package's XML loader and consumed by
 * {@link ProceduralTextureGenerator}.
 */
public final class TextureDefinition {

    /** Supported noise contributions that can be combined in a diffuse or normal map. */
    public enum LayerType {
        /** Seeded Perlin noise with configurable frequency octaves. */
        PERLIN,
        /** Periodic sinusoidal ridges with optional Perlin warping. */
        RIPPLE,
        /** Seeded per-pixel random contribution. */
        GRAIN
    }

    /** Immutable settings for one XML {@code <layer>} contribution. */
    public static final class Layer {
        /** Noise contribution type. */
        public final LayerType type;
        /** Contribution scale; its effect is interpreted by the selected layer type. */
        public final float amplitude;
        /** Perlin base cell count; used only by {@link LayerType#PERLIN}. */
        public final int cells;
        /** Number of Perlin noise octaves; used only by {@link LayerType#PERLIN}. */
        public final int octaves;
        /** Perlin amplitude multiplier between octaves; used only by {@link LayerType#PERLIN}. */
        public final float persistence;
        /** Ripple cycles per tile; used only by {@link LayerType#RIPPLE}. */
        public final int frequency;
        /** Perlin warp cell count; used only by {@link LayerType#RIPPLE}. */
        public final int warpCells;
        /** Ripple warp scale; used only by {@link LayerType#RIPPLE}. */
        public final float warpAmount;

        Layer(LayerType type, float amplitude, int cells, int octaves, float persistence,
              int frequency, int warpCells, float warpAmount) {
            this.type = type; this.amplitude = amplitude; this.cells = cells; this.octaves = octaves;
            this.persistence = persistence; this.frequency = frequency;
            this.warpCells = warpCells; this.warpAmount = warpAmount;
        }
    }

    /** Settings and ordered noise layers for one diffuse or normal map. */
    public static final class MapSpec {
        /** Ordered layer contributions; exposed as an unmodifiable list. */
        public final List<Layer> layers;
        /** Constant base value for diffuse generation. */
        public final float base;
        /** Multiplier applied to the summed diffuse layers. */
        public final float contrast;
        /** Scale applied to height differences when deriving normal vectors. */
        public final float strength;
        /** Red-channel diffuse multiplier. */
        public final float r;
        /** Green-channel diffuse multiplier. */
        public final float g;
        /** Blue-channel diffuse multiplier. */
        public final float b;

        MapSpec(List<Layer> layers, float base, float contrast, float strength, float r, float g, float b) {
            this.layers = Collections.unmodifiableList(layers);
            this.base = base; this.contrast = contrast; this.strength = strength;
            this.r = r; this.g = g; this.b = b;
        }
    }

    /** Unique catalog key used to request this texture. */
    public final String name;
    /** Square output image dimension in pixels. */
    public final int size;
    /** World-space metres covered by one repetition of this texture. */
    public final float tileMeters;
    /** Diffuse map settings, or {@code null} if the catalog entry omits that map. */
    public final MapSpec diffuse; // can be null
    /** Normal map settings, or {@code null} if the catalog entry omits that map. */
    public final MapSpec normal;  // can be null

    TextureDefinition(String name, int size, float tileMeters, MapSpec diffuse, MapSpec normal) {
        this.name = name; this.size = size; this.tileMeters = tileMeters;
        this.diffuse = diffuse; this.normal = normal;
    }
}