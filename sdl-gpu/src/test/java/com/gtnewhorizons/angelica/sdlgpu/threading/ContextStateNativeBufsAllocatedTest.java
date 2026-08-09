package com.gtnewhorizons.angelica.sdlgpu.threading;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void nonMainThreadContextStateHasAllNativeBufsAllocated() throws Exception {
        final AtomicReference<ContextState> result = new AtomicReference<>();
        final Thread t = new Thread(() -> result.set(new ContextState()), "non-main-ctxstate");
        t.start();
        t.join();
        final ContextState cs = result.get();
        assertNotNull(cs);
        assertNotEquals(0L, cs.vboBindingsAddr);
        assertNotNull(cs.cachedViewport);
        assertNotNull(cs.cachedScissor);
        assertNotNull(cs.cachedBlendColor);
        assertNotNull(cs.eboBinding);
    }
}
