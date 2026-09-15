package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.mixins.interfaces.SectionRenderDataStorageRegionAccessor;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderPassConfiguration;
import com.gtnewhorizons.angelica.rendering.celeritas.TerrainDrawStats;
import lombok.Getter;
import net.coderbot.iris.pipeline.ShadowRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.DrawCommandList;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class GpuTerrainCuller {

    private static final Logger LOG = LogManager.getLogger("Angelica-Culling");

    private static final int ENTRY_BYTES = GpuDrivenChunkCuller.VISIBLE_ENTRY_BYTES;

    private final GpuDrivenChunkCuller culler;
    private final SectionMetaBuffer sectionMeta;

    private ByteBuffer visibleStaging;
    private ByteBuffer visibleStagingView;
    private int stagingCapacityEntries;

    private int visibleSsboGlId;
    private int indirectSsboGlId;
    private int gpuCapacityEntries;

    private static final long[] EMPTY_RANGES = new long[0];
    private static final int[] EMPTY_STAMPS = new int[0];

    private static final class PassState {
        int entryBase;
        int entryCount;
        int indexPointerMask;
        int maxElementCount;
        boolean dispatched;

        int stamp;
        long[] regionRanges = EMPTY_RANGES;
        int[] regionStamps = EMPTY_STAMPS;

        void reset(int mask, int base) {
            entryBase = base;
            entryCount = 0;
            indexPointerMask = mask;
            maxElementCount = 0;
            dispatched = false;
            stamp++;
            if (stamp == 0) {
                Arrays.fill(regionStamps, 0);
                stamp = 1;
            }
        }

        void putRange(int regionId, long packed) {
            if (regionId >= regionStamps.length) {
                final int cap = Math.max(regionId + 1, Math.max(regionStamps.length * 2, 64));
                regionRanges = Arrays.copyOf(regionRanges, cap);
                regionStamps = Arrays.copyOf(regionStamps, cap);
            }
            regionRanges[regionId] = packed;
            regionStamps[regionId] = stamp;
        }

        long getRange(int regionId) {
            if (regionId >= regionStamps.length || regionStamps[regionId] != stamp) return -1L;
            return regionRanges[regionId];
        }

        void releaseRanges() {
            regionRanges = EMPTY_RANGES;
            regionStamps = EMPTY_STAMPS;
        }
    }

    private final PassState primaryPass = new PassState();
    private final PassState secondPass = new PassState();
    private PassState buildPass = primaryPass;
    private PassState current = primaryPass;
    private boolean secondPrepared;
    private int totalAppendedEntries;
    @Getter private boolean computeActiveThisPass;

    private int currentDrawStart;
    private int currentDrawCount;
    private boolean indirectBufferBound;

    private ByteBuffer frustumUboBytes;

    private final GpuCulledMultiDrawBatch batch = new GpuCulledMultiDrawBatch(this);

    private ByteBuffer uboBytes;
    private Matrix4f mvp;
    private Matrix4f mvNoTranslation;
    private int passOutputBase;
    private ChunkPrimitiveType walkPrimitiveType;

    private final SectionMetaBuffer.Sink uploadSectionMetaSink;

    public GpuTerrainCuller(GpuDrivenChunkCuller culler, SectionMetaBuffer sectionMeta) {
        this.culler = culler;
        this.sectionMeta = sectionMeta;
        this.uploadSectionMetaSink = buf -> {
            final int bytes = buf.remaining();
            if (!culler.uploadSectionMeta(buf)) return false;
            if (Tracy.ENABLED) TerrainDrawStats.recordSectionMetaBytes(bytes);
            return true;
        };
    }

    void beginCullPass(int indexPointerMask) {
        this.computeActiveThisPass = GpuCulling.mode().computeEnabled();
        this.totalAppendedEntries = 0;
        this.secondPrepared = false;
        primaryPass.reset(indexPointerMask, 0);
        this.buildPass = primaryPass;
        this.current = primaryPass;
        this.currentDrawStart = 0;
        this.currentDrawCount = 0;
    }

    void beginCombinedPasses(int firstMask, int secondMask) {
        beginCullPass(firstMask);
        secondPass.reset(secondMask, 0);
    }

    void startSecondPass() {
        primaryPass.entryCount = totalAppendedEntries - primaryPass.entryBase;
        secondPass.entryBase = totalAppendedEntries;
        this.buildPass = secondPass;
        this.current = secondPass;
    }

    void finishCombinedBuild() {
        secondPass.entryCount = totalAppendedEntries - secondPass.entryBase;
        this.secondPrepared = true;
        this.buildPass = primaryPass;
        this.current = primaryPass;
    }

    boolean selectPreparedSecondPass() {
        if (!secondPrepared || !computeActiveThisPass) return false;
        this.current = secondPass;
        this.currentDrawStart = 0;
        this.currentDrawCount = 0;
        return true;
    }

    void reserveSections(int count) {
        if (count > 0) ensureStagingCapacity(totalAppendedEntries + count);
    }

    void appendSection(int slot, int facingMask, int outputBase) {
        if ((outputBase & ~0x00FFFFFF) != 0) {
            throw new IllegalStateException("packed outputBase overflow: " + outputBase + " (max " + 0x00FFFFFF + ")");
        }
        if (totalAppendedEntries >= stagingCapacityEntries) ensureStagingCapacity(totalAppendedEntries + 1);
        final int off = totalAppendedEntries * ENTRY_BYTES;
        visibleStaging.putInt(off + 0, slot);
        visibleStaging.putInt(off + 4, (outputBase << 8) | (facingMask & 0xFF));
        totalAppendedEntries++;
        buildPass.entryCount = totalAppendedEntries - buildPass.entryBase;
    }

    void recordRegion(RenderRegion region, int drawStart, int drawCount, int maxElementCount) {
        if (drawCount <= 0) return;
        buildPass.putRange(region.getId(), (((long) drawStart) << 32) | (drawCount & 0xFFFFFFFFL));
        if (maxElementCount > buildPass.maxElementCount) buildPass.maxElementCount = maxElementCount;
    }

    private void prepareRegion(RenderRegion region) {
        final long packed = current.getRange(region.getId());
        if (packed < 0L) {
            currentDrawStart = 0;
            currentDrawCount = 0;
        } else {
            currentDrawStart = (int) (packed >>> 32);
            currentDrawCount = (int) (packed & 0xFFFFFFFFL);
        }
    }

    void syncSectionMetaIfDirty() {
        culler.ensureReady();
        sectionMeta.syncIfDirty(uploadSectionMetaSink);
    }

    public void endPass() {
        if (indirectBufferBound) {
            GLStateManager.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
            indirectBufferBound = false;
        }
        currentDrawStart = 0;
        currentDrawCount = 0;
        if (current == secondPass) secondPrepared = false;
    }

    private void dispatchPreparedPasses() {
        if (!computeActiveThisPass || totalAppendedEntries == 0) return;
        if (!needsDispatch(primaryPass) && (!secondPrepared || !needsDispatch(secondPass))) return;
        if (!culler.ensureReady()) return;
        if (frustumUboBytes == null) {
            LOG.warn("GpuTerrainCuller: frustum UBO not set before dispatch; skipping cull");
            return;
        }

        ensureGpuBuffers(totalAppendedEntries);
        visibleStagingView.position(0).limit(totalAppendedEntries * ENTRY_BYTES);
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, visibleSsboGlId);
        GLStateManager.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, visibleStagingView);

        BackendManager.RENDER_BACKEND.beginComputeDispatchBatch();
        try {
            dispatchPass(primaryPass);
            if (secondPrepared) dispatchPass(secondPass);
        } finally {
            BackendManager.RENDER_BACKEND.endComputeDispatchBatch();
        }
    }

    private static boolean needsDispatch(PassState r) {
        return !r.dispatched && r.entryCount > 0;
    }

    private void dispatchPass(PassState r) {
        if (!needsDispatch(r)) return;
        FrustumExtractor.patchControl(r.entryCount, r.indexPointerMask, frustumUboBytes);
        FrustumExtractor.patchBatchEntryBase(r.entryBase, frustumUboBytes);
        culler.uploadFrustum(frustumUboBytes);
        culler.dispatch(visibleSsboGlId, indirectSsboGlId, r.entryCount);
        r.dispatched = true;
    }

    private void ensureGpuBuffers(int maxVisible) {
        if (visibleSsboGlId == 0) visibleSsboGlId = GLStateManager.glGenBuffers();
        if (indirectSsboGlId == 0) indirectSsboGlId = GLStateManager.glGenBuffers();
        if (maxVisible <= gpuCapacityEntries) return;
        final int newCap = Math.max(maxVisible, Math.max(gpuCapacityEntries * 2, 64));
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, visibleSsboGlId);
        GLStateManager.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) newCap * ENTRY_BYTES, GL15.GL_STREAM_DRAW);
        GLStateManager.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, indirectSsboGlId);
        GLStateManager.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) newCap * GpuDrivenChunkCuller.FACINGS_PER_SECTION * GpuDrivenChunkCuller.INDIRECT_COMMAND_BYTES, GL15.GL_STREAM_DRAW);
        gpuCapacityEntries = newCap;
    }

    private void ensureStagingCapacity(int needed) {
        if (visibleStaging != null && needed <= stagingCapacityEntries) return;
        final int newCap = Math.max(needed, Math.max(stagingCapacityEntries * 2, 64));
        final ByteBuffer next = MemoryUtilities.memAlloc(newCap * ENTRY_BYTES).order(ByteOrder.nativeOrder());
        if (visibleStaging != null) {
            visibleStaging.position(0).limit(totalAppendedEntries * ENTRY_BYTES);
            next.put(visibleStaging);
            MemoryUtilities.memFree(visibleStaging);
        }
        next.position(0);
        next.limit(newCap * ENTRY_BYTES);
        visibleStaging = next;
        visibleStagingView = visibleStaging.duplicate().order(ByteOrder.nativeOrder());
        stagingCapacityEntries = newCap;
    }

    public void beginRenderPass(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera, CameraTransform camera, boolean useBlockFaceCulling) {
        if (renderPass == AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS && renderLists.hasPass(renderPass)
            && selectPreparedSecondPass()) {
            return;
        }
        if (!renderLists.hasPass(renderPass)) return;

        final boolean combined = renderPass == AngelicaRenderPassConfiguration.SOLID_PASS && AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS != null && renderLists.hasPass(AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS);

        final int indexPointerMask = renderPass.isSorted() ? 0xFFFFFFFF : 0;
        if (combined) {
            beginCombinedPasses(0, 0);
        } else {
            beginCullPass(indexPointerMask);
        }
        passOutputBase = 0;
        walkPrimitiveType = null;

        if (!computeActiveThisPass) {
            return;
        }

        if (uboBytes == null) {
            uboBytes = FrustumExtractor.allocateUboByteBuffer();
        }
        final boolean shadow = ShadowRenderer.ACTIVE;
        final Matrix4fc proj = matrices.projection();
        final Matrix4fc mv = matrices.modelView();
        if (mvp == null) mvp = new Matrix4f();
        if (mvNoTranslation == null) mvNoTranslation = new Matrix4f();
        mvNoTranslation.set(mv).setTranslation(0f, 0f, 0f);
        proj.mul(mvNoTranslation, mvp);
        final float camX = (float) camera.intX + camera.fracX;
        final float camY = (float) camera.intY + camera.fracY;
        final float camZ = (float) camera.intZ + camera.fracZ;
        FrustumExtractor.writeStd140(mvp, 0, 0, uboBytes);
        FrustumExtractor.patchCameraWorld(camX, camY, camZ, uboBytes);
        FrustumExtractor.patchBypassFrustum(shadow, uboBytes);

        frustumUboBytes = uboBytes;

        syncSectionMetaIfDirty();

        walkPass(renderLists, renderPass, occlusionCamera, useBlockFaceCulling);
        if (combined) {
            startSecondPass();
            walkPass(renderLists, AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS, occlusionCamera, useBlockFaceCulling);
            finishCombinedBuild();
        }

        if (walkPrimitiveType != null) {
            FrustumExtractor.patchPrimitiveRatio(walkPrimitiveType.getVerticesPerPrimitive(), walkPrimitiveType.getIndexBufferElementsPerPrimitive(), uboBytes);
        }
    }

    private void walkPass(ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera, boolean useBlockFaceCulling) {
        if (renderPass.isSorted()) {
            walkSortedPass(renderLists, renderPass);
        } else {
            walkMergedPass(renderLists, renderPass, occlusionCamera, useBlockFaceCulling);
        }
    }

    private void walkMergedPass(ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera, boolean useBlockFaceCulling) {
        final boolean reverse = renderPass.isReverseOrder();
        final int step = reverse ? -1 : 1;
        final long stride = SectionRenderDataUnsafe.Strategy.COMPACT.getStride();
        final int cameraX = occlusionCamera.intX;
        final int cameraY = occlusionCamera.intY;
        final int cameraZ = occlusionCamera.intZ;

        final int n = renderLists.getNumRegions();
        for (int regionIndex = 0; regionIndex < n; regionIndex++) {
            final ChunkRenderList list = renderLists.getRegion(reverse ? n - 1 - regionIndex : regionIndex);
            final RenderRegion region = list.getRegion();
            final SectionRenderDataStorage storage = region.getStorage(renderPass);
            if (storage == null) continue;
            final byte[] sections = list.getSectionsWithGeometry();
            final int sectionCount = list.getSectionsWithGeometryCount();
            if (sectionCount == 0) continue;
            final SectionRenderDataStorageRegionAccessor accessor = (SectionRenderDataStorageRegionAccessor) storage;
            final ChunkPrimitiveType primitiveType = storage.getPrimitiveType();
            trackPrimitiveType(primitiveType);
            final int[] slotCache = accessor.angelica$getSlotCache();
            if (slotCache == null) continue;

            reserveSections(sectionCount);

            final long pBase = storage.getRowBasePointer();
            final int regionOriginX = region.getChunkX();
            final int regionOriginY = region.getChunkY();
            final int regionOriginZ = region.getChunkZ();
            final int uniformMask = useBlockFaceCulling ? BatchAssembler.uniformCullMask(region, occlusionCamera) : ModelQuadFacing.ALL;

            final int regionDrawStart = passOutputBase;
            int regionDrawCount = 0;
            int regionMaxElems = 0;

            int cursor = reverse ? sectionCount - 1 : 0;

            for (int i = 0; i < sectionCount; i++, cursor += step) {
                final int sectionIndex = sections[cursor] & 0xFF;

                int slices = uniformMask != BatchAssembler.MASK_NOT_UNIFORM ? uniformMask : BatchAssembler.getVisibleFaces(cameraX, cameraY, cameraZ, regionOriginX + LocalSectionIndex.unpackX(sectionIndex), regionOriginY + LocalSectionIndex.unpackY(sectionIndex), regionOriginZ + LocalSectionIndex.unpackZ(sectionIndex));
                slices &= storage.getSliceMask(sectionIndex);
                if (slices == 0) continue;

                final int slot = slotCache[sectionIndex];
                if (slot < 0) continue;

                final long pMeshData = pBase + (sectionIndex * stride);
                final long runs = BatchAssembler.packRuns(slices & 0x7F);
                final int sectionDraws = BatchAssembler.runCount(runs);
                appendSection(slot, slices, passOutputBase);
                passOutputBase += sectionDraws;
                regionDrawCount += sectionDraws;

                int maxElems = 0;
                for (int run = 0; run < sectionDraws; run++) {
                    final int startVertex = SectionRenderDataUnsafe.Strategy.COMPACT.getVertexOffset(pMeshData, BatchAssembler.runFirst(runs, run));
                    final int endVertex = SectionRenderDataUnsafe.Strategy.COMPACT.getRunVertexEnd(pMeshData, BatchAssembler.runLast(runs, run), primitiveType);
                    final int ec = SectionRenderDataUnsafe.elementsForVertices(endVertex - startVertex, primitiveType);
                    if (ec > maxElems) maxElems = ec;
                }
                if (maxElems > regionMaxElems) regionMaxElems = maxElems;
            }

            if (regionDrawCount > 0) {
                recordRegion(region, regionDrawStart, regionDrawCount, regionMaxElems);
            }
        }
    }

    private void walkSortedPass(ChunkRenderListIterable renderLists, TerrainRenderPass renderPass) {
        final boolean reverse = renderPass.isReverseOrder();
        final int step = reverse ? -1 : 1;
        final long stride = SectionRenderDataUnsafe.Strategy.FULL.getStride();

        final int n = renderLists.getNumRegions();
        for (int regionIndex = 0; regionIndex < n; regionIndex++) {
            final ChunkRenderList list = renderLists.getRegion(reverse ? n - 1 - regionIndex : regionIndex);
            final RenderRegion region = list.getRegion();
            final SectionRenderDataStorage storage = region.getStorage(renderPass);
            if (storage == null) continue;
            final byte[] sections = list.getSectionsWithGeometry();
            final int sectionCount = list.getSectionsWithGeometryCount();
            if (sectionCount == 0) continue;
            final SectionRenderDataStorageRegionAccessor accessor = (SectionRenderDataStorageRegionAccessor) storage;
            final ChunkPrimitiveType primitiveType = storage.getPrimitiveType();
            trackPrimitiveType(primitiveType);
            final int[] slotCache = accessor.angelica$getSlotCache();
            if (slotCache == null) continue;

            reserveSections(sectionCount);

            final long pBase = storage.getRowBasePointer();

            final int regionDrawStart = passOutputBase;
            int regionDrawCount = 0;
            int regionMaxElems = 0;

            int cursor = reverse ? sectionCount - 1 : 0;

            for (int i = 0; i < sectionCount; i++, cursor += step) {
                final int sectionIndex = sections[cursor] & 0xFF;

                final int slices = ModelQuadFacing.ALL & storage.getSliceMask(sectionIndex);
                if (slices == 0) continue;

                final int slot = slotCache[sectionIndex];
                if (slot < 0) continue;

                final long pMeshData = pBase + (sectionIndex * stride);
                final int sectionDraws = Integer.bitCount(slices);
                appendSection(slot, slices, passOutputBase);
                passOutputBase += sectionDraws;
                regionDrawCount += sectionDraws;

                int maxElems = 0;
                for (int f = 0; f < ModelQuadFacing.COUNT; f++) {
                    final int ec = SectionRenderDataUnsafe.Strategy.FULL.getElementCount(pMeshData, f, primitiveType);
                    if (ec > maxElems) maxElems = ec;
                }
                if (maxElems > regionMaxElems) regionMaxElems = maxElems;
            }

            if (regionDrawCount > 0) {
                recordRegion(region, regionDrawStart, regionDrawCount, regionMaxElems);
            }
        }
    }

    private void trackPrimitiveType(ChunkPrimitiveType primitiveType) {
        if (walkPrimitiveType == null) {
            walkPrimitiveType = primitiveType;
        } else if (walkPrimitiveType.getVerticesPerPrimitive() != primitiveType.getVerticesPerPrimitive() || walkPrimitiveType.getIndexBufferElementsPerPrimitive() != primitiveType.getIndexBufferElementsPerPrimitive()) {
            throw new IllegalStateException("Batched cull dispatch spans passes with differing primitive ratios: " + walkPrimitiveType + " vs " + primitiveType);
        }
    }

    public GpuCulledMultiDrawBatch batchForRegion(RenderRegion region) {
        prepareRegion(region);
        if (currentDrawCount == 0) return null;
        batch.prepare(currentDrawStart, currentDrawCount, current.maxElementCount);
        return batch;
    }

    void drawRegionRange(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType, int drawStart, int drawCount) {
        if (drawCount == 0) return;
        dispatchPreparedPasses();
        if (!current.dispatched) return;

        if (Tracy.ENABLED) {
            TerrainDrawStats.recordCullRegion(drawCount);
        }

        if (!indirectBufferBound) {
            GLStateManager.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, indirectSsboGlId);
            indirectBufferBound = true;
        }

        final long offsetBytes = (long) drawStart * GpuDrivenChunkCuller.INDIRECT_COMMAND_BYTES;
        try (DrawCommandList ignored = commandList.beginTessellating(tessellation)) {
            GLStateManager.glMultiDrawElementsIndirect(primitiveType.getId(), GL11.GL_UNSIGNED_INT, offsetBytes, drawCount, 0);
        }
    }

    public void delete() {
        if (indirectBufferBound) {
            GLStateManager.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
            indirectBufferBound = false;
        }
        if (visibleStaging != null) {
            MemoryUtilities.memFree(visibleStaging);
            visibleStaging = null;
            visibleStagingView = null;
            stagingCapacityEntries = 0;
        }
        if (visibleSsboGlId != 0) {
            GLStateManager.glDeleteBuffers(visibleSsboGlId);
            visibleSsboGlId = 0;
        }
        if (indirectSsboGlId != 0) {
            GLStateManager.glDeleteBuffers(indirectSsboGlId);
            indirectSsboGlId = 0;
        }
        gpuCapacityEntries = 0;
        frustumUboBytes = null;
        uboBytes = null;
        primaryPass.releaseRanges();
        secondPass.releaseRanges();
    }
}
