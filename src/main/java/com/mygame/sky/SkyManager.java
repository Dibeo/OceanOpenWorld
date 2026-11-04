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

/** Apparence du ciel : ne possède pas de direction solaire indépendante. */
public final class SkyManager {
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

    public SkyManager(Node scene, AssetManager assets, Camera camera, LightingManager lighting) {
        this.scene = scene;
        this.assets = assets;
        this.camera = camera;
        this.lighting = lighting;
    }

    public void initialize() {
        if (initialized) return;

        // Le dôme ne doit pas dépasser le far plane, sinon il serait coupé par le frustum.
        skyRadius = Math.min(MAX_SKY_RADIUS, camera.getFrustumFar() * 0.9f);
        sunDistance = skyRadius * 0.78f;

        Sphere sphere = new Sphere(32, 64, skyRadius);
        // Unshaded + VertexColor n'a besoin ni de normales ni de coordonnées de texture.
        sphere.clearBuffer(VertexBuffer.Type.Normal);
        sphere.clearBuffer(VertexBuffer.Type.TexCoord);
        addGradient(sphere);

        Geometry sky = new Geometry("SkyGradient", sphere);
        Material skyMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        skyMaterial.setBoolean("VertexColor", true);
        skyMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        skyMaterial.getAdditionalRenderState().setDepthWrite(false);
        // Le bucket Sky est dessiné APRÈS l'opaque, avec une profondeur fixée à 1 :
        // le test de profondeur doit rester actif, sinon le dôme recouvre le terrain.
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
        // Le soleil doit être masqué par un objet opaque situé devant lui.
        sunMaterial.getAdditionalRenderState().setDepthTest(true);
        sunGeometry.setMaterial(sunMaterial);
        // Bucket Sky (profondeur 1, ordre d'insertion) : dessiné avant les nuages, qui peuvent
        // ainsi passer devant le soleil ; le terrain, lui, le masque toujours.
        sunGeometry.setQueueBucket(RenderQueue.Bucket.Sky);
        skyNode.attachChild(sunGeometry);

        scene.attachChild(skyNode);
        initialized = true;
        update(0f);
    }

    private void addGradient(Mesh mesh) {
        FloatBuffer positions = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        int count = mesh.getVertexCount();
        float[] colors = new float[count * 4];
        ColorRGBA horizon = HORIZON;
        ColorRGBA zenith = new ColorRGBA(0.08f, 0.34f, 0.78f, 1f);
        ColorRGBA lower = new ColorRGBA(0.27f, 0.43f, 0.60f, 1f);
        ColorRGBA c = new ColorRGBA();

        for (int i = 0; i < count; i++) {
            float y = positions.get(i * 3 + 1) / skyRadius;
            c.set(horizon);
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

        int version = lighting.getSunVersion();
        if (version != lastSunVersion) {
            // Le vecteur de la lumière indique où vont les rayons ;
            // le soleil visible se trouve dans la direction opposée.
            Vector3f d = lighting.getSun().getDirection(); // référence interne, aucune allocation
            sunGeometry.setLocalTranslation(-d.x * sunDistance, -d.y * sunDistance, -d.z * sunDistance);
            lastSunVersion = version;
        }
    }

    public void cleanup() {
        skyNode.removeFromParent();
        initialized = false;
    }

    public Node getSkyNode() {
        return skyNode;
    }

    /** Rayon du dôme (valide après {@link #initialize()}). */
    public float getSkyRadius() {
        return skyRadius;
    }

    /** Couleur de l'horizon : référence commune pour la brume et l'ambiant des nuages. */
    public static ColorRGBA horizonColor() {
        return HORIZON.clone();
    }
}