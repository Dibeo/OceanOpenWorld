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

public class WaterManager {
    private final Node reflectionScene;
    private final AssetManager assets;
    private final ViewPort viewPort;
    private final Vector3f sunDirection;
    private final GameConfig config;
    private final WindModel wind;
    private WaterFilter water;
    private FilterPostProcessor fpp;

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

    /** @param preFilters filtres appliqués avant l'eau (ex. ombres), dans l'ordre. */
    public void initialize(Filter... preFilters) {
        if (water != null) return;

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
    }

    public void update(float tpf) {
        if (water == null) return;
        applyWind();
    }

    private void applyWind() {
        water.setWindDirection(wind.getDirection());
        water.setMaxAmplitude(wind.getWaveAmplitude());
    }

    public WaterFilter getWaterFilter() { return water; }
    public FilterPostProcessor getFilterPostProcessor() { return fpp; }

    public void cleanup() {
        if (fpp != null) {
            viewPort.removeProcessor(fpp);
            fpp.cleanup();
            fpp = null;
        }
        water = null;
    }
}