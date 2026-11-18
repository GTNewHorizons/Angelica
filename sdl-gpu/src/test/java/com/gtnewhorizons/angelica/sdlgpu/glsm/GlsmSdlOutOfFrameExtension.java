package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GlsmRedirectAgent;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.assertFalse;

public final class GlsmSdlOutOfFrameExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        GlsmSdlHeadlessRig.boot();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.endFrame();
        assertFalse(GlsmSdlHeadlessRig.frameManager().isFrameActive(), "test must start with no active frame");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.endFrame();
        GlsmRedirectAgent.assertNoFailures();
    }
}
