package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.testutil.TestPaths;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ClearImage3DReflectionTest {

    private static final String SHADER = "clear_image3d.csh";

    private static String source() {
        return TestPaths.readString("src/main/resources/assets/angelica/shaders/sdlgpu/" + SHADER);
    }

    @Test
    void compileShaderLeavesComputeSpirvPristine() throws Exception {
        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL43.GL_COMPUTE_SHADER);
        sm.shaderSource(shader, source());
        sm.compileShader(shader);

        final var shaderObjects = ShaderManager.class.getDeclaredField("shaderObjects");
        shaderObjects.setAccessible(true);
        final Object obj = ((java.util.Map<?, ?>) shaderObjects.get(sm)).values().iterator().next();
        final var objClass = obj.getClass();
        final var compiledField = objClass.getDeclaredField("compiled");
        compiledField.setAccessible(true);
        assertTrue((boolean) compiledField.get(obj), "clear compute must compile");
        final var reflField = objClass.getDeclaredField("reflection");
        reflField.setAccessible(true);
        assertTrue(reflField.get(obj) == ShaderManager.StageReflection.EMPTY,
            "compute shaders must not receive the graphics-convention reflection; link reflects the compute-remapped copy");

        final var spirvField = objClass.getDeclaredField("spirv");
        spirvField.setAccessible(true);
        final ByteBuffer pristine = (ByteBuffer) spirvField.get(obj);
        final ByteBuffer copy = MemoryUtil.memAlloc(pristine.remaining());
        copy.put(pristine.duplicate()).flip();
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(copy);
            final List<String> members = ShaderManager.reflectStage(copy, false)
                .uboMembers().stream().map(ShaderManager.UboMember::name).toList();
            assertTrue(members.contains("u_extent"),
                "link-time reflection of the pristine compute SPIR-V must expose the clear uniforms; got " + members);
        } finally {
            MemoryUtil.memFree(copy);
        }
    }

    @Test
    void looseUniformsReflectAsBindingZeroUboMembers() {
        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(source(), GL43.GL_COMPUTE_SHADER, SHADER, true);
        final String src = pre != null ? pre.rewrittenSource() : source();
        final SpirvCompiler.Result r = SpirvCompiler.compile(src, Shaderc.shaderc_compute_shader, SHADER, SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) fail(SHADER + " failed to compile: " + r.error() + "\nsource:\n" + src);
        final ByteBuffer spirv = r.spirv();
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final List<String> members = refl.uboMembers().stream().map(ShaderManager.UboMember::name).toList();
            assertTrue(members.contains("u_extent"), "clear uniforms must reflect as UBO members; got uboSize=" + refl.uboSize() + " members=" + members + " extraNames=" + refl.extraUniformNames());
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
