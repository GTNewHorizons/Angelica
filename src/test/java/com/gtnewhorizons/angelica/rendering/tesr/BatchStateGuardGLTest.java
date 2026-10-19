package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
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
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @AfterEach
    void restoreState() {
        BatchStateGuard.flush = null;
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPopMatrix();
        GLStateManager.glPopAttrib();
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
            GLStateManager.glDrawBuffer(drawBuffer);
        }
    }

    static Stream<Arguments> flushCases() {
        return Stream.of(
            Arguments.of("fog", (Runnable) () -> GLStateManager.glFogf(GL11.GL_FOG_START, 17),
                (Runnable) () -> assertEquals(17f, GLStateManager.getFogState().getStart()),
                (Runnable) () -> {
                    GLStateManager.glFogf(GL11.GL_FOG_START, 29);
                    assertEquals(29f, GLStateManager.getFogState().getStart());
                }, 1, NOTHING),
            Arguments.of("stencil", (Runnable) () -> GLStateManager.glDisable(GL11.GL_STENCIL_TEST),
                (Runnable) () -> assertFalse(GLStateManager.glIsEnabled(GL11.GL_STENCIL_TEST)),
                (Runnable) () -> GLStateManager.glEnable(GL11.GL_STENCIL_TEST), 1, NOTHING),
            Arguments.of("polygon mode", NOTHING,
                (Runnable) () -> assertEquals(GL11.GL_FILL, GLStateManager.getPolygonState().getFrontMode()),
                (Runnable) () -> {
                    GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                    GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                }, 1, NOTHING),
            Arguments.of("depth range", NOTHING,
                (Runnable) () -> assertEquals(1.0, GLStateManager.getViewportState().depthRangeFar),
                (Runnable) () -> {
                    GLStateManager.glDepthRange(0.0, 0.5);
                    GLStateManager.glDepthRange(0.0, 0.5);
                }, 1, NOTHING),
            Arguments.of("logic op and color sum", NOTHING, NOTHING,
                (Runnable) () -> {
                    GLStateManager.glEnable(GL11.GL_COLOR_LOGIC_OP);
                    GLStateManager.glLogicOp(GL11.GL_XOR);
                    GLStateManager.glEnable(GL14.GL_COLOR_SUM);
                }, 3, NOTHING),
            Arguments.of("sample and discard state", NOTHING, NOTHING,
                (Runnable) () -> {
                    GLStateManager.glEnable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
                    GLStateManager.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
                    GLStateManager.glEnable(GL30.GL_RASTERIZER_DISCARD);
                    GLStateManager.glDisable(GL30.GL_RASTERIZER_DISCARD);
                    GLStateManager.glSampleCoverage(0.5f, false);
                }, 5, (Runnable) () -> GLStateManager.glSampleCoverage(1.0f, false)),
            Arguments.of("draw buffer", NOTHING, NOTHING,
                (Runnable) () -> {
                    GLStateManager.glDrawBuffer(GL11.GL_NONE);
                    GLStateManager.glDrawBuffer(GL11.GL_NONE);
                }, 1, NOTHING),
            Arguments.of("draw buffers", NOTHING, NOTHING, (Runnable) () -> GLStateManager.glDrawBuffers(GL11.GL_NONE), 1, NOTHING),
            Arguments.of("projection matrix", (Runnable) () -> GLStateManager.glMatrixMode(GL11.GL_PROJECTION),
                (Runnable) () -> {
                    assertEquals(1f, GLStateManager.getProjectionMatrix().m00());
                    GLStateManager.glScissor(0, 0, 8, 8);
                },
                (Runnable) () -> {
                    GLStateManager.glScalef(2, 3, 1);
                    assertEquals(2f, GLStateManager.getProjectionMatrix().m00());
                }, 1, NOTHING),
            Arguments.of("texture matrix unit 2", (Runnable) () -> {
                    GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
                    GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
                    GLStateManager.glPushMatrix();
                    GLStateManager.glLoadIdentity();
                },
                (Runnable) () -> assertEquals(0f, GLStateManager.getTextures().getTextureUnitMatrix(2).m30()),
                (Runnable) () -> GLStateManager.glTranslatef(0.5f, 0, 0), 1,
                (Runnable) () -> {
                    GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
                    GLStateManager.glPopMatrix();
                }),
            Arguments.of("texture matrix unit 0 stays instanced", (Runnable) () -> {
                    GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
                    GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
                }, NOTHING,
                (Runnable) () -> {
                    GLStateManager.glPushMatrix();
                    GLStateManager.glTranslatef(0.5f, 0, 0);
                    GLStateManager.glPopMatrix();
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
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glPushMatrix();
                GLStateManager.glTranslatef(1, 2, 3);
                GLStateManager.glPopMatrix();
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
                GLStateManager.glColor4f(0.5f, 1, 1, 1);
                GLStateManager.enableBlend();
                GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }),
            Arguments.of("managed pass setup", (Runnable) () -> IrisDisplayListState.runProgramTransition(() -> {
                GLStateManager.glFogf(GL11.GL_FOG_START, 123);
                GLStateManager.glViewport(0, 0, 8, 8);
            })),
            Arguments.of("lighting", (Runnable) () -> {
                GLStateManager.glEnable(GL11.GL_LIGHT0);
                GLStateManager.glDisable(GL11.GL_LIGHT0);
                GLStateManager.glEnable(GL11.GL_COLOR_MATERIAL);
                GLStateManager.glDisable(GL11.GL_COLOR_MATERIAL);
                GLStateManager.glEnable(GL11.GL_NORMALIZE);
                GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 2.0f);
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
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        final int texture = GLStateManager.glGenTextures();
        try {
                BatchStateGuard.flush = () -> {
                assertEquals(0, GLStateManager.getBoundTextureForServerState(2));
                flushes.incrementAndGet();
            };
            RenderSystem.bindTextureToUnit(GL11.GL_TEXTURE_2D, 2, texture);
            assertTrue(flushes.get() > 0);
            flushes.set(0);
            BatchStateGuard.flush = () -> {
                assertEquals(texture, GLStateManager.getBoundTextureForServerState(2));
                flushes.incrementAndGet();
            };
            GLStateManager.glPopAttrib();
            assertTrue(flushes.get() > 0);
            assertEquals(0, GLStateManager.getBoundTextureForServerState(2));
        } finally {
            BatchStateGuard.flush = null;
            GLStateManager.glDeleteTextures(texture);
        }
    }

    @Test
    void compileOnlyDefersTheBarrierUntilPlayback() {
        final int list = GLStateManager.glGenLists(1);
        countFlushes();
        try {
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            GLStateManager.glScissor(1, 2, 3, 4);
            GLStateManager.glEndList();
            assertEquals(0, flushes.get());
            GLStateManager.glCallList(list);
            assertEquals(1, flushes.get());
        } finally {
            GLStateManager.glDeleteLists(list, 1);
        }
    }

    @Test
    void perUnitCapsFlushExceptTheTexturesBatchLayersOwn() {
        countFlushes();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            assertEquals(0, flushes.get(), "albedo and lightmap enables are stored with each layer");

            GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
            GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            flushes.set(0);
            BatchStateGuard.flush = () -> {
                assertFalse(GLStateManager.getTextures().getTextureUnitStates(2).isEnabled());
                flushes.incrementAndGet();
            };
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            assertEquals(1, flushes.get());
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            assertEquals(1, flushes.get(), "unchanged enable does not flush");

            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_S);
            flushes.set(0);
            countFlushes();
            GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_S);
            assertEquals(1, flushes.get());
        } finally {
            BatchStateGuard.flush = null;
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void secondaryColorFlushesOnlyWhileColorSumIsOn() {
        GLStateManager.glSecondaryColor3f(0f, 0f, 0f);
        countFlushes();
        try {
            GLStateManager.glSecondaryColor3f(0.5f, 0f, 0f);
            assertEquals(0, flushes.get(), "secondary color is unused while color sum is off");
            GLStateManager.glEnable(GL14.GL_COLOR_SUM);
            flushes.set(0);
            GLStateManager.glSecondaryColor3f(0.25f, 0f, 0f);
            assertEquals(1, flushes.get());
        } finally {
            BatchStateGuard.flush = null;
            GLStateManager.glDisable(GL14.GL_COLOR_SUM);
            GLStateManager.glSecondaryColor3f(0f, 0f, 0f);
        }
    }
}
