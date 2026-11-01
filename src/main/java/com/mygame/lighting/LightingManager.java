package com.mygame.lighting;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class LightingManager {
    private final Node rootNode;
    private DirectionalLight sun;
    private final ColorRGBA sunColor = new ColorRGBA(1f, 0.95f, 0.82f, 1f);

    public LightingManager(Node rootNode) { this.rootNode = rootNode; }

    public void initialize(float intensity) {
        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.3f, -0.8f, -0.5f).normalizeLocal());
        sun.setColor(sunColor.mult(intensity));
        rootNode.addLight(sun);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.4f, 0.5f, 0.65f, 1f));
        rootNode.addLight(ambient);
    }

    public Vector3f getSunDirection() { return sun.getDirection().clone(); }
    public ColorRGBA getSunColor() { return sunColor.clone(); }
}
