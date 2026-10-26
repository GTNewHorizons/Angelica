package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.driverTextureBinding;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyIsEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_StateSet_GLTest {

    @AfterEach
    void resetState() {
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMBlend.glBlendColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLSMBlend.glBlendEquation(GL14.GL_FUNC_ADD);
        GLSMLighting.disableLighting();
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMRasterizer.glShadeModel(GL11.GL_SMOOTH);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(0.0f, 0.0f);
        GLSMRasterizer.disableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTexEnvGen.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GLSMLighting.disableColorMaterial();
        GLSMLighting.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    private static boolean[] driverColorWriteMask() {
        final ByteBuffer buf = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, buf);
        return new boolean[] { buf.get(0) != 0, buf.get(1) != 0, buf.get(2) != 0, buf.get(3) != 0 };
    }

    @Test
    void batchRestoresBlendAxisAndLightingBooleanButNotStencil() {
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMLighting.disableLighting();
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);

        final int d = GLSMAttribStack.pushState(StateSet.BATCH);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
        GLSMLighting.enableLighting();
        GLSMEnable.glEnable(GL11.GL_STENCIL_TEST);
        GLSMAttribStack.popStateTo(d);

        verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored by BATCH pop");
        assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), "driver blend dst rgb");
        assertEquals(GL11.GL_SRC_ALPHA, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb");
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GLSMBlend.getBlendState().getDstRgb(), "cache blend dst rgb");
        verifyIsEnabled(GL11.GL_LIGHTING, false, "Lighting enable - restored by BATCH pop");
        verifyIsEnabled(GL11.GL_STENCIL_TEST, true, "Stencil test - not a BATCH member, must stay toggled");
    }

    @Test
    void fontRestoresDepthAxisShadeModelAndPolygonOffsetButNotStencil() {
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMRasterizer.glShadeModel(GL11.GL_SMOOTH);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(0.0f, 0.0f);
        GLSMEnable.glEnable(GL11.GL_STENCIL_TEST);

        final int d = GLSMAttribStack.pushState(StateSet.FONT);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
        GLSMRasterizer.glShadeModel(GL11.GL_FLAT);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(-10.0f, -10.0f);
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);
        GLSMAttribStack.popStateTo(d);

        verifyIsEnabled(GL11.GL_DEPTH_TEST, true, "Depth test enable - restored by FONT pop");
        assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getDepthState().getFunc(), "cache depth func");
        assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func");
        assertEquals(GL11.GL_SMOOTH, GLSMRasterizer.getShadeModelState().getValue(), "cache shade model");
        verifyIsEnabled(GL11.GL_POLYGON_OFFSET_FILL, false, "Polygon offset fill - restored by FONT pop");
        assertEquals(0.0f, GLSMRasterizer.getPolygonState().getOffsetFactor(), "cache polygon offset factor");
        assertEquals(0.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), "driver polygon offset factor");
        assertEquals(0.0f, GLSMRasterizer.getPolygonState().getOffsetUnits(), "cache polygon offset units");
        assertEquals(0.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS), "driver polygon offset units");
        verifyIsEnabled(GL11.GL_STENCIL_TEST, false, "Stencil test - not a FONT member, must stay toggled");
    }

    @Test
    void fontPipelineRestoresBlendAndDepthFuncButNotProgram() {
        final int p0 = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        final int p1 = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        try {
            GLSMPrograms.glUseProgram(p0);
            GLSMBlend.enableBlend();
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);

            final int d = GLSMAttribStack.pushState(StateSet.FONT_PIPELINE);
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
            GLSMPrograms.glUseProgram(p1);
            GLSMAttribStack.popStateTo(d);

            assertEquals(GL11.GL_SRC_ALPHA, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - restored by FONT_PIPELINE pop");
            assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - restored by FONT_PIPELINE pop");
            assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getDepthState().getFunc(), "cache depth func - restored by FONT_PIPELINE pop");
            assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func - restored by FONT_PIPELINE pop");
            assertEquals(p1, GLSMPrograms.getActiveProgram(), "program - not a FONT_PIPELINE member, must stay toggled");

            GLSMPrograms.glUseProgram(p0);
        } finally {
            GLSMPrograms.glUseProgram(0);
            GLSMPrograms.glDeleteProgram(p0);
            GLSMPrograms.glDeleteProgram(p1);
        }
    }

    @Test
    void handRestoresBlendAxisAndUnit1TexturingButNotColorMask() {
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.enableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);

        final int d = GLSMAttribStack.pushState(StateSet.HAND);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
        GLSMBlend.glColorMask(false, false, false, false);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMAttribStack.popStateTo(d);

        verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored by HAND pop");
        assertEquals(GL11.GL_SRC_ALPHA, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb");
        assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");
        assertTrue(GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled(), "unit 1 texturing - restored by HAND pop");

        final boolean[] driverMask = driverColorWriteMask();
        assertFalse(driverMask[0] || driverMask[1] || driverMask[2] || driverMask[3], "driver color mask - not a HAND member, must stay toggled");
        assertFalse(GLSMBlend.getColorMask().red || GLSMBlend.getColorMask().green
            || GLSMBlend.getColorMask().blue || GLSMBlend.getColorMask().alpha, "cache color mask - not a HAND member, must stay toggled");
    }

    @Test
    void masksRestoresDepthMaskAndColorMaskButNotDepthTestEnable() {
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMBlend.glColorMask(true, false, true, false);
        GLSMDepthStencilAlpha.disableDepthTest();

        final int d = GLSMAttribStack.pushState(StateSet.MASKS);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
        GLSMBlend.glColorMask(false, true, false, true);
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMAttribStack.popStateTo(d);

        assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth write mask - restored by MASKS pop");
        assertTrue(GLSMDepthStencilAlpha.getDepthState().isEnabled(), "cache depth write mask - restored by MASKS pop");
        assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getDepthState().getFunc(), "cache depth func");
        assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func");

        final boolean[] driverMask = driverColorWriteMask();
        assertTrue(driverMask[0] && !driverMask[1] && driverMask[2] && !driverMask[3], "driver color mask - restored by MASKS pop");
        assertTrue(GLSMBlend.getColorMask().red && !GLSMBlend.getColorMask().green
            && GLSMBlend.getColorMask().blue && !GLSMBlend.getColorMask().alpha, "cache color mask - restored by MASKS pop");

        verifyIsEnabled(GL11.GL_DEPTH_TEST, true, "Depth test enable - not a MASKS member, must stay toggled");
    }

    @Test
    void cullRestoresPolygonAxisAndCullEnableButNotBlendFunc() {
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        final int d = GLSMAttribStack.pushState(StateSet.CULL);
        GLSMRasterizer.disableCull();
        GLSMRasterizer.glCullFace(GL11.GL_FRONT);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
        GLSMAttribStack.popStateTo(d);

        verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull enable - restored by CULL pop");
        assertEquals(GL11.GL_BACK, GLSMRasterizer.getPolygonState().getCullFaceMode(), "cache cull face mode");
        assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode");

        verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - not a CULL member, must stay toggled");
        assertEquals(GL11.GL_DST_COLOR, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - not a CULL member, must stay toggled");
        assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - not a CULL member, must stay toggled");
    }

    @Test
    void blendRestoresBlendAxisButNotCull() {
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);

        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
        GLSMRasterizer.disableCull();
        GLSMRasterizer.glCullFace(GL11.GL_FRONT);
        GLSMAttribStack.popStateTo(d);

        verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored by BLEND pop");
        assertEquals(GL11.GL_SRC_ALPHA, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb");
        assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");

        verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull enable - not a BLEND member, must stay toggled");
        assertEquals(GL11.GL_FRONT, GLSMRasterizer.getPolygonState().getCullFaceMode(), "cache cull face mode - not a BLEND member, must stay toggled");
        assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - not a BLEND member, must stay toggled");
    }

    @Test
    void alphaRestoresAlphaAxisButNotBlend() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        final int d = GLSMAttribStack.pushState(StateSet.ALPHA);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.1f);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
        GLSMAttribStack.popStateTo(d);

        verifyIsEnabled(GL11.GL_ALPHA_TEST, true, "Alpha test enable - restored by ALPHA pop");
        assertEquals(GL11.GL_GREATER, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "cache alpha func");
        assertEquals(0.5f, GLSMDepthStencilAlpha.getAlphaState().getReference(), 0.0001f, "cache alpha ref");

        verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - not an ALPHA member, must stay toggled");
        assertEquals(GL11.GL_DST_COLOR, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - not an ALPHA member, must stay toggled");
        assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - not an ALPHA member, must stay toggled");
    }

    @Test
    void cutoutRestoresAlphaAxisAndUnit1TexturingButNotBlend() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.enableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        final int d = GLSMAttribStack.pushState(StateSet.CUTOUT);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.1f);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);

        final int fragBefore = GLStateManager.ctx().fragmentGeneration;
        GLSMAttribStack.popStateTo(d);

        assertEquals(fragBefore + 1, GLStateManager.ctx().fragmentGeneration, "CUTOUT pop must bump fragment generation once when the value changed inside the bracket");

        verifyIsEnabled(GL11.GL_ALPHA_TEST, true, "Alpha test enable - restored by CUTOUT pop");
        assertEquals(GL11.GL_GREATER, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "cache alpha func");
        assertEquals(0.5f, GLSMDepthStencilAlpha.getAlphaState().getReference(), 0.0001f, "cache alpha ref");
        assertTrue(GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled(), "unit 1 texturing - restored by CUTOUT pop");

        verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - not a CUTOUT member, must stay toggled");
        assertEquals(GL11.GL_DST_COLOR, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - not a CUTOUT member, must stay toggled");
        assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - not a CUTOUT member, must stay toggled");
    }

    @Test
    void nonMemberMutationsInsideBracketSaveAndDiscardNothing() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);

        final int d = GLSMAttribStack.pushState(StateSet.CUTOUT);
        final long discardsBefore = GLSMAttribStack.attribDiscards;
        try {
            final long savedBefore = GLSMAttribStack.attribSlotsSaved;
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(savedBefore, GLSMAttribStack.attribSlotsSaved, "non-member mutations must not save slots");
        } finally {
            GLSMAttribStack.popStateTo(d);
        }

        assertEquals(discardsBefore, GLSMAttribStack.attribDiscards, "non-member mutations must not leave slots to discard");
        assertEquals(GL11.GL_DST_COLOR, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - not a CUTOUT member, must stay toggled");
    }

    @Test
    void unchangedSettersSaveNothing() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMBlend.glBlendColor(0.1f, 0.2f, 0.3f, 0.4f);
        GLSMBlend.glBlendEquation(GL14.GL_FUNC_ADD);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.enableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);

        final int d = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        try {
            final long savedBefore = GLSMAttribStack.attribSlotsSaved;
            GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMBlend.glColorMask(true, true, true, true);
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLSMBlend.glBlendColor(0.1f, 0.2f, 0.3f, 0.4f);
            GLSMBlend.glBlendEquation(GL14.GL_FUNC_ADD);
            GLSMBlend.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.enableTexture();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(1, GLSMAttribStack.attribSlotsSaved - savedBefore, "only the active unit change may save a slot");
        } finally {
            GLSMAttribStack.popStateTo(d);
        }

        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        final int d2 = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        try {
            final long savedBefore = GLSMAttribStack.attribSlotsSaved;
            GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            assertEquals(0, GLSMAttribStack.attribSlotsSaved - savedBefore, "unchanged glBlendFunc must not save a slot");
        } finally {
            GLSMAttribStack.popStateTo(d2);
        }
    }

    @Test
    void bracketDoesNotInheritMembersOfAnEarlierBracketAtItsDepth() {
        for (boolean nested : new boolean[] {false, true}) {
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
            final int outer = nested ? GLSMAttribStack.pushState(StateSet.CULL) : GLSMAttribStack.getAttribDepth();
            try {
                GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                GLSMAttribStack.glPopAttrib();
                final int d = GLSMAttribStack.pushState(StateSet.CUTOUT);
                try {
                    final long savedBefore = GLSMAttribStack.attribSlotsSaved;
                    GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
                    GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
                    assertEquals(savedBefore, GLSMAttribStack.attribSlotsSaved, "nested=" + nested);
                } finally {
                    GLSMAttribStack.popStateTo(d);
                }
            } finally {
                GLSMAttribStack.popStateTo(outer);
            }
        }
    }

    @Test
    void cullFaceModeRestoredThroughBatchBracketRegression2117() {
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        final int d = GLSMAttribStack.pushState(StateSet.BATCH);
        GLSMRasterizer.glCullFace(GL11.GL_FRONT);
        GLSMAttribStack.popStateTo(d);

        assertEquals(GL11.GL_BACK, GLSMRasterizer.getPolygonState().getCullFaceMode(), "cache cull face mode");
        assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode");
    }

    @Test
    void batchRestoresUnit0And1BindingsAndActiveUnitButNotUnit2() {
        if (GLStateManager.MAX_TEXTURE_UNITS < 3) return;
        final int tex0Base = GLSMTextures.glGenTextures();
        final int tex1Base = GLSMTextures.glGenTextures();
        final int tex2Base = GLSMTextures.glGenTextures();
        final int tex0Dirty = GLSMTextures.glGenTextures();
        final int tex1Dirty = GLSMTextures.glGenTextures();
        final int tex2Dirty = GLSMTextures.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex0Base);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex1Base);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + 2);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex2Base);

            final int d = GLSMAttribStack.pushState(StateSet.BATCH);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex0Dirty);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex1Dirty);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + 2);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex2Dirty);
            GLSMAttribStack.popStateTo(d);

            assertEquals(2, GLSMTextures.getActiveTextureUnit(), "cache active unit - restored by BATCH pop");
            assertEquals(GL13.GL_TEXTURE0 + 2, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit - restored by BATCH pop");

            assertEquals(tex0Base, GLSMTextures.getTextures().getTextureUnitBindings(0).getBinding(), "cache unit 0 binding - restored by BATCH pop");
            assertEquals(tex0Base, driverTextureBinding(0), "driver unit 0 binding - restored by BATCH pop");
            assertEquals(tex1Base, GLSMTextures.getTextures().getTextureUnitBindings(1).getBinding(), "cache unit 1 binding - restored by BATCH pop");
            assertEquals(tex1Base, driverTextureBinding(1), "driver unit 1 binding - restored by BATCH pop");

            assertEquals(tex2Dirty, GLSMTextures.getTextures().getTextureUnitBindings(2).getBinding(), "cache unit 2 binding - not a BATCH member, must stay toggled");
            assertEquals(tex2Dirty, driverTextureBinding(2), "driver unit 2 binding - not a BATCH member, must stay toggled");
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + 2);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glDeleteTextures(tex0Base);
            GLSMTextures.glDeleteTextures(tex1Base);
            GLSMTextures.glDeleteTextures(tex2Base);
            GLSMTextures.glDeleteTextures(tex0Dirty);
            GLSMTextures.glDeleteTextures(tex1Dirty);
            GLSMTextures.glDeleteTextures(tex2Dirty);
        }
    }

    @Test
    void popStateToRepairsUnbalancedInnerPush() {
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMAttribStack.pushState(StateSet.CULL);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMAttribStack.popStateTo(d);
        assertEquals(d, GLSMAttribStack.getAttribDepth());
        assertEquals(GL11.GL_ONE, GLSMBlend.getBlendState().getSrcRgb(), "popStateTo repairs the unbalanced non-member push and carries the change through to the BLEND member pop, restoring the pre-bracket baseline");
        assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");
    }

    @Test
    void popStateToToleratesUnbalancedInnerPop() {
        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMAttribStack.popState();
        assertEquals(d, GLSMAttribStack.getAttribDepth());
        GLSMAttribStack.popStateTo(d);
        assertEquals(d, GLSMAttribStack.getAttribDepth());
    }

    @Test
    void exceptionMidBracketLeavesDepthAndStateCorrectViaFinally() {
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        final int depthBefore = GLSMAttribStack.getAttribDepth();

        final RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            final int d = GLSMAttribStack.pushState(StateSet.BLEND);
            try {
                GLSMBlend.disableBlend();
                GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
                throw new RuntimeException("boom");
            } finally {
                GLSMAttribStack.popStateTo(d);
            }
        });

        assertEquals("boom", thrown.getMessage());
        assertEquals(depthBefore, GLSMAttribStack.getAttribDepth());
        verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored despite exception");
        assertEquals(GL11.GL_SRC_ALPHA, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - restored despite exception");
    }

    @Test
    void allAttribBitsNestedInsideInternalBracketRestoresBothLevels() {
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);

        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLSMRasterizer.glCullFace(GL11.GL_FRONT);
        GLSMAttribStack.glPopAttrib();

        verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull enable - restored by inner GL_ALL_ATTRIB_BITS pop");
        assertEquals(GL11.GL_BACK, GLSMRasterizer.getPolygonState().getCullFaceMode(), "cache cull face mode - restored by inner GL_ALL_ATTRIB_BITS pop");
        assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - restored by inner GL_ALL_ATTRIB_BITS pop");

        GLSMAttribStack.popStateTo(d);

        verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - restored by outer BLEND pop");
        assertEquals(GL11.GL_ONE, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - restored by outer BLEND pop");
    }

    @Test
    void internalBracketNestedInsideAllAttribBitsRestoresBothLevels() {
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);

        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GLSMRasterizer.glCullFace(GL11.GL_FRONT);

            final int d = GLSMAttribStack.pushState(StateSet.BLEND);
            GLSMBlend.enableBlend();
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLSMAttribStack.popStateTo(d);

            verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - restored by inner BLEND pop");
            assertEquals(GL11.GL_ONE, GLSMBlend.getBlendState().getSrcRgb(), "cache blend src rgb - restored by inner BLEND pop");
            assertEquals(GL11.GL_FRONT, GLSMRasterizer.getPolygonState().getCullFaceMode(), "cache cull face mode - change survives, not a BLEND member");
            assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - change survives, not a BLEND member");
        } finally {
            GLSMAttribStack.glPopAttrib();
        }

        assertEquals(GL11.GL_BACK, GLSMRasterizer.getPolygonState().getCullFaceMode(), "cache cull face mode - restored by outer GL_ALL_ATTRIB_BITS pop");
        assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - restored by outer GL_ALL_ATTRIB_BITS pop");
    }

    @Test
    void rProgramFontRestoresRealProgramToDriverAndCache() {
        final int p0 = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        final int p1 = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        try {
            GLSMPrograms.glUseProgram(p0);

            final int d = GLSMAttribStack.pushState(StateSet.FONT);
            GLSMPrograms.glUseProgram(p1);
            GLSMAttribStack.popStateTo(d);

            assertEquals(p0, GLSMPrograms.getActiveProgram(), "cache active program restored");
            assertEquals(p0, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), "driver current program restored");
            assertFalse(ShaderManager.getInstance().isActive(), "FFP must stay inactive when a real program was bound before the bracket");
        } finally {
            GLSMPrograms.glUseProgram(0);
            GLSMPrograms.glDeleteProgram(p0);
            GLSMPrograms.glDeleteProgram(p1);
        }
    }

    @Test
    void rProgramFontReactivatesFfpAfterBracket() {
        final int p1 = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        try {
            GLSMPrograms.glUseProgram(0);
            assertTrue(ShaderManager.getInstance().isActive(), "FFP must be active before the bracket");

            final int d = GLSMAttribStack.pushState(StateSet.FONT);
            GLSMPrograms.glUseProgram(p1);
            assertFalse(ShaderManager.getInstance().isActive(), "FFP must deactivate once a real program binds");
            GLSMAttribStack.popStateTo(d);

            assertEquals(0, GLSMPrograms.getActiveProgram(), "cache active program restored to the FFP sentinel");
            assertTrue(ShaderManager.getInstance().isActive(), "FFP must be active again after the pop");
        } finally {
            GLSMPrograms.glUseProgram(0);
            GLSMPrograms.glDeleteProgram(p1);
        }
    }

    @Test
    void blendPopDoesNotBumpFragmentOrLightingGeneration() {
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);

        final int fragBefore = GLStateManager.ctx().fragmentGeneration;
        final int lightBefore = GLStateManager.ctx().lightingGeneration;
        GLSMAttribStack.popStateTo(d);

        assertEquals(fragBefore, GLStateManager.ctx().fragmentGeneration, "BLEND pop must not bump fragment generation");
        assertEquals(lightBefore, GLStateManager.ctx().lightingGeneration, "BLEND pop must not bump lighting generation");
    }

    @Test
    void alphaPopBumpsFragmentGenerationWhenChanged() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);

        final int d = GLSMAttribStack.pushState(StateSet.ALPHA);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.1f);

        final int fragBefore = GLStateManager.ctx().fragmentGeneration;
        GLSMAttribStack.popStateTo(d);

        assertEquals(fragBefore + 1, GLStateManager.ctx().fragmentGeneration, "ALPHA pop must bump fragment generation once when the value changed inside the bracket");
    }

    @Test
    void textureBitBracketRestoresUnit1BindingAndActiveUnit() {
        final int texBase = GLSMTextures.glGenTextures();
        final int texDirty = GLSMTextures.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texBase);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            final int d = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_TEXTURE_BIT));
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texDirty);
            GLSMAttribStack.popStateTo(d);
            assertEquals(0, GLSMTextures.getActiveTextureUnit(), "cache active unit restored by GL_TEXTURE_BIT pop");
            assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit restored by GL_TEXTURE_BIT pop");
            assertEquals(texBase, GLSMTextures.getTextures().getTextureUnitBindings(1).getBinding(), "cache unit 1 binding restored by GL_TEXTURE_BIT pop");
            assertEquals(texBase, driverTextureBinding(1), "driver unit 1 binding restored by GL_TEXTURE_BIT pop");
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glDeleteTextures(texBase);
            GLSMTextures.glDeleteTextures(texDirty);
        }
    }

    @Test
    void textureBitBracketRestoresTexEnvModeAndColor() {
        GLSMTexEnvGen.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        final FloatBuffer zero = BufferUtils.createFloatBuffer(4);
        zero.put(0.0f).put(0.0f).put(0.0f).put(0.0f).flip();
        GLSMTexEnvGen.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, zero);
        final int d = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_TEXTURE_BIT));
        GLSMTexEnvGen.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_DECAL);
        final FloatBuffer dirty = BufferUtils.createFloatBuffer(4);
        dirty.put(0.1f).put(0.2f).put(0.3f).put(0.4f).flip();
        GLSMTexEnvGen.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, dirty);
        GLSMAttribStack.popStateTo(d);
        assertEquals(GL11.GL_MODULATE, GLSMTextures.getTextures().getTexEnvState(0).mode, "cache tex env mode restored by GL_TEXTURE_BIT pop");
        assertEquals(0.0f, GLSMTextures.getTextures().getTexEnvState(0).envColorR, 0.0001f, "cache tex env color restored by GL_TEXTURE_BIT pop");
    }

    @Test
    void lightingBitBracketRestoresBakedFrontMaterialAfterColorMaterialToggle() {
        GLSMCurrentAttribs.glColor4f(0.9f, 0.9f, 0.9f, 0.9f);
        GLSMLighting.enableColorMaterial();
        GLSMLighting.disableColorMaterial();
        final int d = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_LIGHTING_BIT));
        GLSMCurrentAttribs.glColor4f(0.1f, 0.2f, 0.3f, 0.4f);
        GLSMLighting.enableColorMaterial();
        GLSMAttribStack.popStateTo(d);
        assertEquals(0.9f, GLSMLighting.getFrontMaterial().ambient.x, 0.0001f, "cache front material ambient restored by GL_LIGHTING_BIT pop");
        assertEquals(0.9f, GLSMLighting.getFrontMaterial().diffuse.x, 0.0001f, "cache front material diffuse restored by GL_LIGHTING_BIT pop");
    }

    @Test
    void batchAppliesPassLightingAndRestoresLiveLighting() {
        final boolean lightmapWas = GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled();
        final LightingSnapshot pass = new LightingSnapshot();
        try {
            GLSMEnable.glEnable(GL11.GL_LIGHT0);
            GLSMEnable.glEnable(GL11.GL_LIGHT1);
            GLSMLighting.enableColorMaterial();
            GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 2.0f);
            GLSMLighting.captureLighting(pass);

            GLSMEnable.glDisable(GL11.GL_LIGHT0);
            GLSMEnable.glDisable(GL11.GL_LIGHT1);
            GLSMLighting.disableColorMaterial();
            GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 1.0f);
            GLSMTextures.getTextures().getTextureUnitStates(1).setEnabled(false);
            final int liveGeneration = GLSMLighting.getLightingGeneration();

            final int d = GLSMAttribStack.pushState(StateSet.BATCH);
            GLSMLighting.applyLighting(pass);
            GLSMTextures.getTextures().getTextureUnitStates(1).setEnabled(true);
            assertTrue(GLSMLighting.getLightStates()[0].isEnabled(), "light 0 from the pass");
            assertTrue(GLSMLighting.getLightStates()[1].isEnabled(), "light 1 from the pass");
            assertTrue(GLSMLighting.getColorMaterial().isEnabled(), "color material from the pass");
            assertEquals(2.0f, GLSMLighting.getLightDataStates()[0].constantAttenuation, "light 0 data from the pass");
            assertTrue(GLSMLighting.getLightingGeneration() != liveGeneration, "lighting uniforms marked dirty");
            final int passGeneration = GLSMLighting.getLightingGeneration();
            GLSMAttribStack.popStateTo(d);

            assertFalse(GLSMLighting.getLightStates()[0].isEnabled(), "light 0 restored by BATCH pop");
            assertFalse(GLSMLighting.getLightStates()[1].isEnabled(), "light 1 restored by BATCH pop");
            assertFalse(GLSMLighting.getColorMaterial().isEnabled(), "color material restored by BATCH pop");
            assertEquals(1.0f, GLSMLighting.getLightDataStates()[0].constantAttenuation, "light 0 data restored by BATCH pop");
            assertFalse(GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled(), "lightmap enable restored by BATCH pop");
            assertTrue(GLSMLighting.getLightingGeneration() != passGeneration, "lighting uniforms marked dirty after pop");
        } finally {
            GLSMEnable.glDisable(GL11.GL_LIGHT0);
            GLSMEnable.glDisable(GL11.GL_LIGHT1);
            GLSMLighting.disableColorMaterial();
            GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 1.0f);
            GLSMTextures.getTextures().getTextureUnitStates(1).setEnabled(lightmapWas);
        }
    }
}
