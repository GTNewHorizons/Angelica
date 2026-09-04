package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeviceLifecycleGpuTest {

    private Device device;

    @BeforeAll
    static void initSdl() {
        SdlTestRig.initSdlVideo();
    }

    @AfterEach
    void tearDown() {
        if (device != null) device.destroyDevice();
    }

    @Test
    void createsWithoutAWindowAndIsMemoized() {
        device = new Device();
        assumeTrue(device.createDevice(), "no SDL GPU device on this machine");

        final long handle = device.getDevice();
        assertNotEquals(0L, handle);
        assertTrue(device.createDevice(), "second createDevice should be a no-op");
        assertEquals(handle, device.getDevice(), "createDevice must not replace a live device");
    }

    @Test
    void destroyIsIdempotentOnAnUnclaimedDevice() {
        device = new Device();
        assumeTrue(device.createDevice(), "no SDL GPU device on this machine");

        device.destroyDevice();
        assertEquals(0L, device.getDevice());
        device.destroyDevice();
        assertEquals(0L, device.getDevice());
    }
}
