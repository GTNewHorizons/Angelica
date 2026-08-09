package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.testutil.TestPaths;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ComputeStorageImageReflectionTest {

    private static ShaderManager.ComputeBindingMap reflect(String name, String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_compute_shader, name, SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) fail(name + " failed to compile: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            return ShaderManager.remapSpirvForComputeSDLGPU(spirv);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void qualifiedFormatsAndDimensionsAreReflected() {
        final ShaderManager.ComputeBindingMap map = reflect("mixed.csh", """
            #version 460 core
            layout(local_size_x = 1) in;
            layout(binding = 0, rgba8) readonly uniform image2D u_ro;
            layout(binding = 1, r32ui) writeonly uniform uimage3D u_rw;
            void main() {
                vec4 v = imageLoad(u_ro, ivec2(0));
                imageStore(u_rw, ivec3(0), uvec4(uint(v.r)));
            }
            """);

        assertEquals(1, map.roStorageTextureFormats().length);
        assertEquals(InternalTextureFormat.RGBA8.getGlFormat(), map.roStorageTextureFormats()[0]);
        assertEquals(GL11.GL_TEXTURE_2D, map.roStorageTextureTargets()[0]);

        assertEquals(1, map.rwStorageTextureFormats().length);
        assertEquals(InternalTextureFormat.R32UI.getGlFormat(), map.rwStorageTextureFormats()[0]);
        assertEquals(GL12.GL_TEXTURE_3D, map.rwStorageTextureTargets()[0]);
    }

    @Test
    void formatlessWriteonlyImageFallsBackToItsSampledType() {
        final ShaderManager.ComputeBindingMap map = reflect("clear_image3d.csh", TestPaths.readString("src/main/resources/assets/angelica/shaders/sdlgpu/clear_image3d.csh"));

        assertEquals(1, map.rwStorageTextureFormats().length, "one writeonly uimage3D");
        assertEquals(InternalTextureFormat.R32UI.getGlFormat(), map.rwStorageTextureFormats()[0], "no layout format qualifier, so the unsigned sampled type picks the stand-in format");
        assertEquals(GL12.GL_TEXTURE_3D, map.rwStorageTextureTargets()[0]);
    }
}
