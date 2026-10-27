package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.BLACK;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlAttachmentClearTest {

    private static final int SIZE = GlsmSdlHeadlessRig.SIZE;

    private static FrameManager.FrameState frame() {
        return GlsmSdlHeadlessRig.frameManager().frame();
    }

    private static void setDisableInPassClear(boolean value) {
        Reflect.setStaticFinal(SystemProperties.class, "SDL_DISABLE_IN_PASS_CLEAR", boolean.class, value);
    }

    /** Clears colour to black and depth to 0.9, then draws a quad so a render pass is open. */
    private static void primeTarget(float r, float g, float b) {
        GlsmSdlHeadlessRig.bindTarget();
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMDepthStencilAlpha.glClearDepth(0.9);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(r, g, b);
    }

    private static void primeTargetWithStencil(int stencil) {
        GlsmSdlHeadlessRig.bindTarget();
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMDepthStencilAlpha.glClearDepth(1.0);
        GLSMDepthStencilAlpha.glClearStencil(stencil);
        GLSMDepthStencilAlpha.glStencilMask(0xFF);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);
    }

    private static void drawFullQuadAtZ(float z, float r, float g, float b) {
        GLSMCurrentAttribs.glColor4f(r, g, b, 1.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
        GLSMDraws.glBegin(GL11.GL_QUADS);
        GLSMDraws.glVertex3f(-1.0f, -1.0f, z);
        GLSMDraws.glVertex3f(1.0f, -1.0f, z);
        GLSMDraws.glVertex3f(1.0f, 1.0f, z);
        GLSMDraws.glVertex3f(-1.0f, 1.0f, z);
        GLSMDraws.glEnd();
    }

    private static boolean isRed(int argb) {
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        return r > 200 && g < 64;
    }

    private static boolean isGreen(int argb) {
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        return g > 200 && r < 64;
    }

    private static boolean depthEquals(float depthValue) {
        GLSMEnable.glEnable(GL11.GL_DEPTH_TEST);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_EQUAL);
        GLSMDepthStencilAlpha.glDepthMask(false);
        drawFullQuadAtZ(depthValue * 2.0f - 1.0f, 1.0f, 0.0f, 0.0f);
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        return isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2));
    }

    @Test
    void depthMaskOffSuppressesTheClearAndBreaksNoPass() {
        primeTarget(0.1f, 0.1f, 0.1f);

        final FrameManager.FrameState f = frame();
        final long passBefore = f.renderPass;
        assertTrue(passBefore != 0, "expected an active render pass before the masked clear");
        final int inPassBefore = f.inPassClearsThisFrame;
        final int passEndClearBefore = f.passEndCauseCountsThisFrame[FrameManager.PASS_END_CLEAR];

        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMDepthStencilAlpha.glClearDepth(0.0);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLSMDepthStencilAlpha.glDepthMask(true);

        assertEquals(passBefore, f.renderPass, "a masked clear must not break the active render pass");
        assertEquals(inPassBefore, f.inPassClearsThisFrame, "a masked clear must not take the in-pass path");
        assertEquals(passEndClearBefore, f.passEndCauseCountsThisFrame[FrameManager.PASS_END_CLEAR], "a masked clear must not end the pass for a clear");
        assertTrue(depthEquals(0.9f), "a masked clear must leave the prior depth content untouched");
    }

    @Test
    void inPassPathIsTakenForACompatibleActivePass() {
        primeTarget(0.1f, 0.1f, 0.1f);

        final FrameManager.FrameState f = frame();
        final long passBefore = f.renderPass;
        assertTrue(passBefore != 0, "expected an active render pass before the clear");
        final int inPassBefore = f.inPassClearsThisFrame;

        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMDepthStencilAlpha.glClearDepth(0.25);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        assertEquals(passBefore, f.renderPass, "the in-pass clear must not break the active render pass");
        assertEquals(inPassBefore + 1, f.inPassClearsThisFrame, "the in-pass clear must be taken exactly once");
        assertTrue(depthEquals(0.25f), "the in-pass clear must have written the new depth value");
    }

    @Test
    void disableInPassClearFlagForcesTheLegacyPath() {
        primeTarget(0.1f, 0.1f, 0.1f);

        final FrameManager.FrameState f = frame();
        assertTrue(f.renderPass != 0, "expected an active render pass before the clear");
        final int inPassBefore = f.inPassClearsThisFrame;

        setDisableInPassClear(true);
        try {
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glClearDepth(0.6);
            GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            assertEquals(inPassBefore, f.inPassClearsThisFrame, "angelica.sdlgpu.disableInPassClear must force the legacy path, never the in-pass path");
        } finally {
            setDisableInPassClear(false);
        }
    }

    @Test
    void inPassClearMatchesTheLegacyPathForVariousDepths() {
        for (final float depthValue : new float[]{ 0.0f, 0.25f, 0.5f, 1.0f }) {
            assertTrue(clearAndProbe(depthValue, false), "in-pass: depth=" + depthValue + " must read back exactly");
            assertTrue(clearAndProbe(depthValue, true), "legacy: depth=" + depthValue + " must read back exactly");
        }
    }

    private static boolean clearAndProbe(float depthValue, boolean disableInPass) {
        setDisableInPassClear(disableInPass);
        try {
            GlsmSdlHeadlessRig.beginFrame();
            GlsmSdlHeadlessRig.resetState();
            primeTarget(0.1f, 0.1f, 0.1f);

            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glClearDepth(depthValue);
            GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            return depthEquals(depthValue);
        } finally {
            setDisableInPassClear(false);
        }
    }

    @Test
    void scissoredClearOnlyAffectsTheScissorRect() {
        primeTarget(0.1f, 0.1f, 0.1f);

        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, SIZE / 2, SIZE);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMDepthStencilAlpha.glClearDepth(0.0);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);

        GLSMEnable.glEnable(GL11.GL_DEPTH_TEST);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_EQUAL);
        GLSMDepthStencilAlpha.glDepthMask(false);

        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, SIZE / 2, SIZE);
        drawFullQuadAtZ(-1.0f, 1.0f, 0.0f, 0.0f);
        GLSMRasterizer.glScissor(SIZE / 2, 0, SIZE - SIZE / 2, SIZE);
        drawFullQuadAtZ(0.9f * 2.0f - 1.0f, 1.0f, 0.0f, 0.0f);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 4, SIZE / 2)), "inside the scissor rect the depth must have been cleared to 0");
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2 + SIZE / 4, SIZE / 2)), "outside the scissor rect the prior depth must be untouched");
    }

    @Test
    void stencilWriteMaskIsHonored() {
        primeTargetWithStencil(0xA5);

        final FrameManager.FrameState f = frame();
        final long passBefore = f.renderPass;
        final int inPassBefore = f.inPassClearsThisFrame;

        GLSMDepthStencilAlpha.glClearStencil(0x3F);
        GLSMDepthStencilAlpha.glStencilMask(0x0F);
        GLSMFramebuffer.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GLSMDepthStencilAlpha.glStencilMask(0xFF);

        assertEquals(passBefore, f.renderPass, "the stencil-only clear must not break the active render pass");
        assertEquals(inPassBefore + 1, f.inPassClearsThisFrame, "the stencil clear must take the in-pass path");

        final StencilProbeResult result = probeStencilReconcile();
        assertEquals(0xAF, result.bitPlaneValue(), "bit-plane read: only the low nibble should have been replaced: 0xA5 & ~0x0F | 0x3F & 0x0F == 0xAF");
        assertFalse(result.fullMask3F(), "GL_EQUAL(ref=0x3F, mask=0xFF) must not match: the write mask must have been honored, not ignored");
    }

    @Test
    @Disabled("stencil reference >= 0x80 compares as 0xFF on this backend; see stencil reference finding")
    void stencilReferenceAtOrAbove0x80ComparesExactly() {
        primeTargetWithStencil(0xAF);

        final StencilProbeResult result = probeStencilReconcile();
        assertEquals(0xAF, result.bitPlaneValue(), "bit-plane read of a plain 0xAF stencil clear");
        assertTrue(result.fullMaskAF(), "GL_EQUAL(ref=0xAF, mask=0xFF) must match a stored 0xAF");
    }

    @Test
    void baselineStencilClearReadsBackExactly() {
        primeTargetWithStencil(0xA5);

        assertEquals(0xA5, readStencilByteSameFrame(), "a full-write-mask clear (the legacy load-op path) must read back exactly what was cleared");
    }

    private static int readStencilByteSameFrame() {
        final int regionW = Math.max(1, SIZE / 8);
        for (int bit = 0; bit < 8; bit++) {
            probeStrip(bit, regionW, 8, GL11.GL_EQUAL, 1 << bit, 1 << bit);
        }
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        int value = 0;
        for (int bit = 0; bit < 8; bit++) {
            if (isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, stripCenter(bit, regionW, 8), SIZE / 2))) value |= (1 << bit);
        }
        return value;
    }

    private record StencilProbeResult(int bitPlaneValue, boolean fullMaskAF, boolean fullMask3F) {}

    private static StencilProbeResult probeStencilReconcile() {
        final int[][] extra = {
            { 0xAF, 0xFF }, { 0xAF, 0xFE }, { 0xAF, 0x7F }, { 0xAF, 0x80 }, { 0xAF, 0x0F },
            { 0x2F, 0x7F }, { 0xAF, 0x70 }, { 0x20, 0x70 },
        };
        final int strips = 8 + extra.length + 1;
        final int regionW = SIZE / strips;
        for (int bit = 0; bit < 8; bit++) {
            probeStrip(bit, regionW, strips, GL11.GL_EQUAL, 1 << bit, 1 << bit);
        }
        for (int i = 0; i < extra.length; i++) {
            probeStrip(8 + i, regionW, strips, GL11.GL_EQUAL, extra[i][0], extra[i][1]);
        }
        probeStrip(8 + extra.length, regionW, strips, GL11.GL_EQUAL, 0x3F, 0xFF);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        int bitPlaneValue = 0;
        for (int bit = 0; bit < 8; bit++) {
            final boolean set = isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, stripCenter(bit, regionW, strips), SIZE / 2));
            if (set) bitPlaneValue |= (1 << bit);
        }
        final boolean fullMaskAF = isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, stripCenter(8, regionW, strips), SIZE / 2));
        final boolean fullMask3F = isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, stripCenter(8 + extra.length, regionW, strips), SIZE / 2));
        return new StencilProbeResult(bitPlaneValue, fullMaskAF, fullMask3F);
    }

    private static void probeStrip(int index, int regionW, int strips, int func, int ref, int mask) {
        final int x = index * regionW;
        final int w = (index == strips - 1) ? SIZE - x : regionW;
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(x, 0, w, SIZE);
        GLSMEnable.glEnable(GL11.GL_STENCIL_TEST);
        GLSMDepthStencilAlpha.glStencilFunc(func, ref, mask);
        GLSMDepthStencilAlpha.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        drawFullQuadAtZ(0.0f, 1.0f, 0.0f, 0.0f);
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static int stripCenter(int index, int regionW, int strips) {
        final int x = index * regionW;
        final int w = (index == strips - 1) ? SIZE - x : regionW;
        return x + w / 2;
    }

    @Test
    void colorIsUntouchedByADepthStencilOnlyClear() {
        GlsmSdlHeadlessRig.bindTarget();
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glClearColor(0.2f, 0.4f, 0.6f, 1.0f);
        GLSMDepthStencilAlpha.glClearDepth(0.9);
        GLSMDepthStencilAlpha.glClearStencil(0x55);
        GLSMDepthStencilAlpha.glStencilMask(0xFF);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.2f, 0.4f, 0.6f);

        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMDepthStencilAlpha.glClearDepth(0.1);
        GLSMDepthStencilAlpha.glClearStencil(0x77);
        GLSMDepthStencilAlpha.glStencilMask(0xFF);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int argb = GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2);
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        assertTrue(Math.abs(r - 51) <= 2, "red must be untouched: " + r);
        assertTrue(Math.abs(g - 102) <= 2, "green must be untouched: " + g);
        assertTrue(Math.abs(b - 153) <= 2, "blue must be untouched: " + b);
    }

    @Test
    void scissoredColorAndDepthClearStaysInTheRect() {
        primeTarget(0.0f, 0.0f, 1.0f);

        final FrameManager.FrameState f = frame();
        final long passBefore = f.renderPass;
        assertTrue(passBefore != 0, "expected an active render pass before the scissored clear");
        final int inPassBefore = f.inPassClearsThisFrame;

        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, SIZE / 2, SIZE);
        GLSMBlend.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLSMDepthStencilAlpha.glClearDepth(0.0);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);

        assertEquals(passBefore, f.renderPass, "the scissored clear must not break the active render pass");
        assertEquals(inPassBefore + 1, f.inPassClearsThisFrame, "the scissored clear must take the in-pass path");

        final int probeSize = 4;
        final int leftProbeX = SIZE / 2 - probeSize * 2;
        final int rightProbeX = SIZE / 2 + probeSize;

        GLSMEnable.glEnable(GL11.GL_DEPTH_TEST);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_EQUAL);
        GLSMDepthStencilAlpha.glDepthMask(false);

        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(leftProbeX, 0, probeSize, SIZE);
        drawFullQuadAtZ(0.0f * 2.0f - 1.0f, 0.0f, 1.0f, 0.0f);
        GLSMRasterizer.glScissor(rightProbeX, 0, probeSize, SIZE);
        drawFullQuadAtZ(0.9f * 2.0f - 1.0f, 0.0f, 1.0f, 0.0f);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 8, SIZE / 2)), "inside the scissor rect must be the clear color");
        assertFalse(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE - SIZE / 8, SIZE / 2)), "outside the scissor rect must keep the prior blue quad");
        assertTrue(isGreen(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, leftProbeX + probeSize / 2, SIZE / 2)), "inside the scissor rect the depth must have been cleared to 0");
        assertTrue(isGreen(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, rightProbeX + probeSize / 2, SIZE / 2)), "outside the scissor rect the prior depth must be untouched");
    }

    @Test
    void scissoredClearAfterPendingFullClear() {
        GlsmSdlHeadlessRig.bindTarget();
        GLSMBlend.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, SIZE / 2, SIZE);
        GLSMBlend.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GLSMRasterizer.glScissor(0, 0, 0, SIZE);
        GLSMBlend.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertTrue(isGreen(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 4, SIZE / 2)), "the scissored clear must have painted the left half green");
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2 + SIZE / 4, SIZE / 2)), "the empty-scissor clear must not have touched the right half");
    }

    @Test
    void colorMaskIsHonoredByClear() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.solidQuad(0.2f, 0.4f, 0.6f);

        GLSMBlend.glColorMask(true, false, false, true);
        GLSMBlend.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMBlend.glColorMask(true, true, true, true);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int argb = GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2);
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        assertEquals(255, r, "red must be written: colorMask allowed it");
        assertTrue(Math.abs(g - 102) <= 2, "green must be untouched by the masked clear: " + g);
        assertTrue(Math.abs(b - 153) <= 2, "blue must be untouched by the masked clear: " + b);
    }

    @Test
    void scissoredClearWritesEveryDrawBuffer() {
        final int texA = createColorTexture();
        final int texB = createColorTexture();

        final int mrtFbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mrtFbo);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texA, 0);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, texB, 0);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMFramebuffer.glDrawBuffers(IntBuffer.wrap(new int[]{ GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1 }));

        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, SIZE / 2, SIZE);
        GLSMBlend.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);

        final int readFbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, readFbo);

        assertLeftHalfRed(texA, "texture A");
        assertLeftHalfRed(texB, "texture B");

        GLSMFramebuffer.glDeleteFramebuffers(readFbo);
        GLSMFramebuffer.glDeleteFramebuffers(mrtFbo);
        GLSMTextures.glDeleteTextures(texA);
        GLSMTextures.glDeleteTextures(texB);

        GlsmSdlHeadlessRig.bindTarget();
    }

    @Test
    void clearReachesARenderbufferRespecifiedWhileAttached() {
        final int rb = GLSMFramebuffer.glGenRenderbuffers();
        GLSMFramebuffer.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rb);
        GLSMFramebuffer.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, SIZE / 4, SIZE / 4);
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMFramebuffer.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, rb);

        GLSMFramebuffer.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, SIZE, SIZE);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMBlend.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(), RED, "respecified renderbuffer must stay attached");

        GLSMFramebuffer.glDeleteFramebuffers(fbo);
        GLSMFramebuffer.glDeleteRenderbuffers(rb);
        GlsmSdlHeadlessRig.bindTarget();
    }

    private static int createColorTexture() {
        return createColorTexture(GL11.GL_RGBA8, GL11.GL_RGBA);
    }

    private static int createColorTexture(int internalFormat, int format) {
        final int tex = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, SIZE, SIZE, 0, format, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return tex;
    }

    private static void assertLeftHalfRed(int texture, String label) {
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 4, SIZE / 2)), label + " left half must be cleared red");
        assertFalse(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2 + SIZE / 4, SIZE / 2)), label + " right half must keep the base clear");
    }

    @Test
    void rasterizerDiscardSkipsClearsAndDraws() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        GLSMEnable.glEnable(GL30.GL_RASTERIZER_DISCARD);
        GLSMBlend.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.0f, 0.0f, 1.0f);
        GLSMEnable.glDisable(GL30.GL_RASTERIZER_DISCARD);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2)), "rasterizer discard must skip both the clear and the draw");
    }

    @Test
    void clearIsIgnoredOutsideRenderMode() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        final FloatBuffer feedbackBuffer = BufferUtils.createFloatBuffer(16);
        GLSMDraws.glFeedbackBuffer(GL11.GL_3D, feedbackBuffer);
        GLSMDraws.glRenderMode(GL11.GL_FEEDBACK);
        GLSMBlend.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMDraws.glRenderMode(GL11.GL_RENDER);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2)), "a clear issued outside GL_RENDER mode must be ignored");
    }

    @Test
    void colorMaskCoveringEveryPresentChannelIsAFullClear() {
        final int tex = createColorTexture(GL30.GL_R8, GL11.GL_RED);
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);

        GLSMBlend.glClearColor(0.2f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.4f, 0.0f, 0.0f);

        final FrameManager.FrameState f = frame();
        final int inPassBefore = f.inPassClearsThisFrame;

        GLSMBlend.glColorMask(true, false, false, false);
        GLSMBlend.glClearColor(0.6f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMBlend.glColorMask(true, true, true, true);

        assertEquals(inPassBefore, f.inPassClearsThisFrame, "masking every present channel on a single-red-channel target must take the load-op path, not in-pass");

        GLSMFramebuffer.glDeleteFramebuffers(fbo);
        GLSMTextures.glDeleteTextures(tex);
        GlsmSdlHeadlessRig.bindTarget();
    }

    @Test
    void stencilWrittenLastFramePersists() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.writeStencilOneInBottomHalf();

        GlsmSdlHeadlessRig.endFrame();
        GlsmSdlHeadlessRig.beginFrameAndReset();
        GlsmSdlHeadlessRig.drawRedWhereStencilIsOne();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, 4, RED, "stencil written last frame");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE - 5, BLACK, "outside last frame's stencil");
    }

    @Test
    void aSingleAspectClearLeavesTheTextureUndefined() {
        final int fbo = GlsmSdlHeadlessRig.createDepthStencilFbo();
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        final ResourceManager resourceManager = GlsmSdlHeadlessRig.resourceManager();
        final long depthTexture = resourceManager.getFbo(fbo).depthTexture;
        GlsmSdlHeadlessRig.endFrame();

        assertFalse(resourceManager.isTextureContentDefined(depthTexture), "the stencil aspect was never written, so the first pass must still clear it");
    }
}
