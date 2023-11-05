package com.gtnewhorizons.angelica.compat.mojang;

import net.coderbot.batchedentityrendering.impl.BufferBuilderExt;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

// This is the Tessellator - TODO: Merge/Overwrite
public class BufferBuilder implements VertexConsumer, BufferBuilderExt {
    private ByteBuffer buffer;
    private List<DrawState> vertexCounts;
    private int lastRenderedCountIndex;
    private int totalRenderedBytes;
    private int nextElementByte;
    private int totalUploadedBytes;
    private int vertices;
    private VertexFormat format;

    private boolean dupeNextVertex;
    int i;

    public BufferBuilder() {
        this(0);
    }

    public BufferBuilder(int i) {
        this.buffer = BufferUtils.createByteBuffer(i * 4);
    }

    public void begin(int glQuads, VertexFormat position) {}

    public void end() {}

    @Override
    public VertexConsumer vertex(double d, double e, double f) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer texture(float u, float v) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer overlay(int u, int v) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer light(int u, int v) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return null;
    }

    @Override
    public void next() {

    }

    public void sortQuads(int i, int i1, int i2) {}

    public Pair<DrawState, ByteBuffer> popNextBuffer() {
        return null;
    }

    public void clear() {}

    @Override
    public void setupBufferSlice(ByteBuffer buffer, DrawState drawState) {
        // add the buffer slice
        this.buffer = buffer;

        // add our singular parameter
        this.vertexCounts.clear();
        this.vertexCounts.add(drawState);

        // should be zero, just making sure
        this.lastRenderedCountIndex = 0;

        // configure the build start (to avoid a warning message) and element offset (probably not important)
        this.totalRenderedBytes = drawState.getVertexCount() * drawState.getFormat().getVertexSize();
        this.nextElementByte = this.totalRenderedBytes;

        // should be zero, just making sure
        this.totalUploadedBytes = 0;

        // target.vertexCount is never nonzero in this process.
        // target.currentElement is never non-null in this process.
        // target.currentElementId is never nonzero.
        // target.drawMode is irrelevant.
        // target.format is irrelevant.
        // The final 3 booleans are also irrelevant.
    }

    @Override
    public void teardownBufferSlice() {
        // the parameters got popped by the render call, we don't need to worry about them
        // make sure to un-set the buffer to prevent anything bad from happening with it.
        this.buffer = null;

        // target.parameters gets reset.
        // target.lastParameterIndex gets reset.
        // target.buildStart gets reset.
        // target.elementOffset gets reset.
        // target.nextDrawStart gets reset.

        // target.vertexCount is never nonzero in this process.
        // target.currentElement is never non-null in this process.
        // target.currentElementId is never nonzero.
        // target.drawMode is irrelevant.
        // target.format is irrelevant.
        // The final 3 booleans are also irrelevant.
    }

    @Override
    public void splitStrip() {
        if (vertices == 0) {
            // no strip to split, not building.
            return;
        }

        duplicateLastVertex();
        dupeNextVertex = true;
    }

    private void duplicateLastVertex() {
        int i = this.format.getVertexSize();
        this.buffer.position(this.nextElementByte);
        ByteBuffer byteBuffer = this.buffer.duplicate();
        byteBuffer.position(this.nextElementByte - i).limit(this.nextElementByte);
        this.buffer.put(byteBuffer);
        this.nextElementByte += i;
        ++this.vertices;
        this.ensureVertexCapacity();
    }
    protected void ensureVertexCapacity() {

    }
}
