package com.gtnewhorizons.angelica.rendering.voxelization;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
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

public final class ShadowVoxelizer {

    public interface Sink {
        /** @return false to skip this region */
        boolean region(RenderRegion region, GlVertexFormat format, float offsetX, float offsetY, float offsetZ);

        /** @return false to skip the rest of this region; later regions are still attempted */
        boolean range(int vertexOffset, int vertexCount);

        void finish();
    }

    public void walkPass(ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, GlVertexFormat format, CameraTransform camera, CameraTransform occlusionCamera, boolean useBlockFaceCulling, Sink sink) {
        try {
            final boolean reverse = renderPass.isReverseOrder();
            final int n = renderLists.getNumRegions();
            if (renderPass.isSorted()) {
                for (int i = 0; i < n; i++) {
                    walkRegionFull(renderLists.getRegion(reverse ? n - 1 - i : i), renderPass, format, camera, sink);
                }
            } else {
                for (int i = 0; i < n; i++) {
                    walkRegionCompact(renderLists.getRegion(reverse ? n - 1 - i : i), renderPass, format, camera, occlusionCamera, useBlockFaceCulling, sink);
                }
            }
        } finally {
            sink.finish();
        }
    }

    private static void walkRegionCompact(ChunkRenderList list, TerrainRenderPass renderPass, GlVertexFormat format, CameraTransform camera, CameraTransform occlusionCamera, boolean useBlockFaceCulling, Sink sink) {
        final RenderRegion region = list.getRegion();
        final SectionRenderDataStorage storage = region.getStorage(renderPass);
        if (storage == null) return;

        final int sectionCount = list.getSectionsWithGeometryCount();
        if (sectionCount == 0) return;

        final byte[] sections = list.getSectionsWithGeometry();
        final long pBase = storage.getRowBasePointer();
        final long stride = SectionRenderDataUnsafe.Strategy.COMPACT.getStride();
        final ChunkPrimitiveType primitiveType = storage.getPrimitiveType();
        final int regionChunkX = region.getChunkX();
        final int regionChunkY = region.getChunkY();
        final int regionChunkZ = region.getChunkZ();
        boolean bound = false;

        for (int i = 0; i < sectionCount; i++) {
            final int sectionIndex = sections[i] & 0xFF;
            int slices = useBlockFaceCulling
                ? BatchAssembler.getVisibleFaces(occlusionCamera.intX, occlusionCamera.intY, occlusionCamera.intZ,
                    regionChunkX + LocalSectionIndex.unpackX(sectionIndex),
                    regionChunkY + LocalSectionIndex.unpackY(sectionIndex),
                    regionChunkZ + LocalSectionIndex.unpackZ(sectionIndex))
                : ModelQuadFacing.ALL;
            slices &= storage.getSliceMask(sectionIndex);
            slices &= 0x7F;
            if (slices == 0) continue;

            final long pMeshData = pBase + sectionIndex * stride;
            final long runs = BatchAssembler.packRuns(slices);
            final int runCount = BatchAssembler.runCount(runs);
            for (int run = 0; run < runCount; run++) {
                final int startVertex = SectionRenderDataUnsafe.Strategy.COMPACT.getVertexOffset(pMeshData, BatchAssembler.runFirst(runs, run));
                final int endVertex = SectionRenderDataUnsafe.Strategy.COMPACT.getRunVertexEnd(pMeshData, BatchAssembler.runLast(runs, run), primitiveType);
                final int vertexCount = endVertex - startVertex;
                if (vertexCount <= 0) continue;

                if (!bound) {
                    if (!bindRegion(region, format, camera, sink)) return;
                    bound = true;
                }
                if (!sink.range(startVertex, vertexCount)) return;
            }
        }
    }

    private static void walkRegionFull(ChunkRenderList list, TerrainRenderPass renderPass, GlVertexFormat format, CameraTransform camera, Sink sink) {
        final RenderRegion region = list.getRegion();
        final SectionRenderDataStorage storage = region.getStorage(renderPass);
        if (storage == null) return;

        final int sectionCount = list.getSectionsWithGeometryCount();
        if (sectionCount == 0) return;

        final byte[] sections = list.getSectionsWithGeometry();
        final long pBase = storage.getRowBasePointer();
        final long stride = SectionRenderDataUnsafe.Strategy.FULL.getStride();
        final ChunkPrimitiveType primitiveType = storage.getPrimitiveType();
        boolean bound = false;

        for (int i = 0; i < sectionCount; i++) {
            final int sectionIndex = sections[i] & 0xFF;
            final int slices = ModelQuadFacing.ALL & storage.getSliceMask(sectionIndex);
            if (slices == 0) continue;

            final long pMeshData = pBase + sectionIndex * stride;
            final long runs = BatchAssembler.packRuns(slices);
            final int runCount = BatchAssembler.runCount(runs);
            for (int run = 0; run < runCount; run++) {
                final int startVertex = SectionRenderDataUnsafe.Strategy.FULL.getVertexOffset(pMeshData, BatchAssembler.runFirst(runs, run));
                final int endVertex = SectionRenderDataUnsafe.Strategy.FULL.getRunVertexEnd(pMeshData, BatchAssembler.runLast(runs, run), primitiveType);
                final int vertexCount = endVertex - startVertex;
                if (vertexCount <= 0) continue;

                if (!bound) {
                    if (!bindRegion(region, format, camera, sink)) return;
                    bound = true;
                }
                if (!sink.range(startVertex, vertexCount)) return;
            }
        }
    }

    private static boolean bindRegion(RenderRegion region, GlVertexFormat format, CameraTransform camera, Sink sink) {
        return sink.region(region, format,
            cameraRelative(region.getOriginX(), camera.intX, camera.fracX),
            cameraRelative(region.getOriginY(), camera.intY, camera.fracY),
            cameraRelative(region.getOriginZ(), camera.intZ, camera.fracZ));
    }

    private static float cameraRelative(int originBlock, int cameraBlock, float cameraFrac) {
        return (originBlock - cameraBlock) - cameraFrac;
    }
}
