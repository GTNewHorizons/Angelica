package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.glsm.streaming.StreamingUploader.UploadStrategy;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class StreamingBindingCacheGLTest {

    private static ByteBuffer filled(int bytes) {
        final ByteBuffer data = memAlloc(bytes);
        for (int i = 0; i < bytes; i++) {
            data.put(i, (byte) i);
        }
        data.position(0).limit(bytes);
        return data;
    }

    private static int glsmUniformBufferBinding() {
        final Object ctx = Reflect.invokeStatic(GLStateManager.class, "ctx", new Class<?>[0]);
        final Int2IntMap bound = Reflect.get(ctx, "boundOtherBuffers");
        return bound.get(GL31.GL_UNIFORM_BUFFER);
    }

    @ParameterizedTest
    @EnumSource(UploadStrategy.class)
    void orphanUploadLeavesTheArrayBufferCacheMatchingTheDriver(UploadStrategy strategy) {
        final int other = GLStateManager.glGenBuffers();
        final OrphanStreamingBuffer buf = new OrphanStreamingBuffer();
        final ByteBuffer data = filled(64);
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, other);
            buf.upload(strategy, data);
            assertEquals(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), GLStateManager.getBoundVBO(), "first upload respecifies the buffer");
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, other);
            buf.upload(strategy, data);
            assertEquals(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), GLStateManager.getBoundVBO(), "second upload takes the in-capacity path");
        } finally {
            memFree(data);
            buf.destroy();
            GLStateManager.glDeleteBuffers(other);
        }
    }

    @Test
    void uniformRingOrphanPathLeavesTheUniformBufferCacheMatchingTheDriver() {
        final int other = GLStateManager.glGenBuffers();
        final ByteBuffer src = filled(64);
        UniformRingBuffer ring = null;
        try {
            GLStateManager.glBindBuffer(GL31.GL_UNIFORM_BUFFER, other);
            ring = new UniformRingBuffer(1024, 64, true);
            assertFalse(ring.isPersistent());
            assertEquals(GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING), glsmUniformBufferBinding(), "constructor allocation");
            GLStateManager.glBindBuffer(GL31.GL_UNIFORM_BUFFER, other);
            ring.writeBlock(src);
            assertEquals(GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING), glsmUniformBufferBinding(), "writeBlock");
        } finally {
            if (ring != null) ring.destroy();
            GLStateManager.glDeleteBuffers(other);
            memFree(src);
        }
    }

    @Test
    void persistentRingMarksItsOwnBufferAndLeavesTheCacheMatchingTheDriver() {
        assumeTrue(RenderSystem.supportsBufferStorage(), "GL4.4+ required");
        final int other = GLStateManager.glGenBuffers();
        PersistentStreamingBuffer ring = null;
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, other);
            ring = PersistentStreamingBuffer.createOrNull(4096, false);
            assertNotNull(ring);
            final IntSet persistent = Reflect.getStatic(FfpExtendedAttribs.class, "persistentSources");
            assertTrue(persistent.contains(ring.getBufferId()), "the ring buffer is the persistent source");
            assertFalse(persistent.contains(other), "the previously bound buffer is not persistent");
            assertEquals(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), GLStateManager.getBoundVBO());
        } finally {
            if (ring != null) ring.destroy();
            GLStateManager.glDeleteBuffers(other);
        }
    }
}
