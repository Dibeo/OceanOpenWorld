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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Random;

/**
 * Un chunk de terrain.
 * <ul>
 *   <li>{@link #generate} : calcul pur (hauteurs, normales, tangentes, UV, couleurs) dans des
 *       buffers directs, utilisable depuis un thread de fond ;</li>
 *   <li>le constructeur : assemble Mesh/Geometry, à appeler sur le thread de rendu.</li>
 * </ul>
 */
public final class TerrainChunk {

    /** Résultat du calcul de fond : buffers prêts à être confiés au Mesh. */
    public static final class Data {
        final FloatBuffer positions, normals, tangents, uvs;
        final ByteBuffer blendMap;
        final int blendMapSize;
        final float minY, maxY;
        final float[] heights;
        final int heightSide;

        Data(FloatBuffer positions, FloatBuffer normals, FloatBuffer tangents, FloatBuffer uvs,
             ByteBuffer blendMap, int blendMapSize, float minY, float maxY,
             float[] heights, int heightSide) {
            this.positions = positions; this.normals = normals; this.tangents = tangents;
            this.uvs = uvs;
            this.blendMap = blendMap; this.blendMapSize = blendMapSize;
            this.minY = minY; this.maxY = maxY;
            this.heights = heights; this.heightSide = heightSide;
        }
    }

    /** Maillage d'herbe d'un chunk. {@code params} = (hauteur de la touffe, réservé) par sommet. */
    static final class GrassData {
        final FloatBuffer positions, normals, colors, uvs, params;
        final IntBuffer indices;
        final ShortBuffer shortIndices;
        final int indexCount;
        final float minY, maxY;

        GrassData(FloatBuffer positions, FloatBuffer normals, FloatBuffer colors, FloatBuffer uvs,
                  FloatBuffer params, IntBuffer indices, ShortBuffer shortIndices, int indexCount,
                  float minY, float maxY) {
            this.positions = positions; this.normals = normals; this.colors = colors; this.uvs = uvs;
            this.params = params;
            this.indices = indices; this.shortIndices = shortIndices; this.indexCount = indexCount;
            this.minY = minY; this.maxY = maxY;
        }
    }

    private final int chunkX, chunkZ, size;
    private final float[] heights;
    private final int heightSide;
    private final Geometry geometry;
    private Geometry grassGeometry;
    private boolean grassBuilt;

    public TerrainChunk(int chunkX, int chunkZ, Data data, Material material,
                        VertexBuffer sharedIndices, int size) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.size = size;
        // Conservées (4-5 Ko) : l'herbe est générée à la demande, quand la caméra approche.
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

        ArrayList<java.nio.ByteBuffer> blendMapData = new ArrayList<>(1);
        blendMapData.add(data.blendMap);
        Texture2D blendMap = new Texture2D(new Image(Image.Format.RGB8, data.blendMapSize,
                data.blendMapSize, 0, blendMapData,
                new int[] { data.blendMapSize * data.blendMapSize * 3 }, ColorSpace.Linear));
        blendMap.setWrap(Texture.WrapMode.Clamp);
        blendMap.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        blendMap.setMagFilter(Texture.MagFilter.Bilinear);
        Material chunkMaterial = material.clone();
        chunkMaterial.setTexture("AlphaMap", blendMap);

        geometry = new Geometry("Chunk_" + chunkX + "_" + chunkZ, mesh);
        geometry.setMaterial(chunkMaterial);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.setLocalTranslation((float) ((double) chunkX * size), 0f, (float) ((double) chunkZ * size));
    }

    /**
     * Construit la géométrie d'herbe (thread de rendu). {@code data} nul ou vide : le chunk est
     * marqué « traité » sans géométrie, pour ne pas être recalculé en boucle.
     */
    Geometry buildGrass(GrassData data, Material material) {
        grassBuilt = true;
        grassGeometry = (data == null || data.indexCount == 0) ? null
                : createGrassGeometry(chunkX, chunkZ, data, material, size);
        return grassGeometry;
    }

    /** Libère l'herbe (le terrain reste) : elle sera régénérée à l'identique si la caméra revient. */
    void clearGrass() {
        if (grassGeometry != null) grassGeometry.removeFromParent();
        grassGeometry = null;
        grassBuilt = false;
    }

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
        mesh.setBuffer(vertexBuffer(Type.TexCoord2, 2, Format.Float, data.params));
        VertexBuffer indices = data.shortIndices != null
                ? vertexBuffer(Type.Index, 3, Format.UnsignedShort, data.shortIndices)
                : vertexBuffer(Type.Index, 3, Format.UnsignedInt, data.indices);
        mesh.setBuffer(indices);
        // Marge : largeur des touffes + déplacement du vent.
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

    private static VertexBuffer vertexBuffer(Type type, int components, Format format, java.nio.Buffer data) {
        VertexBuffer vb = new VertexBuffer(type);
        vb.setupData(Usage.Static, components, format, data);
        return vb;
    }

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
     * Calcul pur, thread-safe. Une seule évaluation du bruit par échantillon :
     * on échantillonne une grille (side+2)² et les normales sont déduites des voisins.
     * Les bords restent identiques entre chunks voisins car tout est calculé en coordonnées mondiales.
     *
     * La texture RGB stocke des poids de mélange continus (R=base, G=sable, B=gravier).
     */
    public static Data generate(int chunkX, int chunkZ, TerrainGenerator generator,
                                int size, int subdivisions, float waterHeight) {
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
        ByteBuffer blendMap = BufferUtils.createByteBuffer(side * side * 3);
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

                // Normale
                float nx = heights[c - 1] - heights[c + 1];
                float nz = heights[c - hs] - heights[c + hs];
                float inv = 1f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                float nxn = nx * inv, nyn = ny * inv, nzn = nz * inv;
                normals.put(nxn).put(nyn).put(nzn);

                // Tangente le long de +X (u), orthogonalisée par rapport à la normale.
                // w = -1 : la bitangente pointe vers +Z (v), convention OpenGL (vert vers le haut).
                float tx = 1f, ty = (heights[c + 1] - heights[c - 1]) / (2f * step), tz = 0f;
                float dot = tx * nxn + ty * nyn + tz * nzn;
                tx -= nxn * dot; ty -= nyn * dot; tz -= nzn * dot;
                float tl = 1f / (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                tangents.put(tx * tl).put(ty * tl).put(tz * tl).put(-1f);

                uvs.put((x + 0.5f) * uvStep).put((z + 0.5f) * uvStep);

                float[] weights = surfaceWeights(h, waterHeight);
                float grassAndSand = weights[0] + weights[1];
                blendMap.put((byte) 255)
                        .put(toByte(grassAndSand > 0f ? weights[1] / grassAndSand : 0f))
                        .put(toByte(weights[2]));
            }
        }
        positions.flip();
        normals.flip();
        tangents.flip();
        uvs.flip();
        blendMap.flip();
        return new Data(positions, normals, tangents, uvs, blendMap, side, minY, maxY, heights, hs);
    }

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final int VERTICES_PER_TUFT = 8;

    /**
     * Touffes d'herbe d'un chunk. Calcul pur et déterministe (seed du monde + coordonnées du chunk) :
     * un chunk régénéré après déchargement retrouve exactement la même herbe.
     * Une touffe = 2 quads croisés portant plusieurs brins (voir {@link GrassTexture}).
     * Grille jitterée d'une touffe par cellule de 1/sqrt(density) mètres.
     */
    static GrassData generateGrass(int chunkX, int chunkZ, TerrainGenerator generator,
                                   float[] heights, int heightSide, int size, int subdivisions,
                                   float waterHeight, float tuftsPerSquareMeter) {
        final float cellSize = (float) (1.0 / Math.sqrt(tuftsPerSquareMeter));
        final int cells = (int) Math.ceil(size / cellSize);
        final float terrainStep = (float) size / subdivisions;
        final double originX = (double) chunkX * size, originZ = (double) chunkZ * size;
        final Random random = new Random(generator.getGrassChunkSeed(chunkX, chunkZ));
        final GrassBuffers out = new GrassBuffers(Math.min(cells * cells, 4096) * VERTICES_PER_TUFT);
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        final float e = 0.6f;   // demi-pas pour estimer la pente locale

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

                // Normale du terrain (sert à l'éclairage) ; pas d'herbe sur les pentes raides.
                float xl = Math.max(0f, localX - e), xr = Math.min(size, localX + e);
                float zl = Math.max(0f, localZ - e), zr = Math.min(size, localZ + e);
                float nx = (sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, xl, localZ)
                        - sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, xr, localZ)) / (xr - xl);
                float nz = (sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, zl)
                        - sampleTerrainHeight(heights, heightSide, subdivisions, terrainStep, localX, zr)) / (zr - zl);
                float inv = 1f / (float) Math.sqrt(nx * nx + 1f + nz * nz);
                nx *= inv; nz *= inv;
                float ny = inv;
                if (random.nextFloat() >= smooth(0.6f, 0.85f, ny)) continue;

                float dry = generator.getGrassDryness(worldX, worldZ);
                float lush = 1f - dry;
                float angle = random.nextFloat() * TWO_PI;
                float halfWidth = 0.5f * (0.62f + 0.40f * random.nextFloat()) * (0.85f + 0.30f * lush);
                float height = (0.38f + 0.42f * random.nextFloat()) * (0.75f + 0.25f * grassWeight)
                        * (0.85f + 0.25f * lush);
                float leanAngle = random.nextFloat() * TWO_PI;
                float lean = height * (0.05f + 0.18f * random.nextFloat());
                float phase = random.nextFloat();
                float bright = 0.72f + 0.48f * random.nextFloat();
                float r = (1f + 0.35f * dry) * bright;
                float g = (1f + 0.05f * dry) * bright;
                float b = (1f - 0.45f * dry) * bright;

                float baseY = y - 0.03f;                 // légèrement enterrée : pas de jour sur les pentes
                float topY = baseY + height;
                float leanX = (float) Math.cos(leanAngle) * lean, leanZ = (float) Math.sin(leanAngle) * lean;
                minY = Math.min(minY, baseY);
                maxY = Math.max(maxY, topY);
                for (int plane = 0; plane < 2; plane++) {
                    float planeAngle = angle + plane * (float) (Math.PI * 0.5);
                    float dx = (float) Math.cos(planeAngle) * halfWidth;
                    float dz = (float) Math.sin(planeAngle) * halfWidth;
                    int base = out.vertexCount();
                    out.vertex(localX - dx, baseY, localZ - dz, nx, ny, nz, r, g, b, phase, 0f, 0f, height);
                    out.vertex(localX + dx, baseY, localZ + dz, nx, ny, nz, r, g, b, phase, 1f, 0f, height);
                    out.vertex(localX - dx + leanX, topY, localZ - dz + leanZ, nx, ny, nz, r, g, b, phase, 0f, 1f, height);
                    out.vertex(localX + dx + leanX, topY, localZ + dz + leanZ, nx, ny, nz, r, g, b, phase, 1f, 1f, height);
                    out.triangle(base, base + 1, base + 2);
                    out.triangle(base + 2, base + 1, base + 3);
                }
            }
        }
        return out.build(minY, maxY);
    }

    /** Tampons extensibles : la taille finale dépend du nombre de touffes acceptées. */
    private static final class GrassBuffers {
        private float[] positions, normals, colors, uvs, params;
        private int[] indices;
        private int vertices, indexCount;

        GrassBuffers(int initialVertices) {
            allocate(Math.max(initialVertices, VERTICES_PER_TUFT));
        }

        private void allocate(int capacity) {
            positions = new float[capacity * 3];
            normals = new float[capacity * 3];
            colors = new float[capacity * 4];
            uvs = new float[capacity * 2];
            params = new float[capacity * 2];
            indices = new int[capacity / 4 * 6 + 12];
        }

        private void ensure(int extraVertices) {
            int capacity = positions.length / 3;
            if (vertices + extraVertices <= capacity) return;
            int grown = Math.max(capacity + capacity / 2, vertices + extraVertices);
            positions = java.util.Arrays.copyOf(positions, grown * 3);
            normals = java.util.Arrays.copyOf(normals, grown * 3);
            colors = java.util.Arrays.copyOf(colors, grown * 4);
            uvs = java.util.Arrays.copyOf(uvs, grown * 2);
            params = java.util.Arrays.copyOf(params, grown * 2);
            indices = java.util.Arrays.copyOf(indices, grown / 4 * 6 + 12);
        }

        int vertexCount() { return vertices; }

        void vertex(float x, float y, float z, float nx, float ny, float nz,
                    float r, float g, float b, float phase, float u, float v, float height) {
            ensure(1);
            int p = vertices * 3, c = vertices * 4, t = vertices * 2;
            positions[p] = x; positions[p + 1] = y; positions[p + 2] = z;
            normals[p] = nx; normals[p + 1] = ny; normals[p + 2] = nz;
            colors[c] = r; colors[c + 1] = g; colors[c + 2] = b; colors[c + 3] = phase;
            uvs[t] = u; uvs[t + 1] = v;
            params[t] = height; params[t + 1] = 0f;
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
            FloatBuffer paramBuffer = BufferUtils.createFloatBuffer(vertices * 2);
            paramBuffer.put(params, 0, vertices * 2).flip();
            if (vertices <= 65536) {
                ShortBuffer shortBuffer = BufferUtils.createShortBuffer(indexCount);
                for (int i = 0; i < indexCount; i++) shortBuffer.put((short) indices[i]);
                shortBuffer.flip();
                return new GrassData(positionBuffer, normalBuffer, colorBuffer, uvBuffer, paramBuffer,
                        null, shortBuffer, indexCount, minY, maxY);
            }
            IntBuffer indexBuffer = BufferUtils.createIntBuffer(indexCount);
            indexBuffer.put(indices, 0, indexCount).flip();
            return new GrassData(positionBuffer, normalBuffer, colorBuffer, uvBuffer, paramBuffer,
                    indexBuffer, null, indexCount, minY, maxY);
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

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public Geometry getGeometry() { return geometry; }
    public Geometry getGrassGeometry() { return grassGeometry; }
}