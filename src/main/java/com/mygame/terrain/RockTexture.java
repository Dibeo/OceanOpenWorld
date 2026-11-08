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
 * Creates a seamless, seed-dependent RGBA noise texture used by the triplanar rock shader.
 * Red stores broad low-frequency tone variation, green stores fine high-frequency grain,
 * and blue stores crack and relief information. The shader also uses the texture as a height
 * map, deriving relief without tangents.
 */
final class RockTexture {
    private static final int SIZE = 256;

    private RockTexture() { }

    /**
     * Generates the rock noise texture for a world seed.
     *
     * @param seed world seed
     * @return repeating 256-by-256 linear-space RGBA texture
     */
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
                // Inverted absolute noise creates fine crack ridges where the noise reaches zero.
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
        texture.setMinFilter(Texture.MinFilter.Trilinear);   // jME generates mipmaps
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