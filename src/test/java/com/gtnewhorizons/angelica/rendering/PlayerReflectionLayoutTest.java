package com.gtnewhorizons.angelica.rendering;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.TexturedQuad;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerReflectionLayoutTest {

    static final int FLOATS = PlayerReflectionCapture.FLOATS_PER_VERTEX;
    static final int PART_VERTS = PlayerReflectionCapture.VERTICES_PER_PART;
    static final float MODEL_SCALE = 0.0625f;

    private static Field quadListField;

    static TexturedQuad[] quadsOf(ModelRenderer part) {
        if (part == null || part.cubeList == null || part.cubeList.isEmpty()) return null;
        try {
            if (quadListField == null) {
                quadListField = ModelBox.class.getDeclaredField("quadList");
                quadListField.setAccessible(true);
            }
            return (TexturedQuad[]) quadListField.get(part.cubeList.getFirst());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ModelBox.quadList is no longer reachable", e);
        }
    }

    static float[] emit(ModelBiped model, float scale) {
        final float[] out = new float[PlayerReflectionCapture.VERTEX_COUNT * FLOATS];
        final ModelRenderer[] parts = PlayerReflectionCapture.partOrder(model, new ModelRenderer[PlayerReflectionCapture.PART_COUNT]);
        int o = 0;
        for (ModelRenderer part : parts) {
            o = PlayerReflectionCapture.emitBox(part, quadsOf(part), out, o, scale);
        }
        assertEquals(out.length, o, "emit did not fill exactly 288 vertices");
        return out;
    }

    private static float min(float[] v, int block, int component) {
        float m = Float.POSITIVE_INFINITY;
        for (int i = 0; i < PART_VERTS; i++) m = Math.min(m, v[(block * PART_VERTS + i) * FLOATS + component]);
        return m;
    }

    private static float max(float[] v, int block, int component) {
        float m = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < PART_VERTS; i++) m = Math.max(m, v[(block * PART_VERTS + i) * FLOATS + component]);
        return m;
    }

    private static void assertTexelRange(float[] v, int block, int u0, int u1, int v0, int v1, String part) {
        assertEquals(u0, Math.round(min(v, block, 3) * 64f), part + ": u start");
        assertEquals(u1, Math.round(max(v, block, 3) * 64f), part + ": u end");
        assertEquals(v0, Math.round(min(v, block, 4) * 64f), part + ": v start");
        assertEquals(v1, Math.round(max(v, block, 4) * 64f), part + ": v end");
    }

    @Test
    void partOrderMatchesTheShaderpackVertexIdPartitioning() {
        final ModelBiped model = new ModelBiped(0.0f);
        final ModelRenderer[] parts = PlayerReflectionCapture.partOrder(model, new ModelRenderer[PlayerReflectionCapture.PART_COUNT]);

        assertSame(model.bipedHead, parts[0], "block 0 must be the head");
        assertSame(model.bipedHeadwear, parts[1], "block 1 must stand in for the hat overlay");
        assertSame(model.bipedRightArm, parts[2], "block 2 must be the right arm");
        assertSame(model.bipedRightArm, parts[3], "block 3 must stand in for the right sleeve");
        assertSame(model.bipedLeftLeg, parts[4], "block 4 must be the left leg");
        assertSame(model.bipedLeftLeg, parts[5], "block 5 must stand in for the left trouser leg");
        assertSame(model.bipedLeftArm, parts[6], "block 6 must be the left arm");
        assertSame(model.bipedLeftArm, parts[7], "block 7 must stand in for the left sleeve");
        assertSame(model.bipedRightLeg, parts[8], "block 8 must be the right leg");
        assertSame(model.bipedRightLeg, parts[9], "block 9 must stand in for the right trouser leg");
        assertSame(model.bipedBody, parts[10], "block 10 must be the body");
        assertSame(model.bipedBody, parts[11], "block 11 must stand in for the jacket");
    }

    @Test
    void skinTexelRangesLandWhereTheRayTracerSamples() {
        final float[] v = emit(new ModelBiped(0.0f), MODEL_SCALE);

        assertTexelRange(v, 0, 0, 32, 0, 16, "head");
        assertTexelRange(v, 1, 32, 64, 0, 16, "hat");
        assertTexelRange(v, 2, 40, 56, 16, 32, "right arm");
        assertTexelRange(v, 4, 0, 16, 16, 32, "left leg");
        assertTexelRange(v, 6, 40, 56, 16, 32, "left arm");
        assertTexelRange(v, 8, 0, 16, 16, 32, "right leg");
        assertTexelRange(v, 10, 16, 40, 16, 32, "body");
    }

    @Test
    void everyQuadIsAParallelogramInV0V1V2Order() {
        final float[] v = emit(new ModelBiped(0.0f), MODEL_SCALE);

        for (int q = 0; q < PlayerReflectionCapture.VERTEX_COUNT / 4; q++) {
            final int b = q * 4 * FLOATS;
            final float[] v0 = { v[b], v[b + 1], v[b + 2] };
            final float[] v1 = { v[b + FLOATS], v[b + FLOATS + 1], v[b + FLOATS + 2] };
            final float[] v2 = { v[b + 2 * FLOATS], v[b + 2 * FLOATS + 1], v[b + 2 * FLOATS + 2] };
            final float[] v3 = { v[b + 3 * FLOATS], v[b + 3 * FLOATS + 1], v[b + 3 * FLOATS + 2] };

            for (int c = 0; c < 3; c++) {
                assertEquals(v0[c] + v2[c] - v1[c], v3[c], 1e-4f, "quad " + q + " component " + c + ": the ray tracer reconstructs the 4th corner as v0 + v2 - v1");
            }

            float dot = 0.0f;
            for (int c = 0; c < 3; c++) dot += (v1[c] - v0[c]) * (v2[c] - v1[c]);
            assertEquals(0.0f, dot, 1e-6f, "quad " + q + ": edges v0->v1 and v1->v2 must be perpendicular");
        }
    }

    @Test
    void boxExtentsMatchTheVanillaModel() {
        final float[] v = emit(new ModelBiped(0.0f), MODEL_SCALE);

        assertEquals(-0.25f, min(v, 0, 0), 1e-5f, "head min x");
        assertEquals(0.25f, max(v, 0, 0), 1e-5f, "head max x");
        assertEquals(-0.5f, min(v, 0, 1), 1e-5f, "head min y");
        assertEquals(0.0f, max(v, 0, 1), 1e-5f, "head max y");

        assertEquals(-0.25f, min(v, 10, 0), 1e-5f, "body min x");
        assertEquals(0.25f, max(v, 10, 0), 1e-5f, "body max x");
        assertEquals(0.0f, min(v, 10, 1), 1e-5f, "body min y");
        assertEquals(0.75f, max(v, 10, 1), 1e-5f, "body max y");
        assertEquals(-0.125f, min(v, 10, 2), 1e-5f, "body min z");
        assertEquals(0.125f, max(v, 10, 2), 1e-5f, "body max z");
    }

    @Test
    void hatIsLargerThanTheHeadSoAnOpaqueOverlayWins() {
        final float[] v = emit(new ModelBiped(0.0f), MODEL_SCALE);

        assertTrue(max(v, 1, 0) > max(v, 0, 0), "the hat box must enclose the head box");
        assertEquals(0.5f + 2 * 0.5f * MODEL_SCALE, max(v, 1, 0) - min(v, 1, 0), 1e-5f, "hat width");
    }

    @Test
    void hiddenPartsEmitDegenerateQuads() {
        final ModelBiped model = new ModelBiped(0.0f);
        model.bipedHeadwear.showModel = false;
        model.bipedRightArm.isHidden = true;

        final float[] v = emit(model, MODEL_SCALE);
        final float[] zeros = new float[PART_VERTS * FLOATS];

        assertArrayEquals(zeros, java.util.Arrays.copyOfRange(v, PART_VERTS * FLOATS, 2 * PART_VERTS * FLOATS), "a hidden hat must not be ray-traced");
        assertArrayEquals(zeros, java.util.Arrays.copyOfRange(v, 2 * PART_VERTS * FLOATS, 3 * PART_VERTS * FLOATS), "a hidden right arm must not be ray-traced");
    }

    @Test
    void aPartWhoseLayoutOverflowsTheAtlasIsDropped() {
        final ModelBiped model = new ModelBiped(0.0f);
        model.bipedHead.setTextureSize(128, 128);

        final float[] v = emit(model, MODEL_SCALE);

        assertArrayEquals(new float[PART_VERTS * FLOATS], java.util.Arrays.copyOfRange(v, 0, PART_VERTS * FLOATS), "a >64 layout cannot be expressed in the 64x64 atlas, so the head block must be degenerate rather than emit UVs > 1");
        assertTexelRange(v, 10, 16, 40, 16, 32, "body is unaffected by the head's layout");
    }

    @Test
    void headUvsAreUnaffectedByHeadRotation() {
        final ModelBiped model = new ModelBiped(0.0f);
        model.bipedHead.rotateAngleY = 0.9f;
        model.bipedHead.rotateAngleX = -0.4f;

        final float[] v = emit(model, MODEL_SCALE);

        assertTexelRange(v, 0, 0, 32, 0, 16, "rotated head");
        assertTrue(max(v, 0, 0) - min(v, 0, 0) > 0.5f, "a yawed head must present a wider silhouette than 8 units");
    }
}
