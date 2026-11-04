package com.mygame.terrain;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Texture d'une touffe d'herbe : plusieurs brins courbes et effilés, de hauteurs différentes.
 * v = 0 à la base, v = 1 au sommet. Le canal alpha porte la découpe ; la couleur n'est qu'une
 * légère variation de luminosité par brin (la teinte vient du shader et des sommets).
 * Le contenu est fixe (indépendant de la seed du monde).
 */
final class GrassTexture {
    private static final int SIZE = 128;
    private static final int BLADES = 9;
    private static final long PATTERN_SEED = 0x6A09E667L;

    private GrassTexture() { }

    static Texture2D createTuftTexture() {
        float[] alpha = new float[SIZE * SIZE];
        float[] shade = new float[SIZE * SIZE];
        java.util.Arrays.fill(shade, 0.8f);

        Random random = new Random(PATTERN_SEED);
        for (int i = 0; i < BLADES; i++) {
            // Les brins sont répartis sur la largeur, légèrement décalés au hasard.
            float root = 0.10f + 0.80f * (i + random.nextFloat() * 0.8f) / BLADES;
            float height = 0.55f + 0.45f * random.nextFloat();
            float baseHalfWidth = 0.030f + 0.018f * random.nextFloat();
            float lean = (random.nextFloat() - 0.5f) * 0.34f;
            float curl = (random.nextFloat() - 0.5f) * 0.14f;
            float brightness = 0.78f + 0.22f * random.nextFloat();
            drawBlade(alpha, shade, root, height, baseHalfWidth, lean, curl, brightness);
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        for (int i = 0; i < SIZE * SIZE; i++) {
            int value = Math.round(Math.min(1f, shade[i]) * 255f);
            pixels.put((byte) value).put((byte) value).put((byte) value)
                    .put((byte) Math.round(Math.min(1f, alpha[i]) * 255f));
        }
        pixels.flip();

        Texture2D texture = new Texture2D(new Image(Image.Format.RGBA8, SIZE, SIZE, pixels, ColorSpace.Linear));
        texture.setWrap(Texture.WrapMode.Clamp);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        // Trilinear : jME génère les mipmaps (voir Texture.setMinFilter). Le shader compense
        // l'amincissement de l'alpha aux mips élevés.
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setAnisotropicFilter(4);
        return texture;
    }

    private static void drawBlade(float[] alpha, float[] shade, float root, float height,
                                  float baseHalfWidth, float lean, float curl, float brightness) {
        for (int y = 0; y < SIZE; y++) {
            float v = (y + 0.5f) / SIZE;
            if (v > height) break;
            float t = v / height;                              // 0 base .. 1 pointe
            float center = root + lean * t * t + curl * (float) Math.sin(t * Math.PI);
            float halfWidth = baseHalfWidth * (float) Math.pow(1f - t, 0.85) + 0.004f;
            for (int x = 0; x < SIZE; x++) {
                float u = (x + 0.5f) / SIZE;
                float edge = (halfWidth - Math.abs(u - center)) * SIZE;   // distance au bord en pixels
                float coverage = Math.max(0f, Math.min(1f, edge + 0.5f));
                if (coverage <= 0f) continue;
                int i = y * SIZE + x;
                // Nervure centrale un peu plus sombre.
                float vein = 1f - 0.12f * Math.max(0f, 1f - Math.abs(u - center) / halfWidth * 3f);
                if (coverage >= alpha[i]) shade[i] = brightness * vein;
                alpha[i] = Math.max(alpha[i], coverage);
            }
        }
    }
}