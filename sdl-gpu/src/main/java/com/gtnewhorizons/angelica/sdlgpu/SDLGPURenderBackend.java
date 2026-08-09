package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.sdlgpu.compute.VoxelizationDispatcher;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.backend.GLDebugMessageListener;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.sdlgpu.compute.ComputeBinder;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.device.SDLDrawable;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FenceTracker;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.DrawDispatch;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineStore;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.VertexAttribs;
import com.gtnewhorizons.angelica.sdlgpu.resource.BufferParams;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FormatMap;
import com.gtnewhorizons.angelica.sdlgpu.resource.Image3DClear;
import com.gtnewhorizons.angelica.sdlgpu.resource.PersistentBufferSync;
import com.gtnewhorizons.angelica.sdlgpu.resource.PersistentMapping;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ReadbackShadows;
import com.gtnewhorizons.angelica.sdlgpu.resource.TextureOps;
import com.gtnewhorizons.angelica.sdlgpu.resource.TextureSamplerState;
import com.gtnewhorizons.angelica.sdlgpu.resource.TransferThread;
import com.gtnewhorizons.angelica.sdlgpu.sampler.SamplerBinder;
import com.gtnewhorizons.angelica.sdlgpu.sampler.StorageBufferBinder;
import com.gtnewhorizons.angelica.sdlgpu.sampler.StorageTextureBinder;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.splash.SplashDispatcher;
import com.gtnewhorizons.angelica.sdlgpu.splash.SplashOffscreenTarget;
import com.gtnewhorizons.angelica.sdlgpu.util.DebugLabels;
import com.gtnewhorizons.angelica.sdlgpu.util.DebugMessageRelay;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.lwjgl.sdl.SDLGPU;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDL_GPUBlitInfo;
import org.lwjgl.sdl.SDL_GPUBufferLocation;
import org.lwjgl.sdl.SDL_GPUTextureLocation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjglx.opengl.Display;

import me.eigenraven.lwjgl3ify.api.DisplayEvents;
import com.gtnewhorizons.angelica.sdlgpu.compat.Lwjgl3GLCapabilitiesShim;
import me.eigenraven.lwjgl3ify.api.GLCapabilitiesOverride;
import me.eigenraven.lwjgl3ify.api.SwapchainInvalidatingChange;

import static org.lwjgl.sdl.SDLGPU.*;

/** SDL GPU implementation of {@link RenderBackend}. */
public class SDLGPURenderBackend extends RenderBackend {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final Tracy.ZoneId Z_SDL_INDIRECT_DRAW = Tracy.zoneId("sdlIndirectDraw", Tracy.COLOR_TERRAIN);

    private final Device device = SDLGPUGate.device();
    private final FrameManager frameManager = new FrameManager(device);
    private final ResourceManager resourceManager = new ResourceManager(device, frameManager);
    private final Image3DClear image3DClear = new Image3DClear();
    private final ShaderManager shaderManager = new ShaderManager(device);
    private final PipelineStore pipelineStore = new PipelineStore(device);

    private String cachedRenderer;
    private String cachedVendor;
    private String cachedVersion;
    private String cachedExtensions = "";
    private int[] cachedSwapchainFormatArray;

    private DebugLabels debugLabels;


    private final FenceTracker fenceTracker = new FenceTracker(device, frameManager);
    private final FBOClearTracker fboClearTracker = new FBOClearTracker(frameManager, resourceManager, shaderManager);
    private final SamplerBinder samplerBinder = new SamplerBinder(device, resourceManager, shaderManager);
    private final StorageTextureBinder storageTextureBinder = new StorageTextureBinder(resourceManager, shaderManager);
    private final StorageBufferBinder storageBufferBinder = new StorageBufferBinder(resourceManager, shaderManager);
    private final ComputeBinder computeBinder = new ComputeBinder(frameManager, resourceManager, shaderManager, samplerBinder);
    private final VoxelizationDispatcher voxelizationDispatcher =
        new VoxelizationDispatcher(computeBinder);
    private final VertexAttribs vertexAttribs = new VertexAttribs();
    private final TextureOps textureOps = new TextureOps(device, frameManager, resourceManager, fboClearTracker);
    private final ReadbackShadows readbackShadows = new ReadbackShadows(device);
    private final PersistentBufferSync persistentSync = new PersistentBufferSync(frameManager, resourceManager,
        new PersistentBufferSync.UploadSink() {
            @Override public void enqueue(TransferThread.DeferredUpload upload) { enqueueUpload(upload); }
            @Override public long nextSeq() { return TransferThread.nextSeq(); }
        });
    private final PipelineApplier pipelineApplier = new PipelineApplier(frameManager, resourceManager, shaderManager, pipelineStore, fboClearTracker, persistentSync, samplerBinder, storageTextureBinder, storageBufferBinder);
    private final DrawDispatch drawDispatch = new DrawDispatch(device, frameManager, resourceManager, pipelineApplier, this::enqueuePreCopied);

    private final IntOpenHashSet missingProgramWarned = new IntOpenHashSet();

    private boolean droppedCopyWarned;
    private boolean droppedSsboWriteWarned;
    private final DeferredCopyOp deferredCopyOp = new DeferredCopyOp();

    private TransferThread transferThread;

    private volatile boolean shutdown;

    private SplashOffscreenTarget splashTarget;

    private static final CopyOnWriteArrayList<ContextState> registeredStates = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<ContextState> tlState = ThreadLocal.withInitial(() -> {
        final ContextState st = new ContextState();
        for (int i = 0; i < ContextState.MAX_VERTEX_ATTRIBS; i++) st.attribDefaults[i * 4 + 3] = 1.0f;
        st.attribDefaults[4] = 1.0f; st.attribDefaults[5] = 1.0f;
        st.attribDefaults[6] = 1.0f;
        registeredStates.add(st);
        refreshSoleState();
        return st;
    });

    private static volatile ContextState soleState;

    private static void refreshSoleState() {
        soleState = registeredStates.size() == 1 ? registeredStates.get(0) : null;
    }

    private static ContextState s() {
        final ContextState st = soleState;
        if (st != null && st.owner == Thread.currentThread()) return st;
        return tlState.get();
    }

    private void markAllPipelineInputDirty() {
        for (ContextState st : registeredStates) st.pipeline.markInputDirty();
    }

    public static final int CTR_SLOT_WRITES = 0;
    public static final int CTR_SLOT_WRITES_ELIDED = 1;
    public static final int CTR_SSBO_BINDS = 2;
    public static final int CTR_KEY_RECOMPUTES = 3;
    public static final int CTR_FAST_PATH_HITS = 4;
    public static final int CTR_COUNT = 5;

    public static void takeContextCounters(int[] out) {
        Arrays.fill(out, 0);
        for (ContextState st : registeredStates) {
            out[CTR_SLOT_WRITES] += st.slotWrites;
            out[CTR_SLOT_WRITES_ELIDED] += st.slotWritesElided;
            out[CTR_SSBO_BINDS] += st.ssboBinds;
            out[CTR_KEY_RECOMPUTES] += st.pipeline.keyRecomputes;
            out[CTR_FAST_PATH_HITS] += st.pipeline.fastPathHits;
            st.slotWrites = 0;
            st.slotWritesElided = 0;
            st.ssboBinds = 0;
            st.pipeline.keyRecomputes = 0;
            st.pipeline.fastPathHits = 0;
        }
    }

    private void releaseUniformStagingAllThreads(ShaderManager.ProgramObject prog) {
        for (ContextState st : registeredStates) st.releaseUniformStaging(prog);
    }



    @Override public void onPersistentBufferWrite(int glId, long offset, long size) {
        persistentSync.onPersistentBufferWrite(glId, offset, size);
    }

    @Override public void onPostWindowCreate(long window) {
        buildCachedStrings();
        populateGLCapabilities();
    }

    @Override public void init() {
        final boolean lwjglDebugFlag = GLStateManager.getInitConfig() != null && GLStateManager.getInitConfig().isLwjglDebug();
        final boolean enableMarkers = lwjglDebugFlag || CaptureGate.enabledAtStartup();
        debugLabels = new DebugLabels(frameManager, enableMarkers);
        if (enableMarkers) {
            LOG.info("SDL-GPU debug markers ENABLED (lwjglDebug={}, angelica.debug.markers={}, captureTool={})", lwjglDebugFlag, SystemProperties.DEBUG_MARKERS, CaptureGate.TOOL_ATTACHED);
        }
        frameManager.setResourceManager(resourceManager);
        device.setLossDiagnostics(resourceManager::describeGpuState);
        frameManager.setBeforeEndCopyPassCallback(() -> resourceManager.flushBatchedUploads(frameManager.getCopyPass()));
        frameManager.setBeforeSubmitCallback(() -> {
            endComputeDispatchBatch();
            textureOps.drainPendingMipGen();
            drainDeferredPersistentRegions(s());
            debugLabels.popAutoDebugGroup(s());
        });
        frameManager.setAfterPresentCallback(() -> {
            frameManager.requestFlushOnAllRegisteredFrames();
            resourceManager.drainPendingFreeShadows();
        });
        GLSMHooks.LOADING_CHECKPOINT
            .addListener(event -> {
                if (Thread.currentThread() == GLStateManager.getMainThread()) {
                    if (!GLStateManager.isSplashComplete()) {
                        SplashDispatcher.tryDispatch(this, splashTarget);
                    } else if (splashTarget != null) {
                        SplashDispatcher.signalFrameReady();
                        SplashDispatcher.tryDispatch(this, splashTarget);
                        splashTarget.destroy(resourceManager);
                        splashTarget = null;
                        SplashDispatcher.reset();
                    }
                }
                if (event.requiresSync) {
                    frameManager.requestSyncFlushOnAllRegisteredFrames();
                } else {
                    frameManager.requestFlushOnAllRegisteredFrames();
                }
            });
        pipelineApplier.setDeferredUploadSink(this::enqueuePreCopied);
        frameManager.setPreRenderPassHook(() -> {
            final ContextState st = s();
            pipelineApplier.flushUniformBlocks(st);
            if (st.deferUploads) {
                drainDeferredPersistentRegions(st);
                return;
            }
            int mask = st.attribDefaultsDirtyMask;
            if (st.attribDefaultsRingBase != 0) mask = (1 << ContextState.MAX_VERTEX_ATTRIBS) - 1;
            if (mask == 0) return;
            final long dummyVBO = resourceManager.getOrCreateDummyVertexBuffer();
            if (dummyVBO == 0) return;
            st.attribDefaultsDirtyMask = 0;
            final long copyPass = frameManager.ensureCopyPass();
            resourceManager.uploadAttribDefaults(copyPass, st.attribDefaults, mask);
            frameManager.endCopyPassIfActive();
            if (st.attribDefaultsRingBase != 0) {
                st.attribDefaultsRingBase = 0;
                st.bumpAttribStateGen();
            }
        });
        DisplayEvents.addPreSwapchainInvalidatingChangeListener(SDLGPUGate.sdlGpuPreSwapchainInvalidatingCallback());
        GLSMConfig.expandVertexFormats = true;
        resourceManager.cachePreferredDepthFormats();
        LOG.warn("Swapchain texture format: 0x{} (12=B8G8R8A8_UNORM, 53=B8G8R8A8_SRGB, 4=R8G8B8A8_UNORM)", Integer.toHexString(device.getSwapchainTextureFormat()));
        cachedSwapchainFormatArray = new int[]{device.getSwapchainTextureFormat()};
        PipelineCache.setSwapchainFormats(cachedSwapchainFormatArray);
        pipelineStore.setBufferHandleResolver(resourceManager::getBufferHandle);
        pipelineStore.setVertexVariantResolver(shaderManager::getOrBuildVertexVariant);
        resourceManager.setBufferLivenessListener(this::markAllPipelineInputDirty);
        shaderManager.setUniformReleaseListener(this::releaseUniformStagingAllThreads);
        fenceTracker.setUnresolvedFenceFlush(this::midFrameFenceFlush);

        final int splashW = Math.max(1, Display.getWidth());
        final int splashH = Math.max(1, Display.getHeight());
        final int[] maxDesktop = device.getMaxDesktopSizePixels();
        final int splashTexW = Math.max(splashW, maxDesktop[0]);
        final int splashTexH = Math.max(splashH, maxDesktop[1]);
        splashTarget = new SplashOffscreenTarget();
        splashTarget.create(device, resourceManager, splashTexW, splashTexH, device.getSwapchainTextureFormat());
        SplashDispatcher.seedDrawnSize(splashW, splashH);
        LOG.info("Splash offscreen target {}x{} (window {}x{}, max desktop {}x{})", splashTexW, splashTexH, splashW, splashH, maxDesktop[0], maxDesktop[1]);

        Lwjgl3GLCapabilitiesShim.installOnCurrentThread(advertisedCapabilities());

        LOG.info("SDLGPURenderBackend initialized: renderer={}, version={}", cachedRenderer, cachedVersion);
    }

    private void buildCachedStrings() {
        String gpuName = device.getDeviceName();
        String driver = device.getDriverName();
        String version = device.getDriverVersion();

        StringBuilder renderer = new StringBuilder(gpuName);
        renderer.append(" (SDL GPU/").append(driver);
        if (!version.isEmpty()) {
            renderer.append(", ").append(version);
        }
        renderer.append(')');
        cachedRenderer = renderer.toString();

        cachedVersion = "4.6 (Core Profile Equivalent) [SDL GPU/" + driver + "]";

        String nameLower = gpuName.toLowerCase(Locale.ROOT);
        if (nameLower.startsWith("amd") || nameLower.contains("radeon")) {
            cachedVendor = "AMD";
        } else if (nameLower.startsWith("nvidia") || nameLower.contains("geforce") || nameLower.contains("quadro")) {
            cachedVendor = "NVIDIA Corporation";
        } else if (nameLower.startsWith("intel") || nameLower.contains("intel")) {
            cachedVendor = "Intel";
        } else if (nameLower.contains("mesa") || nameLower.contains("llvmpipe")) {
            cachedVendor = "Mesa";
        } else {
            cachedVendor = "SDL GPU";
        }
    }

    private static final String[] ADVERTISED_EXTENSIONS = {
        "GL_ARB_framebuffer_object", "GL_ARB_vertex_array_object", "GL_ARB_vertex_buffer_object",
        "GL_ARB_vertex_shader", "GL_ARB_fragment_shader", "GL_ARB_shader_objects",
        "GL_ARB_shading_language_100", "GL_ARB_texture_non_power_of_two", "GL_ARB_multitexture",
        "GL_ARB_sampler_objects", "GL_ARB_uniform_buffer_object", "GL_ARB_map_buffer_range",
        "GL_ARB_copy_buffer", "GL_ARB_copy_image", "GL_ARB_blend_func_extended", "GL_ARB_explicit_attrib_location",
        "GL_ARB_texture_storage", "GL_ARB_texture_swizzle", "GL_ARB_depth_clamp",
        "GL_ARB_seamless_cube_map", "GL_ARB_sync",
        "GL_ARB_draw_elements_base_vertex", "GL_ARB_instanced_arrays", "GL_ARB_draw_instanced",
        "GL_ARB_provoking_vertex", "GL_ARB_compute_shader", "GL_ARB_shader_storage_buffer_object",
        "GL_ARB_shader_image_load_store", "GL_ARB_buffer_storage", "GL_ARB_clear_texture",
        "GL_ARB_clear_buffer_object",
        "GL_EXT_framebuffer_object", "GL_EXT_framebuffer_blit", "GL_EXT_framebuffer_multisample",
        "GL_EXT_framebuffer_sRGB", "GL_EXT_texture_filter_anisotropic",
        "GL_EXT_texture_compression_s3tc", "GL_EXT_blend_func_separate",
        "GL_EXT_blend_equation_separate", "GL_EXT_texture_sRGB", "GL_EXT_shader_image_load_store",
        "GL_EXT_gpu_shader4", "GL_KHR_debug",
    };

    private static final String[] ADVERTISED_VERSIONS = {
        "OpenGL11", "OpenGL12", "OpenGL13", "OpenGL14", "OpenGL15",
        "OpenGL20", "OpenGL21",
        "OpenGL30", "OpenGL31", "OpenGL32", "OpenGL33",
        "OpenGL40", "OpenGL41", "OpenGL42", "OpenGL43", "OpenGL44", "OpenGL45", "OpenGL46",
    };

    public static Set<String> advertisedCapabilities() {
        final Set<String> caps = new HashSet<>(ADVERTISED_VERSIONS.length + ADVERTISED_EXTENSIONS.length);
        Collections.addAll(caps, ADVERTISED_VERSIONS);
        Collections.addAll(caps, ADVERTISED_EXTENSIONS);
        return caps;
    }

    private void populateGLCapabilities() {
        final Set<String> caps = advertisedCapabilities();
        GLCapabilitiesOverride.set(caps);
        cachedExtensions = String.join(" ", ADVERTISED_EXTENSIONS);

        LOG.info("Populated GL capabilities for SDL GPU backend");
    }

    @Override public void setVSyncEnabled(boolean enabled) {
        device.setVSyncEnabled(enabled);
    }

    @Override public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        if (splashTarget != null) {
            splashTarget.destroy(resourceManager);
            splashTarget = null;
        }
        SplashDispatcher.reset();
        final long dev = device.getDevice();
        if (transferThread != null) {
            transferThread.shutdown();
            try {
                transferThread.getThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            transferThread = null;
            resourceManager.setTransferThread(null);
        }
        SDL_WaitForGPUIdle(dev);
        frameManager.endFrame();
        readbackShadows.dispose();
        fenceTracker.dispose();
        frameManager.releaseAllRegisteredFrames();
        resourceManager.shutdownTexSamplerStates();
        for (ContextState st : registeredStates) {
            if (st.fanIndexBuffer != 0) {
                resourceManager.releaseBufferHandle(st.fanIndexBuffer);
                st.fanIndexBuffer = 0;
            }
            if (st.vboBindingsAddr != 0) { MemoryUtil.nmemFree(st.vboBindingsAddr); st.vboBindingsAddr = 0; }
            if (st.eboBinding != null) { st.eboBinding.free(); st.eboBinding = null; }
            if (st.cachedViewport != null) { st.cachedViewport.free(); st.cachedViewport = null; }
            if (st.cachedScissor != null) { st.cachedScissor.free(); st.cachedScissor = null; }
            if (st.cachedBlendColor != null) { st.cachedBlendColor.free(); st.cachedBlendColor = null; }
            st.fragSamplerBindings.free();
            st.vertSamplerBindings.free();
            if (st.mappedStagingBuffer != null) {
                MemoryUtil.memFree(st.mappedStagingBuffer);
            }
            clearMappedState(st);
        }
        registeredStates.clear();
        soleState = null;

        drawDispatch.clearWarnedState();
        pipelineStore.shutdown();
        shaderManager.shutdown();
        resourceManager.shutdown();
        device.shutdown();
    }

    @Override public boolean isAvailable() {
        return SystemProperties.USE_SDL_GPU && SDLGPUGate.isSDLGPUAvailable() && SDLGPUGate.isEngaged();
    }

    @Override public String getName() {
        return "SDL GPU (" + (device.getDriverName() != null ? device.getDriverName() : "not initialized") + ")";
    }

    @Override public boolean hasContext() {
        return device.getDevice() != 0;
    }

    @Override public boolean isCurrent() {
        return Thread.currentThread() == GLStateManager.getMainThread();
    }

    @Override public int getPriority() {
        return 150;
    }

    @Override public boolean isIndirectRequired() { return true; }
    @Override public boolean supportsGeometryShaders() { return false; }
    @Override public boolean framebufferCompletenessIsMeaningful() { return false; }
    @Override public boolean isSDLGPU() { return true; }

    @Override public int getMinGLSLVersion() {
        return 460;
    }

    @Override public void onRenderThreadReleased(Thread t) {
        if (frameManager.isFrameActive()) {
            frameManager.endFrame();
        }
        frameManager.releaseThreadState();
        registeredStates.remove(tlState.get());
        tlState.remove();
        refreshSoleState();
    }

    @Override public boolean handleMakeCurrent(Object drawable) {
        if (!isSDLManagedDrawable(drawable)) return false;
        Lwjgl3GLCapabilitiesShim.installOnCurrentThread(advertisedCapabilities());
        if (splashTarget != null && Thread.currentThread() != GLStateManager.getMainThread()) {
            s().boundFboId = splashTarget.fboId();
            frameManager.frame().swapchainUnavailable = true;
        }
        return true;
    }

    @Override public boolean handleReleaseContext(Object drawable) {
        if (isSDLManagedDrawable(drawable)) {
            Lwjgl3GLCapabilitiesShim.clearOnCurrentThread();
            onRenderThreadReleased(Thread.currentThread());
            return true;
        }
        return false;
    }

    private static boolean isSDLManagedDrawable(Object drawable) {
        if (drawable == null) return false;
        if (drawable instanceof SDLDrawable) return true;
        return SDLGPUDisplayBridge.isSdlSharedDrawable(drawable);
    }

    @Override public boolean handleSwapBuffers() {
        if (splashTarget != null && splashTarget.isFor(s()) && Thread.currentThread() != GLStateManager.getMainThread()) {
            endFrameUploadFlush();
            frameManager.endFrame();
            SplashDispatcher.signalFrameReady((int) s().viewportW, (int) s().viewportH);
            frameManager.beginFrame();
            beginFrameInit();
            frameManager.frame().swapchainUnavailable = true;
            return true;
        }
        frameManager.presentFrame();
        return true;
    }

    public void dispatchSplashBlit(SplashOffscreenTarget target) {
        if (shutdown || target == null) return;
        onFrameBegin();
        final FrameManager.FrameState f = frameManager.frame();
        if (!frameManager.ensureSwapchainAcquired(f)) {
            onFrameEnd();
            return;
        }
        frameManager.endCopyPassIfActive();
        frameManager.endRenderPassIfActive();
        final long drawnSize = SplashDispatcher.getDrawnSize();
        final int srcW = Math.max(1, Math.min((int) (drawnSize >> 32), target.width()));
        final int srcH = Math.max(1, Math.min((int) drawnSize, target.height()));
        try (var stack = MemoryStack.stackPush()) {
            final var info = SDL_GPUBlitInfo.calloc(stack);
            info.source(s -> s.texture(target.colorTexture()).x(0).y(0).w(srcW).h(srcH));
            info.destination(d -> d.texture(f.swapchainTexture).x(0).y(0).w(f.swapchainWidth).h(f.swapchainHeight));
            info.filter(SDL_GPU_FILTER_LINEAR);
            info.flip_mode(SDLSurface.SDL_FLIP_VERTICAL);
            SDL_BlitGPUTexture(f.commandBuffer, info);
        }
        f.swapchainUsedThisFrame = true;
        onFrameEnd();
    }

    @Override public void onFrameBegin() {
        if (shutdown) return;
        frameManager.beginFrame();
        beginFrameInit();
    }

    private void beginFrameInit() {
        if (!frameManager.isFrameActive()) return;
        final ContextState st = s();
        if (st.needsSeed) {
            st.needsSeed = false;
            GLStateManager.replayStateToBackend();
        }
        ResourceManager.setSingleThreadedReads(GLStateManager.isSplashComplete());
        st.clearedTexturesThisFrame.clear();
        st.pendingColorTextures.clear();
        st.pendingDepthTextures.clear();
        st.pendingSwapchainClear = false;
        st.uniformBlocks[0].flushedThisFrame = false;
        st.uniformBlocks[1].flushedThisFrame = false;
        st.fanIndexBufferOffset = 0;
        st.deferUploads = true;
        st.frameHighestEnqueuedSeq = TransferThread.currentSeq();
        st.ringAdvancesThisFrame = 0;
        if (transferThread != null) {
            transferThread.resetFrameStats();
        }
    }

    private void midFrameFenceFlush() {
        final ContextState st = s();
        drainDeferredPersistentRegions(st);
        pipelineApplier.flushAttribRingChunk(st);
        resourceManager.flushUploadArena();
        if (transferThread != null && st.frameHighestEnqueuedSeq > transferThread.getSubmittedSeq()) {
            transferThread.awaitSubmittedUpTo(st.frameHighestEnqueuedSeq);
        }
        frameManager.submitMidFrame();
        fenceTracker.resolvePendingFences();
    }

    private void requestUploadFlush() {
        if (transferThread != null) transferThread.requestFlushUpTo(s().frameHighestEnqueuedSeq);
    }

    private void endFrameUploadFlushNoWait() {
        final ContextState st = s();
        drainDeferredPersistentRegions(st);
        pipelineApplier.flushAttribRingChunk(st);
        st.deferUploads = false;
        resourceManager.flushUploadArena();
    }

    private void drainDeferredPersistentRegions(ContextState st) {
        if (!st.deferUploads) return;
        st.drawsSincePersistentDrain = 0;
        persistentSync.enqueueDirtyPersistentRegions();
    }

    private void awaitUploadFlush() {
        final ContextState st = s();
        if (transferThread != null && st.frameHighestEnqueuedSeq > transferThread.getSubmittedSeq()) {
            transferThread.awaitSubmittedUpTo(st.frameHighestEnqueuedSeq);
        }
    }

    private void endFrameUploadFlush() {
        endFrameUploadFlushNoWait();
        awaitUploadFlush();
    }

    private static boolean emptyFrameWarned;

    @Override public void onFrameEnd() {
        if (shutdown) return;
        requestUploadFlush();
        endFrameUploadFlushNoWait();

        final FrameState f = frameManager.frame();
        if (f.frameActive && !f.swapchainUsedThisFrame) {
            if (!emptyFrameWarned) {
                emptyFrameWarned = true;
                LOG.info("Frame {} ended without drawing to the swapchain; skipping present (further occurrences counted in sdl.emptyFrames)", f.frameNumber);
            }
            frameManager.markFrameEmpty(f);
        }

        awaitUploadFlush();
        frameManager.endFrame();
        fenceTracker.resolvePendingFences();
        resourceManager.flushDeferredReleases();
        resourceManager.recycleGpuBufferPool(frameManager.getFrameNumber());
    }

    @Override public void onPreSwapchainInvalidatingChange(SwapchainInvalidatingChange change) {
        endFrameUploadFlush();
        if (frameManager.isFrameActive()) {
            frameManager.endFrame();
        }
        SDL_WaitForGPUIdle(device.getDevice());
    }


    private void enqueueUpload(TransferThread.DeferredUpload upload) {
        if (shutdown) return;
        if (transferThread == null) {
            transferThread = new TransferThread(device, resourceManager);
            resourceManager.setTransferThread(transferThread);
        }
        transferThread.enqueue(upload);
        s().frameHighestEnqueuedSeq = upload.seq();
    }

    /** Wake the transfer thread once after a batch of enqueueUpload calls. */
    private void wakeTransferThread() {
        if (transferThread != null) transferThread.wake();
    }

    private static final int INLINE_UPLOAD_MAX_BYTES = 64 * 1024;

    private void enqueuePreCopied(ByteBuffer data, long dstHandle, long dstOffset, boolean cycle) {
        if (data.remaining() <= INLINE_UPLOAD_MAX_BYTES && resourceManager.arenaUpload(data, dstHandle, dstOffset, cycle)) return;
        final long dataSize = data.remaining();
        final long xfer = resourceManager.acquireTransferBufferThreadSafe(dataSize);
        if (xfer == 0) return;
        final long mapSize = resourceManager.getTransferBufferMapSize(dataSize);
        final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, true, mapSize);
        if (mapped != null) {
            final int prevPos = data.position();
            mapped.put(data);
            data.position(prevPos);
        }
        SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
        final long seq = TransferThread.nextSeq();
        enqueueUpload(TransferThread.PreCopiedUpload.acquire(xfer, dataSize, dstHandle, dstOffset, seq, cycle));
        wakeTransferThread();
    }


    @Override public String getTransferDebugInfo() {
        return transferThread != null ? transferThread.getDebugInfo() : null;
    }

    @Override public void enable(int cap) { setBoolCap(cap, true); }
    @Override public void enablei(int cap, int index) { setBoolCapIndexed(cap, index, true); }
    @Override public void disable(int cap) { setBoolCap(cap, false); }
    @Override public void disablei(int cap, int index) { setBoolCapIndexed(cap, index, false); }

    private void setBoolCapIndexed(int cap, int index, boolean on) {
        final ContextState cs = s();
        if (cap == GL11.GL_BLEND) {
            if (index < 0 || index >= ContextState.MAX_COLOR_ATTACHMENTS) return;
            if (cs.pipeline.blendEnabledPerAttachment[index] != on) {
                cs.pipeline.blendEnabledPerAttachment[index] = on;
                cs.pipeline.markOutputDirty();
            }
            return;
        }
        setBoolCap(cap, on);
    }

    private void setBoolCap(int cap, boolean on) {
        final ContextState cs = s();
        switch (cap) {
            case GL11.GL_BLEND -> {
                boolean changed = false;
                for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
                    if (cs.pipeline.blendEnabledPerAttachment[i] != on) {
                        cs.pipeline.blendEnabledPerAttachment[i] = on;
                        changed = true;
                    }
                }
                if (changed) cs.pipeline.markOutputDirty();
            }
            case GL11.GL_DEPTH_TEST -> {
                if (cs.pipeline.depthTestEnabled != on) { cs.pipeline.depthTestEnabled = on; cs.pipeline.markOutputDirty(); }
            }
            case GL11.GL_CULL_FACE -> {
                if (cs.pipeline.cullEnabled != on) { cs.pipeline.cullEnabled = on; cs.pipeline.markOutputDirty(); }
            }
            case GL11.GL_STENCIL_TEST -> {
                if (cs.pipeline.stencilTestEnabled != on) { cs.pipeline.stencilTestEnabled = on; cs.pipeline.markOutputDirty(); }
            }
            case GL11.GL_SCISSOR_TEST -> {
                final ContextState st = cs;
                st.scissorEnabled = on;
                st.scissorDirty = true;
            }
            case GL31.GL_PRIMITIVE_RESTART -> cs.primitiveRestartEnabled = on;
        }
    }

    @Override public void blendFunc(int sfactor, int dfactor) {
        blendFuncSeparate(sfactor, dfactor, sfactor, dfactor);
    }

    @Override public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        final ContextState cs = s();
        final int sc = FormatMap.mapBlendFactor(srcRGB);
        final int dc = FormatMap.mapBlendFactor(dstRGB);
        final int sa = FormatMap.mapBlendFactor(srcAlpha);
        final int da = FormatMap.mapBlendFactor(dstAlpha);
        if (sc == cs.pipeline.srcColorFactor && dc == cs.pipeline.dstColorFactor && sa == cs.pipeline.srcAlphaFactor && da == cs.pipeline.dstAlphaFactor) return;
        cs.pipeline.srcColorFactor = sc;
        cs.pipeline.dstColorFactor = dc;
        cs.pipeline.srcAlphaFactor = sa;
        cs.pipeline.dstAlphaFactor = da;
        cs.pipeline.markOutputDirty();
    }

    @Override public void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override public void blendEquation(int mode) {
        blendEquationSeparate(mode, mode);
    }

    @Override public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        final ContextState cs = s();
        final int co = FormatMap.mapBlendOp(modeRGB);
        final int ao = FormatMap.mapBlendOp(modeAlpha);
        if (co == cs.pipeline.colorBlendOp && ao == cs.pipeline.alphaBlendOp) return;
        cs.pipeline.colorBlendOp = co;
        cs.pipeline.alphaBlendOp = ao;
        cs.pipeline.markOutputDirty();
    }

    @Override public void blendColor(float red, float green, float blue, float alpha) {
        final ContextState cs = s();
        cs.blendColorR = red;
        cs.blendColorG = green;
        cs.blendColorB = blue;
        cs.blendColorA = alpha;
        cs.blendColorDirty = true;
    }

    @Override public void depthFunc(int func) {
        final ContextState cs = s();
        final int op = FormatMap.mapCompareOp(func);
        if (op == cs.pipeline.depthCompareOp) return;
        cs.pipeline.depthCompareOp = op;
        cs.pipeline.markOutputDirty();
    }

    @Override public void depthMask(boolean flag) {
        final ContextState cs = s();
        if (flag == cs.pipeline.depthWriteEnabled) return;
        cs.pipeline.depthWriteEnabled = flag;
        cs.pipeline.markOutputDirty();
    }

    @Override public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        final ContextState cs = s();
        int mask = 0;
        if (red) mask |= SDL_GPU_COLORCOMPONENT_R;
        if (green) mask |= SDL_GPU_COLORCOMPONENT_G;
        if (blue) mask |= SDL_GPU_COLORCOMPONENT_B;
        if (alpha) mask |= SDL_GPU_COLORCOMPONENT_A;
        if (mask == cs.pipeline.colorWriteMask) return;
        cs.pipeline.colorWriteMask = mask;
        cs.pipeline.markOutputDirty();
    }

    @Override public void cullFace(int mode) {
        final ContextState cs = s();
        final boolean newCullAll = (mode == GL11.GL_FRONT_AND_BACK);
        final int newMode = switch (mode) {
            case GL11.GL_BACK -> SDL_GPU_CULLMODE_BACK;
            case GL11.GL_FRONT -> SDL_GPU_CULLMODE_FRONT;
            case GL11.GL_FRONT_AND_BACK -> SDL_GPU_CULLMODE_BACK;
            default -> SDL_GPU_CULLMODE_BACK;
        };
        if (newCullAll == cs.pipeline.cullAll && newMode == cs.pipeline.cullFaceMode) return;
        cs.pipeline.cullAll = newCullAll;
        cs.pipeline.cullFaceMode = newMode;
        cs.pipeline.markOutputDirty();
    }

    @Override public void frontFace(int mode) {
        final ContextState cs = s();
        final int ff = mode == GL11.GL_CW
            ? SDL_GPU_FRONTFACE_CLOCKWISE
            : SDL_GPU_FRONTFACE_COUNTER_CLOCKWISE;
        if (ff == cs.pipeline.frontFace) return;
        cs.pipeline.frontFace = ff;
        cs.pipeline.markOutputDirty();
    }

    @Override public void polygonMode(int face, int mode) {
        final ContextState cs = s();
        final int fm = mode == GL11.GL_LINE ? SDL_GPU_FILLMODE_LINE : SDL_GPU_FILLMODE_FILL;
        if (fm == cs.pipeline.fillMode) return;
        cs.pipeline.fillMode = fm;
        cs.pipeline.markOutputDirty();
    }

    @Override public void polygonOffset(float factor, float units) {
        polygonOffsetClamp(factor, units, 0.0f);
    }

    @Override public void polygonOffsetClamp(float factor, float units, float clamp) {
        final ContextState cs = s();
        if (factor == cs.pipeline.depthBiasSlopeFactor && units == cs.pipeline.depthBiasConstant && clamp == cs.pipeline.depthBiasClamp) return;
        cs.pipeline.depthBiasSlopeFactor = factor;
        cs.pipeline.depthBiasConstant = units;
        cs.pipeline.depthBiasClamp = clamp;
        cs.pipeline.markOutputDirty();
    }

    @Override public void viewport(int x, int y, int width, int height) {
        final ContextState cs = s();
        cs.viewportX = x; cs.viewportY = y; cs.viewportW = width; cs.viewportH = height;
        cs.viewportDirty = true;
    }

    @Override public void depthRange(double nearVal, double farVal) {
        final ContextState cs = s();
        cs.viewportDepthNear = (float) nearVal;
        cs.viewportDepthFar = (float) farVal;
        cs.viewportDirty = true;
    }

    @Override public void scissor(int x, int y, int width, int height) {
        final ContextState cs = s();
        cs.scissorX = x; cs.scissorY = y; cs.scissorW = width; cs.scissorH = height;
        cs.scissorDirty = true;
    }

    @Override public void clearColor(float red, float green, float blue, float alpha) {
        final ContextState cs = s();
        cs.clearR = red; cs.clearG = green; cs.clearB = blue; cs.clearA = alpha;
    }

    @Override public void clearDepth(double depth) { s().depthClearValue = (float) depth; }
    @Override public void clearStencil(int s) {}

    @Override public void clear(int mask) {
        final ContextState cs = s();
        if (!frameManager.isFrameActive()) return;
        final boolean wantColor = (mask & GL11.GL_COLOR_BUFFER_BIT) != 0;
        final boolean wantDepth = (mask & GL11.GL_DEPTH_BUFFER_BIT) != 0;
        if (!wantColor && !wantDepth) return;

        final ContextState st = cs;

        // Swapchain path: just record pending state; ensureSwapchainRenderPass consumes it lazily.
        if (st.boundFboId == 0) {
            if (wantColor) {
                st.pendingSwapchainClear = true;
                st.pendingSwapchainR = st.clearR;
                st.pendingSwapchainG = st.clearG;
                st.pendingSwapchainB = st.clearB;
                st.pendingSwapchainA = st.clearA;
            }
            // swapchain has no depth attachment in this backend; ignore wantDepth
            return;
        }

        final FboState fbo = resourceManager.getFbo(st.boundFboId);
        if (fbo == null) return;

        boolean affectsActivePass = false;
        final boolean passActive = frameManager.isRenderPassActive();
        final long activeColor = passActive ? frameManager.getCurrentColorTarget() : 0;
        final long activeDepth = passActive ? frameManager.getCurrentDepthTarget() : 0;

        if (wantColor) {
            for (int db : fbo.drawBuffers) {
                if (db < 0 || db >= ContextState.MAX_COLOR_ATTACHMENTS) continue;
                final long tex = fbo.colorTextures[db];
                if (tex == 0) continue;
                FBOClearTracker.recordPendingColorClear(st, tex, st.clearR, st.clearG, st.clearB, st.clearA);
                if (passActive && tex == activeColor) affectsActivePass = true;
            }
        }
        if (wantDepth && fbo.depthTexture != 0) {
            FBOClearTracker.recordPendingDepthClear(st, fbo.depthTexture, cs.depthClearValue);
            if (passActive && fbo.depthTexture == activeDepth) affectsActivePass = true;
        }

        if (affectsActivePass) {
            frameManager.endRenderPassIfActive();
        }
    }

    @Override public void lineWidth(float width) { /* no-op */ }
    @Override public void pointSize(float size) { /* no-op */ }
    @Override public void logicOp(int opcode) { /* no-op */ }
    @Override public void hint(int target, int mode) { /* no-op */ }

    @Override public void flush() { /* no-op */ }
    @Override public void finish() {
        if (device.getDevice() != 0) {
            SDL_WaitForGPUIdle(device.getDevice());
        }
    }

    @Override public void drawArrays(int mode, int first, int count) {
        final FrameState f = frameManager.frame();
        if (!f.frameActive) { f.droppedDrawsThisFrame++; return; }
        final ContextState st = s();
        if (mode == GL11.GL_TRIANGLE_FAN && count >= 3) {
            drawDispatch.drawTriangleFanAsTriangleList(st, first, count);
            return;
        }
        drawDispatch.setPrimitiveTypeForDraw(st, FormatMap.mapPrimitiveType(mode));
        pipelineApplier.ensureRenderPass(st, f);
        if (f.renderPass == 0) { f.droppedDrawsThisFrame++; drawDispatch.warnDrawArraysNoRenderPass(mode, st.boundFboId); return; }
        if (!pipelineApplier.applyPipelineAndState(st, f)) { f.droppedDrawsThisFrame++; return; }
        SDL_DrawGPUPrimitives(f.renderPass, count, 1, first, 0);
    }

    @Override public void drawElements(int mode, int count, int type, long indices) {
        final ContextState st = s();
        final long rp = prepareIndexedDrawBind(st, mode, type, "drawElements");
        if (rp == 0) return;
        final int firstIndex = (int) (indices / FormatMap.indexElementSize(type));
        drawDispatch.issueIndexedDraw(st, rp, st.currentVao.elementBuffer, type, count, 1, firstIndex, 0);
    }

    @Override public void drawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        final ContextState st = s();
        final long rp = prepareIndexedDrawBind(st, mode, type, "drawElementsInstanced");
        if (rp == 0) return;
        final int firstIndex = (int) (indices / FormatMap.indexElementSize(type));
        drawDispatch.issueIndexedDraw(st, rp, st.currentVao.elementBuffer, type, count, primcount, firstIndex, 0);
    }


    @Override public void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        final ContextState st = s();
        final long rp = prepareIndexedDrawBind(st, mode, type, "drawElementsBaseVertex");
        if (rp == 0) return;
        final int firstIndex = (int) (indices / FormatMap.indexElementSize(type));
        drawDispatch.issueIndexedDraw(st, rp, st.currentVao.elementBuffer, type, count, 1, firstIndex, baseVertex);
    }

    private long prepareIndexedDrawBind(ContextState st, int mode, int type, String opName) {
        if (!drawDispatch.prepareIndexedDraw(st, mode, type)) return 0;
        final int ebo = st.currentVao.elementBuffer;
        final long eboHandle = resourceManager.getBufferHandle(ebo);
        if (eboHandle == 0) {
            LOG.warn("{}: EBO {} has no GPU handle, skipping draw", opName, ebo);
            return 0;
        }
        final long rp = frameManager.getRenderPass();
        drawDispatch.bindIndexBufferIfChanged(st, rp, eboHandle, FormatMap.mapIndexElementSize(type), 0);
        return rp;
    }

    @Override public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        if (drawcount <= 0) return;
        final ContextState st = s();
        final long rp = prepareIndexedDrawBind(st, mode, type, "multiDrawElementsBaseVertex");
        if (rp == 0) return;
        final int elementSize = FormatMap.indexElementSize(type);
        final int ebo = st.currentVao.elementBuffer;
        for (int i = 0; i < drawcount; i++) {
            final long offset = (long) i * Integer.BYTES;
            final int count = MemoryUtil.memGetInt(pCount + offset);
            if (count <= 0) continue;
            final long indices = MemoryUtil.memGetAddress(pIndices + (long) i * Pointer.POINTER_SIZE);
            drawDispatch.issueIndexedDraw(st, rp, ebo, type, count, 1, (int) (indices / elementSize), MemoryUtil.memGetInt(pBaseVertex + offset));
        }
    }

    @Override public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        final ContextState cs = s();
        if (drawcount <= 0) return;
        Tracy.beginZone(Z_SDL_INDIRECT_DRAW);
        try {
            final ContextState st = cs;
            final long rp = prepareIndexedDrawBind(st, mode, type, "multiDrawElementsIndirect");
            if (rp == 0) return;
            final long indirectHandle = resourceManager.getBufferHandle(st.boundIndirectBuffer);
            if (indirectHandle == 0) {
                LOG.warn("multiDrawElementsIndirect: indirect buffer {} has no GPU handle", st.boundIndirectBuffer);
                return;
            }
            if (stride != 0 && stride != 20) {
                LOG.warn("multiDrawElementsIndirect: non-standard stride {} (expected 0 or 20)", stride);
            }
            if (cs.primitiveRestartEnabled && !drawDispatch.restartMultiDrawWarned) {
                LOG.warn("multiDrawElementsIndirect with primitive restart is not supported on the SDL backend; "
                    + "sentinel indices in the EBO will be drawn as regular indices.");
                drawDispatch.restartMultiDrawWarned = true;
            }
            SDL_DrawGPUIndexedPrimitivesIndirect(rp, indirectHandle, (int) indirect, drawcount);
        } finally {
            Tracy.endZone();
        }
    }

    @Override public void drawBuffer(int mode) { /* render target config */ }

    @Override public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        computeBinder.executeComputeDispatch(s(), numGroupsX, numGroupsY, numGroupsZ, 0L, false);
    }

    @Override public void dispatchComputeIndirect(long offset) {
        final int indirectGlId = getBoundBuffer(GL40.GL_DRAW_INDIRECT_BUFFER);
        if (indirectGlId == 0) return;
        final long indirectHandle = resourceManager.getBufferHandle(indirectGlId);
        if (indirectHandle == 0) return;
        computeBinder.executeComputeDispatch(s(), 0, 0, 0, indirectHandle, true, (int) offset);
    }

    public void beginComputeDispatchBatch() {
        s().computeBatchRequested = true;
    }

    public void endComputeDispatchBatch() {
        final ContextState st = s();
        st.computeBatchRequested = false;
        computeBinder.endBatchPassIfOpen(st);
    }

    @Override public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        final int srcGlId = getBoundBuffer(readTarget);
        final int dstGlId = getBoundBuffer(writeTarget);
        if (srcGlId == 0 || dstGlId == 0 || size <= 0) return;
        resourceManager.dropArrayShadow(dstGlId);
        resourceManager.dropEboShadow(dstGlId);
        resourceManager.markBufferContentsDefined(dstGlId);
        readbackShadows.invalidate(dstGlId, writeOffset);

        if (s().deferUploads) {
            final long dstHandle = resourceManager.getBufferHandle(dstGlId);
            if (dstHandle != 0) {
                final long seq = TransferThread.nextSeq();
                deferredCopyOp.readOffset = readOffset;
                deferredCopyOp.size = size;
                deferredCopyOp.dstHandle = dstHandle;
                deferredCopyOp.writeOffset = writeOffset;
                deferredCopyOp.seq = seq;
                final boolean handled = resourceManager.enqueuePersistentCopyDeferred(srcGlId, dstGlId, seq, deferredCopyOp);
                if (handled) {
                    wakeTransferThread();
                    return;
                }
                final long srcHandle = resourceManager.getBufferHandle(srcGlId);
                if (srcHandle != 0) {
                    resourceManager.flushUploadArena();
                    enqueueUpload(TransferThread.GpuCopyUpload.acquire(srcHandle, dstHandle, readOffset, writeOffset, size, seq));
                    wakeTransferThread();
                    return;
                }
            }
        } else {
            final long dstHandle = resourceManager.getBufferHandle(dstGlId);
            if (dstHandle != 0 && frameManager.getCommandBuffer() != 0) {
                if (resourceManager.uploadFromPersistentMapping(srcGlId, readOffset, size,
                        dstHandle, writeOffset, dstGlId, frameManager.ensureCopyPass())) {
                    return;
                }
            } else if (dstHandle == 0) {
                LOG.warn("copyBufferSubData: dest buffer {} has no GPU handle", dstGlId);
                return;
            }
        }

        final long srcHandle = resourceManager.getBufferHandle(srcGlId);
        final long dstHandle = resourceManager.getBufferHandle(dstGlId);
        if (srcHandle == 0 || dstHandle == 0) {
            LOG.warn("copyBufferSubData: src={} (handle={}) dst={} (handle={})", srcGlId, srcHandle, dstGlId, dstHandle);
            return;
        }
        if (frameManager.getCommandBuffer() == 0) {
            FrameManager.warnDroppedOutsideFrame(droppedCopyWarned, "copyBufferSubData");
            droppedCopyWarned = true;
            return;
        }
        final long cp = frameManager.ensureCopyPass();
        if (cp == 0) return;
        if (resourceManager.getBufferGlUsage(dstGlId) == GL15.GL_STREAM_READ
                && readbackShadows.scheduleDownload(cp, srcHandle, readOffset, dstGlId, writeOffset, size)) {
            return;
        }
        try (var stack = MemoryStack.stackPush()) {
            final SDL_GPUBufferLocation src = SDL_GPUBufferLocation.calloc(stack).buffer(srcHandle).offset((int) readOffset);
            final SDL_GPUBufferLocation dst = SDL_GPUBufferLocation.calloc(stack).buffer(dstHandle).offset((int) writeOffset);
            SDL_CopyGPUBufferToBuffer(cp, src, dst, (int) size, false);
        }
    }

    @Override public int genTextures() { return resourceManager.genTexture(); }
    @Override public void genTextures(IntBuffer textures) {
        for (int i = 0; i < textures.remaining(); i++) {
            textures.put(textures.position() + i, resourceManager.genTexture());
        }
    }
    @Override public void deleteTextures(int texture) {
        fboClearTracker.scrubPendingClearsForTexture(s(), texture);
        resourceManager.deleteTexture(texture);
    }


    @Override public void bindTexture(int target, int texture) {
        final ContextState st = s();
        if (st.activeTextureUnit >= 0 && st.activeTextureUnit < st.boundTextures.length) {
            if (st.boundTextures[st.activeTextureUnit] != texture) {
                st.boundTextures[st.activeTextureUnit] = texture;
                st.samplerBindGen++;
            }
        }
    }
    @Override public void activeTexture(int texture) {
        final ContextState st = s();
        st.activeTextureUnit = texture - GL13.GL_TEXTURE0;
    }

    private static boolean canReuseTexture(ResourceManager.TextureMeta m, long handle, int target, int glFormat, int sdlFormat, int w, int h, int d, int levels) {
        return m != null
            && handle != 0
            && m.width() == w
            && m.height() == h
            && m.depth() == d
            && m.levels() == levels
            && m.glTarget() == target
            && m.glFormat() == glFormat
            && m.sdlFormat() == sdlFormat;
    }

    @Override public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        final ContextState st = s();
        final int glId = st.boundTextures[st.activeTextureUnit];
        if (glId == 0) return;

        if (level == 0) {
            final TextureSamplerState ss = resourceManager.getOrCreateTexSamplerState(glId);
            final int numLevels = ss.maxLevel >= 0 ? ss.maxLevel + 1 : PixelOps.defaultMipLevels(width, height);
            final boolean bgraSwizzle = format == GL12.GL_BGRA && (internalFormat == GL11.GL_RGBA || internalFormat == GL11.GL_RGBA8);
            final int targetSdlFormat = bgraSwizzle
                ? SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM
                : resourceManager.mapTextureFormat(internalFormat);
            if (!canReuseTexture(resourceManager.getTextureMeta(glId), resourceManager.getTextureHandle(glId),
                    target, internalFormat, targetSdlFormat, width, height, 1, numLevels)) {
                textureOps.releaseTextureForRealloc(st, glId);
                if (bgraSwizzle) {
                    resourceManager.createTextureWithSdlFormat(glId, target, SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, internalFormat, width, height, 1, numLevels);
                } else {
                    resourceManager.createTexture(glId, target, internalFormat, width, height, 1, numLevels);
                }
                resourceManager.refreshTextureReferences(glId);
            }
        }

        textureOps.uploadTextureRegion(st, glId, resourceManager.getTextureMeta(glId), resourceManager.getTextureHandle(glId), pixels, 0, 0, width, height, level, format, type);
    }
    @Override public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        texImage2D(target, level, internalFormat, width, height, border, format, type, pixels != null ? MemoryUtil.memByteBuffer(pixels) : (ByteBuffer) null);
    }
    @Override public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        texImage2D(target, level, internalFormat, width, height, border, format, type, pixels != null ? MemoryUtil.memByteBuffer(pixels) : (ByteBuffer) null);
    }
    @Override public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        texImage2D(target, level, internalFormat, width, height, border, format, type, pixels != null ? MemoryUtil.memByteBuffer(pixels) : (ByteBuffer) null);
    }
    @Override public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        final ContextState st = s();
        final int glId = st.boundTextures[st.activeTextureUnit];
        if (glId == 0) return;

        if (level == 0) {
            final int numLevels = 1;
            final int targetSdlFormat = resourceManager.mapTextureFormat(internalFormat);
            if (!canReuseTexture(resourceManager.getTextureMeta(glId), resourceManager.getTextureHandle(glId),
                    target, internalFormat, targetSdlFormat, width, height, depth, numLevels)) {
                textureOps.releaseTextureForRealloc(st, glId);
                resourceManager.createTexture(glId, target, internalFormat, width, height, depth, numLevels);
                resourceManager.refreshTextureReferences(glId);
            }
        }
        if (pixels == null) return;
        final long texHandle = resourceManager.getTextureHandle(glId);
        if (texHandle == 0 || frameManager.getCommandBuffer() == 0) return;
        final long cp = frameManager.ensureCopyPass();
        if (cp == 0) return;
        final ByteBuffer unpacked = PixelOps.applyUnpackPixelStore(pixels, width, height * depth, format, type, st.pixelStore);
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(glId);
        final ByteBuffer prepped = PixelOps.prepareUploadBuffer(format, meta.sdlFormat(), unpacked, width, height * depth);
        try {
            resourceManager.uploadToTexture3D(cp, prepped, texHandle, 0, 0, 0, width, height, depth, level);
        } finally {
            if (prepped != unpacked) MemoryUtil.memFree(prepped);
            if (unpacked != pixels) MemoryUtil.memFree(unpacked);
        }
    }
    @Override public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {
        texImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels != null ? MemoryUtil.memByteBuffer(pixels) : (ByteBuffer) null);
    }
    @Override public void texImage1D(int target, int level, int internalFormat, int width, int border, int format, int type, ByteBuffer pixels) {
        final ContextState st = s();
        final int glId = st.boundTextures[st.activeTextureUnit];
        if (glId == 0) return;

        if (level == 0) {
            final int numLevels = 1;
            final int targetSdlFormat = resourceManager.mapTextureFormat(internalFormat);
            if (!canReuseTexture(resourceManager.getTextureMeta(glId), resourceManager.getTextureHandle(glId),
                    target, internalFormat, targetSdlFormat, width, 1, 1, numLevels)) {
                textureOps.releaseTextureForRealloc(st, glId);
                resourceManager.createTexture(glId, target, internalFormat, width, 1, 1, numLevels);
                resourceManager.refreshTextureReferences(glId);
            }
        }
        if (pixels == null) return;
        textureOps.uploadTextureRegion(st, glId, resourceManager.getTextureMeta(glId), resourceManager.getTextureHandle(glId),
            pixels, 0, 0, width, 1, level, format, type);
    }
    @Override public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixelBufferOffset) {
        final ContextState st = s();
        final int glId = st.boundTextures[st.activeTextureUnit];
        if (glId == 0) return;

        if (level == 0) {
            final TextureSamplerState ss = resourceManager.getOrCreateTexSamplerState(glId);
            final int numLevels = ss.maxLevel >= 0 ? ss.maxLevel + 1 : PixelOps.defaultMipLevels(width, height);
            final int targetSdlFormat = resourceManager.mapTextureFormat(internalFormat);
            if (!canReuseTexture(resourceManager.getTextureMeta(glId), resourceManager.getTextureHandle(glId), target, internalFormat, targetSdlFormat, width, height, 1, numLevels)) {
                textureOps.releaseTextureForRealloc(st, glId);
                resourceManager.createTexture(glId, target, internalFormat, width, height, 1, numLevels);
                resourceManager.refreshTextureReferences(glId);
            }
        }

        if (st.boundPixelUnpackBuffer == 0) return;
        final ByteBuffer staging = resourceManager.getPboStaging(st.boundPixelUnpackBuffer);
        if (staging == null) return;
        final int offset = (int) pixelBufferOffset;
        final int dataLen = width * height * PixelOps.glPixelSize(format, type);
        if (offset + dataLen > staging.capacity()) return;
        staging.position(offset).limit(offset + dataLen);
        textureOps.uploadTextureRegion(st, glId, resourceManager.getTextureMeta(glId),
            resourceManager.getTextureHandle(glId),
            staging.slice(), 0, 0, width, height, level, format, type);
    }

    @Override public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        final ContextState st = s();
        final int glId = st.boundTextures[st.activeTextureUnit];
        if (glId == 0 || pixels == null) return;
        textureOps.uploadTextureRegion(st, glId, resourceManager.getTextureMeta(glId), resourceManager.getTextureHandle(glId), pixels, xoffset, yoffset, width, height, level, format, type);
    }
    @Override public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        if (pixels != null) texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, MemoryUtil.memByteBuffer(pixels));
    }
    @Override public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pboOffset) {
        final ContextState st = s();
        if (st.boundPixelUnpackBuffer == 0) return;
        final int glId = st.boundTextures[st.activeTextureUnit];
        if (glId == 0) return;
        final ByteBuffer staging = resourceManager.getPboStaging(st.boundPixelUnpackBuffer);
        if (staging == null) return;
        final int offset = (int) pboOffset;
        final int dataLen = width * height * PixelOps.glPixelSize(format, type);
        if (offset + dataLen > staging.capacity()) return;
        staging.position(offset).limit(offset + dataLen);
        textureOps.uploadTextureRegion(st, glId, resourceManager.getTextureMeta(glId),
            resourceManager.getTextureHandle(glId),
            staging.slice(), xoffset, yoffset, width, height, level, format, type);
    }
    @Override public void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        final ContextState cs = s();
        final int destGlId = cs.boundTextures[cs.activeTextureUnit];
        textureOps.copyTexSubImageImpl(cs,destGlId, level, xoffset, yoffset, x, y, width, height);
    }

    @Override public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        final ContextState cs = s();
        final int destGlId = cs.boundTextures[cs.activeTextureUnit];
        textureOps.copyTexSubImageImpl(cs, destGlId, level, 0, 0, x, y, width, height);
    }

    @Override public void texParameteri(int target, int pname, int param) {
        final ContextState cs = s();
        textureOps.texParameteri(cs, pname, param, cs.boundTextures[cs.activeTextureUnit]);
    }

    @Override public void texParameterf(int target, int pname, float param) {
        final ContextState cs = s();
        textureOps.texParameterf(cs, pname, param, cs.boundTextures[cs.activeTextureUnit]);
    }

    @Override public void texParameteriv(int target, int pname, IntBuffer params) {
        if (params != null && params.remaining() > 0) texParameteri(target, pname, params.get(0));
    }
    @Override public void texParameterfv(int target, int pname, FloatBuffer params) {
        if (params != null && params.remaining() > 0) texParameterf(target, pname, params.get(params.position()));
    }
    @Override public int getTexParameteri(int target, int pname) {
        final ContextState cs = s();
        return getTextureParameteri(cs.boundTextures[cs.activeTextureUnit], target, pname);
    }
    @Override public float getTexParameterf(int target, int pname) {
        final ContextState cs = s();
        return getTextureParameterf(cs.boundTextures[cs.activeTextureUnit], target, pname);
    }
    @Override public int getTexLevelParameteri(int target, int level, int pname) {
        final ContextState cs = s();
        final int glId = cs.boundTextures[cs.activeTextureUnit];
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(glId);
        if (meta == null) return 0;
        final int mipW = Math.max(1, meta.width() >> level);
        final int mipH = Math.max(1, meta.height() >> level);
        return switch (pname) {
            case GL11.GL_TEXTURE_WIDTH -> mipW;
            case GL11.GL_TEXTURE_HEIGHT -> mipH;
            case GL11.GL_TEXTURE_INTERNAL_FORMAT -> {
                final int stored = meta.glFormat();
                yield TextureInfoCache.isGenericCompressedInternalFormat(stored) ? GL11.GL_RGBA8 : stored;
            }
            default -> 0;
        };
    }
    @Override public void generateMipmap(int target) {
        final ContextState st = s();
        generateTextureMipmap(st.boundTextures[st.activeTextureUnit]);
    }
    @Override public void pixelStorei(int pname, int param) {
        final ContextState.PixelStoreState ps = s().pixelStore;
        switch (pname) {
            case GL11.GL_UNPACK_ALIGNMENT -> ps.unpackAlignment = param;
            case GL11.GL_PACK_ALIGNMENT -> ps.packAlignment = param;
            case GL11.GL_UNPACK_ROW_LENGTH -> ps.unpackRowLength = param;
            case GL11.GL_UNPACK_SKIP_PIXELS -> ps.unpackSkipPixels = param;
            case GL11.GL_UNPACK_SKIP_ROWS -> ps.unpackSkipRows = param;
            default -> { /* GL_PACK_* and GL_UNPACK_SWAP_BYTES etc are ignored */ }
        }
    }

    @Override public int genSamplers() { return resourceManager.genSampler(); }
    @Override public void deleteSamplers(int sampler) {
        resourceManager.deleteSamplerObject(sampler);
        final ContextState st = s();
        for (int i = 0; i < st.boundSamplerObjects.length; i++) {
            if (st.boundSamplerObjects[i] == sampler) {
                st.boundSamplerObjects[i] = 0;
                st.samplerBindGen++;
            }
        }
        resourceManager.deleteSampler(sampler);
    }
    @Override public void bindSampler(int unit, int sampler) {
        final ContextState st = s();
        if (unit >= 0 && unit < st.boundSamplerObjects.length) {
            if (st.boundSamplerObjects[unit] != sampler) {
                st.boundSamplerObjects[unit] = sampler;
                st.samplerBindGen++;
            }
        }
    }
    @Override public void samplerParameteri(int sampler, int pname, int param) {
        if (sampler == 0) return;
        final TextureSamplerState ss = resourceManager.getOrCreateSamplerObject(sampler);
        switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> ss.minFilter = param;
            case GL11.GL_TEXTURE_MAG_FILTER -> ss.magFilter = param;
            case GL11.GL_TEXTURE_WRAP_S -> ss.wrapS = param;
            case GL11.GL_TEXTURE_WRAP_T -> ss.wrapT = param;
            case GL12.GL_TEXTURE_WRAP_R -> ss.wrapR = param;
            case GL12.GL_TEXTURE_MIN_LOD -> ss.minLod = (float) param;
            case GL12.GL_TEXTURE_MAX_LOD -> ss.maxLod = (float) param;
            case GL14.GL_TEXTURE_COMPARE_MODE -> ss.compareMode = param;
            case GL14.GL_TEXTURE_COMPARE_FUNC -> ss.compareFunc = param;
            default -> { return; }
        }
        ss.sdlSampler = 0;
        s().samplerBindGen++;
    }
    @Override public void samplerParameterf(int sampler, int pname, float param) {
        if (sampler == 0) return;
        final TextureSamplerState ss = resourceManager.getOrCreateSamplerObject(sampler);
        switch (pname) {
            case GL12.GL_TEXTURE_MIN_LOD -> ss.minLod = param;
            case GL12.GL_TEXTURE_MAX_LOD -> ss.maxLod = param;
            case GL14.GL_TEXTURE_LOD_BIAS -> ss.lodBias = param;
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> ss.maxAnisotropy = param;
            default -> { samplerParameteri(sampler, pname, (int) param); return; }
        }
        ss.sdlSampler = 0;
        s().samplerBindGen++;
    }

    @Override public int genFramebuffers() {
        final int id = resourceManager.genFboId();
        resourceManager.createFbo(id);
        return id;
    }

    @Override public void deleteFramebuffers(int framebuffer) {
        final ContextState cs = s();
        resourceManager.deleteFbo(framebuffer);
        if (cs.boundFboId == framebuffer) cs.boundFboId = 0;
        if (cs.boundReadFboId == framebuffer) cs.boundReadFboId = 0;
    }

    @Override public void bindFramebuffer(int target, int framebuffer) {
        final ContextState cs = s();
        final boolean isRead = target == GL30.GL_READ_FRAMEBUFFER;
        final boolean isDraw = target == GL30.GL_DRAW_FRAMEBUFFER;
        final boolean isBoth = !isRead && !isDraw;

        if (isRead) {
            cs.boundReadFboId = framebuffer;
            return;
        }

        if (isBoth) {
            cs.boundReadFboId = framebuffer;
        }

        if (cs.boundFboId == framebuffer) return;
        final int oldFboId = cs.boundFboId;
        final boolean structurallyIdentical = oldFboId != 0 && framebuffer != 0 && fboClearTracker.fbosHaveSameAttachments(cs, oldFboId, framebuffer);
        cs.boundFboId = framebuffer;

        if (structurallyIdentical) {
            return;
        }

        if (framebuffer == 0) {
            cs.pipeline.setColorTargetFormats(cachedSwapchainFormatArray);
            cs.pipeline.setDrawBuffers(null);
            cs.pipeline.hasDepthTarget = false;
            cs.pipeline.depthTargetFormat = 0;
        } else {
            final FboState fbo = resourceManager.getFbo(framebuffer);
            if (fbo != null && fbo.getColorTexture() != 0) {
                fboClearTracker.updatePipelineCacheColorFormats(cs, fbo);
                cs.pipeline.hasDepthTarget = fbo.depthTexture != 0;
                cs.pipeline.depthTargetFormat = fbo.depthFormat;
            }
        }
        cs.pipeline.markOutputDirty();
    }

    @Override public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        final ContextState cs = s();
        if (cs.boundFboId == 0) return;
        final FboState fbo = resourceManager.getFbo(cs.boundFboId);
        if (fbo == null) return;

        final int colorIdx = attachment - GL30.GL_COLOR_ATTACHMENT0;
        if (colorIdx >= 0 && colorIdx < ContextState.MAX_COLOR_ATTACHMENTS) {
            if (texture == 0) {
                fbo.detachColor(colorIdx);
                fboClearTracker.updatePipelineCacheColorFormats(cs, fbo);
                cs.pipeline.markOutputDirty();
                return;
            }
            final long handle = resourceManager.ensureTextureUsage(texture, SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
            final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(texture);
            fbo.colorTextures[colorIdx] = handle;
            fbo.colorGlIds[colorIdx] = texture;
            resourceManager.markFboAttachment(texture);
            fbo.targetsDirty = true;
            if (meta != null) {
                fbo.colorFormats[colorIdx] = meta.sdlFormat();
                if (colorIdx == 0) {
                    fbo.width = meta.width();
                    fbo.height = meta.height();
                }
            }
            fbo.colorAttachmentCount = Math.max(fbo.colorAttachmentCount, colorIdx + 1);
            fbo.cachedFormatsDirty = true;
            fboClearTracker.updatePipelineCacheColorFormats(cs, fbo);
            cs.pipeline.markOutputDirty();
        } else if (attachment == GL30.GL_DEPTH_ATTACHMENT || attachment == GL30.GL_DEPTH_STENCIL_ATTACHMENT) {
            if (texture == 0) {
                fbo.detachDepth();
                cs.pipeline.hasDepthTarget = false;
                cs.pipeline.depthTargetFormat = 0;
                cs.pipeline.markOutputDirty();
                return;
            }
            final long handle = resourceManager.ensureTextureUsage(texture, SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET);
            final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(texture);
            fbo.depthTexture = handle;
            fbo.depthGlId = texture;
            resourceManager.markFboAttachment(texture);
            fbo.targetsDirty = true;
            if (meta != null) {
                fbo.depthFormat = meta.sdlFormat();
            }
            cs.pipeline.hasDepthTarget = true;
            cs.pipeline.depthTargetFormat = fbo.depthFormat;
            cs.pipeline.markOutputDirty();
        }
    }

    @Override public void framebufferTexture(int target, int attachment, int texture, int level) {
        framebufferTexture2D(target, attachment, GL11.GL_TEXTURE_2D, texture, level);
    }

    @Override public int checkFramebufferStatus(int target) { return GL30.GL_FRAMEBUFFER_COMPLETE; }
    @Override public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        final ContextState cs = s();
        blitNamedFramebuffer(cs.boundReadFboId, cs.boundFboId, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    private static final int[] EMPTY_DRAW_BUFFERS = new int[0];

    private static int classifyDrawBuffer(int buf) {
        if (buf >= GL30.GL_COLOR_ATTACHMENT0 && buf < GL30.GL_COLOR_ATTACHMENT0 + ContextState.MAX_COLOR_ATTACHMENTS) {
            return buf - GL30.GL_COLOR_ATTACHMENT0;
        }
        return -1;
    }

    @Override public void drawBuffers(int buffer) {
        final ContextState cs = s();
        if (cs.boundFboId == 0) return;
        final FboState fbo = resourceManager.getFbo(cs.boundFboId);
        if (fbo == null) return;

        if (buffer == GL11.GL_NONE) {
            if (fbo.drawBuffers.length == 0) return;
            frameManager.endRenderPassIfActive();
            fbo.drawBuffers = EMPTY_DRAW_BUFFERS;
            markDrawBuffersChanged(fbo, true);
            return;
        }
        final int idx = classifyDrawBuffer(buffer);
        if (fbo.drawBuffers.length == 1 && fbo.drawBuffers[0] == idx) return;
        frameManager.endRenderPassIfActive();
        if (fbo.drawBuffers.length == 1) {
            fbo.drawBuffers[0] = idx;
        } else {
            fbo.drawBuffers = new int[]{idx};
        }
        markDrawBuffersChanged(fbo, true);
    }
    @Override public void drawBuffers(IntBuffer bufs) {
        final ContextState cs = s();
        if (cs.boundFboId == 0) return;
        final FboState fbo = resourceManager.getFbo(cs.boundFboId);
        if (fbo == null) return;
        applyDrawBuffersFromIntBuffer(fbo, bufs, true);
    }

    private void applyDrawBuffersFromIntBuffer(FboState fbo, IntBuffer bufs, boolean isBound) {
        final int count = bufs.remaining();
        final int basePos = bufs.position();
        if (fbo.drawBuffers.length == count) {
            boolean same = true;
            for (int i = 0; i < count; i++) {
                if (fbo.drawBuffers[i] != classifyDrawBuffer(bufs.get(basePos + i))) { same = false; break; }
            }
            if (same) return;
        }
        if (isBound) frameManager.endRenderPassIfActive();
        final int[] indices = (fbo.drawBuffers.length == count) ? fbo.drawBuffers : new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = classifyDrawBuffer(bufs.get(basePos + i));
        }
        if (indices != fbo.drawBuffers) fbo.drawBuffers = indices;
        markDrawBuffersChanged(fbo, isBound);
    }

    private void markDrawBuffersChanged(FboState fbo, boolean isBound) {
        final ContextState cs = s();
        fbo.targetsDirty = true;
        fbo.cachedFormatsDirty = true;
        if (isBound) {
            fboClearTracker.updatePipelineCacheColorFormats(cs, fbo);
            cs.pipeline.markOutputDirty();
        }
    }
    @Override public void readBuffer(int mode) {
        final int fboId = s().boundReadFboId;
        if (fboId == 0) return;
        final FboState fbo = resourceManager.getFbo(fboId);
        if (fbo == null) return;
        if (mode >= GL30.GL_COLOR_ATTACHMENT0 && mode < GL30.GL_COLOR_ATTACHMENT0 + ContextState.MAX_COLOR_ATTACHMENTS) {
            fbo.readBufferIndex = mode - GL30.GL_COLOR_ATTACHMENT0;
        }
    }


    @Override public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        final ContextState cs = s();
        final long texHandle;
        final int srcSdlFormat;
        if (cs.boundReadFboId == 0) {
            texHandle = frameManager.getSwapchainTexture();
            srcSdlFormat = frameManager.getSwapchainFormat();
        } else {
            final FboState fbo = resourceManager.getFbo(cs.boundReadFboId);
            if (fbo == null) return;
            final int colorGlId = fbo.colorGlIds[fbo.readBufferIndex];
            texHandle = resourceManager.getTextureHandle(colorGlId);
            final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(colorGlId);
            srcSdlFormat = meta != null ? meta.sdlFormat() : SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        }
        textureOps.readbackTexture(texHandle, x, y, width, height, 0, pixels);
        pixels.rewind();
        PixelOps.postProcessReadback(pixels, width, height, format, srcSdlFormat, cs.boundReadFboId == 0);
    }
    @Override public void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        readPixels(x, y, width, height, format, type, MemoryUtil.memByteBuffer(pixels));
    }
    @Override public void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        readPixels(x, y, width, height, format, type, MemoryUtil.memByteBuffer(pixels));
    }
    @Override public void getTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        final ContextState cs = s();
        final int glId = cs.boundTextures[cs.activeTextureUnit];
        final long texHandle = resourceManager.getTextureHandle(glId);
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(glId);
        if (meta == null) return;
        textureOps.readbackTexture(texHandle, 0, 0, meta.width(), meta.height(), level, pixels);
        pixels.rewind();
        PixelOps.postProcessReadback(pixels, meta.width(), meta.height(), format, meta.sdlFormat(), false);
    }
    @Override public void getTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        getTexImage(target, level, format, type, MemoryUtil.memByteBuffer(pixels));
    }
    @Override public void getTexImage(int target, int level, int format, int type, long pixelBufferOffset) {
        final ContextState cs = s();
        final int pbo = cs.boundPixelPackBuffer;
        if (pbo == 0) return;
        final int glId = cs.boundTextures[cs.activeTextureUnit];
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(glId);
        if (meta == null) return;
        final ByteBuffer tmp = MemoryUtil.memAlloc(meta.width() * meta.height() * 4);
        try {
            getTexImage(target, level, format, type, tmp);
            final long handle = resourceManager.getBufferHandle(pbo);
            if (handle != 0 && frameManager.getCommandBuffer() != 0) {
                tmp.rewind();
                resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), tmp, handle, pixelBufferOffset, false);
                resourceManager.markBufferContentsDefined(pbo);
            }
        } finally {
            MemoryUtil.memFree(tmp);
        }
    }

    @Override public int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        final FboState fbo = resourceManager.getFbo(s().boundFboId);
        if (fbo == null) return 0;
        final int colorIdx = attachment - GL30.GL_COLOR_ATTACHMENT0;
        final boolean isColor = (colorIdx >= 0 && colorIdx < ContextState.MAX_COLOR_ATTACHMENTS);
        final boolean isDepth = (attachment == GL30.GL_DEPTH_ATTACHMENT || attachment == GL30.GL_DEPTH_STENCIL_ATTACHMENT);
        if (pname == GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) {
            return isColor ? fbo.colorGlIds[colorIdx] : isDepth ? fbo.depthGlId : 0;
        }
        if (pname == GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) {
            if (isColor && fbo.colorGlIds[colorIdx] != 0) return GL11.GL_TEXTURE;
            if (isDepth && fbo.depthGlId != 0) return GL11.GL_TEXTURE;
            return GL11.GL_NONE;
        }
        return 0;
    }

    @Override public int createShader(int type) { return shaderManager.createShader(type); }
    @Override public void deleteShader(int shader) { shaderManager.deleteShader(shader); }
    @Override public void shaderSource(int shader, CharSequence source) { shaderManager.shaderSource(shader, source); }
    @Override public void compileShader(int shader) { shaderManager.compileShader(shader); }
    @Override public boolean isShader(int obj)  { return shaderManager.isShader(obj); }
    @Override public boolean isProgram(int obj) { return shaderManager.isProgram(obj); }
    @Override public int createProgram() { return shaderManager.createProgram(); }
    @Override public void deleteProgram(int program) {
        debugLabels.onProgramDeleted(program);
        uniformEntriesCache.remove(program);
        if (s().boundProgram == program) {
            shaderManager.markProgramForDeletion(program);
            return;
        }
        shaderManager.releaseProgramFinal(program);
    }
    @Override public void attachShader(int program, int shader) { shaderManager.attachShader(program, shader); }
    @Override public void detachShader(int program, int shader) { /* No-op: SDL GPU shaders only needed at link time */ }
    @Override public void linkProgram(int program) {
        uniformEntriesCache.remove(program);
        shaderManager.linkProgram(program);
        final ContextState st = s();
        if (st.boundProgram == program) {
            refreshBoundProgramShaderHandles(program);
        }
    }

    private void refreshBoundProgramShaderHandles(int program) {
        final ContextState cs = s();
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog == null) {
            cs.pipeline.invalidateShaderHandles();
            return;
        }
        if (prog.sdlVertexShader == 0 && prog.vertexSpirv != null) {
            final var vr = prog.vertexResources;
            prog.sdlVertexShader = shaderManager.createSDLShader(prog.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, vr.numSamplers(), vr.numUBOs(), vr.numStorageBuffers(), vr.numStorageTextures());
        }
        if (prog.sdlFragmentShader == 0 && prog.fragmentSpirv != null) {
            final var fr = prog.fragmentResources;
            prog.sdlFragmentShader = shaderManager.createSDLShader(prog.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, fr.numSamplers(), fr.numUBOs(), fr.numStorageBuffers(), fr.numStorageTextures());
        }
        cs.pipeline.vertexShader = prog.sdlVertexShader;
        cs.pipeline.fragmentShader = prog.sdlFragmentShader;
        cs.pipeline.programId = program;
        cs.pipeline.maxFragOutputLocation = prog.maxFragOutputLocation;
        cs.pipeline.shaderInputMask = prog.vertexInputMask;
        cs.pipeline.shaderInputVecSize = prog.vertexInputVecSize;
        cs.pipeline.shaderInputBaseType = prog.vertexInputBaseType;
        cs.pipeline.shaderInputName = prog.vertexInputName;
        cs.pipeline.markShaderDirty();
    }
    @Override public void useProgram(int program) {
        final ContextState cs = s();
        final ContextState st = cs;
        final int prevBoundProgram = st.boundProgram;
        if (prevBoundProgram == program) return;
        debugLabels.updateAutoDebugGroup(st, program);

        if (prevBoundProgram != 0) {
            final ShaderManager.ProgramObject prevProg = shaderManager.getProgram(prevBoundProgram);
            if (prevProg != null && prevProg.deletePending) {
                shaderManager.releaseProgramFinal(prevBoundProgram);
            }
        }
        st.boundProgram = program;
        if (program == 0) {
            st.boundProgramObj = null;
            cs.pipeline.vertexShader = 0;
            cs.pipeline.fragmentShader = 0;
            cs.pipeline.programId = 0;
            cs.pipeline.markShaderDirty();
            return;
        }
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog == null) {
            st.boundProgramObj = null;
            if (missingProgramWarned.add(program)) {
                LOG.warn("useProgram({}): program object missing from shaderManager (was it deleted?); zeroing pipeline shaders, draws will silently skip", program);
            }
            cs.pipeline.vertexShader = 0;
            cs.pipeline.fragmentShader = 0;
            cs.pipeline.programId = 0;
            cs.pipeline.markShaderDirty();
            return;
        }
        st.boundProgramObj = prog;
        if (prog.sdlVertexShader == 0 && prog.vertexSpirv != null) {
            final var vr = prog.vertexResources;
            prog.sdlVertexShader = shaderManager.createSDLShader(prog.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, vr.numSamplers(), vr.numUBOs(), vr.numStorageBuffers(), vr.numStorageTextures());
        }
        if (prog.sdlFragmentShader == 0 && prog.fragmentSpirv != null) {
            final var fr = prog.fragmentResources;
            prog.sdlFragmentShader = shaderManager.createSDLShader(prog.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, fr.numSamplers(), fr.numUBOs(), fr.numStorageBuffers(), fr.numStorageTextures());
        }
        cs.pipeline.vertexShader = prog.sdlVertexShader;
        cs.pipeline.fragmentShader = prog.sdlFragmentShader;
        cs.pipeline.programId = program;
        cs.pipeline.maxFragOutputLocation = prog.maxFragOutputLocation;
        cs.pipeline.shaderInputMask = prog.vertexInputMask;
        cs.pipeline.shaderInputVecSize = prog.vertexInputVecSize;
        cs.pipeline.shaderInputBaseType = prog.vertexInputBaseType;
        cs.pipeline.shaderInputName = prog.vertexInputName;
        cs.pipeline.markShaderDirty();
    }

    @Override public boolean supportsDebugOutput() { return debugLabels.isEnabled(); }

    @Override
    public void debugMessageCallback(GLDebugMessageListener listener, long userParam) {
        DebugMessageRelay.setListener(listener, userParam, debugLabels.isEnabled());
    }

    @Override
    public int getDebugMessageLog(int count, IntBuffer sources, IntBuffer types, IntBuffer ids, IntBuffer severities, IntBuffer lengths, ByteBuffer messageLog) {
        return DebugMessageRelay.drain(count, sources, types, ids, severities, lengths, messageLog);
    }

    @Override
    public void objectLabel(int identifier, int name, CharSequence label) {
        debugLabels.objectLabel(identifier, name, label);
    }

    @Override
    public void pushDebugGroup(int source, int id, CharSequence message) {
        debugLabels.pushDebugGroup(message);
    }

    @Override
    public void popDebugGroup() {
        debugLabels.popDebugGroup();
    }

    @Override
    public void debugMessageInsert(int source, int type, int id, int severity, CharSequence message) {
        debugLabels.debugMessageInsert(message);
    }


    @Override public String getShaderInfoLog(int shader, int maxLength) { return shaderManager.getShaderInfoLog(shader); }
    @Override public void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        writeUtf8(shaderManager.getShaderInfoLog(shader), length, infoLog);
    }
    @Override public String getProgramInfoLog(int program, int maxLength) { return shaderManager.getProgramInfoLog(program); }
    @Override public void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        writeUtf8(shaderManager.getProgramInfoLog(program), length, infoLog);
    }

    private static void writeUtf8(String s, IntBuffer length, ByteBuffer out) {
        if (out == null) {
            if (length != null && length.remaining() > 0) length.put(0, 0);
            return;
        }
        if (s == null) return;
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        final int n = Math.min(bytes.length, out.remaining());
        out.put(bytes, 0, n);
        if (length != null && length.remaining() > 0) length.put(0, n);
    }
    @Override public int getShaderi(int shader, int pname) {
        return switch (pname) {
            case GL20.GL_COMPILE_STATUS  ->  shaderManager.isCompiled(shader) ? 1 : 0;
            case GL20.GL_SHADER_TYPE     ->  shaderManager.getShaderType(shader);
            case GL20.GL_INFO_LOG_LENGTH ->  shaderManager.getShaderInfoLog(shader).length();
            default -> 0;
        };
    }
    @Override public int getProgrami(int program, int pname) {
        return switch (pname) {
            case GL20.GL_LINK_STATUS -> shaderManager.isLinked(program) ? 1 : 0;
            case GL20.GL_VALIDATE_STATUS -> shaderManager.isLinked(program) ? 1 : 0;
            case GL20.GL_INFO_LOG_LENGTH -> shaderManager.getProgramInfoLog(program).length();
            case GL20.GL_ATTACHED_SHADERS -> shaderManager.getAttachedShaderCount(program);
            case GL20.GL_ACTIVE_UNIFORMS -> {
                final var entries = getOrBuildUniformEntries(program);
                yield entries != null ? entries.size() : 0;
            }
            case GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH -> {
                final var entries = getOrBuildUniformEntries(program);
                int max = 0;
                if (entries != null) for (UniformEntry e : entries) max = Math.max(max, e.name.length() + 1);
                yield max;
            }
            case GL20.GL_ACTIVE_ATTRIBUTES -> {
                final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
                yield prog != null ? prog.getActiveAttribNames().size() : 0;
            }
            case GL20.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH -> {
                final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
                int max = 0;
                if (prog != null) for (String n : prog.getActiveAttribNames()) max = Math.max(max, n.length() + 1);
                yield max;
            }
            default -> 0;
        };
    }

    private record UniformEntry(String name, int glType, int arrayLen) {}
    private final Int2ObjectOpenHashMap<List<UniformEntry>> uniformEntriesCache = new Int2ObjectOpenHashMap<>();

    private List<UniformEntry> getOrBuildUniformEntries(int program) {
        final List<UniformEntry> cached = uniformEntriesCache.get(program);
        if (cached != null) return cached;
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog == null || !prog.linked) return null;
        final ArrayList<UniformEntry> result = new ArrayList<>(prog.nameToLocation.size());
        for (Map.Entry<String, Integer> e : prog.nameToLocation.entrySet()) {
            final String name = e.getKey();
            final int loc = e.getValue();
            int glType;
            int arrayLen = 1;
            if (prog.allSamplerNames.contains(name)) {
                glType = GL20.GL_SAMPLER_2D;
            } else {
                ShaderManager.UniformMemberInfo umi = prog.anyBlockMemberInfo(loc);
                if (umi == null) umi = prog.locationToMemberInfo.get(loc);
                if (umi != null) {
                    glType = FormatMap.mapMemberInfoToGlType(umi);
                    arrayLen = Math.max(1, umi.arrayLen());
                } else {
                    glType = GL20.GL_FLOAT_VEC4;
                }
                if (prog.boolUniforms.contains(name)) {
                    glType = FormatMap.remapUnsignedIntToBool(glType);
                }
            }
            result.add(new UniformEntry(name, glType, arrayLen));
        }
        result.sort(Comparator.comparing(UniformEntry::name));
        final List<UniformEntry> immutable = Collections.unmodifiableList(result);
        uniformEntriesCache.put(program, immutable);
        return immutable;
    }

    @Override public void getAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        shaderManager.getAttachedShaders(program, count, shaders);
    }
    @Override public String getShaderSource(int shader, int maxLength) {
        final String src = shaderManager.getShaderSource(shader);
        return maxLength > 0 && src.length() > maxLength ? src.substring(0, maxLength) : src;
    }
    @Override public void getProgramiv(int program, int pname, IntBuffer params) {
        if (params.remaining() > 0) params.put(0, getProgrami(program, pname));
    }
    @Override public String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        final List<UniformEntry> entries = getOrBuildUniformEntries(program);
        if (entries == null || index < 0 || index >= entries.size()) {
            if (sizeType != null && sizeType.remaining() >= 2) { sizeType.put(0, 1); sizeType.put(1, GL20.GL_FLOAT_VEC4); }
            return "";
        }
        final UniformEntry e = entries.get(index);
        if (sizeType != null && sizeType.remaining() >= 2) {
            sizeType.put(0, e.arrayLen());
            sizeType.put(1, e.glType());
        }
        return maxLength > 0 && e.name().length() > maxLength ? e.name().substring(0, maxLength) : e.name();
    }
    @Override public void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        final List<UniformEntry> entries = getOrBuildUniformEntries(program);
        final UniformEntry e = (entries != null && index >= 0 && index < entries.size()) ? entries.get(index) : null;
        if (size != null && size.remaining() > 0) size.put(0, e != null ? e.arrayLen() : 1);
        if (type != null && type.remaining() > 0) type.put(0, e != null ? e.glType() : GL20.GL_FLOAT_VEC4);
        writeUtf8(e != null ? e.name() : "", length, name);
    }
    @Override public void bindAttribLocation(int program, int index, CharSequence name) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog != null) {
            prog.attribLocationBindings.put(name.toString(), index);
        }
    }
    @Override public int getAttribLocation(int program, CharSequence name) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog == null) return -1;
        if (prog.linked) return prog.resolvedAttribLocations.getInt(name.toString());
        return prog.attribLocationBindings.getInt(name.toString());
    }
    @Override public int getAttribLocation(int program, ByteBuffer name) {
        return getAttribLocation(program, MemoryUtil.memASCII(name));
    }
    @Override public int getUniformLocation(int program, CharSequence name) {
        return shaderManager.getUniformLocation(program, name.toString());
    }
    @Override public int getUniformLocation(int program, ByteBuffer name) {
        return shaderManager.getUniformLocation(program, MemoryUtil.memASCII(name));
    }
    @Override public void getShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        writeUtf8(shaderManager.getShaderSource(shader), length, source);
    }

    @Override public void uniform1i(int location, int v0) {
        if (location < 0) return;
        final ContextState st = s();
        final ShaderManager.ProgramObject prog = st.boundProgramObj;
        if (prog != null) {
            final String name = prog.locationToName.get(location);
            if (name != null && prog.allSamplerNames.contains(name)) {
                if (prog.samplerTextureUnits.put(name, v0) != v0) prog.samplerUnitsDirty = true;
                return;
            }
            if (name != null && prog.allImageNames.contains(name)) {
                prog.imageTextureUnits.put(name, v0);
                return;
            }
            if (debugLabels.isEnabled() && name == null && !prog.allSamplerNames.isEmpty() && v0 >= 0 && v0 <= 31) {
                LOG.warn("uniform1i: loc={} val={} prog={} -- location NOT in locationToName (samplers={})", location, v0, st.boundProgram, prog.allSamplerNames);
            }
        } else if (debugLabels.isEnabled() && v0 >= 0 && v0 <= 16) {
            LOG.warn("uniform1i: loc={} val={} -- NO program bound", location, v0);
        }
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 1);
        a[0] = Float.intBitsToFloat(v0);
        pipelineApplier.putUniform(st, location, a);
    }
    @Override public void uniform1f(int location, float v0) {
        if (location < 0) return;
        final ContextState st = s();
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 1);
        a[0] = v0;
        pipelineApplier.putUniform(st, location, a);
    }
    @Override public void uniform2f(int location, float v0, float v1) {
        if (location < 0) return;
        final ContextState st = s();
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 2);
        a[0] = v0; a[1] = v1;
        pipelineApplier.putUniform(st, location, a);
    }
    @Override public void uniform2i(int location, int v0, int v1) {
        if (location < 0) return;
        final ContextState st = s();
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 2);
        a[0] = Float.intBitsToFloat(v0); a[1] = Float.intBitsToFloat(v1);
        pipelineApplier.putUniform(st, location, a);
    }
    @Override public void uniform3f(int location, float v0, float v1, float v2) {
        if (location < 0) return;
        final ContextState st = s();
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 3);
        a[0] = v0; a[1] = v1; a[2] = v2;
        pipelineApplier.putUniform(st, location, a);
    }
    @Override public void uniform3i(int location, int v0, int v1, int v2) {
        if (location < 0) return;
        final ContextState st = s();
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 3);
        a[0] = Float.intBitsToFloat(v0); a[1] = Float.intBitsToFloat(v1); a[2] = Float.intBitsToFloat(v2);
        pipelineApplier.putUniform(st, location, a);
    }
    @Override public void uniform4f(int location, float v0, float v1, float v2, float v3) {
        if (location < 0) return;
        final ContextState st = s();
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 4);
        a[0] = v0; a[1] = v1; a[2] = v2; a[3] = v3;
        pipelineApplier.putUniform(st, location, a);
    }
    @Override public void uniform4i(int location, int v0, int v1, int v2, int v3) {
        if (location < 0) return;
        final ContextState st = s();
        final float[] a = pipelineApplier.reuseOrAlloc(st, location, 4);
        a[0] = Float.intBitsToFloat(v0); a[1] = Float.intBitsToFloat(v1);
        a[2] = Float.intBitsToFloat(v2); a[3] = Float.intBitsToFloat(v3);
        pipelineApplier.putUniform(st, location, a);
    }

    @Override public void uniform1fv(int location, FloatBuffer value) {
        pipelineApplier.putUniformFv(s(), location, value);
    }
    @Override public void uniform2(int location, FloatBuffer value) {
        pipelineApplier.putUniformFv(s(), location, value);
    }
    @Override public void uniform1iv(int location, IntBuffer value) { storeIntUniform(location, value); }
    @Override public void uniform2iv(int location, IntBuffer value) { storeIntUniform(location, value); }
    @Override public void uniform3iv(int location, IntBuffer value) { storeIntUniform(location, value); }
    @Override public void uniform4iv(int location, IntBuffer value) { storeIntUniform(location, value); }
    private void storeIntUniform(int location, IntBuffer value) {
        if (location < 0) return;
        final ContextState st = s();
        final int n = value.remaining();
        final int pos = value.position();
        final float[] v = pipelineApplier.reuseOrAlloc(st, location, n);
        for (int i = 0; i < n; i++) v[i] = Float.intBitsToFloat(value.get(pos + i));
        pipelineApplier.putUniform(st, location, v);
    }
    @Override public void uniform3(int location, FloatBuffer value) {
        pipelineApplier.putUniformFv(s(), location, value);
    }
    @Override public void uniform4(int location, FloatBuffer value) {
        pipelineApplier.putUniformFv(s(), location, value);
    }
    @Override public void uniform3fv(int location, float[] values) { pipelineApplier.putUniform(s(), location, values); }
    @Override public void uniform4fv(int location, float[] values) { pipelineApplier.putUniform(s(), location, values); }
    @Override public void uniformMatrix2(int location, boolean transpose, FloatBuffer value) {
        pipelineApplier.storeMatrix(s(), location, transpose, value, 2);
    }
    @Override public void uniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        pipelineApplier.storeMatrix(s(), location, transpose, value, 3);
    }
    @Override public void uniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        pipelineApplier.storeMatrix(s(), location, transpose, value, 4);
    }

    @Override public void vertexAttrib2f(int index, float v0, float v1) {
        if (index >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = s();
        final float[] a = st.attribDefaults;
        final int base = index * 4;
        if (a[base] != v0 || a[base+1] != v1 || a[base+2] != 0f || a[base+3] != 1f) {
            a[base] = v0; a[base+1] = v1; a[base+2] = 0f; a[base+3] = 1f;
            st.attribDefaultsDirtyMask |= (1 << index);
        }
    }
    @Override public void vertexAttrib3f(int index, float v0, float v1, float v2) {
        if (index >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = s();
        final float[] a = st.attribDefaults;
        final int base = index * 4;
        if (a[base] != v0 || a[base+1] != v1 || a[base+2] != v2 || a[base+3] != 1f) {
            a[base] = v0; a[base+1] = v1; a[base+2] = v2; a[base+3] = 1f;
            st.attribDefaultsDirtyMask |= (1 << index);
        }
    }
    @Override public void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        if (index >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = s();
        final float[] a = st.attribDefaults;
        final int base = index * 4;
        if (a[base] != v0 || a[base+1] != v1 || a[base+2] != v2 || a[base+3] != v3) {
            a[base] = v0; a[base+1] = v1; a[base+2] = v2; a[base+3] = v3;
            st.attribDefaultsDirtyMask |= (1 << index);
        }
    }

    @Override public int genBuffers() { return resourceManager.genBuffer(); }
    @Override public void deleteBuffers(int buffer) { dropMappingFor(buffer); readbackShadows.release(buffer); resourceManager.deleteBuffer(buffer); }
    @Override public void deleteBuffers(IntBuffer buffers) {
        for (int i = 0; i < buffers.remaining(); i++) {
            final int buffer = buffers.get(buffers.position() + i);
            dropMappingFor(buffer);
            readbackShadows.release(buffer);
            resourceManager.deleteBuffer(buffer);
        }
    }

    private static void dropMappingFor(int buffer) {
        final ContextState st = s();
        if (buffer == 0 || st.mappedBufferGlId != buffer || st.mappedStagingBuffer == null) return;
        MemoryUtil.memFree(st.mappedStagingBuffer);
        clearMappedState(st);
    }
    @Override public void bindBuffer(int target, int buffer) {
        final ContextState st = s();
        switch (target) {
            case GL15.GL_ARRAY_BUFFER -> st.boundArrayBuffer = buffer;
            case GL15.GL_ELEMENT_ARRAY_BUFFER -> {
                if (st.currentVao.elementBuffer != buffer) {
                    st.currentVao.elementBuffer = buffer;
                    st.bumpAttribStateGen();
                }
            }
            case GL31.GL_UNIFORM_BUFFER -> st.boundUniformBuffer = buffer;
            case GL30.GL_PIXEL_UNPACK_BUFFER -> st.boundPixelUnpackBuffer = buffer;
            case GL30.GL_PIXEL_PACK_BUFFER -> st.boundPixelPackBuffer = buffer;
            case GL40.GL_DRAW_INDIRECT_BUFFER -> st.boundIndirectBuffer = buffer;
            case GL31.GL_COPY_READ_BUFFER -> st.boundCopyReadBuffer = buffer;
            case GL31.GL_COPY_WRITE_BUFFER -> st.boundCopyWriteBuffer = buffer;
            case GL43.GL_SHADER_STORAGE_BUFFER -> {
                st.boundSSBO = buffer;
                resourceManager.markBufferContentsDefined(buffer);
            }
        }
    }
    @Override public void bindBufferBase(int target, int index, int buffer) {
        final ContextState cs = s();
        if (index >= 0 && index < ContextState.MAX_INDEXED_BUFFERS) {
            switch (target) {
                case GL31.GL_UNIFORM_BUFFER -> {
                    final ContextState st = cs;
                    st.boundUboByIndex[index] = buffer;
                    st.uboRangeOffset[index] = 0;
                    st.uboRangeSize[index] = 0;
                    st.uboRangeGen++;
                }
                case GL43.GL_SHADER_STORAGE_BUFFER -> {
                    cs.boundSsboByIndex[index] = buffer;
                    cs.ssboBindGen++;
                }
            }
        }
        bindBuffer(target, buffer);
    }

    @Override public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        final ContextState cs = s();
        if (index >= 0 && index < ContextState.MAX_INDEXED_BUFFERS) {
            switch (target) {
                case GL31.GL_UNIFORM_BUFFER -> {
                    final ContextState st = cs;
                    st.boundUboByIndex[index] = buffer;
                    st.uboRangeOffset[index] = (int) offset;
                    st.uboRangeSize[index] = (int) size;
                    st.uboRangeGen++;
                }
                case GL43.GL_SHADER_STORAGE_BUFFER -> {
                    cs.boundSsboByIndex[index] = buffer;
                    cs.ssboBindGen++;
                }
            }
        }
        bindBuffer(target, buffer);
    }

    @Override public int getUniformBlockIndex(int program, CharSequence name) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog == null) return -1;
        return (prog.vertexUboSize > 0 || prog.fragmentUboSize > 0) ? 0 : -1;
    }

    @Override public void uniformBlockBinding(int program, int blockIndex, int binding) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog != null) prog.externalUboBinding = binding;
    }

    @Override public int getIndexedBufferBinding(int target, int index) {
        final ContextState cs = s();
        if (index < 0 || index >= ContextState.MAX_INDEXED_BUFFERS) return 0;
        return switch (target) {
            case GL31.GL_UNIFORM_BUFFER -> cs.boundUboByIndex[index];
            case GL43.GL_SHADER_STORAGE_BUFFER -> cs.boundSsboByIndex[index];
            default -> 0;
        };
    }

    @Override public int getIntegerIndexed(int pname, int index) {
        final ContextState cs = s();
        if (index < 0 || index >= ContextState.MAX_IMAGE_UNITS) return unknownGetInteger(pname);
        return switch (pname) {
            case GL42.GL_IMAGE_BINDING_NAME -> cs.boundStorageTextureByUnit[index];
            case GL42.GL_IMAGE_BINDING_LEVEL -> cs.boundStorageTextureLevel[index];
            case GL42.GL_IMAGE_BINDING_LAYERED -> cs.boundStorageTextureLayered[index] ? GL11.GL_TRUE : GL11.GL_FALSE;
            case GL42.GL_IMAGE_BINDING_LAYER -> cs.boundStorageTextureLayer[index];
            case GL42.GL_IMAGE_BINDING_ACCESS -> cs.boundStorageTextureAccess[index];
            case GL42.GL_IMAGE_BINDING_FORMAT -> cs.boundStorageTextureFormat[index];
            default -> unknownGetInteger(pname);
        };
    }

    @Override public void bufferData(int target, long size, int usage) {
        final int glId = getBoundBuffer(target);
        if (glId == 0) return;
        resourceManager.recordBufferGlParams(glId, usage, BufferParams.MUTABLE_STORE);
        if (target == GL31.GL_UNIFORM_BUFFER) {
            if (resourceManager.getBufferSize(glId) == size) return;
            if (size > 0) {
                resourceManager.recordBufferSizeOnly(glId, size);
                final ByteBuffer shadow = resourceManager.getOrAllocUboShadow(glId, (int) size);
                shadow.position(0).limit((int) size);
                for (int i = 0; i < (int) size; i += 8) shadow.putLong(i, 0L);
            }
            return;
        }
        final int sdlUsage = FormatMap.mapBufferUsage(target);
        readbackShadows.release(glId);
        resourceManager.deleteBuffer(glId);
        resourceManager.createBuffer(glId, sdlUsage, size);
        if (target == GL15.GL_ARRAY_BUFFER) s().bumpAttribStateGen();
    }

    @Override public void bufferData(int target, ByteBuffer data, int usage) {
        final ContextState cs = s();
        final int glId = getBoundBuffer(target);
        if (glId == 0 || data == null) return;
        final long dataSize = data.remaining();
        resourceManager.recordBufferGlParams(glId, usage, BufferParams.MUTABLE_STORE);
        if (target == GL31.GL_UNIFORM_BUFFER) {
            if (dataSize > 0) {
                resourceManager.recordBufferSizeOnly(glId, dataSize);
                final ByteBuffer shadow = resourceManager.getOrAllocUboShadow(glId, (int) dataSize);
                final ByteBuffer src = data.duplicate();
                shadow.position(0).limit((int) dataSize);
                shadow.put(src);
                shadow.position(0).limit((int) dataSize);
            }
            return;
        }
        final int sdlUsage = FormatMap.mapBufferUsage(target);
        readbackShadows.release(glId);
        resourceManager.deleteBuffer(glId);
        final long handle = resourceManager.createBuffer(glId, sdlUsage, dataSize);
        if (target == GL15.GL_ARRAY_BUFFER) cs.bumpAttribStateGen();
        if (target == GL15.GL_ELEMENT_ARRAY_BUFFER && dataSize > 0
                && (cs.primitiveRestartEnabled || resourceManager.hasEboShadow(glId))) {
            persistentSync.mirrorEboShadow(glId, data, 0, (int) dataSize);
        }
        if (target == GL15.GL_ARRAY_BUFFER && dataSize > 0 && resourceManager.hasArrayShadow(glId)) {
            resourceManager.mirrorArrayShadowFull(glId, data);
        }
        if (handle != 0) {
            if (cs.deferUploads) {
                enqueuePreCopied(data, handle, 0, false);
            } else if (frameManager.getCommandBuffer() != 0) {
                resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), data, handle, 0, false);
            }
            resourceManager.markBufferContentsDefined(glId);
        }
    }


    @Override public void bufferData(int target, FloatBuffer data, int usage) {
        if (data != null) bufferData(target, MemoryUtil.memByteBuffer(data), usage);
    }

    @Override public void bufferData(int target, ShortBuffer data, int usage) {
        if (data != null) bufferData(target, MemoryUtil.memByteBuffer(data), usage);
    }

    @Override public void bufferData(int target, IntBuffer data, int usage) {
        if (data != null) bufferData(target, MemoryUtil.memByteBuffer(data), usage);
    }

    @Override public void bufferData(int target, DoubleBuffer data, int usage) {
        if (data != null) bufferData(target, MemoryUtil.memByteBuffer(data), usage);
    }

    private static final int STACK_PRIM_BYTES_MAX = 64 * 1024;

    @Override public void bufferData(int target, int[] data, int usage) {
        final int bytes = data.length * 4;
        if (bytes <= STACK_PRIM_BYTES_MAX) {
            try (var stack = MemoryStack.stackPush()) {
                final ByteBuffer buf = stack.malloc(bytes);
                buf.asIntBuffer().put(data);
                buf.position(0).limit(bytes);
                bufferData(target, buf, usage);
            }
            return;
        }
        final ByteBuffer buf = MemoryUtil.memAlloc(bytes);
        buf.asIntBuffer().put(data);
        bufferData(target, buf, usage);
        MemoryUtil.memFree(buf);
    }

    @Override public void bufferData(int target, float[] data, int usage) {
        final int bytes = data.length * 4;
        if (bytes <= STACK_PRIM_BYTES_MAX) {
            try (var stack = MemoryStack.stackPush()) {
                final ByteBuffer buf = stack.malloc(bytes);
                buf.asFloatBuffer().put(data);
                buf.position(0).limit(bytes);
                bufferData(target, buf, usage);
            }
            return;
        }
        final ByteBuffer buf = MemoryUtil.memAlloc(bytes);
        buf.asFloatBuffer().put(data);
        bufferData(target, buf, usage);
        MemoryUtil.memFree(buf);
    }

    @Override public void bufferSubData(int target, long offset, ByteBuffer data) {
        final ContextState cs = s();
        final int glId = getBoundBuffer(target);
        if (glId == 0 || data == null) return;
        if (target == GL31.GL_UNIFORM_BUFFER) {
            final ByteBuffer shadow = resourceManager.getUboShadow(glId);
            if (shadow != null) {
                final ByteBuffer src = data.duplicate();
                final int rem = src.remaining();
                shadow.position((int) offset).limit((int) offset + rem);
                shadow.put(src);
                shadow.position(0);
            }
            return;
        }
        if (target == GL15.GL_ELEMENT_ARRAY_BUFFER
                && (cs.primitiveRestartEnabled || resourceManager.hasEboShadow(glId))) {
            persistentSync.mirrorEboShadow(glId, data, (int) offset, data.remaining());
        }
        if (target == GL15.GL_ARRAY_BUFFER && resourceManager.hasArrayShadow(glId)) {
            resourceManager.mirrorArrayShadowRegion(glId, data, (int) offset);
        }
        final long handle = resourceManager.getBufferHandle(glId);
        if (handle != 0) {
            if (target == GL43.GL_SHADER_STORAGE_BUFFER) {
                if (frameManager.getCommandBuffer() != 0) {
                    resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), data, handle, offset, true);
                } else {
                    FrameManager.warnDroppedOutsideFrame(droppedSsboWriteWarned, "bufferSubData(GL_SHADER_STORAGE_BUFFER)");
                    droppedSsboWriteWarned = true;
                }
                resourceManager.markBufferContentsDefined(glId);
                return;
            }
            final boolean cycle = target == GL40.GL_DRAW_INDIRECT_BUFFER;
            if (cs.deferUploads) {
                enqueuePreCopied(data, handle, offset, cycle);
            } else if (frameManager.getCommandBuffer() != 0) {
                resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), data, handle, offset, cycle);
            }
            resourceManager.markBufferContentsDefined(glId);
        }
    }

    @Override public void bufferSubData(int target, long offset, ShortBuffer data) {
        if (data != null) bufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }

    @Override public void bufferSubData(int target, long offset, IntBuffer data) {
        if (data != null) bufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }

    @Override public void bufferSubData(int target, long offset, FloatBuffer data) {
        if (data != null) bufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }

    @Override public void bufferSubData(int target, long offset, DoubleBuffer data) {
        if (data != null) bufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }

    private int getBoundBuffer(int target) { return s().getBoundBuffer(target); }

    @Override public ByteBuffer mapBuffer(int target, int access) {
        final int glId = getBoundBuffer(target);
        final long size = resourceManager.getBufferSize(glId);
        if (size <= 0) {
            LOG.error("mapBuffer: buffer {} has no known size, cannot map", glId);
            return null;
        }
        return mapBufferRange(target, 0, size, BufferParams.accessEnumToBits(access));
    }

    @Override public ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        return mapBufferRange(target, 0, length, BufferParams.accessEnumToBits(access));
    }

    @Override public boolean unmapBuffer(int target) {
        final ContextState cs = s();
        final int glId = getBoundBuffer(target);

        final PersistentMapping pm = resourceManager.removePersistentMapping(glId);
        if (pm != null) {
            resourceManager.releasePersistentStaging(pm);
            return true;
        }

        final ContextState st = cs;
        if (st.mappedStagingBuffer == null) return false;

        final boolean writeMap = (st.mappedAccessFlags & GL30.GL_MAP_WRITE_BIT) != 0;

        if (target == GL30.GL_PIXEL_UNPACK_BUFFER) {
            st.mappedStagingBuffer.position(0).limit((int) st.mappedLength);
            final long gpuHandle = resourceManager.getBufferHandle(st.mappedBufferGlId);
            if (writeMap && gpuHandle != 0) {
                if (st.deferUploads) {
                    enqueuePreCopied(st.mappedStagingBuffer, gpuHandle, st.mappedOffset, false);
                } else if (frameManager.getCommandBuffer() != 0) {
                    resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), st.mappedStagingBuffer, gpuHandle, st.mappedOffset, false);
                }
                resourceManager.markBufferContentsDefined(st.mappedBufferGlId);
            }
            final ByteBuffer old = resourceManager.putPboStaging(glId, st.mappedStagingBuffer);
            if (old != null) MemoryUtil.memFree(old);
            clearMappedState(st);
            return true;
        }

        if (target == GL31.GL_UNIFORM_BUFFER) {
            final ByteBuffer shadow = resourceManager.getUboShadow(st.mappedBufferGlId);
            if (writeMap && shadow != null && st.mappedOffset + st.mappedLength <= shadow.capacity()) {
                st.mappedStagingBuffer.position(0).limit((int) st.mappedLength);
                shadow.position((int) st.mappedOffset).limit((int) (st.mappedOffset + st.mappedLength));
                shadow.put(st.mappedStagingBuffer);
                shadow.position(0).limit(shadow.capacity());
                resourceManager.markBufferContentsDefined(st.mappedBufferGlId);
            }
            MemoryUtil.memFree(st.mappedStagingBuffer);
            clearMappedState(st);
            return true;
        }

        if (!writeMap) {
            MemoryUtil.memFree(st.mappedStagingBuffer);
            clearMappedState(st);
            return true;
        }

        st.mappedStagingBuffer.position(0).limit((int) st.mappedLength);
        final long gpuHandle;
        if (st.mappedInvalidate) {
            final int sdlUsage = FormatMap.mapBufferUsage(0);
            final long glSize = Math.max(resourceManager.getBufferSize(st.mappedBufferGlId), st.mappedLength);
            final int glUsage = resourceManager.getBufferGlUsage(st.mappedBufferGlId);
            final int storageFlags = resourceManager.getBufferStorageFlags(st.mappedBufferGlId);
            resourceManager.deleteBuffer(st.mappedBufferGlId);
            gpuHandle = resourceManager.createBuffer(st.mappedBufferGlId, sdlUsage, glSize);
            resourceManager.recordBufferGlParams(st.mappedBufferGlId, glUsage, storageFlags);
            st.bumpAttribStateGen();
        } else {
            gpuHandle = resourceManager.getBufferHandle(st.mappedBufferGlId);
        }
        if (target == GL15.GL_ELEMENT_ARRAY_BUFFER
                && (cs.primitiveRestartEnabled || resourceManager.hasEboShadow(st.mappedBufferGlId))) {
            final int len = (int) st.mappedLength;
            st.mappedStagingBuffer.position(0).limit(len);
            persistentSync.mirrorEboShadow(st.mappedBufferGlId, st.mappedStagingBuffer, (int) st.mappedOffset, len);
            st.mappedStagingBuffer.position(0).limit(len);
        }
        if (target == GL15.GL_ARRAY_BUFFER && resourceManager.hasArrayShadow(st.mappedBufferGlId)) {
            final int len = (int) st.mappedLength;
            st.mappedStagingBuffer.position(0).limit(len);
            resourceManager.mirrorArrayShadowRegion(st.mappedBufferGlId, st.mappedStagingBuffer, (int) st.mappedOffset);
            st.mappedStagingBuffer.position(0).limit(len);
        }
        if (gpuHandle != 0) {
            if (st.deferUploads) {
                enqueuePreCopied(st.mappedStagingBuffer, gpuHandle, st.mappedOffset, false);
            } else if (frameManager.getCommandBuffer() != 0) {
                resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), st.mappedStagingBuffer, gpuHandle, st.mappedOffset, false);
            }
            resourceManager.markBufferContentsDefined(st.mappedBufferGlId);
        }

        MemoryUtil.memFree(st.mappedStagingBuffer);
        clearMappedState(st);
        return true;
    }

    private static void clearMappedState(ContextState st) {
        st.mappedStagingBuffer = null;
        st.mappedBufferGlId = 0;
        st.mappedOffset = 0;
        st.mappedLength = 0;
        st.mappedInvalidate = false;
        st.mappedAccessFlags = 0;
    }

    @Override public ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        final int glId = getBoundBuffer(target);
        if (glId == 0 || length <= 0) return null;

        final boolean persistent = (access & GL44.GL_MAP_PERSISTENT_BIT) != 0;

        if (persistent) {
            if (target == GL15.GL_ARRAY_BUFFER) resourceManager.dropArrayShadow(glId);
            final ByteBuffer staging = MemoryUtil.memCalloc((int) length);
            final PersistentMapping fresh = new PersistentMapping(staging, offset, length, access);
            final PersistentMapping prior = resourceManager.swapPersistentMapping(glId, fresh);
            if (prior != null) resourceManager.releasePersistentStaging(prior);
            return staging;
        }

        final ContextState st = s();
        if (st.mappedStagingBuffer != null) MemoryUtil.memFree(st.mappedStagingBuffer);
        st.mappedStagingBuffer = MemoryUtil.memAlloc((int) length);
        st.mappedBufferGlId = glId;
        st.mappedOffset = offset;
        st.mappedLength = length;
        st.mappedInvalidate = (access & GL30.GL_MAP_INVALIDATE_BUFFER_BIT) != 0;
        st.mappedAccessFlags = access;
        if ((access & (GL30.GL_MAP_INVALIDATE_BUFFER_BIT | GL30.GL_MAP_INVALIDATE_RANGE_BIT)) == 0) {
            seedMappedStaging(target, glId, offset, length, st.mappedStagingBuffer);
        }
        return st.mappedStagingBuffer;
    }

    private void seedMappedStaging(int target, int glId, long offset, long length, ByteBuffer staging) {
        final int len = (int) length;
        final long handle = resourceManager.getBufferHandle(glId);

        if (handle == 0 || resourceManager.hasUndefinedContents(glId)) {
            MemoryUtil.memSet(staging, 0);
            return;
        }

        final ByteBuffer direct = switch (target) {
            case GL31.GL_UNIFORM_BUFFER -> resourceManager.getUboShadow(glId);
            case GL30.GL_PIXEL_UNPACK_BUFFER -> resourceManager.getPboStaging(glId);
            default -> null;
        };
        if (direct != null && offset >= 0 && offset + len <= direct.capacity()) {
            serveFromShadow(direct, (int) offset, staging, len);
            staging.position(0).limit(len);
            return;
        }
        if (target == GL31.GL_UNIFORM_BUFFER) {
            MemoryUtil.memSet(staging, 0);
            return;
        }

        if (readBufferRangeCpuFirst(target, glId, handle, offset, staging, len)) {
            staging.position(0).limit(len);
            return;
        }
        final long size = resourceManager.getBufferSize(glId);
        if (offset < 0 || offset + len > size) {
            MemoryUtil.memSet(staging, 0);
            return;
        }
        downloadBufferBlocking(handle, (int) offset, len, staging);
        staging.position(0).limit(len);
    }

    private boolean readBufferRangeCpuFirst(int target, int glId, long handle, long offset, ByteBuffer dst, int rem) {
        if (target == GL15.GL_ARRAY_BUFFER) {
            final ByteBuffer shadow = resourceManager.getArrayShadow(glId);
            if (shadow != null && offset >= 0 && offset + rem <= shadow.capacity()) {
                serveFromShadow(shadow, (int) offset, dst, rem);
                return true;
            }
            if (resourceManager.isArrayShadowWanted(glId)) {
                final int fullSize = (int) resourceManager.getBufferSize(glId);
                if (resourceManager.canShadowArrayBuffer(fullSize) && offset >= 0 && offset + rem <= fullSize) {
                    final ByteBuffer full = resourceManager.getOrAllocArrayShadow(glId, fullSize);
                    full.position(0).limit(fullSize);
                    downloadBufferBlocking(handle, 0, fullSize, full);
                    full.position(0).limit(fullSize);
                    serveFromShadow(full, (int) offset, dst, rem);
                    return true;
                }
            } else {
                resourceManager.markArrayShadowWanted(glId);
            }
        } else if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) {
            final ByteBuffer shadow = resourceManager.getEboShadow(glId);
            if (shadow != null && offset >= 0 && offset + rem <= shadow.capacity()) {
                serveFromShadow(shadow, (int) offset, dst, rem);
                return true;
            }
            if (resourceManager.isEboShadowWanted(glId)) {
                final int fullSize = (int) resourceManager.getBufferSize(glId);
                if (fullSize > 0 && offset >= 0 && offset + rem <= fullSize) {
                    final ByteBuffer full = resourceManager.getOrAllocEboShadow(glId, fullSize);
                    full.position(0).limit(fullSize);
                    downloadBufferBlocking(handle, 0, fullSize, full);
                    full.position(0).limit(fullSize);
                    resourceManager.bumpEboShadowVersion(glId);
                    resourceManager.invalidateSplitCacheFor(glId);
                    serveFromShadow(full, (int) offset, dst, rem);
                    return true;
                }
            } else {
                resourceManager.markEboShadowWanted(glId);
            }
        }
        return false;
    }

    @Override public void flushMappedBufferRange(int target, long offset, long length) {
        final int glId = getBoundBuffer(target);
        if (glId == 0 || length <= 0) return;
        persistentSync.onPersistentBufferWrite(glId, offset, length);
    }

    @Override public void bufferStorage(int target, ByteBuffer data, int flags) {
        bufferData(target, data, GL15.GL_DYNAMIC_DRAW);
        recordImmutableStorage(target, flags);
    }

    @Override public void bufferStorage(int target, long size, int flags) {
        bufferData(target, size, GL15.GL_DYNAMIC_DRAW);
        recordImmutableStorage(target, flags);
    }

    private void recordImmutableStorage(int target, int flags) {
        final int glId = getBoundBuffer(target);
        if (glId != 0) resourceManager.recordBufferGlParams(glId, GL15.GL_DYNAMIC_DRAW, flags);
    }
    @Override public void getBufferSubData(int target, long offset, ByteBuffer data) {
        final int glId = getBoundBuffer(target);
        if (glId == 0 || data == null) return;
        final long handle = resourceManager.getBufferHandle(glId);
        if (handle == 0) return;
        final int rem = data.remaining();

        if (readbackShadows.serve(glId, offset, data, rem)) return;
        if (readBufferRangeCpuFirst(target, glId, handle, offset, data, rem)) return;

        downloadBufferBlocking(handle, (int) offset, rem, data);
    }

    private void downloadBufferBlocking(long handle, int offset, int size, ByteBuffer out) {
        flushDeferredUploadsForRead();
        frameManager.endCopyPassIfActive();
        frameManager.endRenderPassIfActive();
        final long cb = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (cb == 0) return;
        resourceManager.downloadFromBuffer(cb, handle, offset, size, out);
    }

    private void flushDeferredUploadsForRead() {
        final ContextState st = s();
        drainDeferredPersistentRegions(st);
        resourceManager.flushUploadArena();
        if (transferThread != null && st.frameHighestEnqueuedSeq > transferThread.getSubmittedSeq()) {
            transferThread.awaitSubmittedUpTo(st.frameHighestEnqueuedSeq);
        }
    }

    private static void serveFromShadow(ByteBuffer shadow, int srcOffset, ByteBuffer data, int rem) {
        final int dstPos = data.position();
        if (shadow.isDirect() && data.isDirect()) {
            MemoryUtil.memCopy(MemoryUtil.memAddress0(shadow) + srcOffset, MemoryUtil.memAddress(data), rem);
            data.position(dstPos + rem);
        } else {
            final ByteBuffer s = shadow.duplicate();
            s.position(srcOffset).limit(srcOffset + rem);
            data.put(s);
        }
    }
    @Override public void getBufferSubData(int target, long offset, ShortBuffer data) {
        getBufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }
    @Override public void getBufferSubData(int target, long offset, IntBuffer data) {
        getBufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }
    @Override public void getBufferSubData(int target, long offset, FloatBuffer data) {
        getBufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }
    @Override public void getBufferSubData(int target, long offset, DoubleBuffer data) {
        getBufferSubData(target, offset, MemoryUtil.memByteBuffer(data));
    }
    @Override public int getBufferParameteri(int target, int pname) {
        if (BufferParams.isUnknownPname(pname)) {
            GLStateManager.warnOnce("buffer-param-pname", "getBufferParameteri: unsupported pname 0x{} on target 0x{}",
                Integer.toHexString(pname), Integer.toHexString(target));
            return 0;
        }
        final int glId = getBoundBuffer(target);
        if (glId == 0) {
            GLStateManager.warnOnce("buffer-param-unbound", "getBufferParameteri: no buffer bound to target 0x{}", Integer.toHexString(target));
            return 0;
        }

        final PersistentMapping pm = resourceManager.getPersistentMapping(glId);
        final ContextState st = s();
        final boolean plainMapped = st.mappedStagingBuffer != null && st.mappedBufferGlId == glId;

        final boolean mapped;
        final int accessFlags;
        final long mapOffset;
        final long mapLength;
        if (pm != null) {
            mapped = true;
            accessFlags = pm.accessFlags;
            mapOffset = pm.offset;
            mapLength = pm.length;
        } else if (plainMapped) {
            mapped = true;
            accessFlags = st.mappedAccessFlags;
            mapOffset = st.mappedOffset;
            mapLength = st.mappedLength;
        } else {
            mapped = false;
            accessFlags = 0;
            mapOffset = 0;
            mapLength = 0;
        }

        return BufferParams.resolve(pname, resourceManager.getBufferSize(glId),
            resourceManager.getBufferGlUsage(glId), resourceManager.getBufferStorageFlags(glId),
            mapped, accessFlags, mapOffset, mapLength);
    }
    @Override public boolean isBuffer(int buffer) { return resourceManager.getBufferHandle(buffer) != 0 || resourceManager.getBufferSize(buffer) > 0; }
    @Override public boolean isTexture(int texture) { return resourceManager.getTextureHandle(texture) != 0 || resourceManager.getTextureMeta(texture) != null; }
    @Override public boolean isFramebuffer(int framebuffer) { return resourceManager.getFbo(framebuffer) != null; }
    @Override public boolean isRenderbuffer(int renderbuffer) { return resourceManager.getTextureHandle(renderbuffer) != 0 || resourceManager.getTextureMeta(renderbuffer) != null; }
    @Override public boolean isSampler(int sampler) { return resourceManager.getSamplerHandle(sampler) != 0 || resourceManager.getSamplerObject(sampler) != null; }
    @Override public boolean isQuery(int query) { return query > 0 && query < nextQueryId.get(); }

    private final AtomicInteger nextQueryId = new AtomicInteger(1);

    @Override public void genQueries(IntBuffer ids) {
        for (int i = ids.position(); i < ids.limit(); i++) ids.put(i, nextQueryId.getAndIncrement());
    }
    @Override public int genQueries() { return nextQueryId.getAndIncrement(); }
    @Override public void deleteQueries(int id) {}
    @Override public void beginQuery(int target, int id) { warnQueriesUnsupported(); }
    @Override public void endQuery(int target) {}
    @Override public void queryCounter(int id, int target) { warnQueriesUnsupported(); }
    @Override public void getQueryObjectui(int id, int pname, IntBuffer params) {
        params.put(params.position(), queryResult(pname));
    }
    @Override public int getQueryObjecti(int id, int pname) { return queryResult(pname); }
    @Override public long getQueryObjectui64(int id, int pname) { return queryResult(pname); }

    private static int queryResult(int pname) {
        return pname == GL15.GL_QUERY_RESULT_AVAILABLE ? 1 : 0;
    }

    private boolean queriesUnsupportedWarned;

    private void warnQueriesUnsupported() {
        if (queriesUnsupportedWarned) return;
        queriesUnsupportedWarned = true;
        LOG.warn("GL query objects are unsupported on the SDL GPU backend: SDL_gpu exposes no timestamp or occlusion query API.");
    }

    private final AtomicInteger nextVaoId = new AtomicInteger(1);

    @Override public int genVertexArrays() {
        final int id = nextVaoId.getAndIncrement();
        resourceManager.putVao(id, new ContextState.VAOState());
        return id;
    }
    @Override public void deleteVertexArrays(int array) {
        final ContextState cs = s();
        final ContextState st = cs;
        if (st.boundVAO == array) {
            st.boundVAO = 0;
            st.currentVao = st.defaultVaoState;
            st.bumpAttribStateGen();
            cs.pipeline.markInputDirty();
        }
        resourceManager.deleteVao(array);
    }
    @Override public void bindVertexArray(int array) {
        final ContextState cs = s();
        final ContextState st = cs;
        if (array == st.boundVAO) return;
        st.boundVAO = array;
        st.currentVao = (array == 0) ? st.defaultVaoState : resourceManager.getOrCreateVao(array);
        st.bumpAttribStateGen();
        cs.pipeline.markInputDirty();
    }
    @Override public boolean isVertexArray(int array) {
        return array != 0 && resourceManager.getVao(array) != null;
    }
    @Override public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        applyVertexAttribPointer(index, size, type, normalized, false, stride, pointer);
    }
    @Override public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        applyVertexAttribPointer(index, size, type, false, true, stride, pointer);
    }

    private void applyVertexAttribPointer(int index, int size, int type, boolean normalized, boolean isInteger, int stride, long pointer) {
        vertexAttribs.applyVertexAttribPointer(s(), index, size, type, normalized, isInteger, stride, pointer);
    }
    @Override public void enableVertexAttribArray(int index) {
        final ContextState cs = s();
        if (index < ContextState.MAX_VERTEX_ATTRIBS) {
            final ContextState st = cs;
            final ContextState.VAOState vao = st.currentVao;
            if (vao.attribEnabled[index]) return;
            vao.attribEnabled[index] = true;
            vao.attribEnabledMask |= (1 << index);
            st.bumpAttribStateGen();
            cs.pipeline.markInputDirty();
        }
    }
    @Override public void disableVertexAttribArray(int index) {
        final ContextState cs = s();
        if (index < ContextState.MAX_VERTEX_ATTRIBS) {
            final ContextState st = cs;
            final ContextState.VAOState vao = st.currentVao;
            if (!vao.attribEnabled[index]) return;
            vao.attribEnabled[index] = false;
            vao.attribEnabledMask &= ~(1 << index);
            st.bumpAttribStateGen();
            cs.pipeline.markInputDirty();
        }
    }
    @Override public void vertexAttribDivisor(int index, int divisor) {
        final ContextState cs = s();
        if (index >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = cs;
        final ContextState.VAOState vao = st.currentVao;
        final int b = vao.attribBinding[index];
        if (b < 0 || b >= ContextState.MAX_VERTEX_ATTRIBS) return;
        int effective = divisor;
        if (divisor > 1) {
            vertexAttribs.warnDivisorClamp(st.boundProgram, index, divisor);
            effective = 1;
        }
        if (vao.bindingDivisor[b] != effective) {
            vao.bindingDivisor[b] = effective;
            st.bumpAttribStateGen();
            cs.pipeline.markInputDirty();
        }
    }

    @Override public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        final ContextState cs = s();
        if (bindingindex < 0 || bindingindex >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = cs;
        final ContextState.VAOState vao = st.currentVao;
        if (vao.bindingBuffer[bindingindex] != buffer || vao.bindingOffset[bindingindex] != offset) {
            final int oldBuffer = vao.bindingBuffer[bindingindex];
            vao.bindingBuffer[bindingindex] = buffer;
            vao.bindingOffset[bindingindex] = offset;
            st.bumpAttribStateGen();
            if (oldBuffer != buffer) {
                cs.pipeline.markInputDirtyIfLivenessChanged(pipelineStore, oldBuffer, buffer);
            }
        }
        if (vao.bindingStride[bindingindex] != stride) {
            vao.bindingStride[bindingindex] = stride;
            cs.pipeline.markInputDirty();
        }
    }
    @Override public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        final ContextState cs = s();
        if (attribindex >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = cs;
        final ContextState.VAOState vao = st.currentVao;
        vao.attribSize[attribindex] = size;
        vao.attribType[attribindex] = type;
        vao.attribNormalized[attribindex] = normalized;
        vao.attribIsInteger[attribindex] = false;
        vao.attribRelativeOffset[attribindex] = relativeoffset;
        st.bumpAttribStateGen();
        cs.pipeline.markInputDirty();
    }
    @Override public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        final ContextState cs = s();
        if (attribindex >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = cs;
        final ContextState.VAOState vao = st.currentVao;
        vao.attribSize[attribindex] = size;
        vao.attribType[attribindex] = type;
        vao.attribNormalized[attribindex] = false;
        vao.attribIsInteger[attribindex] = true;
        vao.attribRelativeOffset[attribindex] = relativeoffset;
        st.bumpAttribStateGen();
        cs.pipeline.markInputDirty();
    }
    @Override public void vertexAttribBinding(int attribindex, int bindingindex) {
        final ContextState cs = s();
        if (attribindex >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState st = cs;
        final ContextState.VAOState vao = st.currentVao;
        if (vao.attribBinding[attribindex] != bindingindex) {
            vao.attribBinding[attribindex] = bindingindex;
            st.bumpAttribStateGen();
            cs.pipeline.markInputDirty();
        }
    }

    @Override public int createTextures(int target) { return resourceManager.genTexture(); }
    @Override public void bindTextureUnit(int unit, int texture) {
        final ContextState st = s();
        if (unit >= 0 && unit < st.boundTextures.length) {
            if (st.boundTextures[unit] != texture) {
                st.boundTextures[unit] = texture;
                st.samplerBindGen++;
            }
        }
    }
    @Override public void textureParameteri(int texture, int target, int pname, int param) {
        textureOps.texParameteri(s(), pname, param, texture);
    }
    @Override public void textureParameterf(int texture, int target, int pname, float param) {
        textureOps.texParameterf(s(), pname, param, texture);
    }
    @Override public void textureParameteriv(int texture, int target, int pname, IntBuffer params) {
        if (params.remaining() > 0) textureParameteri(texture, target, pname, params.get(params.position()));
    }
    @Override public void texStorage1D(int target, int levels, int internalFormat, int width) {
        final ContextState st = s();
        texStorageImpl(st, st.boundTextures[st.activeTextureUnit], target, internalFormat, width, 1, 1, levels);
    }
    @Override public void texStorage2D(int target, int levels, int internalFormat, int width, int height) {
        final ContextState st = s();
        texStorageImpl(st, st.boundTextures[st.activeTextureUnit], target, internalFormat, width, height, 1, levels);
    }
    @Override public void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth) {
        final ContextState st = s();
        texStorageImpl(st, st.boundTextures[st.activeTextureUnit], target, internalFormat, width, height, depth, levels);
    }
    @Override public void textureStorage1D(int texture, int levels, int internalFormat, int width) {
        texStorageImpl(s(), texture, GL11.GL_TEXTURE_2D, internalFormat, width, 1, 1, levels);
    }
    @Override public void textureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        texStorageImpl(s(), texture, GL11.GL_TEXTURE_2D, internalFormat, width, height, 1, levels);
    }
    @Override public void textureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth) {
        texStorageImpl(s(), texture, GL11.GL_TEXTURE_2D, internalFormat, width, height, depth, levels);
    }

    private void texStorageImpl(ContextState st, int glId, int target, int internalFormat, int w, int h, int d, int levels) {
        if (glId == 0) return;
        textureOps.releaseTextureForRealloc(st, glId);
        resourceManager.createTexture(glId, target, internalFormat, w, h, d, levels);
        resourceManager.refreshTextureReferences(glId);
    }
    @Override public void generateTextureMipmap(int texture) {
        final long texHandle = resourceManager.getTextureHandle(texture);
        if (texHandle == 0 || frameManager.getCommandBuffer() == 0) return;
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(texture);
        if (meta == null || meta.levels() <= 1) return;
        // SDL asserts if any pass (render or copy) is active when generating mipmaps.
        frameManager.endCopyPassIfActive();
        frameManager.endRenderPassIfActive();
        frameManager.noteMipGen();
        textureOps.clearPendingMipGen(texture);
        SDL_GenerateMipmapsForGPUTexture(frameManager.getCommandBuffer(), texHandle);
    }
    @Override public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        if (texture == 0) return;
        final ContextState st = s();
        if (level == 0) {
            final TextureSamplerState ss = resourceManager.getOrCreateTexSamplerState(texture);
            final int numLevels = ss.maxLevel >= 0 ? ss.maxLevel + 1 : PixelOps.defaultMipLevels(width, height);
            textureOps.releaseTextureForRealloc(st, texture);
            if (format == GL12.GL_BGRA && (internalformat == GL11.GL_RGBA || internalformat == GL11.GL_RGBA8)) {
                resourceManager.createTextureWithSdlFormat(texture, target,
                    SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM,
                    internalformat, width, height, 1, numLevels);
            } else {
                resourceManager.createTexture(texture, target, internalformat, width, height, 1, numLevels);
            }
            resourceManager.refreshTextureReferences(texture);
        }
        textureOps.uploadTextureRegion(st, texture, resourceManager.getTextureMeta(texture), resourceManager.getTextureHandle(texture), pixels, 0, 0, width, height, level, format, type);
    }
    @Override public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        if (pixels != null) textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, MemoryUtil.memByteBuffer(pixels));
        else textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, (ByteBuffer) null);
    }
    @Override public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        if (texture == 0 || pixels == null) return;
        textureOps.uploadTextureRegion(s(), texture, resourceManager.getTextureMeta(texture),
            resourceManager.getTextureHandle(texture),
            pixels, xoffset, yoffset, width, height, level, format, type);
    }
    @Override public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        if (pixels != null) textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, MemoryUtil.memByteBuffer(pixels));
    }
    @Override public int createFramebuffers() { return genFramebuffers(); }
    @Override public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        final ContextState cs = s();
        final int savedFbo = cs.boundFboId;
        cs.boundFboId = framebuffer;
        if (resourceManager.getFbo(framebuffer) == null) resourceManager.createFbo(framebuffer);
        framebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, level);
        cs.boundFboId = savedFbo;
        if (savedFbo != 0 && savedFbo != framebuffer) {
            final FboState currentFbo = resourceManager.getFbo(savedFbo);
            if (currentFbo != null) {
                fboClearTracker.updatePipelineCacheColorFormats(cs, currentFbo);
                cs.pipeline.markOutputDirty();
            }
        }
    }
    @Override public void namedFramebufferReadBuffer(int framebuffer, int mode) {
        final FboState fbo = resourceManager.getFbo(framebuffer);
        if (fbo == null) return;
        if (mode >= GL30.GL_COLOR_ATTACHMENT0 && mode < GL30.GL_COLOR_ATTACHMENT0 + ContextState.MAX_COLOR_ATTACHMENTS) {
            fbo.readBufferIndex = mode - GL30.GL_COLOR_ATTACHMENT0;
        }
    }
    @Override public void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs) {
        final FboState fbo = resourceManager.getFbo(framebuffer);
        if (fbo == null) return;
        applyDrawBuffersFromIntBuffer(fbo, bufs, framebuffer == s().boundFboId);
    }
    @Override public void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        final FboState srcFbo = resourceManager.getFbo(readFramebuffer);
        final FboState dstFbo = resourceManager.getFbo(drawFramebuffer);
        if (srcFbo == null || dstFbo == null) return;

        final boolean isDepth = (mask & GL11.GL_DEPTH_BUFFER_BIT) != 0;
        long srcTex = 0, dstTex = 0;
        int dstGlId = 0;
        if (isDepth) {
            srcTex = srcFbo.depthTexture;
            dstTex = dstFbo.depthTexture;
            dstGlId = dstFbo.depthGlId;
        } else if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            srcTex = srcFbo.colorTextures[srcFbo.readBufferIndex];
            dstTex = dstFbo.colorTextures[0];
            dstGlId = dstFbo.colorGlIds[0];
        }
        if (srcTex == 0 || dstTex == 0) return;

        final ContextState cs = s();
        fboClearTracker.materializePendingClearForTexture(cs, srcTex);
        if (!fboClearTracker.discardPendingClearIfFullyCovered(cs, dstTex, dstX0, dstY0, 0, dstX1 - dstX0, dstY1 - dstY0, resourceManager.getTextureMeta(dstGlId))) {
            fboClearTracker.materializePendingClearForTexture(cs, dstTex);
        }

        if (isDepth) {
            textureOps.copyTexture(srcTex, srcX0, srcY0, dstTex, dstX0, dstY0, srcX1 - srcX0, srcY1 - srcY0);
        } else {
            final ResourceManager.TextureMeta srcMeta = resourceManager.getTextureMeta(srcFbo.colorGlIds[srcFbo.readBufferIndex]);
            final ResourceManager.TextureMeta dstMeta = resourceManager.getTextureMeta(dstFbo.colorGlIds[0]);
            if (TextureOps.canCopyInsteadOfBlit(srcMeta, dstMeta, srcX1 - srcX0, srcY1 - srcY0, dstX1 - dstX0, dstY1 - dstY0)) {
                textureOps.copyTexture(srcTex, srcX0, srcY0, dstTex, dstX0, dstY0, srcX1 - srcX0, srcY1 - srcY0);
            } else {
                textureOps.blitTexture(srcTex, srcX0, srcY0, srcX1 - srcX0, srcY1 - srcY0, dstTex, dstX0, dstY0, dstX1 - dstX0, dstY1 - dstY0, filter);
            }
        }
    }
    @Override public int createBuffers() { return resourceManager.genBuffer(); }
    @Override public void namedBufferData(int buffer, long size, int usage) {
        if (buffer == 0 || size <= 0) return;
        final int sdlUsage = FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER);
        resourceManager.deleteBuffer(buffer);
        resourceManager.createBuffer(buffer, sdlUsage, size);
        resourceManager.recordBufferGlParams(buffer, usage, BufferParams.MUTABLE_STORE);
    }
    @Override public void namedBufferData(int buffer, ByteBuffer data, int usage) {
        if (buffer == 0 || data == null) return;
        final int sdlUsage = FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER);
        resourceManager.deleteBuffer(buffer);
        final long handle = resourceManager.createBuffer(buffer, sdlUsage, data.remaining());
        resourceManager.recordBufferGlParams(buffer, usage, BufferParams.MUTABLE_STORE);
        if (handle != 0 && frameManager.getCommandBuffer() != 0) {
            resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), data, handle, 0, false);
            resourceManager.markBufferContentsDefined(buffer);
        }
    }
    @Override public void namedBufferData(int buffer, FloatBuffer data, int usage) {
        if (data != null) namedBufferData(buffer, MemoryUtil.memByteBuffer(data), usage);
    }
    @Override public void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        if (buffer == 0 || data == null) return;
        final long handle = resourceManager.getBufferHandle(buffer);
        if (handle != 0 && frameManager.getCommandBuffer() != 0) {
            resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), data, handle, offset, false);
            resourceManager.markBufferContentsDefined(buffer);
        }
    }
    @Override public void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        textureOps.copyTexSubImageImpl(s(),texture, level, xoffset, yoffset, x, y, width, height);
    }


    @Override public int getTextureParameteri(int texture, int target, int pname) {
        final TextureSamplerState ss = resourceManager.getTexSamplerState(texture);
        if (ss == null) {
            return switch (pname) {
                case GL11.GL_TEXTURE_MIN_FILTER -> GL11.GL_NEAREST_MIPMAP_LINEAR;
                case GL11.GL_TEXTURE_MAG_FILTER -> GL11.GL_LINEAR;
                case GL11.GL_TEXTURE_WRAP_S, GL11.GL_TEXTURE_WRAP_T, GL12.GL_TEXTURE_WRAP_R -> GL11.GL_REPEAT;
                case GL12.GL_TEXTURE_MAX_LEVEL -> 1000;
                default -> 0;
            };
        }
        return switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> ss.minFilter;
            case GL11.GL_TEXTURE_MAG_FILTER -> ss.magFilter;
            case GL11.GL_TEXTURE_WRAP_S -> ss.wrapS;
            case GL11.GL_TEXTURE_WRAP_T -> ss.wrapT;
            case GL12.GL_TEXTURE_WRAP_R -> ss.wrapR;
            case GL12.GL_TEXTURE_MAX_LEVEL -> ss.maxLevel;
            default -> 0;
        };
    }
    @Override public float getTextureParameterf(int texture, int target, int pname) {
        final TextureSamplerState ss = resourceManager.getTexSamplerState(texture);
        if (ss == null) {
            return switch (pname) {
                case GL12.GL_TEXTURE_MIN_LOD -> -1000.0f;
                case GL12.GL_TEXTURE_MAX_LOD -> 1000.0f;
                case GL14.GL_TEXTURE_LOD_BIAS -> 0.0f;
                case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> 1.0f;
                default -> getTextureParameteri(texture, target, pname);
            };
        }
        return switch (pname) {
            case GL12.GL_TEXTURE_MIN_LOD -> ss.minLod;
            case GL12.GL_TEXTURE_MAX_LOD -> ss.maxLod;
            case GL14.GL_TEXTURE_LOD_BIAS -> ss.lodBias;
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> ss.maxAnisotropy;
            default -> getTextureParameteri(texture, target, pname);
        };
    }
    @Override public int getTextureLevelParameteri(int texture, int level, int pname) {
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(texture);
        if (meta == null) return 0;
        final int mipW = Math.max(1, meta.width() >> level);
        final int mipH = Math.max(1, meta.height() >> level);
        return switch (pname) {
            case GL11.GL_TEXTURE_WIDTH -> mipW;
            case GL11.GL_TEXTURE_HEIGHT -> mipH;
            case GL11.GL_TEXTURE_INTERNAL_FORMAT -> {
                final int stored = meta.glFormat();
                yield TextureInfoCache.isGenericCompressedInternalFormat(stored) ? GL11.GL_RGBA8 : stored;
            }
            default -> 0;
        };
    }

    @Override public int getInteger(int pname) {
        final ContextState cs = s();
        return switch (pname) {
            case GL11.GL_MAX_TEXTURE_SIZE -> 16384;
            case GL20.GL_MAX_TEXTURE_IMAGE_UNITS -> 17;
            case GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS -> 17;
            case GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS -> 32;
            case GL20.GL_MAX_VERTEX_ATTRIBS -> 16;
            case GL20.GL_MAX_DRAW_BUFFERS -> 8;
            case GL30.GL_MAX_COLOR_ATTACHMENTS -> 8;
            case GL15.GL_ARRAY_BUFFER_BINDING -> cs.boundArrayBuffer;
            case GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING -> cs.currentVao.elementBuffer;
            case GL20.GL_CURRENT_PROGRAM -> cs.boundProgram;
            case GL32.GL_CONTEXT_PROFILE_MASK -> GL32.GL_CONTEXT_CORE_PROFILE_BIT;
            case GL30.GL_MAJOR_VERSION -> 4;
            case GL30.GL_MINOR_VERSION -> 6;
            case GL30.GL_NUM_EXTENSIONS -> ADVERTISED_EXTENSIONS.length;
            case GL42.GL_MAX_IMAGE_UNITS -> 8;
            case GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS -> 8;
            case GL43.GL_MAX_DEBUG_GROUP_STACK_DEPTH -> 64;
            case GL43.GL_MAX_LABEL_LENGTH -> 256;
            case GL43.GL_MAX_DEBUG_MESSAGE_LENGTH -> 1024;
            case GL11.GL_DRAW_BUFFER -> drawBufferEnum(cs.boundFboId);
            case GL11.GL_READ_BUFFER -> readBufferEnum(cs.boundReadFboId);
            case GL11.GL_TEXTURE_BINDING_2D -> boundTextureOf(cs.activeTextureUnit, cs.boundTextures);
            default -> {
                final int limit = deviceLimit(pname);
                yield limit >= 0 ? limit : unknownGetInteger(pname);
            }
        };
    }

    static int bufferEnum(int fboId, int attachmentIndex) {
        if (fboId == 0) return GL11.GL_BACK;
        if (attachmentIndex < 0) return GL11.GL_NONE;
        return GL30.GL_COLOR_ATTACHMENT0 + attachmentIndex;
    }

    private int drawBufferEnum(int fboId) {
        if (fboId == 0) return bufferEnum(0, 0);
        final FboState fbo = resourceManager.getFbo(fboId);
        final int idx = (fbo == null || fbo.drawBuffers.length == 0) ? -1 : fbo.drawBuffers[0];
        return bufferEnum(fboId, idx);
    }

    private int readBufferEnum(int fboId) {
        if (fboId == 0) return bufferEnum(0, 0);
        final FboState fbo = resourceManager.getFbo(fboId);
        return bufferEnum(fboId, fbo == null ? -1 : fbo.readBufferIndex);
    }

    static int boundTextureOf(int unit, int[] boundTextures) {
        return (unit >= 0 && unit < boundTextures.length) ? boundTextures[unit] : 0;
    }

    static int deviceLimit(int pname) {
        return switch (pname) {
            case GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS, GL20.GL_MAX_FRAGMENT_UNIFORM_COMPONENTS -> MAX_UNIFORM_COMPONENTS_PER_STAGE;
            case GL30.GL_MAX_ARRAY_TEXTURE_LAYERS -> MAX_ARRAY_TEXTURE_LAYERS;
            default -> -1;
        };
    }

    static final int MAX_UNIFORM_COMPONENTS_PER_STAGE = 4096 / Float.BYTES;
    static final int MAX_ARRAY_TEXTURE_LAYERS = 256;

    private static int unknownGetInteger(int pname) {
        synchronized (UNKNOWN_GET_INTEGER_SEEN) {
            if (UNKNOWN_GET_INTEGER_SEEN.add(pname)) {
                LOG.warn("getInteger: unhandled pname 0x{}, returning 0; callers that save/restore this state will corrupt it", Integer.toHexString(pname));
            }
        }
        return 0;
    }

    private static final IntOpenHashSet UNKNOWN_GET_INTEGER_SEEN = new IntOpenHashSet();
    @Override public void getInteger(int pname, IntBuffer params) {
        if (params.remaining() > 0) params.put(params.position(), getInteger(pname));
    }
    @Override public float getFloat(int pname) {
        return switch (pname) {
            case GL11.GL_LINE_WIDTH -> 1.0f;
            case GL11.GL_POINT_SIZE -> 1.0f;
            default -> 0.0f;
        };
    }
    @Override public void getFloat(int pname, FloatBuffer params) {
        if (params == null || params.remaining() == 0) return;
        if (pname == GL12.GL_ALIASED_LINE_WIDTH_RANGE || pname == GL11.GL_LINE_WIDTH_RANGE) {
            if (params.remaining() >= 2) {
                params.put(params.position(), 1.0f);
                params.put(params.position() + 1, 1.0f);
            }
        } else {
            params.put(params.position(), getFloat(pname));
        }
    }
    @Override public boolean getBoolean(int pname) {
        final ContextState cs = s();
        return switch (pname) {
            case GL11.GL_DEPTH_TEST -> cs.pipeline.depthTestEnabled;
            case GL11.GL_BLEND -> cs.pipeline.blendEnabledPerAttachment[0];
            case GL11.GL_CULL_FACE -> cs.pipeline.cullEnabled;
            case GL11.GL_SCISSOR_TEST -> cs.scissorEnabled;
            case GL11.GL_STENCIL_TEST -> cs.pipeline.stencilTestEnabled;
            case GL11.GL_DEPTH_WRITEMASK -> cs.pipeline.depthWriteEnabled;
            default -> false;
        };
    }
    @Override public void getBoolean(int pname, ByteBuffer params) {
        if (params != null && params.remaining() > 0) params.put(params.position(), (byte)(getBoolean(pname) ? 1 : 0));
    }
    @Override public String getString(int pname) {
        if (pname == GL11.GL_RENDERER) return cachedRenderer;
        if (pname == GL11.GL_VENDOR) return cachedVendor;
        if (pname == GL11.GL_VERSION) return cachedVersion;
        if (pname == GL20.GL_SHADING_LANGUAGE_VERSION) return "4.60";
        if (pname == GL11.GL_EXTENSIONS) return cachedExtensions;
        return "";
    }
    @Override public String getStringi(int name, int index) {
        if (name == GL11.GL_EXTENSIONS && index >= 0 && index < ADVERTISED_EXTENSIONS.length) {
            return ADVERTISED_EXTENSIONS[index];
        }
        return "";
    }
    @Override public int getError() { return 0; }

    @Override public long fenceSync(int condition, int flags) { return fenceTracker.fenceSync(); }
    @Override public int clientWaitSync(long sync, int flags, long timeout) { return fenceTracker.clientWaitSync(sync, flags, timeout); }
    @Override public void deleteSync(long sync) { fenceTracker.deleteSync(sync); }
    public boolean isFenceSignaled(long sync) { return fenceTracker.isFenceSignaled(sync); }
    public int getSyncStatus(long sync) { return fenceTracker.getSyncStatus(sync); }
    @Override public void waitSync(long sync, int flags, long timeout) { fenceTracker.waitSync(sync); }
    @Override public int getSynci(long sync, int pname, IntBuffer length) {
        final int val = fenceTracker.getSyncStatus(sync);
        if (length != null && length.remaining() > 0) length.put(length.position(), val);
        return val;
    }

    private static final int CLEAR_CHUNK_BYTES = 64 * 1024;
    @Override public void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) {
        final int glId = getBoundBuffer(target);
        if (glId == 0 || size <= 0) return;
        final long handle = resourceManager.getBufferHandle(glId);
        if (handle == 0 || frameManager.getCommandBuffer() == 0) return;

        final int elemBytes = Math.max(1, PixelOps.glPixelSize(format, type));
        final int chunkBytes = (int) Math.min(size, CLEAR_CHUNK_BYTES);
        final int alignedChunk = (chunkBytes / elemBytes) * elemBytes;
        if (alignedChunk == 0) return;
        resourceManager.markBufferContentsDefined(glId);
        final ByteBuffer chunk = MemoryUtil.memAlloc(alignedChunk);
        try {
            for (int i = 0; i < alignedChunk; i++) {
                final byte v = (data != null && (i % elemBytes) < data.remaining())
                    ? data.get(data.position() + (i % elemBytes)) : 0;
                chunk.put(i, v);
            }
            chunk.position(0).limit(alignedChunk);
            long remaining = size;
            long writeOffset = offset;
            final boolean defer = s().deferUploads && target != GL43.GL_SHADER_STORAGE_BUFFER;
            while (remaining > 0) {
                final int n = (int) Math.min(remaining, alignedChunk);
                chunk.position(0).limit(n);
                if (defer) {
                    enqueuePreCopied(chunk, handle, writeOffset, false);
                } else {
                    resourceManager.uploadToBuffer(frameManager.ensureCopyPass(), chunk, handle, writeOffset, false);
                }
                remaining -= n;
                writeOffset += n;
            }
        } finally {
            MemoryUtil.memFree(chunk);
        }
    }

    @Override public void clearTexImage(int texture, int level, int format, int type) {
        final long texHandle = resourceManager.getTextureHandle(texture);
        if (texHandle == 0 || frameManager.getCommandBuffer() == 0) return;
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(texture);
        if (meta == null || level >= meta.levels()) return;
        final int mipW = Math.max(1, meta.width() >> level);
        final int mipH = Math.max(1, meta.height() >> level);
        final int mipD = Math.max(1, meta.depth() >> level);
        final int bpp = PixelOps.sdlFormatTexelBytes(meta.sdlFormat());
        if (mipW <= 0 || mipH <= 0 || bpp <= 0) return;

        if (mipD > 1 && PixelOps.isSdlFormatUnsignedInteger(meta.sdlFormat())
                && image3DClear.clear(texture, mipW, mipH, mipD)) {
            return;
        }

        // Volume-sized staging, so it is only reached for 3D formats the compute clear cannot address.
        final long totalBytes = (long) mipW * mipH * mipD * bpp;
        if (totalBytes <= 0 || totalBytes > Integer.MAX_VALUE) return;
        final long cp = frameManager.ensureCopyPass();
        if (cp == 0) return;
        final ByteBuffer zeros = MemoryUtil.memCalloc((int) totalBytes);
        try {
            if (mipD > 1) {
                resourceManager.uploadToTexture3D(cp, zeros, texHandle, 0, 0, 0, mipW, mipH, mipD, level);
            } else {
                resourceManager.uploadToTexture(cp, zeros, texHandle, 0, 0, mipW, mipH, level);
            }
        } finally {
            MemoryUtil.memFree(zeros);
        }
    }

    @Override public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        final ContextState cs = s();
        if (unit < 0 || unit >= ContextState.MAX_IMAGE_UNITS) return;
        if (texture != 0) {
            resourceManager.ensureTextureUsage(texture, imageUsageForAccess(access));
        }
        cs.boundStorageTextureByUnit[unit] = texture;
        cs.boundStorageTextureLevel[unit] = level;
        cs.boundStorageTextureLayered[unit] = layered;
        cs.boundStorageTextureLayer[unit] = layer;
        cs.boundStorageTextureAccess[unit] = access;
        cs.boundStorageTextureFormat[unit] = format;
    }

    public static int imageUsageForAccess(int access) {
        return access == GL15.GL_READ_ONLY ? SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ : SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE;
    }

    @Override public void memoryBarrier(int barriers) {
        // Graphics storage bindings are read-only on SDL_GPU; RW is compute-pass only
        frameManager.endCopyPassIfActive();
    }

    @Override public void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                            int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                            int srcWidth, int srcHeight, int srcDepth) {
        final long srcTex = resourceManager.getTextureHandle(srcName);
        final long dstTex = resourceManager.getTextureHandle(dstName);
        if (srcTex == 0 || dstTex == 0) return;
        final ContextState copySt = s();
        fboClearTracker.materializePendingClearForTexture(copySt, srcTex);
        if (dstZ != 0 || !fboClearTracker.discardPendingClearIfFullyCovered(copySt, dstTex, dstX, dstY, dstLevel, srcWidth, srcHeight, resourceManager.getTextureMeta(dstName))) {
            fboClearTracker.materializePendingClearForTexture(copySt, dstTex);
        }
        final long cp = frameManager.ensureCopyPass();
        if (cp == 0) return;
        try (var stack = MemoryStack.stackPush()) {
            final var src = SDL_GPUTextureLocation.calloc(stack)
                .texture(srcTex).mip_level(srcLevel).x(srcX).y(srcY).z(srcZ);
            final var dst = SDL_GPUTextureLocation.calloc(stack)
                .texture(dstTex).mip_level(dstLevel).x(dstX).y(dstY).z(dstZ);
            SDL_CopyGPUTextureToTexture(cp, src, dst, srcWidth, srcHeight, Math.max(1, srcDepth), false);
        }
    }

    @Override public void stencilFunc(int func, int ref, int mask) {
        final ContextState cs = s();
        final int op = FormatMap.mapCompareOp(func);
        final int cm = mask & 0xFF;
        cs.stencilRef = ref;
        if (op == cs.pipeline.stencilFrontCompareOp && op == cs.pipeline.stencilBackCompareOp && cm == cs.pipeline.stencilCompareMask)
            return;
        cs.pipeline.stencilFrontCompareOp = op;
        cs.pipeline.stencilBackCompareOp = op;
        cs.pipeline.stencilCompareMask = cm;
        cs.pipeline.markOutputDirty();
    }
    @Override public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        final ContextState cs = s();
        final int op = FormatMap.mapCompareOp(func);
        final int cm = mask & 0xFF;
        cs.stencilRef = ref;
        final boolean front = (face == GL11.GL_FRONT || face == GL20.GL_FRONT_AND_BACK);
        final boolean back = (face == GL11.GL_BACK || face == GL20.GL_FRONT_AND_BACK);
        final int newFront = front ? op : cs.pipeline.stencilFrontCompareOp;
        final int newBack = back ? op : cs.pipeline.stencilBackCompareOp;
        if (newFront == cs.pipeline.stencilFrontCompareOp && newBack == cs.pipeline.stencilBackCompareOp && cm == cs.pipeline.stencilCompareMask)
            return;
        cs.pipeline.stencilFrontCompareOp = newFront;
        cs.pipeline.stencilBackCompareOp = newBack;
        cs.pipeline.stencilCompareMask = cm;
        cs.pipeline.markOutputDirty();
    }
    @Override public void stencilOp(int sfail, int dpfail, int dppass) {
        final ContextState cs = s();
        final int sf = FormatMap.mapStencilOp(sfail);
        final int df = FormatMap.mapStencilOp(dpfail);
        final int pa = FormatMap.mapStencilOp(dppass);
        if (sf == cs.pipeline.stencilFrontFailOp && df == cs.pipeline.stencilFrontDepthFailOp && pa == cs.pipeline.stencilFrontPassOp && sf == cs.pipeline.stencilBackFailOp && df == cs.pipeline.stencilBackDepthFailOp && pa == cs.pipeline.stencilBackPassOp)
            return;
        cs.pipeline.stencilFrontFailOp = sf;
        cs.pipeline.stencilFrontDepthFailOp = df;
        cs.pipeline.stencilFrontPassOp = pa;
        cs.pipeline.stencilBackFailOp = sf;
        cs.pipeline.stencilBackDepthFailOp = df;
        cs.pipeline.stencilBackPassOp = pa;
        cs.pipeline.markOutputDirty();
    }
    @Override public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        final ContextState cs = s();
        final int sf = FormatMap.mapStencilOp(sfail);
        final int df = FormatMap.mapStencilOp(dpfail);
        final int pa = FormatMap.mapStencilOp(dppass);
        final boolean front = (face == GL11.GL_FRONT || face == GL20.GL_FRONT_AND_BACK);
        final boolean back = (face == GL11.GL_BACK || face == GL20.GL_FRONT_AND_BACK);
        final int nFFail = front ? sf : cs.pipeline.stencilFrontFailOp;
        final int nFDFail = front ? df : cs.pipeline.stencilFrontDepthFailOp;
        final int nFPass = front ? pa : cs.pipeline.stencilFrontPassOp;
        final int nBFail = back ? sf : cs.pipeline.stencilBackFailOp;
        final int nBDFail = back ? df : cs.pipeline.stencilBackDepthFailOp;
        final int nBPass = back ? pa : cs.pipeline.stencilBackPassOp;
        if (nFFail == cs.pipeline.stencilFrontFailOp && nFDFail == cs.pipeline.stencilFrontDepthFailOp && nFPass == cs.pipeline.stencilFrontPassOp && nBFail == cs.pipeline.stencilBackFailOp && nBDFail == cs.pipeline.stencilBackDepthFailOp && nBPass == cs.pipeline.stencilBackPassOp)
            return;
        cs.pipeline.stencilFrontFailOp = nFFail;
        cs.pipeline.stencilFrontDepthFailOp = nFDFail;
        cs.pipeline.stencilFrontPassOp = nFPass;
        cs.pipeline.stencilBackFailOp = nBFail;
        cs.pipeline.stencilBackDepthFailOp = nBDFail;
        cs.pipeline.stencilBackPassOp = nBPass;
        cs.pipeline.markOutputDirty();
    }
    @Override public void stencilMask(int mask) {
        final ContextState cs = s();
        final int wm = mask & 0xFF;
        if (wm == cs.pipeline.stencilWriteMask) return;
        cs.pipeline.stencilWriteMask = wm;
        cs.pipeline.markOutputDirty();
    }
    @Override public void stencilMaskSeparate(int face, int mask) {
        final ContextState cs = s();
        final int wm = mask & 0xFF;
        if (wm == cs.pipeline.stencilWriteMask) return;
        cs.pipeline.stencilWriteMask = wm;
        cs.pipeline.markOutputDirty();
    }

    public long getFrameNumber() {
        return frameManager.getFrameNumber();
    }

    private int voxLocStart = -1;
    private int voxLocCount = -1;

    public boolean bindVoxelizationRegion(int ssboBinding, int vertexBufferGlId, long openPass, float x, float y, float z) {
        if (ssboBinding < 0 || ssboBinding >= ContextState.MAX_INDEXED_BUFFERS || vertexBufferGlId == 0) return false;
        final ContextState st = s();
        if (st.boundProgram == 0) return false;
        bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, ssboBinding, vertexBufferGlId);
        final int loc = shaderManager.getUniformLocation(st.boundProgram, "u_RegionOffset");
        if (loc >= 0) GLStateManager.glUniform3f(loc, x, y, z);
        if (openPass != 0) voxelizationDispatcher.rebindVertexBuffer(st, openPass);
        return true;
    }

    /** Reflected at link time, so it names exactly the images the compute writes rather than everything bound. */
    public String[] getComputeWrittenImageNames(int programId) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(programId);
        if (prog == null || !prog.linked || prog.computeBindingMap == null) return new String[0];
        final String[] names = prog.computeBindingMap.rwStorageTextureNames();
        return names == null ? new String[0] : names.clone();
    }

    public long beginVoxelizationBatch(int ssboBinding) {
        if (ssboBinding < 0 || ssboBinding >= ContextState.MAX_INDEXED_BUFFERS) return 0;
        final ContextState st = s();
        final int program = st.boundProgram;
        if (program == 0 || st.boundSsboByIndex[ssboBinding] == 0) return 0;
        voxLocStart = shaderManager.getUniformLocation(program, "_vg_startVertex");
        voxLocCount = shaderManager.getUniformLocation(program, "_vg_vertexCount");
        return voxelizationDispatcher.beginBatch(st);
    }

    public void voxelizeRange(long pass, int vertexOffset, int vertexCount) {
        voxelizationDispatcher.dispatchRange(pass, voxLocStart, voxLocCount, vertexOffset, vertexCount, s());
    }

    public void endVoxelizationBatch(long pass) {
        voxelizationDispatcher.endBatch(pass);
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int primcount) {
        if (!frameManager.isFrameActive()) return;
        final ContextState st = s();
        drawDispatch.setPrimitiveTypeForDraw(st, FormatMap.mapPrimitiveType(mode));
        pipelineApplier.ensureRenderPass(st);
        if (!frameManager.isRenderPassActive()) return;
        if (!pipelineApplier.applyPipelineAndState(st)) return;
        SDL_DrawGPUPrimitives(frameManager.getRenderPass(), count, primcount, first, 0);
    }

    @Override
    public void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        if (firsts == null || counts == null) return;
        final int n = Math.min(firsts.remaining(), counts.remaining());
        for (int i = 0; i < n; i++) {
            drawArrays(mode, firsts.get(firsts.position() + i), counts.get(counts.position() + i));
        }
    }

    @Override
    public void primitiveRestartIndex(int index) {
        final ContextState cs = s();
        if (cs.primitiveRestartSentinel == index) return;
        cs.primitiveRestartSentinel = index;
        resourceManager.clearSplitCache();
    }

    @Override
    public void pointParameterf(int pname, float param) {}

    @Override
    public void pointParameteri(int pname, int param) {}

    @Override
    public void getActiveAttrib(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        final List<String> names = prog != null ? prog.getActiveAttribNames() : List.of();
        final String aname = (index >= 0 && index < names.size()) ? names.get(index) : "";
        if (size != null && size.remaining() > 0) size.put(0, 1);
        if (type != null && type.remaining() > 0) type.put(0, prog != null ? prog.getAttribGlType(aname) : GL20.GL_FLOAT_VEC4);
        writeUtf8(aname, length, name);
    }

    @Override
    public String getActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(program);
        if (prog == null) {
            if (sizeType != null && sizeType.remaining() >= 2) {
                sizeType.put(0, 1);
                sizeType.put(1, GL20.GL_FLOAT_VEC4);
            }
            return "";
        }
        final var names = prog.getActiveAttribNames();
        final String aname = (index >= 0 && index < names.size()) ? names.get(index) : "";
        if (sizeType != null && sizeType.remaining() >= 2) {
            sizeType.put(0, 1);
            sizeType.put(1, prog.getAttribGlType(aname));
        }
        if (aname.isEmpty()) return "";
        return maxLength > 0 && aname.length() > maxLength ? aname.substring(0, maxLength) : aname;
    }

    private int boundRenderbufferId;

    @Override
    public int genRenderbuffers() {
        return resourceManager.genTexture();
    }

    @Override
    public void deleteRenderbuffers(int renderbuffer) {
        if (renderbuffer == boundRenderbufferId) boundRenderbufferId = 0;
        deleteTextures(renderbuffer);
    }

    @Override
    public void bindRenderbuffer(int target, int renderbuffer) {
        boundRenderbufferId = renderbuffer;
    }

    @Override
    public void renderbufferStorage(int target, int internalformat, int width, int height) {
        if (boundRenderbufferId == 0) return;
        resourceManager.deleteTexture(boundRenderbufferId);
        resourceManager.createTexture(boundRenderbufferId, GL11.GL_TEXTURE_2D, internalformat, width, height, 1, 1);
    }

    private boolean warnedMSAARenderbuffer = false;

    @Override
    public void renderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        if (samples > 1 && !warnedMSAARenderbuffer) {
            warnedMSAARenderbuffer = true;
            LOG.warn("SDL backend: MSAA renderbuffer requested (samples={}) but unsupported; falling back to 1-sample.", samples);
        }
        renderbufferStorage(target, internalformat, width, height);
    }

    @Override
    public void framebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        framebufferTexture2D(target, attachment, GL11.GL_TEXTURE_2D, renderbuffer, 0);
    }

    @Override
    public void clearBufferData(int target, int internalformat, int format, int type, ByteBuffer data) {
        final int glId = getBoundBuffer(target);
        if (glId == 0) return;
        final long size = resourceManager.getBufferSize(glId);
        if (size <= 0) return;
        clearBufferSubData(target, internalformat, 0L, size, format, type, data);
    }

    private final class DeferredCopyOp implements BiConsumer<PersistentMapping, PersistentMapping> {
        long readOffset, size, dstHandle, writeOffset, seq;
        @Override
        public void accept(PersistentMapping src, PersistentMapping dst) {
            enqueueUpload(TransferThread.StagingReadUpload.acquire(src.staging, readOffset, size, dstHandle, writeOffset, seq, false));
            if (dst != null) PersistentBufferSync.mirrorPersistentCopy(src.staging, readOffset, dst.staging, writeOffset, size);
        }
    }
}
