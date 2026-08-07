package com.gtnewhorizons.angelica.sdlgpu.shader.msl;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests SPIR-V → MSL cross-compilation for the macOS/Metal backend.
 */
class MslCrossCompileTest {



    @Test
    void vertexShaderCrossCompiles() {
        final ByteBuffer spirv = compile(SpirvTestShaders.VERTEX_GLSL, Shaderc.shaderc_vertex_shader);
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_VERTEX_SHADER);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL20.GL_VERTEX_SHADER);
            try {
                final String msl = decode(out.code());
                assertTrue(msl.length() > 0, "MSL output non-empty");
                assertTrue(msl.contains("vertex "), "MSL should contain vertex stage qualifier:\n" + msl);
                assertFalse(msl.contains("descriptor_set"), "MSL must not leak descriptor_set decorations:\n" + msl);
                assertTrue(msl.contains("[[buffer(0)]]"), "UBO should land at [[buffer(0)]]:\n" + msl);
                assertTrue(msl.contains("[[texture(0)]]"), "sampler2D should land at [[texture(0)]]:\n" + msl);
                assertTrue(msl.contains("[[sampler(0)]]"), "sampler2D should land at [[sampler(0)]]:\n" + msl);
                assertEquals("main0", out.entrypoint(), "Metal forbids main; expect main0");
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void fragmentShaderCrossCompiles() {
        final ByteBuffer spirv = compile(SpirvTestShaders.FRAGMENT_GLSL, Shaderc.shaderc_fragment_shader);
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL20.GL_FRAGMENT_SHADER);
            try {
                final String msl = decode(out.code());
                assertTrue(msl.contains("fragment "), "MSL should contain fragment stage qualifier:\n" + msl);
                assertTrue(msl.contains("[[buffer(0)]]"), "Fragment UBO should land at [[buffer(0)]]:\n" + msl);
                assertTrue(msl.contains("[[texture(0)]]"), "First sampler at [[texture(0)]]:\n" + msl);
                assertTrue(msl.contains("[[texture(1)]]"), "Second sampler at [[texture(1)]]:\n" + msl);
                assertTrue(msl.contains("[[sampler(0)]]") && msl.contains("[[sampler(1)]]"),
                    "Samplers at slots 0 and 1:\n" + msl);
                assertEquals("main0", out.entrypoint());
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static final String CHUNK_CULL_GLSL = """
        #version 460 core
        layout(local_size_x = 64) in;
        layout(std430, binding = 0) readonly  buffer A { uint a[]; } visible;
        layout(std430, binding = 1) readonly  buffer B { uint b[]; } meta;
        layout(std430, binding = 2) writeonly buffer C { uint c[]; } indirect;
        layout(binding = 3) uniform sampler2D u_Hiz;
        layout(std140, binding = 0) uniform Frustum { vec4 control; } frustum;
        void main() {
            uint gid = gl_GlobalInvocationID.x;
            if (gid >= uint(frustum.control.x)) return;
            indirect.c[gid] = visible.a[gid] + meta.b[gid] + uint(textureLod(u_Hiz, vec2(0), 0).r);
        }
        """;


    @Test
    void ldsMultiMipComputeCrossCompiles() {
        final ByteBuffer spirv = compile(SpirvTestShaders.LDS_MULTI_MIP_GLSL, Shaderc.shaderc_compute_shader);
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL43.GL_COMPUTE_SHADER);
            try {
                final String msl = decode(out.code());
                assertTrue(msl.contains("kernel "), "Compute MSL should contain kernel stage qualifier:\n" + msl);
                assertTrue(msl.contains("threadgroup spvUnsafeArray<float") || msl.contains("threadgroup float"), "shared float array must become threadgroup memory:\n" + msl);
                assertTrue(msl.contains("threadgroup_barrier"), "barrier() must become threadgroup_barrier:\n" + msl);
                assertTrue(msl.contains("[[texture(0)]]"), "u_Src RW image at [[texture(0)]]:\n" + msl);
                assertTrue(msl.contains("[[texture(1)]]"), "u_Dst1 at [[texture(1)]]:\n" + msl);
                assertTrue(msl.contains("[[texture(2)]]"), "u_Dst2 at [[texture(2)]]:\n" + msl);
                assertTrue(msl.contains("[[buffer(0)]]"), "Down UBO at [[buffer(0)]]:\n" + msl);
                assertFalse(msl.contains("descriptor_set"), "MSL must not leak descriptor_set decorations:\n" + msl);
                assertEquals("main0", out.entrypoint());
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    /**
     * The clear shader omits the image format qualifier, which GLSL allows only for writeonly images. Metal needs a
     * concrete texture type, so verify SPIRV-Cross resolves it rather than emitting an unusable access-less image.
     */
    @Test
    void clearImage3dComputeCrossCompiles() throws IOException {
        final String glsl = Files.readString(
            Path.of("..", "src", "main", "resources", "assets", "angelica", "shaders", "sdlgpu", "clear_image3d.csh"),
            StandardCharsets.UTF_8);
        final ByteBuffer spirv = compile(glsl, Shaderc.shaderc_compute_shader);
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL43.GL_COMPUTE_SHADER);
            try {
                final String msl = decode(out.code());
                assertTrue(msl.contains("kernel "), "compute MSL should contain kernel stage qualifier:\n" + msl);
                assertTrue(msl.contains("texture3d<uint"), "formatless writeonly uimage3D must resolve to a uint 3D texture:\n" + msl);
                assertTrue(msl.contains("access::write"), "the image must carry write access:\n" + msl);
                assertTrue(msl.contains("[[texture(0)]]"), "the target image should land at [[texture(0)]]:\n" + msl);
                assertEquals("main0", out.entrypoint());
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void chunkCullComputeCrossCompiles() {
        final ByteBuffer spirv = compile(CHUNK_CULL_GLSL, Shaderc.shaderc_compute_shader);
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL43.GL_COMPUTE_SHADER);
            try {
                final String msl = decode(out.code());
                assertTrue(msl.contains("kernel "), "Compute MSL should contain kernel stage qualifier:\n" + msl);
                assertTrue(msl.contains("[[texture(0)]]"), "u_Hiz at [[texture(0)]]:\n" + msl);
                assertTrue(msl.contains("[[sampler(0)]]"), "u_Hiz sampler at [[sampler(0)]]:\n" + msl);
                assertTrue(msl.contains("[[buffer(0)]]"), "Frustum UBO at [[buffer(0)]]:\n" + msl);
                assertTrue(msl.contains("[[buffer(1)]]"), "visible RO SSBO at [[buffer(1)]]:\n" + msl);
                assertTrue(msl.contains("[[buffer(2)]]"), "meta RO SSBO at [[buffer(2)]]:\n" + msl);
                assertTrue(msl.contains("[[buffer(3)]]"), "indirect RW SSBO at [[buffer(3)]] (numUBOs+numROBuf=3):\n" + msl);
                assertEquals("main0", out.entrypoint());
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static final String VERTEX_WRITES_IMAGE_GLSL = """
        #version 460 core
        layout(set = 0, binding = 0) uniform UBO { mat4 u_Mvp; } ubo;
        layout(set = 0, binding = 1, r32ui) writeonly uniform uimage3D voxel_img;
        layout(location = 0) in vec3 a_Position;
        void main() {
            gl_Position = ubo.u_Mvp * vec4(a_Position, 1.0);
            imageStore(voxel_img, ivec3(a_Position), uvec4(1u, 0u, 0u, 0u));
        }
        """;

    @Test
    void vertexShaderWritingStorageImageStillReturnsPosition() {
        final ByteBuffer spirv = compile(VERTEX_WRITES_IMAGE_GLSL, Shaderc.shaderc_vertex_shader);
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_VERTEX_SHADER);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL20.GL_VERTEX_SHADER);
            try {
                final String msl = decode(out.code());
                assertFalse(msl.contains("vertex void main0"),
                    "vertex stage must not be emitted with void return when gl_Position is written:\n" + msl);
                assertTrue(msl.contains("[[position]]"),
                    "vertex output struct should include [[position]] decoration:\n" + msl);
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void crossCompileFreesNativeMemory() {
        for (int i = 0; i < 200; i++) {
            final ByteBuffer spirv = compile(SpirvTestShaders.VERTEX_GLSL, Shaderc.shaderc_vertex_shader);
            try {
                ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_VERTEX_SHADER);
                final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL20.GL_VERTEX_SHADER);
                MemoryUtil.memFree(out.code());
            } finally {
                MemoryUtil.memFree(spirv);
            }
        }
    }

    // Mimics the shape of RwImageStoreExtractor's chunk-vertex compute output:
    //   - loose uniforms (consolidated by shaderc into a default UBO)
    //   - readonly SSBO at binding 9 (_VgVbuf)
    //   - one iimage2D used by imageAtomicAdd (read+write)
    //   - one writeonly uimage3D used by imageStore only
    private static final String CHUNK_COMPUTE_WITH_ATOMIC_IMAGE_GLSL = """
        #version 460 core
        layout(local_size_x = 64) in;
        layout(binding = 0, r32i) uniform iimage2D endcrystal_img;
        layout(binding = 1, r16ui) writeonly uniform uimage3D voxel_img;
        uniform int _vg_startVertex;
        uniform int _vg_vertexCount;
        layout(std430, binding = 9) readonly buffer _VgVbuf { uint data[]; } _vg_vbuf;
        void main() {
            int id = _vg_startVertex + int(gl_GlobalInvocationID.x);
            if (id >= _vg_startVertex + _vg_vertexCount) return;
            imageAtomicAdd(endcrystal_img, ivec2(id, 0), 1);
            imageStore(voxel_img, ivec3(0), uvec4(uint(id), 0u, 0u, 0u));
        }
        """;

    @Test
    void chunkComputeWithImageAtomic_mslBufferSlotsAreUnique() {
        assertMslBufferSlotsUnique(CHUNK_COMPUTE_WITH_ATOMIC_IMAGE_GLSL);
    }

    private static final String COMPOSITE_VSH_COMPUTE_WITH_ATOMIC_IMAGE_GLSL = """
        #version 460 core
        layout(local_size_x = 64) in;
        layout(binding = 0, r32i) uniform iimage2D endcrystal_img;
        uniform mat4 iris_ModelViewProjectionMatrix;
        const vec4 _vg_quad[4] = vec4[4](
            vec4(1.0, 1.0, 0.0, 1.0),
            vec4(0.0, 1.0, 0.0, 1.0),
            vec4(1.0, 0.0, 0.0, 1.0),
            vec4(0.0, 0.0, 0.0, 1.0));
        vec4 _vg_gl_vertex;
        int _vg_gl_vertex_id;
        #define iris_GlVertex _vg_gl_vertex
        #define iris_GlVertexID _vg_gl_vertex_id
        vec4 iris_ftransform() { return iris_ModelViewProjectionMatrix * iris_GlVertex; }
        void _vg_body() {
            imageAtomicAdd(endcrystal_img, ivec2(iris_GlVertexID, 0), 1);
        }
        void main() {
            uint vid = gl_GlobalInvocationID.x;
            if (vid >= 4u) return;
            _vg_gl_vertex = _vg_quad[vid];
            _vg_gl_vertex_id = int(vid);
            _vg_body();
        }
        """;

    @Test
    void compositeVshComputeWithImageAtomic_mslBufferSlotsAreUnique() {
        assertMslBufferSlotsUnique(COMPOSITE_VSH_COMPUTE_WITH_ATOMIC_IMAGE_GLSL);
    }

    // Mirrors composite-fsh: 2D dispatch + _vg_target_size uniform + gl_FragCoord shim.
    private static final String COMPOSITE_FSH_COMPUTE_WITH_ATOMIC_IMAGE_GLSL = """
        #version 460 core
        layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
        layout(binding = 0, r32i) uniform iimage2D endcrystal_img;
        uniform ivec2 _vg_target_size;
        vec4 _vg_gl_fragcoord_raw;
        #define gl_FragCoord _vg_gl_fragcoord_raw
        void _vg_body() {
            imageAtomicAdd(endcrystal_img, ivec2(gl_FragCoord.xy), 1);
        }
        void main() {
            ivec2 px = ivec2(gl_GlobalInvocationID.xy);
            if (px.x >= _vg_target_size.x || px.y >= _vg_target_size.y) return;
            _vg_gl_fragcoord_raw = vec4(vec2(px) + 0.5, 0.0, 1.0);
            _vg_body();
        }
        """;

    @Test
    void compositeFshComputeWithImageAtomic_mslBufferSlotsAreUnique() {
        assertMslBufferSlotsUnique(COMPOSITE_FSH_COMPUTE_WITH_ATOMIC_IMAGE_GLSL);
    }

    private static final String CHUNK_COMPUTE_WITH_INLINE_ATOMIC_GLSL = """
        #version 460 core
        layout(local_size_x = 64) in;
        layout(binding = 0, r32i) uniform iimage2D endcrystal_img;
        uniform int _vg_startVertex;
        uniform int _vg_vertexCount;
        layout(std430, binding = 9) readonly buffer _VgVbuf { uint data[]; } _vg_vbuf;
        void main() {
            { int _vg_prev = imageLoad(endcrystal_img, ivec2(0, 0)).x;
              imageStore(endcrystal_img, ivec2(0, 0), ivec4(_vg_prev + 1, 0, 0, 0)); }
        }
        """;

    @Test
    void chunkComputeWithInlineAtomic_shadercAccepts() {
        final ByteBuffer spirv = compile(CHUNK_COMPUTE_WITH_INLINE_ATOMIC_GLSL, Shaderc.shaderc_compute_shader);
        MemoryUtil.memFree(spirv);
    }

    private static void assertMslBufferSlotsUnique(String glsl) {
        final ByteBuffer spirv = compile(glsl, Shaderc.shaderc_compute_shader);
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL43.GL_COMPUTE_SHADER);
            try {
                final String msl = decode(out.code());
                assertUniqueBufferSlots(msl);
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static void assertUniqueBufferSlots(String msl) {
        final Set<Integer> seen = new HashSet<>();
        int from = 0;
        final String marker = "[[buffer(";
        while (true) {
            final int open = msl.indexOf(marker, from);
            if (open < 0) break;
            final int numStart = open + marker.length();
            final int close = msl.indexOf(')', numStart);
            if (close < 0) break;
            final String numText = msl.substring(numStart, close).trim();
            final int slot;
            try {
                slot = Integer.parseInt(numText);
            } catch (NumberFormatException nfe) {
                from = close + 1;
                continue;
            }
            assertTrue(seen.add(slot),
                "duplicate [[buffer(" + slot + ")]] annotation in MSL output (SDL_GPU MSL atomic emulation collision?):\n" + msl);
            from = close + 1;
        }
        assertFalse(seen.isEmpty(), "expected at least one [[buffer(N)]] in MSL output:\n" + msl);
    }

    private static void assertSlot(String msl, String imageName, int slot, String shader) {
        final Matcher m = Pattern .compile(imageName + "\\s*\\[\\[texture\\((\\d+)\\)\\]\\]").matcher(msl);
        assertTrue(m.find(), shader + ": " + imageName + " missing [[texture(N)]]:\n" + msl);
        assertEquals(slot, Integer.parseInt(m.group(1)), shader + ": " + imageName + " at wrong Metal texture slot:\n" + msl);
    }

    @Test
    void realCullingShadersCrossCompile() throws IOException {
        final String[] names = { "chunk_cull.csh" };
        for (String name : names) {
            Path p = Path.of("src/main/resources/assets/angelica/shaders/culling/" + name);
            if (!Files.exists(p)) {
                p = Path.of("../src/main/resources/assets/angelica/shaders/culling/" + name);
            }
            final String glsl = Files.readString(p);
            final ByteBuffer spirv = compile(glsl, Shaderc.shaderc_compute_shader);
            try {
                ShaderManager.remapSpirvForComputeSDLGPU(spirv);
                final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, GL43.GL_COMPUTE_SHADER);
                try {
                    assertTrue(decode(out.code()).contains("kernel "), name + " should produce compute MSL");
                } finally {
                    MemoryUtil.memFree(out.code());
                }
            } finally {
                MemoryUtil.memFree(spirv);
            }
        }
    }

    private static String decode(ByteBuffer buf) {
        final ByteBuffer slice = buf.duplicate();
        int len = slice.remaining();
        if (len > 0 && slice.get(slice.limit() - 1) == 0) len--;
        final byte[] bytes = new byte[len];
        slice.get(bytes, 0, len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ByteBuffer compile(String source, int shaderKind) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "test_shader",
            SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            fail("Shader compilation failed: " + r.error());
        }
        return r.spirv();
    }
}
