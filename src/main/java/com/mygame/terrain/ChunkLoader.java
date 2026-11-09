package com.mygame.terrain;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.mygame.config.GameConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Streams terrain chunks around the camera, generating mesh data asynchronously and installing
 * scene-graph objects on the render thread. At most {@code maxChunksPerFrame} chunks are assembled
 * per frame.
 *
 * <p>Grass is generated on demand only for chunks within {@code grass.distance}; sampled terrain
 * heights are retained so grass can be released when the camera moves away and regenerated
 * deterministically when it returns.
 */
public final class ChunkLoader {
    private static final Logger LOG = LogManager.getLogger(ChunkLoader.class);

    private record Result(long key, int generation, TerrainChunk.Data data) {
    }

    private record GrassResult(long key, int generation, TerrainChunk.GrassData data) {
    }

    private record TreeResult(long key, int generation, int lod, TreeShape.Meshes data, boolean successful) {
    }

    private final int renderDistance, maxNewChunksPerFrame, maxInFlight;
    private final int grassRadius, maxGrassInFlight, maxGrassBuildsPerFrame;
    private final int maxTreesInFlight;
    private final GameConfig config;
    private final Node terrainNode;
    private final Camera camera;
    private final Material material;
    private final Material grassMaterial;
    private final Material rockMaterial;
    private final Material barkMaterial;
    private final Material leavesMaterial;
    private final VertexBuffer sharedIndices;
    private final ExecutorService executor;

    // The following state is accessed only by the render thread.
    private final Map<Long, TerrainChunk> loaded = new HashMap<>();
    private final Set<Long> pending = new HashSet<>();
    private final Queue<Long> requestQueue = new ArrayDeque<>();
    private TerrainGenerator generator;
    private int generation;
    private boolean dirty = true;
    private int lastCx, lastCz;

    private final ConcurrentLinkedQueue<Result> ready = new ConcurrentLinkedQueue<>();

    private final Set<Long> grassPending = new HashSet<>();
    private final Queue<Long> grassQueue = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<GrassResult> grassReady = new ConcurrentLinkedQueue<>();
    private boolean grassDirty = true;
    private final Map<Long, Integer> treePending = new HashMap<>();
    private final ConcurrentLinkedQueue<TreeResult> treeReady = new ConcurrentLinkedQueue<>();

    /**
     * Creates the worker pool and loader for the supplied terrain scene and validated settings.
     *
     * @param terrainNode parent node for loaded chunk geometry
     * @param camera camera used to choose nearby chunks
     * @param material terrain material
     * @param grassMaterial grass material
     * @param rockMaterial rock material
     * @param barkMaterial tree trunk and branch material
     * @param leavesMaterial foliage material
     * @param generator seeded terrain generator
     * @param config runtime settings controlling streaming and geometry
     */
    public ChunkLoader(Node terrainNode, Camera camera, Material material, Material grassMaterial,
                       Material rockMaterial, Material barkMaterial, Material leavesMaterial,
                       TerrainGenerator generator, GameConfig config) {
        this.terrainNode = terrainNode;
        this.camera = camera;
        this.material = material;
        this.grassMaterial = grassMaterial;
        this.rockMaterial = rockMaterial;
        this.barkMaterial = barkMaterial;
        this.leavesMaterial = leavesMaterial;
        this.generator = generator;
        this.config = config;
        this.renderDistance = config.renderDistance;
        this.maxNewChunksPerFrame = config.maxChunksPerFrame;
        this.sharedIndices = TerrainChunk.createIndexBuffer(config.subdivisions);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.maxInFlight = Math.max(maxNewChunksPerFrame, threads * 2);
        // A chunk d + 1 indices away from the camera is at least d × chunkSize meters away.
        this.grassRadius = Math.min(renderDistance, (int) Math.ceil(config.grassDistance / config.chunkSize));
        this.maxGrassInFlight = threads * 2;
        this.maxGrassBuildsPerFrame = Math.max(1, maxNewChunksPerFrame / 2);
        this.maxTreesInFlight = Math.max(1, threads);
        final AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "ChunkWorker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        LOG.debug("Created chunk workers: threads={}, max in flight={}, grass radius={}",
                threads, maxInFlight, grassRadius);
    }

    private static long pack(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    private static int unpackX(long k) { return (int) (k >> 32); }
    private static int unpackZ(long k) { return (int) k; }

    private int coordToChunk(float coord) {
        return (int) Math.floor(coord / config.chunkSize);
    }

    private boolean inRange(int x, int z, int cx, int cz) {
        return Math.abs(x - cx) <= renderDistance && Math.abs(z - cz) <= renderDistance;
    }

    /**
     * Advances chunk streaming and attaches completed geometry.
     *
     * <p>Call once per frame on the render thread.
     */
    public void update() {
        Vector3f location = camera.getLocation();
        int cx = coordToChunk(location.x), cz = coordToChunk(location.z);

        drainReady(cx, cz);
        drainGrassReady(cx, cz);
        drainTreeReady();

        // Recalculate the region only when the camera enters a different chunk, including after teleporting.
        if (dirty || cx != lastCx || cz != lastCz) {
            dirty = false;
            lastCx = cx;
            lastCz = cz;
            unloadOutOfRange(cx, cz);
            trimGrass(cx, cz);
            rebuildRequestQueue(cx, cz);
            grassDirty = true;
            LOG.debug("Updated chunk requests around ({}, {}): {} chunks pending, {} loaded",
                    cx, cz, requestQueue.size(), loaded.size());
        }
        if (grassDirty) {
            grassDirty = false;
            rebuildGrassQueue(cx, cz);
        }
        submitRequests(location);
        submitGrassRequests();
        submitTreeRequests(location);
    }

    private void drainReady(int cx, int cz) {
        int built = 0;
        Result r;
        while (built < maxNewChunksPerFrame && (r = ready.poll()) != null) {
            if (r.generation != generation) continue; // result from an earlier seed
            pending.remove(r.key);
            if (r.data == null) continue;
            int x = unpackX(r.key), z = unpackZ(r.key);
            if (!inRange(x, z, cx, cz)) continue;    // the camera moved away before this result was ready
            TerrainChunk chunk = new TerrainChunk(x, z, r.data, material, rockMaterial,
                    barkMaterial, leavesMaterial, sharedIndices, config.chunkSize);
            loaded.put(r.key, chunk);
            terrainNode.attachChild(chunk.getGeometry());
            if (chunk.getRockGeometry() != null) terrainNode.attachChild(chunk.getRockGeometry());
            if (chunk.getBarkGeometry() != null) terrainNode.attachChild(chunk.getBarkGeometry());
            if (chunk.getLeavesGeometry() != null) terrainNode.attachChild(chunk.getLeavesGeometry());
            LOG.debug("Loaded tree meshes for chunk ({}, {}) at LOD {}: bark vertices={}, foliage vertices={}",
                    x, z, chunk.getTreeLod(), vertexCount(chunk.getBarkGeometry()),
                    vertexCount(chunk.getLeavesGeometry()));
            grassDirty = true;
            built++;
        }
    }

    private void unloadOutOfRange(int cx, int cz) {
        Iterator<Map.Entry<Long, TerrainChunk>> it = loaded.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, TerrainChunk> e = it.next();
            if (!inRange(unpackX(e.getKey()), unpackZ(e.getKey()), cx, cz)) {
                e.getValue().getGeometry().removeFromParent();
                if (e.getValue().getRockGeometry() != null) e.getValue().getRockGeometry().removeFromParent();
                e.getValue().clearGrass();
                e.getValue().clearTrees();
                it.remove();
            }
        }
    }

    private boolean inGrassRange(long key, int cx, int cz, int margin) {
        return Math.abs(unpackX(key) - cx) <= grassRadius + margin
                && Math.abs(unpackZ(key) - cz) <= grassRadius + margin;
    }

    private void drainGrassReady(int cx, int cz) {
        int built = 0;
        GrassResult r;
        while (built < maxGrassBuildsPerFrame && (r = grassReady.poll()) != null) {
            if (r.generation != generation) continue;
            grassPending.remove(r.key);
            TerrainChunk chunk = loaded.get(r.key);
            if (chunk == null || chunk.isGrassBuilt() || !inGrassRange(r.key, cx, cz, 1)) continue;
            Geometry grass = chunk.buildGrass(r.data, grassMaterial);
            if (grass != null) terrainNode.attachChild(grass);
            built++;
        }
    }

    private void drainTreeReady() {
        TreeResult result;
        while ((result = treeReady.poll()) != null) {
            if (result.generation != generation) continue;
            Integer pendingLod = treePending.get(result.key);
            if (pendingLod != null && pendingLod == result.lod) treePending.remove(result.key);
            TerrainChunk chunk = loaded.get(result.key);
            if (chunk == null || treeLodForChunk(chunk.getChunkX(), chunk.getChunkZ(), camera.getLocation())
                    != result.lod || !result.successful) {
                continue;
            }
            chunk.updateTrees(result.data, result.lod);
            if (chunk.getBarkGeometry() != null) terrainNode.attachChild(chunk.getBarkGeometry());
            if (chunk.getLeavesGeometry() != null) terrainNode.attachChild(chunk.getLeavesGeometry());
            LOG.debug("Updated tree detail for chunk ({}, {}) to LOD {}: bark vertices={}, foliage vertices={}",
                    chunk.getChunkX(), chunk.getChunkZ(), result.lod,
                    vertexCount(chunk.getBarkGeometry()), vertexCount(chunk.getLeavesGeometry()));
        }
    }

    private static int vertexCount(Geometry geometry) {
        return geometry == null ? 0 : geometry.getMesh().getVertexCount();
    }

    private int treeLodForChunk(int chunkX, int chunkZ, Vector3f cameraLocation) {
        double centerX = ((double) chunkX + 0.5) * config.chunkSize;
        double centerZ = ((double) chunkZ + 0.5) * config.chunkSize;
        double dx = centerX - cameraLocation.x, dz = centerZ - cameraLocation.z;
        return dx * dx + dz * dz <= config.treeDetailDistance * config.treeDetailDistance
                ? TreeShape.LOD_HIGH : TreeShape.LOD_LOW;
    }

    private void submitTreeRequests(Vector3f cameraLocation) {
        if (config.treeDensity <= 0f || treePending.size() >= maxTreesInFlight) return;
        List<TerrainChunk> candidates = new ArrayList<>();
        for (TerrainChunk chunk : loaded.values()) {
            int lod = treeLodForChunk(chunk.getChunkX(), chunk.getChunkZ(), cameraLocation);
            long key = pack(chunk.getChunkX(), chunk.getChunkZ());
            if (chunk.getTreeLod() != lod && !treePending.containsKey(key)) candidates.add(chunk);
        }
        candidates.sort(Comparator.comparingDouble(chunk -> {
            double dx = ((double) chunk.getChunkX() + 0.5) * config.chunkSize - cameraLocation.x;
            double dz = ((double) chunk.getChunkZ() + 0.5) * config.chunkSize - cameraLocation.z;
            return dx * dx + dz * dz;
        }));
        for (TerrainChunk chunk : candidates) {
            if (treePending.size() >= maxTreesInFlight) break;
            int x = chunk.getChunkX(), z = chunk.getChunkZ();
            long key = pack(x, z);
            int lod = treeLodForChunk(x, z, cameraLocation);
            treePending.put(key, lod);
            int gen = generation;
            TerrainGenerator treeGenerator = generator;
            float[] heights = chunk.getHeights();
            int heightSide = chunk.getHeightSide();
            int size = config.chunkSize, subdivisions = config.subdivisions;
            float waterHeight = config.waterHeight, density = config.treeDensity;
            executor.execute(() -> {
                TreeShape.Meshes data = null;
                boolean successful = true;
                try {
                    data = TerrainChunk.generateTrees(x, z, treeGenerator, heights, heightSide, size,
                            subdivisions, waterHeight, density, lod);
                } catch (RuntimeException e) {
                    successful = false;
                    LOG.error("Failed to generate trees for chunk ({}, {}) at LOD {}", x, z, lod, e);
                }
                treeReady.add(new TreeResult(key, gen, lod, data, successful));
            });
        }
    }

    /** Releases grass from distant chunks, keeping a one-chunk margin to avoid repeated rebuilds. */
    private void trimGrass(int cx, int cz) {
        for (Map.Entry<Long, TerrainChunk> e : loaded.entrySet()) {
            if (e.getValue().isGrassBuilt() && !inGrassRange(e.getKey(), cx, cz, 1)) {
                e.getValue().clearGrass();
            }
        }
    }

    private void rebuildGrassQueue(int cx, int cz) {
        List<Long> missing = new ArrayList<>();
        for (Map.Entry<Long, TerrainChunk> e : loaded.entrySet()) {
            long key = e.getKey();
            if (!e.getValue().isGrassBuilt() && !grassPending.contains(key) && inGrassRange(key, cx, cz, 0)) {
                missing.add(key);
            }
        }
        missing.sort(Comparator.comparingLong(k -> {
            long dx = unpackX(k) - cx, dz = unpackZ(k) - cz;
            return dx * dx + dz * dz;
        }));
        grassQueue.clear();
        grassQueue.addAll(missing);
    }

    private void submitGrassRequests() {
        while (grassPending.size() < maxGrassInFlight && !grassQueue.isEmpty()) {
            final long key = grassQueue.poll();
            TerrainChunk chunk = loaded.get(key);
            if (chunk == null || chunk.isGrassBuilt()) continue;
            grassPending.add(key);
            final int gen = generation;
            final TerrainGenerator terrainGenerator = generator;
            final float[] heights = chunk.getHeights();
            final int heightSide = chunk.getHeightSide();
            final int x = unpackX(key), z = unpackZ(key);
            final int size = config.chunkSize, sub = config.subdivisions;
            final float waterH = config.waterHeight, density = config.grassDensity, flowers = config.grassFlowers;
            executor.execute(() -> {
                TerrainChunk.GrassData data = null;
                try {
                    data = TerrainChunk.generateGrass(x, z, terrainGenerator, heights, heightSide,
                            size, sub, waterH, density, flowers);
                } catch (Throwable t) {
                    LOG.error("Failed to generate grass at chunk ({}, {})", x, z, t);
                }
                grassReady.add(new GrassResult(key, gen, data));
            });
        }
    }

    private void rebuildRequestQueue(int cx, int cz) {
        List<Long> missing = new ArrayList<>();
        for (int dz = -renderDistance; dz <= renderDistance; dz++) {
            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                long key = pack(cx + dx, cz + dz);
                if (!loaded.containsKey(key) && !pending.contains(key)) missing.add(key);
            }
        }
        missing.sort(Comparator.comparingLong(k -> {
            long dx = unpackX(k) - cx, dz = unpackZ(k) - cz;
            return dx * dx + dz * dz;
        }));
        requestQueue.clear();
        requestQueue.addAll(missing);
    }

    private void submitRequests(Vector3f cameraLocation) {
        while (pending.size() < maxInFlight && !requestQueue.isEmpty()) {
            final long key = requestQueue.poll();
            pending.add(key);
            final int gen = generation;
            final TerrainGenerator gen3d = generator;
            final int x = unpackX(key), z = unpackZ(key);
            final int size = config.chunkSize, sub = config.subdivisions;
            final float waterH = config.waterHeight;
            final float rockDensity = config.rockDensity, rockScale = config.rockSizeScale;
            final float treeDensity = config.treeDensity;
            final int treeLod = treeLodForChunk(x, z, cameraLocation);
            executor.execute(() -> {
                TerrainChunk.Data data = null;
                try {
                    data = TerrainChunk.generate(x, z, gen3d, size, sub, waterH,
                            rockDensity, rockScale, treeDensity, treeLod);
                } catch (Throwable t) {
                    LOG.error("Failed to generate chunk ({}, {})", x, z, t);
                }
                ready.add(new Result(key, gen, data));
            });
        }
    }

    /**
     * Discards loaded and pending work, then starts streaming with a new terrain generator.
     *
     * @param newGenerator generator to use for future chunk data
     */
    public void regenerate(TerrainGenerator newGenerator) {
        LOG.debug("Invalidating chunk generation {} and restarting with new seed", generation);
        generation++; // invalidate all in-progress work
        ready.clear();
        pending.clear();
        requestQueue.clear();
        grassReady.clear();
        grassPending.clear();
        grassQueue.clear();
        treeReady.clear();
        treePending.clear();
        terrainNode.detachAllChildren();
        loaded.clear();
        generator = newGenerator;
        dirty = true;
        update();
    }

    /** Stops background workers and clears completed results. */
    public void shutdown() {
        LOG.debug("Stopping chunk workers; loaded={}, pending={}", loaded.size(), pending.size());
        executor.shutdownNow();
        ready.clear();
        grassReady.clear();
    }

    /**
     * Returns the number of chunks currently attached to the terrain node.
     *
     * @return loaded chunk count
     */
    public int getLoadedChunkCount() { return loaded.size(); }

    /**
     * Returns the number of terrain chunks awaiting background generation.
     *
     * @return pending chunk count
     */
    public int getPendingChunkCount() { return pending.size(); }
}