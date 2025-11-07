package me.jellysquid.mods.sodium.client.render.chunk.region;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.ChunkDrawCallBatcher;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;

public class ChunkRegion<T extends ChunkGraphicsState> {
    private static final int EXPECTED_CHUNK_SIZE = 4 * 1024;

    private final GlBufferArena arena;
    private final ChunkDrawCallBatcher batch;
    private final RenderDevice device;

    private final ObjectArrayList<ChunkBuildResult<T>> uploadQueue;

    private GlTessellation tessellation;

    private final int x, y, z;

    public float camDistance;

    public ChunkRegion(RenderDevice device, int size, int x, int y, int z) {
        int arenaSize = EXPECTED_CHUNK_SIZE * size;

        this.device = device;
        this.arena = new GlBufferArena(device, arenaSize, arenaSize);
        this.uploadQueue = new ObjectArrayList<>();

        this.batch = ChunkDrawCallBatcher.create(size * ModelQuadFacing.COUNT);

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getCenterBlockX() {
        return (this.x * ChunkRegionManager.BUFFER_WIDTH * 16) + (ChunkRegionManager.BUFFER_WIDTH / 2 * 16);
    }

    public int getCenterBlockY() {
        return (this.y * ChunkRegionManager.BUFFER_HEIGHT * 16) + (ChunkRegionManager.BUFFER_HEIGHT / 2 * 16);
    }

    public int getCenterBlockZ() {
        return (this.z * ChunkRegionManager.BUFFER_LENGTH * 16) + (ChunkRegionManager.BUFFER_LENGTH / 2 * 16);
    }

    public GlBufferArena getBufferArena() {
        return this.arena;
    }

    public boolean isArenaEmpty() {
        return this.arena.isEmpty();
    }

    public void deleteResources() {
        if (this.tessellation != null) {
            try (CommandList commands = this.device.createCommandList()) {
                commands.deleteTessellation(this.tessellation);
            }

            this.tessellation = null;
        }

        this.arena.delete();
        this.batch.delete();
    }

    public ObjectArrayList<ChunkBuildResult<T>> getUploadQueue() {
        return this.uploadQueue;
    }

    public ChunkDrawCallBatcher getDrawBatcher() {
        return this.batch;
    }

    public GlTessellation getTessellation() {
        return this.tessellation;
    }

    public void setTessellation(GlTessellation tessellation) {
        this.tessellation = tessellation;
    }
}
