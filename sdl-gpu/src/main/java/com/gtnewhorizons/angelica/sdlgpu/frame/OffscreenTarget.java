package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COLOR_TARGET;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_SAMPLER;

public final class OffscreenTarget {

    private int fboGlId;
    private int colorTexGlId;
    private long colorTexHandle;
    private int width;
    private int height;
    private boolean hasContent;

    public void create(Device device, ResourceManager rm, int width, int height, int swapchainSdlFormat) {
        this.width = width;
        this.height = height;

        this.colorTexGlId = rm.genTexture();
        this.colorTexHandle = rm.createTextureWithSdlFormat(colorTexGlId, GL11.GL_TEXTURE_2D, swapchainSdlFormat, GL11.GL_RGBA8, width, height, 1, 1, SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);

        this.fboGlId = rm.genFboId();
        final FboState fbo = rm.createFbo(fboGlId);
        fbo.colorTextures[0] = colorTexHandle;
        fbo.colorGlIds[0] = colorTexGlId;
        fbo.colorFormats[0] = swapchainSdlFormat;
        fbo.colorAttachmentCount = 1;
        fbo.drawBuffers = new int[]{0};
        fbo.width = width;
        fbo.height = height;
        fbo.targetsDirty = true;
        fbo.cachedFormatsDirty = true;
    }

    public void ensureSize(Device device, ResourceManager rm, int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (colorTexHandle != 0 && width == this.width && height == this.height) return;
        destroy(rm);
        create(device, rm, width, height, device.getSwapchainTextureFormat());
    }

    public int fboId() { return fboGlId; }
    public long colorTexture() { return colorTexHandle; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean isReady() { return colorTexHandle != 0; }
    public boolean hasContent() { return hasContent; }
    public void markContent() { hasContent = true; }

    public boolean isFor(ContextState st) {
        return fboGlId != 0 && st.boundFboId == fboGlId;
    }

    public void destroy(ResourceManager rm) {
        if (fboGlId != 0) {
            rm.deleteFbo(fboGlId);
            fboGlId = 0;
        }
        if (colorTexGlId != 0) {
            rm.deleteTexture(colorTexGlId);
            colorTexGlId = 0;
            colorTexHandle = 0;
        }
        hasContent = false;
    }
}
