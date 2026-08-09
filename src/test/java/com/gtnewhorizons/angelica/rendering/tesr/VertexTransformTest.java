package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import net.coderbot.batchedentityrendering.impl.BatchVertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.LIGHT_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.NORMAL_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Z_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VertexTransformTest {

    private static final float NORM_EPS = 1.5f / 127f;
    private static final VertexFormat DEST = BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL;
    private static final int OFF_POS = 0, OFF_COLOR = 12, OFF_TEX = 16, OFF_LIGHT = 24, OFF_NORMAL = 32;

    private static int[] oneVertexUp() {
        final int[] data = new int[VERTEX_SIZE];
        data[COLOR_INDEX] = 0xFFFFFFFF;
        data[X_INDEX] = Float.floatToRawIntBits(0f);
        data[Y_INDEX] = Float.floatToRawIntBits(1f);
        data[Z_INDEX] = Float.floatToRawIntBits(0f);
        data[TEX_X_INDEX] = Float.floatToRawIntBits(0.25f);
        data[TEX_Y_INDEX] = Float.floatToRawIntBits(0.75f);
        data[NORMAL_INDEX] = NormI8.pack(0f, 1f, 0f);
        return data;
    }

    private static TemplateBuffer template(int[] data) {
        return new TemplateBuffer(data, 1, 7);
    }

    @Test
    void writeInstanceTranslatesPositionSetsColorLightKeepsUv() {
        final TemplateBuffer template = template(oneVertexUp());
        final Matrix4f mv = new Matrix4f().translation(10f, -2f, 3f);
        final ByteBuffer dest = ByteBuffer.allocateDirect(DEST.getVertexSize());
        final long addr = memAddress0(dest);

        VertexTransform.writeInstance(addr, DEST, template, mv, new Vector3f(), 0xFF8040C0, 0x00F000F0, null);

        assertEquals(10f, memGetFloat(addr + OFF_POS), 1e-6f);
        assertEquals(-1f, memGetFloat(addr + OFF_POS + 4), 1e-6f);
        assertEquals(3f, memGetFloat(addr + OFF_POS + 8), 1e-6f);
        assertEquals(0xFF8040C0, memGetInt(addr + OFF_COLOR));
        assertEquals(0.25f, memGetFloat(addr + OFF_TEX), 0f);
        assertEquals(0.75f, memGetFloat(addr + OFF_TEX + 4), 0f);
        assertEquals(240f, memGetFloat(addr + OFF_LIGHT), 0f);      // 0x00F0
        assertEquals(240f, memGetFloat(addr + OFF_LIGHT + 4), 0f);  // 0x00F0
        final int n = memGetInt(addr + OFF_NORMAL);
        assertEquals(0f, NormI8.unpackX(n), NORM_EPS);
        assertEquals(1f, NormI8.unpackY(n), NORM_EPS);
        assertEquals(0f, NormI8.unpackZ(n), NORM_EPS);
    }

    @Test
    void writeInstanceRotatesNormal() {
        final TemplateBuffer template = template(oneVertexUp());
        final Matrix4f mv = new Matrix4f().rotationX((float) Math.toRadians(90));
        final ByteBuffer dest = ByteBuffer.allocateDirect(DEST.getVertexSize());
        final long addr = memAddress0(dest);

        VertexTransform.writeInstance(addr, DEST, template, mv, new Vector3f(), 0, 0, null);

        final int n = memGetInt(addr + OFF_NORMAL);
        assertEquals(0f, NormI8.unpackX(n), NORM_EPS);
        assertEquals(0f, NormI8.unpackY(n), NORM_EPS);
        assertEquals(1f, NormI8.unpackZ(n), NORM_EPS);
    }

    @Test
    void writeInstanceModulatesBakedColorAndPreservesTemplate() {
        final int[] data = oneVertexUp();
        data[COLOR_INDEX] = 0xFF0080FF; // ABGR: a=255 b=0 g=128 r=255
        final TemplateBuffer template = template(data);
        final ByteBuffer dest = ByteBuffer.allocateDirect(DEST.getVertexSize());
        final long addr = memAddress0(dest);

        VertexTransform.writeInstance(addr, DEST, template, new Matrix4f(), new Vector3f(), 0x80FFFFFF, 0, null);
        assertEquals(0x800080FF, memGetInt(addr + OFF_COLOR));

        VertexTransform.writeInstance(addr, DEST, template, new Matrix4f(), new Vector3f(), -1, 0, null);
        assertEquals(0xFF0080FF, memGetInt(addr + OFF_COLOR));
    }

    @Test
    void mulColorIsExactForWhiteAndRounds() {
        assertEquals(0x12345678, VertexTransform.mulColor(0x12345678, 0xFFFFFFFF));
        assertEquals(0x12345678, VertexTransform.mulColor(0xFFFFFFFF, 0x12345678));
        assertEquals(0x80, VertexTransform.mulColor(0xFF, 0x80));
        assertEquals(0x00, VertexTransform.mulColor(0x7F, 0x00));
    }

    @Test
    void writeInstanceAppliesTextureMatrixToUvThenRestores() {
        final TemplateBuffer template = template(oneVertexUp());
        final ByteBuffer dest = ByteBuffer.allocateDirect(DEST.getVertexSize());
        final long addr = memAddress0(dest);
        final Matrix4f texMatrix = new Matrix4f().translation(0f, 0.1f, 0f).scale(1f, 0.5f, 1f);

        VertexTransform.writeInstance(addr, DEST, template, new Matrix4f(), new Vector3f(), -1, 0, texMatrix);
        assertEquals(0.25f, memGetFloat(addr + OFF_TEX), 1e-6f);
        assertEquals(0.75f * 0.5f + 0.1f, memGetFloat(addr + OFF_TEX + 4), 1e-6f);

        VertexTransform.writeInstance(addr, DEST, template, new Matrix4f(), new Vector3f(), -1, 0, null);
        assertEquals(0.25f, memGetFloat(addr + OFF_TEX), 0f);
        assertEquals(0.75f, memGetFloat(addr + OFF_TEX + 4), 0f);
    }

    @Test
    void decodeReadsPositionUvNormalByUsage() {
        final VertexFormat src = DefaultVertexFormat.POSITION_TEXTURE_NORMAL;
        final ByteBuffer buf = ByteBuffer.allocateDirect(src.getVertexSize());
        final long addr = memAddress0(buf);
        memPutFloat(addr, 5f);
        memPutFloat(addr + 4, 6f);
        memPutFloat(addr + 8, 7f);
        memPutFloat(addr + 12, 0.1f);
        memPutFloat(addr + 16, 0.2f);
        memPutInt(addr + 20, NormI8.pack(1f, 0f, 0f));

        final int[] out = VertexTransform.decode(addr, src, 1);

        assertEquals(5f, Float.intBitsToFloat(out[X_INDEX]), 0f);
        assertEquals(6f, Float.intBitsToFloat(out[Y_INDEX]), 0f);
        assertEquals(7f, Float.intBitsToFloat(out[Z_INDEX]), 0f);
        assertEquals(0.1f, Float.intBitsToFloat(out[TEX_X_INDEX]), 0f);
        assertEquals(0.2f, Float.intBitsToFloat(out[TEX_Y_INDEX]), 0f);
        assertEquals(1f, NormI8.unpackX(out[NORMAL_INDEX]), NORM_EPS);
    }
}
