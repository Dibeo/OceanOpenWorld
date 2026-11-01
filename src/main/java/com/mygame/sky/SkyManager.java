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
import com.mygame.lighting.LightingManager;

import java.nio.FloatBuffer;

/** Apparence du ciel : ne possède pas de direction solaire indépendante. */
public final class SkyManager {
    private static final float SKY_RADIUS = 450f;
    private static final float SUN_DISTANCE = 350f;
    private static final float SUN_RADIUS = 14f;

    private final Node scene;
    private final AssetManager assets;
    private final Camera camera;
    private final LightingManager lighting;
    private final Node skyNode = new Node("Sky");

    private Geometry sunGeometry;
    private final Vector3f lastSunDirection = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    private boolean initialized;

    public SkyManager(Node scene, AssetManager assets, Camera camera,
                      LightingManager lighting) {
        this.scene = scene;
        this.assets = assets;
        this.camera = camera;
        this.lighting = lighting;
    }

    public void initialize() {
        if (initialized) return;

        Sphere sphere = new Sphere(48, 96, SKY_RADIUS);
        addGradient(sphere);

        Geometry sky = new Geometry("SkyGradient", sphere);
        Material skyMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        skyMaterial.setBoolean("VertexColor", true);
        skyMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        skyMaterial.getAdditionalRenderState().setDepthWrite(false);
        skyMaterial.getAdditionalRenderState().setDepthTest(false);
        sky.setMaterial(skyMaterial);
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        skyNode.attachChild(sky);

        sunGeometry = new Geometry("Sun", new Sphere(24, 48, SUN_RADIUS));
        Material sunMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        sunMaterial.setColor("Color", new ColorRGBA(1f, 0.96f, 0.75f, 1f));
        sunMaterial.getAdditionalRenderState().setDepthWrite(false);
        // Le soleil doit être masqué par un objet opaque situé devant lui.
        sunMaterial.getAdditionalRenderState().setDepthTest(true);
        sunGeometry.setMaterial(sunMaterial);
        // Dessiné après le ciel et la géométrie opaque, avec test de profondeur.
        sunGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        skyNode.attachChild(sunGeometry);

        scene.attachChild(skyNode);
        initialized = true;
        update(0f);
    }

    private void addGradient(Mesh mesh) {
        FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        float[] colors = new float[mesh.getVertexCount() * 4];
        ColorRGBA horizon = new ColorRGBA(0.72f, 0.85f, 0.96f, 1f);
        ColorRGBA zenith = new ColorRGBA(0.08f, 0.34f, 0.78f, 1f);
        ColorRGBA lower = new ColorRGBA(0.27f, 0.43f, 0.60f, 1f);

        for (int i = 0; i < mesh.getVertexCount(); i++) {
            float y = positions.get(i * 3 + 1) / SKY_RADIUS;
            ColorRGBA c = horizon.clone();
            if (y >= 0f) {
                c.interpolateLocal(zenith, (float) Math.pow(y, 0.55));
            } else {
                c.interpolateLocal(lower, Math.min(1f, -y * 2f));
            }
            int n = 4 * i;
            colors[n] = c.r;
            colors[n + 1] = c.g;
            colors[n + 2] = c.b;
            colors[n + 3] = 1f;
        }
        mesh.setBuffer(VertexBuffer.Type.Color, 4, colors);
        mesh.updateBound();
    }

    public void update(float tpf) {
        if (!initialized) return;
        skyNode.setLocalTranslation(camera.getLocation());

        Vector3f current = lighting.getSunDirection();
        if (!current.equals(lastSunDirection)) {
            // Le vecteur de la lumière indique où vont les rayons ;
            // le soleil visible doit se trouver dans la direction opposée.
            sunGeometry.setLocalTranslation(current.negate().mult(SUN_DISTANCE));
            lastSunDirection.set(current);
        }
    }

    public Node getSkyNode() {
        return skyNode;
    }
}
