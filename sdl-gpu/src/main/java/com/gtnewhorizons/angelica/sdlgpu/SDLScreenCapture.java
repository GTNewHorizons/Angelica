package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.glsm.backend.ScreenCapture;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ColorSource;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.TextureOps;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.sdl.SDLSurface.SDL_FLIP_NONE;

final class SDLScreenCapture extends ScreenCapture {

    private final SDLGPURenderBackend backend;
    private final Device device;
    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final TextureOps textureOps;

    private final long[] textures = new long[RING];
    private final long[] transferBuffers = new long[RING];

    SDLScreenCapture(SDLGPURenderBackend backend, Device device, FrameManager frameManager, ResourceManager resourceManager, TextureOps textureOps, int width, int height) {
        super(width, height);
        this.backend = backend;
        this.device = device;
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.textureOps = textureOps;
    }

    @Override
    protected boolean start(int slot, int srcFramebuffer, int srcWidth, int srcHeight) {
        if (frameManager.getCommandBuffer() == 0) return false;
        final ColorSource source = backend.colorSourceForRead(srcFramebuffer);
        if (source == null) return false;

        final int w = width();
        final int h = height();
        if (textures[slot] == 0) {
            textures[slot] = resourceManager.createScratchTexture(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, w, h, SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
        }
        if (transferBuffers[slot] == 0) {
            transferBuffers[slot] = resourceManager.createTransferBuffer((long) w * h * 4, SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD);
            if (transferBuffers[slot] == 0) return false;
        }

        textureOps.blitTexture(source.texture, source.level, 0, 0, Math.min(srcWidth, source.width), Math.min(srcHeight, source.height), textures[slot], 0, 0, 0, w, h, GL11.GL_LINEAR, SDL_FLIP_NONE);
        final long copyPass = frameManager.ensureCopyPass();
        if (copyPass == 0) return false;
        resourceManager.recordTextureDownload(copyPass, textures[slot], 0, 0, 0, 0, w, h, 1, transferBuffers[slot]);
        return true;
    }

    @Override
    protected long map(int slot) {
        return nSDL_MapGPUTransferBuffer(device.getDevice(), transferBuffers[slot], false);
    }

    @Override
    protected void unmap(int slot) {
        resourceManager.unmapTransferBuffer(transferBuffers[slot]);
    }

    @Override
    protected void deleteSlot(int slot) {
        resourceManager.releaseTextureDeferred(textures[slot]);
        resourceManager.releaseTransferBuffer(transferBuffers[slot]);
        textures[slot] = 0L;
        transferBuffers[slot] = 0L;
    }
}
