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
            fixtureTexA = GLStateManager.glGenTextures();
            fixtureTexB = GLStateManager.glGenTextures();
        }
    }

    @AfterAll
    static void deleteFixtureTextures() {
        if (fixtureTexA > 0) GLStateManager.glDeleteTextures(fixtureTexA);
        if (fixtureTexB > 0) GLStateManager.glDeleteTextures(fixtureTexB);
        fixtureTexA = fixtureTexB = -1;
    }

    private static final String[] NAMES = {
        "glAlphaFunc", "glBlendFunc", "tryBlendFuncSeparate", "glBlendEquation", "glBlendEquationSeparate",
        "glBlendColor", "glColorMask", "glClearColor", "glDrawBuffer", "glLogicOp",
        "glDepthFunc", "glDepthMask", "glClearDepth", "glStencilFunc", "glStencilOp",
        "glStencilMask", "glClearStencil", "glViewport", "glDepthRange", "glLineWidth",
        "glLineStipple", "glPointSize", "glPolygonMode", "glCullFace", "glFrontFace",
        "glPolygonOffset", "fogColor", "glFogf", "glFogi", "glLightf",
        "glLighti", "glLight", "glLightModelf", "glLightModeli", "glLightModel",
        "glMaterialf", "glMateriali", "glMaterial", "glShadeModel", "glColorMaterial",
        "glMatrixMode", "glActiveTexture", "glBindTexture", "glTexEnvi", "glTexEnv",
        "glSecondaryColor3ub", "enableBlend/disableBlend", "enableAlphaTest/disableAlphaTest",
        "glEnable(GL_CULL_FACE)", "dsaBindTextureToUnit"
    };

    private static final Runnable[] MUTATIONS = {
        () -> GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.1f),
        () -> GLStateManager.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO),
        () -> GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO),
        () -> GLStateManager.glBlendEquation(GL14.GL_FUNC_SUBTRACT),
        () -> GLStateManager.glBlendEquationSeparate(GL14.GL_FUNC_SUBTRACT, GL14.GL_FUNC_REVERSE_SUBTRACT),
        () -> GLStateManager.glBlendColor(0.5f, 0.6f, 0.7f, 0.8f),
        () -> GLStateManager.glColorMask(false, true, false, true),
        () -> GLStateManager.glClearColor(1.0f, 0.5f, 0.25f, 0.75f),
        () -> GLStateManager.glDrawBuffer(GL11.GL_NONE),
        () -> GLStateManager.glLogicOp(GL11.GL_XOR),
        () -> GLStateManager.glDepthFunc(GL11.GL_ALWAYS),
        () -> GLStateManager.glDepthMask(false),
        () -> GLStateManager.glClearDepth(0.25),
        () -> GLStateManager.glStencilFunc(GL11.GL_NOTEQUAL, 3, 0x0F),
        () -> GLStateManager.glStencilOp(GL11.GL_REPLACE, GL11.GL_INCR, GL11.GL_DECR),
        () -> GLStateManager.glStencilMask(0x0F),
        () -> GLStateManager.glClearStencil(9),
        () -> GLStateManager.glViewport(1, 2, 3, 4),
        () -> GLStateManager.glDepthRange(0.2, 0.8),
        () -> GLStateManager.glLineWidth(3.0f),
        () -> GLStateManager.glLineStipple(3, (short) 0x00FF),
        () -> GLStateManager.glPointSize(4.0f),
        () -> GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE),
        () -> GLStateManager.glCullFace(GL11.GL_FRONT),
        () -> GLStateManager.glFrontFace(GL11.GL_CW),
        () -> GLStateManager.glPolygonOffset(3.0f, 4.0f),
        () -> GLStateManager.fogColor(0.5f, 0.6f, 0.7f, 0.8f),
        () -> GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.5f),
        () -> GLStateManager.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR),
        () -> GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, 12.0f),
        () -> GLStateManager.glLighti(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, 45),
        () -> GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, buf4(1.0f, 2.0f, 3.0f, 1.0f)),
        () -> GLStateManager.glLightModelf(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 1.0f),
        () -> GLStateManager.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR),
        () -> GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, buf4(0.5f, 0.6f, 0.7f, 0.8f)),
        () -> GLStateManager.glMaterialf(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, 32.0f),
        () -> GLStateManager.glMateriali(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, 32),
        () -> GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE, buf4(0.1f, 0.2f, 0.3f, 0.4f)),
        () -> GLStateManager.glShadeModel(GL11.GL_FLAT),
        () -> GLStateManager.glColorMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR),
        () -> GLStateManager.glMatrixMode(GL11.GL_PROJECTION),
        () -> GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + 2),
        () -> {
            ensureFixtureTextures();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, fixtureTexA);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        },
        () -> GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_DECAL),
        () -> GLStateManager.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, buf4(0.1f, 0.2f, 0.3f, 0.4f)),
        () -> GLStateManager.glSecondaryColor3ub((byte) 255, (byte) 128, (byte) 64),
        () -> {
            GLStateManager.disableBlend();
            GLStateManager.enableBlend();
        },
        () -> {
            GLStateManager.disableAlphaTest();
            GLStateManager.enableAlphaTest();
        },
        () -> {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.glEnable(GL11.GL_CULL_FACE);
        },
        () -> {
            assumeTrue(GLStateManager.capabilities.GL_ARB_direct_state_access, "test requires GL_ARB_direct_state_access");
            ensureFixtureTextures();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, fixtureTexA);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            new DSAARB().bindTextureToUnit(1, fixtureTexB);
        }
    };

    @Test
    void everyMutationSiteReachesTheSaveHook() {
        final int entryDepth = GLStateManager.getAttribDepth();
        try {
            for (int i = 0; i < MUTATIONS.length; i++) {
                final int d = GLStateManager.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
                try {
                    final long before = GLStateManager.attribSlotsSaved;
                    MUTATIONS[i].run();
                    assertTrue(GLStateManager.attribSlotsSaved - before >= 1, NAMES[i] + ": mutation site did not reach the save hook");
                } finally {
                    GLStateManager.popStateTo(d);
                }
            }
        } finally {
            GLStateManager.popStateTo(entryDepth);
        }
    }
}
