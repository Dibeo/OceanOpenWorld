package com.mygame.terrain;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.mygame.config.GameConfig;
import com.mygame.texture.TextureLibrary;
import com.mygame.water.WindModel;

/**
 * Terrain facade used by Main.java and other modules to coordinate procedural materials,
 * chunk streaming, wind-driven grass, and world reseeding.
 */
public final class TerrainManager {
    /** Texture-layer names defined in textures.xml. */
    private static final String GRASS = "grass";
    private static final String SAND = "sand";
    private static final String GRAVEL = "gravel";
    /** Noise-blended green variants used by terrain layers 3 and 4. */
    private static final String GRASS_DRY = "grass_dry";
    private static final String GRASS_DEEP = "grass_deep";
    private static final String TEXTURES_XML = "/textures.xml";

    private final Node rootNode;
    private final AssetManager assets;
    private final Camera camera;
    private final GameConfig config;
    private final WindModel wind;
    private final Node terrainNode = new Node("InfiniteSandTerrain");
    private TerrainGenerator generator;
    private ChunkLoader loader;
    private TextureLibrary textures;
    private Material terrainMaterial;
    private Material grassMaterial;
    private Material rockMaterial;
    private long seed;
    private float elapsedTime;

    /**
     * Creates the terrain manager with its scene, asset, camera, configuration, and wind services.
     *
     * @param rootNode scene node receiving terrain
     * @param assets engine asset manager
     * @param camera camera used for chunk and grass streaming
     * @param config validated runtime settings
     * @param wind shared deterministic wind model
     */
    public TerrainManager(Node rootNode, AssetManager assets, Camera camera, GameConfig config,
                          WindModel wind) {
        this.rootNode = rootNode;
        this.assets = assets;
        this.camera = camera;
        this.config = config;
        this.wind = wind;
    }

    /**
     * Loads materials and textures, then begins loading terrain for the supplied seed.
     *
     * @param seed world seed
     */
    public void initialize(long seed) {
        this.seed = seed;
        generator = new TerrainGenerator(seed);

        // Procedural textures defined in textures.xml and derived from the world seed.
        textures = TextureLibrary.fromClasspath(TEXTURES_XML);
        textures.setSeed(seed);

        terrainMaterial = createMaterial();
        applyTextures();
        grassMaterial = createGrassMaterial();
        rockMaterial = createRockMaterial(seed);

        if (terrainNode.getParent() == null) rootNode.attachChild(terrainNode);
        loader = new ChunkLoader(terrainNode, camera, terrainMaterial, grassMaterial, rockMaterial, generator, config);
        loader.update();
    }

    private Material createGrassMaterial() {
        Material material = new Material(assets, "MatDefs/Grass/Grass.j3md");
        Texture2D[] atlas = GrassTexture.create();
        material.setTexture("ColorMap", atlas[0]);
        material.setTexture("FlowerMap", atlas[1]);
        // Grass shrinks to zero between these horizontal world-space distances, in meters.
        material.setFloat("FadeStart", config.grassDistance * 0.6f);
        material.setFloat("FadeEnd", config.grassDistance);
        material.setColor("AmbientColor", new ColorRGBA(0.38f, 0.39f, 0.32f, 1f));
        material.setColor("SunColor", new ColorRGBA(0.85f, 0.82f, 0.68f, 1f));
        material.setVector3("SunDirection", new Vector3f(-0.3f, -0.8f, -0.5f).normalizeLocal());
        Vector2f direction = wind.getDirection();
        material.setVector2("WindDirection", direction);
        material.setFloat("WindStrength", wind.getStrength());
        material.setFloat("Time", 0f);
        return material;
    }

    private Material createRockMaterial(long seed) {
        Material material = new Material(assets, "MatDefs/Rock/Rock.j3md");
        material.setTexture("NoiseMap", RockTexture.create(seed));
        material.setColor("AmbientColor", new ColorRGBA(0.38f, 0.39f, 0.36f, 1f));
        material.setColor("SunColor", new ColorRGBA(0.85f, 0.82f, 0.68f, 1f));
        material.setVector3("SunDirection", new Vector3f(-0.3f, -0.8f, -0.5f).normalizeLocal());
        material.setFloat("TextureScale", 1.6f);    // meters per repeat of the triplanar texture
        material.setFloat("BumpStrength", 0.9f);
        return material;
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
        String[] textureNames = { GRASS, SAND, GRAVEL, GRASS_DRY, GRASS_DEEP };
        String[] diffuseParameters = { "DiffuseMap", "DiffuseMap_1", "DiffuseMap_2", "DiffuseMap_3", "DiffuseMap_4" };
        String[] normalParameters = { "NormalMap", "NormalMap_1", "NormalMap_2", "NormalMap_3", "NormalMap_4" };
        String[] scaleParameters = { "DiffuseMap_0_scale", "DiffuseMap_1_scale", "DiffuseMap_2_scale",
                "DiffuseMap_3_scale", "DiffuseMap_4_scale" };
        for (int i = 0; i < textureNames.length; i++) {
            String textureName = textureNames[i];
            terrainMaterial.setTexture(diffuseParameters[i], textures.diffuse(textureName));
            terrainMaterial.setTexture(normalParameters[i], textures.normal(textureName));
            terrainMaterial.setFloat(scaleParameters[i],
                    config.chunkSize / textures.getTileMeters(textureName));
        }
    }

    /**
     * Updates chunk streaming, grass animation time, and current wind uniforms.
     *
     * @param tpf elapsed frame time in seconds
     */
    public void update(float tpf) {
        if (loader != null) loader.update();
        elapsedTime = (elapsedTime + tpf) % 10000f;
        if (grassMaterial != null) {
            grassMaterial.setFloat("Time", elapsedTime);
            updateWindUniforms();
        }
    }

    private void updateWindUniforms() {
        Vector2f direction = wind.getDirection();
        grassMaterial.setVector2("WindDirection", direction);
        grassMaterial.setFloat("WindStrength", wind.getStrength());
    }

    /**
     * Updates the sun-ray direction used by terrain vegetation and rock materials.
     *
     * @param direction direction in which sun rays travel
     */
    public void setSunDirection(Vector3f direction) {
        if (grassMaterial != null) grassMaterial.setVector3("SunDirection", direction);
        if (rockMaterial != null) rockMaterial.setVector3("SunDirection", direction);
    }

    /**
     * Updates the sun color used by terrain vegetation and rock materials.
     *
     * @param color emitted sun color
     */
    public void setSunColor(ColorRGBA color) {
        if (grassMaterial != null) grassMaterial.setColor("SunColor", color);
        if (rockMaterial != null) rockMaterial.setColor("SunColor", color);
    }

    /**
     * Returns the current procedural terrain height at world-space X and Z coordinates.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return terrain height on world Y
     */
    public float getHeight(float x, float z) { return generator.getHeight(x, z); }
    /**
     * Returns the seed used by the current terrain generation.
     *
     * @return current world seed
     */
    public long getSeed() { return seed; }
    /**
     * Returns the number of terrain chunks currently loaded.
     *
     * @return loaded chunk count
     */
    public int getLoadedChunkCount() { return loader.getLoadedChunkCount(); }
    /**
     * Returns the scene node containing terrain, grass, and rock geometries.
     *
     * @return terrain scene node
     */
    public Node getTerrainNode() { return terrainNode; }

    /**
     * Rebuilds procedural terrain and textures for a new world seed.
     *
     * @param newSeed new world seed
     */
    public void regenerate(long newSeed) {
        seed = newSeed;
        generator = new TerrainGenerator(newSeed);
        textures.setSeed(newSeed);   // regenerate textures for the new seed
        rockMaterial.setTexture("NoiseMap", RockTexture.create(newSeed));
        applyTextures();
        loader.regenerate(generator);
    }

    /** Stops chunk workers and removes terrain geometry from its parent node. */
    public void cleanup() {
        if (loader != null) loader.shutdown();
        terrainNode.detachAllChildren();
        loader = null;
        terrainNode.removeFromParent();
    }
}