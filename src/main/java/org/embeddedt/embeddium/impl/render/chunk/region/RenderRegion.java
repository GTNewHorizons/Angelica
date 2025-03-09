package org.embeddedt.embeddium.impl.render.chunk.region;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.gl.arena.GlBufferArena;
import org.embeddedt.embeddium.impl.gl.arena.staging.StagingBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class RenderRegion {
    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    protected static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    protected static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    protected static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    static {
        Preconditions.checkArgument(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Preconditions.checkArgument(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Preconditions.checkArgument(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final StagingBuffer stagingBuffer;
    private final int x, y, z;

    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    private int sectionCount;

    private final Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData = new Reference2ReferenceOpenHashMap<>();
    private DeviceResources resources;
    private final int stride;

    public RenderRegion(int x, int y, int z, StagingBuffer stagingBuffer, int stride) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.stagingBuffer = stagingBuffer;
        this.stride = stride;
    }

    public static long key(int x, int y, int z) {
        return PositionUtil.packSection(x, y, z);
    }

    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    public int getCenterX() {
        return (this.getChunkX() + REGION_WIDTH / 2) << 4;
    }

    public int getCenterY() {
        return (this.getChunkY() + REGION_HEIGHT / 2) << 4;
    }

    public int getCenterZ() {
        return (this.getChunkZ() + REGION_LENGTH / 2) << 4;
    }

    public void delete(CommandList commandList) {
        for (var storage : this.sectionRenderData.values()) {
            storage.delete();
        }

        this.sectionRenderData.clear();

        if (this.resources != null) {
            this.resources.delete(commandList);
            this.resources = null;
        }

        Arrays.fill(this.sections, null);
    }

    public boolean isEmpty() {
        return this.sectionCount == 0;
    }

    public SectionRenderDataStorage getStorage(TerrainRenderPass pass) {
        return this.sectionRenderData.get(pass);
    }

    public SectionRenderDataStorage createStorage(TerrainRenderPass pass) {
        var storage = this.sectionRenderData.get(pass);

        if (storage == null) {
            this.sectionRenderData.put(pass, storage = new SectionRenderDataStorage());
        }

        return storage;
    }

    public void removeEmptyStorages() {
        if (this.sectionRenderData.isEmpty()) {
            return;
        }

        this.sectionRenderData.values().removeIf(s -> {
            if (s.isEmpty()) {
                s.delete();
                return true;
            } else {
                return false;
            }
        });
    }

    public void removeMeshes(int sectionIndex) {
        if (this.sectionRenderData.isEmpty()) {
            return;
        }
        for (var storage : this.sectionRenderData.values()) {
            storage.removeMeshes(sectionIndex);
        }
    }

    public boolean hasSectionsInPass(TerrainRenderPass pass) {
        return this.sectionRenderData.containsKey(pass);
    }

    public Set<TerrainRenderPass> getPasses() {
        return this.sectionRenderData.keySet();
    }

    public void refresh(CommandList commandList) {
        if (this.resources != null) {
            this.resources.deleteTessellations(commandList);
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.onBufferResized();
        }
    }

    public void addSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev != null) {
            throw new IllegalStateException("Section has already been added to the region");
        }

        this.sections[sectionIndex] = section;
        this.sectionCount++;
    }

    public void removeSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        } else if (prev != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.removeMeshes(sectionIndex);
        }

        this.sections[sectionIndex] = null;
        this.sectionCount--;
    }

    @Nullable
    public RenderSection getSection(int id) {
        return this.sections[id];
    }

    public DeviceResources getResources() {
        return this.resources;
    }

    public DeviceResources createResources(CommandList commandList) {
        if (this.resources == null) {
            this.resources = new DeviceResources(commandList, this.stagingBuffer, this.stride);
        }

        return this.resources;
    }

    public void update(CommandList commandList) {
        if (this.resources != null) {
            if (this.resources.shouldDelete()) {
                this.resources.delete(commandList);
                this.resources = null;
            } else {
                this.resources.deleteIndexArenaIfPossible(commandList);
            }
        }
    }

    public static class DeviceResources {
        private final GlBufferArena geometryArena;
        private final StagingBuffer stagingBuffer;
        private GlBufferArena indexArena;
        private GlTessellation tessellation;
        private GlTessellation indexedTessellation;

        public DeviceResources(CommandList commandList, StagingBuffer stagingBuffer, int stride) {
            this.geometryArena = new GlBufferArena(commandList, REGION_SIZE * 756, stride, stagingBuffer);
            this.stagingBuffer = stagingBuffer;
        }

        public void updateTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
            }

            this.tessellation = tessellation;
        }

        public GlTessellation getTessellation() {
            return this.tessellation;
        }

        public void updateIndexedTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.indexedTessellation != null) {
                this.indexedTessellation.delete(commandList);
            }

            this.indexedTessellation = tessellation;
        }

        public GlTessellation getIndexedTessellation() {
            return this.indexedTessellation;
        }

        public void deleteTessellations(CommandList commandList) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
                this.tessellation = null;
            }

            if (this.indexedTessellation != null) {
                this.indexedTessellation.delete(commandList);
                this.indexedTessellation = null;
            }
        }

        public GlBuffer getVertexBuffer() {
            return this.geometryArena.getBufferObject();
        }

        public GlBuffer getIndexBuffer() {
            if (this.indexArena == null) {
                throw new IllegalStateException("Attempted to retrieve index buffer for a non-indexed region");
            }
            return this.indexArena.getBufferObject();
        }

        public void delete(CommandList commandList) {
            this.deleteTessellations(commandList);
            this.geometryArena.delete(commandList);
            if (this.indexArena != null) {
                this.indexArena.delete(commandList);
            }
        }

        public GlBufferArena getGeometryArena() {
            return this.geometryArena;
        }


        public GlBufferArena getIndexArena() {
            return this.indexArena;
        }

        public GlBufferArena getOrCreateIndexArena(CommandList commandList) {
            if (this.indexArena == null) {
                this.indexArena = new GlBufferArena(commandList, (REGION_SIZE * 126) / 4 * 6, 4, this.stagingBuffer);
            }
            return this.indexArena;
        }

        public boolean shouldDelete() {
            return this.geometryArena.isEmpty();
        }

        public void deleteIndexArenaIfPossible(CommandList commandList) {
            if (this.indexArena != null && this.indexArena.isEmpty()) {
                this.updateIndexedTessellation(commandList, null);
                this.indexArena.delete(commandList);
                this.indexArena = null;
            }
        }
    }
}
