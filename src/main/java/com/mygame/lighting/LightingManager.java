package com.mygame.lighting;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

/** Source unique pour la direction et l'intensité du soleil. */
public final class LightingManager {
    private final Node scene;
    private final ColorRGBA baseSunColor = new ColorRGBA(1f, 0.95f, 0.82f, 1f);
    private DirectionalLight sun;
    private AmbientLight ambient;
    private int sunVersion;

    public LightingManager(Node scene) {
        this.scene = scene;
    }

    public void initialize(float intensity) {
        if (sun != null) return;

        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.3f, -0.8f, -0.5f).normalizeLocal());
        scene.addLight(sun);
        setSunIntensity(intensity);

        ambient = new AmbientLight();
        // Ambiant neutre-chaud (lumière rebondie par le sable) : un ambiant bleu ciel
        // multiplié par un sable jaune donne des ombres grises/bleutées.
        ambient.setColor(new ColorRGBA(0.42f, 0.40f, 0.36f, 1f));
        scene.addLight(ambient);
    }

    public void setSunDirection(Vector3f direction) {
        ensureInitialized();
        if (direction == null || direction.lengthSquared() < 1e-8f) {
            throw new IllegalArgumentException("La direction du soleil doit être non nulle.");
        }
        sun.setDirection(direction.normalize()); // ne modifie pas le vecteur fourni
        sunVersion++;
    }

    public void setSunIntensity(float intensity) {
        ensureInitialized();
        if (intensity < 0f || !Float.isFinite(intensity)) {
            throw new IllegalArgumentException("Intensité solaire invalide.");
        }
        sun.setColor(baseSunColor.mult(intensity));
    }

    /** Copie défensive de la direction des rayons. */
    public Vector3f getSunDirection() {
        ensureInitialized();
        return sun.getDirection().clone();
    }

    /**
     * Incrémenté à chaque changement de direction : permet au ciel de détecter
     * un changement sans allouer ni comparer de vecteurs à chaque frame.
     */
    public int getSunVersion() { return sunVersion; }

    /** Couleur réellement émise, intensité comprise : utile à WaterFilter. */
    public ColorRGBA getSunColor() {
        ensureInitialized();
        return sun.getColor().clone();
    }

    public DirectionalLight getSun() {
        ensureInitialized();
        return sun;
    }

    public void cleanup() {
        if (sun != null) { scene.removeLight(sun); sun = null; }
        if (ambient != null) { scene.removeLight(ambient); ambient = null; }
    }

    private void ensureInitialized() {
        if (sun == null) {
            throw new IllegalStateException("LightingManager.initialize() doit être appelé d'abord.");
        }
    }
}