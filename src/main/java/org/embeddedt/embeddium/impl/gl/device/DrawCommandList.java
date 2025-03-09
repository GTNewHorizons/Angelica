package org.embeddedt.embeddium.impl.gl.device;

import org.embeddedt.embeddium.impl.gl.tessellation.GlIndexType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;

public interface DrawCommandList extends AutoCloseable {
    void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlPrimitiveType primitiveType, GlIndexType indexType);

    void endTessellating();

    void flush();

    @Override
    default void close() {
        this.flush();
    }
}
