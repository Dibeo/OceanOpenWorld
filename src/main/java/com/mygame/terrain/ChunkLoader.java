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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Charge les chunks proches et détache ceux qui sortent du rayon.
 * Le calcul lourd (bruit, normales, poids de textures) se fait sur des threads de fond ;
 * seul l'assemblage du Mesh et l'attachement au scene graph ont lieu sur le thread de rendu,
 * au maximum {@code maxChunksPerFrame} par frame.
 *
 * <p>L'herbe est bien plus coûteuse que le terrain : elle n'existe que pour les chunks situés dans
 * {@code grass.distance} de la caméra. Elle est calculée à la demande (hauteurs conservées dans le
 * chunk), libérée quand la caméra s'éloigne, et regénérée à l'identique (placement déterministe).
 */
public final class ChunkLoader {
    private static final Logger LOG = Logger.getLogger(ChunkLoader.class.getName());

    private static final class Result {
        final long key; final int generation; final TerrainChunk.Data data;
        Result(long key, int generation, TerrainChunk.Data data) {
            this.key = key; this.generation = generation; this.data = data;
        }
    }

    private static final class GrassResult {
        final long key; final int generation; final TerrainChunk.GrassData data;
        GrassResult(long key, int generation, TerrainChunk.GrassData data) {
            this.key = key; this.generation = generation; this.data = data;
        }
    }

    private final int renderDistance, maxNewChunksPerFrame, maxInFlight;
    private final int grassRadius, maxGrassInFlight, maxGrassBuildsPerFrame;
    private final GameConfig config;
    private final Node terrainNode;
    private final Camera camera;
    private final Material material;
    private final Material grassMaterial;
    private final Material rockMaterial;
    private final VertexBuffer sharedIndices;
    private final ExecutorService executor;

    // Tout ce qui suit n'est touché que par le thread de rendu.
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

    public ChunkLoader(Node terrainNode, Camera camera, Material material, Material grassMaterial,
                       Material rockMaterial, TerrainGenerator generator, GameConfig config) {
        this.terrainNode = terrainNode;
        this.camera = camera;
        this.material = material;
        this.grassMaterial = grassMaterial;
        this.rockMaterial = rockMaterial;
        this.generator = generator;
        this.config = config;
        this.renderDistance = config.renderDistance;
        this.maxNewChunksPerFrame = config.maxChunksPerFrame;
        this.sharedIndices = TerrainChunk.createIndexBuffer(config.subdivisions);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.maxInFlight = Math.max(maxNewChunksPerFrame, threads * 2);
        // Un chunk à l'indice d + 1 de la caméra est à au moins d × chunkSize mètres.
        this.grassRadius = Math.min(renderDistance, (int) Math.ceil(config.grassDistance / config.chunkSize));
        this.maxGrassInFlight = threads * 2;
        this.maxGrassBuildsPerFrame = Math.max(1, maxNewChunksPerFrame / 2);
        final AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "ChunkWorker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
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

    /** À appeler à chaque frame sur le thread de rendu. */
    public void update() {
        Vector3f location = camera.getLocation();
        int cx = coordToChunk(location.x), cz = coordToChunk(location.z);

        drainReady(cx, cz);
        drainGrassReady(cx, cz);

        // Recalcul de la zone seulement quand la caméra change de chunk (même après téléportation).
        if (dirty || cx != lastCx || cz != lastCz) {
            dirty = false;
            lastCx = cx;
            lastCz = cz;
            unloadOutOfRange(cx, cz);
            trimGrass(cx, cz);
            rebuildRequestQueue(cx, cz);
            grassDirty = true;
        }
        if (grassDirty) {
            grassDirty = false;
            rebuildGrassQueue(cx, cz);
        }
        submitRequests();
        submitGrassRequests();
    }

    private void drainReady(int cx, int cz) {
        int built = 0;
        Result r;
        while (built < maxNewChunksPerFrame && (r = ready.poll()) != null) {
            if (r.generation != generation) continue; // résultat d'une ancienne seed
            pending.remove(r.key);
            if (r.data == null) continue;
            int x = unpackX(r.key), z = unpackZ(r.key);
            if (!inRange(x, z, cx, cz)) continue;     // la caméra s'est éloignée entre-temps
            TerrainChunk chunk = new TerrainChunk(x, z, r.data, material, rockMaterial, sharedIndices,
                    config.chunkSize);
            loaded.put(r.key, chunk);
            terrainNode.attachChild(chunk.getGeometry());
            if (chunk.getRockGeometry() != null) terrainNode.attachChild(chunk.getRockGeometry());
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

    /** Libère l'herbe des chunks qui s'éloignent (marge d'un chunk contre les allers-retours). */
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
                    LOG.log(Level.SEVERE, "Échec de génération de l'herbe " + x + "," + z, t);
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

    private void submitRequests() {
        while (pending.size() < maxInFlight && !requestQueue.isEmpty()) {
            final long key = requestQueue.poll();
            pending.add(key);
            final int gen = generation;
            final TerrainGenerator gen3d = generator;
            final int x = unpackX(key), z = unpackZ(key);
            final int size = config.chunkSize, sub = config.subdivisions;
            final float waterH = config.waterHeight;
            final float rockDensity = config.rockDensity, rockScale = config.rockSizeScale;
            executor.execute(() -> {
                TerrainChunk.Data data = null;
                try {
                    data = TerrainChunk.generate(x, z, gen3d, size, sub, waterH, rockDensity, rockScale);
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, "Échec de génération du chunk " + x + "," + z, t);
                }
                ready.add(new Result(key, gen, data));
            });
        }
    }

    public void regenerate(TerrainGenerator newGenerator) {
        generation++; // invalide tous les calculs en cours
        ready.clear();
        pending.clear();
        requestQueue.clear();
        grassReady.clear();
        grassPending.clear();
        grassQueue.clear();
        terrainNode.detachAllChildren();
        loaded.clear();
        generator = newGenerator;
        dirty = true;
        update();
    }

    /** Arrête les threads de fond. */
    public void shutdown() {
        executor.shutdownNow();
        ready.clear();
        grassReady.clear();
    }

    public int getLoadedChunkCount() { return loaded.size(); }
    public int getPendingChunkCount() { return pending.size(); }
}