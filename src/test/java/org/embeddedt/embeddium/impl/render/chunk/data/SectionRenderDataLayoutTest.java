package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SectionRenderDataLayoutTest {
    private static final ChunkPrimitiveType QUADS = new StubPrimitiveType(4, 6);
    private static final ChunkPrimitiveType TRIANGLES = new StubPrimitiveType(3, 3);

    private static final SectionRenderDataUnsafe.Strategy FULL = SectionRenderDataUnsafe.Strategy.FULL;
    private static final SectionRenderDataUnsafe.Strategy COMPACT = SectionRenderDataUnsafe.Strategy.COMPACT;

    private record StubPrimitiveType(int verticesPerPrimitive, int elementsPerPrimitive) implements ChunkPrimitiveType {
        @Override
        public int getVerticesPerPrimitive() {
            return this.verticesPerPrimitive;
        }

        @Override
        public int getIndexBufferElementsPerPrimitive() {
            return this.elementsPerPrimitive;
        }

        @Override
        public void generateSimpleIndexBuffer(ByteBuffer indexBuffer, int numPrimitives) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void generateSortedIndexBuffer(ByteBuffer indexBuffer, int numPrimitives, TranslucentQuadAnalyzer.SortState chunkData, float x, float y, float z) {
            throw new UnsupportedOperationException();
        }
    }

    private static Map<ModelQuadFacing, VertexRange> ranges(ChunkPrimitiveType type, int... primitivesPerFacing) {
        Map<ModelQuadFacing, VertexRange> map = new EnumMap<>(ModelQuadFacing.class);
        int start = 0;

        for (int facing = 0; facing < primitivesPerFacing.length; facing++) {
            int vertexCount = primitivesPerFacing[facing] * type.getVerticesPerPrimitive();

            if (vertexCount > 0) {
                map.put(ModelQuadFacing.VALUES[facing], new VertexRange(start, vertexCount));
            }

            start += vertexCount;
        }

        return map;
    }

    private static long alloc(SectionRenderDataUnsafe.Strategy strategy) {
        long heap = strategy.allocateHeap();
        assertNotEquals(0L, heap, "allocation failed");
        return heap;
    }

    @Test
    void bothLayoutsAgreeOnEveryPerFacingField() {
        Random random = new Random(20260801L);

        for (int trial = 0; trial < 200; trial++) {
            int[] primitives = new int[ModelQuadFacing.COUNT];
            for (int facing = 0; facing < primitives.length; facing++) {
                primitives[facing] = random.nextInt(4) == 0 ? 0 : random.nextInt(64);
            }

            int vertexBase = random.nextInt(100_000);
            var meshes = ranges(QUADS, primitives);

            long fullHeap = alloc(FULL);
            long compactHeap = alloc(COMPACT);

            try {
                FULL.writeMeshesAndSliceMask(fullHeap, 0, vertexBase, 0, meshes, QUADS);
                COMPACT.writeMeshesAndSliceMask(compactHeap, 0, vertexBase, 0, meshes, QUADS);

                assertEquals(FULL.getSliceMask(fullHeap, 0), COMPACT.getSliceMask(compactHeap, 0),
                        "slice mask must be layout independent");

                long full = FULL.heapPointer(fullHeap, 0);
                long compact = COMPACT.heapPointer(compactHeap, 0);

                for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                    assertEquals(FULL.getVertexOffset(full, facing),
                            COMPACT.getVertexOffset(compact, facing),
                            "vertex offset for facing " + facing);

                    assertEquals(FULL.getElementCount(full, facing, QUADS),
                            COMPACT.getElementCount(compact, facing, QUADS),
                            "element count for facing " + facing);

                    assertEquals(FULL.getRunVertexEnd(full, facing, QUADS),
                            COMPACT.getRunVertexEnd(compact, facing, QUADS),
                            "run end for facing " + facing);
                }
            } finally {
                FULL.freeHeap(fullHeap);
                COMPACT.freeHeap(compactHeap);
            }
        }
    }

    @Test
    void mergedRunElementCountEqualsSumOfItsFacings() {
        for (ChunkPrimitiveType type : new ChunkPrimitiveType[] { QUADS, TRIANGLES }) {
            var meshes = ranges(type, 3, 0, 7, 11, 0, 5, 2);
            long heap = alloc(COMPACT);

            try {
                COMPACT.writeMeshesAndSliceMask(heap, 0, 512, 0, meshes, type);
                long ptr = COMPACT.heapPointer(heap, 0);

                for (int first = 0; first < ModelQuadFacing.COUNT; first++) {
                    for (int last = first; last < ModelQuadFacing.COUNT; last++) {
                        int summed = 0;
                        for (int facing = first; facing <= last; facing++) {
                            summed += COMPACT.getElementCount(ptr, facing, type);
                        }

                        int start = COMPACT.getVertexOffset(ptr, first);
                        int end = COMPACT.getRunVertexEnd(ptr, last, type);
                        int merged = SectionRenderDataUnsafe.elementsForVertices(end - start, type);

                        assertEquals(summed, merged, "run [" + first + ", " + last + "]");
                    }
                }
            } finally {
                COMPACT.freeHeap(heap);
            }
        }
    }

    @Test
    void compactRebasePreservesEveryFacingSpan() {
        var meshes = ranges(QUADS, 5, 0, 9, 0, 0, 13, 1);

        long heap = alloc(COMPACT);

        try {
            COMPACT.writeMeshesAndSliceMask(heap, 0, 1024, 0, meshes, QUADS);
            long ptr = COMPACT.heapPointer(heap, 0);

            int[] spansBefore = new int[ModelQuadFacing.COUNT];
            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                spansBefore[facing] = COMPACT.getElementCount(ptr, facing, QUADS);
            }

            COMPACT.rebase(ptr, 8192, 0, QUADS);

            assertEquals(8192, COMPACT.getVertexOffset(ptr, 0), "rebased base offset");

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                assertEquals(spansBefore[facing], COMPACT.getElementCount(ptr, facing, QUADS),
                        "element count for facing " + facing + " changed across rebase");
            }
        } finally {
            COMPACT.freeHeap(heap);
        }
    }

    @Test
    void fullRebasePreservesEveryFacingSpan() {
        var meshes = ranges(QUADS, 5, 0, 9, 0, 0, 13, 1);

        long heap = alloc(FULL);

        try {
            FULL.writeMeshesAndSliceMask(heap, 0, 1024, 256, meshes, QUADS);
            long ptr = FULL.heapPointer(heap, 0);

            int[] spansBefore = new int[ModelQuadFacing.COUNT];
            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                spansBefore[facing] = FULL.getElementCount(ptr, facing, QUADS);
            }

            FULL.rebase(ptr, 8192, 512, QUADS);

            assertEquals(8192, FULL.getVertexOffset(ptr, 0), "rebased base offset");
            assertEquals(512, FULL.getIndexOffset(ptr, 0), "rebased index offset");

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                assertEquals(spansBefore[facing], FULL.getElementCount(ptr, facing, QUADS),
                        "element count for facing " + facing + " changed across rebase");
            }
        } finally {
            FULL.freeHeap(heap);
        }
    }

    @Test
    void compactDrawsFromTheSharedIndexBufferSoEveryFacingHasAZeroIndexOffset() {
        var meshes = ranges(QUADS, 4, 4, 4, 4, 4, 4, 4);
        long heap = alloc(COMPACT);

        try {
            COMPACT.writeMeshesAndSliceMask(heap, 0, 0, 4096, meshes, QUADS);
            long ptr = COMPACT.heapPointer(heap, 0);

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                assertEquals(0, COMPACT.getIndexOffset(ptr, facing),
                        "facing " + facing + " must draw from the shared index buffer");
            }
        } finally {
            COMPACT.freeHeap(heap);
        }
    }

    @Test
    void compactRejectsPerFacingIndexOffsets() {
        long heap = alloc(COMPACT);

        try {
            long ptr = COMPACT.heapPointer(heap, 0);
            assertThrows(UnsupportedOperationException.class, () -> COMPACT.writeIndexOffsets(ptr, 0, QUADS));
        } finally {
            COMPACT.freeHeap(heap);
        }
    }

    @Test
    void compactIsSmallerThanFull() {
        assertEquals(32, COMPACT.getStride());
        assertEquals(92, FULL.getStride());
    }

    @Test
    void onlyCompactReservesASliceMaskHeader() {
        assertEquals(0, FULL.getHeaderSize(), "the full layout keeps its slice mask in the row");
        assertEquals(RenderRegion.REGION_SIZE, COMPACT.getHeaderSize(), "one slice mask byte per section");

        for (var strategy : SectionRenderDataUnsafe.Strategy.values()) {
            long heap = alloc(strategy);

            try {
                assertEquals(strategy.getHeaderSize(), strategy.heapPointer(heap, 0) - heap,
                        strategy + " row 0 must start after the header");
            } finally {
                strategy.freeHeap(heap);
            }
        }
    }

    @Test
    void sliceMasksAreIndependentAcrossRows() {
        long heap = alloc(COMPACT);

        try {
            COMPACT.writeMeshesAndSliceMask(heap, 0, 0, 0, ranges(QUADS, 1, 0, 0, 0, 0, 0, 0), QUADS);
            COMPACT.writeMeshesAndSliceMask(heap, 1, 64, 0, ranges(QUADS, 0, 0, 2, 0, 0, 0, 0), QUADS);

            assertEquals(1 << 0, COMPACT.getSliceMask(heap, 0), "row 0 slice mask");
            assertEquals(1 << 2, COMPACT.getSliceMask(heap, 1), "row 1 slice mask");

            COMPACT.clearRow(heap, 0);

            assertEquals(0, COMPACT.getSliceMask(heap, 0), "row 0 cleared");
            assertEquals(1 << 2, COMPACT.getSliceMask(heap, 1), "clearing row 0 must not disturb row 1");
        } finally {
            COMPACT.freeHeap(heap);
        }
    }

    @Test
    void clearedRowsHaveNoPopulatedFacings() {
        var meshes = ranges(QUADS, 1, 2, 3, 4, 5, 6, 7);

        for (var strategy : SectionRenderDataUnsafe.Strategy.values()) {
            long heap = alloc(strategy);

            try {
                strategy.writeMeshesAndSliceMask(heap, 0, 64, 64, meshes, QUADS);
                assertNotEquals(0, strategy.getSliceMask(heap, 0));

                strategy.clearRow(heap, 0);
                assertEquals(0, strategy.getSliceMask(heap, 0), strategy + " slice mask after clear");

                long ptr = strategy.heapPointer(heap, 0);
                for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                    assertEquals(0, strategy.getElementCount(ptr, facing, QUADS),
                            strategy + " element count for facing " + facing + " after clear");
                }
            } finally {
                strategy.freeHeap(heap);
            }
        }
    }

    @Test
    void heapRowsAreStridedAndZeroInitialized() {
        for (var strategy : SectionRenderDataUnsafe.Strategy.values()) {
            long heap = alloc(strategy);

            try {
                long rowBase = strategy.getRowBasePointer(heap);

                for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
                    assertEquals(rowBase + (i * strategy.getStride()), strategy.heapPointer(heap, i), strategy + " row " + i);
                    assertEquals(0, LWJGL.memGetInt(strategy.heapPointer(heap, i)), strategy + " row " + i + " not zeroed");
                }
            } finally {
                strategy.freeHeap(heap);
            }
        }
    }
}
