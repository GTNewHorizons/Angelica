package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.testutil.TestPaths;
import com.gtnewhorizons.angelica.sdlgpu.sampler.SamplerLookup;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryUtil.memFree;

class FontFilterVertexSamplerTest {

    private static final String FONT_FILTER_VSH = TestPaths.readString("src/main/resources/assets/angelica/shaders/fontFilter.vsh");

    private static String runSdlPipeline(String raw) {
        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(raw, GL20.GL_VERTEX_SHADER, "fontFilter.vsh", true);
        String src = pre != null ? pre.rewrittenSource() : raw;
        src = ClipZRemap.injectGLToVulkanClipZ(src);
        src = SamplerStripper.stripUnused(src);
        return src;
    }

    private static ShaderManager.StageReflection reflect(String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_vertex_shader,
            "fontFilter.vsh", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "fontFilter.vsh failed to compile for Vulkan: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            return ShaderManager.reflectStage(spirv, true);
        } finally {
            memFree(spirv);
        }
    }

    @Test
    void samplerSurvivesStrippingAndReflectsOnTheVertexStage() {
        final String transformed = runSdlPipeline(FONT_FILTER_VSH);
        assertTrue(transformed.contains("uniform sampler2D lightmap"), "a reference inside a runtime if must keep the declaration; only preprocessor #if hides it");

        final List<String> samplers = reflect(transformed).samplerNames();
        assertEquals(List.of("lightmap"), samplers, "the vertex stage must expose exactly the lightmap sampler");
    }

    @Test
    void looseVec3UniformReflectsWithAStd140Offset() {
        final List<ShaderManager.UboMember> members = reflect(runSdlPipeline(FONT_FILTER_VSH)).uboMembers();

        final ShaderManager.UboMember lightmap = members.stream()
            .filter(m -> m.name().endsWith("u_Lightmap"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("u_Lightmap missing from the default uniform block: " + members));

        assertEquals(3, lightmap.vectorSize(), "u_Lightmap must reflect as a 3-wide vector");
        assertEquals(1, lightmap.columns(), "u_Lightmap is a vector, not a matrix");
        assertEquals(0, lightmap.arrayStride(), "a scalar member takes Std140Writer's contiguous path");
        assertEquals(0, lightmap.offset() % 16, "std140 aligns a vec3 to 16 bytes");
    }

    @Test
    void samplerNamedLightmapResolvesToTextureUnitOne() {
        final List<String> samplers = reflect(runSdlPipeline(FONT_FILTER_VSH)).samplerNames();
        final Object2IntOpenHashMap<String> noExplicitUnits = new Object2IntOpenHashMap<>();
        noExplicitUnits.defaultReturnValue(-1);

        assertEquals(1, SamplerLookup.getSamplerTextureUnit(samplers, noExplicitUnits, 0), "even without a glUniform1i the SDL fallback must put a sampler named 'lightmap' on unit 1");
    }
}
