package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlOffContextCreationTest {

    @Test
    void creationWithoutContextThrows() throws InterruptedException {
        TestThreads.run("sdl-no-context", () -> assertThrows(IllegalStateException.class, GLStateManager::glGenTextures));
    }
}
