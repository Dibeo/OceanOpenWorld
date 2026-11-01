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

        Data(FloatBuffer positions, FloatBuffer normals, FloatBuffer tangents, FloatBuffer uvs,
             ByteBuffer blendMap, int blendMapSize, float minY, float maxY) {
            this.positions = positions; this.normals = normals; this.tangents = tangents;
            this.uvs = uvs;
            this.blendMap = blendMap; this.blendMapSize = blendMapSize;
            this.minY = minY; this.maxY = maxY;
        }
    }

    private final int chunkX, chunkZ;
    private final Geometry geometry;

    public TerrainChunk(int chunkX, int chunkZ, Data data, Material material,
                        VertexBuffer sharedIndices, int size) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;

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
        return new Data(positions, normals, tangents, uvs, blendMap, side, minY, maxY);
    }

    static float[] surfaceWeights(float height, float waterHeight) {
        float gravel = 1f - smooth(waterHeight - 4f, waterHeight - 1f, height);
        float grass = smooth(waterHeight + 0.5f, waterHeight + 3f, height);
        float sand = Math.max(0f, 1f - gravel - grass);
        float total = grass + sand + gravel;
        return new float[] { grass / total, sand / total, gravel / total };
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
}