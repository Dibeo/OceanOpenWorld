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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Renders a deterministic volumetric cloud layer and updates its motion and lighting.
 * Cloud noise and wind direction derive from {@code world.seed}; the cloud texture is
 * generated on the CPU, and each world starts with a zero wind offset.
 *
 * <p>The dome is drawn in the {@code Sky} bucket after opaque geometry. It is attached
 * after {@link SkyManager}, so the terrain occludes clouds, clouds can appear in front
 * of the sun, and water-reflection cameras see the same cloud layer.
 */
public final class CloudManager {
    private static final Logger LOG = LogManager.getLogger(CloudManager.class);

    // Appearance parameters that do not need to be exposed in config.xml.
    private static final float EROSION = 0.6f;
    private static final float SUN_GAIN = 1.4f;
    private static final float DISTANCE_FACTOR = 16f;       // range = 16 × (altitude + thickness)
    private static final float SHEAR_FACTOR = 0.35f;        // downwind tilt × thickness
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

    /**
     * Creates the cloud manager with dependencies shared by the world scene.
     *
     * @param scene scene receiving the cloud dome
     * @param assets engine asset manager
     * @param camera active camera
     * @param lighting shared sun state
     * @param sky initialized sky manager
     * @param config validated cloud settings
     */
    public CloudManager(Node scene, AssetManager assets, Camera camera,
                        LightingManager lighting, SkyManager sky, GameConfig config) {
        this.scene = scene;
        this.assets = assets;
        this.camera = camera;
        this.lighting = lighting;
        this.sky = sky;
        this.config = config;
    }

    /**
     * Creates the cloud dome and seed-derived noise texture when clouds are enabled.
     *
     * @param seed world seed
     */
    public void initialize(long seed) {
        if (initialized) return;
        if (!config.cloudsEnabled) {
            LOG.debug("Cloud rendering is disabled");
            return;
        }

        LOG.debug("Initializing clouds for seed {} with {}x{} noise texture",
                seed, config.cloudTextureSize, config.cloudTextureSize);
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
        // Generate the first texture synchronously so clouds are visible in the first frame.
        applyTexture(CloudNoiseGenerator.generate(seed, config.cloudTextureSize));
        update(0f);
        LOG.info("Cloud rendering initialized");
    }

    /**
     * Schedules a seed-derived cloud texture off the render thread and resets the wind offset.
     *
     * @param seed new world seed
     */
    public void regenerate(long seed) {
        if (!initialized) return;
        LOG.info("Regenerating cloud texture with seed {}", seed);
        applySeed(seed);
        int size = config.cloudTextureSize;
        // A newer regeneration supersedes the previous pending result.
        pendingTexture = CompletableFuture.supplyAsync(() -> CloudNoiseGenerator.generate(seed, size));
    }

    /**
     * Advances cloud motion and applies completed background texture generation.
     *
     * @param tpf elapsed frame time in seconds
     */
    public void update(float tpf) {
        if (!initialized) return;
        cloudNode.setLocalTranslation(camera.getLocation());

        if (pendingTexture != null && pendingTexture.isDone()) {
            CompletableFuture<byte[]> done = pendingTexture;
            pendingTexture = null;
            if (done.isCompletedExceptionally()) {
                LOG.error("Cloud generation failed", done.exceptionNow());
            } else {
                applyTexture(done.resultNow());
                LOG.debug("Cloud texture generation completed");
            }
        }

        // Keep the periodic texture offset in [0, tileMeters) to preserve float precision.
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

    /** Detaches the cloud node and abandons any pending texture result. */
    public void cleanup() {
        LOG.debug("Cleaning up cloud rendering");
        pendingTexture = null;
        cloudNode.removeFromParent();
        initialized = false;
    }

    /**
     * Returns the scene node containing the cloud dome.
     *
     * @return cloud scene node
     */
    public Node getCloudNode() {
        return cloudNode;
    }

    private void applySeed(long seed) {
        double angle = CloudNoiseGenerator.windAngle(seed);
        windDirection.set((float) Math.cos(angle), (float) Math.sin(angle));
        windX = 0.0;
        windY = 0.0;
        // Clouds shear slightly downwind as altitude increases.
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
        // Trilinear filtering makes jME generate mipmaps automatically (see Texture.setMinFilter).
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        material.setTexture("NoiseMap", texture);
    }

    /** @param rayDirection direction of the light rays (downward); the sun lies in the opposite direction */
    private void applySun(Vector3f rayDirection) {
        Vector3f toSun = rayDirection.negate();
        material.setVector3("SunDirection", toSun);

        float up = Math.max(0f, toSun.y);
        float daylight = smoothstep(-0.08f, 0.18f, toSun.y);
        // Approximate air mass: a low sun passes through more atmosphere and appears redder.
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

    private static float smoothstep(float edge0, float edge1, float y) {
        float t = Math.max(0f, Math.min(1f, (y - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}