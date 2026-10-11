package com.gtnewhorizons.angelica.sdlgpu.threading;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerThreadContextStateTest {

    @Test
    void freshContextStateHasIdentityAttribBinding() {
        final ContextState cs = new ContextState();
        for (int i = 0; i < ContextState.MAX_VERTEX_ATTRIBS; i++) {
            assertEquals(i, cs.currentVao.attribBinding[i], "currentVao.attribBinding[" + i + "] should default to identity per GL spec");
        }
    }

    @Test
    void freshContextStateHasGLSpecDefaults() {
        final ContextState cs = new ContextState();
        assertEquals(0.0f, cs.viewportDepthNear);
        assertEquals(1.0f, cs.viewportDepthFar);
        assertTrue(cs.viewportDirty);
        assertTrue(cs.scissorDirty);
        assertTrue(cs.blendColorDirty);
    }
}
