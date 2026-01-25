package com.gtnewhorizons.angelica.rendering.celeritas.iris;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;

/**
 * VANILLA_LIKE (28 bytes) + Iris extensions (20 bytes) = 48 bytes total.
 *
 * Iris extensions:
 *   mc_midTexCoord  ushort[2]  - quad center UV (average of 4 vertices)
 *   at_tangent      byte[4]    - tangent vector, normalized
 *   iris_Normal     byte[3]+1  - face normal, normalized
 *   mc_Entity       short[2]   - (blockId, renderType)
 *   at_midBlock     byte[4]    - (xyz offset from block center, lightValue)
 */
public class IrisExtendedChunkVertexType implements ChunkVertexType {
    public static final ChunkVertexType BASE_TYPE = ChunkMeshFormats.VANILLA_LIKE;
    public static final int STRIDE = 48;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
        .addAllElements(BASE_TYPE.getVertexFormat())
        .addElement("mc_midTexCoord", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false, false)
        .addElement("at_tangent", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 4, true, false)
        .addElement("iris_Normal", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 3, true, false)
        .addElement("mc_Entity", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.SHORT, 2, false, false)
        .addElement("at_midBlock", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.BYTE, 4, false, false)
        .build();

    private static final int TEXTURE_MAX_VALUE = 32768;
    public static final float MID_TEX_SCALE = 1.0f / TEXTURE_MAX_VALUE;

    /** Pack UV as two unsigned shorts: low=u, high=v. */
    public static int encodeMidTexture(float u, float v) {
        return ((Math.round(u * TEXTURE_MAX_VALUE) & 0xFFFF)) |
               ((Math.round(v * TEXTURE_MAX_VALUE) & 0xFFFF) << 16);
    }

    @Override
    public ChunkVertexEncoder createEncoder() {
        return new IrisExtendedChunkVertexEncoder();
    }

    @Override
    public float getPositionScale() {
        return BASE_TYPE.getPositionScale();
    }

    @Override
    public float getPositionOffset() {
        return BASE_TYPE.getPositionOffset();
    }

    @Override
    public float getTextureScale() {
        return BASE_TYPE.getTextureScale();
    }

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }
}
