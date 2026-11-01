package com.mygame.terrain;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
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
 */
public final class ChunkLoader {
    private static final Logger LOG = Logger.getLogger(ChunkLoader.class.getName());

    private static final class Result {
        final long key; final int generation; final TerrainChunk.Data data;
        Result(long key, int generation, TerrainChunk.Data data) {
            this.key = key; this.generation = generation; this.data = data;
        }
    }

    private final int renderDistance, maxNewChunksPerFrame, maxInFlight;
    private final GameConfig config;
    private final Node terrainNode;
    private final Camera camera;
    private final Material material;
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

    public ChunkLoader(Node terrainNode, Camera camera, Material material,
                       TerrainGenerator generator, GameConfig config) {
        this.terrainNode = terrainNode;
        this.camera = camera;
        this.material = material;
        this.generator = generator;
        this.config = config;
        this.renderDistance = config.renderDistance;
        this.maxNewChunksPerFrame = config.maxChunksPerFrame;
        this.sharedIndices = TerrainChunk.createIndexBuffer(config.subdivisions);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.maxInFlight = Math.max(maxNewChunksPerFrame, threads * 2);
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

        // Recalcul de la zone seulement quand la caméra change de chunk (même après téléportation).
        if (dirty || cx != lastCx || cz != lastCz) {
            dirty = false;
            lastCx = cx;
            lastCz = cz;
            unloadOutOfRange(cx, cz);
            rebuildRequestQueue(cx, cz);
        }
        submitRequests();
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
            TerrainChunk chunk = new TerrainChunk(x, z, r.data, material, sharedIndices, config.chunkSize);
            loaded.put(r.key, chunk);
            terrainNode.attachChild(chunk.getGeometry());
            built++;
        }
    }

    private void unloadOutOfRange(int cx, int cz) {
        Iterator<Map.Entry<Long, TerrainChunk>> it = loaded.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, TerrainChunk> e = it.next();
            if (!inRange(unpackX(e.getKey()), unpackZ(e.getKey()), cx, cz)) {
                e.getValue().getGeometry().removeFromParent();
                it.remove();
            }
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
            executor.execute(() -> {
                TerrainChunk.Data data = null;
                try {
                    data = TerrainChunk.generate(x, z, gen3d, size, sub, waterH);
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
    }

    public int getLoadedChunkCount() { return loaded.size(); }
    public int getPendingChunkCount() { return pending.size(); }
}