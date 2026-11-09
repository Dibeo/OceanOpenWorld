package com.mygame.terrain;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.util.BufferUtils;
import com.mygame.noise.PerlinNoise;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a deterministic procedural tree mesh from a tree seed and world noise.
 *
 * <p>Oaks have rounded crowns, pines have drooping conical branches, and birches have pale bark
 * and a narrow upright form. Trunks and branches use Perlin-bent tapered tubes. Leaf cards are
 * placed at branch tips and oriented for soft volumetric shading. Random values that determine
 * primary branch placement are consumed consistently for both detail levels.
 */
final class TreeShape {
    private static float a;

    /** Supported tree species. */
    enum Species { OAK, PINE, BIRCH }

    static final int LOD_LOW = 1;
    static final int LOD_HIGH = 2;

    /**
     * Immutable dimensions and appearance parameters for one species.
     */
        private record Spec(float minHeight, float maxHeight, float radiusRatio, float taper, float trunkTop,
                            float crownStart, float lengthRatio, int primaryHigh, int stride, float pitchMin,
                            float pitchMax, float gravity, int leafCell0, int leafCells, float leafSize, float[] barkTint,
                            float[] leafTint) {
    }

    private static final Spec OAK = new Spec(9f, 15f, 0.022f, 1.1f, 0.70f, 0.28f, 0.50f, 11, 2,
            50f, 82f, -0.35f, 0, 2, 2.9f,
            new float[] {0.060f, 0.045f, 0.032f}, new float[] {0.070f, 0.200f, 0.035f});
    private static final Spec PINE = new Spec(12f, 20f, 0.016f, 0.8f, 0.97f, 0.22f, 0.26f, 26, 2, 78f, 100f, 0.60f,
            3, 1, 1.9f, new float[] {0.085f, 0.050f, 0.034f}, new float[] {0.025f, 0.085f, 0.040f});
    private static final Spec BIRCH = new Spec(8f, 13f, 0.013f, 0.9f, 0.90f, 0.35f, 0.30f, 11, 2,
            28f, 50f, 0.30f, 2, 1, 1.7f,
            new float[] {0.50f, 0.48f, 0.44f}, new float[] {0.120f, 0.260f, 0.040f});

    private static Spec spec(Species species) {
        return species == Species.PINE ? PINE : species == Species.BIRCH ? BIRCH : OAK;
    }

    private TreeShape() {
    }

    /** Mutable geometry accumulator for bark or leaf vertices. */
    static final class Part {
        private float[] pos = new float[3 * 512], nrm = new float[3 * 512], uv = new float[2 * 512],
                col = new float[4 * 512], uv2 = new float[2 * 512];
        private int[] idx = new int[6 * 512];
        private int vertices, indexCount;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        boolean isEmpty() {
            return vertices == 0;
        }

        private void ensure(int extra) {
            if (vertices + extra > pos.length / 3) {
                int capacity = Math.max(pos.length / 3 * 2, vertices + extra);
                pos = java.util.Arrays.copyOf(pos, capacity * 3);
                nrm = java.util.Arrays.copyOf(nrm, capacity * 3);
                uv = java.util.Arrays.copyOf(uv, capacity * 2);
                col = java.util.Arrays.copyOf(col, capacity * 4);
                uv2 = java.util.Arrays.copyOf(uv2, capacity * 2);
            }
        }

        int vertex(Vector3f position, Vector3f normal, float u, float v,
                   float r, float g, float b, float a, float u2, float v2) {
            ensure(1);
            int index = vertices++;
            pos[index * 3] = position.x;
            pos[index * 3 + 1] = position.y;
            pos[index * 3 + 2] = position.z;
            nrm[index * 3] = normal.x;
            nrm[index * 3 + 1] = normal.y;
            nrm[index * 3 + 2] = normal.z;
            uv[index * 2] = u;
            uv[index * 2 + 1] = v;
            col[index * 4] = r;
            col[index * 4 + 1] = g;
            col[index * 4 + 2] = b;
            col[index * 4 + 3] = a;
            uv2[index * 2] = u2;
            uv2[index * 2 + 1] = v2;
            minX = Math.min(minX, position.x);
            maxX = Math.max(maxX, position.x);
            minY = Math.min(minY, position.y);
            maxY = Math.max(maxY, position.y);
            minZ = Math.min(minZ, position.z);
            maxZ = Math.max(maxZ, position.z);
            return index;
        }

        void triangle(int a, int b, int c) {
            if (indexCount + 3 > idx.length) {
                idx = java.util.Arrays.copyOf(idx, idx.length * 2);
            }
            idx[indexCount++] = a;
            idx[indexCount++] = b;
            idx[indexCount++] = c;
        }

        TreeMesh build() {
            FloatBuffer positions = BufferUtils.createFloatBuffer(vertices * 3);
            positions.put(pos, 0, vertices * 3).flip();
            FloatBuffer normals = BufferUtils.createFloatBuffer(vertices * 3);
            normals.put(nrm, 0, vertices * 3).flip();
            FloatBuffer uvs = BufferUtils.createFloatBuffer(vertices * 2);
            uvs.put(uv, 0, vertices * 2).flip();
            FloatBuffer colors = BufferUtils.createFloatBuffer(vertices * 4);
            colors.put(col, 0, vertices * 4).flip();
            FloatBuffer params = BufferUtils.createFloatBuffer(vertices * 2);
            params.put(uv2, 0, vertices * 2).flip();
            ShortBuffer shortIndices = null;
            IntBuffer indices = null;
            if (vertices <= 65535) {
                shortIndices = BufferUtils.createShortBuffer(indexCount);
                for (int i = 0; i < indexCount; i++) {
                    shortIndices.put((short) idx[i]);
                }
                shortIndices.flip();
            } else {
                indices = BufferUtils.createIntBuffer(indexCount);
                indices.put(idx, 0, indexCount).flip();
            }
            return new TreeMesh(positions, normals, uvs, colors, params, indices, shortIndices, indexCount,
                    minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /** Combined bark and foliage accumulators for the trees in one chunk. */
    static final class Buffers {
        final Part bark = new Part();
        final Part leaves = new Part();

        boolean isEmpty() {
            return bark.isEmpty() && leaves.isEmpty();
        }

        Meshes build() {
            return new Meshes(bark.isEmpty() ? null : bark.build(), leaves.isEmpty() ? null : leaves.build());
        }
    }

    /**
     * Finished bark and foliage meshes generated for a chunk.
     */
        record Meshes(TreeMesh bark, TreeMesh leaves) {
    }

    /**
     * Finished tree mesh buffers with bounds in chunk-local coordinates.
     */
        record TreeMesh(FloatBuffer positions, FloatBuffer normals, FloatBuffer uvs, FloatBuffer colors, FloatBuffer params,
                        IntBuffer indices, ShortBuffer shortIndices, int indexCount, float minX, float minY, float minZ,
                        float maxX, float maxY, float maxZ) {
    }

    /**
     * Branch point that receives one or more foliage cards.
     */
        private record Anchor(Vector3f pos, Vector3f dir, float size) {
    }

    /**
     * Adds one tree to a chunk's bark and foliage buffers.
     *
     * @param out output buffers shared by the chunk
     * @param noise world noise used to bend the trunk and branches
     * @param seed per-tree seed; identical inputs produce identical geometry
     * @param x chunk-local X coordinate
     * @param y terrain height at the tree base
     * @param z chunk-local Z coordinate
     * @param species tree species
     * @param vigor relative tree size in [0, 1]
     * @param tone foliage color tone in [0, 1]
     * @param lod detail level
     */
    static void add(Buffers out, PerlinNoise noise, long seed, float x, float y, float z,
                    Species species, float vigor, float tone, int lod) {
        final boolean high = lod == LOD_HIGH;
        final Spec s = spec(species);
        final Random rnd = new Random(seed);
        final double ox = rnd.nextDouble() * 1000.0;
        final double oy = rnd.nextDouble() * 1000.0;
        final double oz = rnd.nextDouble() * 1000.0;

        final float height = (s.minHeight + (s.maxHeight - s.minHeight) * (0.25f + 0.75f * vigor))
                * (0.85f + 0.30f * rnd.nextFloat());
        final float r0 = height * s.radiusRatio * (0.9f + 0.2f * rnd.nextFloat());
        final float trunkHeight = height * s.trunkTop;
        final float leanX = (rnd.nextFloat() - 0.5f) * 0.10f * height;
        final float leanZ = (rnd.nextFloat() - 0.5f) * 0.10f * height;

        // Build a Perlin-bent trunk with a flared base.
        final int rings = high ? 12 : 5;
        final Vector3f[] trunk = new Vector3f[rings + 1];
        final float[] trunkR = new float[rings + 1];
        for (int k = 0; k <= rings; k++) {
            float t = k / (float) rings;
            float bendX = (float) noise.fractal(ox + t * 1.3, oy, oz, 2, 0.5, 2.0) * height * 0.16f * t;
            float bendZ = (float) noise.fractal(ox, oy + t * 1.3, oz + 17.0, 2, 0.5, 2.0)
                    * height * 0.16f * t;
            trunk[k] = new Vector3f(x + bendX + leanX * t * t,
                    y + t * trunkHeight - 0.2f, z + bendZ + leanZ * t * t);
            float meters = t * trunkHeight;
            float flare = 1f + 0.9f * FastMath.exp(-meters / 0.7f);
            trunkR[k] = r0 * (float) Math.pow(1.0 - 0.92 * t, s.taper) * flare;
        }
        final float[] bark = s.barkTint;
        final float barkVariation = 0.85f + 0.3f * rnd.nextFloat();
        final float[] barkColor = {
                bark[0] * barkVariation, bark[1] * barkVariation, bark[2] * barkVariation
        };
        tube(out.bark, trunk, trunkR, high ? 9 : 5, barkColor, 0.55f, 1f);

        // Primary branches are laid out in a golden-angle spiral.
        final List<Anchor> anchors = new ArrayList<>();
        final int n = s.primaryHigh;
        final float maxLength = height * s.lengthRatio;
        for (int i = 0; i < n; i++) {
            float hFrac = lerp(s.crownStart, s.trunkTop - 0.02f, (i + 0.5f * rnd.nextFloat()) / n);
            float azimuth = i * 2.39996f + (rnd.nextFloat() - 0.5f) * 0.9f
                    + 0.8f * (float) noise.fractal(ox + i * 0.7, oy, oz + 5.0, 2, 0.5, 2.0);
            float pitchDeg = lerp(s.pitchMin, s.pitchMax, rnd.nextFloat());
            if (species != Species.PINE) {
                pitchDeg -= 18f * (hFrac - s.crownStart) / (1f - s.crownStart);
            }
            float lengthRoll = 0.8f + 0.4f * rnd.nextFloat();
            float profile = crownProfile(species, hFrac, s);
            float length = Math.max(0.5f, maxLength * profile * lengthRoll);
            int secondaries = 2 + rnd.nextInt(2);
            float secondaryAzimuth = rnd.nextFloat() * FastMath.TWO_PI;
            float[] secondarySpreadRolls = new float[secondaries];
            float[] secondaryLengthRolls = new float[secondaries];
            for (int j = 0; j < secondaries; j++) {
                secondarySpreadRolls[j] = rnd.nextFloat();
                secondaryLengthRolls[j] = rnd.nextFloat();
            }
            if (!high && (i % s.stride) != 0) continue;

            Vector3f attach = along(trunk, hFrac / s.trunkTop);
            float trunkRadius = alongRadius(trunkR, hFrac / s.trunkTop);
            float pitch = pitchDeg * FastMath.DEG_TO_RAD;
            Vector3f dir = new Vector3f(FastMath.sin(pitch) * FastMath.cos(azimuth), FastMath.cos(pitch),
                    FastMath.sin(pitch) * FastMath.sin(azimuth)).normalizeLocal();
            float radius0 = Math.max(0.02f, trunkRadius * (species == Species.PINE ? 0.30f : 0.62f)
                    * (0.55f + 0.45f * (length / maxLength)));
            Branch branch = branch(out.bark, noise, ox + i * 3.1, oy, oz, attach, dir, length, radius0,
                    s.gravity, high ? 6 : 3, high ? 6 : 4, barkColor);

            Vector3f tip = branch.points[branch.points.length - 1];
            Vector3f tipDir = tip.subtract(branch.points[branch.points.length - 2]).normalizeLocal();
            anchors.add(new Anchor(tip, tipDir, s.leafSize));
            if (!high) {
                anchors.add(new Anchor(along(branch.points, 0.6f), alongDir(branch.points, 0.6f),
                        s.leafSize * 0.9f));
            }
            if (high) {
                anchors.add(new Anchor(along(branch.points, 0.62f), alongDir(branch.points, 0.62f),
                        s.leafSize * 0.85f));
                anchors.add(new Anchor(along(branch.points, 0.38f), alongDir(branch.points, 0.38f),
                        s.leafSize * 0.75f));
                anchors.add(new Anchor(along(branch.points, 0.85f), alongDir(branch.points, 0.85f),
                        s.leafSize * 0.85f));
                for (int j = 0; j < secondaries; j++) {
                    float f = 0.40f + 0.50f * (j + 0.5f) / secondaries;
                    Vector3f start = along(branch.points, f);
                    Vector3f parentDir = alongDir(branch.points, f);
                    float spread = (38f + 24f * secondarySpreadRolls[j]) * FastMath.DEG_TO_RAD;
                    Vector3f side = parentDir.cross(Vector3f.UNIT_Y).normalizeLocal();
                    if (side.lengthSquared() < 1e-4f) side.set(1f, 0f, 0f);
                    Vector3f side2 = parentDir.cross(side);
                    float a = secondaryAzimuth + j * 2.1f;
                    Vector3f sideDir = side.mult(FastMath.cos(a)).addLocal(side2.mult(FastMath.sin(a)));
                    Vector3f secondaryDir = parentDir.mult(FastMath.cos(spread))
                            .addLocal(sideDir.mult(FastMath.sin(spread)));
                    secondaryDir.y += 0.25f;
                    secondaryDir.normalizeLocal();
                    float secondaryLength = length * (0.30f + 0.20f * secondaryLengthRolls[j])
                            * (1.1f - f * 0.6f);
                    float secondaryRadius = alongRadius(branch.radii, f) * 0.55f;
                    Branch secondary = branch(out.bark, noise, ox + i * 3.1 + j * 1.7, oy + 11.0, oz,
                            start, secondaryDir, secondaryLength, Math.max(0.012f, secondaryRadius),
                            s.gravity * 0.8f, 4, 5, barkColor);
                    anchors.add(new Anchor(secondary.points[secondary.points.length - 1],
                            secondary.points[secondary.points.length - 1]
                                    .subtract(secondary.points[secondary.points.length - 2]).normalizeLocal(),
                            s.leafSize * 0.9f));
                }
            }
        }

        // Add the crown leader for conifers and birches, or multiple leaders for oaks.
        Vector3f top = trunk[rings];
        if (species == Species.OAK) {
            int leaders = high ? 3 : 2;
            for (int i = 0; i < leaders; i++) {
                float azimuth = rnd.nextFloat() * FastMath.TWO_PI;
                float pitch = (12f + 16f * rnd.nextFloat()) * FastMath.DEG_TO_RAD;
                Vector3f direction = new Vector3f(FastMath.sin(pitch) * FastMath.cos(azimuth),
                        FastMath.cos(pitch), FastMath.sin(pitch) * FastMath.sin(azimuth)).normalizeLocal();
                float length = height * (0.26f + 0.10f * rnd.nextFloat());
                Branch leader = branch(out.bark, noise, ox + 40.0 + i * 2.3, oy, oz, top, direction, length,
                        Math.max(0.04f, trunkR[rings] * 1.8f), s.gravity, high ? 6 : 3,
                        high ? 6 : 4, barkColor);
                anchors.add(new Anchor(leader.points[leader.points.length - 1], direction, s.leafSize * 1.15f));
                if (high) {
                    anchors.add(new Anchor(along(leader.points, 0.6f), direction, s.leafSize));
                }

            }
        } else {
            anchors.add(new Anchor(top.add(0f, 0.3f, 0f), Vector3f.UNIT_Y,
                    s.leafSize * (species == Species.PINE ? 0.9f : 1.1f)));
        }

        if (anchors.isEmpty()) return;
        Vector3f crownCenter = new Vector3f();
        for (Anchor anchor : anchors) {
            crownCenter.addLocal(anchor.pos);
        }
        crownCenter.multLocal(1f / anchors.size());
        float crownRadius = 0.5f;
        for (Anchor anchor : anchors) {
            crownRadius = Math.max(crownRadius, anchor.pos.distance(crownCenter));
        }

        float[] leaf = s.leafTint;
        Random colorRandom = new Random(seed ^ 0xC6BC279692B5CC83L);
        float yellow = 0.35f * tone + 0.12f * colorRandom.nextFloat();
        float[] treeLeaf = {
                leaf[0] * (1f + 1.3f * yellow),
                leaf[1] * (1f + 0.15f * yellow),
                leaf[2] * (1f - 0.35f * yellow)
        };
        Random cardRandom = new Random(seed ^ 0x5DEECE66DL);
        final float sizeScale = high ? 1f : 1.8f;
        for (Anchor anchor : anchors) {
            int cards = species == Species.PINE ? 2 : 5;
            Anchor scaled = high ? anchor : new Anchor(anchor.pos, anchor.dir, anchor.size * sizeScale);
            for (int c = 0; c < cards; c++) {
                int cell = s.leafCell0 + cardRandom.nextInt(s.leafCells);
                card(out.leaves, cardRandom, species, scaled, c, cell, crownCenter, crownRadius, treeLeaf);
            }
        }
    }

    /**
     * Adds a low fern-like shrub made from the pine-spray atlas cell.
     *
     * @param out chunk mesh buffers
     * @param seed per-bush seed
     * @param x chunk-local X coordinate
     * @param y ground height
     * @param z chunk-local Z coordinate
     * @param lod detail level
     */
    static void addFern(Buffers out, long seed, float x, float y, float z, int lod) {
        Random random = new Random(seed);
        int fronds = lod == LOD_HIGH ? 7 : 5;
        float offsetAngle = random.nextFloat() * FastMath.TWO_PI;
        float offsetDistance = 1.0f + random.nextFloat() * 0.5f;
        Vector3f center = new Vector3f(x + FastMath.cos(offsetAngle) * offsetDistance, y,
                z + FastMath.sin(offsetAngle) * offsetDistance);
        Vector3f crownCenter = center.add(0f, 0.45f, 0f);
        float crownRadius = 1.2f;
        float[] tint = {0.035f, 0.16f, 0.035f};
        for (int i = 0; i < fronds; i++) {
            float angle = FastMath.TWO_PI * i / fronds + (random.nextFloat() - 0.5f) * 0.35f;
            float elevation = (24f + 18f * random.nextFloat()) * FastMath.DEG_TO_RAD;
            Vector3f direction = new Vector3f(FastMath.cos(angle) * FastMath.cos(elevation),
                    FastMath.sin(elevation), FastMath.sin(angle) * FastMath.cos(elevation)).normalizeLocal();
            float size = 1.0f + 0.45f * random.nextFloat();
            Anchor anchor = new Anchor(center.add(0f, 0.12f, 0f), direction, size);
            card(out.leaves, random, Species.PINE, anchor, i % 2, 3, crownCenter, crownRadius, tint);
        }
    }

    /**
     * Sampled centerline positions and radii of a generated branch.
     */
        private record Branch(Vector3f[] points, float[] radii) {
    }

    /** Builds a branch centerline bent by gravity or phototropism and three-dimensional noise. */
    private static Branch branch(Part bark, PerlinNoise noise, double ox, double oy, double oz, Vector3f start,
                                 Vector3f initialDir, float length, float radius0, float gravity, int rings,
                                 int sides, float[] tint) {
        Vector3f dir = initialDir.clone();
        Vector3f[] points = new Vector3f[rings + 1];
        float[] radii = new float[rings + 1];
        points[0] = start.subtract(initialDir.mult(radius0 * 1.2f));
        radii[0] = radius0;
        Vector3f point = start.clone();
        float step = length / rings;
        for (int k = 1; k <= rings; k++) {
            float t = k / (float) rings;
            dir.y -= gravity * step * 0.30f;
            double wx = noise.fractal(ox + point.x * 0.35, oy + point.y * 0.35, oz + point.z * 0.35,
                    2, 0.5, 2.0);
            double wz = noise.fractal(ox + 31.0 + point.z * 0.35, oy + point.x * 0.35,
                    oz + point.y * 0.35, 2, 0.5, 2.0);
            dir.x += (float) wx * 0.50f * step;
            dir.z += (float) wz * 0.50f * step;
            dir.normalizeLocal();
            point = point.add(dir.mult(step));
            points[k] = point;
            radii[k] = radius0 * (1f - 0.88f * t);
        }
        tube(bark, points, radii, sides, tint, 0.75f, 1f);
        return new Branch(points, radii);
    }

    /** Creates bark rings transported along the branch, with circumferential and length UVs. */
    private static void tube(Part part, Vector3f[] points, float[] radii, int sides,
                             float[] tint, float aoStart, float aoEnd) {
        int rings = points.length;
        Vector3f firstTangent = points[1].subtract(points[0]).normalizeLocal();
        Vector3f normal = firstTangent.cross(
                Math.abs(firstTangent.y) < 0.9f ? Vector3f.UNIT_Y : Vector3f.UNIT_X).normalizeLocal();
        float circumference = FastMath.TWO_PI * Math.max(radii[0], 0.03f);
        float uRepeat = Math.max(1f, Math.round(circumference / 0.9f));
        float v = 0f;
        int base = part.vertices;
        for (int i = 0; i < rings; i++) {
            Vector3f tangent = (i == 0 ? points[1].subtract(points[0])
                    : i == rings - 1 ? points[i].subtract(points[i - 1])
                    : points[i + 1].subtract(points[i - 1])).normalizeLocal();
            normal = normal.subtract(tangent.mult(normal.dot(tangent))).normalizeLocal();
            Vector3f binormal = tangent.cross(normal);
            if (i > 0) v += points[i].distance(points[i - 1]) / 1.3f;
            float ao = lerp(aoStart, aoEnd, i / (float) (rings - 1));
            for (int k = 0; k <= sides; k++) {
                float angle = FastMath.TWO_PI * k / sides;
                Vector3f direction = normal.mult(FastMath.cos(angle))
                        .addLocal(binormal.mult(FastMath.sin(angle)));
                Vector3f position = points[i].add(direction.mult(radii[i]));
                part.vertex(position, direction, k / (float) sides * uRepeat, v,
                        tint[0], tint[1], tint[2], ao, 0f, 0f);
            }
        }
        int stride = sides + 1;
        for (int i = 0; i < rings - 1; i++) {
            for (int k = 0; k < sides; k++) {
                int a = base + i * stride + k, b = a + 1, c = a + stride, d = c + 1;
                part.triangle(a, c, b);
                part.triangle(b, c, d);
            }
        }
    }

    /**
     * Places a foliage card at an anchor. Broadleaf cards face varied directions; pine cards align
     * with their branch, with a second card tilted to add volume.
     */
    private static void card(Part leaves, Random rnd, Species species, Anchor anchor, int index, int cell,
                             Vector3f crownCenter, float crownRadius, float[] tint) {
        Vector3f right, up;
        float halfWidth, halfHeight;
        Vector3f center;
        if (species == Species.PINE) {
            up = anchor.dir.clone().normalizeLocal();
            right = up.cross(Vector3f.UNIT_Y).normalizeLocal();
            if (right.lengthSquared() < 1e-4f) right.set(1f, 0f, 0f);
            if (index > 0) {
                Vector3f flat = up.cross(right).normalizeLocal();
                float tilt = 55f * FastMath.DEG_TO_RAD;
                right = right.mult(FastMath.cos(tilt)).addLocal(flat.mult(FastMath.sin(tilt))).normalizeLocal();
            }
            halfWidth = anchor.size * 0.45f;
            halfHeight = anchor.size * 0.60f;
            center = anchor.pos.add(up.mult(anchor.size * 0.30f));
        } else {
            Vector3f facing = new Vector3f(rnd.nextFloat() * 2f - 1f,
                    rnd.nextFloat() * 1.4f - 0.5f, rnd.nextFloat() * 2f - 1f);
            if (facing.lengthSquared() < 1e-4f) facing.set(0f, 1f, 0f);
            facing.normalizeLocal();
            right = facing.cross(Vector3f.UNIT_Y).normalizeLocal();
            if (right.lengthSquared() < 1e-4f) right.set(1f, 0f, 0f);
            up = right.cross(facing).normalizeLocal();
            float size = anchor.size * (0.80f + 0.40f * rnd.nextFloat());
            halfWidth = halfHeight = size * 0.5f;
            center = anchor.pos.add(rnd.nextFloat() - 0.5f,
                    (rnd.nextFloat() - 0.5f) * 0.8f, rnd.nextFloat() - 0.5f)
                    .addLocal(anchor.dir.mult(anchor.size * 0.15f));
        }

        Vector3f outward = center.subtract(crownCenter);
        float distance = outward.length();
        outward = distance > 1e-4f ? outward.divide(distance) : Vector3f.UNIT_Y.clone();
        Vector3f normal = outward.mult(species == Species.PINE ? 0.45f : 0.75f)
                .addLocal(0f, species == Species.PINE ? 0.85f : 0.45f, 0f).normalizeLocal();

        float depth = Math.min(1f, distance / crownRadius);
        float ao = 0.42f + 0.58f * depth * depth * (3f - 2f * depth);
        ao *= 0.82f + 0.18f * Math.max(0f,
                Math.min(1f, (center.y - crownCenter.y) / crownRadius * 0.5f + 0.5f));
        float variation = 0.80f + 0.40f * rnd.nextFloat();
        float phase = rnd.nextFloat();
        float flex = 0.45f + 0.55f * depth;

        float cu = (cell % 2) * 0.5f, cv = (cell / 2) * 0.5f;
        boolean flip = rnd.nextBoolean();
        float u0 = flip ? cu + 0.5f : cu, u1 = flip ? cu : cu + 0.5f;
        Vector3f rw = right.mult(halfWidth), uh = up.mult(halfHeight);
        int v0 = leaves.vertex(center.subtract(rw).subtractLocal(uh), normal, u0, cv,
                tint[0] * variation, tint[1] * variation, tint[2] * variation, ao, phase, flex);
        int v1 = leaves.vertex(center.add(rw).subtractLocal(uh), normal, u1, cv,
                tint[0] * variation, tint[1] * variation, tint[2] * variation, ao, phase, flex);
        int v2 = leaves.vertex(center.subtract(rw).addLocal(uh), normal, u0, cv + 0.5f,
                tint[0] * variation, tint[1] * variation, tint[2] * variation, ao, phase, flex);
        int v3 = leaves.vertex(center.add(rw).addLocal(uh), normal, u1, cv + 0.5f,
                tint[0] * variation, tint[1] * variation, tint[2] * variation, ao, phase, flex);
        leaves.triangle(v0, v1, v2);
        leaves.triangle(v2, v1, v3);
    }

    /** Computes relative branch length from insertion height to shape the crown profile. */
    private static float crownProfile(Species species, float h, Spec s) {
        float t = Math.max(0f, Math.min(1f, (h - s.crownStart) / (s.trunkTop - s.crownStart)));
        switch (species) {
            case PINE:
                return Math.max(0.12f, (float) Math.pow(1.0 - t, 0.9) * 0.95f + 0.05f);
            case BIRCH:
                return 0.25f + 0.75f * FastMath.sin(FastMath.PI * (0.15f + 0.8f * t));
            default:
                return 0.30f + 0.70f
                        * (float) Math.pow(FastMath.sin(FastMath.PI * (0.10f + 0.85f * t)), 0.7);
        }
    }

    /** Returns the point at fraction {@code f} of a polyline's segment-index length. */
    private static Vector3f along(Vector3f[] points, float f) {
        float x = Math.max(0f, Math.min(1f, f)) * (points.length - 1);
        int i = Math.min(points.length - 2, (int) x);
        return points[i].mult(1f - (x - i)).addLocal(points[i + 1].mult(x - i));
    }

    private static Vector3f alongDir(Vector3f[] points, float f) {
        int i = Math.min(points.length - 2,
                (int) (Math.max(0f, Math.min(1f, f)) * (points.length - 1)));
        return points[i + 1].subtract(points[i]).normalizeLocal();
    }

    private static float alongRadius(float[] radii, float f) {
        float x = Math.max(0f, Math.min(1f, f)) * (radii.length - 1);
        int i = Math.min(radii.length - 2, (int) x);
        return radii[i] * (1f - (x - i)) + radii[i + 1] * (x - i);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
