package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlAttachmentClearTest {

    private static final int SIZE = GlsmSdlHeadlessRig.SIZE;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    private static FrameManager frameManager() {
        return Reflect.get(BackendManager.RENDER_BACKEND, "frameManager");
    }

    private static FrameManager.FrameState frame() {
        return frameManager().frame();
    }

    private static void setDisableInPassClear(boolean value) {
        try {
            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            final Unsafe unsafe = (Unsafe) theUnsafe.get(null);
            final Field field = SystemProperties.class.getDeclaredField("SDL_DISABLE_IN_PASS_CLEAR");
            unsafe.putBoolean(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void drawFullQuadAtZ(float z, float r, float g, float b) {
        GLStateManager.glColor4f(r, g, b, 1.0f);
        GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glVertex3f(-1.0f, -1.0f, z);
        GLStateManager.glVertex3f(1.0f, -1.0f, z);
        GLStateManager.glVertex3f(1.0f, 1.0f, z);
        GLStateManager.glVertex3f(-1.0f, 1.0f, z);
        GLStateManager.glEnd();
    }

    private static boolean isRed(int argb) {
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        return r > 200 && g < 64;
    }

    private static boolean depthEquals(float depthValue) {
        GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.glDepthMask(false);
        drawFullQuadAtZ(depthValue * 2.0f - 1.0f, 1.0f, 0.0f, 0.0f);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        return isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2));
    }

    @Test
    void depthMaskOffSuppressesTheClearAndBreaksNoPass() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClearDepth(0.9);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

        final FrameManager.FrameState f = frame();
        final long passBefore = f.renderPass;
        assertTrue(passBefore != 0, "expected an active render pass before the masked clear");
        final int inPassBefore = f.inPassClearsThisFrame;
        final int passEndClearBefore = f.passEndCauseCountsThisFrame[FrameManager.PASS_END_CLEAR];

        GLStateManager.glDepthMask(false);
        GLStateManager.glClearDepth(0.0);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLStateManager.glDepthMask(true);

        assertEquals(passBefore, f.renderPass, "a masked clear must not break the active render pass");
        assertEquals(inPassBefore, f.inPassClearsThisFrame, "a masked clear must not take the in-pass path");
        assertEquals(passEndClearBefore, f.passEndCauseCountsThisFrame[FrameManager.PASS_END_CLEAR], "a masked clear must not end the pass for a clear");
        assertTrue(depthEquals(0.9f), "a masked clear must leave the prior depth content untouched");
    }

    @Test
    void inPassPathIsTakenForACompatibleActivePass() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClearDepth(0.9);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

        final FrameManager.FrameState f = frame();
        final long passBefore = f.renderPass;
        assertTrue(passBefore != 0, "expected an active render pass before the clear");
        final int inPassBefore = f.inPassClearsThisFrame;

        GLStateManager.glDepthMask(true);
        GLStateManager.glClearDepth(0.25);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        assertEquals(passBefore, f.renderPass, "the in-pass clear must not break the active render pass");
        assertEquals(inPassBefore + 1, f.inPassClearsThisFrame, "the in-pass clear must be taken exactly once");
        assertTrue(depthEquals(0.25f), "the in-pass clear must have written the new depth value");
    }

    @Test
    void disableInPassClearFlagForcesTheLegacyPath() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClearDepth(0.9);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

        final FrameManager.FrameState f = frame();
        assertTrue(f.renderPass != 0, "expected an active render pass before the clear");
        final int inPassBefore = f.inPassClearsThisFrame;

        setDisableInPassClear(true);
        try {
            GLStateManager.glDepthMask(true);
            GLStateManager.glClearDepth(0.6);
            GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);
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
            GlsmSdlHeadlessRig.bindTarget();
            GLStateManager.glDepthMask(true);
            GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLStateManager.glClearDepth(0.9);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

            GLStateManager.glDepthMask(true);
            GLStateManager.glClearDepth(depthValue);
            GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            return depthEquals(depthValue);
        } finally {
            setDisableInPassClear(false);
        }
    }

    @Test
    void scissoredClearOnlyAffectsTheScissorRect() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClearDepth(0.9);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, 0, SIZE / 2, SIZE);
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearDepth(0.0);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);

        GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.glDepthMask(false);

        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, 0, SIZE / 2, SIZE);
        drawFullQuadAtZ(-1.0f, 1.0f, 0.0f, 0.0f);
        GLStateManager.glScissor(SIZE / 2, 0, SIZE - SIZE / 2, SIZE);
        drawFullQuadAtZ(0.9f * 2.0f - 1.0f, 1.0f, 0.0f, 0.0f);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 4, SIZE / 2)), "inside the scissor rect the depth must have been cleared to 0");
        assertTrue(isRed(GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2 + SIZE / 4, SIZE / 2)), "outside the scissor rect the prior depth must be untouched");
    }

    @Test
    void stencilWriteMaskIsHonored() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glClearStencil(0xA5);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

        final FrameManager.FrameState f = frame();
        final long passBefore = f.renderPass;
        final int inPassBefore = f.inPassClearsThisFrame;

        GLStateManager.glClearStencil(0x3F);
        GLStateManager.glStencilMask(0x0F);
        GLStateManager.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GLStateManager.glStencilMask(0xFF);

        assertEquals(passBefore, f.renderPass, "the stencil-only clear must not break the active render pass");
        assertEquals(inPassBefore + 1, f.inPassClearsThisFrame, "the stencil clear must take the in-pass path");

        final StencilProbeResult result = probeStencilReconcile();
        assertEquals(0xAF, result.bitPlaneValue(), "bit-plane read: only the low nibble should have been replaced: 0xA5 & ~0x0F | 0x3F & 0x0F == 0xAF");
        assertFalse(result.fullMask3F(), "GL_EQUAL(ref=0x3F, mask=0xFF) must not match: the write mask must have been honored, not ignored");
    }

    @Test
    @Disabled("stencil reference >= 0x80 compares as 0xFF on this backend; see stencil reference finding")
    void stencilReferenceAtOrAbove0x80ComparesExactly() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glClearStencil(0xAF);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

        final StencilProbeResult result = probeStencilReconcile();
        assertEquals(0xAF, result.bitPlaneValue(), "bit-plane read of a plain 0xAF stencil clear");
        assertTrue(result.fullMaskAF(), "GL_EQUAL(ref=0xAF, mask=0xFF) must match a stored 0xAF");
    }

    @Test
    void baselineStencilClearReadsBackExactly() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glClearStencil(0xA5);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.1f, 0.1f, 0.1f);

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
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(x, 0, w, SIZE);
        GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
        GLStateManager.glStencilFunc(func, ref, mask);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        drawFullQuadAtZ(0.0f, 1.0f, 0.0f, 0.0f);
        GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static int stripCenter(int index, int regionW, int strips) {
        final int x = index * regionW;
        final int w = (index == strips - 1) ? SIZE - x : regionW;
        return x + w / 2;
    }

    @Test
    void colorIsUntouchedByADepthStencilOnlyClear() {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glDepthMask(true);
        GLStateManager.glClearColor(0.2f, 0.4f, 0.6f, 1.0f);
        GLStateManager.glClearDepth(0.9);
        GLStateManager.glClearStencil(0x55);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GlsmSdlHeadlessRig.solidQuad(0.2f, 0.4f, 0.6f);

        GLStateManager.glDepthMask(true);
        GLStateManager.glClearDepth(0.1);
        GLStateManager.glClearStencil(0x77);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int argb = GlsmSdlHeadlessRig.pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2);
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        assertTrue(Math.abs(r - 51) <= 2, "red must be untouched: " + r);
        assertTrue(Math.abs(g - 102) <= 2, "green must be untouched: " + g);
        assertTrue(Math.abs(b - 153) <= 2, "blue must be untouched: " + b);
    }
}
