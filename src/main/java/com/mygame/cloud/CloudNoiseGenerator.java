package com.mygame.cloud;

import com.mygame.noise.PerlinNoise;

import java.util.stream.IntStream;

/**
 * Génère, uniquement à partir de la seed du monde, la texture RGBA8 tileable qui décrit les nuages.
 * Même seed + même taille = mêmes octets, quelle que soit la machine : aucune valeur aléatoire
 * n'est tirée sur le GPU, tout le bruit vient de {@link PerlinNoise}.
 *
 * <pre>
 *  R : forme générale des cumulus      (fBm Perlin, basses fréquences)
 *  G : « météo » : présence et hauteur (fBm Perlin, très basses fréquences)
 *  B : détail bosselé n°1              (|Perlin| : creux nets, bosses rondes)
 *  A : détail bosselé n°2              (|Perlin|, fréquence plus haute)
 * </pre>
 *
 * Chaque canal et chaque octave dérive de sa propre sous-seed (SplitMix64) et reçoit un décalage
 * propre : sans cela, tous les octaves s'annuleraient au même point (le bruit de Perlin vaut 0
 * sur son réseau) et laisseraient une grille visible.
 */
public final class CloudNoiseGenerator {
    /** Le plus haut octave (64 cellules) doit couvrir au moins 4 pixels par cellule. */
    public static final int MIN_SIZE = 256;
    public static final int MAX_SIZE = 2048;

    private record LayerSpec(int baseCells, int octaves, double persistence, boolean billow, long salt) { }
    private record Octave(PerlinNoise noise, int cells, double offsetX, double offsetY, double amplitude) { }
    private record Layer(Octave[] octaves, boolean billow) { }

    private static final LayerSpec[] SPECS = {
            new LayerSpec(4, 5, 0.50, false, 0x52L),   // R : forme
            new LayerSpec(2, 3, 0.55, false, 0x47L),   // G : météo
            new LayerSpec(8, 4, 0.50, true, 0x42L),    // B : détail 1
            new LayerSpec(16, 3, 0.50, true, 0x41L),   // A : détail 2
    };
    private static final long WIND_SALT = 0x57494E44L;

    private CloudNoiseGenerator() { }

    /** @return size × size × 4 octets (RGBA, ligne par ligne), tileable sur les deux axes. */
    public static byte[] generate(long seed, int size) {
        if (size < MIN_SIZE || size > MAX_SIZE || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException(
                    "La taille doit être une puissance de 2 dans [" + MIN_SIZE + ", " + MAX_SIZE + "] : " + size);
        }

        Layer[] layers = new Layer[SPECS.length];
        for (int i = 0; i < SPECS.length; i++) {
            layers[i] = buildLayer(seed, SPECS[i]);
        }

        byte[] rgba = new byte[size * size * 4];
        // Chaque pixel est indépendant : le parallélisme ne change donc pas le résultat.
        IntStream.range(0, size).parallel().forEach(y -> {
            for (int x = 0; x < size; x++) {
                int base = (y * size + x) * 4;
                for (int c = 0; c < layers.length; c++) {
                    rgba[base + c] = (byte) Math.round(sample(layers[c], x, y, size) * 255.0);
                }
            }
        });
        return rgba;
    }

    /** Direction du vent (radians, plan XZ), fixée par la seed : un monde = un vent. */
    public static double windAngle(long seed) {
        return unit(mix(seed ^ mix(WIND_SALT))) * (Math.PI * 2.0);
    }

    private static double sample(Layer layer, int px, int py, int size) {
        double total = 0.0, amplitudeSum = 0.0;
        for (Octave o : layer.octaves()) {
            double x = (double) px * o.cells() / size + o.offsetX();
            double y = (double) py * o.cells() / size + o.offsetY();
            double n = o.noise().noise(x, y, o.cells(), o.cells());
            total += (layer.billow() ? Math.abs(n) : n) * o.amplitude();
            amplitudeSum += o.amplitude();
        }
        double v = total / amplitudeSum;
        // Un fBm de Perlin dépasse rarement ±0,6 : on étire pour exploiter toute la plage 0..1.
        return clamp01(layer.billow() ? v * 2.4 : 0.5 + v * 0.75);
    }

    private static Layer buildLayer(long seed, LayerSpec spec) {
        long layerSeed = mix(seed ^ mix(spec.salt()));
        Octave[] octaves = new Octave[spec.octaves()];
        double amplitude = 1.0;
        for (int i = 0; i < octaves.length; i++) {
            long octaveSeed = mix(layerSeed + i);
            int cells = spec.baseCells() << i;
            double offsetX = unit(mix(octaveSeed ^ 0x9E3779B1L)) * cells;
            double offsetY = unit(mix(octaveSeed ^ 0x85EBCA6BL)) * cells;
            octaves[i] = new Octave(new PerlinNoise(octaveSeed), cells, offsetX, offsetY, amplitude);
            amplitude *= spec.persistence();
        }
        return new Layer(octaves, spec.billow());
    }

    /** SplitMix64 : bon mélangeur de bits pour dériver des sous-seeds indépendantes. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 53 bits de poids fort → [0, 1). */
    private static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }
}