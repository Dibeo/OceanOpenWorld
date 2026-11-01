
package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;

import com.mygame.config.GameConfig;
import com.mygame.camera.CameraManager;
import com.mygame.scene.WorldScene;

public class Main extends SimpleApplication {

    private final GameConfig config;

    private CameraManager cameraManager;
    private WorldScene worldScene;

    public Main(GameConfig config) {
        this.config = config;
    }

    public static void main(String[] args) {

        // Configuration XML externe
        GameConfig config = GameConfig.load(
            "config.xml"
        );

        Main app = new Main(config);

        AppSettings settings = new AppSettings(true);

        settings.setTitle("OpenWorld");
        settings.setResolution(
            config.width,
            config.height
        );

        settings.setVSync(config.vsync);

        settings.setGammaCorrection(
            config.gammaCorrection
        );

        settings.setFrameRate(config.fpsLimit);
        settings.setSamples(0);

        app.setSettings(settings);
        app.setShowSettings(false);

        app.start();
    }

    @Override
    public void simpleInitApp() {

        // Initialisation de la caméra
        cameraManager = new CameraManager(
            cam,
            flyCam
        );

        cameraManager.initialize(
            config.cameraSpeed,
            config.cameraFar
        );

        // Position initiale
        cameraManager.setPosition(
            new Vector3f(0f, 45f, 75f)
        );

        cameraManager.lookAt(
            new Vector3f(0f, 0f, 0f)
        );

        viewPort.setBackgroundColor(
            new ColorRGBA(
                0.60f, 0.78f, 0.95f, 1f
            )
        );

        // Création de la scène complète
        worldScene = new WorldScene(
            assetManager,
            cam,
            viewPort,
            config
        );

        // Intégration au Scene Graph
        rootNode.attachChild(worldScene);

        // Initialisation des modules du monde
        worldScene.initialize();
    }

    @Override
    public void simpleUpdate(float tpf) {

        // Actualise le ciel et les chunks
        worldScene.update(tpf);
    }

    public WorldScene getWorldScene() {
        return worldScene;
    }
}
