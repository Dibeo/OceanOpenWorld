package com.mygame.water;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.water.WaterFilter;
import com.mygame.config.GameConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configures and updates the jMonkeyEngine water filter and its post-processing pipeline.
 */
public class WaterManager {
    private static final Logger LOG = LogManager.getLogger(WaterManager.class);

    private final Node reflectionScene;
    private final AssetManager assets;
    private final ViewPort viewPort;
    private final Vector3f sunDirection;
    private final GameConfig config;
    private final WindModel wind;
    private WaterFilter water;
    private FilterPostProcessor fpp;

    /**
     * Creates a water manager using the scene, viewport, lighting direction, and settings supplied.
     *
     * @param reflectionScene scene rendered into the water reflection
     * @param assets engine asset manager
     * @param viewPort viewport that receives the water post-processing filter
     * @param sunDirection initial direction of the sun rays
     * @param config validated runtime settings
     * @param wind shared deterministic wind model
     */
    public WaterManager(Node reflectionScene, AssetManager assets,
                        ViewPort viewPort, Vector3f sunDirection, GameConfig config,
                        WindModel wind) {
        this.reflectionScene = reflectionScene;
        this.assets = assets;
        this.viewPort = viewPort;
        this.sunDirection = sunDirection.clone();
        this.config = config;
        this.wind = wind;
    }

    /**
     * Adds the water filter after the supplied filters, which retain their input order.
     *
     * @param preFilters filters to apply before water
     */
    public void initialize(Filter... preFilters) {
        if (water != null) return;

        LOG.debug("Initializing water filter at height {} with reflection map size {}",
                config.waterHeight, config.reflectionMapSize);
        water = new WaterFilter(reflectionScene, sunDirection);
        water.setWaterHeight(config.waterHeight);
        water.setWaterColor(new ColorRGBA(0.025f, 0.22f, 0.28f, 1f));
        water.setDeepWaterColor(new ColorRGBA(0.01f, 0.055f, 0.11f, 1f));
        water.setWaterTransparency(config.waterTransparency);
        water.setColorExtinction(new Vector3f(5f, 20f, 30f));
        water.setUseRefraction(true);
        water.setUseSpecular(true);
        water.setRefractionStrength(0.1f);
        water.setUseRipples(true);
        water.setWaveScale(config.waveScale);
        water.setSpeed(config.waveSpeed);
        applyWind();
        water.setShininess(0.8f);
        water.setSunScale(2.5f);
        water.setUseFoam(true);
        water.setFoamIntensity(0.2f);
        water.setUseHQShoreline(true);
        water.setUseCaustics(true);
        water.setCausticsIntensity(0.4f);
        water.setReflectionMapSize(config.reflectionMapSize);

        fpp = new FilterPostProcessor(assets);
        for (Filter f : preFilters) fpp.addFilter(f);
        fpp.addFilter(water);
        viewPort.addProcessor(fpp);
        LOG.debug("Water filter initialized");
    }

    /**
     * Copies the current wind direction and wave amplitude into the water filter.
     *
     * @param tpf elapsed frame time in seconds
     */
    public void update(float tpf) {
        if (water == null) return;
        applyWind();
    }

    private void applyWind() {
        water.setWindDirection(wind.getDirection());
        water.setMaxAmplitude(wind.getWaveAmplitude());
    }

    /**
     * Returns the configured water filter, or {@code null} before initialization or after cleanup.
     *
     * @return water filter, or {@code null}
     */
    public WaterFilter getWaterFilter() { return water; }

    /**
     * Returns the installed post-processor, or {@code null} before initialization or after cleanup.
     *
     * @return filter post-processor, or {@code null}
     */
    public FilterPostProcessor getFilterPostProcessor() { return fpp; }

    /** Removes and cleans up the post-processor; safe to call when not initialized. */
    public void cleanup() {
        LOG.debug("Cleaning up water filter");
        if (fpp != null) {
            viewPort.removeProcessor(fpp);
            fpp.cleanup();
            fpp = null;
        }
        water = null;
    }
}