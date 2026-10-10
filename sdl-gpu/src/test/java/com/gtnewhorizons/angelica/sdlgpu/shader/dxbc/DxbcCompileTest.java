package com.gtnewhorizons.angelica.sdlgpu.shader.dxbc;

import com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO;
import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders;
import com.gtnewhorizons.angelica.sdlgpu.shader.cross.CrossCompileCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders.compileOrFail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
class DxbcCompileTest {
    private static CrossCompileCache cache() { return Reflect.getStatic(DxbcCrossCompile.class, "CACHE"); }

    @AfterEach
    void resetDiskCache() {
        Reflect.setStatic(ShaderDiskCache.class, "root", null);
        Reflect.<Map<?, ?>>get(cache(), "cache").clear();
    }

    private static final byte DXBC_MAGIC_D = 'D';
    private static final byte DXBC_MAGIC_X = 'X';
    private static final byte DXBC_MAGIC_B = 'B';
    private static final byte DXBC_MAGIC_C = 'C';

    static Stream<Arguments> stages() {
        return Stream.of(
            Arguments.of("vertex", SpirvTestShaders.VERTEX_GLSL, Shaderc.shaderc_vertex_shader, GL20.GL_VERTEX_SHADER),
            Arguments.of("fragment", SpirvTestShaders.FRAGMENT_GLSL, Shaderc.shaderc_fragment_shader, GL20.GL_FRAGMENT_SHADER),
            Arguments.of("compute", SpirvTestShaders.HIZ_INIT_GLSL, Shaderc.shaderc_compute_shader, GL43.GL_COMPUTE_SHADER));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stages")
    void shaderCrossCompilesAndProducesDxbc(String name, String glsl, int shadercKind, int glStage) {
        final ByteBuffer spirv = compileOrFail(glsl, shadercKind);
        try {
            if (glStage == GL43.GL_COMPUTE_SHADER) {
                ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            } else {
                ShaderManager.remapSpirvForSDLGPU(spirv, glStage);
            }
            final CrossCompileCache.Output out = DxbcCrossCompile.compile(spirv, glStage);
            try {
                assertNotNull(out.code());
                assertTrue(out.code().remaining() >= 4, "DXBC " + name + " output too short");
                assertDxbcMagic(out.code());
                assertEquals("main", out.entrypoint());
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void garbageHlslSurfacesD3DCompileError() {
        final String bad = "this is not valid hlsl !!!";
        final RuntimeException ex = assertThrows(RuntimeException.class,
            () -> {
                final ByteBuffer code = D3DCompiler.compile(bad, "main", "vs_5_0", D3DCompiler.D3DCOMPILE_OPTIMIZATION_LEVEL3 | D3DCompiler.D3DCOMPILE_ENABLE_STRICTNESS, 0);
                MemoryUtil.memFree(code);
            });
        assertTrue(ex.getMessage().contains("D3DCompile failed"), "Exception message should mention D3DCompile failure: " + ex.getMessage());
        assertTrue(ex.getMessage().length() > "D3DCompile failed (HRESULT=0x80004005): ".length(), "Exception message should include error blob text: " + ex.getMessage());
    }

    @Test
    void diskCacheHitReturnsStoredBlob(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        Reflect.<Map<?, ?>>get(cache(), "cache").clear();
        final ByteBuffer spirv = compileOrFail(SpirvTestShaders.VERTEX_GLSL, Shaderc.shaderc_vertex_shader);
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_VERTEX_SHADER);
            final byte[] heap = ShaderCacheIO.toHeap(spirv);
            MemoryUtil.memFree(DxbcCrossCompile.compile(spirv, GL20.GL_VERTEX_SHADER).code());
            Reflect.<Map<?, ?>>get(cache(), "cache").clear();
            ShaderDiskCache.putBlob(cache().diskKey(heap, GL20.GL_VERTEX_SHADER), "poison_entry", "poison".getBytes(StandardCharsets.UTF_8));
            final CrossCompileCache.Output out = DxbcCrossCompile.compile(spirv, GL20.GL_VERTEX_SHADER);
            try {
                assertEquals("poison_entry", out.entrypoint());
                assertEquals("poison", new String(ShaderCacheIO.toHeap(out.code()), StandardCharsets.UTF_8));
            } finally {
                MemoryUtil.memFree(out.code());
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static void assertDxbcMagic(ByteBuffer dxbc) {
        final ByteBuffer view = dxbc.duplicate();
        assertEquals(DXBC_MAGIC_D, view.get(0), "DXBC magic byte 0");
        assertEquals(DXBC_MAGIC_X, view.get(1), "DXBC magic byte 1");
        assertEquals(DXBC_MAGIC_B, view.get(2), "DXBC magic byte 2");
        assertEquals(DXBC_MAGIC_C, view.get(3), "DXBC magic byte 3");
    }
}
