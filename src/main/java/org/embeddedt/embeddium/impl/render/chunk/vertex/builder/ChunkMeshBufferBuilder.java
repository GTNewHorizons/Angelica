package org.embeddedt.embeddium.impl.render.chunk.vertex.builder;

import org.embeddedt.embeddium.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int stride;

    private final int initialCapacity;
    private final TranslucentQuadAnalyzer analyzer;

    private ByteBuffer buffer;
    private int count;
    private int capacity;
    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexEncoder encoder, int stride, int initialCapacity, boolean collectSortState) {
        this.encoder = encoder;
        this.stride = stride;

        this.buffer = null;

        this.capacity = 0;
        this.initialCapacity = initialCapacity;

        this.analyzer = collectSortState ? new TranslucentQuadAnalyzer() : null;
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        var vertexStart = this.count * this.stride;
        var vertexSize = vertices.length * this.stride;

        if (vertexStart + vertexSize >= this.capacity) {
            this.grow(vertexSize);
        }

        long ptr = MemoryUtil.memAddress(this.buffer, vertexStart);

        if (this.analyzer != null) {
            for (ChunkVertexEncoder.Vertex vertex : vertices) {
                this.analyzer.capture(vertex);
            }
        }

        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            ptr = this.encoder.write(ptr, material, vertex, this.sectionIndex);
        }

        this.count += vertices.length;
    }

    private void grow(int bytesNeeded) {
        // Grow by a factor of 2, or by however many bytes more we need, whichever is larger.
        int newCapacity = Math.max(this.capacity * 2, this.capacity + bytesNeeded);
        // Ensure we allocate at least initialCapacity bytes
        newCapacity = Math.max(newCapacity, this.initialCapacity);

        this.buffer = MemoryUtil.memRealloc(this.buffer, newCapacity);
        this.capacity = newCapacity;
    }

    public void start(int sectionIndex) {
        this.count = 0;
        this.sectionIndex = sectionIndex;
        if(this.analyzer != null) {
            this.analyzer.clear();
        }
    }

    @Nullable
    public TranslucentQuadAnalyzer.SortState getSortState() {
        return this.analyzer != null ? this.analyzer.getSortState() : null;
    }

    public void resetSortState() {
        if (this.analyzer != null) {
            this.analyzer.clear();
        }
    }

    public void destroy() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }

        this.buffer = null;
        this.capacity = 0;

        this.resetSortState();
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memSlice(this.buffer, 0, this.stride * this.count);
    }

    public int count() {
        return this.count;
    }
}
