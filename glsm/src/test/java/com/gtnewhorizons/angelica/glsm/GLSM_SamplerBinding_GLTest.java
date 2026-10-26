package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class GLSM_SamplerBinding_GLTest {

    private static final int UNITS = 4;

    @BeforeEach
    void requireSamplerObjects() {
        assumeTrue(RenderSystem.supportsSamplerObjects());
    }

    @AfterEach
    void unbindTestUnits() {
        if (!RenderSystem.supportsSamplerObjects()) return;
        for (int unit = 0; unit < UNITS; unit++) {
            GLSMTextures.glBindSampler(unit, 0);
            GL33.glBindSampler(unit, 0);
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static int driverBinding(int unit) {
        final int saved = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        final int binding = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GL13.glActiveTexture(saved);
        return binding;
    }

    private static void assertUnits(String context, int... expected) {
        for (int unit = 0; unit < expected.length; unit++) {
            assertEquals(expected[unit], GLSMTextures.getSamplerBinding(unit), context + ": cache unit " + unit);
            assertEquals(expected[unit], driverBinding(unit), context + ": driver unit " + unit);
        }
    }

    private static IntBuffer ints(int... values) {
        final IntBuffer buf = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        buf.put(values).flip();
        return buf;
    }

    @Test
    void renderSystemRebindAfterGlsmUnbindReachesDriver() {
        final int sampler = RenderSystem.genSampler();
        try {
            RenderSystem.bindSamplerToUnit(0, sampler);
            GLSMTextures.glBindSampler(0, 0);
            assertEquals(0, driverBinding(0), "setup: GLSM unbind must reach the driver");

            RenderSystem.bindSamplerToUnit(0, sampler);

            assertEquals(sampler, driverBinding(0), "RenderSystem skipped a bind the driver no longer holds");
        } finally {
            RenderSystem.destroySampler(sampler);
        }
    }

    @Test
    void deleteClearsEveryUnitAndTheNextBindReachesDriver() {
        final int sampler = RenderSystem.genSampler();
        GLSMTextures.glBindSampler(1, sampler);
        GLSMTextures.glBindSampler(3, sampler);

        GLSMTextures.glDeleteSamplers(sampler);

        assertUnits("after delete", 0, 0, 0, 0);

        final int next = RenderSystem.genSampler();
        try {
            GLSMTextures.glBindSampler(1, next);
            assertEquals(next, driverBinding(1), "bind after delete never reached the driver");
        } finally {
            RenderSystem.destroySampler(next);
        }
    }

    @Test
    void samplerBindingQueryAnswersForTheActiveUnit() {
        final int sampler = RenderSystem.genSampler();
        try {
            GLSMTextures.glBindSampler(1, sampler);

            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING),
                GLSMQueries.glGetInteger(GL33.GL_SAMPLER_BINDING));
            assertEquals(sampler, GLSMQueries.glGetInteger(GL33.GL_SAMPLER_BINDING));

            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING),
                GLSMQueries.glGetInteger(GL33.GL_SAMPLER_BINDING));
            assertEquals(0, GLSMQueries.glGetInteger(GL33.GL_SAMPLER_BINDING));
        } finally {
            RenderSystem.destroySampler(sampler);
        }
    }

    @Test
    void unbindAllSamplersClearsUnitsBoundThroughGlsm() {
        final int sampler = RenderSystem.genSampler();
        final Runnable previousFlush = BatchStateGuard.flush;
        final AtomicInteger flushes = new AtomicInteger();
        try {
            GLSMTextures.glBindSampler(1, sampler);
            GLSMTextures.glBindSampler(3, sampler);
            BatchStateGuard.flush = () -> {
                assertEquals(sampler, driverBinding(1), "deferred draws must see the original bindings");
                assertEquals(sampler, driverBinding(3), "deferred draws must see the original bindings");
                flushes.incrementAndGet();
            };

            RenderSystem.unbindAllSamplers();
            RenderSystem.unbindAllSamplers();

            assertEquals(1, flushes.get(), "bulk unbind should drain deferred draws once");
            assertEquals(0, driverBinding(1), "unit 1 still bound on the driver");
            assertEquals(0, driverBinding(3), "unit 3 still bound on the driver");
            assertEquals(0, GLSMTextures.getSamplerBinding(1));
            assertEquals(0, GLSMTextures.getSamplerBinding(3));
        } finally {
            BatchStateGuard.flush = previousFlush;
            RenderSystem.destroySampler(sampler);
        }
    }

    @Test
    void replayForwardsCachedSamplerBindings() {
        final int highUnit = RenderSystem.getMaxCombinedTextureImageUnits() - 1;
        final int sampler = RenderSystem.genSampler();
        try {
            GLSMTextures.glBindSampler(2, sampler);
            GLSMTextures.glBindSampler(highUnit, sampler);
            GL33.glBindSampler(2, 0);
            GL33.glBindSampler(highUnit, 0);
            assertEquals(0, driverBinding(2), "setup failed to diverge the driver");
            assertEquals(0, driverBinding(highUnit), "setup failed to diverge the driver");

            GLSMAttribStack.replayStateToBackend();

            assertEquals(sampler, driverBinding(2), "replay dropped a cached sampler binding");
            assertEquals(sampler, driverBinding(highUnit), "replay skipped the highest bound unit");
        } finally {
            RenderSystem.destroySampler(sampler);
        }
    }

    @Test
    void sparseBindingsKeepTheCleanupLimitAccurateAfterUnbindAndDeletion() {
        final int highUnit = RenderSystem.getMaxCombinedTextureImageUnits() - 1;
        final int low = RenderSystem.genSampler();
        final int high = RenderSystem.genSampler();
        try {
            GLSMTextures.glBindSampler(1, low);
            GLSMTextures.glBindSampler(3, high);
            GLSMTextures.glBindSampler(highUnit, high);
            assertEquals(highUnit, GLStateManager.ctx().samplerUnits.getHighestBoundUnit());

            GLSMTextures.glBindSampler(highUnit, 0);
            assertEquals(3, GLStateManager.ctx().samplerUnits.getHighestBoundUnit());
            assertEquals(0, driverBinding(highUnit));

            GLSMTextures.glBindSampler(highUnit, high);
            RenderSystem.destroySampler(high);
            assertEquals(1, GLStateManager.ctx().samplerUnits.getHighestBoundUnit());
            assertEquals(0, driverBinding(highUnit));
            assertEquals(0, driverBinding(3));

            RenderSystem.unbindAllSamplers();
            assertEquals(-1, GLStateManager.ctx().samplerUnits.getHighestBoundUnit());
            assertEquals(0, driverBinding(1));

            GLSMTextures.glBindSampler(highUnit, low);
            RenderSystem.unbindAllSamplers();
            RenderSystem.unbindAllSamplers();
            assertEquals(-1, GLStateManager.ctx().samplerUnits.getHighestBoundUnit());
            assertEquals(0, driverBinding(highUnit));
        } finally {
            RenderSystem.destroySampler(low);
            RenderSystem.destroySampler(high);
        }
    }

    @Test
    void intBufferGenFillsFromPositionWithBindableSamplers() {
        final IntBuffer buf = ints(-1, 0, 0);
        buf.position(1);
        GLSMTextures.glGenSamplers(buf);
        final int a = buf.get(1);
        final int b = buf.get(2);
        try {
            assertEquals(-1, buf.get(0), "slot before position was overwritten");
            assertEquals(1, buf.position(), "gen must not move the buffer position");
            assertNotEquals(0, a);
            assertNotEquals(0, b);
            assertNotEquals(a, b);

            GLSMTextures.glBindSampler(0, a);
            GLSMTextures.glBindSampler(1, b);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "generated name rejected by the driver");
            assertEquals(a, driverBinding(0));
            assertEquals(b, driverBinding(1));
        } finally {
            GLSMTextures.glDeleteSamplers(a);
            GLSMTextures.glDeleteSamplers(b);
        }
    }

    @Test
    void bindSamplersFormsBindFromPosition() {
        final int a = RenderSystem.genSampler();
        final int b = RenderSystem.genSampler();
        try {
            final IntBuffer buf = ints(-1, a, b);
            buf.position(1);

            GLSMTextures.glBindSamplers(1, 2, buf);
            assertEquals(1, buf.position(), "count form must not move the buffer position");
            assertUnits("count form", 0, a, b, 0);

            GLSMTextures.glBindSampler(1, 0);
            GLSMTextures.glBindSampler(2, 0);
            buf.position(1);

            GLSMTextures.glBindSamplers(1, buf);
            assertEquals(1, buf.position(), "remaining form must not move the buffer position");
            assertUnits("remaining form", 0, a, b, 0);

            GLSMTextures.glBindSamplers(1, (IntBuffer) null);
            assertUnits("null remaining form", 0, a, b, 0);

            GLSMTextures.glBindSamplers(2, new int[]{a, b});
            assertUnits("int[] form", 0, a, a, b);
        } finally {
            RenderSystem.destroySampler(a);
            RenderSystem.destroySampler(b);
        }
    }

    @Test
    void bindSamplersNullUnbindsRangeAndShortBufferThrows() {
        final int prior = RenderSystem.genSampler();
        final int next = RenderSystem.genSampler();
        try {
            for (int unit = 0; unit < UNITS; unit++) GLSMTextures.glBindSampler(unit, prior);

            GLSMTextures.glBindSamplers(1, 2, (IntBuffer) null);

            assertUnits("null count form", prior, 0, 0, prior);

            for (int unit = 0; unit < UNITS; unit++) GLSMTextures.glBindSampler(unit, prior);
            final IntBuffer buf = ints(next, next);

            assertThrows(IllegalArgumentException.class, () -> GLSMTextures.glBindSamplers(0, UNITS, buf));

            assertEquals(0, buf.position());
            assertUnits("short buffer", prior, prior, prior, prior);
        } finally {
            RenderSystem.destroySampler(prior);
            RenderSystem.destroySampler(next);
        }
    }
}
