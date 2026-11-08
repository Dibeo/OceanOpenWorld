package com.mygame.terrain;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.mygame.config.GameConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChunkLoaderTest {
    @Test
    void loadsChunksInRangeAndUnloadsThemAfterCameraMoves() throws Exception {
        DesktopAssetManager assets = new DesktopAssetManager(true);
        GameConfig config = GameConfig.load(Paths.get(
                ChunkLoaderTest.class.getResource("/chunk-loader-test-config.xml").toURI()).toString());
        Camera camera = new Camera(640, 480);
        camera.setLocation(Vector3f.ZERO);
        Node terrainNode = new Node("test-terrain");
        Material terrainMaterial = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        ChunkLoader loader = new ChunkLoader(terrainNode, camera, terrainMaterial, null, null,
                new TerrainGenerator(config.seed), config);

        try {
            loader.update();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (loader.getLoadedChunkCount() == 0 && System.nanoTime() < deadline) {
                loader.update();
                Thread.sleep(5);
            }

            assertEquals(1, loader.getLoadedChunkCount(), "the chunk around the camera should load");
            assertEquals(1, terrainNode.getQuantity(), "the generated terrain geometry should be attached");

            camera.setLocation(new Vector3f(100f, 0f, 0f));
            loader.update();

            assertEquals(0, loader.getLoadedChunkCount(), "the old chunk should be unloaded after moving away");
            assertEquals(0, terrainNode.getQuantity(), "unloaded terrain geometry should be detached");
        } finally {
            loader.shutdown();
        }
    }
}
