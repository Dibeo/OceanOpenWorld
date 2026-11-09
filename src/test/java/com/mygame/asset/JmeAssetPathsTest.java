package com.mygame.asset;

import com.jme3.asset.AssetKey;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies that custom material definitions and shader files resolve from the JME assets tree. */
final class JmeAssetPathsTest {
    /** Confirms that custom material definitions and shader files resolve from the JME assets tree. */
    @Test
    void customMaterialsAndShadersResolve() {
        DesktopAssetManager assets = new DesktopAssetManager(true);

        for (String materialPath : new String[] {
                "MatDefs/Clouds/Clouds.j3md",
                "MatDefs/Grass/Grass.j3md",
                "MatDefs/Rock/Rock.j3md",
                "MatDefs/Bark/Bark.j3md",
                "MatDefs/Leaves/Leaves.j3md"
        }) {
            assertNotNull(new Material(assets, materialPath), materialPath);
        }

        for (String shaderPath : new String[] {
                "Shaders/Clouds/Clouds.frag",
                "Shaders/Clouds/Clouds.vert",
                "Shaders/Grass/Grass.frag",
                "Shaders/Grass/Grass.vert",
                "Shaders/Rock/Rock.frag",
                "Shaders/Rock/Rock.vert",
                "Shaders/Bark/Bark.frag",
                "Shaders/Bark/Bark.vert",
                "Shaders/Leaves/Leaves.frag",
                "Shaders/Leaves/Leaves.vert"
        }) {
            assertNotNull(assets.locateAsset(new AssetKey<>(shaderPath)), shaderPath);
        }
    }
}
