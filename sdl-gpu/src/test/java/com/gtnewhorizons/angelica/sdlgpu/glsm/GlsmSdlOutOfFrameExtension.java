package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class GlsmSdlOutOfFrameExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.endFrame();
        final FrameManager fm = Reflect.get(BackendManager.RENDER_BACKEND, "frameManager");
        assertFalse(fm.isFrameActive(), "test must start with no active frame");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        GlsmSdlHeadlessRig.endFrame();
        assertTrue(GlsmSdlRedirectAgent.failures().isEmpty(), () -> "redirect agent recorded failures: " + GlsmSdlRedirectAgent.failures());
    }
}
