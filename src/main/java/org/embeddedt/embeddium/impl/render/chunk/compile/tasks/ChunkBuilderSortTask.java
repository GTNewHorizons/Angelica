package org.embeddedt.embeddium.impl.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBufferSorter;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;

import java.util.Map;

public class ChunkBuilderSortTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private final RenderSection render;
    private final float cameraX, cameraY, cameraZ;
    private final int frame;
    private final Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> translucentMeshes;

    public ChunkBuilderSortTask(RenderSection render, float cameraX, float cameraY, float cameraZ, int frame, Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> translucentMeshes) {
        this.render = render;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.frame = frame;
        this.translucentMeshes = translucentMeshes;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationSource) {
        Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();
        for(Map.Entry<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> entry : translucentMeshes.entrySet()) {
            var sortBuffer = entry.getValue();
            var newIndexBuffer = new NativeBuffer(ChunkBufferSorter.getIndexBufferSize(sortBuffer.centersLength() / 3));
            ChunkBufferSorter.sort(newIndexBuffer, sortBuffer, cameraX - this.render.getOriginX(), cameraY - this.render.getOriginY(), cameraZ - this.render.getOriginZ());
            meshes.put(entry.getKey(), new BuiltSectionMeshParts(
                    null,
                    newIndexBuffer,
                    sortBuffer,
                    null
            ));
        }
        ChunkBuildOutput result = new ChunkBuildOutput(render, null, meshes, this.frame);
        result.setIndexOnlyUpload(true);
        return result;
    }
}
