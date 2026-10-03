package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryUtil.memFree;

class ShaderStageSerializerTest {

    private static final PerFrameUniformBlock BLOCK = new PerFrameUniformBlock(List.of(
        new Member("cameraPosition", UniformType.VEC3),
        new Member("rainStrength", UniformType.FLOAT)));

    private static final String VERTEX = """
        #version 460 core
        layout(location = 0) in vec3 a_Position;
        layout(location = 1) in vec2 a_TexCoord;
        layout(location = 0) out vec2 v_TexCoord;
        layout(location = 1) out vec3 v_Color;
        uniform vec3 cameraPosition;
        uniform float rainStrength;
        layout(std140, binding = 0) uniform Scene { mat4 u_Mvp; vec4 u_Tints[3]; float u_Scale; } scene;
        void main() {
            v_TexCoord = a_TexCoord;
            v_Color = cameraPosition * rainStrength * scene.u_Tints[1].xyz * scene.u_Scale;
            gl_Position = scene.u_Mvp * vec4(a_Position, 1.0);
        }
        """;

    private static final String FRAGMENT = """
        #version 460 core
        layout(binding = 0) uniform sampler2D u_TexA;
        layout(binding = 1) uniform sampler2D u_TexB;
        layout(binding = 2, rgba8) readonly uniform image2D u_Img;
        layout(binding = 3) uniform sampler u_Smp;
        layout(binding = 4) uniform texture2D u_Tex;
        layout(location = 0) in vec2 v_TexCoord;
        layout(location = 1) in vec3 v_Color;
        layout(location = 0) out vec4 fragColor;
        layout(location = 1) out vec4 fragColor1;
        void main() {
            fragColor = texture(u_TexA, v_TexCoord) + texture(u_TexB, v_TexCoord) + imageLoad(u_Img, ivec2(0)) + texture(sampler2D(u_Tex, u_Smp), v_TexCoord);
            fragColor1 = vec4(v_Color, 1.0);
        }
        """;

    private record Staged(byte[] spirv, ShaderManager.StageReflection reflection, ShaderManager.GraphicsBindingMap map) {}

    private static Staged stage(String source, int shaderKind, int glType) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "codec", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "shaderc failed: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            final ShaderManager.GraphicsBindingMap map = ShaderManager.remapSpirvForSDLGPU(spirv, glType);
            final ShaderManager.StageReflection reflection = ShaderManager.reflectStage(spirv, glType == GL20.GL_VERTEX_SHADER);
            return new Staged(ShaderCacheIO.toHeap(spirv), reflection, map);
        } finally {
            memFree(spirv);
        }
    }

    private static void assertReflectionEquals(ShaderManager.StageReflection e, ShaderManager.StageReflection a) {
        assertNotNull(a);
        assertEquals(e.counts(), a.counts());
        assertEquals(e.samplerNames(), a.samplerNames());
        assertEquals(e.extraUniformNames(), a.extraUniformNames());
        assertEquals(e.storageImageNames(), a.storageImageNames());
        assertEquals(e.uboSize(), a.uboSize());
        assertEquals(e.uboMembers(), a.uboMembers());
        assertEquals(e.vsInputs(), a.vsInputs());
        assertEquals(e.vsOutputs(), a.vsOutputs());
        assertEquals(e.fsInputs(), a.fsInputs());
        assertEquals(e.maxOutputLocation(), a.maxOutputLocation());
        assertEquals(e.numReadonlyStorageBuffers(), a.numReadonlyStorageBuffers());
        assertEquals(e.numReadwriteStorageBuffers(), a.numReadwriteStorageBuffers());
        assertEquals(e.numReadonlyStorageTextures(), a.numReadonlyStorageTextures());
        assertEquals(e.numReadwriteStorageTextures(), a.numReadwriteStorageTextures());
        assertEquals(e.blocks().length, a.blocks().length);
        for (int b = 0; b < e.blocks().length; b++) assertEquals(e.blocks()[b], a.blocks()[b], "block " + b);
    }

    private static void assertBindingMapEquals(ShaderManager.GraphicsBindingMap e, ShaderManager.GraphicsBindingMap a) {
        assertNotNull(a);
        assertArrayEquals(e.roStorageTextureGlSlots(), a.roStorageTextureGlSlots());
        assertArrayEquals(e.rwStorageTextureGlSlots(), a.rwStorageTextureGlSlots());
        assertArrayEquals(e.roSsboGlSlots(), a.roSsboGlSlots());
    }

    private static byte[] encodedVertex() {
        final String src = ShaderTransformChain.inject(VERTEX, BLOCK, null);
        final Staged s = stage(src, Shaderc.shaderc_vertex_shader, GL20.GL_VERTEX_SHADER);
        return ShaderStageSerializer.encode(s.spirv(), s.reflection(), s.map(), Set.of("b"), src);
    }

    @Test
    void vertexStageRoundTrips() {
        final String src = ShaderTransformChain.inject(VERTEX, BLOCK, null);
        final Staged s = stage(src, Shaderc.shaderc_vertex_shader, GL20.GL_VERTEX_SHADER);
        assertFalse(s.reflection().vsInputs().isEmpty());
        assertFalse(s.reflection().vsOutputs().isEmpty());
        assertTrue(s.reflection().uboMembers().stream().anyMatch(m -> m.arrayLen() > 1), "needs an array member");
        assertFalse(s.reflection().blocks()[ShaderManager.BLOCK_PER_FRAME].members().isEmpty(), "needs the per-frame block");

        final byte[] enc = ShaderStageSerializer.encode(s.spirv(), s.reflection(), s.map(), Set.of("u_flag", "u_other"), src);
        final ShaderStageSerializer.Stage d = ShaderStageSerializer.decode(enc);
        assertNotNull(d);
        assertArrayEquals(s.spirv(), d.spirv());
        assertReflectionEquals(s.reflection(), d.reflection());
        assertBindingMapEquals(s.map(), d.bindingMap());
        assertEquals(Set.of("u_flag", "u_other"), d.boolUniforms());
        assertEquals(src, d.source());
    }

    @Test
    void fragmentStageRoundTrips() {
        final Staged s = stage(FRAGMENT, Shaderc.shaderc_fragment_shader, GL20.GL_FRAGMENT_SHADER);
        assertEquals(List.of("u_TexA", "u_TexB"), s.reflection().samplerNames());
        assertFalse(s.reflection().storageImageNames().isEmpty(), "needs a storage image");
        assertTrue(s.reflection().extraUniformNames().contains("u_Smp"), "needs a separate sampler");
        assertFalse(s.reflection().fsInputs().isEmpty());
        assertEquals(1, s.reflection().maxOutputLocation());

        final ShaderStageSerializer.Stage d = ShaderStageSerializer.decode(ShaderStageSerializer.encode(s.spirv(), s.reflection(), s.map(), Set.of(), FRAGMENT));
        assertNotNull(d);
        assertArrayEquals(s.spirv(), d.spirv());
        assertReflectionEquals(s.reflection(), d.reflection());
        assertBindingMapEquals(s.map(), d.bindingMap());
        assertTrue(d.boolUniforms().isEmpty());
        assertEquals(FRAGMENT, d.source());
    }

    @Test
    void nullableFieldsSurvive() {
        final byte[] spirv = {1, 2, 3, 4};
        final ShaderStageSerializer.Stage d = ShaderStageSerializer.decode(ShaderStageSerializer.encode(spirv, ShaderManager.StageReflection.EMPTY, ShaderManager.GraphicsBindingMap.EMPTY, null, null));
        assertNotNull(d);
        assertArrayEquals(spirv, d.spirv());
        assertReflectionEquals(ShaderManager.StageReflection.EMPTY, d.reflection());
        assertBindingMapEquals(ShaderManager.GraphicsBindingMap.EMPTY, d.bindingMap());
        assertNull(d.source());
        assertTrue(d.boolUniforms().isEmpty());
    }

    @Test
    void boolUniformOrderDoesNotChangeTheBytes() {
        final byte[] spirv = {1, 2, 3, 4};
        final Set<String> forward = new LinkedHashSet<>(List.of("a", "b", "c", "d"));
        final Set<String> backward = new LinkedHashSet<>(List.of("d", "c", "b", "a"));
        final byte[] forwardBytes = ShaderStageSerializer.encode(spirv, ShaderManager.StageReflection.EMPTY, ShaderManager.GraphicsBindingMap.EMPTY, forward, "s");
        final byte[] backwardBytes = ShaderStageSerializer.encode(spirv, ShaderManager.StageReflection.EMPTY, ShaderManager.GraphicsBindingMap.EMPTY, backward, "s");
        assertArrayEquals(forwardBytes, backwardBytes);
    }

    @Test
    void transformRoundTripsAndSortsBools() {
        final Set<String> forward = new LinkedHashSet<>(List.of("a", "b", "c"));
        final Set<String> backward = new LinkedHashSet<>(List.of("c", "b", "a"));
        final byte[] enc = ShaderStageSerializer.encodeTransform(FRAGMENT, forward);
        assertArrayEquals(enc, ShaderStageSerializer.encodeTransform(FRAGMENT, backward));
        final ShaderManager.PrewarmTransformResult d = ShaderStageSerializer.decodeTransform(enc);
        assertNotNull(d);
        assertEquals(FRAGMENT, d.source());
        assertEquals(Set.of("a", "b", "c"), d.boolUniforms());
        assertTrue(ShaderStageSerializer.decodeTransform(ShaderStageSerializer.encodeTransform("", null)).boolUniforms().isEmpty());
    }

    @Test
    void truncatedPayloadsAreRejected() {
        final byte[] enc = encodedVertex();
        for (int len = 0; len < enc.length; len++) {
            assertNull(ShaderStageSerializer.decode(Arrays.copyOf(enc, len)), "prefix of " + len + " bytes decoded");
        }
        final byte[] transform = ShaderStageSerializer.encodeTransform(FRAGMENT, Set.of("a", "b"));
        for (int len = 0; len < transform.length; len++) {
            assertNull(ShaderStageSerializer.decodeTransform(Arrays.copyOf(transform, len)), "prefix of " + len + " bytes decoded");
        }
    }

    @Test
    void hugeLengthPrefixIsRejectedWithoutAllocating() {
        final byte[] enc = encodedVertex();
        final byte[] bad = enc.clone();
        bad[0] = 0x7f;
        bad[1] = (byte) 0xff;
        bad[2] = (byte) 0xff;
        bad[3] = (byte) 0xff;
        assertNull(ShaderStageSerializer.decode(bad));
    }
}
