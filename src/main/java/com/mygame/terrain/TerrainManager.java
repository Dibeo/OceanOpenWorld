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
    /** Noms des couches de texture dans textures.xml. */
    private static final String GRASS = "grass";
    private static final String SAND = "sand";
    private static final String GRAVEL = "gravel";
    private static final String TEXTURES_XML = "/textures.xml";

    private final Node rootNode;
    private final AssetManager assets;
    private final Camera camera;
    private final GameConfig config;
    private final Node terrainNode = new Node("InfiniteSandTerrain");
    private TerrainGenerator generator;
    private ChunkLoader loader;
    private TextureLibrary textures;
    private Material terrainMaterial;
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

        terrainMaterial = createMaterial();
        applyTextures();

        if (terrainNode.getParent() == null) rootNode.attachChild(terrainNode);
        loader = new ChunkLoader(terrainNode, camera, terrainMaterial, generator, config);
        loader.update();
    }

    private Material createMaterial() {
        Material material = new Material(assets, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        material.setBoolean("useTriPlanarMapping", false);
        material.setColor("Diffuse", ColorRGBA.White);
        material.setColor("Ambient", new ColorRGBA(0.32f, 0.32f, 0.32f, 1f));
        material.setColor("Specular", new ColorRGBA(0.08f, 0.08f, 0.08f, 1f));
        material.setFloat("Shininess", 8f);
        return material;
    }

    private void applyTextures() {
        String[] textureNames = { GRASS, SAND, GRAVEL };
        String[] diffuseParameters = { "DiffuseMap", "DiffuseMap_1", "DiffuseMap_2" };
        String[] normalParameters = { "NormalMap", "NormalMap_1", "NormalMap_2" };
        String[] scaleParameters = { "DiffuseMap_0_scale", "DiffuseMap_1_scale", "DiffuseMap_2_scale" };
        for (int i = 0; i < textureNames.length; i++) {
            String textureName = textureNames[i];
            terrainMaterial.setTexture(diffuseParameters[i], textures.diffuse(textureName));
            terrainMaterial.setTexture(normalParameters[i], textures.normal(textureName));
            terrainMaterial.setFloat(scaleParameters[i],
                    config.chunkSize / textures.getTileMeters(textureName));
        }
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
        loader.regenerate(generator);
    }

    public void cleanup() {
        if (loader != null) loader.shutdown();
        terrainNode.removeFromParent();
    }
}