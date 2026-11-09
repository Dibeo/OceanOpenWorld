package com.mygame.terrain;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Builds a deterministic 2x2 atlas of alpha-cutout foliage cards.
 *
 * <p>The cells contain two oak clusters, a birch cluster, and a pine spray, in that order. RGB
 * stores grayscale leaf brightness which the foliage shader multiplies by each tree's vertex tint.
 */
final class LeafTexture {
    private static final int CELL = 128;
    private static final int ATLAS = CELL * 2;
    private static final long SEED = 0x3C6EF372L;

    private LeafTexture() {
    }

    /**
     * Creates the foliage atlas texture.
     *
     * @return clamp-wrapped atlas with filtered mipmaps
     */
    static Texture2D create() {
        float[] alpha = new float[ATLAS * ATLAS];
        float[] shade = new float[ATLAS * ATLAS];
        java.util.Arrays.fill(shade, 0.7f);
        Random random = new Random(SEED);

        drawLeafCluster(alpha, shade, 0, 0, 26, 25f, 14f, random);
        drawLeafCluster(alpha, shade, 1, 0, 15, 32f, 19f, random);
        drawLeafCluster(alpha, shade, 0, 1, 34, 17f, 10f, random);
        drawPineSpray(alpha, shade, 1, 1, random);

        ByteBuffer pixels = BufferUtils.createByteBuffer(ATLAS * ATLAS * 4);
        for (int i = 0; i < ATLAS * ATLAS; i++) {
            byte value = (byte) Math.round(Math.max(0f, Math.min(1f, shade[i])) * 255f);
            pixels.put(value).put(value).put(value)
                    .put((byte) Math.round(Math.min(1f, alpha[i]) * 255f));
        }
        pixels.flip();
        Texture2D texture = new Texture2D(
                new Image(Image.Format.RGBA8, ATLAS, ATLAS, pixels, ColorSpace.Linear));
        texture.setWrap(Texture.WrapMode.Clamp);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setAnisotropicFilter(4);
        return texture;
    }

    /** Draws oval leaves at varied angles inside a roughly circular atlas cell. */
    private static void drawLeafCluster(float[] alpha, float[] shade, int cellX, int cellY, int count,
                                        float length, float width, Random random) {
        float originX = cellX * CELL, originY = cellY * CELL;
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2.0);
            float radius = (CELL * 0.5f - length * 0.55f - 4f) * (float) Math.sqrt(random.nextFloat());
            float theta = random.nextFloat() * (float) (Math.PI * 2.0);
            float centerX = originX + CELL * 0.5f + radius * (float) Math.cos(theta);
            float centerY = originY + CELL * 0.5f + radius * 0.9f * (float) Math.sin(theta);
            float scale = 0.8f + 0.4f * random.nextFloat();
            drawLeaf(alpha, shade, centerX, centerY, angle, length * scale, width * scale,
                    0.62f + 0.38f * random.nextFloat());
        }
    }

    /** Rasterizes a pointed oval leaf with a darker central vein. */
    private static void drawLeaf(float[] alpha, float[] shade, float centerX, float centerY, float angle,
                                 float length, float width, float brightness) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        int radius = (int) Math.ceil(length * 0.55f) + 1;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = Math.round(centerX) + dx, y = Math.round(centerY) + dy;
                if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) continue;
                float px = x + 0.5f - centerX, py = y + 0.5f - centerY;
                float along = px * cos + py * sin, across = -px * sin + py * cos;
                float t = along / length + 0.5f;
                if (t < 0f || t > 1f) continue;
                float halfWidth = width * 0.5f
                        * (float) Math.pow(Math.sin(Math.PI * Math.pow(t, 0.8)), 0.75);
                float coverage = Math.max(0f, Math.min(1f, halfWidth - Math.abs(across) + 0.5f));
                if (coverage <= 0f) continue;
                int index = y * ATLAS + x;
                float vein = 1f - 0.22f * Math.max(0f, 1f - Math.abs(across) * 2.2f);
                float edge = 1f - 0.12f * (Math.abs(across) / Math.max(halfWidth, 0.5f));
                if (coverage >= alpha[index] * 0.9f) shade[index] = brightness * vein * edge;
                alpha[index] = Math.max(alpha[index], coverage);
            }
        }
    }

    /** Draws a central stem and paired needles that spread outward and upward. */
    private static void drawPineSpray(float[] alpha, float[] shade, int cellX, int cellY, Random random) {
        float originX = cellX * CELL, originY = cellY * CELL, centerX = originX + CELL * 0.5f;
        float baseY = originY + 4f, tipY = originY + CELL - 8f;
        drawSegment(alpha, shade, centerX, baseY, centerX + 1.5f, tipY, 1.6f, 0.55f);
        int needles = 34;
        for (int i = 0; i < needles; i++) {
            float fraction = 0.06f + 0.92f * i / needles;
            float startY = baseY + (tipY - baseY) * fraction;
            float startX = centerX + 1.5f * fraction;
            for (int side = -1; side <= 1; side += 2) {
                float spread = (0.9f + 0.5f * random.nextFloat()) * (1.15f - 0.55f * fraction);
                float length = (CELL * 0.46f) * (1.05f - 0.55f * fraction)
                        * (0.8f + 0.4f * random.nextFloat());
                float endX = startX + side * (float) Math.sin(spread) * length;
                float endY = startY + (float) Math.cos(spread) * length * 0.55f;
                drawSegment(alpha, shade, startX, startY, endX, endY, 1.25f,
                        0.62f + 0.38f * random.nextFloat());
            }
        }
    }

    private static void drawSegment(float[] alpha, float[] shade, float x0, float y0, float x1, float y1,
                                    float thickness, float brightness) {
        int minX = (int) Math.floor(Math.min(x0, x1) - thickness - 1);
        int maxX = (int) Math.ceil(Math.max(x0, x1) + thickness + 1);
        int minY = (int) Math.floor(Math.min(y0, y1) - thickness - 1);
        int maxY = (int) Math.ceil(Math.max(y0, y1) + thickness + 1);
        float dx = x1 - x0, dy = y1 - y0;
        float lengthSquared = Math.max(dx * dx + dy * dy, 1e-6f);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) continue;
                float px = x + 0.5f - x0, py = y + 0.5f - y0;
                float t = Math.max(0f, Math.min(1f, (px * dx + py * dy) / lengthSquared));
                float distance = (float) Math.hypot(px - dx * t, py - dy * t);
                float taper = thickness * 0.5f * (1f - 0.55f * t);
                float coverage = Math.max(0f, Math.min(1f, taper - distance + 0.5f));
                if (coverage <= 0f) continue;
                int index = y * ATLAS + x;
                if (coverage >= alpha[index]) shade[index] = brightness;
                alpha[index] = Math.max(alpha[index], coverage);
            }
        }
    }
}
