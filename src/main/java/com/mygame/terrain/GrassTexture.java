package com.mygame.terrain;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Atlas 2×2 de motifs de touffe (4 variantes : le shader en choisit une par plan, donc deux
 * touffes voisines ne se ressemblent pas). Chaque case contient plusieurs brins courbes et effilés ;
 * v = 0 à la base, v = 1 au sommet. L'alpha porte la découpe ; la couleur est une légère variation
 * par brin (frais ↔ plus vieux et jaune), multipliée dans le shader par la teinte de la touffe.
 * Contenu fixe (indépendant de la seed du monde).
 *
 * <p>Chaque motif a aussi 1 à 3 tiges fines et hautes qui se terminent par une tête de fleur. La tête
 * n'est PAS dans l'atlas de couleur : elle est dans une seconde texture alignée (R = pétales, G = cœur),
 * pour que le shader ne l'affiche que sur les touffes fleuries (et la colore selon leur palette).
 *
 * <p>Les brins restent à distance des bords de leur case (racines dans [0,12 ; 0,88], pointes sous
 * 0,94) pour que les mipmaps ne mélangent pas les cases voisines.
 */
final class GrassTexture {
    static final int CELL = 128;
    static final int ATLAS = CELL * 2;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final long PATTERN_SEED = 0x6A09E667L;

    /** Paramètres d'un motif : nombre de brins, largeur de base, hauteur minimale, inclinaison max. */
    private record Pattern(int blades, float width, float minHeight, float lean) { }

    private static final Pattern[] PATTERNS = {
            new Pattern(9, 0.030f, 0.55f, 0.34f),    // touffe classique
            new Pattern(6, 0.044f, 0.62f, 0.26f),    // larges brins, plus rigides
            new Pattern(13, 0.022f, 0.50f, 0.42f),   // fins, nombreux, retombants
            new Pattern(8, 0.034f, 0.34f, 0.50f),    // mélange de brins courts et très penchés
    };

    /** Tiges fleuries par motif. */
    private static final int[] STALKS = {2, 1, 3, 2};
    private static final float HEAD_RADIUS = 7.5f;   // pixels

    private GrassTexture() { }

    /** @return {atlas de touffes, masque des fleurs} : mêmes dimensions, mêmes UV. */
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
                float age = random.nextFloat() * random.nextFloat();       // la plupart des brins sont frais
                float brightness = 0.78f + 0.22f * random.nextFloat();
                drawBlade(alpha, red, green, blue, originX, originY, root, height, halfWidth, lean, curl,
                        brightness, age);
            }
            // Tiges fleuries : fines, hautes ; la tête est dessinée dans le masque, à la pointe.
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
        // Trilinear : jME génère les mipmaps (voir Texture.setMinFilter). Le shader compense
        // l'amincissement de l'alpha aux mips élevés.
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setAnisotropicFilter(4);
        return texture;
    }

    /** Tête de fleur : pétales en lobes (rayon modulé par cos(n·θ)) et cœur circulaire, anti-aliasés. */
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
            float t = v / height;                                // 0 base .. 1 pointe
            float center = root + lean * t * t + curl * (float) Math.sin(t * Math.PI);
            float halfWidth = baseHalfWidth * (float) Math.pow(1f - t, 0.85) + 0.004f;
            for (int x = 0; x < CELL; x++) {
                float u = (x + 0.5f) / CELL;
                float edge = (halfWidth - Math.abs(u - center)) * CELL;   // distance au bord en pixels
                float coverage = Math.max(0f, Math.min(1f, edge + 0.5f));
                if (coverage <= 0f) continue;
                int i = (originY + y) * ATLAS + originX + x;
                float side = (u - center) / halfWidth;               // -1 .. 1 sur la largeur du brin
                // Nervure centrale sombre, flanc gauche plus ombré : le brin paraît plié en V.
                float vein = 1f - 0.14f * Math.max(0f, 1f - Math.abs(side) * 3f);
                float fold = 1f - 0.10f * (side * 0.5f + 0.5f);
                float shade = brightness * vein * fold;
                if (coverage >= alpha[i]) {
                    // Brin plus vieux : moins de bleu, un peu plus de rouge (jaunit).
                    red[i] = shade * (1f + 0.25f * age);
                    green[i] = shade * (1f - 0.04f * age);
                    blue[i] = shade * (1f - 0.40f * age);
                }
                alpha[i] = Math.max(alpha[i], coverage);
            }
        }
    }
}