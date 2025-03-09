package org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

/**
 * This vertex format is less performant and uses more VRAM than {@link CompactChunkVertex}, but should be completely
 * compatible with mods & resource packs that need high precision for models.
 */
public class VanillaLikeChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 28;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("a_PosId", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("a_Color", 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement("a_TexCoord", 16, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("a_LightCoord", 24, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
            .build();

    @Override
    public float getPositionScale() {
        return 1f;
    }

    @Override
    public float getPositionOffset() {
        return 0;
    }

    @Override
    public float getTextureScale() {
        return 1f;
    }

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            MemoryUtil.memPutFloat(ptr + 0, vertex.x);
            MemoryUtil.memPutFloat(ptr + 4, vertex.y);
            MemoryUtil.memPutFloat(ptr + 8, vertex.z);
            MemoryUtil.memPutInt(ptr + 12, vertex.color);
            MemoryUtil.memPutFloat(ptr + 16, encodeTexture(vertex.u));
            MemoryUtil.memPutFloat(ptr + 20, encodeTexture(vertex.v));
            MemoryUtil.memPutInt(ptr + 24, (encodeDrawParameters(material, sectionIndex) << 0) | (encodeLight(vertex.light) << 16));

            return ptr + STRIDE;
        };
    }

    private static int encodeDrawParameters(Material material, int sectionIndex) {
        return (((sectionIndex & 0xFF) << 8) | ((material.bits() & 0xFF) << 0));
    }

    private static int encodeLight(int light) {
        int block = light & 0xFF;
        int sky = (light >> 16) & 0xFF;
        return ((block << 0) | (sky << 8));
    }

    private static float encodeTexture(float value) {
        return Math.min(0.99999997F, value);
    }
}
