package com.gtnewhorizons.angelica.sdlgpu.shader.msl;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FragmentStorageTextureSlotTest {

    private static String fragmentSource(int samplerCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("#version 460 core\n");
        for (int i = 0; i < samplerCount; i++) {
            sb.append("uniform sampler2D tex").append(i).append(";\n");
        }
        sb.append("layout(r16ui) readonly uniform uimage3D voxel_img;\n");
        sb.append("layout(location = 0) out vec4 fragColor;\n");
        sb.append("void main() {\n");
        sb.append("    vec4 acc = vec4(0.0);\n");
        for (int i = 0; i < samplerCount; i++) {
            sb.append("    acc += texture(tex").append(i).append(", vec2(0.5));\n");
        }
        sb.append("    uint v = imageLoad(voxel_img, ivec3(1, 2, 3)).x;\n");
        sb.append("    fragColor = acc + vec4(float(v));\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static int mslTextureIndex(String msl, String name) {
        final Matcher m = Pattern.compile(Pattern.quote(name) + "\\s*\\[\\[texture\\((\\d+)\\)\\]\\]").matcher(msl);
        if (!m.find()) fail("no [[texture(n)]] attribute for '" + name + "' in MSL:\n" + msl);
        return Integer.parseInt(m.group(1));
    }

    private void assertSlots(int samplerCount) {
        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(fragmentSource(samplerCount),
            GL20.GL_FRAGMENT_SHADER, "storage-slot-s" + samplerCount, true);
        final String src = pre != null ? pre.rewrittenSource() : fragmentSource(samplerCount);
        final SpirvCompiler.Result r = SpirvCompiler.compile(src,
            Shaderc.shaderc_fragment_shader, "storage-slot-s" + samplerCount, SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) fail("compile failed: " + r.error() + "\nsource:\n" + src);
        final ByteBuffer spirv = r.spirv();
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL20.GL_FRAGMENT_SHADER);
            try {
                final String msl = StandardCharsets.UTF_8.decode(out.code().duplicate()).toString();
                final boolean[] seen = new boolean[samplerCount];
                for (int i = 0; i < samplerCount; i++) {
                    final int idx = mslTextureIndex(msl, "tex" + i);
                    assertTrue(idx >= 0 && idx < samplerCount, "sampler tex" + i + " landed at Metal texture index " + idx + ", outside [0.." + samplerCount + "):\n" + msl);
                    assertTrue(!seen[idx], "duplicate Metal texture index " + idx + ":\n" + msl);
                    seen[idx] = true;
                }
                assertEquals(samplerCount, mslTextureIndex(msl, "voxel_img"), "storage image must sit at Metal texture index " + samplerCount + ":\n" + msl);
                assertTrue(msl.contains("voxel_img.read("), "storage image must be read via texture read:\n" + msl);
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test void storageTextureAfterZeroSamplers() { assertSlots(0); }
    @Test void storageTextureAfterOneSampler() { assertSlots(1); }
    @Test void storageTextureAfterThreeSamplers() { assertSlots(3); }

    @Test void multipleStorageImagesFollowReflectionOrder() throws Exception {
        final String src = String.join("\n",
            "#version 460 core",
            "uniform sampler2D texA;",
            "uniform sampler2D texB;",
            "layout(r16ui) readonly uniform uimage3D wsr_img;",
            "layout(r16ui) readonly uniform uimage3D voxel_img;",
            "layout(r8ui) readonly uniform uimage3D wsr_lod_img;",
            "layout(location = 0) out vec4 fragColor;",
            "void main() {",
            "    uint v = imageLoad(voxel_img, ivec3(1)).x + imageLoad(wsr_img, ivec3(2)).x + imageLoad(wsr_lod_img, ivec3(3)).x;",
            "    fragColor = texture(texA, vec2(0.5)) + texture(texB, vec2(0.5)) + vec4(float(v));",
            "}",
            "");
        assertProductionContract(src, "multi-image");
    }

    @Test void terrainShapedFragmentFollowsReflectionOrder() throws Exception {
        assertProductionContract(String.join("\n",
            "#version 460 core",
            "uniform sampler2D gtexture;",
            "uniform sampler2D lightmap;",
            "uniform sampler2D normals;",
            "uniform sampler2D specular;",
            "uniform sampler2D shadowtex0;",
            "uniform sampler2D shadowtex1;",
            "uniform sampler2D noisetex;",
            "layout(r16ui) readonly uniform uimage3D voxel_img;",
            "layout(r16ui) readonly uniform uimage3D wsr_img;",
            "in vec4 iris_FrontColor;",
            "in vec2 texcoord;",
            "in vec3 iris_Normal;",
            "layout(location = 0) out vec4 fragColor;",
            "void main() {",
            "    vec4 albedo = texture(gtexture, texcoord) * iris_FrontColor;",
            "    albedo += texture(lightmap, texcoord) + texture(normals, texcoord) + texture(specular, texcoord);",
            "    albedo += texture(shadowtex0, texcoord) + texture(shadowtex1, texcoord) + texture(noisetex, texcoord);",
            "    uint v = imageLoad(voxel_img, ivec3(texcoord * 8.0, 1)).x + imageLoad(wsr_img, ivec3(2)).x;",
            "    fragColor = albedo + vec4(float(v)) + vec4(iris_Normal, 0.0);",
            "}",
            ""), "terrain-shaped");
    }

    private void assertProductionContract(String source, String label) throws Exception {
        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(shader, source);
        sm.compileShader(shader);

        final Map<?, ?> shaderObjects = Reflect.get(sm, "shaderObjects");
        final Object obj = shaderObjects.values().iterator().next();
        assertTrue(Reflect.<Boolean>get(obj, "compiled"), label + " must compile");
        final ByteBuffer spirv = Reflect.get(obj, "spirv");
        final ShaderManager.StageReflection refl = Reflect.get(obj, "reflection");

        final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL20.GL_FRAGMENT_SHADER);
        try {
            final String msl = StandardCharsets.UTF_8.decode(out.code().duplicate()).toString();
            final int samplerCount = refl.counts().numSamplers();
            final StringBuilder report = new StringBuilder();
            for (int i = 0; i < refl.samplerNames().size(); i++) {
                final String n = refl.samplerNames().get(i);
                final int idx = mslTextureIndexOrMissing(msl, n);
                report.append("sampler[").append(i).append("] ").append(n).append(" -> ")
                    .append(idx < 0 ? "ABSENT (SPIRV-Cross inactive)" : "texture(" + idx + ")").append('\n');
            }
            for (int i = 0; i < refl.storageImageNames().size(); i++) {
                final String n = refl.storageImageNames().get(i);
                final int idx = mslTextureIndexOrMissing(msl, n);
                report.append("storage[").append(i).append("] ").append(n).append(" -> ")
                    .append(idx < 0 ? "ABSENT" : "texture(" + idx + ")").append(" expected texture(").append(samplerCount + i).append(")\n");
            }
            for (int i = 0; i < refl.samplerNames().size(); i++) {
                final int idx = mslTextureIndexOrMissing(msl, refl.samplerNames().get(i));
                if (idx < 0) continue; // inactive: nothing reads it; a hole is only fatal if it shifts others
                assertEquals(i, idx, label + ": active sampler '" + refl.samplerNames().get(i) + "' at wrong Metal index.\n" + report + "\nMSL:\n" + msl);
            }
            for (int i = 0; i < refl.storageImageNames().size(); i++) {
                final int idx = mslTextureIndexOrMissing(msl, refl.storageImageNames().get(i));
                if (idx < 0) continue; // inactive: the binder still fills its API slot, which is exactly why shifts matter
                assertEquals(samplerCount + i, idx,
                    label + ": storage image '" + refl.storageImageNames().get(i)
                        + "' at wrong Metal index (binder binds it at API slot " + i
                        + " => Metal texture " + (samplerCount + i) + ").\n" + report + "\nMSL head:\n"
                        + msl.substring(0, Math.min(1500, msl.length())));
            }
        } finally {
            MemoryUtil.memFree(out.code());
        }
    }

    private static int mslTextureIndexOrMissing(String msl, String name) {
        final Matcher m = Pattern.compile(Pattern.quote(name) + "\\s*\\[\\[texture\\((\\d+)\\)\\]\\]").matcher(msl);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
