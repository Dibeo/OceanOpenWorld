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
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import com.mygame.lighting.LightingManager;

import java.nio.FloatBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates and updates the sky gradient and visible sun using the shared lighting direction.
 * The manager does not maintain a separate sun direction.
 */
public final class SkyManager {
    private static final Logger LOG = LogManager.getLogger(SkyManager.class);

    private static final float MAX_SKY_RADIUS = 450f;
    private static final float SUN_RADIUS = 14f;
    private static final ColorRGBA HORIZON = new ColorRGBA(0.72f, 0.85f, 0.96f, 1f);

    private final Node scene;
    private final AssetManager assets;
    private final Camera camera;
    private final LightingManager lighting;
    private final Node skyNode = new Node("Sky");

    private Geometry sunGeometry;
    private float skyRadius;
    private float sunDistance;
    private int lastSunVersion = -1;
    private boolean initialized;

    /**
     * Creates a sky manager attached to the supplied scene and camera.
     *
     * @param scene scene node receiving the sky dome
     * @param assets engine asset manager
     * @param camera active camera
     * @param lighting shared sun state
     */
    public SkyManager(Node scene, AssetManager assets, Camera camera, LightingManager lighting) {
        this.scene = scene;
        this.assets = assets;
        this.camera = camera;
        this.lighting = lighting;
    }

    /** Builds and attaches the sky dome and visible sun. */
    public void initialize() {
        if (initialized) return;

        LOG.debug("Initializing sky");
        // Keep the dome inside the far plane so the camera frustum does not clip it.
        skyRadius = Math.min(MAX_SKY_RADIUS, camera.getFrustumFar() * 0.9f);
        sunDistance = skyRadius * 0.78f;

        Sphere sphere = new Sphere(32, 64, skyRadius);
        // Unshaded with VertexColor needs neither normals nor texture coordinates.
        sphere.clearBuffer(VertexBuffer.Type.Normal);
        sphere.clearBuffer(VertexBuffer.Type.TexCoord);
        addGradient(sphere);

        Geometry sky = new Geometry("SkyGradient", sphere);
        Material skyMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        skyMaterial.setBoolean("VertexColor", true);
        skyMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        skyMaterial.getAdditionalRenderState().setDepthWrite(false);
        // The Sky bucket is drawn after opaque geometry at depth 1. Keep depth testing
        // enabled so the dome does not cover the terrain.
        skyMaterial.getAdditionalRenderState().setDepthTest(true);
        sky.setMaterial(skyMaterial);
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        sky.setCullHint(Spatial.CullHint.Never);
        skyNode.attachChild(sky);

        Sphere sunSphere = new Sphere(16, 32, SUN_RADIUS);
        sunSphere.clearBuffer(VertexBuffer.Type.Normal);
        sunSphere.clearBuffer(VertexBuffer.Type.TexCoord);
        sunGeometry = new Geometry("Sun", sunSphere);
        Material sunMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        sunMaterial.setColor("Color", new ColorRGBA(1f, 0.96f, 0.75f, 1f));
        sunMaterial.getAdditionalRenderState().setDepthWrite(false);
        // Opaque objects in front of the sun must occlude it.
        sunMaterial.getAdditionalRenderState().setDepthTest(true);
        sunGeometry.setMaterial(sunMaterial);
        // The Sky bucket is drawn at depth 1 in insertion order. Draw the sun before
        // the clouds so they can appear in front of it; terrain still occludes both.
        sunGeometry.setQueueBucket(RenderQueue.Bucket.Sky);
        skyNode.attachChild(sunGeometry);

        scene.attachChild(skyNode);
        initialized = true;
        update(0f);
        LOG.debug("Sky initialized with radius {}", skyRadius);
    }

    private void addGradient(Mesh mesh) {
        FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        int count = mesh.getVertexCount();
        float[] colors = new float[count * 4];
        ColorRGBA zenith = new ColorRGBA(0.08f, 0.34f, 0.78f, 1f);
        ColorRGBA lower = new ColorRGBA(0.27f, 0.43f, 0.60f, 1f);
        ColorRGBA c = new ColorRGBA();

        for (int i = 0; i < count; i++) {
            float y = positions.get(i * 3 + 1) / skyRadius;
            c.set(HORIZON);
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

    /**
     * Tracks the camera and updates the visible sun when the shared sun direction changes.
     *
     * @param tpf elapsed frame time in seconds (not used by the static sky)
     */
    public void update(float tpf) {
        if (!initialized) return;
        skyNode.setLocalTranslation(camera.getLocation());

        int version = lighting.getSunVersion();
        if (version != lastSunVersion) {
            // The light vector points along the rays; the visible sun lies in the opposite direction.
            Vector3f d = lighting.getSun().getDirection(); // internal reference; no allocation
            sunGeometry.setLocalTranslation(-d.x * sunDistance, -d.y * sunDistance, -d.z * sunDistance);
            lastSunVersion = version;
        }
    }

    /** Detaches the sky node and resets the initialized state. */
    public void cleanup() {
        LOG.debug("Cleaning up sky");
        skyNode.removeFromParent();
        initialized = false;
    }

    /**
     * Returns the node containing the sky dome and visible sun.
     *
     * @return sky scene node
     */
    public Node getSkyNode() {
        return skyNode;
    }

    /**
     * Returns the computed dome radius in world units; meaningful after initialization.
     *
     * @return sky dome radius
     */
    public float getSkyRadius() {
        return skyRadius;
    }

    /**
     * Returns a defensive copy of the shared horizon color.
     *
     * @return copy of the horizon color
     */
    public static ColorRGBA horizonColor() {
        return HORIZON.clone();
    }
}