package com.mygame.cloud;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.mygame.config.GameConfig;
import com.mygame.lighting.LightingManager;
import com.mygame.sky.SkyManager;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Couche de nuages volumétriques. La forme des nuages ne dépend que de {@code world.seed} :
 * la texture de bruit est produite sur CPU par {@link CloudNoiseGenerator}, le shader ne
 * tire aucun aléa. La direction du vent est elle aussi dérivée de la seed ; le décalage de
 * départ est nul, donc deux lancements identiques démarrent sur le même ciel.
 *
 * <p>Les nuages sont dessinés dans le bucket {@code Sky} (profondeur 1, après l'opaque) juste
 * après le dôme et le soleil : le terrain les masque, ils passent devant le soleil et la
 * caméra de reflet de l'eau les voit sans configuration supplémentaire. L'ordre de rendu
 * dans ce bucket est l'ordre d'insertion dans le graphe de scène, d'où l'attache après
 * {@link SkyManager}.
 */
public final class CloudManager {
    private static final Logger LOG = Logger.getLogger(CloudManager.class.getName());

    // Paramètres d'apparence qui n'ont pas besoin d'être exposés dans config.xml.
    private static final float EROSION = 0.6f;
    private static final float SUN_GAIN = 1.4f;
    private static final float DISTANCE_FACTOR = 16f;       // portée = 16 × (altitude + épaisseur)
    private static final float SHEAR_FACTOR = 0.35f;        // inclinaison sous le vent, × épaisseur
    private static final Vector3f AIR_EXTINCTION = new Vector3f(0.015f, 0.05f, 0.12f);

    private final Node scene;
    private final AssetManager assets;
    private final Camera camera;
    private final LightingManager lighting;
    private final SkyManager sky;
    private final GameConfig config;

    private final Node cloudNode = new Node("Clouds");
    private final Vector2f windOffset = new Vector2f();
    private final Vector2f windDirection = new Vector2f();

    private Material material;
    private CompletableFuture<byte[]> pendingTexture;
    private double windX, windY;
    private int lastSunVersion = -1;
    private boolean initialized;

    public CloudManager(Node scene, AssetManager assets, Camera camera,
                        LightingManager lighting, SkyManager sky, GameConfig config) {
        this.scene = scene;
        this.assets = assets;
        this.camera = camera;
        this.lighting = lighting;
        this.sky = sky;
        this.config = config;
    }

    /** À appeler après {@link SkyManager#initialize()} (ordre de rendu du bucket Sky). */
    public void initialize(long seed) {
        if (initialized || !config.cloudsEnabled) return;

        Sphere dome = new Sphere(12, 24, sky.getSkyRadius());
        dome.clearBuffer(VertexBuffer.Type.Normal);
        dome.clearBuffer(VertexBuffer.Type.TexCoord);

        material = new Material(assets, "MatDefs/Clouds/Clouds.j3md");
        float thickness = config.cloudThickness;
        material.setFloat("CloudBase", config.cloudAltitude);
        material.setFloat("CloudThickness", thickness);
        material.setFloat("TileMeters", config.cloudTileMeters);
        material.setFloat("MaxDistance", DISTANCE_FACTOR * (config.cloudAltitude + thickness));
        material.setFloat("Coverage", config.cloudCoverage);
        material.setFloat("Density", config.cloudDensity);
        material.setFloat("Erosion", EROSION);
        material.setInt("Steps", config.cloudSteps);
        material.setInt("LightSteps", config.cloudLightSteps);
        ColorRGBA horizon = SkyManager.horizonColor();
        material.setVector3("HorizonColor", new Vector3f(horizon.r, horizon.g, horizon.b));

        Geometry geometry = new Geometry("CloudDome", dome);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Sky);
        geometry.setCullHint(Spatial.CullHint.Never);
        geometry.setShadowMode(RenderQueue.ShadowMode.Off);
        cloudNode.attachChild(geometry);
        scene.attachChild(cloudNode);

        initialized = true;
        applySeed(seed);
        // Première texture synchrone : le ciel est complet dès la première image.
        applyTexture(CloudNoiseGenerator.generate(seed, config.cloudTextureSize));
        update(0f);
    }

    /** Nouveau monde : nouvelle texture (calculée hors du thread de rendu) et vent remis à zéro. */
    public void regenerate(long seed) {
        if (!initialized) return;
        applySeed(seed);
        int size = config.cloudTextureSize;
        // Une régénération plus récente remplace simplement la précédente.
        pendingTexture = CompletableFuture.supplyAsync(() -> CloudNoiseGenerator.generate(seed, size));
    }

    public void update(float tpf) {
        if (!initialized) return;
        cloudNode.setLocalTranslation(camera.getLocation());

        if (pendingTexture != null && pendingTexture.isDone()) {
            CompletableFuture<byte[]> done = pendingTexture;
            pendingTexture = null;
            if (done.isCompletedExceptionally()) {
                LOG.log(Level.WARNING, "Génération des nuages échouée", done.exceptionNow());
            } else {
                applyTexture(done.resultNow());
            }
        }

        // Le décalage reste dans [0, tileMeters) : la texture est périodique, on garde la précision float.
        float tile = config.cloudTileMeters;
        windX = wrap(windX + windDirection.x * config.cloudWindSpeed * tpf, tile);
        windY = wrap(windY + windDirection.y * config.cloudWindSpeed * tpf, tile);
        windOffset.set((float) windX, (float) windY);
        material.setVector2("WindOffset", windOffset);

        int version = lighting.getSunVersion();
        if (version != lastSunVersion) {
            applySun(lighting.getSun().getDirection());
            lastSunVersion = version;
        }
    }

    public void cleanup() {
        pendingTexture = null;
        cloudNode.removeFromParent();
        initialized = false;
    }

    public Node getCloudNode() {
        return cloudNode;
    }

    private void applySeed(long seed) {
        double angle = CloudNoiseGenerator.windAngle(seed);
        windDirection.set((float) Math.cos(angle), (float) Math.sin(angle));
        windX = 0.0;
        windY = 0.0;
        // Les nuages s'inclinent légèrement dans le sens du vent quand l'altitude augmente.
        float shear = config.cloudThickness * SHEAR_FACTOR;
        material.setVector2("Shear", new Vector2f(windDirection.x * shear, windDirection.y * shear));
    }

    private void applyTexture(byte[] rgba) {
        int size = config.cloudTextureSize;
        ByteBuffer buffer = BufferUtils.createByteBuffer(rgba.length);
        buffer.put(rgba).flip();
        Texture2D texture = new Texture2D(new Image(Image.Format.RGBA8, size, size, buffer, ColorSpace.Linear));
        texture.setWrap(Texture.WrapMode.Repeat);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        // Trilinear : jME génère les mipmaps tout seul (voir Texture.setMinFilter).
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        material.setTexture("NoiseMap", texture);
    }

    /** @param rayDirection direction des rayons de lumière (vers le bas) : le soleil est à l'opposé. */
    private void applySun(Vector3f rayDirection) {
        Vector3f toSun = rayDirection.negate();
        material.setVector3("SunDirection", toSun);

        float up = Math.max(0f, toSun.y);
        float daylight = smoothstep(-0.08f, 0.18f, toSun.y);
        // Masse d'air approchée : le soleil bas traverse plus d'atmosphère et rougit.
        float airMass = 1f / (0.08f + 0.92f * up);
        Vector3f sunColor = new Vector3f(
                (float) Math.exp(-airMass * AIR_EXTINCTION.x),
                (float) Math.exp(-airMass * AIR_EXTINCTION.y),
                (float) Math.exp(-airMass * AIR_EXTINCTION.z)).multLocal(SUN_GAIN * daylight);
        material.setVector3("SunColor", sunColor);

        ColorRGBA horizon = SkyManager.horizonColor();
        float ambient = 0.6f * (0.25f + 0.75f * daylight);
        material.setVector3("AmbientColor", new Vector3f(horizon.r, horizon.g, horizon.b).multLocal(ambient));
    }

    private static double wrap(double value, double period) {
        return value - Math.floor(value / period) * period;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}