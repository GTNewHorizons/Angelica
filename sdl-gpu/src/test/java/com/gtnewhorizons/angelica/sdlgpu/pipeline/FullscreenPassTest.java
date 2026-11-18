package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullscreenPassTest {
    @Test
    void failedFragmentsDoNotRetryOrLeakCompilerResources() {
        final Device device = new Device();
        final ShaderManager shaders = new ShaderManager(device);
        final FullscreenPass fullscreen = new FullscreenPass(shaders, device);
        final AtomicInteger loads = new AtomicInteger();
        final Map<?, ?> programs = Reflect.get(shaders, "programObjects");
        final Map<?, ?> shaderObjects = Reflect.get(shaders, "shaderObjects");
        try {
            final FullscreenPass.Fragment fragment = fullscreen.fragment("wrong-ubo-size", 16, () -> {
                loads.incrementAndGet();
                return "#version 330 core\nout vec4 color;\nvoid main() { color = vec4(1.0); }\n";
            });
            assertEquals(0, fragment.shader());
            assertEquals(0, fragment.shader());
            assertEquals(1, loads.get(), "a failed variant must only be attempted once");
            assertEquals(0, programs.size(), "the temporary link program must be released even on failure");
            assertEquals(1, shaderObjects.size(), "only the reusable vertex shader remains");
            fullscreen.shutdown();
            assertEquals(0, shaderObjects.size(), "shutdown releases the shared vertex shader");
        } finally {
            fullscreen.shutdown();
            shaders.shutdown();
        }
    }
}
