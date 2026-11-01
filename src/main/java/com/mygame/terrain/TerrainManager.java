package com.mygame.terrain;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.mygame.config.GameConfig;
import com.mygame.texture.TextureLibrary;

/** Façade du terrain pour Main.java et les autres modules. */
public final class TerrainManager {
    /** Nom de la texture dans textures.xml. */
    private static final String SAND = "sand";
    private static final String TEXTURES_XML = "/textures.xml";

    private final Node rootNode;
    private final AssetManager assets;
    private final Camera camera;
    private final GameConfig config;
    private final Node terrainNode = new Node("InfiniteSandTerrain");
    private TerrainGenerator generator;
    private ChunkLoader loader;
    private TextureLibrary textures;
    private Material sand;
    private long seed;

    public TerrainManager(Node rootNode, AssetManager assets, Camera camera, GameConfig config) {
        this.rootNode = rootNode;
        this.assets = assets;
        this.camera = camera;
        this.config = config;
    }

    public void initialize(long seed) {
        this.seed = seed;
        generator = new TerrainGenerator(seed);

        // Textures procédurales décrites dans textures.xml, dépendantes de la seed du monde.
        textures = TextureLibrary.fromClasspath(TEXTURES_XML);
        textures.setSeed(seed);

        // Un seul Material partagé par tous les chunks (moins de changements d'état GPU).
        sand = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        sand.setBoolean("UseMaterialColors", true);
        sand.setBoolean("UseVertexColor", true);
        sand.setColor("Diffuse", ColorRGBA.White);
        sand.setColor("Ambient", ColorRGBA.White);
        // Sable : mat, avec un très léger éclat.
        sand.setColor("Specular", new ColorRGBA(0.10f, 0.095f, 0.08f, 1f));
        sand.setFloat("Shininess", 10f);
        applyTextures();

        if (terrainNode.getParent() == null) rootNode.attachChild(terrainNode);
        loader = new ChunkLoader(terrainNode, camera, sand, generator, seed, config,
                textures.getTileMeters(SAND));
        loader.update();
    }

    private void applyTextures() {
        sand.setTexture("DiffuseMap", textures.diffuse(SAND));
        sand.setTexture("NormalMap", textures.normal(SAND));
    }

    public void update(float tpf) { if (loader != null) loader.update(); }
    public float getHeight(float x, float z) { return generator.getHeight(x, z); }
    public long getSeed() { return seed; }
    public int getLoadedChunkCount() { return loader.getLoadedChunkCount(); }
    public Node getTerrainNode() { return terrainNode; }

    public void regenerate(long newSeed) {
        seed = newSeed;
        generator = new TerrainGenerator(newSeed);
        textures.setSeed(newSeed);   // textures régénérées pour la nouvelle seed
        applyTextures();
        loader.regenerate(generator, newSeed);
    }

    public void cleanup() {
        if (loader != null) loader.shutdown();
        terrainNode.removeFromParent();
    }
}