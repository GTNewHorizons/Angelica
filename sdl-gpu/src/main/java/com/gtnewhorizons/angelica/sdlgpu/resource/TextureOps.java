package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUBlitInfo;
import org.lwjgl.sdl.SDL_GPUTextureLocation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.sdl.SDLGPU.*;

public final class TextureOps {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private final Device device;
    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final FBOClearTracker fboClearTracker;

    public TextureOps(Device device, FrameManager frameManager, ResourceManager resourceManager, FBOClearTracker fboClearTracker) {
        this.device = device;
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.fboClearTracker = fboClearTracker;
    }

    public void releaseTextureForRealloc(ContextState st, int glId) {
        fboClearTracker.scrubPendingClearsForTexture(st, glId);
        resourceManager.releaseTextureHandleForRealloc(glId);
    }

    public boolean uploadTextureRegion(ContextState st, int glId, ResourceManager.TextureMeta meta, long texHandle, ByteBuffer src, int x, int y, int w, int h, int level, int srcFormat, int srcType) {
        if (meta == null || level >= meta.levels()) return false;
        if (src == null || texHandle == 0 || frameManager.getCommandBuffer() == 0) return false;
        final boolean defer = st.deferUploads && !resourceManager.isFboAttachment(glId);
        final long cp = defer ? 0L : frameManager.ensureCopyPass();
        if (!defer && cp == 0) return false;
        final ByteBuffer unpacked = PixelOps.applyUnpackPixelStore(src, w, h, srcFormat, srcType, st.pixelStore);
        final ByteBuffer prepped = PixelOps.prepareUploadBuffer(srcFormat, meta.sdlFormat(), unpacked, w, h);
        try {
            final boolean handled = defer && resourceManager.enqueueDeferredTextureUpload(st, prepped, texHandle, x, y, w, h, level);
            if (!handled) {
                final long inlineCp = (cp != 0) ? cp : frameManager.ensureCopyPass();
                if (inlineCp == 0) return false;
                resourceManager.uploadToTexture(inlineCp, prepped, texHandle, x, y, w, h, level);
            }
        } finally {
            if (prepped != unpacked) MemoryUtil.memFree(prepped);
            if (unpacked != src) MemoryUtil.memFree(unpacked);
        }
        trackMipUploadForTexture(glId, level, meta.levels());
        return true;
    }

    public void texParameteri(ContextState st, int pname, int param, int glId) {
        if (glId == 0) return;
        final TextureSamplerState ss = resourceManager.getOrCreateTexSamplerState(glId);
        switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> ss.minFilter = param;
            case GL11.GL_TEXTURE_MAG_FILTER -> ss.magFilter = param;
            case GL11.GL_TEXTURE_WRAP_S -> ss.wrapS = param;
            case GL11.GL_TEXTURE_WRAP_T -> ss.wrapT = param;
            case GL12.GL_TEXTURE_WRAP_R -> ss.wrapR = param;
            case GL12.GL_TEXTURE_MAX_LEVEL -> {
                ss.maxLevel = param; return;
            }
            case GL12.GL_TEXTURE_MIN_LOD -> ss.minLod = (float) param;
            case GL12.GL_TEXTURE_MAX_LOD -> ss.maxLod = (float) param;
            case GL14.GL_TEXTURE_COMPARE_MODE -> ss.compareMode = param;
            case GL14.GL_TEXTURE_COMPARE_FUNC -> ss.compareFunc = param;
            default -> { return; }
        }
        ss.sdlSampler = 0;
        st.samplerBindGen++;
    }

    public void texParameterf(ContextState st, int pname, float param, int glId) {
        if (glId == 0) return;
        final TextureSamplerState ss = resourceManager.getOrCreateTexSamplerState(glId);
        switch (pname) {
            case GL12.GL_TEXTURE_MIN_LOD -> ss.minLod = param;
            case GL12.GL_TEXTURE_MAX_LOD -> ss.maxLod = param;
            case GL14.GL_TEXTURE_LOD_BIAS -> ss.lodBias = param;
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> ss.maxAnisotropy = param;
            default -> { texParameteri(st, pname, (int) param, glId); return; }
        }
        ss.sdlSampler = 0;
        st.samplerBindGen++;
    }

    public void readbackTexture(long texHandle, int x, int y, int w, int h, int level, ByteBuffer output) {
        if (texHandle == 0 || output == null) return;
        frameManager.submitMidFrame();

        final long cb = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (cb == 0) return;
        resourceManager.downloadFromTexture(cb, texHandle, x, y, w, h, level, output);
    }

    public void copyTexSubImageImpl(ContextState st, int destGlId, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (destGlId == 0) return;
        final long destTex = resourceManager.getTextureHandle(destGlId);
        if (destTex == 0) return;

        final FboState fbo = resourceManager.getFbo(st.boundReadFboId);
        if (fbo == null) return;

        final ResourceManager.TextureMeta destMeta = resourceManager.getTextureMeta(destGlId);
        final boolean isDepthDest = destMeta != null && PixelOps.isDepthFormat(destMeta.sdlFormat());
        final long srcTex = isDepthDest ? fbo.depthTexture : fbo.colorTextures[fbo.readBufferIndex];
        if (srcTex == 0) return;

        if (isDepthDest) {
            copyTexture(srcTex, x, y, destTex, xoffset, yoffset, width, height);
            return;
        }

        final int srcGlId = fbo.colorGlIds[fbo.readBufferIndex];
        final ResourceManager.TextureMeta srcMeta = srcGlId != 0 ? resourceManager.getTextureMeta(srcGlId) : null;
        if (srcMeta != null && destMeta != null && srcMeta.sdlFormat() == destMeta.sdlFormat()) {
            copyTexture(srcTex, x, y, destTex, xoffset, yoffset, width, height);
        } else {
            blitTexture(srcTex, x, y, width, height, destTex, xoffset, yoffset, width, height, GL11.GL_NEAREST);
        }
    }

    public static boolean canCopyInsteadOfBlit(ResourceManager.TextureMeta src, ResourceManager.TextureMeta dst, int srcW, int srcH, int dstW, int dstH) {
        return src != null && dst != null && src.sdlFormat() == dst.sdlFormat() && srcW > 0 && srcH > 0 && srcW == dstW && srcH == dstH;
    }

    public void copyTexture(long srcTex, int srcX, int srcY, long dstTex, int dstX, int dstY, int w, int h) {
        final long cp = frameManager.ensureCopyPass();
        if (cp == 0) return;
        try (var stack = MemoryStack.stackPush()) {
            final var src = SDL_GPUTextureLocation.calloc(stack).texture(srcTex).x(srcX).y(srcY);
            final var dst = SDL_GPUTextureLocation.calloc(stack).texture(dstTex).x(dstX).y(dstY);
            SDL_CopyGPUTextureToTexture(cp, src, dst, w, h, 1, false);
        }
    }

    public void blitTexture(long srcTex, int srcX, int srcY, int srcW, int srcH, long dstTex, int dstX, int dstY, int dstW, int dstH, int glFilter) {
        frameManager.endCopyPassIfActive();
        frameManager.endRenderPassIfActive();
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) return;

        try (var stack = MemoryStack.stackPush()) {
            final var info = SDL_GPUBlitInfo.calloc(stack);
            info.source(s -> s.texture(srcTex).x(srcX).y(srcY).w(srcW).h(srcH));
            info.destination(d -> d.texture(dstTex).x(dstX).y(dstY).w(dstW).h(dstH));
            info.filter(glFilter == GL11.GL_LINEAR ? SDL_GPU_FILTER_LINEAR : SDL_GPU_FILTER_NEAREST);
            info.flip_mode(0);
            frameManager.noteBlit();
            SDL_BlitGPUTexture(cb, info);
        }
    }

    public void trackMipUploadForTexture(int glId, int level, int levels) {
        final FrameManager.FrameState f = frameManager.frame();
        if (level > 0) {
            f.pendingMipGen.remove(glId);
        } else if (levels > 1) {
            f.pendingMipGen.add(glId);
        }
    }

    public void clearPendingMipGen(int glId) {
        frameManager.frame().pendingMipGen.remove(glId);
    }

    public void drainPendingMipGen() {
        final FrameManager.FrameState f = frameManager.frame();
        if (f.pendingMipGen.isEmpty()) return;
        final long uploadCb = f.commandBuffer != 0 ? f.commandBuffer : f.pendingUploadCommandBuffer;
        if (uploadCb == 0) { f.pendingMipGen.clear(); return; }

        final int[] glIds = f.pendingMipGen.toIntArray();
        f.pendingMipGen.clear();

        frameManager.endCopyPassIfActive();
        frameManager.endRenderPassIfActive();

        for (int glId : glIds) {
            final long handle = resourceManager.getTextureHandle(glId);
            if (handle == 0) continue;
            final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(glId);
            if (meta == null || meta.levels() <= 1) continue;
            SDL_GenerateMipmapsForGPUTexture(uploadCb, handle);
        }

        if (!SDL_SubmitGPUCommandBuffer(uploadCb)) {
            device.reportGpuFailure("submit mipmap generation CB");
        }
        frameManager.noteMipGenSubmit();

        if (f.commandBuffer == uploadCb) {
            f.commandBuffer = 0;
        } else {
            f.pendingUploadCommandBuffer = 0;
            f.pendingUploadBytes = 0;
            f.pendingUploadCommands = 0;
        }
    }
}
