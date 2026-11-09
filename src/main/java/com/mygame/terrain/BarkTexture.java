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
 * Creates a tileable procedural bark texture for tree trunks and branches.
 *
 * <p>The red channel contains elongated vertical furrows, green stores tone variation, and blue
 * stores irregular horizontal marks used to distinguish birch bark. The texture is deterministic
 * for a given seed.
 */
final class BarkTexture {
    private static final int SIZE = 256;

    private BarkTexture() {
    }

    /**
     * Creates a repeat-wrapped bark texture derived from the supplied world seed.
     *
     * @param seed world seed
     * @return generated bark texture
     */
    static Texture2D create(long seed) {
        PerlinNoise furrows = new PerlinNoise(seed + 0x6B01L);
        PerlinNoise tone = new PerlinNoise(seed + 0x6B02L);
        PerlinNoise marks = new PerlinNoise(seed + 0x6B03L);

        byte[] data = new byte[SIZE * SIZE * 4];
        IntStream.range(0, SIZE).parallel().forEach(y -> {
            for (int x = 0; x < SIZE; x++) {
                double u = x / (double) SIZE, v = y / (double) SIZE;
                double ridge = 0, amplitude = 1, sum = 0;
                for (int octave = 0; octave < 3; octave++) {
                    int cellsU = 12 << octave, cellsV = 3 << octave;
                    ridge += (1.0 - Math.abs(furrows.noise(u * cellsU, v * cellsV, cellsU, cellsV)) * 2.0)
                            * amplitude;
                    sum += amplitude;
                    amplitude *= 0.5;
                }
                ridge /= sum;
                double variation = tone.noise(u * 6, v * 2, 6, 2) * 0.6
                        + tone.noise(u * 24, v * 8, 24, 8) * 0.4;
                double birchMarks = marks.noise(u * 4, v * 20, 4, 20)
                        + 0.5 * marks.noise(u * 12, v * 48, 12, 48);
                int index = (y * SIZE + x) * 4;
                data[index] = toByte(ridge);
                data[index + 1] = toByte(0.5 + variation * 1.1);
                data[index + 2] = toByte(smooth(0.18, 0.5, birchMarks));
                data[index + 3] = (byte) 255;
            }
        });
        ByteBuffer pixels = BufferUtils.createByteBuffer(data.length);
        pixels.put(data).flip();
        Texture2D texture = new Texture2D(
                new Image(Image.Format.RGBA8, SIZE, SIZE, pixels, ColorSpace.Linear));
        texture.setWrap(Texture.WrapMode.Repeat);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setAnisotropicFilter(4);
        return texture;
    }

    private static double smooth(double edge0, double edge1, double value) {
        double t = Math.max(0, Math.min(1, (value - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private static byte toByte(double value) {
        return (byte) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0);
    }
}
