package com.mygame.scene;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.mygame.config.GameConfig;
import com.mygame.lighting.LightingManager;
import com.mygame.sky.SkyManager;
import com.mygame.terrain.TerrainManager;
import com.mygame.water.WaterManager;

/** Assemble les systèmes sans dupliquer l'état solaire. */
public class WorldScene extends Node {
    private final GameConfig config;
    private final AssetManager assetManager;
    private final Camera camera;
    private final ViewPort viewPort;

    private LightingManager lighting;
    private SkyManager sky;
    private TerrainManager terrain;
    private WaterManager water;
    private boolean initialized;

    public WorldScene(AssetManager assetManager, Camera camera,
                      ViewPort viewPort, GameConfig config) {
        super("WorldScene");
        this.assetManager = assetManager;
        this.camera = camera;
        this.viewPort = viewPort;
        this.config = config;
    }

    public void initialize() {
        if (initialized) return;

        lighting = new LightingManager(this);
        lighting.initialize(config.sunIntensity);

        sky = new SkyManager(this, assetManager, camera, lighting);
        sky.initialize();

        terrain = new TerrainManager(this, assetManager, camera, config);
        terrain.initialize(config.seed);

        water = new WaterManager(this, assetManager, viewPort,
                lighting.getSunDirection(), config);
        water.initialize();
        water.getWaterFilter().setLightColor(lighting.getSunColor());

        initialized = true;
    }

    public void update(float tpf) {
        if (!initialized) return;
        sky.update(tpf);
        terrain.update(tpf);
    }

    public void regenerate(long seed) {
        ensureInitialized();
        terrain.regenerate(seed);
    }

    public void setSunDirection(Vector3f direction) {
        ensureInitialized();
        lighting.setSunDirection(direction);
        // L'eau utilise le même soleil que le terrain ; le ciel le lit
        // directement dans LightingManager au prochain update().
        water.getWaterFilter().setLightDirection(lighting.getSunDirection());
    }

    public void setSunIntensity(float intensity) {
        ensureInitialized();
        lighting.setSunIntensity(intensity);
        water.getWaterFilter().setLightColor(lighting.getSunColor());
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("WorldScene.initialize() doit être appelé d'abord.");
        }
    }

    public TerrainManager getTerrain() { return terrain; }
    public WaterManager getWater() { return water; }
    public SkyManager getSky() { return sky; }
    public LightingManager getLighting() { return lighting; }
}
