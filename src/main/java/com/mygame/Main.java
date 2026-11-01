package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;

import com.mygame.camera.CameraManager;
import com.mygame.config.GameConfig;
import com.mygame.scene.WorldScene;

public class Main extends SimpleApplication {

    private final GameConfig config;
    private CameraManager cameraManager;
    private WorldScene worldScene;

    public Main(GameConfig config) {
        super(); // pas d'AppStates par défaut inutiles : on garde seulement ceux de SimpleApplication
        this.config = config;
    }

    public static void main(String[] args) {
        GameConfig config = GameConfig.load("config.xml");
        Main app = new Main(config);

        AppSettings settings = new AppSettings(true);
        settings.setTitle("OpenWorld");
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
        // Le monde n'utilise que 2 lumières : un seul passage de shader suffit
        // (au lieu d'un passage par lumière en mode MultiPass).
        renderManager.setPreferredLightMode(TechniqueDef.LightMode.SinglePass);
        renderManager.setSinglePassLightBatchSize(2);

        cameraManager = new CameraManager(cam, flyCam);
        cameraManager.initialize(config.cameraSpeed, config.cameraFar);
        cameraManager.setPosition(new Vector3f(0f, 45f, 75f));
        cameraManager.lookAt(Vector3f.ZERO);

        viewPort.setBackgroundColor(new ColorRGBA(0.60f, 0.78f, 0.95f, 1f));

        worldScene = new WorldScene(assetManager, cam, viewPort, config);
        rootNode.attachChild(worldScene);
        worldScene.initialize();
        // Plus de simpleUpdate() : WorldScene se met à jour via un Control
        // appelé automatiquement par le scene graph.
    }

    @Override
    public void destroy() {
        if (worldScene != null) worldScene.cleanup();
        super.destroy();
    }

    public WorldScene getWorldScene() {
        return worldScene;
    }
}