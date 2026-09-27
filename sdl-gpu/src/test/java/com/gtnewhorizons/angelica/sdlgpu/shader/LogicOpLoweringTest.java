package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryUtil.memFree;

class LogicOpLoweringTest {

    private static final int OR_REVERSE = 13;
    private static final int XOR = 6;

    private static long key(int op, int... locClassPairs) {
        long k = op;
        for (int i = 0; i < locClassPairs.length; i += 2) k = LogicOpFormats.withClass(k, locClassPairs[i], locClassPairs[i + 1]);
        return k;
    }

    private static String lower(String source, long key) {
        final String pre = SpirvCompiler.preprocess(ShaderTransformChain.run(source, GL20.GL_FRAGMENT_SHADER), Shaderc.shaderc_fragment_shader, "logicop.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(pre);
        return LogicOpLowering.lower(pre, key, 7);
    }

    private static List<String> compileAndReflectSamplers(String src) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(src, Shaderc.shaderc_fragment_shader, "logicop.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "compile failed: " + r.error() + "\n" + src);
        final ByteBuffer spirv = r.spirv();
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);
            return ShaderManager.reflectStage(spirv, false).samplerNames();
        } finally {
            memFree(spirv);
        }
    }

    @Test
    void singleOutWithEarlyReturn() {
        final int rgba8 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM);
        final String out = lower("""
            #version 330 core
            uniform sampler2D tex;
            in vec2 uv;
            out vec4 color;
            void main() {
                color = texture(tex, uv);
                if (color.a < 0.1) return;
                color.rgb *= 0.5;
            }
            """, key(OR_REVERSE, 0, rgba8));
        assertTrue(out.contains("void angelica_logicOpMain()"), out);
        assertTrue(out.contains("#define ANGELICA_LOGIC_OP 13u"), out);
        assertTrue(out.contains("uniform sampler2D angelica_LogicOpDst0;"), out);
        assertTrue(out.contains("color = angelica_logicOp(color, angelica_LogicOpDst0, vec4(255.0, 255.0, 255.0, 255.0));"), out);
        assertEquals(List.of("tex", "angelica_LogicOpDst0"), compileAndReflectSamplers(out));
    }

    @Test
    void explicitLocationMrtSkipsPassthroughLocation() {
        final int rgba8 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM);
        final int r16 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R16_UNORM);
        final String out = lower("""
            #version 330 core
            layout(location = 2) out vec4 c;
            layout(location = 0) out vec4 a;
            layout(location = 1) out vec4 b;
            void main() { a = vec4(1.0); b = vec4(0.5); c = vec4(0.25); }
            """, key(XOR, 0, rgba8, 2, r16));
        assertTrue(out.contains("a = angelica_logicOp(a, angelica_LogicOpDst0, vec4(255.0, 255.0, 255.0, 255.0));"), out);
        assertTrue(out.contains("c = angelica_logicOp(c, angelica_LogicOpDst2, vec4(65535.0, 0.0, 0.0, 0.0));"), out);
        assertFalse(out.contains("angelica_LogicOpDst1"), out);
        assertFalse(out.contains("b = angelica_logicOp"), out);
        assertEquals(List.of("angelica_LogicOpDst0", "angelica_LogicOpDst2"), compileAndReflectSamplers(out));
    }

    @Test
    void arrayOutExpandsToConsecutiveLocations() {
        final int rgba8 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM);
        final String out = lower("""
            #version 330 core
            #define N 3
            layout(location = 1) out vec4 fragData[N];
            void main() { fragData[0] = vec4(1.0); fragData[1] = vec4(0.0); fragData[2] = vec4(0.5); }
            """, key(OR_REVERSE, 1, rgba8, 3, rgba8));
        assertTrue(out.contains("fragData[0] = angelica_logicOp(fragData[0], angelica_LogicOpDst1,"), out);
        assertTrue(out.contains("fragData[2] = angelica_logicOp(fragData[2], angelica_LogicOpDst3,"), out);
        assertFalse(out.contains("fragData[1] = angelica_logicOp"), out);
        assertEquals(List.of("angelica_LogicOpDst1", "angelica_LogicOpDst3"), compileAndReflectSamplers(out));
    }

    @Test
    void narrowOutputIsWidenedAndNarrowed() {
        final int r8 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8_UNORM);
        final String out = lower("""
            #version 330 core
            out float v;
            void main() { v = 0.5; }
            """, key(XOR, 0, r8));
        assertTrue(out.contains("v = angelica_logicOp(vec4(v, 0.0, 0.0, 0.0), angelica_LogicOpDst0, vec4(255.0, 0.0, 0.0, 0.0)).x;"), out);
        assertEquals(List.of("angelica_LogicOpDst0"), compileAndReflectSamplers(out));
    }

    @Test
    void compatTransformedOutputWithDefaultUniforms() {
        final int rgba8 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM);
        final String out = lower("""
            #version 330 core
            layout (location = 0) out vec4 angelica_FragData0;
            uniform float angelica_currentAlphaTest;
            uniform sampler2D tex;
            uniform vec4 tint;
            in vec2 uv;
            void main() {
                angelica_FragData0 = texture(tex, uv) * tint;
                if (angelica_FragData0.a <= angelica_currentAlphaTest) discard;
            }
            """, key(OR_REVERSE, 0, rgba8));
        assertTrue(out.contains("angelica_FragData0 = angelica_logicOp(angelica_FragData0, angelica_LogicOpDst0,"), out);
        assertEquals(List.of("tex", "angelica_LogicOpDst0"), compileAndReflectSamplers(out));
    }

    @Test
    void ambiguousOutsThrow() {
        final int rgba8 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM);
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> lower("""
            #version 330 core
            out vec4 a;
            out vec4 b;
            void main() { a = vec4(1.0); b = vec4(0.0); }
            """, key(XOR, 0, rgba8)));
        assertTrue(e.getMessage().contains("program 7"), e.getMessage());
    }

    @Test
    void mesaOpMatchesTruthTable() {
        final IntBinaryOperator[] gl = {
            (s, d) -> 0,
            (s, d) -> s & d,
            (s, d) -> s & ~d,
            (s, d) -> s,
            (s, d) -> ~s & d,
            (s, d) -> d,
            (s, d) -> s ^ d,
            (s, d) -> s | d,
            (s, d) -> ~(s | d),
            (s, d) -> ~(s ^ d),
            (s, d) -> ~d,
            (s, d) -> s | ~d,
            (s, d) -> ~s,
            (s, d) -> ~s | d,
            (s, d) -> ~(s & d),
            (s, d) -> ~0,
        };
        for (int i = 0; i < 16; i++) {
            int mode = 0;
            for (int s = 0; s < 2; s++) {
                for (int d = 0; d < 2; d++) {
                    mode |= (gl[i].applyAsInt(s, d) & 1) << (2 * s + d);
                }
            }
            assertEquals(mode, LogicOpFormats.mesaOp(GL11.GL_CLEAR + i), "GL opcode 0x" + Integer.toHexString(GL11.GL_CLEAR + i));
        }
        assertEquals(LogicOpFormats.OP_COPY, LogicOpFormats.mesaOp(GL11.GL_COPY));
        assertEquals(1, LogicOpFormats.mesaOp(GL11.GL_NOR));
        assertEquals(2, LogicOpFormats.mesaOp(GL11.GL_AND_INVERTED));
        assertEquals(4, LogicOpFormats.mesaOp(GL11.GL_AND_REVERSE));
        assertEquals(8, LogicOpFormats.mesaOp(GL11.GL_AND));
    }

    @Test
    void formatClasses() {
        final int rgba = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM);
        final int bgra = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM);
        assertTrue(rgba > 0);
        assertEquals(rgba, bgra);
        for (int c = 0; c < 4; c++) assertEquals(255.0f, LogicOpFormats.channelMax(rgba, c));
        final int b5g6r5 = LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM);
        assertEquals(31.0f, LogicOpFormats.channelMax(b5g6r5, 0));
        assertEquals(63.0f, LogicOpFormats.channelMax(b5g6r5, 1));
        assertEquals(0.0f, LogicOpFormats.channelMax(b5g6r5, 3));
        assertEquals(0, LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB));
        assertEquals(0, LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT));
        assertEquals(0, LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_INVALID));
        assertEquals(0.0f, LogicOpFormats.channelMax(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM));
        assertThrows(UnsupportedOperationException.class, () -> LogicOpFormats.classOf(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT));
    }

    @Test
    void keyPacking() {
        final long k = key(OR_REVERSE, 0, 1, 7, 11);
        assertEquals(OR_REVERSE, LogicOpFormats.opOf(k));
        assertEquals(1, LogicOpFormats.classAt(k, 0));
        assertEquals(0, LogicOpFormats.classAt(k, 3));
        assertEquals(11, LogicOpFormats.classAt(k, 7));
    }
}
