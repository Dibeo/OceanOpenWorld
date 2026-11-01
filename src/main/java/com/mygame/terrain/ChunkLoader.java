package com.mygame.terrain;

import com.jme3.material.Material;
import com.mygame.config.GameConfig;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Charge les chunks proches et détache ceux qui sortent du rayon. */
public final class ChunkLoader {
    private final int renderDistance;
    private final int maxNewChunksPerFrame;
    private final GameConfig config;

    private final Node terrainNode;
    private final Camera camera;
    private final Material material;
    private final Map<ChunkKey,TerrainChunk> loaded = new HashMap<>();
    private TerrainGenerator generator;
    private long seed;

    private static final class ChunkKey {
        final int x,z;
        ChunkKey(int x,int z) { this.x=x; this.z=z; }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof ChunkKey)) return false;
            ChunkKey other=(ChunkKey)obj;
            return x==other.x && z==other.z;
        }
        @Override public int hashCode() { return 31*Integer.hashCode(x)+Integer.hashCode(z); }
    }

    public ChunkLoader(Node terrainNode,Camera camera,Material material,
                       TerrainGenerator generator,long seed,GameConfig config) {
        this.terrainNode=terrainNode;
        this.camera=camera;
        this.material=material;
        this.generator=generator;
        this.seed=seed;
        this.config=config;
        this.renderDistance=config.renderDistance;
        this.maxNewChunksPerFrame=config.maxChunksPerFrame;
    }

    private int coordToChunk(float coord) {
        return (int)Math.floor(coord/config.chunkSize);
    }

    public void update() {
        Vector3f location=camera.getLocation();
        int cx=coordToChunk(location.x), cz=coordToChunk(location.z);
        // Décharger les chunks hors de la zone, même après téléportation.
        Iterator<Map.Entry<ChunkKey,TerrainChunk>> iterator=loaded.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<ChunkKey,TerrainChunk> entry=iterator.next();
            ChunkKey key=entry.getKey();
            if(Math.abs(key.x-cx)>renderDistance || Math.abs(key.z-cz)>renderDistance) {
                entry.getValue().getGeometry().removeFromParent();
                iterator.remove();
            }
        }
        List<ChunkKey> missing=new ArrayList<>();
        for(int dz=-renderDistance;dz<=renderDistance;dz++) {
            for(int dx=-renderDistance;dx<=renderDistance;dx++) {
                ChunkKey key=new ChunkKey(cx+dx,cz+dz);
                if(!loaded.containsKey(key)) missing.add(key);
            }
        }
        missing.sort(Comparator.comparingInt(k ->
            (k.x-cx)*(k.x-cx)+(k.z-cz)*(k.z-cz)));
        int n=Math.min(maxNewChunksPerFrame,missing.size());
        for(int i=0;i<n;i++) {
            ChunkKey key=missing.get(i);
            TerrainChunk chunk=new TerrainChunk(key.x,key.z,generator,seed,material,config.chunkSize,config.subdivisions);
            loaded.put(key,chunk);
            terrainNode.attachChild(chunk.getGeometry());
        }
    }

    public void regenerate(TerrainGenerator newGenerator,long newSeed) {
        terrainNode.detachAllChildren();
        loaded.clear();
        generator=newGenerator;
        seed=newSeed;
        update();
    }

    public int getLoadedChunkCount() { return loaded.size(); }
}
