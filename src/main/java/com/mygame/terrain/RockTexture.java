package com.mygame.terrain;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.mygame.noise.PerlinNoise;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

/**
 * Texture de roche tileable, projetée en triplanaire par le shader (aucune UV sur les rochers).
 * Entièrement issue du Perlin périodique de la seed du monde :
 * <pre>
 *  R : grandes taches de ton        (fBm basse fréquence)
 *  G : grain fin                    (fBm haute fréquence)
 *  B : fissures et reliefs          (|Perlin| inversé : lignes sombres fines)
 * </pre>
 * Le shader s'en sert aussi comme carte de hauteur pour un relief sans tangentes.
 */
final class RockTexture {
    private static final int SIZE = 256;

    private RockTexture() { }

    static Texture2D create(long seed) {
        PerlinNoise low = new PerlinNoise(seed + 0x7A01L);
        PerlinNoise high = new PerlinNoise(seed + 0x7A02L);
        PerlinNoise crack = new PerlinNoise(seed + 0x7A03L);

        byte[] data = new byte[SIZE * SIZE * 4];
        IntStream.range(0, SIZE).parallel().forEach(y -> {
            for (int x = 0; x < SIZE; x++) {
                double u = x / (double) SIZE, v = y / (double) SIZE;
                double a = fbm(low, u, v, 6, 4, 0.5);
                double b = fbm(high, u, v, 32, 3, 0.55);
                double c = 0;
                // Fissures : 1 - |n| donne des crêtes fines là où le bruit s'annule.
                double amplitude = 1, sum = 0;
                for (int o = 0; o < 3; o++) {
                    int cells = 10 << o;
                    c += (1.0 - Math.abs(crack.noise(u * cells, v * cells, cells, cells)) * 2.2) * amplitude;
                    sum += amplitude;
                    amplitude *= 0.5;
                }
                c /= sum;
                int i = (y * SIZE + x) * 4;
                data[i] = toByte(0.5 + a * 0.9);
                data[i + 1] = toByte(0.5 + b * 0.9);
                data[i + 2] = toByte(c);
                data[i + 3] = (byte) 255;
            }
        });

        ByteBuffer pixels = BufferUtils.createByteBuffer(data.length);
        pixels.put(data).flip();
        Texture2D texture = new Texture2D(new Image(Image.Format.RGBA8, SIZE, SIZE, pixels, ColorSpace.Linear));
        texture.setWrap(Texture.WrapMode.Repeat);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setMinFilter(Texture.MinFilter.Trilinear);   // jME génère les mipmaps
        texture.setAnisotropicFilter(4);
        return texture;
    }

    private static double fbm(PerlinNoise noise, double u, double v, int baseCells, int octaves, double persistence) {
        double total = 0, amplitude = 1, sum = 0;
        for (int o = 0; o < octaves; o++) {
            int cells = baseCells << o;
            total += noise.noise(u * cells, v * cells, cells, cells) * amplitude;
            sum += amplitude;
            amplitude *= persistence;
        }
        return total / sum;
    }

    private static byte toByte(double v) {
        return (byte) Math.round(Math.max(0.0, Math.min(1.0, v)) * 255.0);
    }
}