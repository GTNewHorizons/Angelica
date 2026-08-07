package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.DrawCommandList;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.DrawCommandSink;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.IndirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.MultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GpuIndirectMultiDrawEmitter implements MultiDrawEmitter {

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

    private static final class PassState {
        int entryBase;
        int entryCount;
        int indexPointerMask;
        int maxElementCount;
        boolean dispatched;
        final Reference2LongOpenHashMap<RenderRegion> regionRanges = new Reference2LongOpenHashMap<>();
        final Reference2LongOpenHashMap<RenderRegion> regionEntryRanges = new Reference2LongOpenHashMap<>();

        PassState() {
            regionRanges.defaultReturnValue(-1L);
            regionEntryRanges.defaultReturnValue(-1L);
        }

        void reset(int mask, int base) {
            entryBase = base;
            entryCount = 0;
            indexPointerMask = mask;
            maxElementCount = 0;
            dispatched = false;
            regionRanges.clear();
            regionEntryRanges.clear();
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
    private int currentEntryStart;
    private int currentEntryCount;
    private RenderRegion currentRegion;

    @Setter private ByteBuffer frustumUboBytes;

    private IndirectMultiDrawEmitter cpuFallback;

    public static long sectionMetaBytes;

    private static int commandsSubmitted;
    private static int regionsDrawn;
    private static int maxRegionCommands;
    public static int commandsSubmitted() { return commandsSubmitted; }
    public static int takeRegionsDrawn() { final int n = regionsDrawn; regionsDrawn = 0; return n; }
    public static int takeMaxRegionCommands() { final int n = maxRegionCommands; maxRegionCommands = 0; return n; }

    private final SectionMetaBuffer.Sink uploadSectionMetaSink;

    public GpuIndirectMultiDrawEmitter(GpuDrivenChunkCuller culler, SectionMetaBuffer sectionMeta) {
        this.culler = culler;
        this.sectionMeta = sectionMeta;
        this.uploadSectionMetaSink = buf -> {
            final int bytes = buf.remaining();
            if (!culler.uploadSectionMeta(buf)) return false;
            if (Tracy.ENABLED) sectionMetaBytes += bytes;
            return true;
        };
    }

    public void beginCullPass(int indexPointerMask) {
        this.computeActiveThisPass = GpuCulling.mode().computeEnabled();
        this.totalAppendedEntries = 0;
        this.secondPrepared = false;
        primaryPass.reset(indexPointerMask, 0);
        this.buildPass = primaryPass;
        this.current = primaryPass;
        this.currentDrawStart = 0;
        this.currentDrawCount = 0;
        this.currentEntryStart = 0;
        this.currentEntryCount = 0;
        if (computeActiveThisPass && cpuFallback != null) {
            cpuFallback.getCommandSink().clear();
        }
    }

    private IndirectMultiDrawEmitter cpuFallback() {
        if (cpuFallback == null) cpuFallback = new IndirectMultiDrawEmitter();
        return cpuFallback;
    }

    public void beginCombinedPasses(int firstMask, int secondMask) {
        beginCullPass(firstMask);
        secondPass.reset(secondMask, 0);
    }

    public void startSecondPass() {
        primaryPass.entryCount = totalAppendedEntries - primaryPass.entryBase;
        secondPass.entryBase = totalAppendedEntries;
        this.buildPass = secondPass;
        this.current = secondPass;
    }

    public void finishCombinedBuild() {
        secondPass.entryCount = totalAppendedEntries - secondPass.entryBase;
        this.secondPrepared = true;
        this.buildPass = primaryPass;
        this.current = primaryPass;
    }

    public boolean selectPreparedSecondPass() {
        if (!secondPrepared || !computeActiveThisPass) return false;
        this.current = secondPass;
        this.currentDrawStart = 0;
        this.currentDrawCount = 0;
        this.currentEntryStart = 0;
        this.currentEntryCount = 0;
        return true;
    }

    public void reserveSections(int count) {
        if (count > 0) ensureStagingCapacity(totalAppendedEntries + count);
    }

    public int appendSection(int slot, int facingMask, int outputBase) {
        if ((outputBase & ~0x00FFFFFF) != 0) {
            throw new IllegalStateException("packed outputBase overflow: " + outputBase + " (max " + 0x00FFFFFF + ")");
        }
        if (totalAppendedEntries >= stagingCapacityEntries) ensureStagingCapacity(totalAppendedEntries + 1);
        final int idx = totalAppendedEntries;
        final int off = idx * ENTRY_BYTES;
        visibleStaging.putInt(off + 0, slot);
        visibleStaging.putInt(off + 4, (outputBase << 8) | (facingMask & 0xFF));
        totalAppendedEntries = idx + 1;
        buildPass.entryCount = totalAppendedEntries - buildPass.entryBase;
        visibleStagingView.limit(totalAppendedEntries * ENTRY_BYTES);
        return idx;
    }

    public void recordRegion(RenderRegion region, int drawStart, int drawCount, int entryStart, int entryCount, int maxElementCount) {
        if (drawCount <= 0) return;
        buildPass.regionRanges.put(region, (((long) drawStart) << 32) | (drawCount & 0xFFFFFFFFL));
        buildPass.regionEntryRanges.put(region, (((long) entryStart) << 32) | (entryCount & 0xFFFFFFFFL));
        if (maxElementCount > buildPass.maxElementCount) buildPass.maxElementCount = maxElementCount;
    }

    public void prepareRegion(RenderRegion region) {
        currentRegion = region;
        final long packed = current.regionRanges.getLong(region);
        if (packed < 0L) {
            currentDrawStart = 0;
            currentDrawCount = 0;
        } else {
            currentDrawStart = (int) (packed >>> 32);
            currentDrawCount = (int) (packed & 0xFFFFFFFFL);
        }
        final long entryPacked = current.regionEntryRanges.getLong(region);
        if (entryPacked < 0L) {
            currentEntryStart = 0;
            currentEntryCount = 0;
        } else {
            currentEntryStart = (int) (entryPacked >>> 32);
            currentEntryCount = (int) (entryPacked & 0xFFFFFFFFL);
        }
    }

    public void syncSectionMetaIfDirty() {
        culler.ensureReady();
        sectionMeta.syncIfDirty(uploadSectionMetaSink);
    }

    public void endPass() {
        currentDrawStart = 0;
        currentDrawCount = 0;
        currentEntryStart = 0;
        currentEntryCount = 0;
        if (current == secondPass) secondPrepared = false;
    }

    public void dispatchPreparedPasses() {
        if (!computeActiveThisPass || totalAppendedEntries == 0) return;
        if (!needsDispatch(primaryPass) && (!secondPrepared || !needsDispatch(secondPass))) return;
        if (!culler.ensureReady()) return;
        if (frustumUboBytes == null) {
            LOG.warn("GpuIndirectMultiDrawEmitter: frustum UBO not set before dispatch; skipping cull");
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
        final int indirectBytes = newCap * GpuDrivenChunkCuller.FACINGS_PER_SECTION * GpuDrivenChunkCuller.INDIRECT_COMMAND_BYTES;
        GLStateManager.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, ByteBuffer.allocateDirect(indirectBytes).order(ByteOrder.nativeOrder()), GL15.GL_STREAM_DRAW);
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

    private final DrawCommandSink computeSink = new DrawCommandSink() {
        @Override
        public void clear() {
        }

        @Override
        public int size() {
            return currentDrawCount;
        }

        @Override
        public int getIndexBufferSize() {
            return current.maxElementCount;
        }

        @Override
        public void push(int baseVertex, int elementCount, long indexOffset) {
        }
    };

    @Override
    public DrawCommandSink getCommandSink() {
        return computeActiveThisPass ? computeSink : cpuFallback().getCommandSink();
    }

    @Override
    public boolean batchesWholePass() {
        return !computeActiveThisPass && cpuFallback().batchesWholePass();
    }

    @Override
    public void beginPass(CommandList commandList, int sectionCount) {
        if (!computeActiveThisPass) cpuFallback().beginPass(commandList, sectionCount);
    }

    @Override
    public void finishAssembly(CommandList commandList) {
        if (!computeActiveThisPass) cpuFallback().finishAssembly(commandList);
    }

    @Override
    public void selectDrawRange(int firstCommand, int commandCount) {
        if (!computeActiveThisPass) cpuFallback().selectDrawRange(firstCommand, commandCount);
    }

    @Override
    public int getPendingCommandCount() {
        return computeActiveThisPass ? currentDrawCount : cpuFallback().getPendingCommandCount();
    }

    @Override
    public void onPassFinished(CommandList commandList) {
        if (!computeActiveThisPass) cpuFallback().onPassFinished(commandList);
    }

    @Override
    public void executeBatch(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        if (!computeActiveThisPass) {
            cpuFallback().executeBatch(commandList, tessellation, primitiveType);
            return;
        }
        if (currentDrawCount == 0) return;
        dispatchPreparedPasses();
        if (!current.dispatched) return;

        if (Tracy.ENABLED) {
            commandsSubmitted += currentDrawCount;
            regionsDrawn++;
            if (currentDrawCount > maxRegionCommands) maxRegionCommands = currentDrawCount;
        }

        final long offsetBytes = (long) currentDrawStart * GpuDrivenChunkCuller.INDIRECT_COMMAND_BYTES;
        GLStateManager.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, indirectSsboGlId);
        try (DrawCommandList ignored = commandList.beginTessellating(tessellation)) {
            GLStateManager.glMultiDrawElementsIndirect(primitiveType.getId(), GL11.GL_UNSIGNED_INT, offsetBytes, currentDrawCount, 0);
        }
        GLStateManager.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    @Override
    public void delete() {
        if (visibleStaging != null) {
            MemoryUtilities.memFree(visibleStaging);
            visibleStaging = null;
            visibleStagingView = null;
            stagingCapacityEntries = 0;
        }
        if (visibleSsboGlId != 0) { GLStateManager.glDeleteBuffers(visibleSsboGlId); visibleSsboGlId = 0; }
        if (indirectSsboGlId != 0) { GLStateManager.glDeleteBuffers(indirectSsboGlId); indirectSsboGlId = 0; }
        gpuCapacityEntries = 0;
        currentRegion = null;
        frustumUboBytes = null;
        primaryPass.regionRanges.clear();
        primaryPass.regionEntryRanges.clear();
        secondPass.regionRanges.clear();
        secondPass.regionEntryRanges.clear();
        if (cpuFallback != null) cpuFallback.delete();
    }
}
