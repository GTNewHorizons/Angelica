package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GlsmRedirectAgent;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class GlsmSdlFrameExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        GlsmSdlHeadlessRig.boot();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.beginFrameAndReset();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.endFrame();
        GlsmRedirectAgent.assertNoFailures();
    }
}
