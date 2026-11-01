package com.mygame.terrain;

import com.jme3.asset.AssetManager;
import com.mygame.config.GameConfig;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

/** Façade du terrain pour Main.java et les autres modules. */
public final class TerrainManager {
    private final Node rootNode;
    private final AssetManager assets;
    private final Camera camera;
    private final GameConfig config;
    private final Node terrainNode=new Node("InfiniteSandTerrain");
    private TerrainGenerator generator;
    private ChunkLoader loader;
    private long seed;

    public TerrainManager(Node rootNode,AssetManager assets,Camera camera,GameConfig config) {
        this.rootNode=rootNode;
        this.assets=assets;
        this.camera=camera;
        this.config=config;
    }

    public void initialize(long seed) {
        this.seed=seed;
        generator=new TerrainGenerator(seed);
        Material sand=new Material(assets,"Common/MatDefs/Light/Lighting.j3md");
        sand.setBoolean("UseMaterialColors",true);
        sand.setBoolean("UseVertexColor",true);
        sand.setColor("Diffuse",ColorRGBA.White);
        sand.setColor("Ambient",ColorRGBA.White);
        sand.setColor("Specular",ColorRGBA.Black);
        sand.setFloat("Shininess",1f);
        if(terrainNode.getParent()==null) rootNode.attachChild(terrainNode);
        loader=new ChunkLoader(terrainNode,camera,sand,generator,seed,config);
        loader.update();
    }

    public void update(float tpf) { if(loader!=null) loader.update(); }
    public float getHeight(float x,float z) { return generator.getHeight(x,z); }
    public long getSeed() { return seed; }
    public int getLoadedChunkCount() { return loader.getLoadedChunkCount(); }
    public Node getTerrainNode() { return terrainNode; }

    public void regenerate(long newSeed) {
        seed=newSeed;
        generator=new TerrainGenerator(newSeed);
        loader.regenerate(generator,newSeed);
    }
}
