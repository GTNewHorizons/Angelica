package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import net.coderbot.batchedentityrendering.impl.BatchVertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

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
        final Matrix4f mv = new Matrix4f().translation(10f, -2f, 3f).rotateX((float) Math.toRadians(90));
        final ByteBuffer dest = ByteBuffer.allocateDirect(DEST.getVertexSize());
        final long addr = memAddress0(dest);

        VertexTransform.writeInstance(addr, DEST, template, mv, new Vector3f(), 0xFF8040C0, 0x00F000F0, null);

        assertEquals(10f, memGetFloat(addr + OFF_POS), 1e-6f);
        assertEquals(-2f, memGetFloat(addr + OFF_POS + 4), 1e-6f);
        assertEquals(4f, memGetFloat(addr + OFF_POS + 8), 1e-6f);
        assertEquals(0xFF8040C0, memGetInt(addr + OFF_COLOR));
        assertEquals(0.25f, memGetFloat(addr + OFF_TEX), 0f);
        assertEquals(0.75f, memGetFloat(addr + OFF_TEX + 4), 0f);
        assertEquals(240f, memGetFloat(addr + OFF_LIGHT), 0f);      // 0x00F0
        assertEquals(240f, memGetFloat(addr + OFF_LIGHT + 4), 0f);  // 0x00F0
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

    static Stream<Arguments> decodeCases() {
        return Stream.of(
            Arguments.of("explicit normal", GL11.GL_POINTS, new float[][] {{5f, 6f, 7f, 0.1f, 0.2f}}, NormI8.pack(1f, 0f, 0f), 1f, 0f, 0f),
            Arguments.of("quad seeds its face normal", GL11.GL_QUADS, new float[][] {
                {0f, 0f, 0f, 0f, 0f}, {1f, 0f, 0f, 1f, 0f}, {1f, 1f, 0f, 1f, 1f}, {0f, 1f, 0f, 0f, 1f}}, 0, 0f, 0f, 1f));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decodeCases")
    void decodeReadsPositionUvNormalByUsage(String name, int drawMode, float[][] vertices, int packedNormal, float nx, float ny, float nz) {
        final VertexFormat src = DefaultVertexFormat.POSITION_TEXTURE_NORMAL;
        final ByteBuffer buf = ByteBuffer.allocateDirect(src.getVertexSize() * vertices.length);
        final long addr = memAddress0(buf);
        for (int i = 0; i < vertices.length; i++) {
            final long v = addr + (long) i * src.getVertexSize();
            for (int f = 0; f < 5; f++) memPutFloat(v + 4L * f, vertices[i][f]);
            memPutInt(v + 20, packedNormal);
        }

        final int[] out = VertexTransform.decode(addr, src, vertices.length, drawMode);

        for (int i = 0; i < vertices.length; i++) {
            final int base = i * VERTEX_SIZE;
            assertEquals(vertices[i][0], Float.intBitsToFloat(out[base | X_INDEX]), 0f);
            assertEquals(vertices[i][1], Float.intBitsToFloat(out[base | Y_INDEX]), 0f);
            assertEquals(vertices[i][2], Float.intBitsToFloat(out[base | Z_INDEX]), 0f);
            assertEquals(vertices[i][3], Float.intBitsToFloat(out[base | TEX_X_INDEX]), 0f);
            assertEquals(vertices[i][4], Float.intBitsToFloat(out[base | TEX_Y_INDEX]), 0f);
            assertEquals(nx, NormI8.unpackX(out[base | NORMAL_INDEX]), NORM_EPS);
            assertEquals(ny, NormI8.unpackY(out[base | NORMAL_INDEX]), NORM_EPS);
            assertEquals(nz, NormI8.unpackZ(out[base | NORMAL_INDEX]), NORM_EPS);
        }
    }
}
