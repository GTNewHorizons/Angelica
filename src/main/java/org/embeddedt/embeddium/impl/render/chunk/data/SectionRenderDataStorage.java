package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.gl.arena.GlBufferSegment;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class SectionRenderDataStorage {
    private final GlBufferSegment[] allocations = new GlBufferSegment[RenderRegion.REGION_SIZE];
    private final GlBufferSegment[] indexAllocations = new GlBufferSegment[RenderRegion.REGION_SIZE];

    private final long pMeshDataArray;

    private int numAllocations;

    public SectionRenderDataStorage() {
        this.pMeshDataArray = SectionRenderDataUnsafe.allocateHeap(RenderRegion.REGION_SIZE);
        if (this.pMeshDataArray == 0) {
            throw new OutOfMemoryError("Failed to allocate mesh data array");
        }
    }

    public boolean isEmpty() {
        return this.numAllocations == 0;
    }

    public void setMeshes(int localSectionIndex,
                          GlBufferSegment allocation, @Nullable GlBufferSegment indexAllocation, VertexRange[] ranges) {
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;
            this.numAllocations--;
        }

        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;
        }

        this.allocations[localSectionIndex] = allocation;
        this.indexAllocations[localSectionIndex] = indexAllocation;
        this.numAllocations++;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int sliceMask = 0;
        int vertexOffset = allocation.getOffset();
        int indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        for (int facingIndex = 0; facingIndex < ModelQuadFacing.COUNT; facingIndex++) {
            VertexRange vertexRange = ranges[facingIndex];
            int vertexCount;

            if (vertexRange != null) {
                vertexCount = vertexRange.vertexCount();
            } else {
                vertexCount = 0;
            }

            int indexCount = (vertexCount >> 2) * 6;

            SectionRenderDataUnsafe.setVertexOffset(pMeshData, facingIndex, vertexOffset);
            SectionRenderDataUnsafe.setElementCount(pMeshData, facingIndex, indexCount);
            SectionRenderDataUnsafe.setIndexOffset(pMeshData, facingIndex, indexOffset);

            if (vertexCount > 0) {
                sliceMask |= 1 << facingIndex;
            }

            vertexOffset += vertexCount;
            indexOffset += indexCount * 4;
        }

        SectionRenderDataUnsafe.setSliceMask(pMeshData, sliceMask);
    }

    public void removeMeshes(int localSectionIndex) {
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;

            SectionRenderDataUnsafe.clear(this.getDataPointer(localSectionIndex));

            this.numAllocations--;
        }

        removeIndexBuffer(localSectionIndex);
    }

    public void removeIndexBuffer(int localSectionIndex) {
        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;
        }
    }

    public void replaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation) {
        removeIndexBuffer(localSectionIndex);

        this.indexAllocations[localSectionIndex] = indexAllocation;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        for (int facingIndex = 0; facingIndex < ModelQuadFacing.COUNT; facingIndex++) {
            SectionRenderDataUnsafe.setIndexOffset(pMeshData, facingIndex, indexOffset);
            int indexCount = SectionRenderDataUnsafe.getElementCount(pMeshData, facingIndex);
            indexOffset += indexCount * 4;
        }
    }

    public void onBufferResized() {
        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            this.updateMeshes(sectionIndex);
        }
    }

    private void updateMeshes(int sectionIndex) {
        var allocation = this.allocations[sectionIndex];

        if (allocation == null) {
            return;
        }

        var indexAllocation = this.indexAllocations[sectionIndex];

        var vertexOffset = allocation.getOffset();
        var indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        var data = this.getDataPointer(sectionIndex);

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            SectionRenderDataUnsafe.setVertexOffset(data, facing, vertexOffset);
            SectionRenderDataUnsafe.setIndexOffset(data, facing, indexOffset);

            var indexCount = SectionRenderDataUnsafe.getElementCount(data, facing);
            vertexOffset += (indexCount / 6) * 4; // convert elements back into vertices
            indexOffset += indexCount * 4;
        }
    }

    public long getDataPointer(int sectionIndex) {
        return SectionRenderDataUnsafe.heapPointer(this.pMeshDataArray, sectionIndex);
    }

    public void delete() {
        for (var allocation : this.allocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        for (var allocation : this.indexAllocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        Arrays.fill(this.allocations, null);
        Arrays.fill(this.indexAllocations, null);

        SectionRenderDataUnsafe.freeHeap(this.pMeshDataArray);

        this.numAllocations = 0;
    }
}