package com.mygame.water;

import com.jme3.asset.AssetManager;
import com.mygame.config.GameConfig;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.water.WaterFilter;

public class WaterManager {
    private final Node rootNode;
    private final AssetManager assets;
    private final ViewPort viewPort;
    private final Vector3f sunDirection;
    private WaterFilter water;
    private final GameConfig config;

    public WaterManager(Node rootNode, AssetManager assets,
                        ViewPort viewPort, Vector3f sunDirection, GameConfig config) {
        this.rootNode = rootNode;
        this.assets = assets;
        this.viewPort = viewPort;
        this.sunDirection = sunDirection.clone();
        this.config = config;
    }

    public void initialize() {
        water = new WaterFilter(rootNode, sunDirection);
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
        water.setMaxAmplitude(config.maxAmplitude);
        water.setSpeed(config.waveSpeed);
        water.setWindDirection(new Vector2f(1f, 0.3f).normalizeLocal());
        water.setShininess(0.8f);
        water.setSunScale(2.5f);
        water.setUseFoam(true);
        water.setFoamIntensity(0.2f);
        water.setUseHQShoreline(true);
        water.setUseCaustics(true);
        water.setCausticsIntensity(0.4f);
        water.setReflectionMapSize(config.reflectionMapSize);
        FilterPostProcessor fpp = new FilterPostProcessor(assets);
        fpp.addFilter(water);
        viewPort.addProcessor(fpp);
    }

    public WaterFilter getWaterFilter() { return water; }
}
