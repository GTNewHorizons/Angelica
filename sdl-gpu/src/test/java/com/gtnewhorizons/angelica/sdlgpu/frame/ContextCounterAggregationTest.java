package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.angelica.sdlgpu.util.ThreadRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextCounterAggregationTest {

    private ThreadRegistry<ContextState> registered;
    private ContextState a;
    private ContextState b;

    @BeforeEach
    void register() {
        registered = Reflect.getStatic(SDLGPURenderBackend.class, "registeredStates");
        a = new ContextState();
        b = new ContextState();
        registered.add(a);
        registered.add(b);
    }

    @AfterEach
    void unregister() {
        registered.remove(a);
        registered.remove(b);
    }

    @Test
    void sumsAcrossContextsAndZeroesThem() {
        a.slotWrites = 3;
        a.slotWritesElided = 1;
        a.ssboBinds = 5;
        a.pipeline.keyRecomputes = 7;
        a.pipeline.fastPathHits = 11;

        b.slotWrites = 4;
        b.slotWritesElided = 2;
        b.ssboBinds = 6;
        b.pipeline.keyRecomputes = 8;
        b.pipeline.fastPathHits = 12;

        final int[] out = new int[SDLGPURenderBackend.CTR_COUNT];
        SDLGPURenderBackend.takeContextCounters(out);

        assertEquals(7, out[SDLGPURenderBackend.CTR_SLOT_WRITES]);
        assertEquals(3, out[SDLGPURenderBackend.CTR_SLOT_WRITES_ELIDED]);
        assertEquals(11, out[SDLGPURenderBackend.CTR_SSBO_BINDS]);
        assertEquals(15, out[SDLGPURenderBackend.CTR_KEY_RECOMPUTES]);
        assertEquals(23, out[SDLGPURenderBackend.CTR_FAST_PATH_HITS]);

        assertEquals(0, a.slotWrites);
        assertEquals(0, b.pipeline.fastPathHits);
    }

    @Test
    void reusedOutputArrayDoesNotAccumulateAcrossDrains() {
        final int[] out = new int[SDLGPURenderBackend.CTR_COUNT];
        a.ssboBinds = 9;
        SDLGPURenderBackend.takeContextCounters(out);
        assertEquals(9, out[SDLGPURenderBackend.CTR_SSBO_BINDS]);

        SDLGPURenderBackend.takeContextCounters(out);
        assertEquals(0, out[SDLGPURenderBackend.CTR_SSBO_BINDS], "a claimed count must not be replotted");
    }
}
