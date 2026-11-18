package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlUniformArrayTest {

    private static final int COUNT = 8;
    private static final int[][] A = {
        {10, 20, 30}, {40, 50, 60}, {70, 80, 90}, {100, 110, 120},
        {130, 140, 150}, {160, 170, 180}, {190, 200, 210}, {220, 230, 240}
    };
    private static final int[] X = {250, 5, 15};
    private static final int[][] B = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {11, 12, 13}};

    private int program;
    private int vao;
    private int arr;
    private int single;
    private int pick;

    @BeforeEach
    void setUp() {
        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, """
            #version 330 core
            uniform vec4 arr[8];
            uniform vec4 single;
            uniform ivec4 iarr[2];
            uniform vec4 one[1];
            uniform int pick;
            out vec4 color;
            void main() { color = pick == -2 ? vec4(iarr[1]) / 255.0 : pick == -3 ? one[0] : pick < 0 ? single : arr[pick]; }
            """);
        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMPrograms.glUseProgram(program);
        arr = loc("arr");
        single = loc("single");
        pick = loc("pick");
    }

    @AfterEach
    void tearDown() {
        GLSMPrograms.glUseProgram(0);
        GLSMVertexInput.glBindVertexArray(0);
        GLSMVertexInput.glDeleteVertexArrays(vao);
        GLSMPrograms.glDeleteProgram(program);
    }

    private int loc(String name) {
        return GLSMPrograms.glGetUniformLocation(program, name);
    }

    private static int argb(int[] rgb) {
        return 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    private static float[] vec(int[] rgb) {
        return new float[] {rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f, 1f};
    }

    private static FloatBuffer pack(int[]... colors) {
        final FloatBuffer buf = MemoryUtil.memAllocFloat(colors.length * 4);
        for (int[] c : colors) buf.put(vec(c));
        buf.flip();
        return buf;
    }

    private void writeAt(int location, int[]... colors) {
        final FloatBuffer buf = pack(colors);
        try {
            GLSMPrograms.glUniform4(location, buf);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    private void writeElement(int location, int[] rgb) {
        final float[] v = vec(rgb);
        GLSMPrograms.glUniform4f(location, v[0], v[1], v[2], v[3]);
    }

    private void writeAll() {
        writeAt(arr, A);
    }

    private int drawPick(int index) {
        GlsmSdlHeadlessRig.beginFrame();
        GlsmSdlHeadlessRig.bindTarget();
        GLSMPrograms.glUniform1i(pick, index);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        return GlsmSdlHeadlessRig.readTarget(GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE / 2, 1, 1)[0];
    }

    private void assertPick(int index, int[] expected, String label) {
        final int got = drawPick(index);
        assertEquals(Integer.toHexString(argb(expected)), Integer.toHexString(got), label + ": " + GlsmSdlHeadlessRig.describe(got));
    }

    private float[] readUniform(int location) {
        final FloatBuffer buf = MemoryUtil.memAllocFloat(4);
        try {
            GLSMPrograms.glGetUniform(program, location, buf);
            final float[] out = new float[4];
            buf.get(out);
            return out;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    @Test
    void arrayElementLocations() {
        assertNotEquals(-1, arr, "arr");
        assertNotEquals(-1, single, "single");
        assertEquals(arr, loc("arr[0]"), "arr[0]");
        assertEquals(arr + 3, loc("arr[3]"), "arr[3]");
        assertEquals(arr + 7, loc("arr[7]"), "arr[7]");
        assertEquals(-1, loc("arr[8]"), "arr[8]");
        assertEquals(-1, loc("arr[03]"), "arr[03]");
        assertEquals(-1, loc("arr[-1]"), "arr[-1]");
        assertEquals(arr, loc("arr[]"), "arr[] is index 0, as Mesa parses it");
        assertEquals(single, loc("single[0]"), "single[0]");
        assertEquals(-1, loc("single[1]"), "single[1]");
    }

    @Test
    void elementWriteLandsInTheArray() {
        writeAll();
        final int element3 = loc("arr[3]");
        writeElement(element3, X);

        assertPick(3, X, "pick=3 shows the element write");
        assertPick(2, A[2], "pick=2 keeps the bulk write");
        assertArrayEquals(vec(X), readUniform(element3), 0f, "glGetUniform arr[3]");
        assertArrayEquals(vec(A[2]), readUniform(loc("arr[2]")), 0f, "glGetUniform arr[2]");
    }

    @Test
    void elementWriteClampsToTheArrayEnd() {
        writeAll();
        writeAt(loc("arr[6]"), B);

        assertPick(6, B[0], "pick=6");
        assertPick(7, B[1], "pick=7");
        assertPick(5, A[5], "pick=5");
    }

    @Test
    void countBeyondTheDeclarationFollowsMesa() {
        writeElement(single, A[0]);
        writeAt(single, B);

        assertPick(-1, A[0], "count 2 on a non-array is ignored");
        assertArrayEquals(vec(A[0]), readUniform(single), 0f, "glGetUniform single keeps its value");

        writeAt(loc("one"), B);
        assertPick(-3, B[0], "count 2 on a size-1 array clamps");
    }

    @Test
    void fullWriteAfterElementWriteIsNotElided() {
        writeAll();
        writeElement(loc("arr[3]"), X);
        writeAll();

        assertPick(3, A[3], "pick=3 after the repeated full write");
    }

    @Test
    void intElementReadsBackAsInts() {
        final int element1 = loc("iarr[1]");
        assertEquals(loc("iarr") + 1, element1, "iarr[1]");
        GLSMPrograms.glUniform4i(loc("iarr"), 1, 2, 3, 4);
        GLSMPrograms.glUniform4i(element1, X[0], X[1], X[2], 255);

        assertPick(-2, X, "pick=-2 shows iarr[1]");
        assertArrayEquals(new int[] {X[0], X[1], X[2], 255}, readIntUniform(element1), "glGetUniformiv iarr[1]");
        assertArrayEquals(new int[] {1, 2, 3, 4}, readIntUniform(loc("iarr")), "glGetUniformiv iarr[0]");
    }

    private int[] readIntUniform(int location) {
        final IntBuffer buf = MemoryUtil.memAllocInt(4);
        try {
            GLSMPrograms.glGetUniform(program, location, buf);
            final int[] out = new int[4];
            buf.get(out);
            return out;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    @Test
    void activeUniformReportsArrayNameWithIndexZero() {
        final Map<String, Integer> sizes = activeUniformSizes();
        assertEquals(COUNT, sizes.getOrDefault("arr[0]", -1), "size of arr[0] in " + sizes);
        assertEquals(1, sizes.getOrDefault("single", -1), "size of single in " + sizes);
        assertEquals(1, sizes.getOrDefault("one[0]", -1), "size of one[0] in " + sizes);
    }

    private Map<String, Integer> activeUniformSizes() {
        final int active = GLSMPrograms.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        final Map<String, Integer> sizes = new HashMap<>();
        final IntBuffer length = MemoryUtil.memAllocInt(1);
        final IntBuffer size = MemoryUtil.memAllocInt(1);
        final IntBuffer type = MemoryUtil.memAllocInt(1);
        final ByteBuffer name = MemoryUtil.memAlloc(128);
        try {
            for (int i = 0; i < active; i++) {
                GLSMPrograms.glGetActiveUniform(program, i, length, size, type, name);
                final byte[] bytes = new byte[length.get(0)];
                for (int c = 0; c < bytes.length; c++) bytes[c] = name.get(c);
                sizes.put(new String(bytes, java.nio.charset.StandardCharsets.US_ASCII), size.get(0));
            }
        } finally {
            MemoryUtil.memFree(name);
            MemoryUtil.memFree(type);
            MemoryUtil.memFree(size);
            MemoryUtil.memFree(length);
        }
        return sizes;
    }
}
