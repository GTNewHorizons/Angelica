package com.gtnewhorizons.angelica.sdlgpu.glsm;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class GlsmSdlFrameExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.beginFrameAndReset();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.endFrame();
        assertTrue(GlsmSdlRedirectAgent.failures().isEmpty(),
            () -> "redirect agent recorded failures: " + GlsmSdlRedirectAgent.failures());
    }
}
