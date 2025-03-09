package org.embeddedt.embeddium.impl.render.chunk.vertex.format;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;

import java.util.List;

public interface ChunkVertexType {
    /**
     * @return The scale to be applied to vertex coordinates
     */
    float getPositionScale();

    /**
     * @return The translation to be applied to vertex coordinates
     */
    float getPositionOffset();

    /**
     * @return The scale to be applied to texture coordinates
     */
    float getTextureScale();

    GlVertexFormat getVertexFormat();

    ChunkVertexEncoder getEncoder();

    default List<String> getDefines() {
        return List.of();
    }
}
