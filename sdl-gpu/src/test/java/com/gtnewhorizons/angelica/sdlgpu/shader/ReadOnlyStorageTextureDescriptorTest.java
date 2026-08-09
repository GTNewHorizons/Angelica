package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.system.MemoryStack.stackPush;

class ReadOnlyStorageTextureDescriptorTest {

    private static final String FRAGMENT_READONLY_IMAGE = String.join("\n",
        "#version 460 core",
        "uniform sampler2D gtexture;",
        "layout(r16ui) readonly uniform uimage3D voxel_img;",
        "layout(location = 0) out vec4 fragColor;",
        "void main() {",
        "    uint v = imageLoad(voxel_img, ivec3(1, 2, 3)).x;",
        "    fragColor = texture(gtexture, vec2(0.5)) + vec4(float(v));",
        "}",
        "");

    private static String preprocess(String source, int glShaderType) {
        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(source, glShaderType, "readonly-storage-test", true);
        return pre == null ? source : pre.rewrittenSource();
    }

    private static ByteBuffer compileRewritten() {
        final String source = preprocess(FRAGMENT_READONLY_IMAGE, GL20.GL_FRAGMENT_SHADER);
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_fragment_shader, "readonly-storage-test", SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) fail("compile failed: " + r.error() + "\nsource:\n" + source);
        return r.spirv();
    }

    private static Map<String, Integer> bindingsOf(ByteBuffer spirv, int resourceType) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        try (MemoryStack stack = stackPush()) {
            final PointerBuffer pCtx = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_create(pCtx));
            final IntBuffer words = spirv.asIntBuffer();
            final PointerBuffer pIR = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_parse_spirv(pCtx.get(0), words, words.remaining(), pIR));
            final PointerBuffer pCompiler = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_create_compiler(pCtx.get(0), Spvc.SPVC_BACKEND_GLSL, pIR.get(0), Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler));
            final PointerBuffer pResources = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_compiler_create_shader_resources(pCompiler.get(0), pResources));

            final PointerBuffer pList = stack.pointers(0);
            final PointerBuffer pCount = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_resources_get_resource_list_for_type(pResources.get(0), resourceType, pList, pCount));
            final int count = (int) pCount.get(0);
            if (count > 0) {
                final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
                for (int i = 0; i < count; i++) {
                    out.put(list.get(i).nameString(), Spvc.spvc_compiler_get_decoration(pCompiler.get(0), list.get(i).id(), Spv.SpvDecorationBinding));
                }
            }
            Spvc.spvc_context_destroy(pCtx.get(0));
        }
        return out;
    }

    private static List<String> namesOf(ByteBuffer spirv, int resourceType) {
        return new ArrayList<>(bindingsOf(spirv, resourceType).keySet());
    }

    @Test
    void readOnlyImageBecomesSeparateTextureReadByTexelFetch() {
        final String out = preprocess(FRAGMENT_READONLY_IMAGE, GL20.GL_FRAGMENT_SHADER);

        assertTrue(out.contains("uniform utexture3D voxel_img"), "read-only image must become a separate texture:\n" + out);
        assertFalse(out.contains("uimage3D"), "no storage image may survive:\n" + out);
        assertTrue(out.contains("texelFetch(voxel_img, ivec3(1, 2, 3), 0)"), "imageLoad must become a lod-0 texelFetch:\n" + out);
        assertTrue(out.contains(GlslVulkanPreprocess.SAMPLERLESS_EXTENSION), "texelFetch on a bare texture needs the samplerless extension:\n" + out);
        assertTrue(out.indexOf("#version") < out.indexOf(GlslVulkanPreprocess.SAMPLERLESS_EXTENSION), "the extension directive must follow #version:\n" + out);
    }

    @Test
    void extensionIsAnchoredToTheRealVersionDirectiveNotACommentMentioningIt() {
        final String source = "// ported, bumped #version to 150\n" + FRAGMENT_READONLY_IMAGE;
        final String out = preprocess(source, GL20.GL_FRAGMENT_SHADER);

        final int realVersion = out.indexOf("#version 460");
        assertTrue(realVersion >= 0, "the real directive must survive:\n" + out);
        assertTrue(out.indexOf(GlslVulkanPreprocess.SAMPLERLESS_EXTENSION) > realVersion, "anchoring on the first textual '#version' would emit the extension before the real directive, which glslang rejects:\n" + out);
    }

    @Test
    void extensionIsEmittedWhenTheVersionLineHasNoTrailingNewline() {
        final String source = "#version 460 core\nlayout(r16ui) readonly uniform uimage3D voxel_img;\nlayout(location = 0) out vec4 fragColor;\nvoid main() { fragColor = vec4(float(imageLoad(voxel_img, ivec3(0)).x)); }";
        final String out = preprocess(source, GL20.GL_FRAGMENT_SHADER);

        assertTrue(out.contains(GlslVulkanPreprocess.SAMPLERLESS_EXTENSION), "the extension must not be dropped:\n" + out);
        assertTrue(out.contains("utexture3D voxel_img"), "the rewrite must still happen:\n" + out);
    }

    @Test
    void separateTextureSurvivesCompilationAsSeparateImage() {
        final ByteBuffer spirv = compileRewritten();
        try {
            assertEquals(List.of("voxel_img"), namesOf(spirv, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE), "voxel_img must reflect as a separate image (SAMPLED_IMAGE descriptor)");
            assertEquals(List.of(), namesOf(spirv, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE), "nothing may reflect as a storage image");
            assertEquals(List.of("gtexture"), namesOf(spirv, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE), "only gtexture is a real combined sampler");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void separateTextureIsCountedAsStorageTextureNotSampler() {
        final ByteBuffer spirv = compileRewritten();
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);

            assertEquals(1, refl.counts().numSamplers(), "only gtexture consumes a sampler slot");
            assertEquals(1, refl.counts().numStorageTextures(), "voxel_img must be handed to SDL as a storage texture");
            assertEquals(List.of("gtexture"), refl.samplerNames());
            assertEquals(List.of("voxel_img"), refl.storageImageNames(), "StorageTextureBinder binds by this list, so voxel_img must appear in it");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void separateTextureBindsAfterTheSamplers() {
        final ByteBuffer spirv = compileRewritten();
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);

            assertEquals(0, bindingsOf(spirv, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE).get("gtexture"), "the sampler keeps binding 0");
            assertEquals(1, bindingsOf(spirv, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE).get("voxel_img"), "SDL lays set 2 out as samplers then storage textures, so one sampler puts voxel_img at binding 1");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
