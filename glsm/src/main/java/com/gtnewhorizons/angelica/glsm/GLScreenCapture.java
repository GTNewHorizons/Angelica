package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.backend.ScreenCapture;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.states.PixelStoreState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public final class GLScreenCapture extends ScreenCapture {

    private final int[] textures = new int[RING];
    private final int[] framebuffers = new int[RING];
    private final int[] buffers = new int[RING];

    public GLScreenCapture(int width, int height) {
        super(width, height);
    }

    @Override
    protected boolean start(int slot, int srcFramebuffer, int srcWidth, int srcHeight) {
        final int width = width();
        final int height = height();
        final int previousRead = GLSMFramebuffer.getReadFramebuffer();
        final int previousDraw = GLSMFramebuffer.getDrawFramebuffer();
        final int previousPack = GLStateManager.ctx().boundPixelPackBuffer;
        final boolean scissor = GLSMRasterizer.getScissorTest().isEnabled();
        BatchStateGuard.suspend();
        try {
            if (framebuffers[slot] == 0) create(slot);
            if (scissor) GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFramebuffer);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[slot]);
            GLSMFramebuffer.glBlitFramebuffer(0, 0, srcWidth, srcHeight, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffers[slot]);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
            GLSMTextures.forcePixelPackState(PixelStoreState.DEFAULT);
            GLSMFramebuffer.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            return true;
        } finally {
            GLSMTextures.restorePixelPackState(PixelStoreState.DEFAULT);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            if (scissor) GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
            BatchStateGuard.resume();
        }
    }

    private void create(int slot) {
        final int previousTexture = GLSMTextures.getBoundTextureForServerState();
        final int texture;
        try {
            texture = GLSMTextures.glGenTextures();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width(), height(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } finally {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
        textures[slot] = texture;
        framebuffers[slot] = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[slot]);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        buffers[slot] = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
        GLSMBuffers.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) width() * height() * 4, GL15.GL_STREAM_READ);
    }

    @Override
    protected long map(int slot) {
        final int previousPack = GLStateManager.ctx().boundPixelPackBuffer;
        GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
        try {
            return GLSMBuffers.glMapBufferRangeAddress(GL21.GL_PIXEL_PACK_BUFFER, 0L, (long) width() * height() * 4, GL30.GL_MAP_READ_BIT);
        } finally {
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack);
        }
    }

    @Override
    protected void unmap(int slot) {
        final int previousPack = GLStateManager.ctx().boundPixelPackBuffer;
        GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffers[slot]);
        GLSMBuffers.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack);
    }

    @Override
    protected void deleteSlot(int slot) {
        if (buffers[slot] != 0) GLSMBuffers.glDeleteBuffers(buffers[slot]);
        if (framebuffers[slot] != 0) GLSMFramebuffer.glDeleteFramebuffers(framebuffers[slot]);
        if (textures[slot] != 0) GLSMTextures.glDeleteTextures(textures[slot]);
        buffers[slot] = 0;
        framebuffers[slot] = 0;
        textures[slot] = 0;
    }
}
