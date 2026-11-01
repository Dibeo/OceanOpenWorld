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
        ambient.setColor(new ColorRGBA(0.4f, 0.5f, 0.65f, 1f));
        scene.addLight(ambient);
    }

    public void setSunDirection(Vector3f direction) {
        ensureInitialized();
        if (direction == null || direction.lengthSquared() < 1e-8f) {
            throw new IllegalArgumentException("La direction du soleil doit être non nulle.");
        }
        sun.setDirection(direction.normalize()); // ne modifie pas le vecteur fourni
    }

    public void setSunIntensity(float intensity) {
        ensureInitialized();
        if (intensity < 0f || !Float.isFinite(intensity)) {
            throw new IllegalArgumentException("Intensité solaire invalide.");
        }
        sun.setColor(baseSunColor.mult(intensity));
    }

    public Vector3f getSunDirection() {
        ensureInitialized();
        return sun.getDirection().clone();
    }

    /** Couleur réellement émise, intensité comprise : utile à WaterFilter. */
    public ColorRGBA getSunColor() {
        ensureInitialized();
        return sun.getColor().clone();
    }

    public DirectionalLight getSun() {
        ensureInitialized();
        return sun;
    }

    private void ensureInitialized() {
        if (sun == null) {
            throw new IllegalStateException("LightingManager.initialize() doit être appelé d'abord.");
        }
    }
}
