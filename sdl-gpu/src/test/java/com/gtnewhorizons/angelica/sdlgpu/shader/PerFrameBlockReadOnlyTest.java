package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryUtil.memFree;

class PerFrameBlockReadOnlyTest {

    private static final PerFrameUniformBlock BLOCK = new PerFrameUniformBlock(List.of(
        new Member("cameraPosition", UniformType.VEC3),
        new Member("rainStrength", UniformType.FLOAT)));

    private static final PerFrameUniformBlock PER_PASS = new PerFrameUniformBlock(List.of(new Member("gbufferModelView", UniformType.MAT4)));

    private static final String DUAL_SOURCE = """
        #version 330 core
        out vec4 fragColor;
        void main() { fragColor = gbufferModelView * vec4(cameraPosition, rainStrength); }
        """;

    @Test
    void injectedBlocksAreDecoratedNonWritable() {
        final String injected = PerFrameBlockInjector.inject(DUAL_SOURCE, BLOCK, PER_PASS);
        final SpirvCompiler.Result r = SpirvCompiler.compile(injected, Shaderc.shaderc_fragment_shader, "readonly.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "compile failed: " + r.error());

        final ByteBuffer spirv = r.spirv();
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            for (int b = 0; b < ShaderManager.BLOCK_COUNT; b++) {
                final ShaderManager.BlockReflection block = refl.blocks()[b];
                assertTrue(block.size() > 0, "block " + b + " must be present for this assertion to mean anything");
                assertTrue(block.readOnly(), "block " + b + " must carry NonWritable or Vulkan rejects every pipeline that uses it");
            }
        } finally {
            memFree(spirv);
        }
    }
}
