package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyIsEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_StateSet_GLTest {

    private static int driverTextureBinding(int unit) {
        final int saved = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        final int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(saved);
        return binding;
    }

    private static boolean[] driverColorWriteMask() {
        final ByteBuffer buf = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, buf);
        return new boolean[] { buf.get(0) != 0, buf.get(1) != 0, buf.get(2) != 0, buf.get(3) != 0 };
    }

    private static int compileShader(int type, String src) {
        final int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS), () -> "shader compile failed: " + GL20.glGetShaderInfoLog(s, 4096));
        return s;
    }

    private static final String TRIVIAL_VS = "#version 330 core\nvoid main(){ gl_Position = vec4(0.0); }\n";
    private static final String TRIVIAL_FS = "#version 330 core\nout vec4 o;\nvoid main(){ o = vec4(1.0); }\n";

    private static int linkTrivialProgram() {
        final int v = compileShader(GL20.GL_VERTEX_SHADER, TRIVIAL_VS);
        final int f = compileShader(GL20.GL_FRAGMENT_SHADER, TRIVIAL_FS);
        final int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(p, GL20.GL_LINK_STATUS), () -> "link failed: " + GL20.glGetProgramInfoLog(p, 4096));
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        return p;
    }

    @Test
    void batchRestoresBlendAxisAndLightingBooleanButNotStencil() {
        try {
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.disableLighting();
            GLStateManager.glDisable(GL11.GL_STENCIL_TEST);

            final int d = GLStateManager.pushState(StateSet.BATCH);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLStateManager.enableLighting();
            GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
            GLStateManager.popStateTo(d);

            verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored by BATCH pop");
            assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");
            assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), "driver blend dst rgb");
            assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb");
            assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GLStateManager.getBlendState().getDstRgb(), "cache blend dst rgb");
            verifyIsEnabled(GL11.GL_LIGHTING, false, "Lighting enable - restored by BATCH pop");
            verifyIsEnabled(GL11.GL_STENCIL_TEST, true, "Stencil test - not a BATCH member, must stay toggled");
        } finally {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.disableLighting();
            GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    @Test
    void fontRestoresDepthAxisAndShadeModelButNotCull() {
        try {
            GLStateManager.enableDepthTest();
            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
            GLStateManager.glShadeModel(GL11.GL_SMOOTH);
            GLStateManager.enableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);

            final int d = GLStateManager.pushState(StateSet.FONT);
            GLStateManager.disableDepthTest();
            GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
            GLStateManager.glShadeModel(GL11.GL_FLAT);
            GLStateManager.glCullFace(GL11.GL_FRONT);
            GLStateManager.popStateTo(d);

            verifyIsEnabled(GL11.GL_DEPTH_TEST, true, "Depth test enable - restored by FONT pop");
            assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "cache depth func");
            assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func");
            assertEquals(GL11.GL_SMOOTH, GLStateManager.getShadeModelState().getValue(), "cache shade model");
            assertEquals(GL11.GL_FRONT, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode - not a FONT member, must stay toggled");
            assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - not a FONT member, must stay toggled");
        } finally {
            GLStateManager.disableDepthTest();
            GLStateManager.glDepthFunc(GL11.GL_LESS);
            GLStateManager.glShadeModel(GL11.GL_SMOOTH);
            GLStateManager.disableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);
        }
    }

    @Test
    void fontPipelineRestoresBlendAndDepthFuncButNotProgram() {
        final int p0 = linkTrivialProgram();
        final int p1 = linkTrivialProgram();
        try {
            GLStateManager.glUseProgram(p0);
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);

            final int d = GLStateManager.pushState(StateSet.FONT_PIPELINE);
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
            GLStateManager.glUseProgram(p1);
            GLStateManager.popStateTo(d);

            assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb - restored by FONT_PIPELINE pop");
            assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - restored by FONT_PIPELINE pop");
            assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "cache depth func - restored by FONT_PIPELINE pop");
            assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func - restored by FONT_PIPELINE pop");
            assertEquals(p1, GLStateManager.getActiveProgram(), "program - not a FONT_PIPELINE member, must stay toggled");

            GLStateManager.glUseProgram(p0);
        } finally {
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteProgram(p0);
            GLStateManager.glDeleteProgram(p1);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.glDepthFunc(GL11.GL_LESS);
        }
    }

    @Test
    void handRestoresBlendAxisAndUnit1TexturingButNotColorMask() {
        try {
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.glColorMask(true, true, true, true);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.enableTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

            final int d = GLStateManager.pushState(StateSet.HAND);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLStateManager.glColorMask(false, false, false, false);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.disableTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.popStateTo(d);

            verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored by HAND pop");
            assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb");
            assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");
            assertTrue(GLStateManager.getTextures().getTextureUnitStates(1).isEnabled(), "unit 1 texturing - restored by HAND pop");

            final boolean[] driverMask = driverColorWriteMask();
            assertFalse(driverMask[0] || driverMask[1] || driverMask[2] || driverMask[3], "driver color mask - not a HAND member, must stay toggled");
            assertFalse(GLStateManager.getColorMask().red || GLStateManager.getColorMask().green
                || GLStateManager.getColorMask().blue || GLStateManager.getColorMask().alpha, "cache color mask - not a HAND member, must stay toggled");
        } finally {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.glColorMask(true, true, true, true);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.disableTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void masksRestoresDepthMaskAndColorMaskButNotDepthTestEnable() {
        try {
            GLStateManager.glDepthMask(true);
            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
            GLStateManager.glColorMask(true, false, true, false);
            GLStateManager.disableDepthTest();

            final int d = GLStateManager.pushState(StateSet.MASKS);
            GLStateManager.glDepthMask(false);
            GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
            GLStateManager.glColorMask(false, true, false, true);
            GLStateManager.enableDepthTest();
            GLStateManager.popStateTo(d);

            assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth write mask - restored by MASKS pop");
            assertTrue(GLStateManager.getDepthState().isEnabled(), "cache depth write mask - restored by MASKS pop");
            assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "cache depth func");
            assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func");

            final boolean[] driverMask = driverColorWriteMask();
            assertTrue(driverMask[0] && !driverMask[1] && driverMask[2] && !driverMask[3], "driver color mask - restored by MASKS pop");
            assertTrue(GLStateManager.getColorMask().red && !GLStateManager.getColorMask().green
                && GLStateManager.getColorMask().blue && !GLStateManager.getColorMask().alpha, "cache color mask - restored by MASKS pop");

            verifyIsEnabled(GL11.GL_DEPTH_TEST, true, "Depth test enable - not a MASKS member, must stay toggled");
        } finally {
            GLStateManager.glDepthMask(true);
            GLStateManager.glDepthFunc(GL11.GL_LESS);
            GLStateManager.glColorMask(true, true, true, true);
            GLStateManager.disableDepthTest();
        }
    }

    @Test
    void cullRestoresPolygonAxisAndCullEnableButNotBlendFunc() {
        try {
            GLStateManager.enableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            final int d = GLStateManager.pushState(StateSet.CULL);
            GLStateManager.disableCull();
            GLStateManager.glCullFace(GL11.GL_FRONT);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLStateManager.popStateTo(d);

            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull enable - restored by CULL pop");
            assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode");
            assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode");

            verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - not a CULL member, must stay toggled");
            assertEquals(GL11.GL_DST_COLOR, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb - not a CULL member, must stay toggled");
            assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - not a CULL member, must stay toggled");
        } finally {
            GLStateManager.disableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void blendRestoresBlendAxisButNotCull() {
        try {
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.enableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);

            final int d = GLStateManager.pushState(StateSet.BLEND);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLStateManager.disableCull();
            GLStateManager.glCullFace(GL11.GL_FRONT);
            GLStateManager.popStateTo(d);

            verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored by BLEND pop");
            assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb");
            assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");

            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull enable - not a BLEND member, must stay toggled");
            assertEquals(GL11.GL_FRONT, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode - not a BLEND member, must stay toggled");
            assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - not a BLEND member, must stay toggled");
        } finally {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.disableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);
        }
    }

    @Test
    void alphaRestoresAlphaAxisButNotBlend() {
        try {
            GLStateManager.enableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            final int d = GLStateManager.pushState(StateSet.ALPHA);
            GLStateManager.disableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.1f);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
            GLStateManager.popStateTo(d);

            verifyIsEnabled(GL11.GL_ALPHA_TEST, true, "Alpha test enable - restored by ALPHA pop");
            assertEquals(GL11.GL_GREATER, GLStateManager.getAlphaState().getFunction(), "cache alpha func");
            assertEquals(0.5f, GLStateManager.getAlphaState().getReference(), 0.0001f, "cache alpha ref");

            verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - not an ALPHA member, must stay toggled");
            assertEquals(GL11.GL_DST_COLOR, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb - not an ALPHA member, must stay toggled");
            assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - not an ALPHA member, must stay toggled");
        } finally {
            GLStateManager.disableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void cutoutRestoresAlphaAxisAndUnit1TexturingButNotBlend() {
        try {
            GLStateManager.enableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.enableTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            final int d = GLStateManager.pushState(StateSet.CUTOUT);
            GLStateManager.disableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.1f);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.disableTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);

            final int fragBefore = GLStateManager.ctx().fragmentGeneration;
            GLStateManager.popStateTo(d);

            assertEquals(fragBefore + 1, GLStateManager.ctx().fragmentGeneration, "CUTOUT pop must bump fragment generation once when the value changed inside the bracket");

            verifyIsEnabled(GL11.GL_ALPHA_TEST, true, "Alpha test enable - restored by CUTOUT pop");
            assertEquals(GL11.GL_GREATER, GLStateManager.getAlphaState().getFunction(), "cache alpha func");
            assertEquals(0.5f, GLStateManager.getAlphaState().getReference(), 0.0001f, "cache alpha ref");
            assertTrue(GLStateManager.getTextures().getTextureUnitStates(1).isEnabled(), "unit 1 texturing - restored by CUTOUT pop");

            verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - not a CUTOUT member, must stay toggled");
            assertEquals(GL11.GL_DST_COLOR, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb - not a CUTOUT member, must stay toggled");
            assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb - not a CUTOUT member, must stay toggled");
        } finally {
            GLStateManager.disableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.disableTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void cullFaceModeRestoredThroughCullBracket() {
        try {
            GLStateManager.glCullFace(GL11.GL_BACK);
            final int d = GLStateManager.pushState(StateSet.CULL);
            GLStateManager.glCullFace(GL11.GL_FRONT);
            GLStateManager.popStateTo(d);

            assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode");
            assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode");
        } finally {
            GLStateManager.glCullFace(GL11.GL_BACK);
        }
    }

    @Test
    void cullFaceModeRestoredThroughBatchBracketRegression2117() {
        try {
            GLStateManager.glCullFace(GL11.GL_BACK);
            final int d = GLStateManager.pushState(StateSet.BATCH);
            GLStateManager.glCullFace(GL11.GL_FRONT);
            GLStateManager.popStateTo(d);

            assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode");
            assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode");
        } finally {
            GLStateManager.glCullFace(GL11.GL_BACK);
        }
    }

    @Test
    void batchRestoresUnit0And1BindingsAndActiveUnitButNotUnit2() {
        if (GLStateManager.MAX_TEXTURE_UNITS < 3) return;
        final int tex0Base = GLStateManager.glGenTextures();
        final int tex1Base = GLStateManager.glGenTextures();
        final int tex2Base = GLStateManager.glGenTextures();
        final int tex0Dirty = GLStateManager.glGenTextures();
        final int tex1Dirty = GLStateManager.glGenTextures();
        final int tex2Dirty = GLStateManager.glGenTextures();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex0Base);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex1Base);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + 2);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex2Base);

            final int d = GLStateManager.pushState(StateSet.BATCH);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex0Dirty);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex1Dirty);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + 2);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex2Dirty);
            GLStateManager.popStateTo(d);

            assertEquals(2, GLStateManager.getActiveTextureUnit(), "cache active unit - restored by BATCH pop");
            assertEquals(GL13.GL_TEXTURE0 + 2, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit - restored by BATCH pop");

            assertEquals(tex0Base, GLStateManager.getTextures().getTextureUnitBindings(0).getBinding(), "cache unit 0 binding - restored by BATCH pop");
            assertEquals(tex0Base, driverTextureBinding(0), "driver unit 0 binding - restored by BATCH pop");
            assertEquals(tex1Base, GLStateManager.getTextures().getTextureUnitBindings(1).getBinding(), "cache unit 1 binding - restored by BATCH pop");
            assertEquals(tex1Base, driverTextureBinding(1), "driver unit 1 binding - restored by BATCH pop");

            assertEquals(tex2Dirty, GLStateManager.getTextures().getTextureUnitBindings(2).getBinding(), "cache unit 2 binding - not a BATCH member, must stay toggled");
            assertEquals(tex2Dirty, driverTextureBinding(2), "driver unit 2 binding - not a BATCH member, must stay toggled");
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + 2);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDeleteTextures(tex0Base);
            GLStateManager.glDeleteTextures(tex1Base);
            GLStateManager.glDeleteTextures(tex2Base);
            GLStateManager.glDeleteTextures(tex0Dirty);
            GLStateManager.glDeleteTextures(tex1Dirty);
            GLStateManager.glDeleteTextures(tex2Dirty);
        }
    }

    @Test
    void popStateToRepairsUnbalancedInnerPush() {
        try {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            final int d = GLStateManager.pushState(StateSet.BLEND);
            GLStateManager.pushState(StateSet.CULL);
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.popStateTo(d);
            assertEquals(d, GLStateManager.getAttribDepth());
            assertEquals(GL11.GL_ONE, GLStateManager.getBlendState().getSrcRgb(), "popStateTo repairs the unbalanced non-member push and carries the change through to the BLEND member pop, restoring the pre-bracket baseline");
        } finally {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void popStateToToleratesUnbalancedInnerPop() {
        final int d = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.popState();
        assertEquals(d, GLStateManager.getAttribDepth());
        GLStateManager.popStateTo(d);
        assertEquals(d, GLStateManager.getAttribDepth());
    }

    @Test
    void exceptionMidBracketLeavesDepthAndStateCorrectViaFinally() {
        try {
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            final int depthBefore = GLStateManager.getAttribDepth();

            final RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
                final int d = GLStateManager.pushState(StateSet.BLEND);
                try {
                    GLStateManager.disableBlend();
                    GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
                    throw new RuntimeException("boom");
                } finally {
                    GLStateManager.popStateTo(d);
                }
            });

            assertEquals("boom", thrown.getMessage());
            assertEquals(depthBefore, GLStateManager.getAttribDepth());
            verifyIsEnabled(GL11.GL_BLEND, true, "Blend enable - restored despite exception");
            assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb - restored despite exception");
        } finally {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void internalPushDuringDisplayListCompileThrowsAndLeavesDepthUnchanged() {
        final int list = GLStateManager.glGenLists(1);
        final int depthBefore = GLStateManager.getAttribDepth();
        try {
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            assertThrows(IllegalStateException.class, () -> GLStateManager.pushState(StateSet.BLEND));
            GLStateManager.pushState(StateSet.forMask(GL11.GL_COLOR_BUFFER_BIT));
            GLStateManager.popState();
            assertEquals(depthBefore, GLStateManager.getAttribDepth(), "mask-based push/pop is recorded, not executed, and leaves live depth unchanged");
        } finally {
            GLStateManager.glEndList();
            GLStateManager.glDeleteLists(list, 1);
        }
    }

    @Test
    void allAttribBitsNestedInsideInternalBracketRestoresBothLevels() {
        try {
            GLStateManager.enableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);

            final int d = GLStateManager.pushState(StateSet.BLEND);
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GLStateManager.glCullFace(GL11.GL_FRONT);
            GLStateManager.glPopAttrib();

            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull enable - restored by inner GL_ALL_ATTRIB_BITS pop");
            assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode - restored by inner GL_ALL_ATTRIB_BITS pop");
            assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - restored by inner GL_ALL_ATTRIB_BITS pop");

            GLStateManager.popStateTo(d);

            verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - restored by outer BLEND pop");
            assertEquals(GL11.GL_ONE, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb - restored by outer BLEND pop");
        } finally {
            GLStateManager.disableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void internalBracketNestedInsideAllAttribBitsRestoresBothLevels() {
        try {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.enableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);

            GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                GLStateManager.glCullFace(GL11.GL_FRONT);

                final int d = GLStateManager.pushState(StateSet.BLEND);
                GLStateManager.enableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                GLStateManager.popStateTo(d);

                verifyIsEnabled(GL11.GL_BLEND, false, "Blend enable - restored by inner BLEND pop");
                assertEquals(GL11.GL_ONE, GLStateManager.getBlendState().getSrcRgb(), "cache blend src rgb - restored by inner BLEND pop");
                assertEquals(GL11.GL_FRONT, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode - change survives, not a BLEND member");
                assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - change survives, not a BLEND member");
            } finally {
                GLStateManager.glPopAttrib();
            }

            assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face mode - restored by outer GL_ALL_ATTRIB_BITS pop");
            assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode - restored by outer GL_ALL_ATTRIB_BITS pop");
        } finally {
            GLStateManager.disableCull();
            GLStateManager.glCullFace(GL11.GL_BACK);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void rProgramFontRestoresRealProgramToDriverAndCache() {
        final int p0 = linkTrivialProgram();
        final int p1 = linkTrivialProgram();
        try {
            GLStateManager.glUseProgram(p0);

            final int d = GLStateManager.pushState(StateSet.FONT);
            GLStateManager.glUseProgram(p1);
            GLStateManager.popStateTo(d);

            assertEquals(p0, GLStateManager.getActiveProgram(), "cache active program restored");
            assertEquals(p0, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), "driver current program restored");
            assertFalse(ShaderManager.getInstance().isActive(), "FFP must stay inactive when a real program was bound before the bracket");
        } finally {
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteProgram(p0);
            GLStateManager.glDeleteProgram(p1);
        }
    }

    @Test
    void rProgramFontReactivatesFfpAfterBracket() {
        final int p1 = linkTrivialProgram();
        try {
            GLStateManager.glUseProgram(0);
            assertTrue(ShaderManager.getInstance().isActive(), "FFP must be active before the bracket");

            final int d = GLStateManager.pushState(StateSet.FONT);
            GLStateManager.glUseProgram(p1);
            assertFalse(ShaderManager.getInstance().isActive(), "FFP must deactivate once a real program binds");
            GLStateManager.popStateTo(d);

            assertEquals(0, GLStateManager.getActiveProgram(), "cache active program restored to the FFP sentinel");
            assertTrue(ShaderManager.getInstance().isActive(), "FFP must be active again after the pop");
        } finally {
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteProgram(p1);
        }
    }

    @Test
    void blendPopDoesNotBumpFragmentOrLightingGeneration() {
        try {
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            final int d = GLStateManager.pushState(StateSet.BLEND);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);

            final int fragBefore = GLStateManager.ctx().fragmentGeneration;
            final int lightBefore = GLStateManager.ctx().lightingGeneration;
            GLStateManager.popStateTo(d);

            assertEquals(fragBefore, GLStateManager.ctx().fragmentGeneration, "BLEND pop must not bump fragment generation");
            assertEquals(lightBefore, GLStateManager.ctx().lightingGeneration, "BLEND pop must not bump lighting generation");
        } finally {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void alphaPopBumpsFragmentGenerationWhenChanged() {
        try {
            GLStateManager.enableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);

            final int d = GLStateManager.pushState(StateSet.ALPHA);
            GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.1f);

            final int fragBefore = GLStateManager.ctx().fragmentGeneration;
            GLStateManager.popStateTo(d);

            assertEquals(fragBefore + 1, GLStateManager.ctx().fragmentGeneration, "ALPHA pop must bump fragment generation once when the value changed inside the bracket");
        } finally {
            GLStateManager.disableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
        }
    }

    @Test
    void textureBitBracketRestoresUnit1BindingAndActiveUnit() {
        final int texBase = GLStateManager.glGenTextures();
        final int texDirty = GLStateManager.glGenTextures();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texBase);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            final int d = GLStateManager.pushState(StateSet.forMask(GL11.GL_TEXTURE_BIT));
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texDirty);
            GLStateManager.popStateTo(d);
            assertEquals(0, GLStateManager.getActiveTextureUnit(), "cache active unit restored by GL_TEXTURE_BIT pop");
            assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit restored by GL_TEXTURE_BIT pop");
            assertEquals(texBase, GLStateManager.getTextures().getTextureUnitBindings(1).getBinding(), "cache unit 1 binding restored by GL_TEXTURE_BIT pop");
            assertEquals(texBase, driverTextureBinding(1), "driver unit 1 binding restored by GL_TEXTURE_BIT pop");
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDeleteTextures(texBase);
            GLStateManager.glDeleteTextures(texDirty);
        }
    }

    @Test
    void textureBitBracketRestoresTexEnvModeAndColor() {
        GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        final FloatBuffer zero = BufferUtils.createFloatBuffer(4);
        zero.put(0.0f).put(0.0f).put(0.0f).put(0.0f).flip();
        GLStateManager.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, zero);
        try {
            final int d = GLStateManager.pushState(StateSet.forMask(GL11.GL_TEXTURE_BIT));
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_DECAL);
            final FloatBuffer dirty = BufferUtils.createFloatBuffer(4);
            dirty.put(0.1f).put(0.2f).put(0.3f).put(0.4f).flip();
            GLStateManager.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, dirty);
            GLStateManager.popStateTo(d);
            assertEquals(GL11.GL_MODULATE, GLStateManager.getTextures().getTexEnvState(0).mode, "cache tex env mode restored by GL_TEXTURE_BIT pop");
            assertEquals(0.0f, GLStateManager.getTextures().getTexEnvState(0).envColorR, 0.0001f, "cache tex env color restored by GL_TEXTURE_BIT pop");
        } finally {
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        }
    }

    @Test
    void lightingBitBracketRestoresBakedFrontMaterialAfterColorMaterialToggle() {
        try {
            GLStateManager.glColor4f(0.9f, 0.9f, 0.9f, 0.9f);
            GLStateManager.enableColorMaterial();
            GLStateManager.disableColorMaterial();
            final int d = GLStateManager.pushState(StateSet.forMask(GL11.GL_LIGHTING_BIT));
            GLStateManager.glColor4f(0.1f, 0.2f, 0.3f, 0.4f);
            GLStateManager.enableColorMaterial();
            GLStateManager.popStateTo(d);
            assertEquals(0.9f, GLStateManager.getFrontMaterial().ambient.x, 0.0001f, "cache front material ambient restored by GL_LIGHTING_BIT pop");
            assertEquals(0.9f, GLStateManager.getFrontMaterial().diffuse.x, 0.0001f, "cache front material diffuse restored by GL_LIGHTING_BIT pop");
        } finally {
            GLStateManager.disableColorMaterial();
            GLStateManager.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        }
    }
}
