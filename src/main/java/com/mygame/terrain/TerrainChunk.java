package com.mygame.terrain;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

/** Un chunk : un maillage de terrain, calculé exclusivement en coordonnées mondiales. */
public final class TerrainChunk {
    private final int size, subdivisions;
    private final float step;

    private final int chunkX, chunkZ;
    private final Geometry geometry;

    public TerrainChunk(int chunkX, int chunkZ, TerrainGenerator generator,
                        long seed, Material material, int size, int subdivisions) {
        this.size=size;
        this.subdivisions=subdivisions;
        this.step=(float) size/subdivisions;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.geometry = build(generator, seed, material);
    }

    private Geometry build(TerrainGenerator generator, long seed, Material material) {
        int side = subdivisions + 1;
        float[] positions = new float[side*side*3];
        float[] normals = new float[side*side*3];
        float[] colors = new float[side*side*4];
        int[] indices = new int[subdivisions*subdivisions*6];
        double originX = (double)chunkX*size;
        double originZ = (double)chunkZ*size;

        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int index = z*side+x;
                double wx = originX + x*step;
                double wz = originZ + z*step;
                float h = generator.getHeight(wx,wz);
                int p = index*3;
                positions[p] = x*step;
                positions[p+1] = h;
                positions[p+2] = z*step;

                float nx = generator.getHeight(wx-step,wz)-generator.getHeight(wx+step,wz);
                float nz = generator.getHeight(wx,wz-step)-generator.getHeight(wx,wz+step);
                float ny = 2*step;
                float length = (float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                normals[p] = nx/length;
                normals[p+1] = ny/length;
                normals[p+2] = nz/length;

                float grain = grainAt(wx,wz,seed);
                int c = index*4;
                colors[c] = clamp(.91f+grain);
                colors[c+1] = clamp(.79f+grain*.85f);
                colors[c+2] = clamp(.48f+grain*.65f);
                colors[c+3] = 1f;
            }
        }
        int j = 0;
        for (int z = 0; z < subdivisions; z++) {
            for (int x = 0; x < subdivisions; x++) {
                int a = z*side+x, b = a+1, c = a+side, d = c+1;
                indices[j++] = a; indices[j++] = c; indices[j++] = b;
                indices[j++] = b; indices[j++] = c; indices[j++] = d;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position,3,BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Normal,3,BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(VertexBuffer.Type.Color,4,BufferUtils.createFloatBuffer(colors));
        mesh.setBuffer(VertexBuffer.Type.Index,3,BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        mesh.updateCounts();
        mesh.setStatic();
        Geometry g = new Geometry("Chunk_"+chunkX+"_"+chunkZ,mesh);
        g.setMaterial(material);
        g.setLocalTranslation((float)originX,0f,(float)originZ);
        return g;
    }

    private static float clamp(float v) { return Math.max(0f,Math.min(1f,v)); }

    private float grainAt(double x, double z, long seed) {
        // Le hash utilise les coordonnées globales de la grille,
        // ce qui évite les différences de couleur aux jonctions.
        long gx = Math.round(x/step), gz = Math.round(z/step);
        long hash = seed ^ (gx*0x9E3779B97F4A7C15L) ^ (gz*0xC2B2AE3D27D4EB4FL);
        hash ^= hash >>> 30; hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27; hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (float)(((hash >>> 40)/(double)(1L<<24))-.5)*.13f;
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public Geometry getGeometry() { return geometry; }
}
