package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.TexturedQuad;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerReflectionTestSupport {

    static final int FLOATS = PlayerReflectionCapture.FLOATS_PER_VERTEX;
    static final float MODEL_SCALE = 0.0625f;

    private PlayerReflectionTestSupport() {}

    static TexturedQuad[] quadsOf(ModelRenderer part) {
        if (part == null || part.cubeList == null || part.cubeList.isEmpty()) return null;
        return Reflect.get(part.cubeList.getFirst(), "quadList");
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
}
