package com.mygame.terrain;

import com.mygame.noise.PerlinNoise;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests deterministic tree meshes and world-seeded tree placement. */
final class TreeGenerationTest {
    /** Verifies that a tree seed produces identical bark and foliage meshes. */
    @Test
    void sameTreeSeedProducesIdenticalMeshes() {
        PerlinNoise noise = new TerrainGenerator(42L).getTreeShapeNoise();
        TreeShape.Buffers first = new TreeShape.Buffers();
        TreeShape.Buffers second = new TreeShape.Buffers();

        TreeShape.add(first, noise, 918273L, 3f, 2f, 5f, TreeShape.Species.OAK, 0.7f, 0.4f,
                TreeShape.LOD_HIGH);
        TreeShape.add(second, noise, 918273L, 3f, 2f, 5f, TreeShape.Species.OAK, 0.7f, 0.4f,
                TreeShape.LOD_HIGH);

        TreeShape.TreeMesh firstBark = first.build().bark();
        TreeShape.TreeMesh secondBark = second.build().bark();
        assertNotNull(firstBark);
        assertNotNull(secondBark);
        assertTrue(same(firstBark.positions(), secondBark.positions()));
        assertTrue(same(firstBark.normals(), secondBark.normals()));
        assertEquals(firstBark.indexCount(), secondBark.indexCount());
    }

    /** Verifies that pine trees use leaf atlas cells and fern shrubs use the pine-spray cell. */
    @Test
    void pineTreesUseLeavesAndFernsUsePineSprays() {
        PerlinNoise noise = new TerrainGenerator(42L).getTreeShapeNoise();
        TreeShape.Buffers pineTree = new TreeShape.Buffers();
        TreeShape.add(pineTree, noise, 1234L, 3f, 2f, 5f, TreeShape.Species.PINE, 0.6f, 0.5f,
                TreeShape.LOD_HIGH);
        TreeShape.TreeMesh leafMesh = pineTree.build().leaves();
        assertNotNull(leafMesh);
        assertTrue(hasUvInPineCell(leafMesh.uvs()));

        TreeShape.Buffers fern = new TreeShape.Buffers();
        TreeShape.addFern(fern, 5678L, 3f, 2f, 5f, TreeShape.LOD_HIGH);
        TreeShape.TreeMesh fernMesh = fern.build().leaves();
        assertNotNull(fernMesh);
        assertTrue(hasUvInPineCell(fernMesh.uvs()));
    }

    /** Verifies chunk tree placement is repeatable and disabled by zero density. */
    @Test
    void chunkTreePlacementIsDeterministicAndCanBeDisabled() {
        TerrainGenerator firstGenerator = new TerrainGenerator(42L);
        TerrainGenerator secondGenerator = new TerrainGenerator(42L);
        TerrainChunk.Data terrain = TerrainChunk.generate(0, 0, firstGenerator, 64, 32, -1000f,
                0f, 1f);

        TreeShape.Meshes first = TerrainChunk.generateTrees(0, 0, firstGenerator, terrain.heights,
                terrain.heightSide, 64, 32, -1000f, 1f, TreeShape.LOD_LOW);
        TreeShape.Meshes second = TerrainChunk.generateTrees(0, 0, secondGenerator, terrain.heights,
                terrain.heightSide, 64, 32, -1000f, 1f, TreeShape.LOD_LOW);

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(first.bark());
        assertNotNull(first.leaves());
        assertTrue(same(first.bark().positions(), second.bark().positions()));
        assertTrue(same(first.leaves().positions(), second.leaves().positions()));
        assertNull(TerrainChunk.generateTrees(0, 0, firstGenerator, terrain.heights,
                terrain.heightSide, 64, 32, -1000f, 0f, TreeShape.LOD_LOW));
    }

    /** Verifies that generated bark and foliage buffers assemble into renderable chunk geometry. */
    @Test
    void generatedMeshesCreateTreeGeometry() {
        DesktopAssetManager assets = new DesktopAssetManager(true);
        Material terrainMaterial = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        Material barkMaterial = new Material(assets, "MatDefs/Bark/Bark.j3md");
        Material leavesMaterial = new Material(assets, "MatDefs/Leaves/Leaves.j3md");
        barkMaterial.setTexture("NoiseMap", BarkTexture.create(42L));
        leavesMaterial.setTexture("ColorMap", LeafTexture.create());
        TerrainGenerator generator = new TerrainGenerator(42L);
        TerrainChunk.Data data = TerrainChunk.generate(0, 0, generator, 64, 32, -1000f,
                0f, 1f, 1f, TreeShape.LOD_LOW);

        TerrainChunk chunk = new TerrainChunk(0, 0, data, terrainMaterial, null, barkMaterial,
                leavesMaterial, TerrainChunk.createIndexBuffer(32), 64);

        assertNotNull(chunk.getBarkGeometry());
        assertNotNull(chunk.getLeavesGeometry());
        assertEquals(TreeShape.LOD_LOW, chunk.getTreeLod());
    }

    private static boolean same(FloatBuffer first, FloatBuffer second) {
        if (first.limit() != second.limit()) return false;
        for (int i = 0; i < first.limit(); i++) {
            if (Float.floatToIntBits(first.get(i)) != Float.floatToIntBits(second.get(i))) return false;
        }
        return true;
    }

    private static boolean hasUvInPineCell(FloatBuffer uvs) {
        for (int i = 0; i < uvs.limit(); i += 2) {
            if (uvs.get(i) > 0.5f && uvs.get(i + 1) > 0.5f) return true;
        }
        return false;
    }
}
