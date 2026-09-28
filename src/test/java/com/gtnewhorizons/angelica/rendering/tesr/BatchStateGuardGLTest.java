package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.iris.IrisDisplayListState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@GLCoreTest
class BatchStateGuardGLTest {
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

    @Test
    void projectionMutationFlushesBeforeOverwritingTheMatrixAndDoesNotReenter() {
        final AtomicInteger flushes = new AtomicInteger();
        BatchStateGuard.flush = () -> {
            flushes.incrementAndGet();
            assertEquals(1f, GLStateManager.getProjectionMatrix().m00());
            // A flushing renderer is allowed to set up its own draw state.
            GLStateManager.glScissor(0, 0, 8, 8);
        };
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glScalef(2, 3, 1);
        assertEquals(1, flushes.get());
        assertEquals(2f, GLStateManager.getProjectionMatrix().m00());
    }

    @Test
    void commonCapturedStateAndManagedPassSetupDoNotDrainBatches() {
        final AtomicInteger flushes = new AtomicInteger();
        BatchStateGuard.flush = flushes::incrementAndGet;
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(1, 2, 3);
        GLStateManager.glPopMatrix();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glColor4f(0.5f, 1, 1, 1);
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        IrisDisplayListState.runProgramTransition(() -> {
            GLStateManager.glFogf(GL11.GL_FOG_START, 123);
            GLStateManager.glViewport(0, 0, 8, 8);
        });
        assertEquals(0, flushes.get());
        assertFalse(BatchStateGuard.isSuspended());
    }

    @Test
    void fogAndStencilFlushBeforeChangingTheCachedState() {
        GLStateManager.glFogf(GL11.GL_FOG_START, 17);
        BatchStateGuard.flush = () -> assertEquals(17f, GLStateManager.getFogState().getStart());
        GLStateManager.glFogf(GL11.GL_FOG_START, 29);
        assertEquals(29f, GLStateManager.getFogState().getStart());
        BatchStateGuard.flush = null;
        GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
        final AtomicInteger flushes = new AtomicInteger();
        BatchStateGuard.flush = () -> {
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_STENCIL_TEST));
            flushes.incrementAndGet();
        };
        GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
        assertEquals(1, flushes.get());
    }

    @Test
    void extraTextureBindingsAndAttributeRestoresCannotOvertakeQueuedDraws() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        final int texture = GLStateManager.glGenTextures();
        try {
            final AtomicInteger flushes = new AtomicInteger();
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
        final AtomicInteger flushes = new AtomicInteger();
        BatchStateGuard.flush = flushes::incrementAndGet;
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
    void textureMatrixChangesDrainOtherUnitsButKeepUnitZeroInstanced() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        final AtomicInteger flushes = new AtomicInteger();
        try {
            BatchStateGuard.flush = () -> {
                assertEquals(0f, GLStateManager.getTextures().getTextureUnitMatrix(2).m30());
                flushes.incrementAndGet();
            };
            GLStateManager.glTranslatef(0.5f, 0, 0);
            assertEquals(1, flushes.get());
            BatchStateGuard.flush = flushes::incrementAndGet;
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glPushMatrix();
            GLStateManager.glTranslatef(0.5f, 0, 0);
            GLStateManager.glPopMatrix();
            assertEquals(1, flushes.get(), "the base texture matrix is already stored in batch runs");
        } finally {
            BatchStateGuard.flush = null;
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
            GLStateManager.glPopMatrix();
        }
    }
}
