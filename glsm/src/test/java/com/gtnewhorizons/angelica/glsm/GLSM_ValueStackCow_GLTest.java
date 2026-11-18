package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;

import com.gtnewhorizons.angelica.glsm.dsa.DSAARB;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_ValueStackCow_GLTest {

    private static int fixtureTexA = -1;
    private static int fixtureTexB = -1;

    private static FloatBuffer buf4(float a, float b, float c, float d) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(4);
        buf.put(a).put(b).put(c).put(d).flip();
        return buf;
    }

    private static void ensureFixtureTextures() {
        if (fixtureTexA == -1) {
            fixtureTexA = GLSMTextures.glGenTextures();
            fixtureTexB = GLSMTextures.glGenTextures();
            final int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fixtureTexB);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    @AfterAll
    static void deleteFixtureTextures() {
        if (fixtureTexA > 0) GLSMTextures.glDeleteTextures(fixtureTexA);
        if (fixtureTexB > 0) GLSMTextures.glDeleteTextures(fixtureTexB);
        fixtureTexA = fixtureTexB = -1;
    }

    private static final String[] NAMES = {
        "glAlphaFunc", "glBlendFunc", "tryBlendFuncSeparate", "glBlendEquation", "glBlendEquationSeparate",
        "glBlendColor", "glColorMask", "glClearColor", "glDrawBuffer", "glLogicOp",
        "glDepthFunc", "glDepthMask", "glClearDepth", "glStencilFunc", "glStencilOp",
        "glStencilMask", "glClearStencil", "glViewport", "glScissor", "glDepthRange", "glLineWidth",
        "glLineStipple", "glPointSize", "glPolygonMode", "glCullFace", "glFrontFace",
        "glPolygonOffset", "fogColor", "glFogf", "glFogi", "glLightf",
        "glLighti", "glLight", "glLightModelf", "glLightModeli", "glLightModel",
        "glMaterialf", "glMateriali", "glMaterial", "glShadeModel", "glColorMaterial",
        "glMatrixMode", "glActiveTexture", "glBindTexture", "glTexEnvi", "glTexEnv",
        "glSecondaryColor3ub", "enableBlend/disableBlend", "enableAlphaTest/disableAlphaTest",
        "glEnable(GL_CULL_FACE)", "dsaBindTextureToUnit"
    };

    private static final Runnable[] MUTATIONS = {
        () -> GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.1f),
        () -> GLSMBlend.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO),
        () -> GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO),
        () -> GLSMBlend.glBlendEquation(GL14.GL_FUNC_SUBTRACT),
        () -> GLSMBlend.glBlendEquationSeparate(GL14.GL_FUNC_SUBTRACT, GL14.GL_FUNC_REVERSE_SUBTRACT),
        () -> GLSMBlend.glBlendColor(0.5f, 0.6f, 0.7f, 0.8f),
        () -> GLSMBlend.glColorMask(false, true, false, true),
        () -> GLSMBlend.glClearColor(1.0f, 0.5f, 0.25f, 0.75f),
        () -> GLSMFramebuffer.glDrawBuffer(GL11.GL_NONE),
        () -> GLSMBlend.glLogicOp(GL11.GL_XOR),
        () -> GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS),
        () -> GLSMDepthStencilAlpha.glDepthMask(false),
        () -> GLSMDepthStencilAlpha.glClearDepth(0.25),
        () -> GLSMDepthStencilAlpha.glStencilFunc(GL11.GL_NOTEQUAL, 3, 0x0F),
        () -> GLSMDepthStencilAlpha.glStencilOp(GL11.GL_REPLACE, GL11.GL_INCR, GL11.GL_DECR),
        () -> GLSMDepthStencilAlpha.glStencilMask(0x0F),
        () -> GLSMDepthStencilAlpha.glClearStencil(9),
        () -> GLSMRasterizer.glViewport(1, 2, 3, 4),
        () -> GLSMRasterizer.glScissor(1, 2, 3, 4),
        () -> GLSMDepthStencilAlpha.glDepthRange(0.2, 0.8),
        () -> GLSMRasterizer.glLineWidth(3.0f),
        () -> GLSMRasterizer.glLineStipple(3, (short) 0x00FF),
        () -> GLSMRasterizer.glPointSize(4.0f),
        () -> GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE),
        () -> GLSMRasterizer.glCullFace(GL11.GL_FRONT),
        () -> GLSMRasterizer.glFrontFace(GL11.GL_CW),
        () -> GLSMRasterizer.glPolygonOffset(3.0f, 4.0f),
        () -> GLSMFog.fogColor(0.5f, 0.6f, 0.7f, 0.8f),
        () -> GLSMFog.glFogf(GL11.GL_FOG_DENSITY, 0.5f),
        () -> GLSMFog.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR),
        () -> GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, 12.0f),
        () -> GLSMLighting.glLighti(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, 45),
        () -> GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, buf4(1.0f, 2.0f, 3.0f, 1.0f)),
        () -> GLSMLighting.glLightModelf(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 1.0f),
        () -> GLSMLighting.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR),
        () -> GLSMLighting.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, buf4(0.5f, 0.6f, 0.7f, 0.8f)),
        () -> GLSMLighting.glMaterialf(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, 32.0f),
        () -> GLSMLighting.glMateriali(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, 32),
        () -> GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE, buf4(0.1f, 0.2f, 0.3f, 0.4f)),
        () -> GLSMRasterizer.glShadeModel(GL11.GL_FLAT),
        () -> GLSMLighting.glColorMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR),
        () -> GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION),
        () -> GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + 2),
        () -> {
            ensureFixtureTextures();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, fixtureTexA);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        },
        () -> GLSMTexEnvGen.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_DECAL),
        () -> GLSMTexEnvGen.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, buf4(0.1f, 0.2f, 0.3f, 0.4f)),
        () -> GLSMCurrentAttribs.glSecondaryColor3ub((byte) 255, (byte) 128, (byte) 64),
        () -> {
            GLSMBlend.disableBlend();
            GLSMBlend.enableBlend();
        },
        () -> {
            GLSMDepthStencilAlpha.disableAlphaTest();
            GLSMDepthStencilAlpha.enableAlphaTest();
        },
        () -> {
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
            GLSMEnable.glEnable(GL11.GL_CULL_FACE);
        },
        () -> {
            assumeTrue(GLStateManager.capabilities.GL_ARB_direct_state_access, "test requires GL_ARB_direct_state_access");
            ensureFixtureTextures();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, fixtureTexA);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            new DSAARB().bindTextureToUnit(1, fixtureTexB);
        }
    };

    @Test
    void everyMutationSiteReachesTheSaveHook() {
        final int entryDepth = GLSMAttribStack.getAttribDepth();
        try {
            for (int i = 0; i < MUTATIONS.length; i++) {
                final int d = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
                try {
                    final long before = GLSMAttribStack.attribSlotsSaved;
                    MUTATIONS[i].run();
                    assertTrue(GLSMAttribStack.attribSlotsSaved - before >= 1, NAMES[i] + ": mutation site did not reach the save hook");
                } finally {
                    GLSMAttribStack.popStateTo(d);
                }
            }
        } finally {
            GLSMAttribStack.popStateTo(entryDepth);
        }
    }
}
