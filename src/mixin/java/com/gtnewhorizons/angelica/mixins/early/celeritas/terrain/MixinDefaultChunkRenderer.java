package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.culling.FrustumExtractor;
import com.gtnewhorizons.angelica.rendering.culling.GpuIndirectMultiDrawEmitter;
import com.gtnewhorizons.angelica.mixins.interfaces.SectionRenderDataStorageRegionAccessor;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderPassConfiguration;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import net.coderbot.iris.pipeline.ShadowRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.MultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Iterator;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer {

    @Shadow @Final private MultiDrawEmitter emitter;

    @Shadow protected abstract boolean useBlockFaceCulling();

    @Unique private ByteBuffer angelica$frustumUboBytes;
    @Unique private Matrix4f angelica$mvp;
    @Unique private Matrix4f angelica$mvNoTranslation;
    @Unique private int angelica$passOutputBase;
    @Unique private ChunkPrimitiveType angelica$primitiveType;

    @Inject(method = "render", at = @At("HEAD"))
    private void angelica$gpuPreWalkPass(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera, CameraTransform camera, CallbackInfo ci) {
        if (!(emitter instanceof GpuIndirectMultiDrawEmitter gpu)) return;

        if (renderPass == AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS && renderLists.hasPass(renderPass) && gpu.selectPreparedSecondPass()) {
            return;
        }
        if (!renderLists.hasPass(renderPass)) return;

        final boolean combined = renderPass == AngelicaRenderPassConfiguration.SOLID_PASS && AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS != null && renderLists.hasPass(AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS);

        final int indexPointerMask = renderPass.isSorted() ? 0xFFFFFFFF : 0;
        if (combined) {
            gpu.beginCombinedPasses(0, 0);
        } else {
            gpu.beginCullPass(indexPointerMask);
        }
        angelica$passOutputBase = 0;
        angelica$primitiveType = null;

        if (!gpu.isComputeActiveThisPass()) {
            return;
        }

        if (angelica$frustumUboBytes == null) {
            angelica$frustumUboBytes = FrustumExtractor.allocateUboByteBuffer();
        }
        final boolean shadow = ShadowRenderer.ACTIVE;
        final Matrix4fc proj = matrices.projection();
        final Matrix4fc mv   = matrices.modelView();
        if (angelica$mvp == null) angelica$mvp = new Matrix4f();
        if (angelica$mvNoTranslation == null) angelica$mvNoTranslation = new Matrix4f();
        angelica$mvNoTranslation.set(mv).setTranslation(0f, 0f, 0f);
        proj.mul(angelica$mvNoTranslation, angelica$mvp);
        final float camX = (float) camera.intX + camera.fracX;
        final float camY = (float) camera.intY + camera.fracY;
        final float camZ = (float) camera.intZ + camera.fracZ;
        FrustumExtractor.writeStd140(angelica$mvp, 0, 0, angelica$frustumUboBytes);
        FrustumExtractor.patchCameraWorld(camX, camY, camZ, angelica$frustumUboBytes);
        FrustumExtractor.patchBypassFrustum(shadow, angelica$frustumUboBytes);

        gpu.setFrustumUboBytes(angelica$frustumUboBytes);

        gpu.syncSectionMetaIfDirty();

        angelica$walkPass(gpu, renderLists, renderPass, occlusionCamera);
        if (combined) {
            gpu.startSecondPass();
            angelica$walkPass(gpu, renderLists, AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS, occlusionCamera);
            gpu.finishCombinedBuild();
        }

        if (angelica$primitiveType != null) {
            FrustumExtractor.patchPrimitiveRatio(angelica$primitiveType.getVerticesPerPrimitive(),
                angelica$primitiveType.getIndexBufferElementsPerPrimitive(), angelica$frustumUboBytes);
        }
    }

    @Unique
    private void angelica$walkPass(GpuIndirectMultiDrawEmitter gpu, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera) {
        final boolean useFaceCulling = useBlockFaceCulling() && !renderPass.isSorted();
        final boolean reverse = renderPass.isReverseOrder();
        final boolean mergeRuns = !renderPass.isSorted();
        final Iterator<ChunkRenderList> it = renderLists.iterator(reverse);
        while (it.hasNext()) {
            final ChunkRenderList list = it.next();
            final RenderRegion region = list.getRegion();
            final SectionRenderDataStorage storage = region.getStorage(renderPass);
            if (storage == null) continue;
            final byte[] sections = list.getSectionsWithGeometry();
            final int sectionCount = list.getSectionsWithGeometryCount();
            if (sectionCount == 0) continue;
            final SectionRenderDataUnsafe.Strategy layout = storage.getStorageStrategy();
            final ChunkPrimitiveType primitiveType = storage.getPrimitiveType();
            if (angelica$primitiveType == null) {
                angelica$primitiveType = primitiveType;
            } else if (angelica$primitiveType.getVerticesPerPrimitive() != primitiveType.getVerticesPerPrimitive() || angelica$primitiveType.getIndexBufferElementsPerPrimitive() != primitiveType.getIndexBufferElementsPerPrimitive()) {
                throw new IllegalStateException("Batched cull dispatch spans passes with differing primitive ratios: " + angelica$primitiveType + " vs " + primitiveType);
            }
            final SectionRenderDataStorageRegionAccessor accessor = (SectionRenderDataStorageRegionAccessor) storage;
            final int[] slotCache = accessor.angelica$getSlotCache();
            if (slotCache == null) continue;

            gpu.reserveSections(list.getSectionsWithGeometryCount());

            final int regionOriginX = region.getChunkX();
            final int regionOriginY = region.getChunkY();
            final int regionOriginZ = region.getChunkZ();

            final int regionDrawStart = angelica$passOutputBase;
            int regionDrawCount = 0;
            int regionMaxElems = 0;
            int regionEntryStart = -1;
            int regionEntryCount = 0;

            int cursor = reverse ? sectionCount - 1 : 0;
            final int step = reverse ? -1 : 1;

            for (int i = 0; i < sectionCount; i++, cursor += step) {
                final int sectionIndex = sections[cursor] & 0xFF;
                final int chunkX = regionOriginX + LocalSectionIndex.unpackX(sectionIndex);
                final int chunkY = regionOriginY + LocalSectionIndex.unpackY(sectionIndex);
                final int chunkZ = regionOriginZ + LocalSectionIndex.unpackZ(sectionIndex);

                final long pMeshData = storage.getDataPointer(sectionIndex);
                int slices = useFaceCulling ? BatchAssembler.getVisibleFaces(occlusionCamera.intX, occlusionCamera.intY, occlusionCamera.intZ, chunkX, chunkY, chunkZ) : ModelQuadFacing.ALL;
                slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);
                if (slices == 0) continue;

                final int slot = slotCache[sectionIndex];
                if (slot < 0) continue;

                final long runs = mergeRuns ? BatchAssembler.packRuns(slices & 0x7F) : 0L;
                final int sectionDraws = mergeRuns ? BatchAssembler.runCount(runs) : Integer.bitCount(slices & 0x7F);
                final int entryIdx = gpu.appendSection(slot, slices, angelica$passOutputBase);
                if (regionEntryStart < 0) regionEntryStart = entryIdx;
                regionEntryCount++;
                angelica$passOutputBase += sectionDraws;
                regionDrawCount  += sectionDraws;

                int maxElems = 0;
                if (mergeRuns) {
                    for (int run = 0; run < sectionDraws; run++) {
                        final int startVertex = layout.getVertexOffset(pMeshData, BatchAssembler.runFirst(runs, run));
                        final int endVertex = layout.getRunVertexEnd(pMeshData, BatchAssembler.runLast(runs, run), primitiveType);
                        final int ec = SectionRenderDataUnsafe.elementsForVertices(endVertex - startVertex, primitiveType);
                        if (ec > maxElems) maxElems = ec;
                    }
                } else {
                    for (int f = 0; f < ModelQuadFacing.COUNT; f++) {
                        final int ec = layout.getElementCount(pMeshData, f, primitiveType);
                        if (ec > maxElems) maxElems = ec;
                    }
                }
                if (maxElems > regionMaxElems) regionMaxElems = maxElems;
            }

            if (regionDrawCount > 0) {
                gpu.recordRegion(region, regionDrawStart, regionDrawCount, regionEntryStart, regionEntryCount, regionMaxElems);
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void angelica$gpuEndPass(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera, CameraTransform camera, CallbackInfo ci) {
        if (emitter instanceof GpuIndirectMultiDrawEmitter gpu) {
            gpu.endPass();
        }
    }
}
