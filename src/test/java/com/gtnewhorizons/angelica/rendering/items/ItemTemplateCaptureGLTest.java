package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.EntityMaterials;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.NORMAL_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Y_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.Z_INDEX;
import static com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility.SAFE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@GLCoreTest
class ItemTemplateCaptureGLTest {

    private static final float NORM_EPS = 1.5f / 127f;
    private static final float EPS = 1.0e-5f;

    @AfterEach
    void cleanup() {
        DroppedItemInstancer.clear();
    }

    private static TemplateBuffer capture(float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        final AngelicaTesrMeshCache.GtnhMeshBackend backend = new AngelicaTesrMeshCache.GtnhMeshBackend();
        final Tessellator direct = backend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
        RuntimeException failure = null;
        try {
            ItemRenderer.renderItemIn2D(direct, maxU, minV, minU, maxV, width, height, thickness);
        } catch (RuntimeException e) {
            failure = e;
        }
        final TemplateBuffer template = backend.endCaptureToTemplate();
        if (failure != null) throw failure;
        return template;
    }

    private static float[] vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        return new float[] { x, y, z, u, v, nx, ny, nz };
    }

    private static float[][] expected(float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        final float[][] out = new float[4 * (2 + 2 * width + 2 * height)][];
        int i = 0;

        out[i++] = vertex(0f, 0f, 0f, maxU, maxV, 0f, 0f, 1f);
        out[i++] = vertex(1f, 0f, 0f, minU, maxV, 0f, 0f, 1f);
        out[i++] = vertex(1f, 1f, 0f, minU, minV, 0f, 0f, 1f);
        out[i++] = vertex(0f, 1f, 0f, maxU, minV, 0f, 0f, 1f);

        out[i++] = vertex(0f, 1f, -thickness, maxU, minV, 0f, 0f, -1f);
        out[i++] = vertex(1f, 1f, -thickness, minU, minV, 0f, 0f, -1f);
        out[i++] = vertex(1f, 0f, -thickness, minU, maxV, 0f, 0f, -1f);
        out[i++] = vertex(0f, 0f, -thickness, maxU, maxV, 0f, 0f, -1f);

        final float f5 = 0.5f * (maxU - minU) / width;
        final float f6 = 0.5f * (maxV - minV) / height;

        for (int k = 0; k < width; k++) {
            final float f7 = (float) k / width;
            final float f8 = maxU + (minU - maxU) * f7 - f5;
            out[i++] = vertex(f7, 0f, -thickness, f8, maxV, -1f, 0f, 0f);
            out[i++] = vertex(f7, 0f, 0f, f8, maxV, -1f, 0f, 0f);
            out[i++] = vertex(f7, 1f, 0f, f8, minV, -1f, 0f, 0f);
            out[i++] = vertex(f7, 1f, -thickness, f8, minV, -1f, 0f, 0f);
        }

        for (int k = 0; k < width; k++) {
            final float f7 = (float) k / width;
            final float f8 = maxU + (minU - maxU) * f7 - f5;
            final float f9 = f7 + 1f / width;
            out[i++] = vertex(f9, 1f, -thickness, f8, minV, 1f, 0f, 0f);
            out[i++] = vertex(f9, 1f, 0f, f8, minV, 1f, 0f, 0f);
            out[i++] = vertex(f9, 0f, 0f, f8, maxV, 1f, 0f, 0f);
            out[i++] = vertex(f9, 0f, -thickness, f8, maxV, 1f, 0f, 0f);
        }

        for (int k = 0; k < height; k++) {
            final float f7 = (float) k / height;
            final float f8 = maxV + (minV - maxV) * f7 - f6;
            final float f9 = f7 + 1f / height;
            out[i++] = vertex(0f, f9, 0f, maxU, f8, 0f, 1f, 0f);
            out[i++] = vertex(1f, f9, 0f, minU, f8, 0f, 1f, 0f);
            out[i++] = vertex(1f, f9, -thickness, minU, f8, 0f, 1f, 0f);
            out[i++] = vertex(0f, f9, -thickness, maxU, f8, 0f, 1f, 0f);
        }

        for (int k = 0; k < height; k++) {
            final float f7 = (float) k / height;
            final float f8 = maxV + (minV - maxV) * f7 - f6;
            out[i++] = vertex(1f, f7, 0f, minU, f8, 0f, -1f, 0f);
            out[i++] = vertex(0f, f7, 0f, maxU, f8, 0f, -1f, 0f);
            out[i++] = vertex(0f, f7, -thickness, maxU, f8, 0f, -1f, 0f);
            out[i++] = vertex(1f, f7, -thickness, minU, f8, 0f, -1f, 0f);
        }

        assertEquals(out.length, i);
        return out;
    }

    private static void assertCaptureMatches(float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        final TemplateBuffer template = capture(maxU, minV, minU, maxV, width, height, thickness);
        assertNotNull(template, "renderItemIn2D must emit geometry");
        final float[][] expected = expected(maxU, minV, minU, maxV, width, height, thickness);
        assertEquals(expected.length, template.vertexCount);

        for (int v = 0; v < expected.length; v++) {
            final int base = v * VERTEX_SIZE;
            final float[] want = expected[v];
            assertEquals(want[0], Float.intBitsToFloat(template.data[base + X_INDEX]), EPS, "vertex " + v + " x");
            assertEquals(want[1], Float.intBitsToFloat(template.data[base + Y_INDEX]), EPS, "vertex " + v + " y");
            assertEquals(want[2], Float.intBitsToFloat(template.data[base + Z_INDEX]), EPS, "vertex " + v + " z");
            assertEquals(want[3], Float.intBitsToFloat(template.data[base + TEX_X_INDEX]), EPS, "vertex " + v + " u");
            assertEquals(want[4], Float.intBitsToFloat(template.data[base + TEX_Y_INDEX]), EPS, "vertex " + v + " v");
            final int normal = template.data[base + NORMAL_INDEX];
            assertEquals(want[5], NormI8.unpackX(normal), NORM_EPS, "vertex " + v + " nx");
            assertEquals(want[6], NormI8.unpackY(normal), NORM_EPS, "vertex " + v + " ny");
            assertEquals(want[7], NormI8.unpackZ(normal), NORM_EPS, "vertex " + v + " nz");
        }
    }

    @Test
    void capturedIconMatchesVanillaLoopFormula() {
        assertCaptureMatches(1.0f, 0.0f, 0.0f, 1.0f, 16, 16, 0.0625f);
    }

    @Test
    void anEmptyBlockCaptureDrawsVanillaWithoutDenyingTheRenderer() {
        final Block block = new ProbeBlock();
        final int[] calls = { 0 };
        final Operation<Void> original = args -> {
            if (calls[0]++ > 0) GLStateManager.drawCalls++;
            return null;
        };

        BatchEligibility.begin(SAFE, GLStateManager.drawCalls);
        DroppedItemInstancer.batchBlock(EntityMaterials.DROPPED_ITEM_CUTOUT, null, block, 0, 1f, original);
        DroppedItemInstancer.batchBlock(EntityMaterials.DROPPED_ITEM_CUTOUT, null, block, 0, 1f, original);
        assertEquals(SAFE, BatchEligibility.end(SAFE, GLStateManager.drawCalls), "a block with no cached template draws vanilla and reports no part");

        assertEquals(3, calls[0], "one capture, then a vanilla draw on the miss and on the cached null template");
    }

    private static final class ProbeBlock extends Block {

        ProbeBlock() {
            super(Material.rock);
        }
    }
}
