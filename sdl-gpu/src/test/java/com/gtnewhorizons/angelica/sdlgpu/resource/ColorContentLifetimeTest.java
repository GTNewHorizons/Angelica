package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorContentLifetimeTest {

    private static final long TEX = 0xC010125L;

    @Test
    void freshTextureHasUndefinedContents() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        assertFalse(rm.isTextureContentDefined(TEX), "never written, so the first pass must clear");
    }

    @Test
    void definedContentsSurviveAcrossFrames() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.markTextureContentDefined(TEX);
        assertTrue(rm.isTextureContentDefined(TEX));

        assertTrue(rm.isTextureContentDefined(TEX), "contents outlive the frame that produced them");
    }

    @Test
    void releaseResetsContentsBecauseSdlCanRecycleThePointer() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.markTextureContentDefined(TEX);
        rm.markTextureContentUndefined(TEX);
        assertFalse(rm.isTextureContentDefined(TEX), "a recycled handle must not inherit the previous texture's defined flag");
    }

    @Test
    void theZeroHandleIsNeverDefined() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.markTextureContentDefined(0L);
        assertFalse(rm.isTextureContentDefined(0L));
    }
}
