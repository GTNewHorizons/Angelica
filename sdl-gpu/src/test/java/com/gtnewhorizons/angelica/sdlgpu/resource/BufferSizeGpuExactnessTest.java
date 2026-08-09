package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferSizeGpuExactnessTest {

    private static Device device;
    private static ResourceManager resourceManager;
    private static long sdlDevice;

    @BeforeAll
    static void setUp() throws Exception {
        final SdlTestRig rig = SdlTestRig.acquireRealDevice();
        device = rig.device;
        resourceManager = rig.resourceManager;
    }

    @AfterAll
    static void tearDown() {
        if (resourceManager != null) resourceManager.shutdown();
        SdlTestRig.releaseRealDevice();
    }

    @Test
    void subBucketSize_reportsRequestedNotBucketed() {
        final int glId = 9001;
        final long handle = resourceManager.createBuffer(glId, FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER), 1000);
        assertNotEquals(0L, handle, "buffer creation failed");
        assertEquals(1000L, resourceManager.getBufferSize(glId));
        resourceManager.deleteBuffer(glId);
    }

    @Test
    void bucketBoundarySize_isUnchanged() {
        final int glId = 9002;
        resourceManager.createBuffer(glId, FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER), 4096);
        assertEquals(4096L, resourceManager.getBufferSize(glId));
        resourceManager.deleteBuffer(glId);
    }

    @Test
    void freshStore_reportsUndefinedContents() {
        final int glId = 9005;
        resourceManager.createBuffer(glId, FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER), 1000);
        assertTrue(resourceManager.hasUndefinedContents(glId));
        resourceManager.deleteBuffer(glId);
    }

    @Test
    void recycledStore_reportsUndefinedContents() {
        final int usage = FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER);
        final int first = 9006;
        resourceManager.createBuffer(first, usage, 1000);
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
