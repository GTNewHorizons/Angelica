package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeviceImageAtomicsGpuTest {

    private Device device;

    @BeforeAll
    static void initSdl() {
        SdlTestRig.initSdlVideo();
    }

    @AfterEach
    void tearDown() {
        if (device != null) device.destroyDevice();
    }

    private Long2BooleanOpenHashMap verdicts() {
        return Reflect.get(device, "imageAtomicsBySize");
    }

    @Test
    void verdictsAreCachedPerSizeAndDroppedWithTheDevice() {
        device = new Device();
        assumeTrue(device.createDevice(), "no SDL GPU device on this machine");
        assumeTrue(device.metalTextureAtomics(), "needs native Metal compiling at MSL 3.1 or newer");

        assertFalse(device.supportsImageAtomics(40, 10, 2));
        assertFalse(device.supportsImageAtomics(0, 10, 1));
        assertFalse(device.supportsImageAtomics(40, -1, 1));
        assertEquals(0, verdicts().size());

        assertNull(Reflect.get(device, "imageAtomicsProbe"));

        assertTrue(device.supportsImageAtomics(40, 10, 1));
        assertFalse(device.supportsImageAtomics(64, 64, 1));
        assertEquals(2, verdicts().size());
        final Object probe = Reflect.get(device, "imageAtomicsProbe");
        assertTrue(device.supportsImageAtomics(40, 10, 1));
        assertFalse(device.supportsImageAtomics(64, 64, 1));
        assertEquals(2, verdicts().size());
        assertEquals(probe, Reflect.get(device, "imageAtomicsProbe"));

        device.destroyDevice();
        assertFalse(device.metalTextureAtomics());
        assertFalse(device.supportsImageAtomics(40, 10, 1));
        assertEquals(0, verdicts().size());
        assertNull(Reflect.get(device, "imageAtomicsProbe"));
    }
}
