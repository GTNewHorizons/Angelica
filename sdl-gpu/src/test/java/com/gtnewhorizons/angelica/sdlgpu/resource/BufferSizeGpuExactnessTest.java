package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.RealDeviceExtension;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL15;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(RealDeviceExtension.class)
@Tag("sdl-gpu-device")
class BufferSizeGpuExactnessTest {

    private static Device device;
    private static ResourceManager resourceManager;

    @BeforeAll
    static void setUp() throws Exception {
        final SdlTestRig rig = RealDeviceExtension.rig();
        device = rig.device;
        resourceManager = rig.resourceManager;
    }

    @AfterAll
    static void tearDown() {
        if (resourceManager != null) resourceManager.shutdown();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"sub-bucket,9001,1000", "bucket boundary,9002,4096"})
    void createdBufferReportsRequestedSize(String name, int glId, long size) {
        final long handle = resourceManager.createBuffer(glId, FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER), size);
        assertNotEquals(0L, handle, "buffer creation failed");
        assertEquals(size, resourceManager.getBufferSize(glId));
        resourceManager.deleteBuffer(glId);
    }

    @Test
    void recycledStore_reportsUndefinedContents() {
        final int usage = FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER);
        final int first = 9006;
        resourceManager.createBuffer(first, usage, 1000);
        assertTrue(resourceManager.hasUndefinedContents(first));
        resourceManager.markBufferContentsDefined(first);
        resourceManager.deleteBuffer(first);

        final int second = 9007;
        resourceManager.createBuffer(second, usage, 1000);
        assertTrue(resourceManager.hasUndefinedContents(second));
        resourceManager.deleteBuffer(second);
    }

    @Test
    void pooledReuse_afterSubBucketDelete_keepsExactSize() {
        final int usage = FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER);
        final int first = 9003;
        resourceManager.createBuffer(first, usage, 1000);
        resourceManager.deleteBuffer(first);

        final int second = 9004;
        resourceManager.createBuffer(second, usage, 3000);
        assertEquals(3000L, resourceManager.getBufferSize(second));
        resourceManager.deleteBuffer(second);
    }
}
