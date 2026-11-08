package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;

import com.mygame.camera.CameraManager;
import com.mygame.config.GameConfig;
import com.mygame.scene.WorldScene;

/**
 * Application entry point for the desktop OpenWorld demo.
 *
 * <p>Loads {@code config.xml}, configures the jMonkeyEngine window, and creates the world scene.
 */
public class Main extends SimpleApplication {

    private final GameConfig config;
    private WorldScene worldScene;

    /**
     * Creates the application with the already validated runtime settings.
     *
     * @param config validated runtime configuration
     */
    public Main(GameConfig config) {
        super();
        this.config = config;
    }

    /**
     * Loads the root-level configuration and starts the desktop application.
     *
     * @param args command-line arguments; currently unused
     */
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

    /** Initializes camera controls and attaches the configured world scene. */
    @Override
    public void simpleInitApp() {
        // The world uses only two lights, so one shader pass is enough
        // instead of one pass per light in MultiPass mode.
        renderManager.setPreferredLightMode(TechniqueDef.LightMode.SinglePass);
        renderManager.setSinglePassLightBatchSize(2);

        CameraManager cameraManager = new CameraManager(cam, flyCam);
        cameraManager.initialize(config.cameraSpeed, config.cameraFar);
        cameraManager.setPosition(new Vector3f(0f, 45f, 75f));
        cameraManager.lookAt(Vector3f.ZERO);

        viewPort.setBackgroundColor(new ColorRGBA(0.60f, 0.78f, 0.95f, 1f));

        worldScene = new WorldScene(assetManager, cam, viewPort, config);
        rootNode.attachChild(worldScene);
        worldScene.initialize();
        // WorldScene updates through a Control invoked automatically by the scene graph,
        // so no simpleUpdate() override is needed.
    }

    /** Releases world resources before delegating shutdown to the engine. */
    @Override
    public void destroy() {
        if (worldScene != null) worldScene.cleanup();
        super.destroy();
    }

    /**
     * Returns the scene assembled during application initialization, or {@code null} beforehand.
     *
     * @return application world scene, or {@code null} before initialization
     */
    public WorldScene getWorldScene() {
        return worldScene;
    }
}