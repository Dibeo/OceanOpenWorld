package com.mygame.terrain;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GrassMaterialTest {
    @Test
    void grassMaterialDefinitionLoadsFromResources() {
        DesktopAssetManager assets = new DesktopAssetManager(true);
        Material material = new Material(assets, "MatDefs/Grass/Grass.j3md");
        material.setTexture("ColorMap", GrassTexture.createBladeMask());
        material.setVector2("WindDirection", new Vector2f(1f, 0f));
        material.setFloat("WindStrength", 0.5f);
        material.setFloat("Time", 0f);
        material.setVector3("SunDirection", new Vector3f(0f, -1f, 0f));
        material.setColor("SunColor", ColorRGBA.White);
        material.setColor("AmbientColor", ColorRGBA.White);

        assertNotNull(material);
    }
}
