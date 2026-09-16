package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.layer.PassOverride;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class TesrLayerStateTest {

    @BeforeEach
    void liveState() {
        GLStateManager.disableFog();
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.disableBlend();
        GLStateManager.enableCull();
        GLStateManager.enableLighting();
    }

    @AfterEach
    void tearDown() {
        AlphaTestStorage.restoreAlphaTest();
        DepthColorStorage.unlockDepthColor();
        GLStateManager.getAlphaTest().setVanillaLayer(null);
        GLStateManager.getAlphaState().setVanillaLayer(null);
        GLStateManager.getDepthState().setVanillaLayer(null);

        ModelPartBatcher.INSTANCE.clear();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final ShaderManager ffp = ShaderManager.getInstance();
        if (ffp.isActive()) ffp.deactivate();
        ffp.disable();

        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0f);
        GLStateManager.glDepthFunc(GL11.GL_LESS);
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
    }

    private static RenderLayer layer(TesrMaterial material) {
        return RenderLayer.tesr(null, material);
    }

    private static RenderLayer glint(int slot) {
        return RenderLayer.tesr(null, EntityMaterials.GLINT, PassOverride.NONE, 0f, 0f, slot);
    }

    @Test
    void glintTintSlotsSplitLayers() {
        assertSame(glint(0), glint(0), "the same slot must reuse the layer");
        assertNotSame(glint(1), glint(0), "distinct slots must not share a layer");
        assertNotSame(glint(ShaderGlint.NO_TINT), glint(0), "an untinted glint must not share a tinted layer");
    }

    @Test
    void startDrawingLeavesUndeclaredPhasesToTheSurroundingContext() {
        layer(TesrMaterial.CURRENT_STATE).startDrawing();

        assertFalse(GLStateManager.getFogMode().isEnabled(), "fog");
    }

    @Test
    void endDrawingLeavesUndeclaredPhasesToTheSurroundingContext() {
        GLStateManager.enableFog();
        final RenderLayer layer = layer(TesrMaterial.CURRENT_STATE);
        layer.startDrawing();
        layer.endDrawing();

        assertTrue(GLStateManager.getFogMode().isEnabled(), "fog");
    }

    @Test
    void declaredPhasesApply() {
        layer(TesrMaterial.CURRENT_STATE).startDrawing();

        assertFalse(GLStateManager.getAlphaTest().isEnabled(), "ZERO_ALPHA");
        assertTrue(GLStateManager.getDepthTest().isEnabled(), "LEQUAL_DEPTH_TEST");
        assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc());
        assertFalse(GLStateManager.getBlendMode().isEnabled(), "NO_TRANSPARENCY");
        assertEquals(GL11.GL_SMOOTH, GLStateManager.getShadeModelState().getValue(), "SMOOTH_SHADE_MODEL");
    }

    @Test
    void everyMaterialToggleApplies() {
        final TesrMaterial material = TesrMaterial.builder().noCull().unlit().noDepthWrite().cutout(0.5f).translucent().depthEqual().build();
        final RenderLayer layer = layer(material);
        layer.startDrawing();

        assertFalse(GLStateManager.getCullState().isEnabled(), "noCull");
        assertFalse(GLStateManager.getLightingState().isEnabled(), "unlit");
        assertFalse(GLStateManager.getDepthState().isEnabled(), "noDepthWrite");
        assertTrue(GLStateManager.getAlphaTest().isEnabled(), "cutout");
        assertEquals(0.5f, GLStateManager.getAlphaState().getReference(), "cutout threshold");
        assertTrue(GLStateManager.getBlendMode().isEnabled(), "translucent");
        assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb());
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GLStateManager.getBlendState().getDstRgb());
        assertEquals(GL11.GL_EQUAL, GLStateManager.getDepthState().getFunc(), "depthEqual");

        layer.endDrawing();

        assertFalse(GLStateManager.getBlendMode().isEnabled(), "blend restored");
        assertTrue(GLStateManager.getDepthState().isEnabled(), "depth write restored");
        assertTrue(GLStateManager.getLightingState().isEnabled(), "lighting restored");
    }

    @Test
    void effectiveAlphaStateSurvivesAPackOverridingTheRawAlphaTest() {
        GLStateManager.getAlphaTest().setVanillaLayer(AlphaTestStorage.ENABLE_LAYER);
        GLStateManager.getAlphaState().setVanillaLayer(AlphaTestStorage.FUNC_LAYER);

        GLStateManager.disableBlend();
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);

        AlphaTestStorage.overrideAlphaTest(null);

        assertSame(EntityMaterials.CUTOUT, EntityMaterials.fromCurrentState(true), "raw alpha test is disabled by the pack override; a raw read would give SOLID");
        assertSame(EntityMaterials.DROPPED_ITEM_CUTOUT, EntityMaterials.itemFromCurrentState(true));
    }

    @Test
    void effectiveDepthMaskSurvivesAnotherPassHoldingItOff() {
        GLStateManager.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);

        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);

        DepthColorStorage.disableDepthColor();

        assertSame(EntityMaterials.TRANSLUCENT_DEPTH_WRITE, EntityMaterials.fromCurrentState(true), "raw depth mask held off by another pass; the effective vanilla mask must pick the depth-write variant");
    }

    private static boolean unitTexIdentity() {
        return Reflect.<Boolean>invoke(ModelPartBatcher.INSTANCE, "unitTexIdentity", new Class<?>[0]);
    }

    private static Matrix4f texMatrixScratch() {
        return Reflect.get(ModelPartBatcher.INSTANCE, "texMatrixScratch");
    }

    @Test
    void memoTracksTextureModeOperationsOnUnitZero() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final ShaderManager ffp = ShaderManager.getInstance();
        ffp.enable();
        ffp.activate();

        assertTrue(unitTexIdentity(), "a freshly loaded identity unit-0 matrix must read as identity");

        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(0.3f, 0.1f, 0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "a translate in texture mode must stop reading as identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "the scratch copy must hold the unit-0 matrix once it stops being identity");

        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(unitTexIdentity(), "loadIdentity after a push must read as identity again");

        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "popping back to the earlier translate must stop reading as identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "pop must restore the memo to the popped matrix");

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(0.5f, 0.5f, 0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "a unit-1 operation forces a recompute, but unit 0 itself is unchanged and stays non-identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "the recompute must copy unit 0, not the active unit 1");

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glTranslatef(0.5f, 0.5f, 0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(unitTexIdentity(), "an identity unit 0 must read as identity while a non-identity unit 1 is active");
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }
}
