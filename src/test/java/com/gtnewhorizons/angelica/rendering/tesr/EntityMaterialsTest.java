package com.gtnewhorizons.angelica.rendering.tesr;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityMaterialsTest {

    private static final int SA = GL11.GL_SRC_ALPHA;
    private static final int OMSA = GL11.GL_ONE_MINUS_SRC_ALPHA;

    @Test
    void mainModelStateMapsToCutout() {
        assertSame(EntityMaterials.CUTOUT, EntityMaterials.fromState(true, false, SA, OMSA, true, GL11.GL_GREATER, 0.1f, GL11.GL_LEQUAL, true));
    }

    @Test
    void shadowPassHalfAlphaRefMapsToCutoutHalf() {
        assertSame(EntityMaterials.CUTOUT_HALF, EntityMaterials.fromState(true, false, SA, OMSA, true, GL11.GL_GREATER, 0.5f, GL11.GL_LEQUAL, true));
    }

    @Test
    void noAlphaNoBlendMapsToSolid() {
        assertSame(EntityMaterials.SOLID, EntityMaterials.fromState(true, false, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true));
    }

    @Test
    void invisibilityMapsToTranslucent() {
        assertSame(EntityMaterials.TRANSLUCENT, EntityMaterials.fromState(true, true, SA, OMSA, true, GL11.GL_GREATER, 0.0039f, GL11.GL_LEQUAL, false));
        assertSame(EntityMaterials.TRANSLUCENT_DEPTH_WRITE, EntityMaterials.fromState(true, true, SA, OMSA, true, GL11.GL_GREATER, 0.0039f, GL11.GL_LEQUAL, true));
    }

    @Test
    void eyesMapToAdditive() {
        assertSame(EntityMaterials.ADDITIVE, EntityMaterials.fromState(true, true, GL11.GL_ONE, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true));
        assertSame(EntityMaterials.ADDITIVE_NO_DEPTH_WRITE, EntityMaterials.fromState(true, true, GL11.GL_ONE, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, false));
    }

    @Test
    void hurtOverlayMapsToOverlay() {
        assertSame(EntityMaterials.OVERLAY, EntityMaterials.fromState(false, true, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_EQUAL, true));
    }

    @Test
    void glintStateMapsToGlintOnlyWhenTexAnimated() {
        assertSame(EntityMaterials.GLINT, EntityMaterials.fromState(true, true, true, GL11.GL_SRC_COLOR, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_EQUAL, false));
        assertNull(EntityMaterials.fromState(true, false, true, GL11.GL_SRC_COLOR, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_EQUAL, false), () -> "glint blend without texture animation must stay live");
        assertNull(EntityMaterials.fromState(true, true, true, GL11.GL_SRC_COLOR, GL11.GL_ONE, true, GL11.GL_GREATER, 0.1f, GL11.GL_EQUAL, true), () -> "glint blend with depth write is not vanilla glint");
    }

    @Test
    void texAnimatedAdditiveVariantsMap() {
        assertSame(EntityMaterials.ADDITIVE, EntityMaterials.fromState(true, true, true, GL11.GL_ONE, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true));
        assertSame(EntityMaterials.ADDITIVE_ALPHA_NO_DEPTH_WRITE, EntityMaterials.fromState(true, true, true, SA, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, false));
        assertNull(EntityMaterials.fromState(true, true, true, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true), () -> "animated standard-alpha (scrolling translucent) must stay live");
        assertNull(EntityMaterials.fromState(true, true, false, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true), () -> "animated without blend must stay live");
    }

    @Test
    void additiveAlphaMapsWithoutAnimation() {
        assertSame(EntityMaterials.ADDITIVE_ALPHA, EntityMaterials.fromState(true, false, true, SA, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true));
        assertSame(EntityMaterials.ADDITIVE_ALPHA_NO_DEPTH_WRITE, EntityMaterials.fromState(true, false, true, SA, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, false));
    }

    @Test
    void unrecognizedStatesFallBackToLive() {
        assertNull(EntityMaterials.fromState(true, true, GL11.GL_SRC_COLOR, GL11.GL_ONE, false, GL11.GL_ALWAYS, 0f, GL11.GL_EQUAL, false), () -> "glint blend at EQUAL must stay live");
        assertNull(EntityMaterials.fromState(true, false, SA, OMSA, true, GL11.GL_GREATER, 0.3f, GL11.GL_LEQUAL, true), () -> "nonstandard alpha ref must stay live");
        assertNull(EntityMaterials.fromState(false, false, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_LEQUAL, true), () -> "texture off without overlay signature must stay live");
        assertNull(EntityMaterials.fromState(true, true, SA, OMSA, false, GL11.GL_ALWAYS, 0f, GL11.GL_EQUAL, true), () -> "textured EQUAL-depth re-render must stay live");
    }
}
