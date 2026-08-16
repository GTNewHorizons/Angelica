package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageUnitBindingStateTest {


    @Test
    void bindImageTextureRecordsTheFullTuple() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.bindImageTexture(2, 77, 3, true, 5, GL15.GL_READ_WRITE, GL30.GL_R32UI);

        assertEquals(77, st.boundStorageTextureByUnit[2]);
        assertEquals(3, st.boundStorageTextureLevel[2]);
        assertTrue(st.boundStorageTextureLayered[2]);
        assertEquals(5, st.boundStorageTextureLayer[2]);
        assertEquals(GL15.GL_READ_WRITE, st.boundStorageTextureAccess[2]);
        assertEquals(GL30.GL_R32UI, st.boundStorageTextureFormat[2]);
    }

    @Test
    void getIntegerIndexedAnswersImageBindingsFromState() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();

        backend.bindImageTexture(1, 42, 2, false, 4, GL15.GL_READ_ONLY, GL30.GL_RGBA32UI);

        assertEquals(42, backend.getIntegerIndexed(GL42.GL_IMAGE_BINDING_NAME, 1));
        assertEquals(2, backend.getIntegerIndexed(GL42.GL_IMAGE_BINDING_LEVEL, 1));
        assertEquals(GL11.GL_FALSE, backend.getIntegerIndexed(GL42.GL_IMAGE_BINDING_LAYERED, 1));
        assertEquals(4, backend.getIntegerIndexed(GL42.GL_IMAGE_BINDING_LAYER, 1));
        assertEquals(GL15.GL_READ_ONLY, backend.getIntegerIndexed(GL42.GL_IMAGE_BINDING_ACCESS, 1));
        assertEquals(GL30.GL_RGBA32UI, backend.getIntegerIndexed(GL42.GL_IMAGE_BINDING_FORMAT, 1));
    }

    @Test
    void outOfRangeUnitIsIgnored() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.bindImageTexture(0, 11, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
        backend.bindImageTexture(ContextState.MAX_IMAGE_UNITS, 99, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);

        assertEquals(11, st.boundStorageTextureByUnit[0], "an out-of-range unit must not disturb a valid one");
        for (int i = 0; i < ContextState.MAX_IMAGE_UNITS; i++) {
            assertFalse(st.boundStorageTextureByUnit[i] == 99, "unit " + i + " took an out-of-range bind");
        }
    }
}
