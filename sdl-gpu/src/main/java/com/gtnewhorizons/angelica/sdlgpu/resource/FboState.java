package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.Hashing;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;

import static com.gtnewhorizons.angelica.sdlgpu.frame.ContextState.MAX_COLOR_ATTACHMENTS;

public final class FboState {
    public final long[] colorTextures = new long[MAX_COLOR_ATTACHMENTS];
    public final int[] colorGlIds = new int[MAX_COLOR_ATTACHMENTS];
    public final int[] colorFormats = new int[MAX_COLOR_ATTACHMENTS];
    public int colorAttachmentCount;

    public int[] drawBuffers = {0};
    public int readBufferIndex;

    public long depthTexture;
    public int depthGlId;
    public int depthFormat;
    public int width, height;

    public int[] cachedColorFormats;
    public boolean cachedFormatsDirty = true;

    public long primaryTarget;
    public boolean hasAnyColor;
    public boolean targetsDirty = true;

    public final SDL_GPUColorTargetInfo.Buffer cachedColorTargets = SDL_GPUColorTargetInfo.calloc(MAX_COLOR_ATTACHMENTS);
    public final SDL_GPUDepthStencilTargetInfo cachedDepthTarget = SDL_GPUDepthStencilTargetInfo.calloc();
    public long cachedTargetsLayoutHash;
    public long structuralLayoutHash;
    public int cachedTargetsCount = -1;
    public int cachedClearOpFlags;
    public boolean cachedDepthClearLast;
    public boolean cachedStencilClearLast;
    public boolean cachedTargetsValid;

    public long getColorTexture() { return colorTextures[0]; }
    public int getColorGlId() { return colorGlIds[0]; }
    public int getColorFormat() { return colorFormats[0]; }

    public void recomputeTargets() {
        boolean anyColor = false;
        long firstColor = 0;
        for (int db : drawBuffers) {
            if (db >= 0 && db < MAX_COLOR_ATTACHMENTS && colorTextures[db] != 0) {
                anyColor = true;
                firstColor = colorTextures[db];
                break;
            }
        }
        hasAnyColor = anyColor;
        primaryTarget = anyColor ? firstColor : depthTexture;
        targetsDirty = false;

        long h = Hashing.fmix64(0x9E3779B97F4A7C15L, drawBuffers.length);
        for (int i = 0; i < drawBuffers.length; i++) {
            final int db = drawBuffers[i];
            final long tex = (db >= 0 && db < MAX_COLOR_ATTACHMENTS) ? colorTextures[db] : 0L;
            h = Hashing.fmix64(h, tex);
            h = Hashing.fmix64(h, db);
        }
        h = Hashing.fmix64(h, depthTexture);
        if (h == 0L || h == FrameManager.FBO0_LAYOUT_HASH) h = 0xD1CE5EEDL;
        structuralLayoutHash = h;
    }

    public void detachColor(int colorIdx) {
        if (colorIdx < 0 || colorIdx >= MAX_COLOR_ATTACHMENTS) return;
        colorTextures[colorIdx] = 0L;
        colorGlIds[colorIdx] = 0;
        colorFormats[colorIdx] = 0;
        int newCount = 0;
        for (int i = 0; i < MAX_COLOR_ATTACHMENTS; i++) {
            if (colorTextures[i] != 0L) newCount = i + 1;
        }
        colorAttachmentCount = newCount;
        targetsDirty = true;
        cachedFormatsDirty = true;
    }

    public void detachDepth() {
        depthTexture = 0L;
        depthGlId = 0;
        depthFormat = 0;
        targetsDirty = true;
    }

    public void free() {
        cachedColorTargets.free();
        cachedDepthTarget.free();
    }
}
