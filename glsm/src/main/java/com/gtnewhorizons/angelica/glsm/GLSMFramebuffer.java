package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.MainThread;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.requireContext;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMFramebuffer {

    private GLSMFramebuffer() {}

    // This setting varies depending on driver, so it gets queried at runtime
    public static int DEFAULT_DRAW_BUFFER = GL11.GL_BACK;
    public static boolean framebufferCompletenessIsMeaningful() { return RENDER_BACKEND.framebufferCompletenessIsMeaningful(); }

    static int drawFramebufferBits(GLContextState glCtx, boolean stencil) {
        final int sizePname = stencil ? GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE : GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE;
        if (glCtx.drawFramebuffer == 0) {
            return RENDER_BACKEND.getFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, stencil ? GL11.GL_STENCIL : GL11.GL_DEPTH, sizePname);
        }
        final int attachment = stencil ? GL30.GL_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT;
        if (RENDER_BACKEND.getFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_NONE) {
            return 0;
        }
        return RENDER_BACKEND.getFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, sizePname);
    }

    static void queryDefaultDrawBuffer() {
        DEFAULT_DRAW_BUFFER = RENDER_BACKEND.getInteger(GL11.GL_DRAW_BUFFER);
    }

    static void armFirstClear() {
        firstClearPending = true;
    }

    public static void glDrawBuffer(int mode) {
        rejectExitCapture();
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffer(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        if (!isCachingEnabled() || ctx().drawBuffer.getValue() != mode) beforeUncapturedStateChange();
        if (isCachingEnabled()) mod(ctx().drawBuffer).setValue(mode);
        RENDER_BACKEND.drawBuffer(mode);
    }

    private static boolean firstClearPending;
    private static long firstClearBlockNanos;

    public static void glClear(int mask) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClear(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (ctx().rasterizerDiscard.isEnabled() || FeedbackManager.getRenderMode() != GL11.GL_RENDER) {
            return;
        }
        if (firstClearPending && Thread.currentThread() == MainThread && getDrawFramebuffer() == 0) {
            firstClearPending = false;
            final long start = System.nanoTime();
            RENDER_BACKEND.clear(mask);
            final long end = System.nanoTime();
            if (end - start > 0) {
                firstClearBlockNanos += end - start;
                RENDER_BACKEND.recordGate(end - start, end);
            }
            return;
        }
        RENDER_BACKEND.clear(mask);
    }

    public static long takeFirstClearBlockNanos() {
        final long value = firstClearBlockNanos;
        firstClearBlockNanos = 0L;
        return value;
    }

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        type = GLSMTextures.remapPixelTypeForGLES(format, type);
        GLSMBuffers.suspendPixelPackBuffer();
        RENDER_BACKEND.readPixels(x, y, width, height, format, type, pixels);
        GLSMBuffers.restorePixelPackBuffer();
    }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        rejectExitCapture();
        type = GLSMTextures.remapPixelTypeForGLES(format, type);
        GLSMBuffers.suspendPixelPackBuffer();
        RENDER_BACKEND.readPixels(x, y, width, height, format, type, pixels);
        GLSMBuffers.restorePixelPackBuffer();
    }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        rejectExitCapture();
        type = GLSMTextures.remapPixelTypeForGLES(format, type);
        GLSMBuffers.suspendPixelPackBuffer();
        RENDER_BACKEND.readPixels(x, y, width, height, format, type, pixels);
        GLSMBuffers.restorePixelPackBuffer();
    }

    public static void glReadBuffer(int mode) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glReadBuffer");
        }
        RENDER_BACKEND.readBuffer(mode);
    }

    public static void glDrawBuffers(int buffer) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(1, buffer);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        beforeUncapturedStateChange();
        RENDER_BACKEND.drawBuffers(buffer);
    }

    public static void glDrawBuffers(IntBuffer bufs) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(bufs.remaining(), bufs);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        beforeUncapturedStateChange();
        RENDER_BACKEND.drawBuffers(bufs);
    }
    public static boolean glIsFramebuffer(int framebuffer) { rejectExitCapture(); return RENDER_BACKEND.isFramebuffer(framebuffer); }
    public static boolean glIsRenderbuffer(int renderbuffer) { rejectExitCapture(); return RENDER_BACKEND.isRenderbuffer(renderbuffer); }

    public static void glBindFramebuffer(int target, int framebuffer) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (target == GL30.GL_FRAMEBUFFER) {
            if (glCtx.drawFramebuffer == framebuffer && glCtx.readFramebuffer == framebuffer) return;
            if (glCtx.drawFramebuffer != framebuffer) glCtx.drawFramebufferGeneration++;
            glCtx.drawFramebuffer = framebuffer;
            glCtx.readFramebuffer = framebuffer;
        } else if (target == GL30.GL_DRAW_FRAMEBUFFER) {
            if (glCtx.drawFramebuffer == framebuffer) return;
            glCtx.drawFramebufferGeneration++;
            glCtx.drawFramebuffer = framebuffer;
        } else if (target == GL30.GL_READ_FRAMEBUFFER) {
            if (glCtx.readFramebuffer == framebuffer) return;
            glCtx.readFramebuffer = framebuffer;
        }
        RENDER_BACKEND.bindFramebuffer(target, framebuffer);
    }

    public static void glDeleteFramebuffers(int framebuffer) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (glCtx.drawFramebuffer == framebuffer) { glCtx.drawFramebuffer = 0; glCtx.drawFramebufferGeneration++; }
        if (glCtx.readFramebuffer == framebuffer) glCtx.readFramebuffer = 0;
        final int[] slots = glCtx.framebufferTextures.remove(framebuffer);
        if (slots != null) {
            for (int slot = 0; slot < slots.length; slot++) GLSMTextureLifetime.setFramebufferSlot(slots, slot, 0);
        }
        RENDER_BACKEND.deleteFramebuffers(framebuffer);
    }

    public static void glDeleteFramebuffers(IntBuffer framebuffers) {
        for (int i = framebuffers.position(); i < framebuffers.limit(); i++) glDeleteFramebuffers(framebuffers.get(i));
    }

    public static void glDeleteFramebuffers(int[] framebuffers) {
        for (int framebuffer : framebuffers) glDeleteFramebuffers(framebuffer);
    }

    public static int glGenFramebuffers() {
        requireContext("glGenFramebuffers");
        return RENDER_BACKEND.genFramebuffers();
    }

    public static int glCheckFramebufferStatus(int target) {
        rejectExitCapture();
        return RENDER_BACKEND.checkFramebufferStatus(target);
    }

    public static int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        rejectExitCapture();
        return RENDER_BACKEND.getFramebufferAttachmentParameteri(target, attachment, pname);
    }

    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        RENDER_BACKEND.framebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public static void glFramebufferTexture1D(int target, int attachment, int textarget, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        RENDER_BACKEND.framebufferTexture1D(target, attachment, textarget, texture, level);
    }

    public static void glFramebufferTexture3D(int target, int attachment, int textarget, int texture, int level, int zoffset) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        RENDER_BACKEND.framebufferTexture3D(target, attachment, textarget, texture, level, zoffset);
    }

    public static void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        RENDER_BACKEND.framebufferTextureLayer(target, attachment, texture, level, layer);
    }

    public static void glFramebufferTexture(int target, int attachment, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        RENDER_BACKEND.framebufferTexture(target, attachment, texture, level);
    }

    public static void glFramebufferTextureFace(int target, int attachment, int texture, int level, int face) {
        glFramebufferTexture2D(target, attachment, face, texture, level);
    }

    public static void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(ctx(), framebuffer, attachment, texture)) return;
        RENDER_BACKEND.namedFramebufferTexture(framebuffer, attachment, texture, level);
    }

    private static int swapDrawFramebuffer(int framebuffer) {
        final GLContextState glCtx = ctx();
        final int previous = glCtx.drawFramebuffer;
        glCtx.drawFramebuffer = framebuffer;
        RENDER_BACKEND.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        return previous;
    }

    public static void glNamedFramebufferTextureLayer(int framebuffer, int attachment, int texture, int level, int layer) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTextureLayer(GL30.GL_DRAW_FRAMEBUFFER, attachment, texture, level, layer);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferRenderbuffer(int framebuffer, int attachment, int renderbuffertarget, int renderbuffer) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, attachment, renderbuffertarget, renderbuffer);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTexture1DEXT(int framebuffer, int attachment, int textarget, int texture, int level) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture1D(GL30.GL_DRAW_FRAMEBUFFER, attachment, textarget, texture, level);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTexture2DEXT(int framebuffer, int attachment, int textarget, int texture, int level) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment, textarget, texture, level);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTexture3DEXT(int framebuffer, int attachment, int textarget, int texture, int level, int zoffset) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture3D(GL30.GL_DRAW_FRAMEBUFFER, attachment, textarget, texture, level, zoffset);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTextureEXT(int framebuffer, int attachment, int texture, int level) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture(GL30.GL_DRAW_FRAMEBUFFER, attachment, texture, level);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTextureFaceEXT(int framebuffer, int attachment, int texture, int level, int face) {
        glNamedFramebufferTexture2DEXT(framebuffer, attachment, face, texture, level);
    }

    public static void attachTextureForReading(int attachment, int texture) {
        if (GLSMTextureLifetime.isDeferredDelete(texture)) return;
        final GLContextState glCtx = ctx();
        GLSMTextureLifetime.trackFramebufferAttachment(glCtx, glCtx.readFramebuffer, attachment, texture);
        RENDER_BACKEND.framebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, 0);
    }

    public static int glGenRenderbuffers() {
        requireContext("glGenRenderbuffers");
        return RENDER_BACKEND.genRenderbuffers();
    }
    public static void glDeleteRenderbuffers(int renderbuffer) { rejectExitCapture(); RENDER_BACKEND.deleteRenderbuffers(renderbuffer); }
    public static void glBindRenderbuffer(int target, int renderbuffer) { rejectExitCapture(); RENDER_BACKEND.bindRenderbuffer(target, renderbuffer); }
    public static void glRenderbufferStorage(int target, int internalformat, int width, int height) { rejectExitCapture(); RENDER_BACKEND.renderbufferStorage(target, internalformat, width, height); }
    public static void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) { rejectExitCapture(); RENDER_BACKEND.renderbufferStorageMultisample(target, samples, internalformat, width, height); }
    public static void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        rejectExitCapture();
        GLSMTextureLifetime.beginAttach(target, attachment, 0);
        RENDER_BACKEND.framebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        rejectExitCapture();
        RENDER_BACKEND.blitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public static boolean isFramebufferEnabled() {
        return true;
    }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        rejectExitCapture();
        RENDER_BACKEND.readPixels(x, y, width, height, format, GLSMTextures.remapPixelTypeForGLES(format, type), pixels);
    }

    public static int getDrawFramebufferGeneration() {
        return ctx().drawFramebufferGeneration;
    }

    public static IntegerStateStack getDrawBuffer() {
        return ctx().drawBuffer;
    }

    public static int getDrawFramebuffer() {
        return ctx().drawFramebuffer;
    }

    public static int getReadFramebuffer() {
        return ctx().readFramebuffer;
    }
}
