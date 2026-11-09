package com.mygame.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Immutable, validated runtime settings loaded from the external {@code config.xml} document.
 *
 * <p>Every section and attribute is required. Units, accepted ranges, and the absence of defaults
 * are documented in the root {@code config.xml}.
 */
public final class GameConfig {
    private static final Logger LOG = LogManager.getLogger(GameConfig.class);

    /** Seed used to derive procedural world content. */
    public final long seed;
    /** Chunk width and depth in world units. */
    public final int chunkSize;
    /** Terrain grid intervals along each chunk edge. */
    public final int subdivisions;
    /** Maximum loaded chunk radius along each horizontal axis. */
    public final int renderDistance;
    /** Maximum newly built terrain chunks per frame. */
    public final int maxChunksPerFrame;
    /** Fly-camera movement speed in world units per second. */
    public final float cameraSpeed;
    /** Camera far clipping distance in world units. */
    public final float cameraFar;
    /** Window width in pixels. */
    public final int width;
    /** Window height in pixels. */
    public final int height;
    /** Frame-rate limit in frames per second. */
    public final int fpsLimit;
    /** Whether vertical synchronization is enabled. */
    public final boolean vsync;
    /** Whether gamma correction is enabled. */
    public final boolean gammaCorrection;
    /** Water plane height on the world Y axis. */
    public final float waterHeight;
    /** Water transparency parameter. */
    public final float waterTransparency;
    /** Water wave animation speed. */
    public final float waveSpeed;
    /** Water wave scale. */
    public final float waveScale;
    /** Maximum water-wave amplitude in world units. */
    public final float maxAmplitude;
    /** Rate at which the deterministic wind model changes in noise space. */
    public final float windChangeSpeed;
    /** Fractional wind-strength variation in the inclusive range [0, 1]. */
    public final float windStrengthVariation;
    /** Reflection texture width and height in pixels. */
    public final int reflectionMapSize;
    /** Nonnegative multiplier applied to the base sun color. */
    public final float sunIntensity;
    /** Grass clumps per square metre. */
    public final float grassDensity;
    /** Horizontal grass visibility radius in world units. */
    public final float grassDistance;
    /** Flower-placement fraction in [0, 1]. */
    public final float grassFlowers;
    /** Main-rock density per 1000 square metres in rocky areas. */
    public final float rockDensity;
    /** Rock size multiplier. */
    public final float rockSizeScale;
    /** Tree placement fraction in [0, 1] applied to 8 m candidates on grassy terrain; 0 disables trees. */
    public final float treeDensity;
    /** Distance from the camera at which trees use high-detail geometry, in world units. */
    public final float treeDetailDistance;
    /** Whether volumetric clouds are created. */
    public final boolean cloudsEnabled;
    /** Cloud base altitude in world units. */
    public final float cloudAltitude;
    /** Cloud-layer thickness in world units. */
    public final float cloudThickness;
    /** Cloud coverage fraction in [0, 1]. */
    public final float cloudCoverage;
    /** Cloud extinction coefficient per world unit. */
    public final float cloudDensity;
    /** Cloud wind speed in world units per second. */
    public final float cloudWindSpeed;
    /** World-space size in metres of one cloud-noise texture tile. */
    public final float cloudTileMeters;
    /** Square cloud-noise texture dimension in pixels (256, 512, or 1024). */
    public final int cloudTextureSize;
    /** Cloud ray-march sample count. */
    public final int cloudSteps;
    /** Cloud lighting ray-march sample count. */
    public final int cloudLightSteps;

    private GameConfig(Element root) {
        Element world = element(root, "world");
        Element terrain = element(root, "terrain");
        Element camera = element(root, "camera");
        Element graphics = element(root, "graphics");
        Element water = element(root, "water");
        Element lighting = element(root, "lighting");
        Element clouds = element(root, "clouds");
        Element grass = element(root, "grass");
        Element rocks = element(root, "rocks");
        Element trees = element(root, "trees");

        seed = Long.parseLong(required(world, "seed"));
        chunkSize = positiveInt(terrain, "chunkSize");
        subdivisions = positiveInt(terrain, "subdivisions");
        renderDistance = nonnegativeInt(terrain, "renderDistance");
        maxChunksPerFrame = positiveInt(terrain, "maxChunksPerFrame");
        cameraSpeed = positiveFloat(camera, "speed");
        cameraFar = positiveFloat(camera, "farPlane");
        width = positiveInt(graphics, "width");
        height = positiveInt(graphics, "height");
        fpsLimit = positiveInt(graphics, "fpsLimit");
        vsync = bool(graphics, "vsync");
        gammaCorrection = bool(graphics, "gammaCorrection");
        waterHeight = Float.parseFloat(required(water, "height"));
        waterTransparency = nonnegativeFloat(water, "transparency");
        waveSpeed = nonnegativeFloat(water, "waveSpeed");
        waveScale = nonnegativeFloat(water, "waveScale");
        maxAmplitude = nonnegativeFloat(water, "maxAmplitude");
        windChangeSpeed = positiveFloat(water, "windChangeSpeed");
        windStrengthVariation = nonnegativeFloat(water, "windStrengthVariation");
        if (windStrengthVariation > 1f) {
            throw new IllegalArgumentException("water.windStrengthVariation must be <= 1");
        }
        reflectionMapSize = positiveInt(water, "reflectionMapSize");
        sunIntensity = nonnegativeFloat(lighting, "sunIntensity");
        rockDensity = nonnegativeFloat(rocks, "density");
        rockSizeScale = positiveFloat(rocks, "sizeScale");
        if (rockDensity > 50f) throw new IllegalArgumentException("rocks.density must be <= 50");
        if (rockSizeScale < 0.2f || rockSizeScale > 4f) {
            throw new IllegalArgumentException("rocks.sizeScale must be in [0.2, 4]");
        }
        treeDensity = nonnegativeFloat(trees, "density");
        treeDetailDistance = positiveFloat(trees, "detailDistance");
        if (treeDensity > 1f) throw new IllegalArgumentException("trees.density must be <= 1");
        if (treeDetailDistance < 30f || treeDetailDistance > 400f) {
            throw new IllegalArgumentException("trees.detailDistance must be in [30, 400]");
        }
        grassDensity = positiveFloat(grass, "density");
        grassDistance = positiveFloat(grass, "distance");
        grassFlowers = nonnegativeFloat(grass, "flowers");
        if (grassFlowers > 1f) throw new IllegalArgumentException("grass.flowers must be <= 1");
        if (grassDensity > 16f) throw new IllegalArgumentException("grass.density must be <= 16");
        if (grassDistance < 10f || grassDistance > 400f) {
            throw new IllegalArgumentException("grass.distance must be in [10, 400]");
        }
        cloudsEnabled = bool(clouds, "enabled");
        cloudAltitude = positiveFloat(clouds, "altitude");
        cloudThickness = positiveFloat(clouds, "thickness");
        cloudCoverage = nonnegativeFloat(clouds, "coverage");
        cloudDensity = positiveFloat(clouds, "density");
        cloudWindSpeed = nonnegativeFloat(clouds, "windSpeed");
        cloudTileMeters = positiveFloat(clouds, "tileMeters");
        cloudTextureSize = positiveInt(clouds, "textureSize");
        cloudSteps = positiveInt(clouds, "steps");
        cloudLightSteps = positiveInt(clouds, "lightSteps");
        if (cloudCoverage > 1f) throw new IllegalArgumentException("clouds.coverage must be <= 1");
        if (cloudTextureSize < 256 || cloudTextureSize > 1024 || Integer.bitCount(cloudTextureSize) != 1) {
            throw new IllegalArgumentException("clouds.textureSize must be 256, 512, or 1024");
        }
        if (cloudSteps < 8 || cloudSteps > 128) throw new IllegalArgumentException("clouds.steps must be in [8, 128]");
        if (cloudLightSteps > 8) throw new IllegalArgumentException("clouds.lightSteps must be <= 8");
        if (cameraFar <= 1f) throw new IllegalArgumentException("camera.farPlane must be > 1");
        if (renderDistance > 64) throw new IllegalArgumentException("renderDistance is too high (max 64)");
        if (subdivisions > 256) throw new IllegalArgumentException("subdivisions is too high (max 256)");
        if (chunkSize % subdivisions != 0) {
            throw new IllegalArgumentException("chunkSize must be divisible by subdivisions to preserve chunk borders");
        }
    }

    /**
     * Loads and validates a configuration file from the local filesystem.
     *
     * @param filename path to an XML document with a {@code gameConfig} root
     * @return immutable settings read from the document
     * @throws IllegalStateException if the file is missing, malformed, or fails validation
     */
    public static GameConfig load(String filename) {
        Path path = Paths.get(filename).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            LOG.error("Configuration file not found: {}", path);
            throw new IllegalStateException("Configuration file not found: " + path);
        }
        LOG.debug("Loading configuration from {}", path);
        try (InputStream in = Files.newInputStream(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(in);
            if (!"gameConfig".equals(document.getDocumentElement().getTagName())) {
                throw new IllegalArgumentException("Expected XML root: gameConfig");
            }
            GameConfig config = new GameConfig(document.getDocumentElement());
            LOG.debug("Loaded configuration: seed={}, chunkSize={}, renderDistance={}, cloudsEnabled={}",
                    config.seed, config.chunkSize, config.renderDistance, config.cloudsEnabled);
            return config;
        } catch (Exception e) {
            LOG.error("Unable to load configuration from {}", path, e);
            throw new IllegalStateException("Unable to read " + path + ": " + e.getMessage(), e);
        }
    }

    private static Element element(Element parent, String name) {
        var matches = parent.getElementsByTagName(name);
        if (matches.getLength() != 1) throw new IllegalArgumentException("Required XML element: " + name);
        return (Element) matches.item(0);
    }
    private static String required(Element e, String name) {
        String value = e.getAttribute(name);
        if (value.isEmpty()) throw new IllegalArgumentException(e.getTagName() + "." + name + " is required");
        return value;
    }
    private static int positiveInt(Element e, String key) {
        int v = Integer.parseInt(required(e, key));
        if (v < 1) throw new IllegalArgumentException(key + " must be > 0");
        return v;
    }
    private static int nonnegativeInt(Element e, String key) {
        int v = Integer.parseInt(required(e, key));
        if (v < 0) throw new IllegalArgumentException(key + " must be >= 0");
        return v;
    }
    private static float positiveFloat(Element e, String key) {
        float v = Float.parseFloat(required(e, key));
        if (!Float.isFinite(v) || v <= 0) throw new IllegalArgumentException(key + " must be finite and > 0");
        return v;
    }
    private static float nonnegativeFloat(Element e, String key) {
        float v = Float.parseFloat(required(e, key));
        if (!Float.isFinite(v) || v < 0) throw new IllegalArgumentException(key + " must be finite and >= 0");
        return v;
    }
    private static boolean bool(Element e, String key) {
        String v = required(e, key);
        if (!v.equals("true") && !v.equals("false")) throw new IllegalArgumentException(key + " must be true or false");
        return Boolean.parseBoolean(v);
    }
}