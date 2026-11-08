package com.mygame.terrain;

import com.mygame.noise.PerlinNoise;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.jme3.util.BufferUtils;

/**
 * Generates deterministic low-poly rock meshes from world-seeded Perlin noise and a per-rock seed.
 * Each mesh starts with a subdivided icosphere, then applies low-frequency radius variation,
 * several softened clipping planes, and high-frequency roughness. Rocks are stretched, flattened
 * at the base, and slightly tilted.
 *
 * <p>Vertex colors encode rock tint, ambient occlusion in the alpha channel, and moss on upward-facing
 * surfaces. Mesh data is accumulated in chunk-local coordinates for terrain construction.
 */
final class RockShape {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /** Immutable unit-sphere directions and triangles, shared between threads. */
    private static final class Sphere {
        final float[] dirs;
        final int[] indices;
        final int vertices;

        Sphere(float[] dirs, int[] indices) {
            this.dirs = dirs; this.indices = indices; this.vertices = dirs.length / 3;
        }
    }

    private static final Sphere[] SPHERES = new Sphere[4];
    static {
        for (int level = 1; level <= 3; level++) SPHERES[level] = icosphere(level);
    }

    private RockShape() { }

    /** Selects the cached icosphere subdivision level appropriate for a rock radius. */
    static int levelFor(float radius) {
        return radius >= 0.7f ? 3 : radius >= 0.22f ? 2 : 1;
    }

    /** Growable per-chunk accumulators for vertex attributes, indices, and bounds. */
    static final class Buffers {
        private float[] positions = new float[3 * 1024], normals = new float[3 * 1024], colors = new float[4 * 1024];
        private int[] indices = new int[6 * 1024];
        private int vertices, indexCount;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        int vertexCount() { return vertices; }
        boolean isEmpty() { return vertices == 0; }

        private void ensure(int extraVertices, int extraIndices) {
            if ((vertices + extraVertices) * 3 > positions.length) {
                int capacity = Math.max(positions.length / 3 * 2, vertices + extraVertices);
                positions = java.util.Arrays.copyOf(positions, capacity * 3);
                normals = java.util.Arrays.copyOf(normals, capacity * 3);
                colors = java.util.Arrays.copyOf(colors, capacity * 4);
            }
            if (indexCount + extraIndices > indices.length) {
                indices = java.util.Arrays.copyOf(indices, Math.max(indices.length * 2, indexCount + extraIndices));
            }
        }

        RockData build() {
            FloatBuffer p = BufferUtils.createFloatBuffer(vertices * 3);
            p.put(positions, 0, vertices * 3).flip();
            FloatBuffer n = BufferUtils.createFloatBuffer(vertices * 3);
            n.put(normals, 0, vertices * 3).flip();
            FloatBuffer c = BufferUtils.createFloatBuffer(vertices * 4);
            c.put(colors, 0, vertices * 4).flip();
            if (vertices <= 65535) {
                ShortBuffer s = BufferUtils.createShortBuffer(indexCount);
                for (int i = 0; i < indexCount; i++) s.put((short) indices[i]);
                s.flip();
                return new RockData(p, n, c, null, s, indexCount, minX, minY, minZ, maxX, maxY, maxZ);
            }
            IntBuffer ib = BufferUtils.createIntBuffer(indexCount);
            ib.put(indices, 0, indexCount).flip();
            return new RockData(p, n, c, ib, null, indexCount, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /** Immutable combined mesh buffers and bounds for generated rocks in one chunk. */
    static final class RockData {
        final FloatBuffer positions, normals, colors;
        final IntBuffer indices;
        final ShortBuffer shortIndices;
        final int indexCount;
        final float minX, minY, minZ, maxX, maxY, maxZ;

        RockData(FloatBuffer positions, FloatBuffer normals, FloatBuffer colors, IntBuffer indices,
                 ShortBuffer shortIndices, int indexCount, float minX, float minY, float minZ,
                 float maxX, float maxY, float maxZ) {
            this.positions = positions; this.normals = normals; this.colors = colors;
            this.indices = indices; this.shortIndices = shortIndices; this.indexCount = indexCount;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    /**
     * Appends one seed-determined rock mesh to a chunk accumulator.
     *
     * @param out target mesh accumulator
     * @param noise world-seeded 3D shape noise
     * @param seed per-rock seed
     * @param cx rock center X in chunk-local coordinates
     * @param cy rock center Y in world height coordinates
     * @param cz rock center Z in chunk-local coordinates
     * @param radius mean rock radius in world units
     * @param moss possible moss amount in [0, 1]
     */
    static void add(Buffers out, PerlinNoise noise, long seed, float cx, float cy, float cz,
                    float radius, float moss) {
        Random rnd = new Random(seed);
        Sphere sphere = SPHERES[levelFor(radius)];

        // Make rocks wider than tall, with occasional elongated shapes.
        float sx = radius * (0.85f + 0.55f * rnd.nextFloat());
        float sz = radius * (0.85f + 0.55f * rnd.nextFloat());
        float sy = radius * (0.62f + 0.55f * rnd.nextFloat());
        double ox = rnd.nextDouble() * 1000.0, oy = rnd.nextDouble() * 1000.0, oz = rnd.nextDouble() * 1000.0;

        // Apply arbitrary yaw with slight pitch and roll.
        float yaw = rnd.nextFloat() * TWO_PI, pitch = (rnd.nextFloat() - 0.5f) * 0.5f, roll = (rnd.nextFloat() - 0.5f) * 0.5f;
        float cyaw = (float) Math.cos(yaw), syaw = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);

        // Clipping planes create flat faces.
        int planes = 6 + rnd.nextInt(4);
        float[] pn = new float[planes * 3];
        float[] po = new float[planes];
        for (int i = 0; i < planes; i++) {
            double gx = rnd.nextGaussian(), gy = rnd.nextGaussian(), gz = rnd.nextGaussian();
            double len = Math.max(1e-6, Math.sqrt(gx * gx + gy * gy + gz * gz));
            pn[i * 3] = (float) (gx / len); pn[i * 3 + 1] = (float) (gy / len); pn[i * 3 + 2] = (float) (gz / len);
            po[i] = 0.66f + 0.22f * rnd.nextFloat();
        }

        // Choose a warm or cool gray tint in linear albedo space.
        float lum = 0.045f + 0.10f * rnd.nextFloat();
        float warm = (rnd.nextFloat() - 0.5f) * 0.16f;
        float tintR = lum * (1f + warm * 2f), tintG = lum * (1f + warm * 0.6f), tintB = lum * (1f - warm * 1.6f);
        float mossAmount = moss * (0.35f + 0.65f * rnd.nextFloat());

        int v = sphere.vertices;
        float[] px = new float[v], py = new float[v], pz = new float[v], rho = new float[v];
        float[] localY = new float[v];
        for (int i = 0; i < v; i++) {
            float dx = sphere.dirs[i * 3], dy = sphere.dirs[i * 3 + 1], dz = sphere.dirs[i * 3 + 2];
            // 1. Low-frequency irregular radius.
            float r = (float) (1.0 + 0.50 * noise.fractal(dx * 1.4 + ox, dy * 1.4 + oy, dz * 1.4 + oz, 3, 0.5, 2.0));
            // 2. Clipping planes blended with a smooth minimum to soften edges.
            for (int p = 0; p < planes; p++) {
                float dn = dx * pn[p * 3] + dy * pn[p * 3 + 1] + dz * pn[p * 3 + 2];
                if (dn > 0.02f) r = smoothMin(r, po[p] / dn);
            }
            // 3. Fine roughness.
            r += 0.035f * (float) noise.fractal(dx * 6.0 + ox, dy * 6.0 + oy, dz * 6.0 + oz, 2, 0.5, 2.0);
            r = Math.max(r, 0.35f);
            rho[i] = r;

            float lx = dx * r * sx, ly = dy * r * sy, lz = dz * r * sz;
            ly = Math.max(ly, -0.55f * sy);                  // flattened, partially buried base
            localY[i] = ly / sy;
            // Rotate in roll (Z), pitch (X), then yaw (Y).
            float x1 = lx * cr - ly * sr;
            float y1 = lx * sr + ly * cr;
            float y2 = y1 * cp - lz * sp, z2 = y1 * sp + lz * cp;
            px[i] = cx + x1 * cyaw + z2 * syaw;
            py[i] = cy + y2;
            pz[i] = cz - x1 * syaw + z2 * cyaw;
        }

        // Smooth normals from the area-weighted sum of adjacent face normals.
        float[] nx = new float[v], ny = new float[v], nz = new float[v];
        int[] idx = sphere.indices;
        for (int t = 0; t < idx.length; t += 3) {
            int a = idx[t], b = idx[t + 1], c = idx[t + 2];
            float e1x = px[b] - px[a], e1y = py[b] - py[a], e1z = pz[b] - pz[a];
            float e2x = px[c] - px[a], e2y = py[c] - py[a], e2z = pz[c] - pz[a];
            float fx = e1y * e2z - e1z * e2y, fy = e1z * e2x - e1x * e2z, fz = e1x * e2y - e1y * e2x;
            nx[a] += fx; ny[a] += fy; nz[a] += fz;
            nx[b] += fx; ny[b] += fy; nz[b] += fz;
            nx[c] += fx; ny[c] += fy; nz[c] += fz;
        }

        out.ensure(v, idx.length);
        int base = out.vertices;
        for (int i = 0; i < v; i++) {
            float len = (float) Math.sqrt(nx[i] * nx[i] + ny[i] * ny[i] + nz[i] * nz[i]);
            if (len < 1e-8f) { nx[i] = 0f; ny[i] = 1f; nz[i] = 0f; } else { nx[i] /= len; ny[i] /= len; nz[i] /= len; }

            // Darken the base and cavities. h01 ranges from 0 at the bottom to 1 at the top.
            float h01 = Math.max(0f, Math.min(1f, (localY[i] + 0.55f) / 1.55f));
            float cavity = Math.max(0.6f, Math.min(1.05f, 0.55f + (rho[i] - 0.6f)));
            float ao = (0.40f + 0.60f * smoothstep(0.05f, 0.6f, h01)) * cavity;

            // Add strata and tone variation with medium-frequency 3D noise.
            float variation = 0.85f + 0.30f * (float) (noise.fractal(px[i] * 1.7 + ox, py[i] * 1.7 + oy, pz[i] * 1.7 + oz, 2, 0.5, 2.0) + 0.5);
            float r = tintR * variation, g = tintG * variation, b = tintB * variation;

            // Add soil coloring at the base.
            float soil = 1f - smoothstep(0.05f, 0.30f, h01);
            r += (0.040f - r) * soil * 0.6f; g += (0.031f - g) * soil * 0.6f; b += (0.020f - b) * soil * 0.6f;
            // Add moss patches to upward-facing surfaces.
            float patches = (float) noise.fractal(px[i] * 3.1 + oy, py[i] * 3.1 + oz, pz[i] * 3.1 + ox, 2, 0.5, 2.0) + 0.5f;
            float m = mossAmount * smoothstep(0.55f, 0.92f, ny[i]) * smoothstep(0.30f, 0.75f, patches);
            r += (0.020f - r) * m * 0.85f; g += (0.060f - g) * m * 0.85f; b += (0.010f - b) * m * 0.85f;

            int o = (base + i) * 3, c4 = (base + i) * 4;
            out.positions[o] = px[i]; out.positions[o + 1] = py[i]; out.positions[o + 2] = pz[i];
            out.normals[o] = nx[i]; out.normals[o + 1] = ny[i]; out.normals[o + 2] = nz[i];
            out.colors[c4] = r; out.colors[c4 + 1] = g; out.colors[c4 + 2] = b; out.colors[c4 + 3] = ao;
            out.minX = Math.min(out.minX, px[i]); out.maxX = Math.max(out.maxX, px[i]);
            out.minY = Math.min(out.minY, py[i]); out.maxY = Math.max(out.maxY, py[i]);
            out.minZ = Math.min(out.minZ, pz[i]); out.maxZ = Math.max(out.maxZ, pz[i]);
        }
        for (int t = 0; t < idx.length; t++) out.indices[out.indexCount + t] = base + idx[t];
        out.indexCount += idx.length;
        out.vertices += v;
    }

    /** Polynomial smooth minimum that rounds the join between two surfaces over width {@code k}. */
    private static float smoothMin(float a, float b) {
        float h = Math.max((float) 0.08 - Math.abs(a - b), 0f) / (float) 0.08;
        return Math.min(a, b) - h * h * (float) 0.08 * 0.25f;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    /** Builds an icosphere subdivided {@code level} times and projected onto the unit sphere. */
    private static Sphere icosphere(int level) {
        float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] base = {
                {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
                {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        int[][] faces = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        List<float[]> verts = new ArrayList<>();
        for (float[] b : base) verts.add(normalize(b));
        List<int[]> tris = new ArrayList<>(List.of(faces));
        for (int l = 0; l < level; l++) {
            Map<Long, Integer> cache = new HashMap<>();
            List<int[]> next = new ArrayList<>();
            for (int[] f : tris) {
                int a = midpoint(verts, cache, f[0], f[1]);
                int b = midpoint(verts, cache, f[1], f[2]);
                int c = midpoint(verts, cache, f[2], f[0]);
                next.add(new int[] {f[0], a, c});
                next.add(new int[] {f[1], b, a});
                next.add(new int[] {f[2], c, b});
                next.add(new int[] {a, b, c});
            }
            tris = next;
        }
        float[] dirs = new float[verts.size() * 3];
        for (int i = 0; i < verts.size(); i++) System.arraycopy(verts.get(i), 0, dirs, i * 3, 3);
        int[] indices = new int[tris.size() * 3];
        for (int i = 0; i < tris.size(); i++) System.arraycopy(tris.get(i), 0, indices, i * 3, 3);
        return new Sphere(dirs, indices);
    }

    private static int midpoint(List<float[]> verts, Map<Long, Integer> cache, int a, int b) {
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        Integer found = cache.get(key);
        if (found != null) return found;
        float[] va = verts.get(a), vb = verts.get(b);
        verts.add(normalize(new float[] {(va[0] + vb[0]) / 2f, (va[1] + vb[1]) / 2f, (va[2] + vb[2]) / 2f}));
        cache.put(key, verts.size() - 1);
        return verts.size() - 1;
    }

    private static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[] {v[0] / len, v[1] / len, v[2] / len};
    }
}