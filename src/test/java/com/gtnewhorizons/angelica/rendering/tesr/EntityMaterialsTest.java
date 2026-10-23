package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMaterialsTest {

    private static final int SA = GL11.GL_SRC_ALPHA;
    private static final int OMSA = GL11.GL_ONE_MINUS_SRC_ALPHA;

    @Test
    void armorAndHeldGlintRemainDistinctWhenMaterialsAreInterned() {
        assertNotSame(EntityMaterials.GLINT, EntityMaterials.ITEM_GLINT,
            "only held glint uses the per-item stencil guard");
    }

    private static Arguments row(String name, TesrMaterial expected, boolean textured, boolean blend, Boolean texAnimated, int srcRgb, int dstRgb, boolean alphaTest, int alphaFunc, float alphaRef, int depthFunc, boolean depthMask) {
        return Arguments.of(name, expected, textured, blend, texAnimated, srcRgb, dstRgb, alphaTest, alphaFunc, alphaRef, depthFunc, depthMask);
    }

    static Stream<Arguments> fromStateCases() {
        return Stream.of(
            row("main model state maps to cutout", EntityMaterials.CUTOUT, true, false, null, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            row("shadow pass half alpha ref maps to cutout half", EntityMaterials.CUTOUT_HALF, true, false, null, SA, OMSA, true, GL11.GL_GREATER, 0.5f, GL11.GL_LEQUAL, true),
            row("no alpha no blend maps to solid", EntityMaterials.SOLID, true, false, null, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            row("invisibility maps to translucent", EntityMaterials.TRANSLUCENT, true, true, null, SA, OMSA, true, GL11.GL_GREATER, 0.0039f, GL11.GL_LEQUAL, false),
            row("invisibility with depth write maps to translucent depth write", EntityMaterials.TRANSLUCENT_DEPTH_WRITE, true, true, null, SA, OMSA, true, GL11.GL_GREATER, 0.0039f, GL11.GL_LEQUAL, true),
            row("eyes map to additive", EntityMaterials.ADDITIVE, true, true, null, GL11.GL_ONE, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            row("eyes without depth write map to additive no depth write", EntityMaterials.ADDITIVE_NO_DEPTH_WRITE, true, true, null, GL11.GL_ONE, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, false),
            row("additive keeps captured alpha test", EntityMaterials.ADDITIVE_CUTOUT, true, true, null, GL11.GL_ONE, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            row("additive cutout without depth write", EntityMaterials.ADDITIVE_CUTOUT_NO_DEPTH_WRITE, true, true, null, GL11.GL_ONE, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, false),
            row("additive alpha keeps captured alpha test", EntityMaterials.ADDITIVE_ALPHA_CUTOUT, true, true, null, SA, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            row("additive alpha cutout without depth write", EntityMaterials.ADDITIVE_ALPHA_CUTOUT_NO_DEPTH_WRITE, true, true, null, SA, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, false),
            row("animated additive keeps captured alpha test", EntityMaterials.ADDITIVE_CUTOUT, true, true, true, GL11.GL_ONE, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            row("animated additive alpha cutout without depth write", EntityMaterials.ADDITIVE_ALPHA_CUTOUT_NO_DEPTH_WRITE, true, true, true, SA, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, false),
            row("additive with a nonstandard alpha ref stays live", null, true, true, null, GL11.GL_ONE, GL11.GL_ONE, true, GL11.GL_GREATER, 0.5f, GL11.GL_LEQUAL, true),
            row("additive with a non-GREATER alpha func stays live", null, true, true, null, GL11.GL_ONE, GL11.GL_ONE, true, GL11.GL_LESS, 0.1f, GL11.GL_LEQUAL, true),
            row("animated additive with a nonstandard alpha ref stays live", null, true, true, true, SA, GL11.GL_ONE, true, GL11.GL_GREATER, 0.5f, GL11.GL_LEQUAL, true),
            row("hurt overlay maps to overlay", EntityMaterials.OVERLAY, false, true, null, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_EQUAL, true),
            row("glint maps to glint when texture animated", EntityMaterials.GLINT, true, true, true, GL11.GL_SRC_COLOR, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_EQUAL, false),
            row("an alpha-blended animated decal such as a CIT armor overlay is not glint", null, true, true, true, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_EQUAL, false),
            row("glint blend without texture animation stays live", null, true, true, false, GL11.GL_SRC_COLOR, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_EQUAL, false),
            row("glint blend with depth write is not vanilla glint", null, true, true, true, GL11.GL_SRC_COLOR, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_EQUAL, true),
            row("animated additive maps", EntityMaterials.ADDITIVE, true, true, true, GL11.GL_ONE, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            row("animated additive alpha without depth write maps", EntityMaterials.ADDITIVE_ALPHA_NO_DEPTH_WRITE, true, true, true, SA, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, false),
            row("animated standard-alpha scrolling translucent stays live", null, true, true, true, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            row("animated without blend stays live", null, true, false, true, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            row("additive alpha maps without animation", EntityMaterials.ADDITIVE_ALPHA, true, true, false, SA, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            row("additive alpha without animation or depth write", EntityMaterials.ADDITIVE_ALPHA_NO_DEPTH_WRITE, true, true, false, SA, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, false),
            row("glint blend at EQUAL without animation stays live", null, true, true, null, GL11.GL_SRC_COLOR, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_EQUAL, false),
            row("nonstandard alpha ref stays live", null, true, false, null, SA, OMSA, true, GL11.GL_GREATER, 0.3f, GL11.GL_LEQUAL, true),
            row("texture off without overlay signature stays live", null, false, false, null, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            row("textured EQUAL-depth re-render stays live", null, true, true, null, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_EQUAL, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fromStateCases")
    void fromState(String name, TesrMaterial expected, boolean textured, boolean blend, Boolean texAnimated, int srcRgb, int dstRgb, boolean alphaTest, int alphaFunc, float alphaRef, int depthFunc, boolean depthMask) {
        final TesrMaterial actual = texAnimated == null
            ? EntityMaterials.fromState(textured, blend, srcRgb, dstRgb, alphaTest, alphaFunc, alphaRef, depthFunc, depthMask)
            : EntityMaterials.fromState(textured, texAnimated, blend, srcRgb, dstRgb, alphaTest, alphaFunc, alphaRef, depthFunc, depthMask);
        assertSame(expected, actual, name);
    }

    static Stream<Arguments> itemFromStateCases() {
        return Stream.of(
            Arguments.of("dropped item cutout", EntityMaterials.DROPPED_ITEM_CUTOUT, true, false, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            Arguments.of("held block cutout", EntityMaterials.HELD_BLOCK_CUTOUT, false, false, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            Arguments.of("dropped item translucent", EntityMaterials.DROPPED_ITEM_TRANSLUCENT, true, true, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            Arguments.of("held block translucent", EntityMaterials.HELD_BLOCK_TRANSLUCENT, false, true, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            Arguments.of("nonstandard alpha ref must stay live", null, true, false, SA, OMSA, true, GL11.GL_GREATER, 0.3f, GL11.GL_LEQUAL, true),
            Arguments.of("non-GREATER alpha func must stay live", null, true, false, SA, OMSA, true, GL11.GL_LESS, 0.1f, GL11.GL_LEQUAL, true),
            Arguments.of("alpha test disabled must stay live", null, true, false, SA, OMSA, false, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            Arguments.of("depth mask off must stay live", null, true, false, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, false),
            Arguments.of("non-LEQUAL/LESS depth func must stay live", null, true, false, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_ALWAYS, true),
            Arguments.of("additive blend has no item material", null, true, true, GL11.GL_ONE, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("itemFromStateCases")
    void itemFromState(String name, TesrMaterial expected, boolean unfilteredAtlas, boolean blend, int srcRgb, int dstRgb, boolean alphaTest, int alphaFunc, float alphaRef, int depthFunc, boolean depthMask) {
        assertSame(expected, EntityMaterials.itemFromState(unfilteredAtlas, blend, srcRgb, dstRgb, alphaTest, alphaFunc, alphaRef, depthFunc, depthMask), name);
    }

    static Stream<Arguments> depthOnlyFromStateCases() {
        return Stream.of(
            Arguments.of("color masked off with alpha test maps to depth-only cutout", EntityMaterials.DEPTH_ONLY_CUTOUT, true, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true),
            Arguments.of("color masked off maps to depth-only", EntityMaterials.DEPTH_ONLY, true, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true),
            Arguments.of("nonstandard alpha ref must stay live", null, true, true, GL11.GL_GREATER, 0.5f, GL11.GL_LEQUAL, true),
            Arguments.of("no color and no depth writes must stay live", null, true, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, false),
            Arguments.of("non-LEQUAL/LESS depth func must stay live", null, true, false, GL11.GL_ALWAYS, 0f, GL11.GL_EQUAL, true),
            Arguments.of("texture off must stay live", null, false, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("depthOnlyFromStateCases")
    void depthOnlyFromState(String name, TesrMaterial expected, boolean textured, boolean alphaTest, int alphaFunc, float alphaRef, int depthFunc, boolean depthMask) {
        assertSame(expected, EntityMaterials.depthOnlyFromState(textured, alphaTest, alphaFunc, alphaRef, depthFunc, depthMask), name);
        if (expected == EntityMaterials.DEPTH_ONLY) {
            assertFalse(DrawState.forMaterial(EntityMaterials.DEPTH_ONLY, DrawState.CULL_BACK, true, true, 0f, 0f).isColorWrite(), "depth-only must replay with color writes off");
            assertTrue(DrawState.forMaterial(EntityMaterials.DEPTH_ONLY, DrawState.CULL_BACK, true, true, 0f, 0f).isDepthWrite(), "depth-only must replay with depth writes on");
        }
    }
}
