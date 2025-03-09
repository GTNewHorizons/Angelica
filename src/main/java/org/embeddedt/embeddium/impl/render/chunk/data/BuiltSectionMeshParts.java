package org.embeddedt.embeddium.impl.render.chunk.data;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;

public class BuiltSectionMeshParts {
    private final VertexRange[] ranges;
    private final NativeBuffer buffer;
    private final NativeBuffer indexBuffer;
    private final TranslucentQuadAnalyzer.SortState sortState;

    public BuiltSectionMeshParts(NativeBuffer buffer, @Nullable NativeBuffer indexBuffer, TranslucentQuadAnalyzer.SortState sortState, VertexRange[] ranges) {
        this.ranges = ranges;
        this.buffer = buffer;
        this.indexBuffer = indexBuffer;
        this.sortState = sortState;
    }

    public NativeBuffer getVertexData() {
        return this.buffer;
    }

    @Nullable
    public NativeBuffer getIndexData() {
        return this.indexBuffer;
    }

    public VertexRange[] getVertexRanges() {
        return this.ranges;
    }

    public TranslucentQuadAnalyzer.SortState getSortState() {
        return this.sortState;
    }

    public static Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> groupFromBuildBuffers(ChunkBuildBuffers buffers, float relativeCameraX, float relativeCameraY, float relativeCameraZ) {
        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();

        for (TerrainRenderPass pass : buffers.getBuilderPasses()) {
            BuiltSectionMeshParts mesh = buffers.createMesh(pass, relativeCameraX, relativeCameraY, relativeCameraZ);

            if (mesh != null) {
                meshes.put(pass, mesh);
            }
        }

        return meshes;
    }
}
