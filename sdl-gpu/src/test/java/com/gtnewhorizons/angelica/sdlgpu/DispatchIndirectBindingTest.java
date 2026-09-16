package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchIndirectBindingTest {

    @AfterEach
    void resetContextState() {
        final ContextState st = SdlTestRig.contextState();
        st.boundIndirectBuffer = 0;
        st.boundDispatchIndirectBuffer = 0;
    }

    @Test
    void dispatchIndirectHasItsOwnBindingPoint() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.bindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 11);
        backend.bindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, 22);

        assertEquals(11, st.getBoundBuffer(GL40.GL_DRAW_INDIRECT_BUFFER));
        assertEquals(22, st.getBoundBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER));
    }

    @Test
    void bindingDispatchIndirectLeavesTheDrawIndirectSlotAlone() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.bindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 33);
        backend.bindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, 44);
        backend.bindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, 0);

        assertEquals(33, st.getBoundBuffer(GL40.GL_DRAW_INDIRECT_BUFFER));
        assertEquals(0, st.getBoundBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER));
    }
}
