package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class RenderLayerInterningTest {

    private static final ResourceLocation TEX = new ResourceLocation("angelica", "textures/render_layer_interning_test.png");

    private static RenderLayer layer(TesrMaterial material, int cull, boolean lit, boolean lightmap) {
        return RenderLayer.tesr(TEX, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, cull, lit, lightmap);
    }

    private static RenderLayer glint(int slot) {
        return RenderLayer.tesr(null, EntityMaterials.GLINT, PassOverride.NONE, 0f, 0f, slot, DrawState.CULL_BACK, true, true);
    }

    static Stream<?> normalizeCases() {
        final TesrMaterial noCull = TesrMaterial.builder().noCull().build();
        final TesrMaterial unlit = TesrMaterial.builder().unlit().build();
        return Stream.of(
            arguments("noCull",
                (Supplier<RenderLayer>) () -> layer(noCull, DrawState.CULL_BACK, true, true),
                (Supplier<RenderLayer>) () -> layer(noCull, DrawState.CULL_FRONT, true, true),
                (BiConsumer<RenderLayer, RenderLayer>) (a, b) -> assertEquals(DrawState.DISABLED, a.getState().getCull())),
            arguments("unlit",
                (Supplier<RenderLayer>) () -> layer(unlit, DrawState.CULL_BACK, true, true),
                (Supplier<RenderLayer>) () -> layer(unlit, DrawState.CULL_BACK, false, true),
                (BiConsumer<RenderLayer, RenderLayer>) (a, b) -> assertFalse(a.getState().isLit()))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normalizeCases")
    void normalizesToOneLayer(String name, Supplier<RenderLayer> requestA, Supplier<RenderLayer> requestB, BiConsumer<RenderLayer, RenderLayer> stateCheck) {
        final RenderLayer a = requestA.get();
        final RenderLayer b = requestB.get();
        assertSame(a, b);
        stateCheck.accept(a, b);
    }

    static Stream<?> splitCases() {
        final TesrMaterial lit = TesrMaterial.builder().build();
        return Stream.of(
            arguments("lit material requested unlit",
                (Supplier<RenderLayer>) () -> layer(lit, DrawState.CULL_BACK, false, true),
                (Supplier<RenderLayer>) () -> layer(lit, DrawState.CULL_BACK, true, true),
                (BiConsumer<RenderLayer, RenderLayer>) (a, b) -> {
                    assertFalse(a.getState().isLit());
                    assertTrue(b.getState().isLit());
                }),
            arguments("lightmap",
                (Supplier<RenderLayer>) () -> layer(lit, DrawState.CULL_BACK, true, true),
                (Supplier<RenderLayer>) () -> layer(lit, DrawState.CULL_BACK, true, false),
                (BiConsumer<RenderLayer, RenderLayer>) (a, b) -> {
                    assertTrue(a.getState().isLightmap());
                    assertFalse(b.getState().isLightmap());
                }),
            arguments("glint tint slots 0 and 1",
                (Supplier<RenderLayer>) () -> glint(1),
                (Supplier<RenderLayer>) () -> glint(0),
                (BiConsumer<RenderLayer, RenderLayer>) (a, b) -> { }),
            arguments("glint untinted and tinted",
                (Supplier<RenderLayer>) () -> glint(ShaderGlint.NO_TINT),
                (Supplier<RenderLayer>) () -> glint(0),
                (BiConsumer<RenderLayer, RenderLayer>) (a, b) -> { })
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("splitCases")
    void fieldSplitsLayerIdentity(String name, Supplier<RenderLayer> requestA, Supplier<RenderLayer> requestB, BiConsumer<RenderLayer, RenderLayer> stateCheck) {
        final RenderLayer a = requestA.get();
        final RenderLayer b = requestB.get();
        assertNotSame(a, b);
        assertSame(a, requestA.get(), "the same request must reuse the layer");
        stateCheck.accept(a, b);
    }
}
