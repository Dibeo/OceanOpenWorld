package com.mygame.lighting;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Owns the scene's directional sun and ambient light and provides shared sun state. */
public final class LightingManager {
    private static final Logger LOG = LogManager.getLogger(LightingManager.class);

    private final Node scene;
    private final ColorRGBA baseSunColor = new ColorRGBA(1f, 0.95f, 0.82f, 1f);
    private DirectionalLight sun;
    private AmbientLight ambient;
    private int sunVersion;

    /**
     * Creates a light manager whose lights will be attached to {@code scene}.
     *
     * @param scene node that receives the managed lights
     */
    public LightingManager(Node scene) {
        this.scene = scene;
    }

    /**
     * Adds the sun and ambient lights to the scene and sets the initial sun intensity.
     *
     * @param intensity finite, nonnegative multiplier applied to the base sun color
     */
    public void initialize(float intensity) {
        if (sun != null) return;

        LOG.debug("Initializing lighting with sun intensity {}", intensity);
        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.3f, -0.8f, -0.5f).normalizeLocal());
        scene.addLight(sun);
        setSunIntensity(intensity);

        ambient = new AmbientLight();
        // Use neutral-warm ambient light to represent light bounced from sand; sky-blue
        // ambient multiplied by yellow sand would produce gray-blue shadows.
        ambient.setColor(new ColorRGBA(0.42f, 0.40f, 0.36f, 1f));
        scene.addLight(ambient);
        LOG.debug("Lighting initialized");
    }

    /**
     * Changes the direction in which sun rays travel.
     *
     * @param direction nonzero world-space ray direction; normalized internally
     * @throws IllegalStateException if the manager has not been initialized
     * @throws IllegalArgumentException if the direction is null or has zero length
     */
    public void setSunDirection(Vector3f direction) {
        ensureInitialized();
        if (direction == null || direction.lengthSquared() < 1e-8f) {
            throw new IllegalArgumentException("Sun direction must be non-zero.");
        }
        sun.setDirection(direction.normalize()); // does not modify the supplied vector
        sunVersion++;
        LOG.debug("Sun direction changed to {}", sun.getDirection());
    }

    /**
     * Sets the nonnegative multiplier applied to the base sun color.
     *
     * @param intensity finite intensity multiplier
     * @throws IllegalStateException if the manager has not been initialized
     * @throws IllegalArgumentException if the intensity is negative or non-finite
     */
    public void setSunIntensity(float intensity) {
        ensureInitialized();
        if (intensity < 0f || !Float.isFinite(intensity)) {
            throw new IllegalArgumentException("Invalid solar intensity.");
        }
        sun.setColor(baseSunColor.mult(intensity));
        LOG.debug("Sun intensity changed to {}", intensity);
    }

    /**
     * Returns a defensive copy of the normalized direction in which sun rays travel.
     *
     * @return copy of the normalized sun-ray direction
     * @throws IllegalStateException if the manager has not been initialized
     */
    public Vector3f getSunDirection() {
        ensureInitialized();
        return sun.getDirection().clone();
    }

    /**
     * Returns the counter incremented whenever {@link #setSunDirection(Vector3f)} is called.
     * The sky uses it to detect changes without allocating or comparing vectors each frame.
     *
     * @return sun direction change count
     */
    public int getSunVersion() { return sunVersion; }

    /**
     * Returns a defensive copy of the sun's emitted color, including its intensity multiplier.
     *
     * @return copy of the emitted sun color
     * @throws IllegalStateException if the manager has not been initialized
     */
    public ColorRGBA getSunColor() {
        ensureInitialized();
        return sun.getColor().clone();
    }

    /**
     * Returns the engine's directional-light instance.
     *
     * @return managed directional light
     * @throws IllegalStateException if the manager has not been initialized
     */
    public DirectionalLight getSun() {
        ensureInitialized();
        return sun;
    }

    /** Removes the managed lights from the scene; safe to call more than once. */
    public void cleanup() {
        LOG.debug("Cleaning up lighting");
        if (sun != null) { scene.removeLight(sun); sun = null; }
        if (ambient != null) { scene.removeLight(ambient); ambient = null; }
    }

    private void ensureInitialized() {
        if (sun == null) {
            throw new IllegalStateException("LightingManager.initialize() must be called first.");
        }
    }
}