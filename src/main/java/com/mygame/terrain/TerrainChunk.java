package com.mygame.terrain;

import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.mygame.noise.PerlinNoise;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Random;


/**
 * Renderable terrain chunk assembled from worker-generated mesh data. {@link #generate}
 * performs the pure height, normal, tangent, UV, color, and blend-map calculations in direct
 * buffers on a worker thread; the constructor creates engine meshes and geometry on the render thread.
 *
 * <p>Call {@link #generate(int, int, TerrainGenerator, int, int, float, float, float)} on a
 * worker thread, then construct the chunk and attach its geometries on the render thread.
 */
public final class TerrainChunk {

    /** Buffers and sampled heights produced by terrain generation for later mesh assembly. */
    public static final class Data {
        final FloatBuffer positions, normals, tangents, uvs;
        final ByteBuffer blendMap, blendMap1;
        final int blendMapSize;
        final float minY, maxY;
        final float[] heights;
        final int heightSide;
        final RockShape.RockData rocks;

        Data(FloatBuffer positions, FloatBuffer normals, FloatBuffer tangents, FloatBuffer uvs,
             ByteBuffer blendMap, ByteBuffer blendMap1, int blendMapSize, float minY, float maxY,
             float[] heights, int heightSide, RockShape.RockData rocks) {
            this.positions = positions; this.normals = normals; this.tangents = tangents;
            this.uvs = uvs;
            this.blendMap = blendMap; this.blendMap1 = blendMap1; this.blendMapSize = blendMapSize;
            this.minY = minY; this.maxY = maxY;
            this.heights = heights; this.heightSide = heightSide; this.rocks = rocks;
        }
    }

    /**
     * Grass mesh data for a chunk. Per-vertex {@code params} contain blade height, atlas pattern,
     * and base Y; {@code flowers} contain palette index and variation plus a 1/0 flowering flag.
     */
    static final class GrassData {
        final FloatBuffer positions, normals, colors, uvs, params, flowers;
        final IntBuffer indices;
        final ShortBuffer shortIndices;
        final int indexCount;
        final float minY, maxY;

        GrassData(FloatBuffer positions, FloatBuffer normals, FloatBuffer colors, FloatBuffer uvs,
                  FloatBuffer params, FloatBuffer flowers, IntBuffer indices, ShortBuffer shortIndices, int indexCount,
                  float minY, float maxY) {
            this.positions = positions; this.normals = normals; this.colors = colors; this.uvs = uvs;
            this.params = params; this.flowers = flowers;
            this.indices = indices; this.shortIndices = shortIndices; this.indexCount = indexCount;
            this.minY = minY; this.maxY = maxY;
        }
    }

    private final int chunkX, chunkZ, size;
    private final float[] heights;
    private final int heightSide;
    private final Geometry geometry;
    private Geometry grassGeometry;
    private final Geometry rockGeometry;
    private boolean grassBuilt;

    /**
     * Builds terrain and optional rock geometry for one chunk.
     *
     * @param chunkX horizontal chunk coordinate on the world X axis
     * @param chunkZ horizontal chunk coordinate on the world Z axis
     * @param data generated mesh and surface data
     * @param material terrain material to clone for the chunk
     * @param rockMaterial material used for generated rocks
     * @param sharedIndices index buffer shared by terrain chunks of this resolution
     * @param size chunk width and depth in world units
     */
    public TerrainChunk(int chunkX, int chunkZ, Data data, Material material, Material rockMaterial,
                        VertexBuffer sharedIndices, int size) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.size = size;
        // Retain these few kilobytes so grass can be generated on demand as the camera approaches.
        this.heights = data.heights;
        this.heightSide = data.heightSide;

        float half = size * 0.5f;
        float halfY = Math.max(0.01f, (data.maxY - data.minY) * 0.5f);
        Mesh mesh = new Mesh();
        mesh.setBuffer(vertexBuffer(Type.Position, 3, Format.Float, data.positions));
        mesh.setBuffer(vertexBuffer(Type.Normal, 3, Format.Float, data.normals));
        mesh.setBuffer(vertexBuffer(Type.Tangent, 4, Format.Float, data.tangents));
        mesh.setBuffer(vertexBuffer(Type.TexCoord, 2, Format.Float, data.uvs));
        mesh.setBuffer(sharedIndices);
        mesh.setBound(new BoundingBox(new Vector3f(half, (data.minY + data.maxY) * 0.5f, half),
                half, halfY, half));
        mesh.updateCounts();

        Material chunkMaterial = material.clone();
        chunkMaterial.setTexture("AlphaMap", blendTexture(data.blendMap, data.blendMapSize));
        chunkMaterial.setTexture("AlphaMap_1", blendTexture(data.blendMap1, data.blendMapSize));

        geometry = new Geometry("Chunk_" + chunkX + "_" + chunkZ, mesh);
        geometry.setMaterial(chunkMaterial);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.setLocalTranslation((float) ((double) chunkX * size), 0f, (float) ((double) chunkZ * size));

        rockGeometry = (data.rocks == null || data.rocks.indexCount == 0) ? null
                : createRockGeometry(chunkX, chunkZ, data.rocks, rockMaterial, size);
    }

    private static Geometry createRockGeometry(int chunkX, int chunkZ, RockShape.RockData data,
                                               Material material, int size) {
        Mesh mesh = new Mesh();
        mesh.setBuffer(vertexBuffer(Type.Position, 3, Format.Float, data.positions));
        mesh.setBuffer(vertexBuffer(Type.Normal, 3, Format.Float, data.normals));
        mesh.setBuffer(vertexBuffer(Type.Color, 4, Format.Float, data.colors));
        mesh.setBuffer(data.shortIndices != null
                ? vertexBuffer(Type.Index, 3, Format.UnsignedShort, data.shortIndices)
                : vertexBuffer(Type.Index, 3, Format.UnsignedInt, data.indices));
        Vector3f min = new Vector3f(data.minX, data.minY, data.minZ), max = new Vector3f(data.maxX, data.maxY, data.maxZ);
        mesh.setBound(new BoundingBox(min.add(max).multLocal(0.5f), (max.x - min.x) * 0.5f + 0.05f,
                (max.y - min.y) * 0.5f + 0.05f, (max.z - min.z) * 0.5f + 0.05f));
        mesh.updateCounts();
        Geometry rocks = new Geometry("Rocks_" + chunkX + "_" + chunkZ, mesh);
        rocks.setMaterial(material);
        rocks.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        rocks.setLocalTranslation((float) ((double) chunkX * size), 0f, (float) ((double) chunkZ * size));
        return rocks;
    }

    /**
     * Builds grass geometry on the render thread. Null or empty data marks the chunk as built
     * without geometry, preventing repeated generation attempts.
     */
    Geometry buildGrass(GrassData data, Material material) {
        grassBuilt = true;
        grassGeometry = (data == null || data.indexCount == 0) ? null
                : createGrassGeometry(chunkX, chunkZ, data, material, size);
        return grassGeometry;
    }

    /** Releases grass while retaining terrain; it will be regenerated identically if the camera returns. */
    void clearGrass() {
        if (grassGeometry != null) grassGeometry.removeFromParent();
        grassGeometry = null;
        grassBuilt = false;
    }

    /**
     * Returns the optional rock geometry, or {@code null} when no rocks were generated.
     *
     * @return rock geometry, or {@code null}
     */
    public Geometry getRockGeometry() { return rockGeometry; }

    boolean isGrassBuilt() { return grassBuilt; }
    float[] getHeights() { return heights; }
    int getHeightSide() { return heightSide; }

    private static Geometry createGrassGeometry(int chunkX, int chunkZ, GrassData data,
                                                Material material, int size) {
        Mesh mesh = new Mesh();
        mesh.setBuffer(vertexBuffer(Type.Position, 3, Format.Float, data.positions));
        mesh.setBuffer(vertexBuffer(Type.Normal, 3, Format.Float, data.normals));
        mesh.setBuffer(vertexBuffer(Type.Color, 4, Format.Float, data.colors));
        mesh.setBuffer(vertexBuffer(Type.TexCoord, 2, Format.Float, data.uvs));
        mesh.setBuffer(vertexBuffer(Type.TexCoord2, 3, Format.Float, data.params));
        mesh.setBuffer(vertexBuffer(Type.TexCoord3, 2, Format.Float, data.flowers));
        VertexBuffer indices = data.shortIndices != null
                ? vertexBuffer(Type.Index, 3, Format.UnsignedShort, data.shortIndices)
                : vertexBuffer(Type.Index, 3, Format.UnsignedInt, data.indices);
        mesh.setBuffer(indices);
        // Include a margin for clump width and wind displacement.
        float half = size * 0.5f + 0.8f;
        mesh.setBound(new BoundingBox(new Vector3f(size * 0.5f, (data.minY + data.maxY) * 0.5f,
                size * 0.5f), half, (data.maxY - data.minY) * 0.5f + 0.3f, half));
        mesh.updateCounts();
        Geometry geometry = new Geometry("Grass_" + chunkX + "_" + chunkZ, mesh);
        geometry.setMaterial(material);
        geometry.setShadowMode(RenderQueue.ShadowMode.Off);
        geometry.setLocalTranslation((float) ((double) chunkX * size), 0f,
                (float) ((double) chunkZ * size));
        return geometry;
    }

    private static Texture2D blendTexture(ByteBuffer pixels, int size) {
        ArrayList<ByteBuffer> data = new ArrayList<>(1);
        data.add(pixels);
        Texture2D texture = new Texture2D(new Image(Image.Format.RGBA8, size, size, 0, data,
                new int[] { size * size * 4 }, ColorSpace.Linear));
        texture.setWrap(Texture.WrapMode.Clamp);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        return texture;
    }

    private static VertexBuffer vertexBuffer(Type type, int components, Format format, java.nio.Buffer data) {
        VertexBuffer vb = new VertexBuffer(type);
        vb.setupData(Usage.Static, components, format, data);
        return vb;
    }

    /**
     * Creates a triangle index buffer for a regular square grid.
     *
     * @param subdivisions number of grid cells along each edge
     * @return indexed two-triangle cells for a {@code (subdivisions + 1)}-vertex grid
     */
    public static VertexBuffer createIndexBuffer(int subdivisions) {
        int side = subdivisions + 1;
        boolean useShort = side * side <= 65536;
        ShortBuffer shortIndices = useShort ? BufferUtils.createShortBuffer(subdivisions * subdivisions * 6) : null;
        IntBuffer intIndices = useShort ? null : BufferUtils.createIntBuffer(subdivisions * subdivisions * 6);
        for (int z = 0; z < subdivisions; z++) {
            for (int x = 0; x < subdivisions; x++) {
                int a = z * side + x, b = a + 1, c = a + side, d = c + 1;
                int[] cell = { a, c, b, b, c, d };
                for (int index : cell) {
                    if (useShort) shortIndices.put((short) index);
                    else intIndices.put(index);
                }
            }
        }
        if (useShort) {
            shortIndices.flip();
            return vertexBuffer(Type.Index, 3, Format.UnsignedShort, shortIndices);
        }
        intIndices.flip();
        return vertexBuffer(Type.Index, 3, Format.UnsignedInt, intIndices);
    }

    /**
     * Computes chunk vertex data, blend maps, and optional rocks without creating engine scene
     * objects. Samples a {@code (side + 2) × (side + 2)} height grid once, deriving normals from
     * neighboring samples. World-space sampling keeps shared borders consistent across chunks.
     * The RGB blend map stores continuous weights for base grass (R), sand (G), and gravel (B).
     *
     * @param chunkX horizontal chunk coordinate on the world X axis
     * @param chunkZ horizontal chunk coordinate on the world Z axis
     * @param generator seeded world terrain generator
     * @param size chunk width and depth in world units
     * @param subdivisions grid-cell count along each chunk edge
     * @param waterHeight world-space water-plane height used for surface blending
     * @param rockDensity requested density of main rocks in rocky areas
     * @param rockSizeScale rock size multiplier
     * @return generated buffers and height samples
     */
    public static Data generate(int chunkX, int chunkZ, TerrainGenerator generator,
                                int size, int subdivisions, float waterHeight,
                                float rockDensity, float rockSizeScale) {
        final int side = subdivisions + 1;
        final int hs = side + 2;
        final float step = (float) size / subdivisions;
        final double originX = (double) chunkX * size;
        final double originZ = (double) chunkZ * size;

        float[] heights = new float[hs * hs];
        for (int j = 0; j < hs; j++) {
            double wz = originZ + (j - 1) * step;
            for (int i = 0; i < hs; i++) {
                heights[j * hs + i] = generator.getHeight(originX + (i - 1) * step, wz);
            }
        }

        FloatBuffer positions = BufferUtils.createFloatBuffer(side * side * 3);
        FloatBuffer normals = BufferUtils.createFloatBuffer(side * side * 3);
        FloatBuffer tangents = BufferUtils.createFloatBuffer(side * side * 4);
        FloatBuffer uvs = BufferUtils.createFloatBuffer(side * side * 2);
        ByteBuffer blendMap = BufferUtils.createByteBuffer(side * side * 4);
        ByteBuffer blendMap1 = BufferUtils.createByteBuffer(side * side * 4);
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        final float ny = 2f * step;

        final float uvStep = 1f / side;

        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int c = (z + 1) * hs + (x + 1);
                float h = heights[c];
                if (h < minY) minY = h;
                if (h > maxY) maxY = h;
                positions.put(x * step).put(h).put(z * step);

                // Normal
                float nx = heights[c - 1] - heights[c + 1];
                float nz = heights[c - hs] - heights[c + hs];
                float inv = 1f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                float nxn = nx * inv, nyn = ny * inv, nzn = nz * inv;
                normals.put(nxn).put(nyn).put(nzn);

                // Tangent along +X (u), orthogonalized against the normal.
                // w = -1 makes the bitangent point along +Z (v), following OpenGL's upward-vertex convention.
                float tx = 1f, ty = (heights[c + 1] - heights[c - 1]) / (2f * step), tz = 0f;
                float dot = tx * nxn + ty * nyn + tz * nzn;
                tx -= nxn * dot; ty -= nyn * dot; tz -= nzn * dot;
                float tl = 1f / (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                tangents.put(tx * tl).put(ty * tl).put(tz * tl).put(-1f);

                uvs.put((x + 0.5f) * uvStep).put((z + 0.5f) * uvStep);

                float[] weights = surfaceWeights(h, waterHeight);
                float grassAndSand = weights[0] + weights[1];
                // AlphaMap: R base grass, G sand, B gravel, A dry grass.
                // AlphaMap_1: R deep-green grass. Green variants blend over the base color only
                // where grass is present, using seamless world-space Perlin weights.
                double worldX = originX + x * step, worldZ = originZ + z * step;
                float grassShare = weights[0];
                blendMap.put((byte) 255)
                        .put(toByte(grassAndSand > 0f ? weights[1] / grassAndSand : 0f))
                        .put(toByte(weights[2]))
                        .put(toByte(generator.getGrassDryness(worldX, worldZ) * grassShare));
                blendMap1.put(toByte(generator.getGrassDeepness(worldX, worldZ) * grassShare))
                        .put((byte) 0).put((byte) 0).put((byte) 0);
            }
        }
        positions.flip();
        normals.flip();
        tangents.flip();
        uvs.flip();
        blendMap.flip();
        blendMap1.flip();
        RockShape.RockData rocks = generateRocks(chunkX, chunkZ, generator, heights, hs, size, subdivisions,
                waterHeight, rockDensity, rockSizeScale);
        return new Data(positions, normals, tangents, uvs, blendMap, blendMap1, side, minY, maxY, heights, hs, rocks);
    }

    private static final float ROCK_CELL = 8f;

    /**
     * Generates deterministic chunk rocks from the world seed and chunk coordinates. Each rock
     * receives its own seed in a fixed order. Candidates are jittered within 8 m cells; their
     * probability follows the Perlin rocky-area field and increases with slope. Large and medium
     * rocks are surrounded by pebbles.
     */
    static RockShape.RockData generateRocks(int chunkX, int chunkZ, TerrainGenerator generator,
                                            float[] heights, int heightSide, int size, int subdivisions,
                                            float waterHeight, float density, float sizeScale) {
        if (density <= 0f) return null;
        final float terrainStep = (float) size / subdivisions;
        final double originX = (double) chunkX * size, originZ = (double) chunkZ * size;
        final Random random = new Random(generator.getRockChunkSeed(chunkX, chunkZ));
        final PerlinNoise shapeNoise = generator.getRockShapeNoise();
        final RockShape.Buffers out = new RockShape.Buffers();
        final int cells = (int) Math.ceil(size / ROCK_CELL);
        final float baseProbability = density * ROCK_CELL * ROCK_CELL / 1000f;

        for (int cz = 0; cz < cells; cz++) {
            for (int cx = 0; cx < cells; cx++) {
                // Consume random values in the same order even when a cell is rejected.
                float localX = (cx + random.nextFloat()) * ROCK_CELL;
                float localZ = (cz + random.nextFloat()) * ROCK_CELL;
                float acceptance = random.nextFloat();
                float sizePick = random.nextFloat();
                float sizeRoll = random.nextFloat();
                long rockSeed = random.nextLong();
                if (localX >= size || localZ >= size) continue;

                float y = sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, localZ);
                if (y < waterHeight + 0.1f) continue;
                double worldX = originX + localX, worldZ = originZ + localZ;
                float field = generator.getRockField(worldX, worldZ);
                if (field <= 0f) continue;

                // Rocks accumulate on slopes.
                float e = 1.0f;
                float xl = Math.max(0f, localX - e), xr = Math.min(size, localX + e);
                float zl = Math.max(0f, localZ - e), zr = Math.min(size, localZ + e);
                float dhx = (sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, xl, localZ)
                        - sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, xr, localZ)) / (xr - xl);
                float dhz = (sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, zl)
                        - sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, zr)) / (zr - zl);
                float flatness = 1f / (float) Math.sqrt(dhx * dhx + 1f + dhz * dhz);   // 1 = plat
                float probability = baseProbability * field * 3f * (1f + 3f * (1f - flatness));
                if (acceptance >= Math.min(1f, probability)) continue;

                float radius;
                if (sizePick < 0.20f) radius = 0.9f + 1.3f * sizeRoll;          // bloc
                else if (sizePick < 0.55f) radius = 0.35f + 0.45f * sizeRoll;   // rocher moyen
                else radius = 0.08f + 0.17f * sizeRoll;                         // caillou
                radius *= sizeScale;

                // Add moss only on grass-covered ground sufficiently far above the water.
                float moss = y > waterHeight + 1.2f ? generator.getGrassDensity(worldX, worldZ) : 0f;
                placeRock(out, shapeNoise, rockSeed, heights, heightSide, subdivisions, terrainStep, size,
                        localX, localZ, radius, moss);

                if (radius >= 0.25f * sizeScale) {
                    int companions = 2 + random.nextInt(5);
                    for (int i = 0; i < companions; i++) {
                        float angle = random.nextFloat() * TWO_PI;
                        float distance = radius * (1.1f + 1.6f * random.nextFloat());
                        float smallRadius = (0.05f + 0.17f * random.nextFloat()) * sizeScale;
                        long smallSeed = random.nextLong();
                        float px = localX + (float) Math.cos(angle) * distance;
                        float pz = localZ + (float) Math.sin(angle) * distance;
                        if (px < 0f || pz < 0f || px >= size || pz >= size) continue;
                        float py = sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, px, pz);
                        if (py < waterHeight + 0.1f) continue;
                        placeRock(out, shapeNoise, smallSeed, heights, heightSide, subdivisions, terrainStep, size,
                                px, pz, smallRadius, moss);
                    }
                }
            }
        }
        return out.isEmpty() ? null : out.build();
    }

    /** Places a rock on the terrain using its lowest of five support points, then embeds it by one third. */
    private static void placeRock(RockShape.Buffers out, PerlinNoise noise, long seed, float[] heights,
                                  int heightSide, int subdivisions, float terrainStep, int size,
                                  float x, float z, float radius, float moss) {
        float reach = radius * 0.7f;
        float ground = sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, x, z);
        float[][] offsets = {{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}};
        for (float[] o : offsets) {
            float sx = Math.max(0f, Math.min(size, x + o[0])), sz = Math.max(0f, Math.min(size, z + o[1]));
            ground = Math.min(ground, sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, sx, sz));
        }
        RockShape.add(out, noise, seed, x, ground + radius * 0.12f, z, radius, moss);
    }

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final int VERTICES_PER_TUFT = 12;   // 2 planes × 3 rows of 2 vertices
    private static final int FLOWER_COLORS = 6;          // shader palette: white, yellow, pink, purple, red, blue
    private static final int VARIANTS = 4;               // clump patterns in the GrassTexture atlas

    /**
     * Generates deterministic grass clumps for a chunk from the world seed and chunk coordinates,
     * so unloading and regenerating a chunk restores the same grass. Each clump has two crossed
     * planes, each curved in two segments, and carries blades from one of the {@value #VARIANTS}
     * {@link GrassTexture} patterns. Clump candidates are jittered within a grid whose cell width
     * is {@code 1 / sqrt(density)} meters.
     */
    static GrassData generateGrass(int chunkX, int chunkZ, TerrainGenerator generator,
                                   float[] heights, int heightSide, int size, int subdivisions,
                                   float waterHeight, float tuftsPerSquareMeter, float flowerAmount) {
        final float cellSize = (float) (1.0 / Math.sqrt(tuftsPerSquareMeter));
        final int cells = (int) Math.ceil(size / cellSize);
        final float terrainStep = (float) size / subdivisions;
        final double originX = (double) chunkX * size, originZ = (double) chunkZ * size;
        final Random random = new Random(generator.getGrassChunkSeed(chunkX, chunkZ));
        final GrassBuffers out = new GrassBuffers(Math.min(cells * cells, 4096) * VERTICES_PER_TUFT);
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        final float e = 0.6f;   // half-step for estimating the local slope

        for (int cz = 0; cz < cells; cz++) {
            for (int cx = 0; cx < cells; cx++) {
                float localX = (cx + random.nextFloat()) * cellSize;
                float localZ = (cz + random.nextFloat()) * cellSize;
                if (localX >= size || localZ >= size) continue;

                float y = sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, localZ);
                float grassWeight = grassWeight(y, waterHeight);
                if (grassWeight <= 0f) continue;

                double worldX = originX + localX, worldZ = originZ + localZ;
                if (random.nextFloat() >= grassWeight * generator.getGrassDensity(worldX, worldZ)) continue;

                // Use the terrain normal for lighting; omit grass on steep slopes.
                float xl = Math.max(0f, localX - e), xr = Math.min(size, localX + e);
                float zl = Math.max(0f, localZ - e), zr = Math.min(size, localZ + e);
                float nx = (sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, xl, localZ)
                        - sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, xr, localZ)) / (xr - xl);
                float nz = (sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, zl)
                        - sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, zr)) / (zr - zl);
                float inv = 1f / (float) Math.sqrt(nx * nx + 1f + nz * nz);
                nx *= inv; nz *= inv;
                if (random.nextFloat() >= smooth(0.6f, 0.85f, inv)) continue;

                float dry = generator.getGrassDryness(worldX, worldZ);
                float deep = generator.getGrassDeepness(worldX, worldZ);
                float lush = 1f - dry;
                float angle = random.nextFloat() * TWO_PI;
                float halfWidth = 0.5f * (0.62f + 0.40f * random.nextFloat()) * (0.85f + 0.30f * lush);
                float height = (0.38f + 0.42f * random.nextFloat()) * (0.75f + 0.25f * grassWeight)
                        * (0.85f + 0.25f * lush);
                float phase = random.nextFloat();
                float bright = 0.72f + 0.48f * random.nextFloat();
                // Match the ground palette: yellowed on one side and deeper, bluer on the other.
                float r = (1f + 0.35f * dry - 0.30f * deep) * bright;
                float g = (1f + 0.05f * dry - 0.12f * deep) * bright;
                float b = (1f - 0.45f * dry + 0.05f * deep) * bright;

                // Flowering follows the flower-field noise; flower color follows the palette noise
                // in same-color patches, with a different color on one flower in five.
                float flowerChance = flowerAmount * (0.04f + 0.96f * generator.getFlowerField(worldX, worldZ));
                boolean flowering = random.nextFloat() < flowerChance;
                int palette = Math.min(FLOWER_COLORS - 1, (int) (generator.getFlowerHue(worldX, worldZ) * FLOWER_COLORS));
                if (random.nextFloat() < 0.2f) palette = random.nextInt(FLOWER_COLORS);
                float jitter = 0.99f * random.nextFloat();     // brightness variation within the palette
                out.flowers(palette + jitter, flowering ? 1f : 0f);

                float baseY = y - 0.03f;                 // embed slightly to avoid gaps on slopes
                minY = Math.min(minY, baseY);
                for (int plane = 0; plane < 2; plane++) {
                    // Keep planes almost, but not exactly, perpendicular to avoid a regular cross.
                    float planeAngle = angle + plane * (float) (Math.PI * 0.5) + (random.nextFloat() - 0.5f) * 0.4f;
                    float planeWidth = halfWidth * (0.9f + 0.2f * random.nextFloat());
                    float dx = (float) Math.cos(planeAngle) * planeWidth;
                    float dz = (float) Math.sin(planeAngle) * planeWidth;
                    // Bend each plane in its own direction so blades form a fountain and remain
                    // visible from above.
                    float leanAngle = random.nextFloat() * TWO_PI;
                    float lean = height * (0.10f + 0.30f * random.nextFloat());
                    float lx = (float) Math.cos(leanAngle) * lean, lz = (float) Math.sin(leanAngle) * lean;
                    float topY = baseY + (float) Math.sqrt(height * height - lean * lean);   // preserve blade length
                    float midY = baseY + 0.5f * (topY - baseY);
                    maxY = Math.max(maxY, topY);
                    float variant = random.nextInt(VARIANTS);
                    // Parabolic bend: at half height, the blade has moved only one quarter of its total offset.
                    float mx = lx * 0.25f, mz = lz * 0.25f;
                    int base = out.vertexCount();
                    out.vertex(localX - dx, baseY, localZ - dz, nx, inv, nz, r, g, b, phase, 0f, 0f, height, variant, baseY);
                    out.vertex(localX + dx, baseY, localZ + dz, nx, inv, nz, r, g, b, phase, 1f, 0f, height, variant, baseY);
                    out.vertex(localX - dx + mx, midY, localZ - dz + mz, nx, inv, nz, r, g, b, phase, 0f, 0.5f, height, variant, baseY);
                    out.vertex(localX + dx + mx, midY, localZ + dz + mz, nx, inv, nz, r, g, b, phase, 1f, 0.5f, height, variant, baseY);
                    out.vertex(localX - dx + lx, topY, localZ - dz + lz, nx, inv, nz, r, g, b, phase, 0f, 1f, height, variant, baseY);
                    out.vertex(localX + dx + lx, topY, localZ + dz + lz, nx, inv, nz, r, g, b, phase, 1f, 1f, height, variant, baseY);
                    out.triangle(base, base + 1, base + 2);
                    out.triangle(base + 2, base + 1, base + 3);
                    out.triangle(base + 2, base + 3, base + 4);
                    out.triangle(base + 4, base + 3, base + 5);
                }
            }
        }
        return out.build(minY, maxY);
    }

    /** Growable buffers whose final size depends on the number of accepted clumps. */
    private static final class GrassBuffers {
        private float[] positions, normals, colors, uvs, params, flowers;
        private int[] indices;
        private int vertices, indexCount;
        private float flowerColor, flowerOn;   // applied to subsequent vertices

        GrassBuffers(int initialVertices) {
            allocate(Math.max(initialVertices, VERTICES_PER_TUFT));
        }

        private void allocate(int capacity) {
            positions = new float[capacity * 3];
            normals = new float[capacity * 3];
            colors = new float[capacity * 4];
            uvs = new float[capacity * 2];
            params = new float[capacity * 3];
            flowers = new float[capacity * 2];
            indices = new int[capacity * 2 + 12];
        }

        private void ensure() {
            int capacity = positions.length / 3;
            if (vertices + 1 <= capacity) return;
            int grown = Math.max(capacity + capacity / 2, vertices + 1);
            positions = java.util.Arrays.copyOf(positions, grown * 3);
            normals = java.util.Arrays.copyOf(normals, grown * 3);
            colors = java.util.Arrays.copyOf(colors, grown * 4);
            uvs = java.util.Arrays.copyOf(uvs, grown * 2);
            params = java.util.Arrays.copyOf(params, grown * 3);
            flowers = java.util.Arrays.copyOf(flowers, grown * 2);
            indices = java.util.Arrays.copyOf(indices, grown * 2 + 12);
        }

        int vertexCount() { return vertices; }

        /** Current clump flower data: palette index and variation, plus the flowering flag. */
        void flowers(float colorIndex, float on) { flowerColor = colorIndex; flowerOn = on; }

        void vertex(float x, float y, float z, float nx, float ny, float nz,
                    float r, float g, float b, float phase, float u, float v, float height,
                    float variant, float baseY) {
            ensure();
            int p = vertices * 3, c = vertices * 4, t = vertices * 2;
            positions[p] = x; positions[p + 1] = y; positions[p + 2] = z;
            normals[p] = nx; normals[p + 1] = ny; normals[p + 2] = nz;
            colors[c] = r; colors[c + 1] = g; colors[c + 2] = b; colors[c + 3] = phase;
            uvs[t] = u; uvs[t + 1] = v;
            params[p] = height; params[p + 1] = variant; params[p + 2] = baseY;
            flowers[t] = flowerColor; flowers[t + 1] = flowerOn;
            vertices++;
        }

        void triangle(int a, int b, int c) {
            if (indexCount + 3 > indices.length) indices = java.util.Arrays.copyOf(indices, indices.length * 2);
            indices[indexCount++] = a; indices[indexCount++] = b; indices[indexCount++] = c;
        }

        GrassData build(float minY, float maxY) {
            FloatBuffer positionBuffer = BufferUtils.createFloatBuffer(vertices * 3);
            positionBuffer.put(positions, 0, vertices * 3).flip();
            FloatBuffer normalBuffer = BufferUtils.createFloatBuffer(vertices * 3);
            normalBuffer.put(normals, 0, vertices * 3).flip();
            FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(vertices * 4);
            colorBuffer.put(colors, 0, vertices * 4).flip();
            FloatBuffer uvBuffer = BufferUtils.createFloatBuffer(vertices * 2);
            uvBuffer.put(uvs, 0, vertices * 2).flip();
            FloatBuffer paramBuffer = BufferUtils.createFloatBuffer(vertices * 3);
            paramBuffer.put(params, 0, vertices * 3).flip();
            FloatBuffer flowerBuffer = BufferUtils.createFloatBuffer(vertices * 2);
            flowerBuffer.put(flowers, 0, vertices * 2).flip();
            if (vertices <= 65536) {
                ShortBuffer shortBuffer = BufferUtils.createShortBuffer(indexCount);
                for (int i = 0; i < indexCount; i++) shortBuffer.put((short) indices[i]);
                shortBuffer.flip();
                return new GrassData(positionBuffer, normalBuffer, colorBuffer, uvBuffer, paramBuffer,
                        flowerBuffer, null, shortBuffer, indexCount, minY, maxY);
            }
            IntBuffer indexBuffer = BufferUtils.createIntBuffer(indexCount);
            indexBuffer.put(indices, 0, indexCount).flip();
            return new GrassData(positionBuffer, normalBuffer, colorBuffer, uvBuffer, paramBuffer,
                    flowerBuffer, indexBuffer, null, indexCount, minY, maxY);
        }
    }

    private static float sampleTerrainHeight(float[] heights, int heightSide, int subdivisions,
                                             float step, float x, float z) {
        int cellX = Math.min(subdivisions - 1, (int) (x / step));
        int cellZ = Math.min(subdivisions - 1, (int) (z / step));
        float fx = (x - cellX * step) / step;
        float fz = (z - cellZ * step) / step;
        int a = (cellZ + 1) * heightSide + cellX + 1;
        float hA = heights[a], hB = heights[a + 1];
        float hC = heights[a + heightSide], hD = heights[a + heightSide + 1];
        if (fx + fz <= 1f) return hA * (1f - fx - fz) + hB * fx + hC * fz;
        return hB * (1f - fz) + hC * (1f - fx) + hD * (fx + fz - 1f);
    }

    static float[] surfaceWeights(float height, float waterHeight) {
        float gravel = 1f - smooth(waterHeight - 4f, waterHeight - 1f, height);
        float grass = smooth(waterHeight + 0.5f, waterHeight + 3f, height);
        float sand = Math.max(0f, 1f - gravel - grass);
        float total = grass + sand + gravel;
        return new float[] { grass / total, sand / total, gravel / total };
    }

    private static float grassWeight(float height, float waterHeight) {
        float gravel = 1f - smooth(waterHeight - 4f, waterHeight - 1f, height);
        float grass = smooth(waterHeight + 0.5f, waterHeight + 3f, height);
        float sand = Math.max(0f, 1f - gravel - grass);
        return grass / (grass + sand + gravel);
    }

    private static float smooth(float a, float b, float v) {
        float t = Math.max(0f, Math.min(1f, (v - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    private static byte toByte(float v) {
        return (byte) (Math.max(0f, Math.min(1f, v)) * 255f + 0.5f);
    }

    /**
     * Returns the chunk's integer coordinate along world X.
     *
     * @return chunk X coordinate
     */
    public int getChunkX() { return chunkX; }
    /**
     * Returns the chunk's integer coordinate along world Z.
     *
     * @return chunk Z coordinate
     */
    public int getChunkZ() { return chunkZ; }
    /**
     * Returns the terrain surface geometry.
     *
     * @return terrain geometry
     */
    public Geometry getGeometry() { return geometry; }
    /**
     * Returns the grass geometry, or {@code null} until grass is built or when no grass exists.
     *
     * @return grass geometry, or {@code null}
     */
    public Geometry getGrassGeometry() { return grassGeometry; }
}