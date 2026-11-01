package com.mygame.texture;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.mygame.noise.PerlinNoise;
import com.mygame.texture.TextureDefinition.Layer;
import com.mygame.texture.TextureDefinition.MapSpec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;

/**
 * Génère des textures sans couture à partir d'une {@link TextureDefinition} et de la seed du monde.
 * Les mipmaps sont fournies directement (aucun appel à l'API interne d'Image).
 * Déterministe : même définition + même seed = même texture. Utilisable hors thread de rendu.
 */
public final class ProceduralTextureGenerator {
    private ProceduralTextureGenerator() {}

    public static Texture2D diffuse(TextureDefinition def, long worldSeed) {
        MapSpec spec = require(def.diffuse, def, "diffuse");
        int size = def.size;
        float[] h = heightField(spec, size, textureSeed(worldSeed, def.name, 'd'));
        byte[] base = new byte[size * size * 3];
        int k = 0;
        for (float v : h) {
            float value = spec.base + spec.contrast * v;
            base[k++] = toByte(value * spec.r);
            base[k++] = toByte(value * spec.g);
            base[k++] = toByte(value * spec.b);
        }
        return new Texture2D(buildImage(base, size, false));
    }

    public static Texture2D normal(TextureDefinition def, long worldSeed) {
        MapSpec spec = require(def.normal, def, "normal");
        int size = def.size;
        float[] h = heightField(spec, size, textureSeed(worldSeed, def.name, 'n'));
        byte[] base = new byte[size * size * 3];
        int k = 0;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                // différences centrées avec bouclage : la normal map reste sans couture
                float hx = h[j * size + (i + 1) % size] - h[j * size + (i + size - 1) % size];
                float hy = h[((j + 1) % size) * size + i] - h[((j + size - 1) % size) * size + i];
                float nx = -hx * spec.strength, ny = -hy * spec.strength, nz = 1f;
                float inv = 1f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                base[k++] = encode(nx * inv);
                base[k++] = encode(ny * inv);
                base[k++] = encode(nz * inv);
            }
        }
        return new Texture2D(buildImage(base, size, true));
    }

    private static MapSpec require(MapSpec spec, TextureDefinition def, String what) {
        if (spec == null) throw new IllegalStateException("La texture '" + def.name + "' n'a pas de <" + what + ">");
        return spec;
    }

    /** Seed propre à chaque texture et à chaque carte, dérivée de la seed du monde. */
    private static long textureSeed(long worldSeed, String name, char map) {
        return worldSeed ^ (name.hashCode() * 0x9E3779B97F4A7C15L) ^ (map * 0xC2B2AE3D27D4EB4FL);
    }

    private static float[] heightField(MapSpec spec, int size, long seed) {
        float[] h = new float[size * size];
        int index = 0;
        for (Layer l : spec.layers) {
            long layerSeed = seed + (index++) * 7919L;
            switch (l.type) {
                case PERLIN: addPerlin(h, size, l, new PerlinNoise(layerSeed)); break;
                case RIPPLE: addRipple(h, size, l, new PerlinNoise(layerSeed)); break;
                case GRAIN:  addGrain(h, l, new Random(layerSeed)); break;
            }
        }
        return h;
    }

    private static void addPerlin(float[] h, int size, Layer l, PerlinNoise noise) {
        double sum = 0, a = 1;
        for (int o = 0; o < l.octaves; o++) { sum += a; a *= l.persistence; }
        for (int j = 0; j < size; j++) {
            double v = (double) j / size;
            for (int i = 0; i < size; i++) {
                double u = (double) i / size, total = 0, amp = 1;
                for (int o = 0; o < l.octaves; o++) {
                    int c = l.cells << o; // période = nombre de cellules : bouclage exact
                    total += noise.noise(u * c, v * c, c, c) * amp;
                    amp *= l.persistence;
                }
                h[j * size + i] += (float) (total / sum * l.amplitude);
            }
        }
    }

    private static void addRipple(float[] h, int size, Layer l, PerlinNoise noise) {
        for (int j = 0; j < size; j++) {
            double v = (double) j / size;
            for (int i = 0; i < size; i++) {
                double u = (double) i / size;
                double warp = noise.noise(u * l.warpCells, v * l.warpCells, l.warpCells, l.warpCells);
                double s = 0.5 * Math.sin(2 * Math.PI * (u * l.frequency + l.warpAmount * warp * l.frequency));
                h[j * size + i] += (float) (s * l.amplitude);
            }
        }
    }

    private static void addGrain(float[] h, Layer l, Random rnd) {
        for (int i = 0; i < h.length; i++) h[i] += (rnd.nextFloat() - 0.5f) * l.amplitude;
    }

    private static byte toByte(float v) { return (byte) (Math.max(0f, Math.min(1f, v)) * 255f + 0.5f); }
    private static byte encode(float n) { return (byte) ((n * 0.5f + 0.5f) * 255f); }

    /**
     * Image RGB8 avec toute la chaîne de mipmaps (filtre boîte 2x2).
     * Linear : une carte diffuse ici est un multiplicateur, sans conversion sRGB.
     */
    private static Image buildImage(byte[] base, int size, boolean normalMap) {
        ArrayList<byte[]> levels = new ArrayList<>();
        levels.add(base);
        int w = size;
        byte[] cur = base;
        while (w > 1) {
            cur = downsample(cur, w, normalMap);
            w /= 2;
            levels.add(cur);
        }
        int[] sizes = new int[levels.size()];
        int total = 0;
        for (int i = 0; i < sizes.length; i++) { sizes[i] = levels.get(i).length; total += sizes[i]; }

        ByteBuffer all = BufferUtils.createByteBuffer(total);
        for (byte[] level : levels) all.put(level);
        all.flip();

        ArrayList<ByteBuffer> data = new ArrayList<>(1);
        data.add(all);
        return new Image(Image.Format.RGB8, size, size, 0, data, sizes, ColorSpace.Linear);
    }

    private static byte[] downsample(byte[] src, int w, boolean normalMap) {
        int nw = w / 2;
        byte[] dst = new byte[nw * nw * 3];
        float[] acc = new float[3];
        for (int y = 0; y < nw; y++) {
            for (int x = 0; x < nw; x++) {
                acc[0] = acc[1] = acc[2] = 0f;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int s = ((2 * y + dy) * w + (2 * x + dx)) * 3;
                        for (int c = 0; c < 3; c++) acc[c] += (src[s + c] & 0xFF) / 255f;
                    }
                }
                int d = (y * nw + x) * 3;
                if (normalMap) { // moyenne des vecteurs décodés, puis renormalisation
                    float nx = acc[0] / 4f * 2f - 1f, ny = acc[1] / 4f * 2f - 1f, nz = acc[2] / 4f * 2f - 1f;
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len < 1e-5f) { nx = 0; ny = 0; nz = 1; len = 1; }
                    dst[d] = encode(nx / len); dst[d + 1] = encode(ny / len); dst[d + 2] = encode(nz / len);
                } else {
                    for (int c = 0; c < 3; c++) dst[d + c] = (byte) (acc[c] / 4f * 255f + 0.5f);
                }
            }
        }
        return dst;
    }
}