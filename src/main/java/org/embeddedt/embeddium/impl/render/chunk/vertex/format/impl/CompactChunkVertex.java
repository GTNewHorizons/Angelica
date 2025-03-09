package org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

public class CompactChunkVertex implements ChunkVertexType {
    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(20)
            .addElement("a_PosId", 0, GlVertexAttributeFormat.UNSIGNED_SHORT, 4, false, true)
            .addElement("a_Color", 8, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement("a_TexCoord", 12, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false, false)
            .addElement("a_LightCoord", 16, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false, true)
            .build();

    public static final int STRIDE = 20;

    private static final int POSITION_MAX_VALUE = 65536;
    private static final int TEXTURE_MAX_VALUE = 32768;

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;
    private static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;
    private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;

    private static final float TEXTURE_SCALE = (1.0f / TEXTURE_MAX_VALUE);

    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
    }

    @Override
    public float getPositionScale() {
        return MODEL_SCALE;
    }

    @Override
    public float getPositionOffset() {
        return -MODEL_ORIGIN;
    }

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            MemoryUtil.memPutShort(ptr + 0, encodePosition(vertex.x));
            MemoryUtil.memPutShort(ptr + 2, encodePosition(vertex.y));
            MemoryUtil.memPutShort(ptr + 4, encodePosition(vertex.z));

            MemoryUtil.memPutByte(ptr + 6, (byte) (material.bits() & 0xFF));
            MemoryUtil.memPutByte(ptr + 7, (byte) (sectionIndex & 0xFF));

            MemoryUtil.memPutInt(ptr + 8, vertex.color);

            MemoryUtil.memPutShort(ptr + 12, encodeTexture(vertex.u));
            MemoryUtil.memPutShort(ptr + 14, encodeTexture(vertex.v));

            MemoryUtil.memPutInt(ptr + 16, vertex.light);

            return ptr + STRIDE;
        };
    }

    @Override
    public List<String> getDefines() {
        return List.of("USE_VERTEX_COMPRESSION");
    }

    private static short encodePosition(float value) {
        return (short) ((MODEL_ORIGIN + value) * MODEL_SCALE_INV);
    }

    public static float decodePosition(short value) {
        return (((float)Short.toUnsignedInt(value)) / MODEL_SCALE_INV) - MODEL_ORIGIN;
    }

    private static short encodeTexture(float value) {
        return (short) (Math.round(value * TEXTURE_MAX_VALUE) & 0xFFFF);
    }
}
