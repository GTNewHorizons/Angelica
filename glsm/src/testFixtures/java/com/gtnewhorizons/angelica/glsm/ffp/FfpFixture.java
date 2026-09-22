package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class FfpFixture {

    private FfpFixture() {}

    public static final class IntegerInstances {

        private static final int INSTANCES = 3;

        private static final String VS_HEAD = """
            #version 330 core
            layout(location = 0) in vec3 a_Position;
            layout(location = 5) in vec4 a_InstRow0;
            layout(location = 6) in vec4 a_InstRow1;
            layout(location = 7) in vec4 a_InstRow2;
            layout(location = 11) in ivec4 iris_Entity;
            flat out vec3 v_Color;
            void main() {
            """;

        private static final String VS_TAIL = """
              gl_Position = instMV * vec4(a_Position, 1.0);
              if (iris_Entity.x == -1) { v_Color = vec3(1.0, 0.0, 0.0); }
              else if (iris_Entity.x == 7) { v_Color = vec3(0.0, 1.0, 0.0); }
              else { v_Color = vec3(0.0, 0.0, 1.0); }
            }
            """;

        private static final String VS = VS_HEAD + "  mat4 instMV = " + InstancedGlslHelpers.mat4FromRows("a_InstRow0", "a_InstRow1", "a_InstRow2") + ";\n" + VS_TAIL;

        private static final String FS = """
            #version 330 core
            flat in vec3 v_Color;
            out vec4 fragColor;
            void main() { fragColor = vec4(v_Color, 1.0); }
            """;

        private static int program;
        private static int vao;
        private static int templateVbo;
        private static int instanceVbo;

        private IntegerInstances() {}

        public static void build(float spacing) {
            program = buildProgram();
            GLStateManager.glUseProgram(program);

            buildTemplate();
            buildInstances(new float[] { -spacing, 0.0f, spacing }, new int[] { -1, 7, 42 });
        }

        public static void draw() {
            GLStateManager.glDrawArraysInstanced(GL11.GL_QUADS, 0, 4, INSTANCES);
        }

        public static void delete() {
            GLStateManager.glUseProgram(0);
            if (program != 0) { GLStateManager.glDeleteProgram(program); program = 0; }
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (templateVbo != 0) { GLStateManager.glDeleteBuffers(templateVbo); templateVbo = 0; }
            if (instanceVbo != 0) { GLStateManager.glDeleteBuffers(instanceVbo); instanceVbo = 0; }
            GLStateManager.glBindVertexArray(0);
            if (vao != 0) { GLStateManager.glDeleteVertexArrays(vao); vao = 0; }
        }

        private static void buildTemplate() {
            vao = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(vao);

            final ByteBuffer quad = BufferUtils.createByteBuffer(4 * 12);
            putVertex(quad, -0.15f, -0.8f);
            putVertex(quad, 0.15f, -0.8f);
            putVertex(quad, 0.15f, 0.8f);
            putVertex(quad, -0.15f, 0.8f);
            quad.flip();

            templateVbo = GLStateManager.glGenBuffers();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, templateVbo);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, quad, GL15.GL_STATIC_DRAW);
            GLStateManager.glEnableVertexAttribArray(0);
            GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        private static void putVertex(ByteBuffer buf, float x, float y) {
            final int base = buf.position();
            buf.putFloat(base, x).putFloat(base + 4, y).putFloat(base + 8, 0f);
            buf.position(base + 12);
        }

        private static void buildInstances(float[] translateX, int[] entities) {
            final ByteBuffer instances = BufferUtils.createByteBuffer(entities.length * InstancedAttribs.STRIDE);
            final long base0 = memAddress0(instances);
            for (int i = 0; i < entities.length; i++) {
                final long ptr = base0 + (long) i * InstancedAttribs.STRIDE;
                InstancedAttribs.writeHead(ptr, translationMatrix(translateX[i]), 0, 0xFFFFFFFF, 0, InstancedAttribs.packEntityInfo(entities[i], 0, 0));
            }
            instances.position(0).limit(entities.length * InstancedAttribs.STRIDE);

            instanceVbo = GLStateManager.glGenBuffers();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STATIC_DRAW);
            InstancedAttribs.enableHeadArrays();
            InstancedAttribs.pointTemplate(0L);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        private static int buildProgram() {
            final int vs = compile(GL20.GL_VERTEX_SHADER, VS);
            final int fs = compile(GL20.GL_FRAGMENT_SHADER, FS);
            final int p = GLStateManager.glCreateProgram();
            GLStateManager.glAttachShader(p, vs);
            GLStateManager.glAttachShader(p, fs);
            GLStateManager.glLinkProgram(p);
            assertNotEquals(0, GLStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS), () -> "test program must link: " + GLStateManager.glGetProgramInfoLog(p, 4096));
            GLStateManager.glDeleteShader(vs);
            GLStateManager.glDeleteShader(fs);
            return p;
        }

        private static int compile(int type, String source) {
            final int shader = GLStateManager.glCreateShader(type);
            GLStateManager.glShaderSource(shader, source);
            GLStateManager.glCompileShader(shader);
            if (GLStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
                fail("test shader failed to compile:\n" + GLStateManager.glGetShaderInfoLog(shader, 4096) + "\n\n" + source);
            }
            return shader;
        }
    }

    public static void attrib(int loc, int size, int type, boolean normalized, int stride, int offset) {
        GLStateManager.glEnableVertexAttribArray(loc);
        GLStateManager.glVertexAttribPointer(loc, size, type, normalized, stride, offset);
    }

    public static void clear() {
        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public static int[] readRegion(int size) {
        final ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        GLStateManager.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        final int[] out = new int[size * size];
        for (int i = 0; i < out.length; i++) {
            out[i] = pixels.getInt(i * 4);
        }
        return out;
    }

    public static int[] readPixel(int x, int y) {
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GLStateManager.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return new int[] { pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF, pixel.get(3) & 0xFF };
    }

    public static int solidTexture(int r, int g, int b, int a) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        final ByteBuffer texels = BufferUtils.createByteBuffer(2 * 2 * 4);
        for (int i = 0; i < 4; i++) {
            texels.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        }
        texels.flip();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        return id;
    }

    public static float[] translationMatrix(float x) {
        return new float[] {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            x, 0f, 0f, 1f,
        };
    }

    public static void assertPixelParity(String subject, int[] reference, int[] candidate, int size, int background, int minCoverageDivisor, int channelTolerance, IntFunction<String> describe) {
        int covered = 0;
        for (int pixel : reference) {
            if (pixel != background) covered++;
        }
        final int coveredPixels = covered;
        assertTrue(coveredPixels > reference.length / minCoverageDivisor, () -> "reference draw covered only " + coveredPixels + " of " + reference.length + " pixels");

        int mismatches = 0;
        int firstMismatch = -1;
        for (int i = 0; i < reference.length; i++) {
            if (!withinTolerance(reference[i], candidate[i], channelTolerance)) {
                mismatches++;
                if (firstMismatch < 0) firstMismatch = i;
            }
        }
        final int index = firstMismatch;
        final int differing = mismatches;
        assertEquals(0, mismatches, () -> subject + " differ from baked quads in " + differing + " of " + reference.length + " pixels, first at (" + (index % size) + "," + (index / size) + "): reference " + describe.apply(reference[index]) + " instanced " + describe.apply(candidate[index]));
    }

    private static boolean withinTolerance(int a, int b, int tolerance) {
        if (tolerance == 0) return a == b;
        for (int shift = 0; shift <= 24; shift += 8) {
            final int ca = (a >> shift) & 0xFF;
            final int cb = (b >> shift) & 0xFF;
            if (Math.abs(ca - cb) > tolerance) return false;
        }
        return true;
    }

    public static void assertOverlayFootprint(int[] base, int[] overlay, int background, String overlayName) {
        int covered = 0;
        for (int i = 0; i < base.length; i++) {
            final int index = i;
            if (base[i] != background) {
                covered++;
                assertTrue(base[i] != overlay[i], () -> "pixel " + index + " covered by the base draw did not change under the " + overlayName + ": 0x" + Integer.toHexString(overlay[index]));
            } else {
                assertEquals(base[i], overlay[i], () -> "pixel " + index + " outside the base footprint must not be touched by the " + overlayName);
            }
        }
        final int coveredPixels = covered;
        assertTrue(coveredPixels > base.length / 8, "base draw covered only " + coveredPixels + " of " + base.length + " pixels");
    }

    public static void resetFfpState() {
        GLStateManager.ffpInstancing = Instancing.NONE;
        final ShaderManager sm = ShaderManager.getInstance();
        if (sm.isActive()) sm.deactivate();
        sm.disable();
        GLStateManager.glBindVertexArray(0);

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDepthMask(true);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDisable(GL11.GL_CULL_FACE);
        GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
    }
}
