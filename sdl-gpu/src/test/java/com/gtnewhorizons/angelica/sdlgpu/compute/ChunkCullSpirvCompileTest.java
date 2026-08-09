package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.testutil.TestPaths;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ChunkCullSpirvCompileTest {

    private static final String SHADER = "chunk_cull.csh";

    private static String source() {
        return TestPaths.readString("src/main/resources/assets/angelica/shaders/culling/" + SHADER);
    }

    @Test
    void chunkCullReflectsExpectedBindings() {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source(), Shaderc.shaderc_compute_shader, SHADER, SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) fail(SHADER + " failed to compile: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            final ShaderManager.ComputeBindingMap map = ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            assertEquals(0, map.samplerGlSlots().length, "the frustum cull samples no textures");
            assertEquals(1, map.uboGlSlots().length, "Frustum UBO");
            assertEquals(1, map.uboGlSlots()[0], "Frustum UBO is at binding 1");
            assertEquals(160, map.uboSizes()[0], "Frustum UBO is 160 bytes");
            assertEquals(3, map.roSsboGlSlots().length + map.rwSsboGlSlots().length, "visible + meta + indirect");
            assertTrue(IntStream.concat(Arrays.stream(map.roSsboGlSlots()), Arrays.stream(map.rwSsboGlSlots())).allMatch(s -> s >= 0 && s < 8), "a storage binding outside 0..7 exceeds the GL 4.3 guaranteed minimum");
            assertEquals(0, map.roStorageTextureFormats().length, "declares no images");
            assertEquals(0, map.rwStorageTextureFormats().length, "declares no images");
            assertEquals(0, map.roStorageTextureTargets().length, "declares no images");
            assertEquals(0, map.rwStorageTextureTargets().length, "declares no images");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
