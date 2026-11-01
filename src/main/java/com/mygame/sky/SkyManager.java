package com.mygame.sky;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import java.nio.FloatBuffer;

public class SkyManager {
    private final Node rootNode;
    private final AssetManager assets;
    private final Camera camera;
    private final Node skyNode = new Node("Sky");
    private Vector3f sunDirection = new Vector3f(-0.3f, -0.8f, -0.5f).normalizeLocal();
    private Geometry sunGeometry;

    public SkyManager(Node rootNode, AssetManager assets, Camera camera) {
        this.rootNode = rootNode;
        this.assets = assets;
        this.camera = camera;
    }

    public void setSunDirection(Vector3f direction) {
        sunDirection = direction.normalize();
        if (sunGeometry != null) sunGeometry.setLocalTranslation(sunDirection.negate().mult(350f));
    }

    public void initialize() {
        Sphere sphere = new Sphere(48, 96, 450f);
        addGradient(sphere);
        Geometry sky = new Geometry("SkyGradient", sphere);
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setBoolean("VertexColor", true);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setDepthTest(false);
        sky.setMaterial(material);
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        skyNode.attachChild(sky);

        sunGeometry = new Geometry("Sun", new Sphere(24, 48, 14f));
        Material sunMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        sunMaterial.setColor("Color", new ColorRGBA(1f, 0.96f, 0.75f, 1f));
        sunMaterial.getAdditionalRenderState().setDepthWrite(false);
        sunMaterial.getAdditionalRenderState().setDepthTest(false);
        sunGeometry.setMaterial(sunMaterial);
        sunGeometry.setQueueBucket(RenderQueue.Bucket.Sky);
        skyNode.attachChild(sunGeometry);
        setSunDirection(sunDirection);
        rootNode.attachChild(skyNode);
        update(0f);
    }

    private void addGradient(Mesh mesh) {
        FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        float[] colors = new float[mesh.getVertexCount() * 4];
        ColorRGBA horizon = new ColorRGBA(0.72f, 0.85f, 0.96f, 1f);
        ColorRGBA zenith = new ColorRGBA(0.08f, 0.34f, 0.78f, 1f);
        ColorRGBA lower = new ColorRGBA(0.27f, 0.43f, 0.60f, 1f);
        for (int i = 0; i < mesh.getVertexCount(); i++) {
            float y = positions.get(i * 3 + 1) / 450f;
            ColorRGBA c = horizon.clone();
            if (y >= 0f) c.interpolateLocal(zenith, (float) Math.pow(y, 0.55));
            else c.interpolateLocal(lower, Math.min(1f, -y * 2f));
            int n = 4 * i;
            colors[n] = c.r; colors[n+1] = c.g;
            colors[n+2] = c.b; colors[n+3] = 1f;
        }
        mesh.setBuffer(VertexBuffer.Type.Color, 4, colors);
        mesh.updateBound();
    }

    public void update(float tpf) { skyNode.setLocalTranslation(camera.getLocation()); }
}
