package com.gtnewhorizons.angelica.sdlgpu.threading;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextStateNativeBufsAllocatedTest {

    @Test
    void freshContextStateHasAllNativeBufsAllocated() {
        final ContextState cs = new ContextState();
        assertNotEquals(0L, cs.vboBindingsAddr, "vboBindingsAddr must be allocated");
        assertNotNull(cs.eboBinding, "eboBinding must be allocated");
        assertNotNull(cs.cachedViewport, "cachedViewport must be allocated");
        assertNotNull(cs.cachedScissor, "cachedScissor must be allocated");
        assertNotNull(cs.cachedBlendColor, "cachedBlendColor must be allocated");
    }
}
