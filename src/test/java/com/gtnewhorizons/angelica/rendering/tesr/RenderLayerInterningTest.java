package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderLayerInterningTest {

    private static final ResourceLocation TEX = new ResourceLocation("angelica", "textures/render_layer_interning_test.png");

    @Test
    void noCullMaterialInternsToOneLayerRegardlessOfCullCode() {
        final TesrMaterial noCull = TesrMaterial.builder().noCull().build();
        final RenderLayer back = RenderLayer.tesr(TEX, noCull, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true);
        final RenderLayer front = RenderLayer.tesr(TEX, noCull, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_FRONT, true);
        assertSame(back, front);
        assertEquals(DrawState.DISABLED, back.getState().getCull());
    }

    @Test
    void unlitMaterialRequestedLitYieldsUnlitState() {
        final TesrMaterial unlit = TesrMaterial.builder().unlit().build();
        final RenderLayer layer = RenderLayer.tesr(TEX, unlit, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true);
        assertFalse(layer.getState().isLit());
    }

    @Test
    void litMaterialRequestedUnlitYieldsUnlitStateAndDistinctLayer() {
        final TesrMaterial lit = TesrMaterial.builder().build();
        final RenderLayer unlitRequest = RenderLayer.tesr(TEX, lit, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, false);
        final RenderLayer litRequest = RenderLayer.tesr(TEX, lit, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true);
        assertFalse(unlitRequest.getState().isLit());
        assertTrue(litRequest.getState().isLit());
        assertNotSame(unlitRequest, litRequest);
    }

    @Test
    void noPassIsPartOfStructuredInterningIdentity() {
        final TesrMaterial material = TesrMaterial.builder().build();
        final RenderLayer regular = RenderLayer.tesr(TEX, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true);
        final RenderLayer noPass = RenderLayer.tesrNoPass(TEX, material, DrawState.CULL_BACK, true);

        assertNotSame(regular, noPass);
    }

    @Test
    void cacheResetAllowsFreshCanonicalIdentity() {
        final TesrMaterial material = TesrMaterial.builder().build();
        final RenderLayer before = RenderLayer.tesrNoPass(TEX, material, DrawState.CULL_BACK, true);
        RenderLayer.clearInterningAndHooks();
        final RenderLayer first = RenderLayer.tesrNoPass(TEX, material, DrawState.CULL_BACK, true);
        final RenderLayer second = RenderLayer.tesrNoPass(TEX, material, DrawState.CULL_BACK, true);

        assertNotSame(before, first);
        assertSame(first, second);
        assertTrue(RenderLayer.cacheSize() > 0);
    }
}
