package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.iris.IrisDisplayListState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@GLCoreTest
class BatchStateGuardGLTest {
    private final AtomicInteger flushes = new AtomicInteger();
    private Runnable previousFlush;

    @BeforeEach
    void saveState() {
        previousFlush = BatchStateGuard.flush;
        BatchStateGuard.flush = null;
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @AfterEach
    void restoreState() {
        BatchStateGuard.flush = null;
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glPopMatrix();
        GLSMAttribStack.glPopAttrib();
        BatchStateGuard.flush = previousFlush;
    }

    private void countFlushes() {
        BatchStateGuard.flush = flushes::incrementAndGet;
    }

    private static final Runnable NOTHING = () -> { };

    private void assertFlushes(Runnable setup, Runnable atFlush, Runnable action, int expected, Runnable cleanup) {
        final int drawBuffer = GLStateManager.ctx().drawBuffer.getValue();
        setup.run();
        BatchStateGuard.flush = () -> {
            atFlush.run();
            flushes.incrementAndGet();
        };
        try {
            action.run();
            assertEquals(expected, flushes.get());
        } finally {
            BatchStateGuard.flush = null;
            cleanup.run();
            GLSMFramebuffer.glDrawBuffer(drawBuffer);
        }
    }

    static Stream<Arguments> flushCases() {
        return Stream.of(
            Arguments.of("fog", (Runnable) () -> GLSMFog.glFogf(GL11.GL_FOG_START, 17),
                (Runnable) () -> assertEquals(17f, GLSMFog.getFogState().getStart()),
                (Runnable) () -> {
                    GLSMFog.glFogf(GL11.GL_FOG_START, 29);
                    assertEquals(29f, GLSMFog.getFogState().getStart());
                }, 1, NOTHING),
            Arguments.of("stencil", (Runnable) () -> GLSMEnable.glDisable(GL11.GL_STENCIL_TEST),
                (Runnable) () -> assertFalse(GLSMEnable.glIsEnabled(GL11.GL_STENCIL_TEST)),
                (Runnable) () -> GLSMEnable.glEnable(GL11.GL_STENCIL_TEST), 1, NOTHING),
            Arguments.of("polygon mode", NOTHING,
                (Runnable) () -> assertEquals(GL11.GL_FILL, GLSMRasterizer.getPolygonState().getFrontMode()),
                (Runnable) () -> {
                    GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                    GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                }, 1, NOTHING),
            Arguments.of("depth range", NOTHING,
                (Runnable) () -> assertEquals(1.0, GLSMRasterizer.getViewportState().depthRangeFar),
                (Runnable) () -> {
                    GLSMDepthStencilAlpha.glDepthRange(0.0, 0.5);
                    GLSMDepthStencilAlpha.glDepthRange(0.0, 0.5);
                }, 1, NOTHING),
            Arguments.of("logic op and color sum", NOTHING, NOTHING,
                (Runnable) () -> {
                    GLSMEnable.glEnable(GL11.GL_COLOR_LOGIC_OP);
                    GLSMBlend.glLogicOp(GL11.GL_XOR);
                    GLSMEnable.glEnable(GL14.GL_COLOR_SUM);
                }, 3, NOTHING),
            Arguments.of("sample and discard state", NOTHING, NOTHING,
                (Runnable) () -> {
                    GLSMEnable.glEnable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
                    GLSMEnable.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
                    GLSMEnable.glEnable(GL30.GL_RASTERIZER_DISCARD);
                    GLSMEnable.glDisable(GL30.GL_RASTERIZER_DISCARD);
                    GLSMRasterizer.glSampleCoverage(0.5f, false);
                }, 5, (Runnable) () -> GLSMRasterizer.glSampleCoverage(1.0f, false)),
            Arguments.of("draw buffer", NOTHING, NOTHING,
                (Runnable) () -> {
                    GLSMFramebuffer.glDrawBuffer(GL11.GL_NONE);
                    GLSMFramebuffer.glDrawBuffer(GL11.GL_NONE);
                }, 1, NOTHING),
            Arguments.of("draw buffers", NOTHING, NOTHING, (Runnable) () -> GLSMFramebuffer.glDrawBuffers(GL11.GL_NONE), 1, NOTHING),
            Arguments.of("projection matrix", (Runnable) () -> GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION),
                (Runnable) () -> {
                    assertEquals(1f, GLSMMatrices.getProjectionMatrix().m00());
                    GLSMRasterizer.glScissor(0, 0, 8, 8);
                },
                (Runnable) () -> {
                    GLSMMatrices.glScalef(2, 3, 1);
                    assertEquals(2f, GLSMMatrices.getProjectionMatrix().m00());
                }, 1, NOTHING),
            Arguments.of("texture matrix unit 2", (Runnable) () -> {
                    GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
                    GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
                    GLSMMatrices.glPushMatrix();
                    GLSMMatrices.glLoadIdentity();
                },
                (Runnable) () -> assertEquals(0f, GLSMTextures.getTextures().getTextureUnitMatrix(2).m30()),
                (Runnable) () -> GLSMMatrices.glTranslatef(0.5f, 0, 0), 1,
                (Runnable) () -> {
                    GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
                    GLSMMatrices.glPopMatrix();
                }),
            Arguments.of("texture matrix unit 0 stays instanced", (Runnable) () -> {
                    GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
                    GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
                }, NOTHING,
                (Runnable) () -> {
                    GLSMMatrices.glPushMatrix();
                    GLSMMatrices.glTranslatef(0.5f, 0, 0);
                    GLSMMatrices.glPopMatrix();
                }, 0, NOTHING));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("flushCases")
    void changesFlushBeforeMutatingAndOnlyWhenChanged(String name, Runnable setup, Runnable atFlush, Runnable action, int expected, Runnable cleanup) {
        assertFlushes(setup, atFlush, action, expected, cleanup);
    }

    static Stream<Arguments> undrainedCases() {
        return Stream.of(
            Arguments.of("matrices color and blend", (Runnable) () -> {
                GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
                GLSMMatrices.glPushMatrix();
                GLSMMatrices.glTranslatef(1, 2, 3);
                GLSMMatrices.glPopMatrix();
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
                GLSMCurrentAttribs.glColor4f(0.5f, 1, 1, 1);
                GLSMBlend.enableBlend();
                GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }),
            Arguments.of("managed pass setup", (Runnable) () -> IrisDisplayListState.runProgramTransition(() -> {
                GLSMFog.glFogf(GL11.GL_FOG_START, 123);
                GLSMRasterizer.glViewport(0, 0, 8, 8);
            })),
            Arguments.of("lighting", (Runnable) () -> {
                GLSMEnable.glEnable(GL11.GL_LIGHT0);
                GLSMEnable.glDisable(GL11.GL_LIGHT0);
                GLSMEnable.glEnable(GL11.GL_COLOR_MATERIAL);
                GLSMEnable.glDisable(GL11.GL_COLOR_MATERIAL);
                GLSMEnable.glEnable(GL11.GL_NORMALIZE);
                GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 2.0f);
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undrainedCases")
    void commonCapturedStateAndManagedPassSetupDoNotDrainBatches(String name, Runnable action) {
        countFlushes();
        action.run();
        assertEquals(0, flushes.get());
        assertFalse(BatchStateGuard.isSuspended());
    }

    @Test
    void extraTextureBindingsAndAttributeRestoresCannotOvertakeQueuedDraws() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMAttribStack.glPushAttrib(GL11.GL_TEXTURE_BIT);
        final int texture = GLSMTextures.glGenTextures();
        try {
                BatchStateGuard.flush = () -> {
                assertEquals(0, GLSMTextures.getBoundTextureForServerState(2));
                flushes.incrementAndGet();
            };
            RenderSystem.bindTextureToUnit(GL11.GL_TEXTURE_2D, 2, texture);
            assertTrue(flushes.get() > 0);
            flushes.set(0);
            BatchStateGuard.flush = () -> {
                assertEquals(texture, GLSMTextures.getBoundTextureForServerState(2));
                flushes.incrementAndGet();
            };
            GLSMAttribStack.glPopAttrib();
            assertTrue(flushes.get() > 0);
            assertEquals(0, GLSMTextures.getBoundTextureForServerState(2));
        } finally {
            BatchStateGuard.flush = null;
            GLSMTextures.glDeleteTextures(texture);
        }
    }

    @Test
    void compileOnlyDefersTheBarrierUntilPlayback() {
        final int list = GLSMDraws.glGenLists(1);
        countFlushes();
        try {
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMRasterizer.glScissor(1, 2, 3, 4);
            GLSMDraws.glEndList();
            assertEquals(0, flushes.get());
            GLSMDraws.glCallList(list);
            assertEquals(1, flushes.get());
        } finally {
            GLSMDraws.glDeleteLists(list, 1);
        }
    }

    @Test
    void perUnitCapsFlushExceptTheTexturesBatchLayersOwn() {
        countFlushes();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
            GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
            GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
            assertEquals(0, flushes.get(), "albedo and lightmap enables are stored with each layer");

            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
            flushes.set(0);
            BatchStateGuard.flush = () -> {
                assertFalse(GLSMTextures.getTextures().getTextureUnitStates(2).isEnabled());
                flushes.incrementAndGet();
            };
            GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
            assertEquals(1, flushes.get());
            GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
            assertEquals(1, flushes.get(), "unchanged enable does not flush");

            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_S);
            flushes.set(0);
            countFlushes();
            GLSMEnable.glEnable(GL11.GL_TEXTURE_GEN_S);
            assertEquals(1, flushes.get());
        } finally {
            BatchStateGuard.flush = null;
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void secondaryColorFlushesOnlyWhileColorSumIsOn() {
        GLSMCurrentAttribs.glSecondaryColor3f(0f, 0f, 0f);
        countFlushes();
        try {
            GLSMCurrentAttribs.glSecondaryColor3f(0.5f, 0f, 0f);
            assertEquals(0, flushes.get(), "secondary color is unused while color sum is off");
            GLSMEnable.glEnable(GL14.GL_COLOR_SUM);
            flushes.set(0);
            GLSMCurrentAttribs.glSecondaryColor3f(0.25f, 0f, 0f);
            assertEquals(1, flushes.get());
        } finally {
            BatchStateGuard.flush = null;
            GLSMEnable.glDisable(GL14.GL_COLOR_SUM);
            GLSMCurrentAttribs.glSecondaryColor3f(0f, 0f, 0f);
        }
    }
}
