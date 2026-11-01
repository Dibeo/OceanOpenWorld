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

        // Mise à jour automatique à chaque frame par le scene graph :
        // Main n'a plus besoin d'appeler update().
        addControl(new AbstractControl() {
            @Override protected void controlUpdate(float tpf) { WorldScene.this.update(tpf); }
            @Override protected void controlRender(RenderManager rm, ViewPort vp) { }
        });
    }

    public void initialize() {
        if (initialized) return;

        lighting = new LightingManager(this);
        lighting.initialize(config.sunIntensity);

        sky = new SkyManager(this, assetManager, camera, lighting);
        sky.initialize();

        terrain = new TerrainManager(this, assetManager, camera, config);
        terrain.initialize(config.seed);

        // Ombres du soleil, appliquées avant l'eau. Les chunks sont CastAndReceive ;
        // la distance d'ombre est limitée pour ne rendre dans les shadow maps que les chunks proches.
        DirectionalLightShadowFilter shadows = new DirectionalLightShadowFilter(assetManager, 2048, 3);
        shadows.setLight(lighting.getSun());
        shadows.setLambda(0.55f);
        shadows.setShadowIntensity(0.45f);
        shadows.setEdgeFilteringMode(EdgeFilteringMode.PCF4);
        shadows.setShadowZExtend(Math.min(160f, camera.getFrustumFar() * 0.5f));
        shadows.setShadowZFadeLength(30f);

        water = new WaterManager(this, assetManager, viewPort, lighting.getSunDirection(), config);
        water.initialize(shadows);
        water.getWaterFilter().setLightColor(lighting.getSunColor());

        initialized = true;
    }

    private void update(float tpf) {
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
        // Le ciel détecte le changement via LightingManager.getSunVersion().
        water.getWaterFilter().setLightDirection(lighting.getSunDirection());
    }

    public void setSunIntensity(float intensity) {
        ensureInitialized();
        lighting.setSunIntensity(intensity);
        water.getWaterFilter().setLightColor(lighting.getSunColor());
    }

    /** Libère threads, post-processeurs et lumières. À appeler à l'arrêt de l'application. */
    public void cleanup() {
        if (!initialized) return;
        initialized = false;
        water.cleanup();
        terrain.cleanup();
        sky.cleanup();
        lighting.cleanup();
        removeFromParent();
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