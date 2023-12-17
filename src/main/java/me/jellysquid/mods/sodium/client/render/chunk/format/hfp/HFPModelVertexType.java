package me.jellysquid.mods.sodium.client.render.chunk.format.hfp;

import com.gtnewhorizons.angelica.compat.toremove.VertexConsumer;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.type.BlittableVertexType;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import net.coderbot.iris.sodium.vertex_format.IrisGlVertexAttributeFormat;

/**
 * Uses half-precision floating point numbers to represent position coordinates and normalized unsigned shorts for
 * texture coordinates. All texel positions in the block diffuse texture atlas can be exactly mapped (including
 * their centering offset), as the
 */
public class HFPModelVertexType implements ChunkVertexType {
    // TODO Iris?
    public static final int STRIDE = 44; // 20
    public static final GlVertexFormat<ChunkMeshAttribute> VERTEX_FORMAT =
			GlVertexFormat.builder(ChunkMeshAttribute.class, STRIDE)
	        .addElement(ChunkMeshAttribute.POSITION, 0, GlVertexAttributeFormat.UNSIGNED_SHORT, 3, false)
	        .addElement(ChunkMeshAttribute.COLOR, 8, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true)
	        .addElement(ChunkMeshAttribute.TEXTURE, 12, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false)
	        .addElement(ChunkMeshAttribute.LIGHT, 16, GlVertexAttributeFormat.UNSIGNED_SHORT, 2, true)
            .addElement(ChunkMeshAttribute.MID_TEX_COORD, 20, GlVertexAttributeFormat.FLOAT, 2, false)
            .addElement(ChunkMeshAttribute.TANGENT, 28, IrisGlVertexAttributeFormat.BYTE, 4, true)
            .addElement(ChunkMeshAttribute.NORMAL, 32, IrisGlVertexAttributeFormat.BYTE, 3, true)
            .addElement(ChunkMeshAttribute.BLOCK_ID, 36, IrisGlVertexAttributeFormat.SHORT, 2, false)
            .addElement(ChunkMeshAttribute.MID_BLOCK, 40, IrisGlVertexAttributeFormat.BYTE, 3, false)
	        .build();


    public static final float MODEL_SCALE = (32.0f / 65536.0f);
    public static final float TEXTURE_SCALE = (1.0f / 32768.0f);

    @Override
    public ModelVertexSink createFallbackWriter(VertexConsumer consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return direct ? new HFPModelVertexBufferWriterUnsafe(buffer) : new HFPModelVertexBufferWriterNio(buffer);
    }

    @Override
    public BlittableVertexType<ModelVertexSink> asBlittable() {
        return this;
    }

    @Override
    public GlVertexFormat<ChunkMeshAttribute> getCustomVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public float getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
    }
}
