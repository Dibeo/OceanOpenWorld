package com.mygame.scene;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.scene.Node;
import com.jme3.scene.control.AbstractControl;
import com.mygame.cloud.CloudManager;
import com.mygame.config.GameConfig;
import com.mygame.lighting.LightingManager;
import com.mygame.sky.SkyManager;
import com.mygame.terrain.TerrainManager;
import com.mygame.water.WaterManager;
import com.mygame.water.WindModel;

/**
 * Root scene node that initializes, updates, and tears down the world rendering systems
 * while sharing a single source of sun state.
 */
public class WorldScene extends Node {
    private final GameConfig config;
    private final AssetManager assetManager;
    private final Camera camera;
    private final ViewPort viewPort;

    private LightingManager lighting;
    private SkyManager sky;
    private CloudManager clouds;
    private TerrainManager terrain;
    private WaterManager water;
    private WindModel wind;
    private boolean initialized;

    /**
     * Creates the world-scene coordinator.
     *
     * @param assetManager engine asset manager
     * @param camera active camera
     * @param viewPort main viewport
     * @param config validated runtime settings
     */
    public WorldScene(AssetManager assetManager, Camera camera,
                      ViewPort viewPort, GameConfig config) {
        super("WorldScene");
        this.assetManager = assetManager;
        this.camera = camera;
        this.viewPort = viewPort;
        this.config = config;

        // Updated automatically each frame by the scene graph:
        // Main no longer needs to call update().
        addControl(new AbstractControl() {
            @Override protected void controlUpdate(float tpf) { WorldScene.this.update(tpf); }
            @Override protected void controlRender(RenderManager rm, ViewPort vp) { }
        });
    }

    /** Initializes lighting, sky, clouds, terrain, shadows, and water in render order. */
    public void initialize() {
        if (initialized) return;

        lighting = new LightingManager(this);
        lighting.initialize(config.sunIntensity);

        sky = new SkyManager(this, assetManager, camera, lighting);
        sky.initialize();

        // Attach after the sky to establish draw order within the Sky bucket.
        clouds = new CloudManager(this, assetManager, camera, lighting, sky, config);
        clouds.initialize(config.seed);

        wind = new WindModel(config.seed, config.windChangeSpeed,
                config.windStrengthVariation, config.maxAmplitude);
        terrain = new TerrainManager(this, assetManager, camera, config, wind);
        terrain.initialize(config.seed);
        terrain.setSunDirection(lighting.getSunDirection());
        terrain.setSunColor(lighting.getSunColor());

        // Apply sun shadows before water. Chunks both cast and receive shadows; limit the
        // shadow distance so shadow maps only include nearby chunks.
        DirectionalLightShadowFilter shadows = new DirectionalLightShadowFilter(assetManager, 2048, 3);
        shadows.setLight(lighting.getSun());
        shadows.setLambda(0.55f);
        shadows.setShadowIntensity(0.45f);
        shadows.setEdgeFilteringMode(EdgeFilteringMode.PCF4);
        shadows.setShadowZExtend(Math.min(160f, camera.getFrustumFar() * 0.5f));
        shadows.setShadowZFadeLength(30f);

        water = new WaterManager(this, assetManager, viewPort, lighting.getSunDirection(), config, wind);
        water.initialize(shadows);
        water.getWaterFilter().setLightColor(lighting.getSunColor());

        initialized = true;
    }

    private void update(float tpf) {
        if (!initialized) return;
        wind.update(tpf);
        sky.update(tpf);
        clouds.update(tpf);
        terrain.update(tpf);
        water.update(tpf);
    }

    /**
     * Regenerates seed-dependent terrain and cloud content.
     *
     * @param seed new world seed
     * @throws IllegalStateException if the scene has not been initialized
     */
    public void regenerate(long seed) {
        ensureInitialized();
        terrain.regenerate(seed);
        clouds.regenerate(seed);
    }

    /**
     * Changes the shared direction in which sun rays travel.
     *
     * @param direction nonzero world-space ray direction
     * @throws IllegalStateException if the scene has not been initialized
     * @throws IllegalArgumentException if the direction is null or zero length
     */
    public void setSunDirection(Vector3f direction) {
        ensureInitialized();
        lighting.setSunDirection(direction);
        terrain.setSunDirection(lighting.getSunDirection());
        // The sky detects changes through LightingManager.getSunVersion().
        water.getWaterFilter().setLightDirection(lighting.getSunDirection());
    }

    /**
     * Changes the shared sun color intensity.
     *
     * @param intensity finite nonnegative multiplier
     * @throws IllegalStateException if the scene has not been initialized
     * @throws IllegalArgumentException if the intensity is negative or non-finite
     */
    public void setSunIntensity(float intensity) {
        ensureInitialized();
        lighting.setSunIntensity(intensity);
        terrain.setSunColor(lighting.getSunColor());
        water.getWaterFilter().setLightColor(lighting.getSunColor());
    }

    /**
     * Tears down scene systems and removes this node from its parent.
     * Safe to call when the scene has not been initialized.
     */
    public void cleanup() {
        if (!initialized) return;
        initialized = false;
        water.cleanup();
        terrain.cleanup();
        clouds.cleanup();
        sky.cleanup();
        lighting.cleanup();
        wind = null;
        removeFromParent();
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("WorldScene.initialize() doit être appelé d'abord.");
        }
    }

    /**
     * Returns the terrain manager, or {@code null} before initialization.
     *
     * @return terrain manager, or {@code null}
     */
    public TerrainManager getTerrain() { return terrain; }
    /**
     * Returns the water manager, or {@code null} before initialization.
     *
     * @return water manager, or {@code null}
     */
    public WaterManager getWater() { return water; }
    /**
     * Returns the sky manager, or {@code null} before initialization.
     *
     * @return sky manager, or {@code null}
     */
    public SkyManager getSky() { return sky; }
    /**
     * Returns the cloud manager, or {@code null} before initialization.
     *
     * @return cloud manager, or {@code null}
     */
    public CloudManager getClouds() { return clouds; }
    /**
     * Returns the lighting manager, or {@code null} before initialization.
     *
     * @return lighting manager, or {@code null}
     */
    public LightingManager getLighting() { return lighting; }
}