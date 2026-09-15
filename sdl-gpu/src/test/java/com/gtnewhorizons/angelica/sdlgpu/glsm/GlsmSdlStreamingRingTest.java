package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.glsm.streaming.PersistentStreamingBuffer;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL15;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlStreamingRingTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void ringCreationLeavesTheGlsmArrayBufferCacheMatchingSdl() {
        assertTrue(RenderSystem.supportsBufferStorage(), "SDL-GPU must advertise buffer storage");
        final int other = GLStateManager.glGenBuffers();
        PersistentStreamingBuffer ring = null;
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, other);
            ring = PersistentStreamingBuffer.createOrNull(4096);
            assertNotNull(ring);
            final IntSet persistent = Reflect.getStatic(FfpExtendedAttribs.class, "persistentSources");
            assertTrue(persistent.contains(ring.getBufferId()), "the ring buffer is the persistent source");
            assertFalse(persistent.contains(other), "the previously bound buffer is not persistent");
            assertEquals(RENDER_BACKEND.getInteger(GL15.GL_ARRAY_BUFFER_BINDING), GLStateManager.getBoundVBO(), "SDL ContextState.boundArrayBuffer must equal the GLSM cache after ring creation");
        } finally {
            if (ring != null) ring.destroy();
            GLStateManager.glDeleteBuffers(other);
        }
    }
}
