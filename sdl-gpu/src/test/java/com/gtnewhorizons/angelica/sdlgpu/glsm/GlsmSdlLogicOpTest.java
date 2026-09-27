package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.LogicOpFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlLogicOpTest {

    private static final String VERT = """
        #version 330 core
        layout(location = 0) in vec3 a_Position;
        void main() {
            gl_Position = vec4(a_Position, 1.0);
        }
        """;

    private static final String FRAG_SINGLE = """
        #version 330 core
        out vec4 fragColor;
        void main() {
            if (gl_FragCoord.x < 0.0) return;
            fragColor = vec4(51.0, 153.0, 230.0, 128.0) / 255.0;
        }
        """;

    private static final String FRAG_MRT = """
        #version 330 core
        layout(location = 0) out vec4 color0;
        layout(location = 1) out vec4 color1;
        void main() {
            color0 = vec4(51.0, 153.0, 230.0, 128.0) / 255.0;
            color1 = vec4(51.0, 102.0, 204.0, 255.0) / 255.0;
        }
        """;

    private static final int[] PROGRAM_SRC = LogicOpFixture.SOURCES[1];

    private static int singleProgram;
    private static int mrtProgram;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @AfterEach
    void cleanup() {
        GLStateManager.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GLStateManager.glLogicOp(GL11.GL_COPY);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glUseProgram(0);
        LogicOpFixture.deleteResources();
    }

    @Test
    void everyOpMatchesTheBitwiseReference() {
        for (int op : LogicOpFixture.OPS) {
            for (int[] src : LogicOpFixture.SOURCES) {
                GlsmSdlHeadlessRig.beginFrame();
                GlsmSdlHeadlessRig.bindTarget();
                LogicOpFixture.clearToDst();
                LogicOpFixture.drawQuad(op, src);
                assertCenter(op, src, LogicOpFixture.label(op, src));
            }
        }
    }

    @Test
    void blendingIsIgnoredWhileLogicOpIsEnabled() {
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        final int[] src = LogicOpFixture.SOURCES[1];
        LogicOpFixture.clearToDst();
        LogicOpFixture.drawQuad(GL11.GL_XOR, src);
        assertCenter(GL11.GL_XOR, src, "blend enabled under XOR");
    }

    @Test
    void disablingLogicOpRestoresBaseShaderAndBlending() {
        final int[] src = LogicOpFixture.SOURCES[1];
        LogicOpFixture.clearToDst();
        LogicOpFixture.drawQuad(GL11.GL_XOR, src);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        LogicOpFixture.quad(src);

        final int got = centerPixel();
        final int r = Math.min(255, LogicOpFixture.expected(GL11.GL_XOR, src[0], LogicOpFixture.DST_R) + src[0]);
        final int g = Math.min(255, LogicOpFixture.expected(GL11.GL_XOR, src[1], LogicOpFixture.DST_G) + src[1]);
        final int b = Math.min(255, LogicOpFixture.expected(GL11.GL_XOR, src[2], LogicOpFixture.DST_B) + src[2]);
        final int a = Math.min(255, LogicOpFixture.expected(GL11.GL_XOR, src[3], LogicOpFixture.DST_A) + src[3]);
        assertEquals((a << 24) | (r << 16) | (g << 8) | b, got, () -> "additive after XOR: " + describe(got));
    }

    @Test
    void userProgramIsLowered() {
        singleProgram = buildProgram(singleProgram, FRAG_SINGLE);
        for (int op : new int[] { GL11.GL_OR_REVERSE, GL11.GL_COPY_INVERTED, GL11.GL_OR_INVERTED, GL11.GL_XOR }) {
            GlsmSdlHeadlessRig.beginFrame();
            GlsmSdlHeadlessRig.bindTarget();
            LogicOpFixture.clearToDst();
            GLStateManager.glUseProgram(singleProgram);
            LogicOpFixture.drawQuad(op, PROGRAM_SRC);
            GLStateManager.glUseProgram(0);
            assertCenter(op, PROGRAM_SRC, "user program " + LogicOpFixture.label(op, PROGRAM_SRC));
        }
    }

    @Test
    void floatAttachmentPassesThroughWhileUnormAttachmentIsLowered() {
        mrtProgram = buildProgram(mrtProgram, FRAG_MRT);
        final int unorm = colorTexture(GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
        final int half = colorTexture(GL30.GL_RGBA16F, GL11.GL_FLOAT);
        final int fbo = GLStateManager.glGenFramebuffers();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, unorm, 0);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, half, 0);
        final IntBuffer bufs = BufferUtils.createIntBuffer(2);
        bufs.put(GL30.GL_COLOR_ATTACHMENT0).put(GL30.GL_COLOR_ATTACHMENT1).flip();
        GLStateManager.glDrawBuffers(bufs);
        GLStateManager.glViewport(0, 0, SIZE, SIZE);

        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        LogicOpFixture.clearToDst();
        GLStateManager.glUseProgram(mrtProgram);
        LogicOpFixture.drawQuad(GL11.GL_OR_REVERSE, PROGRAM_SRC);
        GLStateManager.glUseProgram(0);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GlsmSdlHeadlessRig.endFrame();

        GLStateManager.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        final ByteBuffer px = BufferUtils.createByteBuffer(4);
        GLStateManager.glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
        assertEquals(LogicOpFixture.expected(GL11.GL_OR_REVERSE, PROGRAM_SRC[0], LogicOpFixture.DST_R), px.get(0) & 0xFF, "unorm red");
        assertEquals(LogicOpFixture.expected(GL11.GL_OR_REVERSE, PROGRAM_SRC[1], LogicOpFixture.DST_G), px.get(1) & 0xFF, "unorm green");
        assertEquals(LogicOpFixture.expected(GL11.GL_OR_REVERSE, PROGRAM_SRC[2], LogicOpFixture.DST_B), px.get(2) & 0xFF, "unorm blue");
        assertEquals(LogicOpFixture.expected(GL11.GL_OR_REVERSE, PROGRAM_SRC[3], LogicOpFixture.DST_A), px.get(3) & 0xFF, "unorm alpha");

        GLStateManager.glReadBuffer(GL30.GL_COLOR_ATTACHMENT1);
        final ByteBuffer fpx = BufferUtils.createByteBuffer(16).order(ByteOrder.nativeOrder());
        GLStateManager.glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, fpx);
        assertEquals(0.2f, fpx.getFloat(0), 1e-3f, "float red passes through unblended");
        assertEquals(0.4f, fpx.getFloat(4), 1e-3f, "float green passes through unblended");
        assertEquals(0.8f, fpx.getFloat(8), 1e-3f, "float blue passes through unblended");
        assertEquals(1.0f, fpx.getFloat(12), 1e-3f, "float alpha passes through unblended");
        GLStateManager.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        GLStateManager.glDeleteFramebuffers(fbo);
        GLStateManager.glDeleteTextures(unorm);
        GLStateManager.glDeleteTextures(half);
    }

    private static int colorTexture(int internalFormat, int type) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, SIZE, SIZE, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return id;
    }

    private static int buildProgram(int cached, String frag) {
        if (cached != 0) return cached;
        final int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
        final int fs = compile(GL20.GL_FRAGMENT_SHADER, frag);
        final int p = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(p, vs);
        GLStateManager.glAttachShader(p, fs);
        GLStateManager.glLinkProgram(p);
        assertTrue(GLStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS) != 0, "program did not link");
        return p;
    }

    private static int compile(int type, String src) {
        final int s = GLStateManager.glCreateShader(type);
        GLStateManager.glShaderSource(s, src);
        GLStateManager.glCompileShader(s);
        assertTrue(GLStateManager.glGetShaderi(s, GL20.GL_COMPILE_STATUS) != 0, () -> "shader: " + GLStateManager.glGetShaderInfoLog(s));
        return s;
    }

    private static int centerPixel() {
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        return pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2);
    }

    private static void assertCenter(int op, int[] src, String label) {
        final int got = centerPixel();
        final int r = LogicOpFixture.expected(op, src[0], LogicOpFixture.DST_R);
        final int g = LogicOpFixture.expected(op, src[1], LogicOpFixture.DST_G);
        final int b = LogicOpFixture.expected(op, src[2], LogicOpFixture.DST_B);
        final int a = LogicOpFixture.expected(op, src[3], LogicOpFixture.DST_A);
        assertEquals((a << 24) | (r << 16) | (g << 8) | b, got, () -> label + ": " + describe(got));
    }
}
