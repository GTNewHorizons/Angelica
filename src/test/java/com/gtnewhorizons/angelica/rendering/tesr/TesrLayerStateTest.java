package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        return RenderLayer.tesr(null, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true);
    }

    private static RenderLayer glint(int slot) {
        return RenderLayer.tesr(null, EntityMaterials.GLINT, PassOverride.NONE, 0f, 0f, slot, DrawState.CULL_BACK, true);
    }

    @Test
    void failedHookSetupRunsTeardownAndSuppressesItsFailure() {
        final int[] releases = { 0 };
        final RuntimeException setup = new RuntimeException("setup");
        final RuntimeException teardown = new RuntimeException("teardown");
        final TesrMaterial material = TesrMaterial.builder()
            .shader(TesrShaders.register("angelica:test_failed_hook_setup", () -> { throw setup; }, () -> {
                releases[0]++;
                throw teardown;
            }))
            .build();

        final RuntimeException thrown = assertThrows(RuntimeException.class, () -> layer(material).startDrawing());
        assertSame(setup, thrown);
        assertEquals(1, releases[0]);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(teardown, thrown.getSuppressed()[0]);
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
    void everyAxisAppliesAbsolutely() {
        GLStateManager.disableCull();
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.disableLighting();
        GLStateManager.glColorMask(false, false, false, false);

        layer(TesrMaterial.CURRENT_STATE).startDrawing();

        assertFalse(GLStateManager.getAlphaTest().isEnabled(), "no cutout");
        assertTrue(GLStateManager.getDepthTest().isEnabled(), "depth test");
        assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc());
        assertTrue(GLStateManager.getDepthState().isEnabled(), "depth write");
        assertFalse(GLStateManager.getBlendMode().isEnabled(), "opaque");
        assertTrue(GLStateManager.getCullState().isEnabled(), "cull pinned on");
        assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode(), "cull face pinned to back");
        assertTrue(GLStateManager.getLightingState().isEnabled(), "lit");
        assertTrue(GLStateManager.getColorMask().red, "color mask pinned on");
        assertFalse(GLStateManager.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "no polygon offset");
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

        assertTrue(GLStateManager.getBlendMode().isEnabled(), "endDrawing must not reset blend");
        assertFalse(GLStateManager.getDepthState().isEnabled(), "endDrawing must not reset the depth mask");
        assertFalse(GLStateManager.getLightingState().isEnabled(), "endDrawing must not reset lighting");
        assertTrue(GLStateManager.getAlphaTest().isEnabled(), "endDrawing must not reset the alpha test");
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
