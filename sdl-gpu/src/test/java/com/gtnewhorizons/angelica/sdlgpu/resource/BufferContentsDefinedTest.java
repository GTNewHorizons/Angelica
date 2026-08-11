package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferContentsDefinedTest {

    private static ResourceManager newResourceManager() {
        return SdlTestRig.resourceManager();
    }

    @Test
    void unknownBuffer_isNotUndefined() {
        assertFalse(newResourceManager().hasUndefinedContents(4242));
    }

    @Test
    void markDefined_clearsTheFlag() {
        final ResourceManager rm = newResourceManager();
        SdlReflect.markUndefined(rm, 7);
        assertTrue(rm.hasUndefinedContents(7));

        rm.markBufferContentsDefined(7);
        assertFalse(rm.hasUndefinedContents(7));
    }

    @Test
    void markDefined_isIdempotentAndIgnoresZero() {
        final ResourceManager rm = newResourceManager();
        rm.markBufferContentsDefined(0);
        rm.markBufferContentsDefined(9);
        rm.markBufferContentsDefined(9);
        assertFalse(rm.hasUndefinedContents(9));
    }

    @Test
    void deleteBuffer_clearsTheFlag() {
        final ResourceManager rm = newResourceManager();
        SdlReflect.markUndefined(rm, 5);
        rm.recordBufferSizeOnly(5, 1000);

        rm.deleteBuffer(5);

        assertFalse(rm.hasUndefinedContents(5));
    }
}
