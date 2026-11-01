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
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

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
        final ByteBuffer colors;
        final float minY, maxY;

        Data(FloatBuffer positions, FloatBuffer normals, FloatBuffer tangents, FloatBuffer uvs,
             ByteBuffer colors, float minY, float maxY) {
            this.positions = positions; this.normals = normals; this.tangents = tangents;
            this.uvs = uvs; this.colors = colors; this.minY = minY; this.maxY = maxY;
        }
    }

    private final int chunkX, chunkZ;
    private final Geometry geometry;

    public TerrainChunk(int chunkX, int chunkZ, Data data, Material material,
                        VertexBuffer sharedIndices, int size) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;

        Mesh mesh = new Mesh();
        mesh.setBuffer(vertexBuffer(Type.Position, 3, Format.Float, data.positions));
        mesh.setBuffer(vertexBuffer(Type.Normal, 3, Format.Float, data.normals));
        mesh.setBuffer(vertexBuffer(Type.Tangent, 4, Format.Float, data.tangents));
        mesh.setBuffer(vertexBuffer(Type.TexCoord, 2, Format.Float, data.uvs));
        VertexBuffer color = vertexBuffer(Type.Color, 4, Format.UnsignedByte, data.colors);
        color.setNormalized(true); // 0..255 -> 0..1 côté GPU
        mesh.setBuffer(color);
        mesh.setBuffer(sharedIndices); // même VertexBuffer pour tous les chunks : un seul VBO GPU

        // Volume englobant connu : inutile de reparcourir les sommets (updateBound).
        float half = size * 0.5f;
        float halfY = Math.max(0.01f, (data.maxY - data.minY) * 0.5f);
        mesh.setBound(new BoundingBox(new Vector3f(half, (data.minY + data.maxY) * 0.5f, half),
                half, halfY, half));
        mesh.updateCounts();

        geometry = new Geometry("Chunk_" + chunkX + "_" + chunkZ, mesh);
        geometry.setMaterial(material);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.setLocalTranslation((float) ((double) chunkX * size), 0f, (float) ((double) chunkZ * size));
    }

    private static VertexBuffer vertexBuffer(Type type, int components, Format format, java.nio.Buffer data) {
        VertexBuffer vb = new VertexBuffer(type);
        vb.setupData(Usage.Static, components, format, data);
        return vb;
    }

    /**
     * Index partagés par tous les chunks (la topologie de la grille est identique).
     * Short non signé si possible (moitié moins de mémoire GPU), sinon int.
     */
    public static VertexBuffer createIndexBuffer(int subdivisions) {
        int side = subdivisions + 1;
        int count = subdivisions * subdivisions * 6;
        VertexBuffer vb = new VertexBuffer(Type.Index);
        boolean useShort = side * side <= 65536;
        ShortBuffer sb = useShort ? BufferUtils.createShortBuffer(count) : null;
        IntBuffer ib = useShort ? null : BufferUtils.createIntBuffer(count);

        for (int z = 0; z < subdivisions; z++) {
            for (int x = 0; x < subdivisions; x++) {
                int a = z * side + x, b = a + 1, c = a + side, d = c + 1;
                int[] tri = {a, c, b, b, c, d};
                for (int v : tri) {
                    if (useShort) sb.put((short) v); else ib.put(v);
                }
            }
        }
        if (useShort) { sb.flip(); vb.setupData(Usage.Static, 3, Format.UnsignedShort, sb); }
        else          { ib.flip(); vb.setupData(Usage.Static, 3, Format.UnsignedInt, ib); }
        return vb;
    }

    /**
     * Calcul pur, thread-safe. Une seule évaluation du bruit par échantillon :
     * on échantillonne une grille (side+2)² et les normales sont déduites des voisins.
     * Les bords restent identiques entre chunks voisins car tout est calculé en coordonnées mondiales.
     *
     * Couleur du sable : plus sombre près de l'eau (humide) et sur les pentes, avec de larges
     * plages de teinte et un grain par sommet.
     */
    public static Data generate(int chunkX, int chunkZ, TerrainGenerator generator,
                                long seed, int size, int subdivisions, float waterHeight,
                                float tileMeters) {
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
        ByteBuffer colors = BufferUtils.createByteBuffer(side * side * 4);
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        final float ny = 2f * step;
        final long baseGx = (long) chunkX * subdivisions;
        final long baseGz = (long) chunkZ * subdivisions;

        // Nombre entier de répétitions par chunk : la texture se raccorde entre chunks.
        final int tiles = Math.max(1, Math.round(size / tileMeters));
        final float uvStep = (float) tiles / subdivisions;

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

                uvs.put(x * uvStep).put(z * uvStep);

                // Couleur
                double wx = originX + x * step, wz = originZ + z * step;
                float slope = 1f - nyn;                                              // 0 = plat
                float wet = 1f - smooth(waterHeight - 0.1f, waterHeight + 1.0f, h);   // 1 au bord de l'eau
                float patch = generator.getTint(wx, wz);
                float k = Math.max(0.45f, 1f - 0.40f * wet - 1.2f * slope + 0.06f * patch);
                float grain = grain(baseGx + x, baseGz + z, seed);
                colors.put(toByte(.91f * k + grain))
                      .put(toByte(.79f * k + grain * .85f))
                      .put(toByte(.48f * k + grain * .65f + 0.04f * wet))
                      .put((byte) 255);
            }
        }
        positions.flip();
        normals.flip();
        tangents.flip();
        uvs.flip();
        colors.flip();
        return new Data(positions, normals, tangents, uvs, colors, minY, maxY);
    }

    private static float smooth(float a, float b, float v) {
        float t = Math.max(0f, Math.min(1f, (v - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    private static byte toByte(float v) {
        return (byte) (Math.max(0f, Math.min(1f, v)) * 255f + 0.5f);
    }

    /** Hash sur les indices globaux de grille : continu aux jonctions entre chunks. */
    private static float grain(long gx, long gz, long seed) {
        long hash = seed ^ (gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xC2B2AE3D27D4EB4FL);
        hash ^= hash >>> 30; hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27; hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (float) (((hash >>> 40) / (double) (1L << 24)) - .5) * .13f;
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public Geometry getGeometry() { return geometry; }
}