package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GetGPUDeviceDriver;

class ImageAtomicsGpuTest {

    private static SdlTestRig rig;
    private static ImageAtomicsProbe probe;

    @BeforeAll
    static void setUp() throws Exception {
        rig = SdlTestRig.acquireRealDevice();
        assumeTrue("metal".equalsIgnoreCase(SDL_GetGPUDeviceDriver(rig.sdlHandle)), "native Metal only");
        assumeTrue(ImageAtomicsProbe.defaultMslVersion() >= ImageAtomicsProbe.MTL_LANGUAGE_VERSION_3_1, "this process compiles Metal source below MSL 3.1");
        probe = new ImageAtomicsProbe(rig.sdlHandle);
    }

    @AfterAll
    static void tearDown() {
        if (probe != null) probe.release();
        SdlTestRig.releaseRealDevice();
    }

    @Test
    void atomicsCountEveryInvocationOnAnImageTheSizeOfEuphoriasEndCrystalMap() {
        assertTrue(probe.run(40, 10));
    }
}
