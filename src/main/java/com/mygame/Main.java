package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.mygame.camera.CameraManager;
import com.mygame.config.GameConfig;
import com.mygame.lighting.LightingManager;
import com.mygame.sky.SkyManager;
import com.mygame.terrain.TerrainManager;
import com.mygame.water.WaterManager;

public class Main extends SimpleApplication {

    private final GameConfig config;
    private TerrainManager terrainManager;
    private WaterManager waterManager;
    private SkyManager skyManager;

    private Main(GameConfig config) {
        this.config = config;
    }

    public static void main(String[] args) {
        // Par défaut : config.xml dans le répertoire de lancement.
        // Autre emplacement : java ... /chemin/vers/config.xml
        GameConfig config = GameConfig.load(args.length > 0 ? args[0] : "config.xml");
        Main app = new Main(config);
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Monde infini - seed " + config.seed);
        settings.setResolution(config.width, config.height);
        settings.setVSync(config.vsync);
        settings.setGammaCorrection(config.gammaCorrection);
        settings.setFrameRate(config.fpsLimit);
        settings.setSamples(0);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        CameraManager cameraManager = new CameraManager(cam, flyCam);
        cameraManager.initialize(config.cameraSpeed, config.cameraFar);

        LightingManager lighting = new LightingManager(rootNode);
        lighting.initialize(config.sunIntensity);

        skyManager = new SkyManager(rootNode, assetManager, cam);
        skyManager.setSunDirection(lighting.getSunDirection());
        skyManager.initialize();

        float startX = 0f, startZ = 0f;
        float groundY;
        terrainManager = new TerrainManager(rootNode, assetManager, cam, config);
        // Positionnement avant chargement pour éviter de construire des chunks inutiles.
        // La hauteur de départ provient du générateur déterministe.
        terrainManager.initialize(config.seed);
        groundY = terrainManager.getHeight(startX, startZ);
        float cameraY = Math.max(20f, groundY + 25f);
        cameraManager.setPosition(new Vector3f(startX, cameraY, startZ + 45f));
        cameraManager.lookAt(new Vector3f(startX, groundY, startZ));
        terrainManager.update(0f);

        waterManager = new WaterManager(rootNode, assetManager,
                viewPort, lighting.getSunDirection(), config);
        waterManager.initialize();
        waterManager.getWaterFilter().setLightColor(lighting.getSunColor());
    }

    @Override
    public void simpleUpdate(float tpf) {
        skyManager.update(tpf);
        terrainManager.update(tpf);
    }

    public void changeWorldSeed(long newSeed) {
        terrainManager.regenerate(newSeed);
    }
}
