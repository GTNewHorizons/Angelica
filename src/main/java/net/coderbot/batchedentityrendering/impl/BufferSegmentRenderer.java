package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.toremove.BufferBuilder;

public class BufferSegmentRenderer {
    private final BufferBuilder fakeBufferBuilder;
    private final BufferBuilderExt fakeBufferBuilderExt;

    public BufferSegmentRenderer() {
        this.fakeBufferBuilder = new BufferBuilder(0);
        this.fakeBufferBuilderExt = (BufferBuilderExt) this.fakeBufferBuilder;
    }

    /**
     * Sets up the render type, draws the buffer, and then tears down the render type.
     */
    public void draw(BufferSegment segment) {
        segment.getRenderType().startDrawing();
        drawInner(segment);
        segment.getRenderType().endDrawing();
    }

    /**
     * Like draw(), but it doesn't setup / tear down the render type.
     */
    public void drawInner(BufferSegment segment) {
        fakeBufferBuilderExt.setupBufferSlice(segment.getSlice(), segment.getDrawState());
        // TODO: BufferUploader
//        BufferUploader.end(fakeBufferBuilder);
        fakeBufferBuilderExt.teardownBufferSlice();
    }
}
