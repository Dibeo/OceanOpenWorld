package com.mygame.terrain;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Builds a fixed 2-by-2 atlas of grass-blade patterns and its aligned flower-mask texture.
 * Each cell contains curved, tapered blades with an alpha mask and subtle age-based color
 * variation. Shaders select among four patterns per plane, so neighboring clumps can differ.
 *
 * <p>Each pattern also contains one to three flower stalks. Petals and centers are stored in
 * the second texture (red and green channels) so shaders can show flowers only on marked
 * clumps and apply the selected palette. Blades stay away from cell edges to prevent mipmaps
 * from blending neighboring patterns.
 */
final class GrassTexture {
    static final int CELL = 128;
    static final int ATLAS = CELL * 2;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final long PATTERN_SEED = 0x6A09E667L;

    /** Number of blades, base width, minimum height, and maximum lean for a pattern. */
    private record Pattern(int blades, float width, float minHeight, float lean) { }

    private static final Pattern[] PATTERNS = {
            new Pattern(9, 0.030f, 0.55f, 0.34f),    // classic clump
            new Pattern(6, 0.044f, 0.62f, 0.26f),    // broad, stiffer blades
            new Pattern(13, 0.022f, 0.50f, 0.42f),   // numerous thin, drooping blades
            new Pattern(8, 0.034f, 0.34f, 0.50f),    // short blades with pronounced lean
    };

    /** Number of flower stalks per pattern. */
    private static final int[] STALKS = {2, 1, 3, 2};
    private static final float HEAD_RADIUS = 7.5f;   // pixels

    private GrassTexture() { }

    /**
     * Creates the grass color atlas and matching flower mask with identical dimensions and UVs.
     *
     * @return two same-sized textures in order: grass blades, then flower mask
     */
    static Texture2D[] create() {
        float[] alpha = new float[ATLAS * ATLAS];
        float[] petals = new float[ATLAS * ATLAS], centers = new float[ATLAS * ATLAS];
        float[] red = new float[ATLAS * ATLAS], green = new float[ATLAS * ATLAS], blue = new float[ATLAS * ATLAS];
        java.util.Arrays.fill(red, 0.8f);
        java.util.Arrays.fill(green, 0.8f);
        java.util.Arrays.fill(blue, 0.8f);

        Random random = new Random(PATTERN_SEED);
        for (int variant = 0; variant < PATTERNS.length; variant++) {
            Pattern pattern = PATTERNS[variant];
            int originX = (variant % 2) * CELL, originY = (variant / 2) * CELL;
            for (int i = 0; i < pattern.blades(); i++) {
                float root = 0.12f + 0.76f * (i + random.nextFloat() * 0.8f) / pattern.blades();
                float height = pattern.minHeight() + (0.94f - pattern.minHeight()) * random.nextFloat();
                float halfWidth = pattern.width() * (0.8f + 0.5f * random.nextFloat());
                float lean = (random.nextFloat() - 0.5f) * pattern.lean();
                float curl = (random.nextFloat() - 0.5f) * 0.14f;
                float age = random.nextFloat() * random.nextFloat();       // most blades are fresh
                float brightness = 0.78f + 0.22f * random.nextFloat();
                drawBlade(alpha, red, green, blue, originX, originY, root, height, halfWidth, lean, curl,
                        brightness, age);
            }
            // Flower stalks are thin and tall; draw each flower head in the mask at its tip.
            for (int i = 0; i < STALKS[variant]; i++) {
                float root = 0.20f + 0.60f * random.nextFloat();
                float tipV = 0.68f + 0.18f * random.nextFloat();
                float lean = (random.nextFloat() - 0.5f) * 0.16f;
                float curl = (random.nextFloat() - 0.5f) * 0.06f;
                drawBlade(alpha, red, green, blue, originX, originY, root, tipV, 0.014f, lean, curl, 0.82f, 0.1f);
                int petalCount = 5 + (variant + i) % 3;
                drawHead(petals, centers, originX + (root + lean) * CELL, originY + tipV * CELL,
                        petalCount, random.nextFloat() * TWO_PI);
            }
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(ATLAS * ATLAS * 4);
        for (int i = 0; i < ATLAS * ATLAS; i++) {
            pixels.put(toByte(red[i])).put(toByte(green[i])).put(toByte(blue[i])).put(toByte(alpha[i]));
        }
        pixels.flip();

        ByteBuffer flowerPixels = BufferUtils.createByteBuffer(ATLAS * ATLAS * 4);
        for (int i = 0; i < ATLAS * ATLAS; i++) {
            flowerPixels.put(toByte(petals[i])).put(toByte(centers[i])).put((byte) 0).put((byte) 255);
        }
        flowerPixels.flip();
        return new Texture2D[] {atlasTexture(pixels), atlasTexture(flowerPixels)};
    }

    private static Texture2D atlasTexture(ByteBuffer pixels) {
        Texture2D texture = new Texture2D(new Image(Image.Format.RGBA8, ATLAS, ATLAS, pixels, ColorSpace.Linear));
        texture.setWrap(Texture.WrapMode.Clamp);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        // Trilinear filtering makes jME generate mipmaps (see Texture.setMinFilter). The shader
        // compensates for alpha becoming thinner at higher mip levels.
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setAnisotropicFilter(4);
        return texture;
    }

    /** Draws an antialiased flower head with cosine-modulated petals and a circular center. */
    private static void drawHead(float[] petals, float[] centers, float cx, float cy, int petalCount, float phase) {
        int r = (int) Math.ceil(HEAD_RADIUS) + 1;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int x = Math.round(cx) + dx, y = Math.round(cy) + dy;
                if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) continue;
                float px = x + 0.5f - cx, py = y + 0.5f - cy;
                float dist = (float) Math.sqrt(px * px + py * py);
                float theta = (float) Math.atan2(py, px);
                float edge = HEAD_RADIUS * (0.74f + 0.26f * (float) Math.cos(petalCount * theta + phase));
                float petal = Math.max(0f, Math.min(1f, edge - dist + 0.5f));
                float center = Math.max(0f, Math.min(1f, HEAD_RADIUS * 0.34f - dist + 0.5f));
                int i = y * ATLAS + x;
                petals[i] = Math.max(petals[i], petal);
                centers[i] = Math.max(centers[i], center);
            }
        }
    }

    private static byte toByte(float v) {
        return (byte) Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
    }

    private static void drawBlade(float[] alpha, float[] red, float[] green, float[] blue,
                                  int originX, int originY, float root, float height, float baseHalfWidth,
                                  float lean, float curl, float brightness, float age) {
        for (int y = 0; y < CELL; y++) {
            float v = (y + 0.5f) / CELL;
            if (v > height) break;
            float t = v / height;                                // 0 at base .. 1 at tip
            float center = root + lean * t * t + curl * (float) Math.sin(t * Math.PI);
            float halfWidth = baseHalfWidth * (float) Math.pow(1f - t, 0.85) + 0.004f;
            for (int x = 0; x < CELL; x++) {
                float u = (x + 0.5f) / CELL;
                float edge = (halfWidth - Math.abs(u - center)) * CELL;   // distance to the edge in pixels
                float coverage = Math.max(0f, Math.min(1f, edge + 0.5f));
                if (coverage <= 0f) continue;
                int i = (originY + y) * ATLAS + originX + x;
                float side = (u - center) / halfWidth;               // -1 .. 1 across the blade width
                // A dark central vein and shaded left side make the blade appear folded into a V.
                float vein = 1f - 0.14f * Math.max(0f, 1f - Math.abs(side) * 3f);
                float fold = 1f - 0.10f * (side * 0.5f + 0.5f);
                float shade = brightness * vein * fold;
                if (coverage >= alpha[i]) {
                    // Older blades have less blue and slightly more red, producing a yellow tint.
                    red[i] = shade * (1f + 0.25f * age);
                    green[i] = shade * (1f - 0.04f * age);
                    blue[i] = shade * (1f - 0.40f * age);
                }
                alpha[i] = Math.max(alpha[i], coverage);
            }
        }
    }
}