package com.gtnewhorizons.angelica.sdlgpu.resource;

import java.util.function.LongConsumer;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.Hashing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.sdl.SDL_GPUBufferCreateInfo;
import org.lwjgl.sdl.SDL_GPUSamplerCreateInfo;
import org.lwjgl.sdl.SDL_GPUTextureCreateInfo;
import org.lwjgl.sdl.SDL_GPUTransferBufferCreateInfo;
import org.lwjgl.sdl.SDL_GPUTransferBufferLocation;
import org.lwjgl.sdl.SDL_GPUBufferRegion;
import org.lwjgl.sdl.SDL_GPUTextureRegion;
import org.lwjgl.sdl.SDL_GPUTextureTransferInfo;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUTextureLocation;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.EBOSplitScanner;
import com.gtnewhorizons.angelica.sdlgpu.sampler.SamplerCache;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * GPU resource lifecycle: buffers, textures, samplers, FBO state, transfer pool.
 */
public final class ResourceManager {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final String D3D12_CLEAR_DEPTH_PROP   = SDL_PROP_GPU_TEXTURE_CREATE_D3D12_CLEAR_DEPTH_FLOAT;
    private static final String D3D12_CLEAR_STENCIL_PROP = SDL_PROP_GPU_TEXTURE_CREATE_D3D12_CLEAR_STENCIL_NUMBER;

    private final Device device;
    private final FrameManager frameManager;

    private final Int2LongOpenHashMap textureHandles = new Int2LongOpenHashMap();
    private final Int2ObjectOpenHashMap<TextureMeta> textureMetas = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet fboAttachedGlIds = new IntOpenHashSet();
    private final Int2LongOpenHashMap bufferHandles = new Int2LongOpenHashMap();
    private final Int2LongOpenHashMap bufferSizes = new Int2LongOpenHashMap();
    private final Int2IntOpenHashMap bufferUsages = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap bufferGlUsages = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap bufferStorageFlags = new Int2IntOpenHashMap();
    private final IntOpenHashSet undefinedContentBuffers = new IntOpenHashSet();
    private final Int2LongOpenHashMap samplerHandles = new Int2LongOpenHashMap();

    private final LongOpenHashSet liveBufferHandles = new LongOpenHashSet();
    private final LongOpenHashSet liveTextureHandles = new LongOpenHashSet();

    private final AtomicInteger nextTextureId = new AtomicInteger(1);
    private final AtomicInteger nextBufferId = new AtomicInteger(1);
    private final AtomicInteger nextSamplerId = new AtomicInteger(1);
    private final AtomicInteger nextFboId = new AtomicInteger(1);

    private final Int2ObjectOpenHashMap<ContextState.VAOState> vaoStates = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<PersistentMapping> persistentMappings = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ByteBuffer> uboShadows = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ByteBuffer> eboShadows = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet eboShadowWanted = new IntOpenHashSet();
    private static final int MAX_ARRAY_SHADOW_BUFFER_BYTES = 8 << 20;
    private final Int2ObjectOpenHashMap<ByteBuffer> arrayShadows = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet arrayShadowWanted = new IntOpenHashSet();
    private final ArrayDeque<ByteBuffer> pendingFreeShadows = new ArrayDeque<>();
    private final Int2LongOpenHashMap eboShadowVersions = new Int2LongOpenHashMap();
    private long nextEboShadowVersion = 1;

    private record EboSplitKey(int glId, long version, int indexType, int sentinel) {}
    private final ConcurrentHashMap<EboSplitKey, EBOSplitScanner.EboSplit[]> splitCache = new ConcurrentHashMap<>();
    private final Int2ObjectOpenHashMap<TextureSamplerState> texSamplerStates = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<TextureSamplerState> samplerObjectStates = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<FboState> fboStates = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ByteBuffer> pboStagingData = new Int2ObjectOpenHashMap<>();

    private final SamplerCache samplerCache = new SamplerCache();

    private final ReentrantReadWriteLock handleLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock rLock = handleLock.readLock();
    private final ReentrantReadWriteLock.WriteLock wLock = handleLock.writeLock();

    public SamplerCache samplerCache() { return samplerCache; }

    private volatile TransferThread transferThread;
    public void setTransferThread(TransferThread t) { this.transferThread = t; }

    public void markFboAttachment(int glId) { fboAttachedGlIds.add(glId); }
    public boolean isFboAttachment(int glId) { return fboAttachedGlIds.contains(glId); }

    private int preferredD24 = SDL_GPU_TEXTUREFORMAT_D24_UNORM;
    private int preferredD24S8 = SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;
    private volatile int swapchainDepthStencilFormat = SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;
    private long swapchainDepthStencil;
    private int swapchainDepthStencilWidth;
    private int swapchainDepthStencilHeight;

    private static final Supplier<ContextState.VAOState> VAO_FACTORY = ContextState.VAOState::new;
    private static final Supplier<TextureSamplerState> TEX_SAMPLER_FACTORY = TextureSamplerState::new;

    public ResourceManager(Device device, FrameManager frameManager) {
        this.device = device;
        this.frameManager = frameManager;
        textureHandles.defaultReturnValue(0);
        bufferHandles.defaultReturnValue(0);
        samplerHandles.defaultReturnValue(0);
        bufferGlUsages.defaultReturnValue(GL15.GL_STATIC_DRAW);
        bufferStorageFlags.defaultReturnValue(BufferParams.MUTABLE_STORE);
    }

    public void cachePreferredDepthFormats() {
        final long dev = device.getDevice();
        final int texType = SDL_GPU_TEXTURETYPE_2D;
        final int depthUsage = SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET;

        if (!SDL_GPUTextureSupportsFormat(dev, SDL_GPU_TEXTUREFORMAT_D24_UNORM, texType, depthUsage)) {
            preferredD24 = SDL_GPU_TEXTUREFORMAT_D32_FLOAT;
            LOG.info("D24_UNORM not supported for depth+sampler, using D32_FLOAT");
        }
        if (!SDL_GPUTextureSupportsFormat(dev, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT, texType, depthUsage)) {
            preferredD24S8 = SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT;
            LOG.info("D24_UNORM_S8_UINT not supported for depth+sampler, using D32_FLOAT_S8_UINT");
        }

        swapchainDepthStencilFormat = preferredD24S8;
        if (!SDL_GPUTextureSupportsFormat(dev, swapchainDepthStencilFormat, texType, SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET)) {
            final int alt = swapchainDepthStencilFormat == SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT ? SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT : SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;
            if (SDL_GPUTextureSupportsFormat(dev, alt, texType, SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET)) {
                swapchainDepthStencilFormat = alt;
            } else {
                swapchainDepthStencilFormat = 0;
                LOG.warn("No depth+stencil format usable as a render target; default framebuffer will have no depth or stencil");
            }
        }
    }

    public int getSwapchainDepthStencilFormat() {
        return swapchainDepthStencilFormat;
    }

    public int genTexture() {
        return nextTextureId.getAndIncrement();
    }

    public long createTexture(int glId, int glTarget, int glFormat, int width, int height, int depth, int levels) {
        final int sdlFormat = mapTextureFormat(glFormat);
        return createTextureCore(glId, glTarget, sdlFormat, glFormat, width, height, depth, levels, defaultUsage(sdlFormat));
    }

    public long createTexture(int glId, int glTarget, int glFormat, int width, int height, int depth, int levels, int usage) {
        return createTextureCore(glId, glTarget, mapTextureFormat(glFormat), glFormat, width, height, depth, levels, usage);
    }

    public long createTextureWithSdlFormat(int glId, int glTarget, int sdlFormat, int glFormat, int width, int height, int depth, int levels) {
        return createTextureCore(glId, glTarget, sdlFormat, glFormat, width, height, depth, levels, defaultUsage(sdlFormat));
    }

    public long createTextureWithSdlFormat(int glId, int glTarget, int sdlFormat, int glFormat, int width, int height, int depth, int levels, int usage) {
        return createTextureCore(glId, glTarget, sdlFormat, glFormat, width, height, depth, levels, usage);
    }

    private static int defaultUsage(int sdlFormat) {
        if (isDepthFormat(sdlFormat)) {
            return SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET | SDL_GPU_TEXTUREUSAGE_SAMPLER;
        }
        if (isIntegerFormat(sdlFormat)) {
            return SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ
                 | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE
                 | SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ;
        }
        return SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET;
    }

    public String describeGpuState() {
        final FrameManager.FrameState f = frameManager.busiestFrame();
        final StringBuilder sb = new StringBuilder(256);
        sb.append("frame=").append(f.frameNumber)
          .append(" submitsThisFrame=").append(f.submitsThisFrame)
          .append(" mipGensThisFrame=").append(f.mipGensThisFrame)
          .append(" pendingUpload=").append(f.pendingUploadBytes >> 10).append("KiB/").append(f.pendingUploadCommands).append("cmds")
          .append('\n');
        sb.append("arena capacity=").append(f.arena.capacity() >> 20).append("MiB used=").append(f.arena.usedBytes() >> 10)
          .append("KiB copies=").append(f.arena.copyCount())
          .append(" overflowFlushes=").append(f.arenaOverflowFlushesThisFrame);
        return sb.toString();
    }

    private long createTextureCore(int glId, int glTarget, int sdlFormat, int glFormat, int width, int height, int depth, int levels, int usage) {
        usage = reconcileUsageAndWarn(glId, usage);
        final int sdlType = mapTextureType(glTarget);
        if (!SDL_GPUTextureSupportsFormat(device.getDevice(), sdlFormat, sdlType, usage)) {
            final int fallback = findSupportedDepthFallback(sdlFormat, sdlType, usage);
            if (fallback != sdlFormat) {
                LOG.info("Format 0x{} unsupported for usage 0x{}, falling back to 0x{}", Integer.toHexString(sdlFormat), Integer.toHexString(usage), Integer.toHexString(fallback));
                sdlFormat = fallback;
            } else {
                LOG.error("No supported format found for GL format 0x{} usage 0x{}", Integer.toHexString(glFormat), Integer.toHexString(usage));
            }
        }

        final int dCount = Math.max(1, depth);
        final int lCount = Math.max(1, levels);
        try (var stack = stackPush()) {
            final SDL_GPUTextureCreateInfo ci = SDL_GPUTextureCreateInfo.calloc(stack)
                .type(sdlType).format(sdlFormat).usage(usage)
                .width(width).height(height)
                .layer_count_or_depth(dCount).num_levels(lCount);
            final int props;
            if ((usage & SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET) != 0) {
                props = SDLProperties.SDL_CreateProperties();
                SDLProperties.SDL_SetFloatProperty(props, D3D12_CLEAR_DEPTH_PROP, 1.0f);
                SDLProperties.SDL_SetNumberProperty(props, D3D12_CLEAR_STENCIL_PROP, 0L);
                ci.props(props);
            } else {
                props = 0;
            }
            final long handle = SDL_CreateGPUTexture(device.getDevice(), ci);
            if (props != 0) SDLProperties.SDL_DestroyProperties(props);
            if (handle == 0) {
                device.reportGpuFailure("createTexture " + width + "x" + height + " fmt=0x" + Integer.toHexString(glFormat) + " sdlFmt=0x" + Integer.toHexString(sdlFormat) + " usage=0x" + Integer.toHexString(usage));
                return 0;
            }
            wLock.lock();
            try {
                textureHandles.put(glId, handle);
                liveTextureHandles.add(handle);
                textureMetas.put(glId, new TextureMeta(glTarget, glFormat, sdlFormat, width, height, dCount, lCount, usage));
            } finally {
                wLock.unlock();
            }
            return handle;
        }
    }

    private int findSupportedDepthFallback(int sdlFormat, int sdlType, int usage) {
        final int[] depthFallbacks = switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_D24_UNORM -> new int[]{ SDL_GPU_TEXTUREFORMAT_D32_FLOAT, SDL_GPU_TEXTUREFORMAT_D16_UNORM };
            case SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT -> new int[]{ SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT };
            case SDL_GPU_TEXTUREFORMAT_D16_UNORM -> new int[]{ SDL_GPU_TEXTUREFORMAT_D32_FLOAT };
            default -> new int[0];
        };
        for (int fallback : depthFallbacks) {
            if (SDL_GPUTextureSupportsFormat(device.getDevice(), fallback, sdlType, usage)) {
                return fallback;
            }
        }
        return sdlFormat;
    }

    public void deleteTexture(int glId) {
        final long handle;
        wLock.lock();
        try {
            handle = textureHandles.remove(glId);
            textureMetas.remove(glId);
            fboAttachedGlIds.remove(glId);
            for (FboState fbo : fboStates.values()) {
                for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
                    if (fbo.colorGlIds[i] == glId) {
                        fbo.colorTextures[i] = 0;
                        fbo.colorGlIds[i] = 0;
                        fbo.colorFormats[i] = 0;
                        fbo.targetsDirty = true;
                    }
                }
                if (fbo.depthGlId == glId) {
                    fbo.depthTexture = 0;
                    fbo.depthGlId = 0;
                    fbo.depthFormat = 0;
                    fbo.targetsDirty = true;
                }
            }
            texSamplerStates.remove(glId);
        } finally {
            wLock.unlock();
        }
        releaseTextureHandleAfterPendingUploads(handle);
    }

    public void releaseTextureHandleForRealloc(int glId) {
        final long handle;
        wLock.lock();
        try {
            handle = textureHandles.remove(glId);
            textureMetas.remove(glId);
        } finally {
            wLock.unlock();
        }
        releaseTextureHandleAfterPendingUploads(handle);
    }

    private void releaseTextureHandleAfterPendingUploads(long handle) {
        if (handle == 0) return;
        if (shouldDeferTextureRelease()) {
            releaseTextureDeferred(handle);
            return;
        }
        releaseTextureHandle(handle);
    }

    boolean shouldDeferTextureRelease() {
        final TransferThread tt = transferThread;
        if (tt != null && tt.getSubmittedSeq() < lastTextureUploadSeq) return true;
        return hasBatchedUploads();
    }

    public void trackBufferHandle(long handle) {
        if (handle == 0) return;
        wLock.lock();
        try { liveBufferHandles.add(handle); } finally { wLock.unlock(); }
    }

    public void trackTextureHandle(long handle) {
        if (handle == 0) return;
        wLock.lock();
        try { liveTextureHandles.add(handle); } finally { wLock.unlock(); }
    }

    private final LongOpenHashSet definedContentTextures = new LongOpenHashSet();

    public boolean isTextureContentDefined(long handle) {
        if (handle == 0) return false;
        if (fastRead()) return definedContentTextures.contains(handle);
        rLock.lock();
        try { return definedContentTextures.contains(handle); } finally { rLock.unlock(); }
    }

    public void markTextureContentDefined(long handle) {
        if (handle == 0) return;
        wLock.lock();
        try { definedContentTextures.add(handle); } finally { wLock.unlock(); }
    }

    void markTextureContentsDefined(LongArrayList handles) {
        if (handles.isEmpty()) return;
        wLock.lock();
        try {
            for (int i = 0, n = handles.size(); i < n; i++) {
                final long handle = handles.getLong(i);
                if (handle != 0) definedContentTextures.add(handle);
            }
        } finally {
            wLock.unlock();
        }
    }

    public void markTextureContentUndefined(long handle) {
        if (handle == 0) return;
        wLock.lock();
        try { definedContentTextures.remove(handle); } finally { wLock.unlock(); }
    }

    public void releaseBufferHandle(long handle) {
        if (handle == 0) return;
        final boolean live;
        wLock.lock();
        try {
            live = liveBufferHandles.remove(handle);
        } finally {
            wLock.unlock();
        }
        if (live) {
            if (Tracy.ENABLED) bufferDeletes.incrementAndGet();
            SDL_ReleaseGPUBuffer(device.getDevice(), handle);
        } else {
            LOG.error("Double release of GPU buffer handle {} - skipped", handle, new Throwable("double-release call site"));
        }
    }

    public void releaseTextureHandle(long handle) {
        if (handle == 0) return;
        dropBatchedUploadsTargeting(handle);
        markTextureContentUndefined(handle);
        final boolean live;
        wLock.lock();
        try {
            live = liveTextureHandles.remove(handle);
        } finally {
            wLock.unlock();
        }
        if (live) {
            SDL_ReleaseGPUTexture(device.getDevice(), handle);
        } else {
            LOG.error("Double release of GPU texture handle {} - skipped", handle, new Throwable("double-release call site"));
        }
    }

    public void refreshTextureReferences(int glId) {
        wLock.lock();
        try {
            final long newHandle = textureHandles.get(glId);
            final TextureMeta meta = textureMetas.get(glId);
            for (FboState fbo : fboStates.values()) {
                for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
                    if (fbo.colorGlIds[i] == glId) {
                        fbo.colorTextures[i] = newHandle;
                        fbo.targetsDirty = true;
                        if (meta != null) {
                            fbo.colorFormats[i] = meta.sdlFormat();
                            if (i == 0) {
                                fbo.width = meta.width();
                                fbo.height = meta.height();
                            }
                        }
                    }
                }
                if (fbo.depthGlId == glId) {
                    fbo.depthTexture = newHandle;
                    fbo.targetsDirty = true;
                    if (meta != null) fbo.depthFormat = meta.sdlFormat();
                }
            }
        } finally {
            wLock.unlock();
        }
    }

    public long getTextureHandle(int glId) {
        if (fastRead()) return textureHandles.get(glId);
        rLock.lock();
        try { return textureHandles.get(glId); } finally { rLock.unlock(); }
    }

    public TextureMeta getTextureMeta(int glId) {
        if (fastRead()) return textureMetas.get(glId);
        rLock.lock();
        try { return textureMetas.get(glId); } finally { rLock.unlock(); }
    }

    static int reconcileUsage(int usage) {
        if ((usage & SDL_GPU_TEXTUREUSAGE_SAMPLER) == 0) return usage;
        final int illegal = usage & (SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_SIMULTANEOUS_READ_WRITE);
        if (illegal == 0) return usage;
        return usage & ~illegal;
    }

    private int reconcileUsageAndWarn(int glId, int usage) {
        final int reconciled = reconcileUsage(usage);
        if (reconciled != usage) warnUsageConflict(glId, usage & ~reconciled, reconciled);
        return reconciled;
    }

    private final IntOpenHashSet loggedUsageConflict = new IntOpenHashSet();

    private void warnUsageConflict(int glId, int illegal, int reconciled) {
        synchronized (loggedUsageConflict) {
            if (!loggedUsageConflict.add(glId)) return;
        }
        LOG.warn("[ResourceManager] texture {} requested SAMPLER together with storage usage 0x{}; SDL cannot resolve a default layout for that combination, dropping to 0x{}. The texture stays sampleable; image-load/store on it may lose coherence.", glId, Integer.toHexString(illegal), Integer.toHexString(reconciled));
    }

    public long ensureTextureUsage(int glId, int requiredUsage) {
        final TextureMeta meta;
        final long oldHandle;
        if (fastRead()) {
            meta = textureMetas.get(glId);
            if (meta == null) return textureHandles.get(glId);
            if ((meta.usage & requiredUsage) == requiredUsage) {
                return textureHandles.get(glId);
            }
            oldHandle = textureHandles.get(glId);
        } else {
            rLock.lock();
            try {
                meta = textureMetas.get(glId);
                if (meta == null) return textureHandles.get(glId);
                if ((meta.usage & requiredUsage) == requiredUsage) {
                    return textureHandles.get(glId);
                }
                oldHandle = textureHandles.get(glId);
            } finally {
                rLock.unlock();
            }
        }

        final int newUsage = reconcileUsageAndWarn(glId, meta.usage | requiredUsage);
        if ((meta.usage & newUsage) == newUsage) return oldHandle;

        final long newHandle = createTexture(glId, meta.glTarget, meta.glFormat, meta.width, meta.height, meta.depth, meta.levels, newUsage);
        if (newHandle == 0) return oldHandle;
        if (oldHandle != 0 && !copyAllMips(oldHandle, newHandle, meta.glTarget, meta.width, meta.height, meta.depth, meta.levels)) {
            LOG.warn("[ResourceManager] texture {} usage promote 0x{} -> 0x{} had no copy pass available; restoring the populated texture and skipping the promote. Image load/store on it will not work this frame.", glId, Integer.toHexString(meta.usage), Integer.toHexString(newUsage));
            releaseTextureDeferred(newHandle);
            restoreTextureHandle(glId, oldHandle, meta);
            return oldHandle;
        }

        frameManager.frame().pendingMipGen.remove(glId);
        releaseTextureDeferred(oldHandle);
        refreshTextureReferences(glId);
        LOG.debug("Recreated texture {} with usage 0x{} -> 0x{} ({}x{})", glId,
            Integer.toHexString(meta.usage), Integer.toHexString(newUsage), meta.width, meta.height);
        return newHandle;
    }

    private boolean copyAllMips(long srcTex, long dstTex, int glTarget, int width, int height, int depth, int levels) {
        final long cp = frameManager.ensureCopyPass();
        if (cp == 0) return false;
        final boolean volume = glTarget == GL12.GL_TEXTURE_3D;
        final int layers = volume ? 1 : Math.max(1, depth);
        try (var stack = stackPush()) {
            final SDL_GPUTextureLocation src = SDL_GPUTextureLocation.calloc(stack);
            final SDL_GPUTextureLocation dst = SDL_GPUTextureLocation.calloc(stack);
            for (int level = 0; level < Math.max(1, levels); level++) {
                final int w = Math.max(1, width >> level);
                final int h = Math.max(1, height >> level);
                final int d = volume ? Math.max(1, depth >> level) : 1;
                for (int layer = 0; layer < layers; layer++) {
                    src.texture(srcTex).mip_level(level).layer(layer).x(0).y(0).z(0);
                    dst.texture(dstTex).mip_level(level).layer(layer).x(0).y(0).z(0);
                    SDL_CopyGPUTextureToTexture(cp, src, dst, w, h, d, false);
                }
            }
        }
        return true;
    }

    private void restoreTextureHandle(int glId, long handle, TextureMeta meta) {
        wLock.lock();
        try {
            textureHandles.put(glId, handle);
            textureMetas.put(glId, meta);
        } finally {
            wLock.unlock();
        }
    }

    private static final long[] GPU_BUFFER_BUCKET_SIZES = {
        4096, 16384, 65536, 262144, 1048576, 4194304, 16777216
    };

    private static final class GpuBucket {
        public final LongArrayList handles = new LongArrayList();
        public final LongArrayList frames = new LongArrayList();
    }

    private final Long2ObjectOpenHashMap<GpuBucket> gpuBufferPool = new Long2ObjectOpenHashMap<>();
    private long gpuPoolCurrentFrame;

    private static int findGpuBucketIndex(long size) {
        for (int i = 0; i < GPU_BUFFER_BUCKET_SIZES.length; i++) {
            if (size <= GPU_BUFFER_BUCKET_SIZES[i]) return i;
        }
        return -1;
    }

    private long acquirePooledBuffer(int usage, long size) {
        final int bucket = findGpuBucketIndex(size);
        if (bucket < 0) return 0;
        final GpuBucket b = gpuBufferPool.get(Hashing.packHiLo(usage, bucket));
        if (b == null || b.handles.isEmpty()) return 0;
        for (int i = 0, n = b.handles.size(); i < n; i++) {
            if (gpuPoolCurrentFrame - b.frames.getLong(i) >= Device.MAX_FRAMES_IN_FLIGHT) {
                final long handle = b.handles.getLong(i);
                final int last = n - 1;
                if (i != last) {
                    b.handles.set(i, b.handles.getLong(last));
                    b.frames.set(i, b.frames.getLong(last));
                }
                b.handles.removeLong(last);
                b.frames.removeLong(last);
                return handle;
            }
        }
        return 0;
    }

    private void returnBufferToPool(long handle, int usage, long size) {
        final int bucket = findGpuBucketIndex(size);
        if (bucket < 0) {
            deferredBufferReleases.add(handle);
            return;
        }
        final long key = Hashing.packHiLo(usage, bucket);
        GpuBucket b = gpuBufferPool.get(key);
        if (b == null) {
            b = new GpuBucket();
            gpuBufferPool.put(key, b);
        }
        b.handles.add(handle);
        b.frames.add(gpuPoolCurrentFrame);
    }

    public void recycleGpuBufferPool(long currentFrame) {
        gpuPoolCurrentFrame = currentFrame;
        recycleBatchSegments(currentFrame);
    }

    private void drainGpuBufferPool() {
        for (GpuBucket b : gpuBufferPool.values()) {
            for (int i = 0, n = b.handles.size(); i < n; i++) {
                releaseBufferHandle(b.handles.getLong(i));
            }
            b.handles.clear();
            b.frames.clear();
        }
        gpuBufferPool.clear();
    }

    public int genBuffer() {
        return nextBufferId.getAndIncrement();
    }

    private Runnable bufferLivenessListener;

    public void setBufferLivenessListener(Runnable listener) {
        this.bufferLivenessListener = listener;
    }

    private void notifyBufferLiveness() {
        final Runnable l = bufferLivenessListener;
        if (l != null) l.run();
    }

    public long createBuffer(int glId, int usage, long size) {
        wLock.lock();
        try {
            final long pooled = acquirePooledBuffer(usage, size);
            if (pooled != 0) {
                if (Tracy.ENABLED) bufferPoolHits.incrementAndGet();
                bufferHandles.put(glId, pooled);
                bufferSizes.put(glId, size);
                bufferUsages.put(glId, usage);
                undefinedContentBuffers.add(glId);
                notifyBufferLiveness();
                return pooled;
            }
            final int bucket = findGpuBucketIndex(size);
            final long allocSize = bucket >= 0 ? GPU_BUFFER_BUCKET_SIZES[bucket] : size;
            try (var stack = stackPush()) {
                final SDL_GPUBufferCreateInfo ci = SDL_GPUBufferCreateInfo.calloc(stack)
                    .usage(usage)
                    .size((int) allocSize);

                final long handle = SDL_CreateGPUBuffer(device.getDevice(), ci);
                if (handle == 0) {
                    LOG.error("Failed to create buffer size={} usage={}: {}", allocSize, usage, SDLError.SDL_GetError());
                    return 0;
                }
                if (Tracy.ENABLED) bufferCreates.incrementAndGet();
                bufferHandles.put(glId, handle);
                liveBufferHandles.add(handle);
                bufferSizes.put(glId, size);
                bufferUsages.put(glId, usage);
                undefinedContentBuffers.add(glId);
                notifyBufferLiveness();
                return handle;
            }
        } finally {
            wLock.unlock();
        }
    }

    private final LongArrayList deferredBufferReleases = new LongArrayList();
    private final LongArrayList deferredTextureReleases = new LongArrayList();
    private volatile long lastTextureUploadSeq;

    public void deleteBuffer(int glId) {
        final ByteBuffer droppedPboStaging;
        final PersistentMapping droppedPm;
        wLock.lock();
        try {
            final long handle = bufferHandles.remove(glId);
            final long size = bufferSizes.remove(glId);
            final int usage = bufferUsages.remove(glId);
            bufferGlUsages.remove(glId);
            bufferStorageFlags.remove(glId);
            undefinedContentBuffers.remove(glId);
            droppedPm = persistentMappings.remove(glId);
            if (droppedPm != null) mappingsVersion++;
            droppedPboStaging = pboStagingData.remove(glId);
            deleteUboShadow(glId);
            deleteEboShadow(glId);
            deleteArrayShadow(glId);
            if (handle != 0) {
                returnBufferToPool(handle, usage, size);
                notifyBufferLiveness();
            }
        } finally {
            wLock.unlock();
        }
        if (droppedPboStaging != null) {
            memFree(droppedPboStaging);
        }
        untrackPersistentDirty(droppedPm);
        releasePersistentStaging(droppedPm);
    }

    public void flushDeferredReleases() {
        wLock.lock();
        try {
            for (int i = 0, n = deferredBufferReleases.size(); i < n; i++) {
                releaseBufferHandle(deferredBufferReleases.getLong(i));
            }
            deferredBufferReleases.clear();
            for (int i = 0, n = deferredTextureReleases.size(); i < n; i++) {
                releaseTextureHandle(deferredTextureReleases.getLong(i));
            }
            deferredTextureReleases.clear();
        } finally {
            wLock.unlock();
        }
    }

    public void releaseBufferDeferred(long handle) {
        if (handle == 0) return;
        wLock.lock();
        try {
            deferredBufferReleases.add(handle);
        } finally {
            wLock.unlock();
        }
    }

    public void releaseTextureDeferred(long handle) {
        if (handle == 0) return;
        wLock.lock();
        try {
            deferredTextureReleases.add(handle);
        } finally {
            wLock.unlock();
        }
    }

    private static volatile boolean singleThreadedReads;
    private static volatile boolean workersActive;
    private final AtomicInteger lockedReads = new AtomicInteger();
    private final AtomicInteger bufferCreates = new AtomicInteger();
    private final AtomicInteger bufferDeletes = new AtomicInteger();
    private final AtomicInteger bufferPoolHits = new AtomicInteger();

    public int takeBufferCreateCount() { return bufferCreates.getAndSet(0); }

    public int takeBufferDeleteCount() { return bufferDeletes.getAndSet(0); }

    public int takeBufferPoolHitCount() { return bufferPoolHits.getAndSet(0); }

    public int takeLockedReadCount() { return lockedReads.getAndSet(0); }

    public static void setSingleThreadedReads(boolean value) { singleThreadedReads = value; }
    public static void setWorkersActive(boolean value) { workersActive = value; }

    private boolean fastRead() {
        if (singleThreadedReads && !workersActive) return true;
        if (Tracy.ENABLED) lockedReads.incrementAndGet();
        return false;
    }

    public long getBufferHandle(int glId) {
        if (fastRead()) return bufferHandles.get(glId);
        rLock.lock();
        try { return bufferHandles.get(glId); } finally { rLock.unlock(); }
    }

    public long getBufferSize(int glId) {
        if (fastRead()) return bufferSizes.get(glId);
        rLock.lock();
        try { return bufferSizes.get(glId); } finally { rLock.unlock(); }
    }

    public void recordBufferGlParams(int glId, int glUsage, int storageFlags) {
        wLock.lock();
        try {
            bufferGlUsages.put(glId, glUsage);
            bufferStorageFlags.put(glId, storageFlags);
        } finally {
            wLock.unlock();
        }
    }

    public int getBufferGlUsage(int glId) {
        if (fastRead()) return bufferGlUsages.get(glId);
        rLock.lock();
        try { return bufferGlUsages.get(glId); } finally { rLock.unlock(); }
    }

    public int getBufferStorageFlags(int glId) {
        if (fastRead()) return bufferStorageFlags.get(glId);
        rLock.lock();
        try { return bufferStorageFlags.get(glId); } finally { rLock.unlock(); }
    }

    public void markBufferContentsDefined(int glId) {
        if (glId == 0 || !hasUndefinedContents(glId)) return;
        wLock.lock();
        try { undefinedContentBuffers.remove(glId); } finally { wLock.unlock(); }
    }

    public boolean hasUndefinedContents(int glId) {
        if (fastRead()) return undefinedContentBuffers.contains(glId);
        rLock.lock();
        try { return undefinedContentBuffers.contains(glId); } finally { rLock.unlock(); }
    }

    public int genSampler() {
        return nextSamplerId.getAndIncrement();
    }

    public long createSampler(int glId, SDL_GPUSamplerCreateInfo ci) {
        final long handle = SDL_CreateGPUSampler(device.getDevice(), ci);
        if (handle == 0) {
            LOG.error("Failed to create sampler: {}", SDLError.SDL_GetError());
            return 0;
        }
        wLock.lock();
        try {
            samplerHandles.put(glId, handle);
        } finally {
            wLock.unlock();
        }
        return handle;
    }

    public void deleteSampler(int glId) {
        final long handle;
        wLock.lock();
        try {
            handle = samplerHandles.remove(glId);
        } finally {
            wLock.unlock();
        }
        if (handle != 0) {
            SDL_ReleaseGPUSampler(device.getDevice(), handle);
        }
    }


    private <V> V getOrCreateLocked(Int2ObjectOpenHashMap<V> map, int key, Supplier<V> factory) {
        rLock.lock();
        try {
            final V s = map.get(key);
            if (s != null) return s;
        } finally { rLock.unlock(); }
        wLock.lock();
        try {
            V s = map.get(key);
            if (s == null) {
                s = factory.get();
                map.put(key, s);
            }
            return s;
        } finally { wLock.unlock(); }
    }

    public ContextState.VAOState getVao(int glId) {
        rLock.lock();
        try { return vaoStates.get(glId); } finally { rLock.unlock(); }
    }
    public ContextState.VAOState getOrCreateVao(int glId) {
        return getOrCreateLocked(vaoStates, glId, VAO_FACTORY);
    }
    public void putVao(int glId, ContextState.VAOState s) {
        wLock.lock();
        try { vaoStates.put(glId, s); } finally { wLock.unlock(); }
    }
    public void deleteVao(int glId) {
        wLock.lock();
        try { vaoStates.remove(glId); } finally { wLock.unlock(); }
    }

    public void putPersistentMapping(int bufferGlId, PersistentMapping m) {
        wLock.lock();
        try { persistentMappings.put(bufferGlId, m); mappingsVersion++; } finally { wLock.unlock(); }
    }
    public PersistentMapping getPersistentMapping(int bufferGlId) {
        if (fastRead()) return persistentMappings.get(bufferGlId);
        rLock.lock();
        try { return persistentMappings.get(bufferGlId); } finally { rLock.unlock(); }
    }
    public PersistentMapping removePersistentMapping(int bufferGlId) {
        final PersistentMapping dropped;
        wLock.lock();
        try { dropped = persistentMappings.remove(bufferGlId); mappingsVersion++; } finally { wLock.unlock(); }
        untrackPersistentDirty(dropped);
        return dropped;
    }
    public PersistentMapping swapPersistentMapping(int bufferGlId, PersistentMapping fresh) {
        final PersistentMapping prior;
        wLock.lock();
        try { prior = persistentMappings.put(bufferGlId, fresh); mappingsVersion++; } finally { wLock.unlock(); }
        untrackPersistentDirty(prior);
        return prior;
    }

    private volatile int mappingsVersion;

    private final AtomicInteger persistentDirtyCount = new AtomicInteger();

    public boolean hasDirtyPersistentRegions() { return persistentDirtyCount.get() != 0; }
    public void trackPersistentDirty() { persistentDirtyCount.incrementAndGet(); }
    public void clearPersistentDirty() { persistentDirtyCount.decrementAndGet(); }

    private void untrackPersistentDirty(PersistentMapping pm) {
        if (pm != null && !PersistentMapping.isClean(pm.claimDirty())) persistentDirtyCount.decrementAndGet();
    }

    public void releasePersistentStaging(PersistentMapping pm) {
        if (pm == null || pm.staging == null) return;
        final TransferThread tt = transferThread;
        final long seq = pm.lastEnqueuedSeq;
        if (tt != null && seq > tt.getSubmittedSeq()) tt.freeAfterSeq(pm.staging, seq);
        else memFree(pm.staging);
    }

    public boolean uploadFromPersistentMapping(int srcGlId, long readOffset, long size, long dstHandle, long writeOffset, int dstGlId, long copyPass) {
        rLock.lock();
        try {
            final PersistentMapping srcPm = persistentMappings.get(srcGlId);
            if (srcPm == null) return false;
            final ByteBuffer slice = srcPm.staging.duplicate();
            slice.position((int) readOffset).limit((int) (readOffset + size));
            uploadToBuffer(copyPass, slice, dstHandle, writeOffset, false);
            final PersistentMapping dstPm = persistentMappings.get(dstGlId);
            if (dstPm != null) {
                PersistentBufferSync.mirrorPersistentCopy(srcPm.staging, readOffset, dstPm.staging, writeOffset, size);
            }
            return true;
        } finally { rLock.unlock(); }
    }

    public boolean enqueuePersistentCopyDeferred(int srcGlId, int dstGlId, long seq,
            BiConsumer<PersistentMapping, PersistentMapping> inLock) {
        rLock.lock();
        try {
            final PersistentMapping srcPm = persistentMappings.get(srcGlId);
            if (srcPm == null) return false;
            final PersistentMapping dstPm = persistentMappings.get(dstGlId);
            srcPm.lastEnqueuedSeq = seq;
            inLock.accept(srcPm, dstPm);
            return true;
        } finally { rLock.unlock(); }
    }

    public int getMappingsVersion() { return mappingsVersion; }

    public void snapshotPersistentMappingsInto(IntArrayList outKeys, ArrayList<PersistentMapping> outVals) {
        outKeys.clear();
        outVals.clear();
        rLock.lock();
        try {
            final var it = persistentMappings.int2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                final var entry = it.next();
                outKeys.add(entry.getIntKey());
                outVals.add(entry.getValue());
            }
        } finally { rLock.unlock(); }
    }

    public void recordBufferSizeOnly(int glId, long size) {
        wLock.lock();
        try {
            bufferSizes.put(glId, size);
        } finally {
            wLock.unlock();
        }
    }

    public ByteBuffer getOrAllocUboShadow(int bufferGlId, int size) {
        wLock.lock();
        try {
            ByteBuffer existing = uboShadows.get(bufferGlId);
            if (existing != null && existing.capacity() >= size) {
                existing.clear();
                return existing;
            }
            if (existing != null) pendingFreeShadows.add(existing);
            final ByteBuffer fresh = memAlloc(size).order(ByteOrder.nativeOrder());
            uboShadows.put(bufferGlId, fresh);
            return fresh;
        } finally { wLock.unlock(); }
    }

    public ByteBuffer getUboShadow(int bufferGlId) {
        rLock.lock();
        try { return uboShadows.get(bufferGlId); } finally { rLock.unlock(); }
    }

    private void deleteUboShadow(int bufferGlId) {
        final ByteBuffer existing = uboShadows.remove(bufferGlId);
        if (existing != null) memFree(existing);
    }

    public ByteBuffer getOrAllocEboShadow(int bufferGlId, int size) {
        wLock.lock();
        try {
            ByteBuffer existing = eboShadows.get(bufferGlId);
            if (existing != null && existing.capacity() >= size) {
                existing.clear();
                return existing;
            }
            if (existing != null) pendingFreeShadows.add(existing);
            final ByteBuffer fresh = memAlloc(size).order(ByteOrder.nativeOrder());
            eboShadows.put(bufferGlId, fresh);
            return fresh;
        } finally { wLock.unlock(); }
    }

    public void drainPendingFreeShadows() {
        wLock.lock();
        try {
            ByteBuffer b;
            while ((b = pendingFreeShadows.pollFirst()) != null) memFree(b);
        } finally { wLock.unlock(); }
    }

    public ByteBuffer getEboShadow(int bufferGlId) {
        rLock.lock();
        try { return eboShadows.get(bufferGlId); } finally { rLock.unlock(); }
    }

    public boolean hasEboShadow(int bufferGlId) {
        rLock.lock();
        try { return eboShadows.containsKey(bufferGlId); } finally { rLock.unlock(); }
    }

    public boolean isEboShadowWanted(int glId) {
        rLock.lock();
        try { return eboShadowWanted.contains(glId); } finally { rLock.unlock(); }
    }

    public void markEboShadowWanted(int glId) {
        wLock.lock();
        try { eboShadowWanted.add(glId); } finally { wLock.unlock(); }
    }

    public void dropEboShadow(int bufferGlId) {
        wLock.lock();
        try {
            final ByteBuffer existing = eboShadows.remove(bufferGlId);
            if (existing != null) pendingFreeShadows.add(existing);
            eboShadowVersions.remove(bufferGlId);
        } finally { wLock.unlock(); }
        invalidateSplitCacheFor(bufferGlId);
    }

    public long bumpEboShadowVersion(int bufferGlId) {
        wLock.lock();
        try {
            final long v = ++nextEboShadowVersion;
            eboShadowVersions.put(bufferGlId, v);
            return v;
        } finally { wLock.unlock(); }
    }

    public long getEboShadowVersion(int bufferGlId) {
        rLock.lock();
        try { return eboShadowVersions.get(bufferGlId); } finally { rLock.unlock(); }
    }

    private void deleteEboShadow(int bufferGlId) {
        final ByteBuffer existing = eboShadows.remove(bufferGlId);
        if (existing != null) memFree(existing);
        eboShadowVersions.remove(bufferGlId);
        eboShadowWanted.remove(bufferGlId);
    }

    public boolean canShadowArrayBuffer(int size) {
        return size > 0 && size <= MAX_ARRAY_SHADOW_BUFFER_BYTES;
    }

    public ByteBuffer getArrayShadow(int glId) {
        rLock.lock();
        try { return arrayShadows.get(glId); } finally { rLock.unlock(); }
    }

    public boolean hasArrayShadow(int glId) {
        rLock.lock();
        try { return arrayShadows.containsKey(glId); } finally { rLock.unlock(); }
    }

    public boolean isArrayShadowWanted(int glId) {
        rLock.lock();
        try { return arrayShadowWanted.contains(glId); } finally { rLock.unlock(); }
    }

    public void markArrayShadowWanted(int glId) {
        wLock.lock();
        try { arrayShadowWanted.add(glId); } finally { wLock.unlock(); }
    }

    public ByteBuffer getOrAllocArrayShadow(int glId, int size) {
        wLock.lock();
        try {
            final ByteBuffer existing = arrayShadows.get(glId);
            if (existing != null && existing.capacity() >= size) {
                existing.clear();
                return existing;
            }
            if (existing != null) pendingFreeShadows.add(existing);
            final ByteBuffer fresh = memAlloc(size).order(ByteOrder.nativeOrder());
            arrayShadows.put(glId, fresh);
            return fresh;
        } finally { wLock.unlock(); }
    }

    public void mirrorArrayShadowFull(int glId, ByteBuffer data) {
        final int len = data.remaining();
        final ByteBuffer shadow = getOrAllocArrayShadow(glId, len);
        copyShadowRegion(data, data.position(), shadow, 0, len);
        shadow.position(0).limit(len);
    }

    public void mirrorArrayShadowRegion(int glId, ByteBuffer data, int dstOffset) {
        wLock.lock();
        try {
            final ByteBuffer shadow = arrayShadows.get(glId);
            if (shadow == null) return;
            final int len = data.remaining();
            if (dstOffset < 0 || dstOffset + len > shadow.capacity()) {
                removeArrayShadowLocked(glId);
                return;
            }
            copyShadowRegion(data, data.position(), shadow, dstOffset, len);
        } finally { wLock.unlock(); }
    }

    public void dropArrayShadow(int glId) {
        wLock.lock();
        try { removeArrayShadowLocked(glId); } finally { wLock.unlock(); }
    }

    private void removeArrayShadowLocked(int glId) {
        final ByteBuffer existing = arrayShadows.remove(glId);
        if (existing != null) pendingFreeShadows.add(existing);
    }

    private void deleteArrayShadow(int glId) {
        final ByteBuffer existing = arrayShadows.remove(glId);
        if (existing != null) memFree(existing);
        arrayShadowWanted.remove(glId);
    }

    private static void copyShadowRegion(ByteBuffer src, int srcOff, ByteBuffer dst, int dstOff, int len) {
        if (src.isDirect() && dst.isDirect()) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(src) + srcOff, MemoryUtil.memAddress(dst) + dstOff, len);
            return;
        }
        final ByteBuffer s = src.duplicate();
        s.position(srcOff).limit(srcOff + len);
        final ByteBuffer d = dst.duplicate();
        d.position(dstOff);
        d.put(s);
    }

    public EBOSplitScanner.EboSplit[] getOrScanSplits(int glId, int indexType, int sentinel) {
        if (!hasEboShadow(glId)) return null;
        final long version = getEboShadowVersion(glId);
        final EboSplitKey key = new EboSplitKey(glId, version, indexType, sentinel);
        return splitCache.computeIfAbsent(key, k -> {
            final ByteBuffer shadow = getEboShadow(glId);
            if (shadow == null) return null;
            final int indexBytes = (indexType == GL11.GL_UNSIGNED_SHORT) ? 2 : 4;
            return EBOSplitScanner.scanIndices(shadow, shadow.capacity() / indexBytes, indexType, sentinel);
        });
    }

    public void invalidateSplitCacheFor(int glId) {
        if (splitCache.isEmpty()) return;
        splitCache.entrySet().removeIf(e -> e.getKey().glId == glId);
    }

    public void clearSplitCache() {
        splitCache.clear();
    }

    public TextureSamplerState getOrCreateTexSamplerState(int textureGlId) {
        return getOrCreateLocked(texSamplerStates, textureGlId, TEX_SAMPLER_FACTORY);
    }
    public TextureSamplerState getTexSamplerState(int textureGlId) {
        if (fastRead()) return texSamplerStates.get(textureGlId);
        rLock.lock();
        try { return texSamplerStates.get(textureGlId); } finally { rLock.unlock(); }
    }

    public TextureSamplerState getOrCreateSamplerObject(int samplerGlId) {
        return getOrCreateLocked(samplerObjectStates, samplerGlId, TEX_SAMPLER_FACTORY);
    }
    public TextureSamplerState getSamplerObject(int samplerGlId) {
        if (fastRead()) return samplerObjectStates.get(samplerGlId);
        rLock.lock();
        try { return samplerObjectStates.get(samplerGlId); } finally { rLock.unlock(); }
    }
    public long getSamplerHandle(int samplerGlId) {
        if (fastRead()) return samplerHandles.get(samplerGlId);
        rLock.lock();
        try { return samplerHandles.get(samplerGlId); } finally { rLock.unlock(); }
    }
    public void deleteSamplerObject(int samplerGlId) {
        wLock.lock();
        try {
            samplerObjectStates.remove(samplerGlId);
        } finally { wLock.unlock(); }
    }

    public void shutdownTexSamplerStates() {
        wLock.lock();
        try {
            texSamplerStates.clear();
            samplerObjectStates.clear();
        } finally { wLock.unlock(); }
    }

    public int genFboId() { return nextFboId.getAndIncrement(); }
    public FboState createFbo(int fboGlId) {
        wLock.lock();
        try {
            final FboState s = new FboState();
            fboStates.put(fboGlId, s);
            return s;
        } finally { wLock.unlock(); }
    }
    public FboState getFbo(int fboGlId) {
        if (fastRead()) return fboStates.get(fboGlId);
        rLock.lock();
        try { return fboStates.get(fboGlId); } finally { rLock.unlock(); }
    }
    public void deleteFbo(int fboGlId) {
        wLock.lock();
        try {
            final FboState removed = fboStates.remove(fboGlId);
            if (removed != null) removed.free();
        } finally { wLock.unlock(); }
    }
    public void forEachFbo(Consumer<FboState> fn) {
        rLock.lock();
        try {
            for (FboState fbo : fboStates.values()) fn.accept(fbo);
        } finally { rLock.unlock(); }
    }

    public ByteBuffer putPboStaging(int bufferGlId, ByteBuffer buf) {
        wLock.lock();
        try { return pboStagingData.put(bufferGlId, buf); } finally { wLock.unlock(); }
    }
    public ByteBuffer getPboStaging(int bufferGlId) {
        rLock.lock();
        try { return pboStagingData.get(bufferGlId); } finally { rLock.unlock(); }
    }

    public static final int[] XFER_BUCKET_SIZES = { 4096, 16384, 65536, 262144, 1048576, 4194304 };
    private final Object xferPoolLock = new Object();
    private final LongArrayFIFOQueue[] xferFreeBuckets = new LongArrayFIFOQueue[XFER_BUCKET_SIZES.length];
    {
        for (int i = 0; i < XFER_BUCKET_SIZES.length; i++) {
            xferFreeBuckets[i] = new LongArrayFIFOQueue();
        }
    }

    // Guarded by xferPoolLock.
    private final LongOpenHashSet liveTransferBufferHandles = new LongOpenHashSet();

    private static int findXferBucket(long size) {
        for (int i = 0; i < XFER_BUCKET_SIZES.length; i++) {
            if (size <= XFER_BUCKET_SIZES[i]) return i;
        }
        return -1;
    }

    public long getTransferBufferMapSize(long dataSize) {
        final int bucket = findXferBucket(dataSize);
        return (bucket >= 0) ? XFER_BUCKET_SIZES[bucket] : dataSize;
    }

    public long acquireTransferBuffer(long size) {
        final int bucket = findXferBucket(size);
        long pooled = 0;
        if (bucket >= 0) {
            synchronized (xferPoolLock) {
                final LongArrayFIFOQueue q = xferFreeBuckets[bucket];
                if (!q.isEmpty()) pooled = q.dequeueLong();
            }
            if (pooled != 0) return pooled;
        }

        final long allocSize = (bucket >= 0) ? XFER_BUCKET_SIZES[bucket] : size;
        return createTransferBuffer(allocSize);
    }

    public void returnTransferBuffer(long handle, long size) {
        final int bucket = findXferBucket(size);
        if (bucket < 0) {
            releaseTransferBuffer(handle);
            return;
        }
        synchronized (xferPoolLock) {
            xferFreeBuckets[bucket].enqueue(handle);
        }
    }

    public long acquireTransferBufferThreadSafe(long size) {
        return acquireTransferBuffer(size);
    }

    public void returnTransferBufferThreadSafe(long handle, long size) {
        returnTransferBuffer(handle, size);
    }

    public void drainTransferBufferPool() {
        synchronized (xferPoolLock) {
            for (int b = 0; b < XFER_BUCKET_SIZES.length; b++) {
                final LongArrayFIFOQueue q = xferFreeBuckets[b];
                while (!q.isEmpty()) {
                    releaseTransferBufferHandle(q.dequeueLong());
                }
            }
            while (!batchSegmentFree.isEmpty()) {
                releaseTransferBufferHandle(batchSegmentFree.dequeueLong());
            }
            for (int i = 0, n = batchSegmentPending.size(); i < n; i += 2) {
                releaseTransferBufferHandle(batchSegmentPending.getLong(i));
            }
            batchSegmentPending.clear();
        }
    }

    private final LongArrayFIFOQueue batchSegmentFree = new LongArrayFIFOQueue();
    private final LongArrayList batchSegmentPending = new LongArrayList();

    private long acquireBatchSegment() {
        synchronized (xferPoolLock) {
            if (!batchSegmentFree.isEmpty()) return batchSegmentFree.dequeueLong();
        }
        return createTransferBuffer(BATCH_SEGMENT_CAPACITY);
    }

    private void releaseBatchSegmentDeferred(long handle) {
        if (handle == 0) return;
        synchronized (xferPoolLock) {
            batchSegmentPending.add(handle);
            batchSegmentPending.add(gpuPoolCurrentFrame);
        }
    }

    private void recycleBatchSegments(long currentFrame) {
        synchronized (xferPoolLock) {
            final int n = batchSegmentPending.size();
            int w = 0;
            for (int i = 0; i < n; i += 2) {
                final long handle = batchSegmentPending.getLong(i);
                final long stamp = batchSegmentPending.getLong(i + 1);
                if (currentFrame - stamp >= Device.MAX_FRAMES_IN_FLIGHT) {
                    batchSegmentFree.enqueue(handle);
                } else {
                    batchSegmentPending.set(w, handle);
                    batchSegmentPending.set(w + 1, stamp);
                    w += 2;
                }
            }
            batchSegmentPending.size(w);
        }
    }

    public long createTransferBuffer(long size) {
        try (var stack = stackPush()) {
            final SDL_GPUTransferBufferCreateInfo ci = SDL_GPUTransferBufferCreateInfo.calloc(stack)
                .usage(SDL_GPU_TRANSFERBUFFERUSAGE_UPLOAD)
                .size((int) size);

            final long handle = SDL_CreateGPUTransferBuffer(device.getDevice(), ci);
            if (handle == 0) {
                device.reportGpuFailure("createTransferBuffer size=" + size);
            } else {
                synchronized (xferPoolLock) { liveTransferBufferHandles.add(handle); }
            }
            return handle;
        }
    }

    public void unmapTransferBuffer(long transferBuffer) {
        SDL_UnmapGPUTransferBuffer(device.getDevice(), transferBuffer);
    }

    public void releaseTransferBuffer(long transferBuffer) {
        releaseTransferBufferHandle(transferBuffer);
    }

    public void releaseTransferBufferHandle(long handle) {
        if (handle == 0) return;
        final boolean live;
        synchronized (xferPoolLock) {
            live = liveTransferBufferHandles.remove(handle);
        }
        if (live) {
            SDL_ReleaseGPUTransferBuffer(device.getDevice(), handle);
        } else {
            LOG.error("Double release of GPU transfer buffer {} - skipped", handle, new Throwable("double-release call site"));
        }
    }

    static void copyMappedFromData(ByteBuffer mapped, ByteBuffer data, int size, int callsiteId) {
        if (size < 0 || data.remaining() < size || mapped.remaining() < size || mapped.position() + (long) size > mapped.capacity()) {
            throw new IllegalStateException(
                "copyMappedFromData out of bounds: callsite=" + callsiteId + " size=" + size
                + " data.remaining=" + data.remaining()
                + " mapped.remaining=" + mapped.remaining()
                + " mapped.position=" + mapped.position()
                + " mapped.capacity=" + mapped.capacity());
        }
        if (data.isDirect() && mapped.isDirect()) {
            final long src = MemoryUtil.memAddress(data);
            final long dst = MemoryUtil.memAddress(mapped);
            MemoryUtil.memCopy(src, dst, size);
            mapped.position(mapped.position() + size);
        } else {
            mapped.put(data.duplicate());
        }
    }

    public static final int COPY_CALLSITE_UPLOAD_BUFFER = 1;
    public static final int COPY_CALLSITE_UPLOAD_TEX_BATCH = 2;
    public static final int COPY_CALLSITE_UPLOAD_TEX_DIRECT = 3;
    public static final int COPY_CALLSITE_ARENA = 4;

    public boolean arenaUpload(ByteBuffer data, long dstHandle, long dstOffset, boolean cycle) {
        final FrameManager.FrameState f = frameManager.frame();
        final int size = data.remaining();
        if (!f.arena.fits(size)) {
            return false;
        }
        if (f.arenaCopyPass == 0 && !beginArenaBatch(f)) return false;
        int at = f.arena.reserve(size);
        if (at < 0) {
            f.arena.requestGrow(f.arena.usedBytes() + size);
            if (Tracy.ENABLED) f.arenaOverflowFlushesThisFrame++;
            flushUploadArena(f);
            if (!beginArenaBatch(f)) return false;
            at = f.arena.reserve(size);
            if (at < 0) return false;
        }
        f.arenaMapped.position(at);
        copyMappedFromData(f.arenaMapped, data, size, COPY_CALLSITE_ARENA);
        try (var stack = stackPush()) {
            final SDL_GPUTransferBufferLocation src = SDL_GPUTransferBufferLocation.calloc(stack)
                .transfer_buffer(f.arenaXfer)
                .offset(at);
            final SDL_GPUBufferRegion dst = SDL_GPUBufferRegion.calloc(stack)
                .buffer(dstHandle)
                .offset((int) dstOffset)
                .size(size);
            SDL_UploadToGPUBuffer(f.arenaCopyPass, src, dst, cycle);
        }
        return true;
    }

    private boolean beginArenaBatch(FrameManager.FrameState f) {
        if (f.arenaXfer != 0 && f.arena.applyGrow()) {
            releaseTransferBufferHandle(f.arenaXfer);
            f.arenaXfer = 0;
        }
        if (f.arenaXfer == 0) {
            f.arena.applyGrow();
            f.arenaXfer = createTransferBuffer(f.arena.capacity());
            if (f.arenaXfer == 0) return false;
        }
        f.arenaMapped = SDL_MapGPUTransferBuffer(device.getDevice(), f.arenaXfer, true, f.arena.capacity());
        if (f.arenaMapped == null) {
            LOG.error("Failed to map arena transfer buffer: {}", SDLError.SDL_GetError());
            return false;
        }
        f.arenaCommandBuffer = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (f.arenaCommandBuffer == 0) {
            LOG.error("Failed to acquire arena upload command buffer: {}", SDLError.SDL_GetError());
            unmapArena(f);
            return false;
        }
        f.arenaCopyPass = SDL_BeginGPUCopyPass(f.arenaCommandBuffer);
        if (f.arenaCopyPass == 0) {
            LOG.error("Failed to begin arena copy pass: {}", SDLError.SDL_GetError());
            SDL_SubmitGPUCommandBuffer(f.arenaCommandBuffer);
            f.arenaCommandBuffer = 0;
            unmapArena(f);
            return false;
        }
        return true;
    }

    private void unmapArena(FrameManager.FrameState f) {
        if (f.arenaMapped == null) return;
        SDL_UnmapGPUTransferBuffer(device.getDevice(), f.arenaXfer);
        f.arenaMapped = null;
    }

    public void flushUploadArena() {
        flushUploadArena(frameManager.frame());
    }

    public void flushUploadArena(FrameManager.FrameState f) {
        if (f.arenaMapped != null) {
            SDL_UnmapGPUTransferBuffer(device.getDevice(), f.arenaXfer);
            f.arenaMapped = null;
        }
        if (f.arenaCopyPass != 0) {
            SDL_EndGPUCopyPass(f.arenaCopyPass);
            f.arenaCopyPass = 0;
        }
        if (f.arenaCommandBuffer != 0) {
            if (!SDL_SubmitGPUCommandBuffer(f.arenaCommandBuffer)) {
                device.reportGpuFailure("submit arena upload command buffer");
            }
            f.arenaCommandBuffer = 0;
            if (Tracy.ENABLED) f.arenaSubmitsThisFrame++;
        }
        f.arena.reset();
    }

    public void uploadToBuffer(long copyPass, ByteBuffer data, long gpuBuffer, long offset, boolean cycle) {
        final long size = data.remaining();
        final long xfer = acquireTransferBuffer(size);
        if (xfer == 0) return;

        final long mapSize = getTransferBufferMapSize(size);
        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, true, mapSize);
        if (mapped == null || mapped.capacity() < size) {
            SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
            returnTransferBuffer(xfer, size);
            throw new IllegalStateException("uploadToBuffer: bad mapping size=" + size + " mapSize=" + mapSize + " mapped=" + (mapped == null ? "null" : "cap=" + mapped.capacity()));
        }
        copyMappedFromData(mapped, data, (int) size, COPY_CALLSITE_UPLOAD_BUFFER);
        SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);

        try (var stack = stackPush()) {
            final SDL_GPUTransferBufferLocation src = SDL_GPUTransferBufferLocation.calloc(stack)
                .transfer_buffer(xfer)
                .offset(0);

            final SDL_GPUBufferRegion dst = SDL_GPUBufferRegion.calloc(stack)
                .buffer(gpuBuffer)
                .offset((int) offset)
                .size((int) size);

            SDL_UploadToGPUBuffer(copyPass, src, dst, cycle);
        }

        frameManager.recordUploadCommands(size, 1);
        returnTransferBuffer(xfer, size);
    }

    public static final int BATCH_SEGMENT_CAPACITY = 4 * 1024 * 1024;

    public void uploadToTexture(long copyPass, ByteBuffer data, long gpuTexture, int x, int y, int w, int h, int level) {
        final FrameManager.FrameState f = frameManager.frame();
        final int size = data.remaining();

        if (size > BATCH_SEGMENT_CAPACITY) {
            directUploadToTexture(copyPass, data, gpuTexture, x, y, w, h, level);
            return;
        }

        f.batchOffset = (f.batchOffset + 15) & -16;

        if (f.batchMapped != null && f.batchOffset + size > BATCH_SEGMENT_CAPACITY) {
            flushBatchedUploads(copyPass);
            if (f.commandBuffer == 0 && f.pendingUploadCommandBuffer != 0) {
                frameManager.flushPendingUploadCommandBuffer();
            }
        }

        if (f.batchMapped == null) {
            f.batchXfer = acquireBatchSegment();
            if (f.batchXfer == 0) return;
            f.batchMapped = SDL_MapGPUTransferBuffer(device.getDevice(), f.batchXfer, false, BATCH_SEGMENT_CAPACITY);
            if (f.batchMapped == null) {
                synchronized (xferPoolLock) { batchSegmentFree.enqueue(f.batchXfer); }
                f.batchXfer = 0;
                return;
            }
            f.batchOffset = 0;
        }

        if ((long) f.batchOffset + size > BATCH_SEGMENT_CAPACITY) {
            throw new IllegalStateException("uploadToTexture batch overflow: batchOffset=" + f.batchOffset + " size=" + size + " cap=" + BATCH_SEGMENT_CAPACITY);
        }
        f.batchMapped.position(f.batchOffset);
        copyMappedFromData(f.batchMapped, data, size, COPY_CALLSITE_UPLOAD_TEX_BATCH);

        f.pendingTexHandles.add(gpuTexture);
        f.pendingTexX.add(x);
        f.pendingTexY.add(y);
        f.pendingTexW.add(w);
        f.pendingTexH.add(h);
        f.pendingTexLevels.add(level);
        f.pendingTexOffsets.add(f.batchOffset);
        f.batchOffset += size;
    }

    public void flushBatchedUploads(long copyPass) {
        final FrameManager.FrameState f = frameManager.frame();
        final int n = f.pendingTexHandles.size();
        if (n == 0) return;

        if (f.batchMapped != null) {
            SDL_UnmapGPUTransferBuffer(device.getDevice(), f.batchXfer);
            f.batchMapped = null;
        }

        markTextureContentsDefined(f.pendingTexHandles);

        try (var stack = stackPush()) {
            final SDL_GPUTextureTransferInfo src = SDL_GPUTextureTransferInfo.calloc(stack);
            final SDL_GPUTextureRegion dst = SDL_GPUTextureRegion.calloc(stack);
            for (int i = 0; i < n; i++) {
                final long texture = f.pendingTexHandles.getLong(i);
                if (texture == 0) continue;
                src.transfer_buffer(f.batchXfer).offset(f.pendingTexOffsets.getInt(i));
                dst.texture(texture).mip_level(f.pendingTexLevels.getInt(i))
                    .x(f.pendingTexX.getInt(i)).y(f.pendingTexY.getInt(i)).z(0)
                    .w(f.pendingTexW.getInt(i)).h(f.pendingTexH.getInt(i)).d(1);
                SDL_UploadToGPUTexture(copyPass, src, dst, false);
            }
        }
        releaseBatchSegmentDeferred(f.batchXfer);
        f.batchXfer = 0;

        final long totalBytes = f.batchOffset;
        f.pendingTexHandles.clear();
        f.pendingTexX.clear();
        f.pendingTexY.clear();
        f.pendingTexW.clear();
        f.pendingTexH.clear();
        f.pendingTexLevels.clear();
        f.pendingTexOffsets.clear();
        f.batchOffset = 0;
        frameManager.recordUploadCommands(totalBytes, n);
    }

    void dropBatchedUploadsTargeting(long handle) {
        final LongArrayList handles = frameManager.frame().pendingTexHandles;
        if (handles.isEmpty()) return;
        for (int i = 0, n = handles.size(); i < n; i++) {
            if (handles.getLong(i) == handle) handles.set(i, 0L);
        }
    }

    public boolean hasBatchedUploads() {
        return !frameManager.frame().pendingTexHandles.isEmpty();
    }

    public void uploadToTexture3D(long copyPass, ByteBuffer data, long gpuTexture, int x, int y, int z, int w, int h, int d, int level) {
        final long size = data.remaining();
        final long xfer = acquireTransferBuffer(size);
        if (xfer == 0) return;

        final long mapSize = getTransferBufferMapSize(size);
        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, true, mapSize);
        if (mapped == null || mapped.capacity() < size) {
            SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
            returnTransferBuffer(xfer, size);
            throw new IllegalStateException("uploadToTexture3D: bad mapping size=" + size + " mapSize=" + mapSize + " mapped=" + (mapped == null ? "null" : "cap=" + mapped.capacity()));
        }
        copyMappedFromData(mapped, data, (int) size, COPY_CALLSITE_UPLOAD_TEX_DIRECT);
        SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);

        try (var stack = stackPush()) {
            final SDL_GPUTextureTransferInfo src = SDL_GPUTextureTransferInfo.calloc(stack)
                .transfer_buffer(xfer)
                .offset(0);
            final SDL_GPUTextureRegion dst = SDL_GPUTextureRegion.calloc(stack)
                .texture(gpuTexture)
                .mip_level(level)
                .x(x).y(y).z(z)
                .w(w).h(h).d(d);
            SDL_UploadToGPUTexture(copyPass, src, dst, false);
        }
        markTextureContentDefined(gpuTexture);

        frameManager.recordUploadCommands(size, 1);
        returnTransferBuffer(xfer, size);
    }

    private void directUploadToTexture(long copyPass, ByteBuffer data, long gpuTexture, int x, int y, int w, int h, int level) {
        final long size = data.remaining();
        final long xfer = acquireTransferBuffer(size);
        if (xfer == 0) return;

        final long mapSize = getTransferBufferMapSize(size);
        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, true, mapSize);
        if (mapped == null || mapped.capacity() < size) {
            SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
            returnTransferBuffer(xfer, size);
            throw new IllegalStateException("directUploadToTexture: bad mapping size=" + size + " mapSize=" + mapSize + " mapped=" + (mapped == null ? "null" : "cap=" + mapped.capacity()));
        }
        copyMappedFromData(mapped, data, (int) size, COPY_CALLSITE_UPLOAD_TEX_DIRECT);
        SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);

        try (var stack = stackPush()) {
            final SDL_GPUTextureTransferInfo src = SDL_GPUTextureTransferInfo.calloc(stack)
                .transfer_buffer(xfer)
                .offset(0);

            final SDL_GPUTextureRegion dst = SDL_GPUTextureRegion.calloc(stack)
                .texture(gpuTexture)
                .mip_level(level)
                .x(x).y(y).z(0)
                .w(w).h(h).d(1);

            SDL_UploadToGPUTexture(copyPass, src, dst, false);
        }
        markTextureContentDefined(gpuTexture);

        frameManager.recordUploadCommands(size, 1);
        returnTransferBuffer(xfer, size);
    }

    public boolean enqueueDeferredTextureUpload(ContextState st, ByteBuffer prepped, long texHandle, int x, int y, int w, int h, int level) {
        final TransferThread tt = transferThread;
        if (tt == null) return false;
        final long size = prepped.remaining();
        final long xfer = acquireTransferBufferThreadSafe(size);
        if (xfer == 0) return false;
        final long mapSize = getTransferBufferMapSize(size);
        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, true, mapSize);
        if (mapped != null) {
            final int prevPos = prepped.position();
            mapped.put(prepped);
            prepped.position(prevPos);
        }
        SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
        final long seq = TransferThread.nextSeq();
        st.frameHighestEnqueuedSeq = seq;
        lastTextureUploadSeq = seq;
        markTextureContentDefined(texHandle);
        tt.enqueue(TransferThread.TextureRegionUpload.acquire(xfer, texHandle, x, y, w, h, level, size, seq));
        tt.wake();
        return true;
    }

    public void downloadFromTexture(long commandBuffer, long gpuTexture, int x, int y, int w, int h, int level, ByteBuffer output) {
        downloadFromTexture(commandBuffer, gpuTexture, x, y, 0, w, h, 1, level, output);
    }

    public void downloadFromTexture(long commandBuffer, long gpuTexture, int x, int y, int z, int w, int h, int d, int level, ByteBuffer output) {
        final int size = output.remaining();

        try (var stack = stackPush()) {
            final SDL_GPUTransferBufferCreateInfo xferCi = SDL_GPUTransferBufferCreateInfo.calloc(stack)
                .usage(SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD)
                .size(size);
            final long xfer = SDL_CreateGPUTransferBuffer(device.getDevice(), xferCi);
            if (xfer == 0) {
                LOG.error("Failed to create download transfer buffer: {}", SDLError.SDL_GetError());
                return;
            }

            final long copyPass = SDL_BeginGPUCopyPass(commandBuffer);
            if (copyPass == 0) {
                LOG.error("Failed to begin copy pass for texture download: {}", SDLError.SDL_GetError());
                SDL_ReleaseGPUTransferBuffer(device.getDevice(), xfer);
                return;
            }

            final SDL_GPUTextureRegion src = SDL_GPUTextureRegion.calloc(stack)
                .texture(gpuTexture)
                .mip_level(level)
                .x(x).y(y).z(z)
                .w(w).h(h).d(d);

            final SDL_GPUTextureTransferInfo dst = SDL_GPUTextureTransferInfo.calloc(stack)
                .transfer_buffer(xfer)
                .offset(0);

            SDL_DownloadFromGPUTexture(copyPass, src, dst);
            SDL_EndGPUCopyPass(copyPass);

            final long fence = SDL_SubmitGPUCommandBufferAndAcquireFence(commandBuffer);
            if (fence == 0) {
                LOG.error("Failed to submit download command buffer: {}", SDLError.SDL_GetError());
                SDL_ReleaseGPUTransferBuffer(device.getDevice(), xfer);
                return;
            }
            try {
                final PointerBuffer fences = stack.pointers(fence);
                SDL_WaitForGPUFences(device.getDevice(), true, fences);
            } finally {
                SDL_ReleaseGPUFence(device.getDevice(), fence);
            }

            final long mappedPtr = nSDL_MapGPUTransferBuffer(device.getDevice(), xfer, false);
            if (mappedPtr != 0) {
                final ByteBuffer mapped = MemoryUtil.memByteBuffer(mappedPtr, size);
                output.put(mapped);
            }
            SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
            SDL_ReleaseGPUTransferBuffer(device.getDevice(), xfer);
        }
    }

    public void downloadFromBuffer(long commandBuffer, long gpuBuffer, int offset, int size, ByteBuffer output) {
        try (var stack = stackPush()) {
            final SDL_GPUTransferBufferCreateInfo xferCi = SDL_GPUTransferBufferCreateInfo.calloc(stack)
                .usage(SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD)
                .size(size);
            final long xfer = SDL_CreateGPUTransferBuffer(device.getDevice(), xferCi);
            if (xfer == 0) {
                LOG.error("Failed to create download transfer buffer for buffer readback: {}", SDLError.SDL_GetError());
                return;
            }

            final long copyPass = SDL_BeginGPUCopyPass(commandBuffer);
            if (copyPass == 0) {
                LOG.error("Failed to begin copy pass for buffer download: {}", SDLError.SDL_GetError());
                SDL_ReleaseGPUTransferBuffer(device.getDevice(), xfer);
                return;
            }

            final SDL_GPUBufferRegion src = SDL_GPUBufferRegion.calloc(stack)
                .buffer(gpuBuffer)
                .offset(offset)
                .size(size);

            final SDL_GPUTransferBufferLocation dst = SDL_GPUTransferBufferLocation.calloc(stack)
                .transfer_buffer(xfer)
                .offset(0);

            SDL_DownloadFromGPUBuffer(copyPass, src, dst);
            SDL_EndGPUCopyPass(copyPass);

            final long fence = SDL_SubmitGPUCommandBufferAndAcquireFence(commandBuffer);
            if (fence == 0) {
                LOG.error("Failed to submit buffer download command buffer: {}", SDLError.SDL_GetError());
                SDL_ReleaseGPUTransferBuffer(device.getDevice(), xfer);
                return;
            }
            try {
                final PointerBuffer fences = stack.pointers(fence);
                SDL_WaitForGPUFences(device.getDevice(), true, fences);
            } finally {
                SDL_ReleaseGPUFence(device.getDevice(), fence);
            }

            final long mappedPtr = nSDL_MapGPUTransferBuffer(device.getDevice(), xfer, false);
            if (mappedPtr != 0) {
                final ByteBuffer mapped = MemoryUtil.memByteBuffer(mappedPtr, size);
                output.put(mapped);
            }
            SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
            SDL_ReleaseGPUTransferBuffer(device.getDevice(), xfer);
        }
    }
    private long defaultSampler;
    private long fallbackTexture;
    private long dummyVertexBuffer;

    public long getOrCreateDefaultSampler() {
        if (defaultSampler != 0) return defaultSampler;

        try (var stack = stackPush()) {
            final SDL_GPUSamplerCreateInfo ci = SDL_GPUSamplerCreateInfo.calloc(stack)
                .min_filter(SDL_GPU_FILTER_LINEAR)
                .mag_filter(SDL_GPU_FILTER_LINEAR)
                .mipmap_mode(SDL_GPU_SAMPLERMIPMAPMODE_LINEAR)
                .address_mode_u(SDL_GPU_SAMPLERADDRESSMODE_REPEAT)
                .address_mode_v(SDL_GPU_SAMPLERADDRESSMODE_REPEAT)
                .address_mode_w(SDL_GPU_SAMPLERADDRESSMODE_REPEAT)
                .min_lod(0.0f)
                .max_lod(1000.0f);

            defaultSampler = SDL_CreateGPUSampler(device.getDevice(), ci);
            if (defaultSampler == 0) {
                LOG.error("Failed to create default sampler: {}", SDLError.SDL_GetError());
            }
        }
        return defaultSampler;
    }

    public long getOrCreateFallbackTexture() {
        if (fallbackTexture != 0) return fallbackTexture;

        try (var stack = stackPush()) {
            final SDL_GPUTextureCreateInfo ci = SDL_GPUTextureCreateInfo.calloc(stack)
                .type(SDL_GPU_TEXTURETYPE_2D)
                .format(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM)
                .usage(SDL_GPU_TEXTUREUSAGE_SAMPLER)
                .width(1)
                .height(1)
                .layer_count_or_depth(1)
                .num_levels(1);

            fallbackTexture = SDL_CreateGPUTexture(device.getDevice(), ci);
            if (fallbackTexture == 0) {
                LOG.error("Failed to create fallback texture: {}", SDLError.SDL_GetError());
                return 0;
            }
            trackTextureHandle(fallbackTexture);
        }
        // GL 4.4 sec 11.1.3.5: sampling an incomplete texture yields (0,0,0,1).
        final byte[] texel = { 0, 0, 0, (byte) 0xFF };
        seedOneTexel(fallbackTexture, texel);
        return fallbackTexture;
    }

    private long fallbackStorageTexture3D;
    public long getOrCreateFallbackStorageTexture3D() {
        if (fallbackStorageTexture3D != 0) return fallbackStorageTexture3D;
        try (var stack = stackPush()) {
            final SDL_GPUTextureCreateInfo ci = SDL_GPUTextureCreateInfo.calloc(stack)
                .type(SDL_GPU_TEXTURETYPE_3D)
                .format(SDL_GPU_TEXTUREFORMAT_R8_UINT)
                .usage(SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ
                     | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ
                     | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE)
                .width(1).height(1).layer_count_or_depth(1).num_levels(1);
            fallbackStorageTexture3D = SDL_CreateGPUTexture(device.getDevice(), ci);
            if (fallbackStorageTexture3D == 0) {
                LOG.error("Failed to create fallback storage 3D texture: {}", SDLError.SDL_GetError());
                return 0;
            }
            trackTextureHandle(fallbackStorageTexture3D);
        }
        seedOneTexel(fallbackStorageTexture3D, new byte[1]);
        return fallbackStorageTexture3D;
    }

    private long fallbackStorageBuffer;
    public long getOrCreateFallbackStorageBuffer() {
        if (fallbackStorageBuffer != 0) return fallbackStorageBuffer;
        try (var stack = stackPush()) {
            final SDL_GPUBufferCreateInfo ci = SDL_GPUBufferCreateInfo.calloc(stack)
                .usage(SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ
                     | SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_READ)
                .size(FALLBACK_STORAGE_BUFFER_BYTES);
            fallbackStorageBuffer = SDL_CreateGPUBuffer(device.getDevice(), ci);
            if (fallbackStorageBuffer == 0) {
                LOG.error("Failed to create fallback storage buffer: {}", SDLError.SDL_GetError());
                return 0;
            }
            trackBufferHandle(fallbackStorageBuffer);
        }
        seedZeroBuffer(fallbackStorageBuffer, FALLBACK_STORAGE_BUFFER_BYTES);
        return fallbackStorageBuffer;
    }

    private static final int FALLBACK_STORAGE_BUFFER_BYTES = 16;

    /**
     * Mesa binds a null image view for an unbound image unit and dispatches anyway
     * (st_atom_image.c st_convert_image_from_unit). SDL has no null descriptor and dereferences the
     * handle, so the equivalent is a real 1x1x1 zeroed texture whose format and target match the
     * shader's declaration.
     */
    private final Long2LongOpenHashMap computeStandInTextures = new Long2LongOpenHashMap();

    static long computeStandInKey(int glFormat, int glTarget, boolean readWrite) {
        return ((long) glFormat << 33) | ((long) glTarget << 1) | (readWrite ? 1L : 0L);
    }

    public long getOrCreateComputeStandInTexture(int glFormat, int glTarget, boolean readWrite) {
        final long key = computeStandInKey(glFormat, glTarget, readWrite);
        synchronized (computeStandInTextures) {
            final long cached = computeStandInTextures.get(key);
            if (cached != 0) return cached;
        }
        final int sdlFormat = mapTextureFormat(glFormat);
        final int usage = readWrite ? SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE : SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ;
        final long handle;
        try (var stack = stackPush()) {
            final SDL_GPUTextureCreateInfo ci = SDL_GPUTextureCreateInfo.calloc(stack)
                .type(mapTextureType(glTarget))
                .format(sdlFormat)
                .usage(usage)
                .width(1).height(1).layer_count_or_depth(1).num_levels(1);
            handle = SDL_CreateGPUTexture(device.getDevice(), ci);
        }
        if (handle == 0) {
            LOG.error("Failed to create compute stand-in texture for GL format 0x{} target 0x{}: {}",
                Integer.toHexString(glFormat), Integer.toHexString(glTarget), SDLError.SDL_GetError());
            return 0;
        }
        trackTextureHandle(handle);
        seedOneTexel(handle, new byte[Math.max(1, PixelOps.sdlFormatTexelBytes(sdlFormat))]);
        synchronized (computeStandInTextures) {
            final long raced = computeStandInTextures.get(key);
            if (raced != 0) {
                releaseTextureHandle(handle);
                return raced;
            }
            computeStandInTextures.put(key, handle);
        }
        return handle;
    }

    private void seedOneTexel(long texture, byte[] texel) {
        if (texture == 0) return;
        final long xfer = createTransferBuffer(texel.length);
        if (xfer == 0) return;
        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, false, texel.length);
        if (mapped != null) mapped.put(texel);
        SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);

        final long cb = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (cb != 0) {
            try (var stack = stackPush()) {
                final long copyPass = SDL_BeginGPUCopyPass(cb);
                final SDL_GPUTextureTransferInfo src = SDL_GPUTextureTransferInfo.calloc(stack)
                    .transfer_buffer(xfer)
                    .offset(0);
                final SDL_GPUTextureRegion dst = SDL_GPUTextureRegion.calloc(stack)
                    .texture(texture)
                    .mip_level(0)
                    .x(0).y(0).z(0)
                    .w(1).h(1).d(1);
                SDL_UploadToGPUTexture(copyPass, src, dst, false);
                SDL_EndGPUCopyPass(copyPass);
            }
            SDL_SubmitGPUCommandBuffer(cb);
        }
        releaseTransferBuffer(xfer);
    }

    private void seedZeroBuffer(long buffer, int bytes) {
        if (buffer == 0) return;
        final long xfer = createTransferBuffer(bytes);
        if (xfer == 0) return;
        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, false, bytes);
        if (mapped != null) MemoryUtil.memSet(mapped, 0);
        SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);

        final long cb = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (cb != 0) {
            try (var stack = stackPush()) {
                final long copyPass = SDL_BeginGPUCopyPass(cb);
                final SDL_GPUTransferBufferLocation src = SDL_GPUTransferBufferLocation.calloc(stack)
                    .transfer_buffer(xfer)
                    .offset(0);
                final SDL_GPUBufferRegion dst = SDL_GPUBufferRegion.calloc(stack)
                    .buffer(buffer)
                    .offset(0)
                    .size(bytes);
                SDL_UploadToGPUBuffer(copyPass, src, dst, false);
                SDL_EndGPUCopyPass(copyPass);
            }
            SDL_SubmitGPUCommandBuffer(cb);
        }
        releaseTransferBuffer(xfer);
    }

    private long dummyColorTarget;
    public long getOrCreateDummyColorTarget() {
        if (dummyColorTarget != 0) return dummyColorTarget;
        try (var stack = stackPush()) {
            final SDL_GPUTextureCreateInfo ci = SDL_GPUTextureCreateInfo.calloc(stack)
                .type(SDL_GPU_TEXTURETYPE_2D)
                .format(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM)
                .usage(SDL_GPU_TEXTUREUSAGE_COLOR_TARGET)
                .width(1).height(1).layer_count_or_depth(1).num_levels(1);
            dummyColorTarget = SDL_CreateGPUTexture(device.getDevice(), ci);
        }
        trackTextureHandle(dummyColorTarget);
        return dummyColorTarget;
    }

    public long getOrCreateSwapchainDepthStencil(int width, int height) {
        if (swapchainDepthStencilFormat == 0 || width <= 0 || height <= 0) return 0;
        if (swapchainDepthStencil != 0 && swapchainDepthStencilWidth == width && swapchainDepthStencilHeight == height) {
            return swapchainDepthStencil;
        }
        if (swapchainDepthStencil != 0) {
            releaseTextureDeferred(swapchainDepthStencil);
            swapchainDepthStencil = 0;
        }
        try (var stack = stackPush()) {
            final SDL_GPUTextureCreateInfo ci = SDL_GPUTextureCreateInfo.calloc(stack)
                .type(SDL_GPU_TEXTURETYPE_2D)
                .format(swapchainDepthStencilFormat)
                .usage(SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET)
                .width(width).height(height).layer_count_or_depth(1).num_levels(1);
            swapchainDepthStencil = SDL_CreateGPUTexture(device.getDevice(), ci);
        }
        if (swapchainDepthStencil == 0) {
            swapchainDepthStencilFormat = 0;
            LOG.warn("Failed to create the default framebuffer depth+stencil target ({}x{}); it will have neither", width, height);
            return 0;
        }
        trackTextureHandle(swapchainDepthStencil);
        swapchainDepthStencilWidth = width;
        swapchainDepthStencilHeight = height;
        return swapchainDepthStencil;
    }

    private static final int DUMMY_VBO_SIZE = 16 * 16;
    public static final int ATTRIB_RING_BLOCKS = 2048 * Device.MAX_FRAMES_IN_FLIGHT;
    public static final int ATTRIB_RING_CHUNK_BLOCKS = 64;
    private static final int ATTRIB_RING_CHUNKS = ATTRIB_RING_BLOCKS / ATTRIB_RING_CHUNK_BLOCKS;
    private final AtomicLong attribRingCounter = new AtomicLong();

    public int nextAttribRingChunkOffset() {
        final int chunk = 1 + (int) (attribRingCounter.getAndIncrement() % (ATTRIB_RING_CHUNKS - 1));
        return chunk * ATTRIB_RING_CHUNK_BLOCKS * DUMMY_VBO_SIZE;
    }

    public long getOrCreateDummyVertexBuffer() {
        if (dummyVertexBuffer != 0) return dummyVertexBuffer;

        try (var stack = stackPush()) {
            dummyVertexBuffer = SDL_CreateGPUBuffer(device.getDevice(), SDL_GPUBufferCreateInfo.calloc(stack).usage(SDL_GPU_BUFFERUSAGE_VERTEX).size(ATTRIB_RING_BLOCKS * DUMMY_VBO_SIZE));
            if (dummyVertexBuffer == 0) {
                LOG.error("Failed to create dummy vertex buffer: {}", SDLError.SDL_GetError());
                return 0;
            }
            trackBufferHandle(dummyVertexBuffer);

            final long xfer = createTransferBuffer(DUMMY_VBO_SIZE);
            if (xfer != 0) {
                final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, false, DUMMY_VBO_SIZE);
                if (mapped != null) {
                    final FloatBuffer fb = mapped.asFloatBuffer();
                    for (int i = 0; i < 16; i++) {
                        if (i == 1) {
                            fb.put(1.0f).put(1.0f).put(1.0f).put(1.0f); // color: white
                        } else {
                            fb.put(0.0f).put(0.0f).put(0.0f).put(1.0f); // default: (0,0,0,1)
                        }
                    }
                }
                SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);

                final long cb = SDL_AcquireGPUCommandBuffer(device.getDevice());
                if (cb != 0) {
                    final long copyPass = SDL_BeginGPUCopyPass(cb);
                    final var src = SDL_GPUTransferBufferLocation.calloc(stack).transfer_buffer(xfer).offset(0);
                    final var dst = SDL_GPUBufferRegion.calloc(stack).buffer(dummyVertexBuffer).offset(0).size(DUMMY_VBO_SIZE);
                    SDL_UploadToGPUBuffer(copyPass, src, dst, false);
                    SDL_EndGPUCopyPass(copyPass);
                    SDL_SubmitGPUCommandBuffer(cb);
                }
                releaseTransferBufferHandle(xfer);
            }
        }
        return dummyVertexBuffer;
    }

    private static final int ATTRIB_SLOT_SIZE = 16;

    private long attribDefaultXfer;

    public void uploadAttribDefaults(long copyPass, float[] defaults, int dirtyMask) {
        if (dummyVertexBuffer == 0 || dirtyMask == 0) return;

        if (attribDefaultXfer == 0) {
            attribDefaultXfer = createTransferBuffer(DUMMY_VBO_SIZE);
            if (attribDefaultXfer == 0) return;
        }

        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), attribDefaultXfer, true, DUMMY_VBO_SIZE);
        if (mapped == null) return;
        final FloatBuffer fb = mapped.asFloatBuffer();
        int wmask = dirtyMask;
        while (wmask != 0) {
            final int slot = Integer.numberOfTrailingZeros(wmask);
            final int base = slot * 4;
            fb.position(base);
            fb.put(defaults[base]).put(defaults[base + 1]).put(defaults[base + 2]).put(defaults[base + 3]);
            wmask &= wmask - 1;
        }
        SDL_UnmapGPUTransferBuffer(device.getDevice(), attribDefaultXfer);

        try (var stack = stackPush()) {
            int mask = dirtyMask;
            while (mask != 0) {
                final int slot = Integer.numberOfTrailingZeros(mask);
                final int offset = slot * ATTRIB_SLOT_SIZE;
                final var src = SDL_GPUTransferBufferLocation.calloc(stack).transfer_buffer(attribDefaultXfer).offset(offset);
                final var dst = SDL_GPUBufferRegion.calloc(stack).buffer(dummyVertexBuffer).offset(offset).size(ATTRIB_SLOT_SIZE);
                SDL_UploadToGPUBuffer(copyPass, src, dst, false);
                mask &= mask - 1;
            }
        }
    }

    public int mapTextureFormat(int glFormat) {
        return mapTextureFormat(glFormat, preferredD24, preferredD24S8);
    }

    public static int mapTextureFormat(int glFormat, int preferredD24, int preferredD24S8) {
        return switch (glFormat) {
            case GL11.GL_RGBA, GL11.GL_RGBA8 -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
            case GL11.GL_RGB, GL11.GL_RGB8 -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM; // RGB -> RGBA promotion
            case GL11.GL_ALPHA, GL11.GL_LUMINANCE, GL11.GL_LUMINANCE_ALPHA -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM; // expanded to RGBA at upload
            case GL21.GL_SRGB8_ALPHA8 -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB;
            case GL30.GL_RGBA32F -> SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT;
            case GL11.GL_RGBA16 -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM;
            case GL30.GL_RGBA16F -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT;
            case GL30.GL_R8 -> SDL_GPU_TEXTUREFORMAT_R8_UNORM;
            case GL30.GL_RG8 -> SDL_GPU_TEXTUREFORMAT_R8G8_UNORM;
            case GL30.GL_R16F -> SDL_GPU_TEXTUREFORMAT_R16_FLOAT;
            case GL30.GL_RG16F -> SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT;
            case GL30.GL_R32F -> SDL_GPU_TEXTUREFORMAT_R32_FLOAT;
            case GL30.GL_RG32F -> SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT;
            case GL11.GL_DEPTH_COMPONENT -> SDL_GPU_TEXTUREFORMAT_D32_FLOAT; // unsized -> D32F default
            case GL14.GL_DEPTH_COMPONENT16 -> SDL_GPU_TEXTUREFORMAT_D16_UNORM;
            case GL14.GL_DEPTH_COMPONENT24 -> preferredD24;
            case GL14.GL_DEPTH_COMPONENT32, GL30.GL_DEPTH_COMPONENT32F -> SDL_GPU_TEXTUREFORMAT_D32_FLOAT;
            case GL30.GL_DEPTH24_STENCIL8 -> preferredD24S8;
            case GL30.GL_DEPTH32F_STENCIL8 -> SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT;
            case GL11.GL_STENCIL_INDEX, GL30.GL_STENCIL_INDEX1, GL30.GL_STENCIL_INDEX4,
                 GL30.GL_STENCIL_INDEX8, GL30.GL_STENCIL_INDEX16 -> preferredD24S8;
            case GL30.GL_R8UI -> SDL_GPU_TEXTUREFORMAT_R8_UINT;
            case GL30.GL_R16UI -> SDL_GPU_TEXTUREFORMAT_R16_UINT;
            case GL30.GL_R32UI -> SDL_GPU_TEXTUREFORMAT_R32_UINT;
            case GL30.GL_RG8UI -> SDL_GPU_TEXTUREFORMAT_R8G8_UINT;
            case GL30.GL_RG16UI -> SDL_GPU_TEXTUREFORMAT_R16G16_UINT;
            case GL30.GL_RG32UI -> SDL_GPU_TEXTUREFORMAT_R32G32_UINT;
            case GL30.GL_RGBA8UI -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT;
            case GL30.GL_RGBA16UI -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT;
            case GL30.GL_RGBA32UI -> SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT;
            case GL30.GL_R8I -> SDL_GPU_TEXTUREFORMAT_R8_INT;
            case GL30.GL_R16I -> SDL_GPU_TEXTUREFORMAT_R16_INT;
            case GL30.GL_R32I -> SDL_GPU_TEXTUREFORMAT_R32_INT;
            case GL30.GL_RG8I -> SDL_GPU_TEXTUREFORMAT_R8G8_INT;
            case GL30.GL_RG16I -> SDL_GPU_TEXTUREFORMAT_R16G16_INT;
            case GL30.GL_RG32I -> SDL_GPU_TEXTUREFORMAT_R32G32_INT;
            case GL30.GL_RGBA8I -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT;
            case GL30.GL_RGBA16I -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT;
            case GL30.GL_RGBA32I -> SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT;
            case GL30.GL_RGB8UI -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT;
            case GL30.GL_RGB16UI -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT;
            case GL30.GL_RGB32UI -> SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT;
            case GL30.GL_RGB8I -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT;
            case GL30.GL_RGB16I -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT;
            case GL30.GL_RGB32I -> SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT;
            case GL30.GL_R11F_G11F_B10F -> SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT;
            case GL31.GL_R8_SNORM -> SDL_GPU_TEXTUREFORMAT_R8_SNORM;
            case GL31.GL_RG8_SNORM -> SDL_GPU_TEXTUREFORMAT_R8G8_SNORM;
            case GL31.GL_RGB8_SNORM -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM; // RGB SNORM -> RGBA SNORM promotion
            case GL31.GL_RGBA8_SNORM -> SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM;
            case GL31.GL_RGBA16_SNORM -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM;
            case GL31.GL_RGB16_SNORM -> SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM; // RGB SNORM -> RGBA SNORM promotion
            default -> {
                LOG.warn("Unmapped GL texture format: 0x{}", Integer.toHexString(glFormat));
                yield SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
            }
        };
    }

    private static boolean isDepthFormat(int sdlFormat) {
        return sdlFormat == SDL_GPU_TEXTUREFORMAT_D16_UNORM
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D24_UNORM
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D32_FLOAT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT;
    }

    static boolean isIntegerFormat(int sdlFormat) {
        return sdlFormat == SDL_GPU_TEXTUREFORMAT_R8_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R8G8_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R16_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R16G16_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R32_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R32G32_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R8_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R8G8_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R16_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R16G16_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R32_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R32G32_INT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT;
    }

    private static int mapTextureType(int glTarget) {
        return switch (glTarget) {
            case GL11.GL_TEXTURE_2D -> SDL_GPU_TEXTURETYPE_2D;
            case GL13.GL_TEXTURE_CUBE_MAP -> SDL_GPU_TEXTURETYPE_CUBE;
            case GL12.GL_TEXTURE_3D -> SDL_GPU_TEXTURETYPE_3D;
            case GL30.GL_TEXTURE_2D_ARRAY -> SDL_GPU_TEXTURETYPE_2D_ARRAY;
            default -> SDL_GPU_TEXTURETYPE_2D;
        };
    }

    private void releaseAllAndClear(Int2LongOpenHashMap map, LongConsumer release) {
        for (long handle : map.values()) {
            if (handle != 0) release.accept(handle);
        }
        map.clear();
    }

    public void shutdown() {
        flushDeferredReleases();

        final long dev = device.getDevice();
        if (attribDefaultXfer != 0) {
            releaseTransferBufferHandle(attribDefaultXfer);
            attribDefaultXfer = 0;
        }

        drainTransferBufferPool();
        drainGpuBufferPool();

        samplerCache.releaseAll(device);

        if (defaultSampler != 0) {
            SDL_ReleaseGPUSampler(dev, defaultSampler);
            defaultSampler = 0;
        }
        if (fallbackTexture != 0) {
            releaseTextureHandle(fallbackTexture);
            fallbackTexture = 0;
        }
        if (dummyVertexBuffer != 0) {
            releaseBufferHandle(dummyVertexBuffer);
            dummyVertexBuffer = 0;
        }

        if (fallbackStorageBuffer != 0) {
            releaseBufferHandle(fallbackStorageBuffer);
            fallbackStorageBuffer = 0;
        }
        if (dummyColorTarget != 0) {
            releaseTextureHandle(dummyColorTarget);
            dummyColorTarget = 0;
        }
        if (swapchainDepthStencil != 0) {
            releaseTextureHandle(swapchainDepthStencil);
            swapchainDepthStencil = 0;
            swapchainDepthStencilWidth = 0;
            swapchainDepthStencilHeight = 0;
        }
        if (fallbackStorageTexture3D != 0) {
            releaseTextureHandle(fallbackStorageTexture3D);
            fallbackStorageTexture3D = 0;
        }
        synchronized (computeStandInTextures) {
            for (long handle : computeStandInTextures.values()) releaseTextureHandle(handle);
            computeStandInTextures.clear();
        }

        releaseAllAndClear(textureHandles, this::releaseTextureHandle);
        releaseAllAndClear(bufferHandles, this::releaseBufferHandle);
        releaseAllAndClear(samplerHandles, h -> SDL_ReleaseGPUSampler(dev, h));
        bufferSizes.clear();
        bufferUsages.clear();
        bufferGlUsages.clear();
        bufferStorageFlags.clear();
        undefinedContentBuffers.clear();

        for (FboState fbo : fboStates.values()) fbo.free();
        fboStates.clear();
        for (ByteBuffer bb : uboShadows.values()) memFree(bb);
        uboShadows.clear();
        for (ByteBuffer bb : eboShadows.values()) memFree(bb);
        eboShadows.clear();
        eboShadowWanted.clear();
        for (ByteBuffer bb : arrayShadows.values()) memFree(bb);
        arrayShadows.clear();
        arrayShadowWanted.clear();
        for (ByteBuffer bb : pboStagingData.values()) memFree(bb);
        pboStagingData.clear();
        for (PersistentMapping pm : persistentMappings.values()) {
            untrackPersistentDirty(pm);
            releasePersistentStaging(pm);
        }
        persistentMappings.clear();
        mappingsVersion++;
    }

    public record TextureMeta(int glTarget, int glFormat, int sdlFormat, int width, int height, int depth, int levels, int usage) {}
}
