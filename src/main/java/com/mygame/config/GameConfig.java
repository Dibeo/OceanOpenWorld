package com.mygame.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Paramètres immuables chargés à partir d'un fichier XML externe. */
public final class GameConfig {
    public final long seed;
    public final int chunkSize, subdivisions, renderDistance, maxChunksPerFrame;
    public final float cameraSpeed, cameraFar;
    public final int width, height, fpsLimit;
    public final boolean vsync, gammaCorrection;
    public final float waterHeight, waterTransparency, waveSpeed, waveScale, maxAmplitude;
    public final float windChangeSpeed, windStrengthVariation;
    public final int reflectionMapSize;
    public final float sunIntensity;
    public final float grassDensity, grassDistance;
    public final boolean cloudsEnabled;
    public final float cloudAltitude, cloudThickness, cloudCoverage, cloudDensity, cloudWindSpeed, cloudTileMeters;
    public final int cloudTextureSize, cloudSteps, cloudLightSteps;

    private GameConfig(Element root) {
        Element world = element(root, "world");
        Element terrain = element(root, "terrain");
        Element camera = element(root, "camera");
        Element graphics = element(root, "graphics");
        Element water = element(root, "water");
        Element lighting = element(root, "lighting");
        Element clouds = element(root, "clouds");
        Element grass = element(root, "grass");

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
            throw new IllegalArgumentException("water.windStrengthVariation doit être <= 1");
        }
        reflectionMapSize = positiveInt(water, "reflectionMapSize");
        sunIntensity = nonnegativeFloat(lighting, "sunIntensity");
        grassDensity = positiveFloat(grass, "density");
        grassDistance = positiveFloat(grass, "distance");
        if (grassDensity > 16f) throw new IllegalArgumentException("grass.density doit être <= 16");
        if (grassDistance < 10f || grassDistance > 400f) {
            throw new IllegalArgumentException("grass.distance doit être dans [10, 400]");
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
        if (cloudCoverage > 1f) throw new IllegalArgumentException("clouds.coverage doit être <= 1");
        if (cloudTextureSize < 256 || cloudTextureSize > 1024 || Integer.bitCount(cloudTextureSize) != 1) {
            throw new IllegalArgumentException("clouds.textureSize doit valoir 256, 512 ou 1024");
        }
        if (cloudSteps < 8 || cloudSteps > 128) throw new IllegalArgumentException("clouds.steps doit être dans [8, 128]");
        if (cloudLightSteps > 8) throw new IllegalArgumentException("clouds.lightSteps doit être <= 8");
        if (cameraFar <= 1f) throw new IllegalArgumentException("camera.farPlane doit être > 1");
        if (renderDistance > 64) throw new IllegalArgumentException("renderDistance trop élevé (max 64)");
        if (subdivisions > 256) throw new IllegalArgumentException("subdivisions trop élevé (max 256)");
        if (chunkSize % subdivisions != 0) {
            throw new IllegalArgumentException("chunkSize doit être divisible par subdivisions pour assurer les bordures");
        }
    }

    public static GameConfig load(String filename) {
        Path path = Paths.get(filename).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Configuration introuvable : " + path);
        }
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
                throw new IllegalArgumentException("Racine XML attendue : gameConfig");
            }
            return new GameConfig(document.getDocumentElement());
        } catch (Exception e) {
            throw new IllegalStateException("Lecture de " + path + " impossible : " + e.getMessage(), e);
        }
    }

    private static Element element(Element parent, String name) {
        var matches = parent.getElementsByTagName(name);
        if (matches.getLength() != 1) throw new IllegalArgumentException("Elément XML requis : " + name);
        return (Element) matches.item(0);
    }
    private static String required(Element e, String name) {
        String value = e.getAttribute(name);
        if (value.isEmpty()) throw new IllegalArgumentException(e.getTagName() + "." + name + " requis");
        return value;
    }
    private static int positiveInt(Element e, String key) {
        int v = Integer.parseInt(required(e, key));
        if (v < 1) throw new IllegalArgumentException(key + " doit être > 0");
        return v;
    }
    private static int nonnegativeInt(Element e, String key) {
        int v = Integer.parseInt(required(e, key));
        if (v < 0) throw new IllegalArgumentException(key + " doit être >= 0");
        return v;
    }
    private static float positiveFloat(Element e, String key) {
        float v = Float.parseFloat(required(e, key));
        if (!Float.isFinite(v) || v <= 0) throw new IllegalArgumentException(key + " doit être fini et > 0");
        return v;
    }
    private static float nonnegativeFloat(Element e, String key) {
        float v = Float.parseFloat(required(e, key));
        if (!Float.isFinite(v) || v < 0) throw new IllegalArgumentException(key + " doit être fini et >= 0");
        return v;
    }
    private static boolean bool(Element e, String key) {
        String v = required(e, key);
        if (!v.equals("true") && !v.equals("false")) throw new IllegalArgumentException(key + " doit être true ou false");
        return Boolean.parseBoolean(v);
    }
}