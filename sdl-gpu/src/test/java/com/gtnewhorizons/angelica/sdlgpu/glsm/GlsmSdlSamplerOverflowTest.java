package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextureLifetime;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A stage reading more samplers than SDL_GPU's 16 slots moves the texelFetch-only ones into texture slots,
 * which bind from the sampler's texture unit rather than an image unit.
 */
@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlSamplerOverflowTest {

    private static final int SAMPLERS = 18;
    private static final int FETCH_RED = 16;
    private static final int FETCH_GREEN = 17;
    private static final int FILTERED_BLUE = 15;
    private static final int EXPECTED = 0xFF4080C0;
    private static final int RELEASE_DELAY_FRAMES = Reflect.getStatic(GLSMTextureLifetime.class, "RELEASE_DELAY_FRAMES");

    private final int[] textures = new int[SAMPLERS];
    private int program;
    private int vao;
    private int redLod;
    private MovedSamplerProbe copyProbe;

    @BeforeEach
    void observeCopies() {
        copyProbe = new MovedSamplerProbe();
    }

    private static String name(int sampler, String redName) {
        return sampler == FETCH_RED ? redName : "t" + sampler;
    }

    private static String fragment(String redName, boolean lastTwoFetchOnly) {
        final StringBuilder sb = new StringBuilder("#version 330 core\n");
        for (int i = 0; i < SAMPLERS; i++) sb.append("uniform sampler2D ").append(name(i, redName)).append(";\n");
        sb.append("uniform int redLod;\nout vec4 fragColor;\nvoid main() {\n    vec4 unused = vec4(0.0);\n");
        final int filtered = lastTwoFetchOnly ? FETCH_RED : SAMPLERS;
        for (int i = 0; i < filtered; i++) sb.append("    unused += texture(").append(name(i, redName)).append(", vec2(0.5)) * 0.0;\n");
        if (lastTwoFetchOnly) {
            sb.append("    fragColor = vec4(texelFetch(").append(redName).append(", ivec2(0), redLod).r, texelFetch(t").append(FETCH_GREEN)
                .append(", ivec2(0), 0).g, texture(t").append(FILTERED_BLUE).append(", vec2(0.5)).b, 1.0) + unused;\n");
        } else {
            sb.append("    fragColor = vec4(1.0) + unused;\n");
        }
        return sb.append("}\n").toString();
    }

    private static int colorFor(int sampler) {
        return switch (sampler) {
            case FETCH_RED -> 0xFF401122;
            case FETCH_GREEN -> 0xFF338044;
            case FILTERED_BLUE -> 0xFF5566C0;
            default -> 0xFF000000;
        };
    }

    private void setUp(String redName, int[] units) {
        setUp(redName, units, () -> GlsmSdlHeadlessRig.createSolidTexture(colorFor(FETCH_RED)));
    }

    private void setUp(String redName, int[] units, IntSupplier redTexture) {
        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, fragment(redName, true));
        final ShaderManager.ProgramObject po = shaderManager().getProgram(program);
        assertEquals(16, po.fragmentResources.numSamplers(), "fragment sampler slots");
        assertEquals(2, po.fragmentResources.numStorageTextures(), "fetch-only samplers moved to texture slots");
        for (int i = 0; i < SAMPLERS; i++) textures[i] = i == FETCH_RED ? redTexture.getAsInt() : GlsmSdlHeadlessRig.createSolidTexture(colorFor(i));
        for (int i = 0; i < SAMPLERS; i++) {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        vao = GLSMVertexInput.glGenVertexArrays();
    }

    private int drawCenter(String redName, int[] units) {
        bindDrawState(redName, units);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GLSMPrograms.glUseProgram(0);
        assertMovedSamplersReadCopiesOnVulkan();
        return GlsmSdlHeadlessRig.readTarget(GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE / 2, 1, 1)[0];
    }

    private void bindDrawState(String redName, int[] units) {
        GlsmSdlHeadlessRig.bindTarget();
        GLSMPrograms.glUseProgram(program);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "redLod"), redLod);
        for (int i = 0; i < SAMPLERS; i++) {
            final int location = GLSMPrograms.glGetUniformLocation(program, name(i, redName));
            assertTrue(location >= 0, name(i, redName) + " has no location");
            GLSMPrograms.glUniform1i(location, units[i]);
        }
        GLSMVertexInput.glBindVertexArray(vao);
    }

    private void assertMovedSamplersReadCopiesOnVulkan() {
        if (!SDLGPUGate.device().isVulkan()) return;
        final ContextState st = SdlTestRig.contextState();
        for (int slot = 0; slot < 2; slot++) {
            final long bound = st.lastFragStorageTex[slot];
            assertNotEquals(0L, bound, "texture slot " + slot + " unbound");
            for (int texture : textures) {
                assertNotEquals(GlsmSdlHeadlessRig.resourceManager().getTextureHandle(texture), bound, "texture slot " + slot + " bound a sampleable texture directly");
            }
        }
    }

    private static int[] shuffledUnits() {
        final int[] units = new int[SAMPLERS];
        for (int i = 0; i < SAMPLERS; i++) units[i] = (i + 5) % SAMPLERS;
        return units;
    }

    @AfterEach
    void cleanUp() {
        copyProbe.close();
        GLSMEnable.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GLSMBlend.glLogicOp(GL11.GL_COPY);
        GLSMTextures.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_WRITE_ONLY, GL11.GL_RGBA8);
        GLSMVertexInput.glBindVertexArray(0);
        if (vao != 0) GLSMVertexInput.glDeleteVertexArrays(vao);
        for (int unit = 0; unit < SAMPLERS; unit++) {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        for (int i = 0; i < SAMPLERS; i++) {
            if (textures[i] != 0) GLSMTextures.glDeleteTextures(textures[i]);
            textures[i] = 0;
        }
        if (program != 0) GLSMPrograms.glDeleteProgram(program);
        program = 0;
        vao = 0;
    }

    @Test
    void fetchOnlySamplersPastTheSlotLimitReadTheTexturesOnTheirUnits() {
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        assertEquals(Integer.toHexString(EXPECTED), Integer.toHexString(drawCenter("t" + FETCH_RED, units)),
            "red and green from the moved samplers, blue from a sampler slot");
    }

    @Test
    void unboundMovedSamplerReadsTheFloat2DFallback() {
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + units[FETCH_RED]);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        assertEquals(0xFF0080C0, drawCenter("t" + FETCH_RED, units));
    }

    @ParameterizedTest
    @ValueSource(strings = {"uniform", "texture binding"})
    void changingAMovedSamplerPreparesAndBindsTheNewTexture(String change) {
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        bindDrawState("t" + FETCH_RED, units);
        for (int i = 0; i < 3; i++) GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        if (change.equals("uniform")) {
            GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "t" + FETCH_RED), units[0]);
        } else {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + units[FETCH_RED]);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, textures[0]);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        }
        final int refreshes = copyProbe.refreshCalls();
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        if (SDLGPUGate.device().isVulkan()) assertEquals(refreshes + 1, copyProbe.refreshCalls(), "a changed moved sampler must invalidate preparation");
        GLSMPrograms.glUseProgram(0);
        assertEquals(0xFF0080C0, GlsmSdlHeadlessRig.readTarget(32, 32, 1, 1)[0], "the new unit supplies black instead of red");
    }

    @Test
    void movedSamplerSeesAClearIssuedBeforeTheDraw() {
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        final int fbo = GlsmSdlHeadlessRig.fboWithColor(textures[FETCH_RED], 0);
        try {
            GLSMRasterizer.glViewport(0, 0, 2, 2);
            GLSMBlend.glClearColor(0x60 / 255.0f, 0.0f, 0.0f, 1.0f);
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
            assertEquals(Integer.toHexString(0xFF6080C0), Integer.toHexString(drawCenter("t" + FETCH_RED, units)),
                "the moved sampler must read the cleared texture, not its uploaded texels");
        } finally {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(fbo);
        }
    }

    @Test
    void movedSamplerReadsAnSrgbTextureDecoded() {
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units, () -> {
            final int texture = GlsmSdlHeadlessRig.newNearestTexture();
            final ByteBuffer texels = MemoryUtil.memAlloc(2 * 2 * 4);
            try {
                for (int i = 0; i < 4; i++) GlsmSdlHeadlessRig.putArgb(texels, i, colorFor(FETCH_RED));
                GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL21.GL_SRGB8_ALPHA8, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
            } finally {
                MemoryUtil.memFree(texels);
            }
            return texture;
        });
        // sRGB 0x40 decodes to linear 0.0513, which the RGBA8 target stores as 13.
        assertChannels(drawCenter("t" + FETCH_RED, units), 13, 0x80, 0xC0, "red decoded from the sRGB texture");
        if (SDLGPUGate.device().isVulkan()) {
            final long copy = watchCopy("t" + FETCH_RED);
            final int writes = copyProbe.writesTo(copy);
            GlsmSdlHeadlessRig.beginFrame();
            assertChannels(drawCenter("t" + FETCH_RED, units), 13, 0x80, 0xC0, "cached conversion keeps the decoded red");
            assertEquals(writes, copyProbe.writesTo(copy), "unchanged sRGB content must not be converted again");
        }
    }

    @Test
    void movedSamplerReadsADepthTexture() {
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units, () -> {
            final int texture = GlsmSdlHeadlessRig.newNearestTexture();
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, 2, 2, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
            final int fbo = GlsmSdlHeadlessRig.fboWithColor(GlsmSdlHeadlessRig.createSolidTexture(0xFF000000), 0);
            GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
            GLSMRasterizer.glViewport(0, 0, 2, 2);
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glClearDepth(0.25);
            GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(fbo);
            return texture;
        });
        assertChannels(drawCenter("t" + FETCH_RED, units), 64, 0x80, 0xC0, "red is the cleared depth 0.25");
    }

    private static void assertChannels(int argb, int r, int g, int b, String message) {
        final int gotR = (argb >> 16) & 0xFF, gotG = (argb >> 8) & 0xFF, gotB = argb & 0xFF;
        assertTrue(Math.abs(gotR - r) <= 1 && Math.abs(gotG - g) <= 1 && Math.abs(gotB - b) <= 1,
            () -> message + ": expected rgb(" + r + "," + g + "," + b + ") but was rgb(" + gotR + "," + gotG + "," + gotB + ")");
    }

    @Test
    void copiesOfDeletedTexturesAreReleased() {
        assumeTrue(SDLGPUGate.device().isVulkan(), "copies exist only on Vulkan");
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        drawCenter("t" + FETCH_RED, units);
        final int beforeDelete = liveTextureCount();

        GLSMTextures.glDeleteTextures(textures[FETCH_RED]);
        textures[FETCH_RED] = 0;
        for (int i = 0; i < RELEASE_DELAY_FRAMES; i++) {
            GlsmSdlHeadlessRig.beginFrame();
            GLSMTextureLifetime.onFrameEnd();
            GlsmSdlHeadlessRig.endFrame();
        }
        assertEquals(beforeDelete - 2, liveTextureCount(), "the deleted texture and its copy are released after the delay");
    }

    @Test
    void copiesNothingReadsAnyMoreAreReleasedAfterAFewFrames() {
        assumeTrue(SDLGPUGate.device().isVulkan(), "copies exist only on Vulkan");
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        drawCenter("t" + FETCH_RED, units);
        final int beforeExpiry = liveTextureCount();

        GLSMPrograms.glDeleteProgram(program);
        program = 0;
        for (int frame = 0; frame < 12; frame++) {
            GlsmSdlHeadlessRig.beginFrame();
            GlsmSdlHeadlessRig.endFrame();
        }
        assertEquals(beforeExpiry - 2, liveTextureCount(), "both unused copies must be released");
    }

    private static int liveTextureCount() {
        final LongSet handles = Reflect.get(GlsmSdlHeadlessRig.resourceManager(), "liveTextureHandles");
        return handles.size();
    }

    private long boundCopy(String name) {
        return SdlTestRig.contextState().lastFragStorageTex[shaderManager().getProgram(program).fragmentImageNames.indexOf(name)];
    }

    private long watchCopy(String name) {
        final long copy = boundCopy(name);
        assertNotEquals(0L, copy);
        copyProbe.watchWrites(copy);
        return copy;
    }

    @Test
    void unchangedSourcesReuseCopiesAcrossRenderPassesAndFrames() {
        assumeTrue(SDLGPUGate.device().isVulkan(), "copies exist only on Vulkan");
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        bindDrawState("t" + FETCH_RED, units);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        final long copy = watchCopy("t" + FETCH_RED);
        final int writes = copyProbe.writesTo(copy);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        final int refreshes = copyProbe.refreshCalls();
        assertTrue(refreshes > 0, "the probe must observe preparation before the cache can skip it");
        final var frame = GlsmSdlHeadlessRig.frameManager().frame();
        final long pass = frame.renderPassGeneration;
        final int bindGen = SdlTestRig.contextState().samplerBindGen;
        final long writeGen = GlsmSdlHeadlessRig.resourceManager().textureWriteGeneration();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + units[0]);
        for (int i = 0; i < 4; i++) {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, textures[(i + 1) % 2]);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        assertEquals(pass, frame.renderPassGeneration, "the draws keep the same render pass");
        assertNotEquals(bindGen, SdlTestRig.contextState().samplerBindGen, "the entity texture changes reached the backend");
        assertEquals(writeGen, GlsmSdlHeadlessRig.resourceManager().textureWriteGeneration(), "observing copies must not change write tracking");
        assertEquals(refreshes, copyProbe.refreshCalls(), "binding unrelated entity textures must skip preparation");
        assertEquals(writes, copyProbe.writesTo(copy), "repeated draws must not copy unchanged content");

        GlsmSdlHeadlessRig.frameManager().endRenderPassIfActive();
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        assertEquals(refreshes + 1, copyProbe.refreshCalls(), "a new pass must recheck its snapshots");
        assertEquals(writes, copyProbe.writesTo(copy), "a new pass must reuse unchanged content");
        GLSMPrograms.glUseProgram(0);
        GlsmSdlHeadlessRig.endFrame();
        GlsmSdlHeadlessRig.beginFrame();
        assertEquals(EXPECTED, drawCenter("t" + FETCH_RED, units));
        assertEquals(copy, boundCopy("t" + FETCH_RED), "the allocation is reused");
        assertEquals(writes, copyProbe.writesTo(copy), "a new frame must reuse unchanged content");
    }

    @ParameterizedTest
    @ValueSource(strings = {"upload", "copy", "blit", "clear", "mipmap", "compute", "copyTexSubImage"})
    void writesRefreshOnlyTheChangedSource(String write) {
        assumeTrue(SDLGPUGate.device().isVulkan(), "copies exist only on Vulkan");
        final int[] units = shuffledUnits();
        if (write.equals("mipmap")) {
            redLod = 1;
            setUp("t" + FETCH_RED, units, () -> {
                final int texture = GlsmSdlHeadlessRig.createSolidTexture(0xFF000000);
                GlsmSdlHeadlessRig.uploadSolidLevel(1, 1, colorFor(FETCH_RED));
                return texture;
            });
        } else {
            setUp("t" + FETCH_RED, units);
        }
        if (write.equals("compute")) GLSMTextures.glBindImageTexture(0, textures[FETCH_RED], 0, false, 0, GL15.GL_WRITE_ONLY, GL11.GL_RGBA8);
        final int fbo = GlsmSdlHeadlessRig.fboWithColor(textures[FETCH_RED], 0);
        try {
            assertEquals(EXPECTED, drawCenter("t" + FETCH_RED, units));
            final long redCopy = watchCopy("t" + FETCH_RED);
            final long greenCopy = watchCopy("t" + FETCH_GREEN);
            final int redWrites = copyProbe.writesTo(redCopy);
            final int greenWrites = copyProbe.writesTo(greenCopy);
            GlsmSdlHeadlessRig.beginFrame();
            switch (write) {
                case "upload" -> {
                    GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + units[FETCH_RED]);
                    final ByteBuffer texels = MemoryUtil.memAlloc(16);
                    try {
                        for (int i = 0; i < 4; i++) GlsmSdlHeadlessRig.putArgb(texels, i, 0xFF000000);
                        GLSMTextures.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 2, 2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
                    } finally {
                        MemoryUtil.memFree(texels);
                        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
                    }
                }
                case "copy" -> BackendManager.RENDER_BACKEND.copyImageSubData(textures[0], GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                    textures[FETCH_RED], GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 2, 2, 1);
                case "blit" -> {
                    final int srcFbo = GlsmSdlHeadlessRig.fboWithColor(textures[0], 0);
                    try {
                        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
                        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
                        GLSMFramebuffer.glBlitFramebuffer(0, 0, 2, 2, 0, 0, 2, 2, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                    } finally {
                        GLSMFramebuffer.glDeleteFramebuffers(srcFbo);
                    }
                }
                case "clear" -> {
                    GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                    GLSMBlend.glClearColor(0, 0, 0, 1);
                    GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
                }
                case "mipmap" -> BackendManager.RENDER_BACKEND.generateTextureMipmap(textures[FETCH_RED]);
                case "compute" -> computeBlack();
                case "copyTexSubImage" -> {
                    final int srcFbo = GlsmSdlHeadlessRig.fboWithColor(textures[0], 0);
                    try {
                        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + units[FETCH_RED]);
                        GLSMTextures.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 2, 2);
                    } finally {
                        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
                        GLSMFramebuffer.glDeleteFramebuffers(srcFbo);
                    }
                }
                default -> throw new AssertionError(write);
            }
            assertEquals(0xFF0080C0, drawCenter("t" + FETCH_RED, units), write + " changes the sampled red");
            assertEquals(redWrites + 1, copyProbe.writesTo(redCopy), "changed source copied once");
            assertEquals(greenWrites, copyProbe.writesTo(greenCopy), "unchanged source not copied again");
        } finally {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(fbo);
        }
    }

    private static void computeBlack() {
        final int shader = GlTestSupport.compile(GL43.GL_COMPUTE_SHADER, """
            #version 430 core
            layout(local_size_x = 1, local_size_y = 1) in;
            layout(rgba8, binding = 0) uniform writeonly image2D target;
            void main() { imageStore(target, ivec2(gl_GlobalInvocationID.xy), vec4(0.0, 0.0, 0.0, 1.0)); }
            """);
        final int compute = GLSMPrograms.glCreateProgram();
        try {
            GLSMPrograms.glAttachShader(compute, shader);
            GLSMPrograms.glLinkProgram(compute);
            assertEquals(1, GLSMPrograms.glGetProgrami(compute, GL20.GL_LINK_STATUS), GLSMPrograms.glGetProgramInfoLog(compute, 4096));
            GLSMPrograms.glUseProgram(compute);
            GLSMPrograms.glDispatchCompute(2, 2, 1);
        } finally {
            GLSMPrograms.glUseProgram(0);
            GLSMPrograms.glDeleteProgram(compute);
            GLSMPrograms.glDeleteShader(shader);
        }
    }

    @Test
    void partialClearDoesNotPrepareMovedSamplers() {
        assumeTrue(SDLGPUGate.device().isVulkan(), "copies exist only on Vulkan");
        final int[] units = shuffledUnits();
        setUp("t" + FETCH_RED, units);
        bindDrawState("t" + FETCH_RED, units);
        final var frame = GlsmSdlHeadlessRig.frameManager().frame();
        final long before = frame.renderPassGeneration;
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, 1, 1);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
        assertEquals(before + 1, frame.renderPassGeneration, "only the clear's render pass is opened");
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        assertNotEquals(0L, boundCopy("t" + FETCH_RED), "the first draw prepares the copy");
        GLSMPrograms.glUseProgram(0);
        assertEquals(EXPECTED, GlsmSdlHeadlessRig.readTarget(32, 32, 1, 1)[0]);
    }

    @ParameterizedTest
    @CsvSource({"sampler1D, int", "sampler3D, ivec3", "sampler2DArray, ivec3", "isampler2D, ivec2", "usampler2D, ivec2"})
    void overflowRejectsTypesWithoutAMatchingFallback(String type, String coord) {
        final String source = "#version 330 core\n" + declareFiltered(16) + "uniform " + type + " fetched;\nout vec4 fragColor;\n"
            + "void main() { vec4 c = vec4(0.0);\n" + readFiltered(16, "c", "")
            + "fragColor = c + vec4(texelFetch(fetched, " + coord + "(0), 0)); }\n";
        final int vs = GlTestSupport.compile(GL20.GL_VERTEX_SHADER, GlsmSdlHeadlessRig.FULLSCREEN_VERTEX);
        final int fs = GlTestSupport.compile(GL20.GL_FRAGMENT_SHADER, source);
        program = GLSMPrograms.glCreateProgram();
        try {
            GLSMPrograms.glAttachShader(program, vs);
            GLSMPrograms.glAttachShader(program, fs);
            GLSMPrograms.glLinkProgram(program);
            assertEquals(0, GLSMPrograms.glGetProgrami(program, GL20.GL_LINK_STATUS));
            assertTrue(GLSMPrograms.glGetProgramInfoLog(program, 4096).contains("Too many fragment shader texture samplers"));
        } finally {
            GLSMPrograms.glDeleteShader(vs);
            GLSMPrograms.glDeleteShader(fs);
        }
    }

    private static String declareFiltered(int count) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append("uniform sampler2D f").append(i).append(";\n");
        return sb.toString();
    }

    private static String readFiltered(int count, String into, String scale) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append("    ").append(into).append(" += texture(f").append(i).append(", vec2(0.5))").append(scale).append(";\n");
        return sb.toString();
    }

    @Test
    void unusedSamplerDeclaredWithAUsedOneStillTakesASlot() {
        final StringBuilder sb = new StringBuilder("#version 330 core\n").append(declareFiltered(14));
        sb.append("uniform sampler2D f14, neverRead;\n");
        for (int i = 0; i < 3; i++) sb.append("uniform sampler2D x").append(i).append(";\n");
        sb.append("out vec4 fragColor;\nvoid main() {\n    vec4 c = vec4(0.0);\n").append(readFiltered(15, "c", ""));
        for (int i = 0; i < 3; i++) sb.append("    c += texelFetch(x").append(i).append(", ivec2(0), 0);\n");
        sb.append("    fragColor = c;\n}\n");

        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, sb.toString());
        final ShaderManager.ProgramObject po = shaderManager().getProgram(program);
        assertEquals(16, po.fragmentResources.numSamplers(), "15 read samplers plus the unread one declared beside f14");
        assertEquals(3, po.fragmentResources.numStorageTextures(), "all three fetch-only samplers moved");
    }

    @ParameterizedTest(name = "helper inside #if: {0}")
    @ValueSource(booleans = {false, true})
    void samplerPassedToTheShadersOwnTextureSizeStaysASampler(boolean helperInConditional) {
        final StringBuilder sb = new StringBuilder("#version 330 core\n").append(declareFiltered(15));
        sb.append("uniform sampler2D helperArgument;\nuniform sampler2D fetched;\nout vec4 fragColor;\n");
        if (helperInConditional) sb.append("#if 1\n");
        sb.append("ivec2 textureSize(sampler2D s) { return textureSize(s, 0); }\n");
        if (helperInConditional) sb.append("#endif\n");
        sb.append("void main() {\n    vec4 c = vec4(0.0);\n").append(readFiltered(15, "c", ""));
        sb.append("    c.xy += vec2(textureSize(helperArgument));\n    c += texelFetch(fetched, ivec2(0), 0);\n    fragColor = c;\n}\n");

        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, sb.toString());
        final ShaderManager.ProgramObject po = shaderManager().getProgram(program);
        assertEquals(16, po.fragmentResources.numSamplers(), "helperArgument must stay a sampler; only 'fetched' moves");
        assertEquals(1, po.fragmentResources.numStorageTextures(), "one sampler moved");
    }

    @Test
    void samplerReadThroughATexelFetchMacroStaysASampler() {
        final StringBuilder sb = new StringBuilder("#version 330 core\n");
        sb.append("#define texelFetch(s, p, l) textureLod(s, vec2(p), float(l))\n").append(declareFiltered(15));
        sb.append("uniform sampler2D throughMacro;\nuniform sampler2D sized0;\nuniform sampler2D sized1;\nout vec4 fragColor;\n");
        sb.append("void main() {\n    vec4 c = vec4(0.0);\n").append(readFiltered(15, "c", ""));
        sb.append("    c += texelFetch(throughMacro, ivec2(0), 0);\n");
        sb.append("    c.xy += vec2(textureSize(sized0, 0) + textureSize(sized1, 0));\n    fragColor = c;\n}\n");

        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, sb.toString());
        final ShaderManager.ProgramObject po = shaderManager().getProgram(program);
        assertEquals(16, po.fragmentResources.numSamplers(), "throughMacro must stay a sampler; the two textureSize-only ones move");
        assertEquals(2, po.fragmentResources.numStorageTextures(), "two samplers moved");
    }

    @Test
    void macroSharingAnUnusedSamplersNameDoesNotKeepTheSampler() {
        final StringBuilder sb = new StringBuilder("#version 330 core\n#define helper(x) (x)\n").append(declareFiltered(16));
        sb.append("uniform sampler2D helper;\nout vec4 fragColor;\nvoid main() {\n    vec4 c = vec4(0.0);\n").append(readFiltered(16, "c", ""));
        sb.append("    fragColor = c;\n}\n");

        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, sb.toString());
        assertEquals(16, shaderManager().getProgram(program).fragmentResources.numSamplers(), "the unread sampler 'helper' must still be stripped");
    }

    @Test
    void samplerNamedThroughAMacroKeepsTheExpandedName() {
        final StringBuilder sb = new StringBuilder("#version 330 core\n#define alias realTex\n").append(declareFiltered(15));
        sb.append("uniform sampler2D alias;\nuniform sampler2D fetched;\nout vec4 fragColor;\n");
        sb.append("void main() {\n    vec4 c = vec4(0.0);\n").append(readFiltered(15, "c", ""));
        sb.append("    c += texelFetch(alias, ivec2(0), 0) + texelFetch(fetched, ivec2(0), 0);\n    fragColor = c;\n}\n");

        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, sb.toString());
        assertEquals(1, shaderManager().getProgram(program).fragmentResources.numStorageTextures(), "only 'fetched' moves");
        assertTrue(GLSMPrograms.glGetUniformLocation(program, "realTex") >= 0, "the uniform keeps the name the macro expands to");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void movedSamplerOnlyReadByAnUncalledFunctionUsesFallbackWithoutCopying(boolean fragment) {
        final StringBuilder sb = new StringBuilder("#version 330 core\n").append(declareFiltered(16));
        sb.append("uniform sampler2D shadowtex0;\n");
        if (fragment) sb.append("out vec4 fragColor;\n");
        sb.append("vec4 neverCalled() { return texelFetch(shadowtex0, ivec2(0), 0); }\n");
        sb.append("void main() {\n    vec4 c = vec4(0.0);\n").append(readFiltered(16, "c", " * 0.0"));
        if (fragment) {
            sb.append("    fragColor = vec4(0.0, 1.0, 0.0, 1.0) + c;\n}\n");
        } else {
            sb.append("    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0) + c;\n}\n");
        }

        program = GlTestSupport.program(fragment ? GlsmSdlHeadlessRig.FULLSCREEN_VERTEX : sb.toString(), fragment ? sb.toString() : """
            #version 330 core
            out vec4 fragColor;
            void main() { fragColor = vec4(0.0, 1.0, 0.0, 1.0); }
            """);
        final ShaderManager.ProgramObject po = shaderManager().getProgram(program);
        assertEquals(1, (fragment ? po.fragmentResources : po.vertexResources).numStorageTextures(), "shadowtex0 moved");
        assertEquals(-1, GLSMPrograms.glGetUniformLocation(program, "shadowtex0"), "an unread sampler must report -1, or Iris treats shadows as used");
        assertTrue(GLSMPrograms.glGetUniformLocation(program, "f0") >= 0, "read samplers keep their location");

        textures[0] = GlsmSdlHeadlessRig.createSolidTexture(GlsmSdlHeadlessRig.RED);
        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMPrograms.glUseProgram(program);
        GlsmSdlHeadlessRig.bindTarget();
        final var rm = GlsmSdlHeadlessRig.resourceManager();
        final long fallback = rm.getOrCreateFallbackStorageTexture2D();
        final long before = rm.textureWriteGeneration();
        for (int i = 0; i < 3; i++) GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        final ContextState st = SdlTestRig.contextState();
        assertEquals(fallback, (fragment ? st.lastFragStorageTex : st.lastVertStorageTex)[0], "the unread slot uses the 2D fallback");
        assertEquals(before, rm.textureWriteGeneration(), "the unread sampler must not start tracking or copying unit 0");
        GLSMPrograms.glUseProgram(0);
        assertEquals(GlsmSdlHeadlessRig.GREEN, GlsmSdlHeadlessRig.readTarget(32, 32, 1, 1)[0]);
    }

    @Test
    void copyIsRefreshedWhenTheDrawMovesToAnotherFramebuffer() {
        final StringBuilder sb = new StringBuilder("#version 330 core\n").append(declareFiltered(16));
        sb.append("uniform sampler2D written;\nuniform int readWritten;\nout vec4 fragColor;\nvoid main() {\n    vec4 unused = vec4(0.0);\n");
        sb.append(readFiltered(16, "unused", " * 0.0"));
        sb.append("    float size = float(textureSize(written, 0).x) * 0.0;\n");
        sb.append("    fragColor = (readWritten == 1 ? texelFetch(written, ivec2(0), 0) : vec4(0x40 / 255.0, 0.0, 0.0, 1.0)) + unused + size;\n}\n");
        program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, sb.toString());
        assertEquals(1, shaderManager().getProgram(program).fragmentResources.numStorageTextures(), "'written' moved");

        for (int i = 0; i < 16; i++) textures[i] = GlsmSdlHeadlessRig.createSolidTexture(0xFF000000);
        textures[16] = GlsmSdlHeadlessRig.createSolidTexture(0xFF00FF00);
        for (int i = 0; i <= 16; i++) {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        vao = GLSMVertexInput.glGenVertexArrays();
        final int fbo = GlsmSdlHeadlessRig.fboWithColor(textures[16], 0);
        try {
            GLSMPrograms.glUseProgram(program);
            for (int i = 0; i < 16; i++) GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "f" + i), i);
            GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "written"), 16);
            GLSMVertexInput.glBindVertexArray(vao);

            GLSMRasterizer.glViewport(0, 0, 2, 2);
            GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "readWritten"), 0);
            for (int i = 0; i < 3; i++) GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            GlsmSdlHeadlessRig.bindTarget();
            GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "readWritten"), 1);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            GLSMPrograms.glUseProgram(0);

            assertChannels(GlsmSdlHeadlessRig.readTarget(GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE / 2, 1, 1)[0], 0x40, 0, 0,
                "the second draw must see what the first draw wrote, not the texture's earlier green");
        } finally {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(fbo);
        }
    }

    @Test
    void tooManyFilteredSamplersFailLinkInsteadOfDraw() {
        final int vs = GlTestSupport.compile(GL20.GL_VERTEX_SHADER, GlsmSdlHeadlessRig.FULLSCREEN_VERTEX);
        final int fs = GlTestSupport.compile(GL20.GL_FRAGMENT_SHADER, fragment("t" + FETCH_RED, false));
        final int failed = GLSMPrograms.glCreateProgram();
        try {
            GLSMPrograms.glAttachShader(failed, vs);
            GLSMPrograms.glAttachShader(failed, fs);
            GLSMPrograms.glLinkProgram(failed);
            assertEquals(0, GLSMPrograms.glGetProgrami(failed, GL20.GL_LINK_STATUS), "18 filtered samplers cannot fit 16 slots");
            final String log = GLSMPrograms.glGetProgramInfoLog(failed, 4096);
            assertTrue(log.contains("Too many fragment shader texture samplers (18, maximum 16)"), () -> "info log should name the count: " + log);
        } finally {
            GLSMPrograms.glDeleteShader(vs);
            GLSMPrograms.glDeleteShader(fs);
            GLSMPrograms.glDeleteProgram(failed);
        }
    }

    private static ShaderManager shaderManager() {
        return Reflect.get(BackendManager.RENDER_BACKEND, "shaderManager");
    }
}
