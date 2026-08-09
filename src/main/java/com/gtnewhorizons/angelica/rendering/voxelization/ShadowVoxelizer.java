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

import java.util.Iterator;

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
            final Iterator<ChunkRenderList> it = renderLists.iterator(renderPass.isReverseOrder());
            while (it.hasNext()) {
                walkRegion(it.next(), renderPass, format, camera, occlusionCamera, useBlockFaceCulling, sink);
            }
        } finally {
            sink.finish();
        }
    }

    private static void walkRegion(ChunkRenderList list, TerrainRenderPass renderPass, GlVertexFormat format, CameraTransform camera, CameraTransform occlusionCamera, boolean useBlockFaceCulling, Sink sink) {
        final RenderRegion region = list.getRegion();
        final SectionRenderDataStorage storage = region.getStorage(renderPass);
        if (storage == null) return;

        final int sectionCount = list.getSectionsWithGeometryCount();
        if (sectionCount == 0) return;

        final byte[] sections = list.getSectionsWithGeometry();
        final SectionRenderDataUnsafe.Strategy layout = storage.getStorageStrategy();
        final ChunkPrimitiveType primitiveType = storage.getPrimitiveType();
        final int regionChunkX = region.getChunkX();
        final int regionChunkY = region.getChunkY();
        final int regionChunkZ = region.getChunkZ();
        boolean bound = false;

        for (int i = 0; i < sectionCount; i++) {
            final int sectionIndex = sections[i] & 0xFF;
            final long pMeshData = storage.getDataPointer(sectionIndex);
            int slices = useBlockFaceCulling
                ? BatchAssembler.getVisibleFaces(occlusionCamera.intX, occlusionCamera.intY, occlusionCamera.intZ,
                    regionChunkX + LocalSectionIndex.unpackX(sectionIndex),
                    regionChunkY + LocalSectionIndex.unpackY(sectionIndex),
                    regionChunkZ + LocalSectionIndex.unpackZ(sectionIndex))
                : ModelQuadFacing.ALL;
            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);
            slices &= 0x7F;
            if (slices == 0) continue;

            final long runs = BatchAssembler.packRuns(slices);
            final int runCount = BatchAssembler.runCount(runs);
            for (int run = 0; run < runCount; run++) {
                final int startVertex = layout.getVertexOffset(pMeshData, BatchAssembler.runFirst(runs, run));
                final int endVertex = layout.getRunVertexEnd(pMeshData, BatchAssembler.runLast(runs, run), primitiveType);
                final int vertexCount = endVertex - startVertex;
                if (vertexCount <= 0) continue;

                if (!bound) {
                    if (!sink.region(region, format,
                            cameraRelative(region.getOriginX(), camera.intX, camera.fracX),
                            cameraRelative(region.getOriginY(), camera.intY, camera.fracY),
                            cameraRelative(region.getOriginZ(), camera.intZ, camera.fracZ))) {
                        return;
                    }
                    bound = true;
                }
                if (!sink.range(startVertex, vertexCount)) return;
            }
        }
    }

    private static float cameraRelative(int originBlock, int cameraBlock, float cameraFrac) {
        return (originBlock - cameraBlock) - cameraFrac;
    }
}
