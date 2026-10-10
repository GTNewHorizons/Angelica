package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.TransferThread;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_CreateGPUDevice;
import static org.lwjgl.sdl.SDLGPU.SDL_DestroyGPUDevice;
import static org.lwjgl.sdl.SDLGPU.SDL_GetGPUDeviceDriver;
import static org.lwjgl.sdl.SDLGPU.SDL_GetGPUShaderFormats;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_DXBC;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_DXIL;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_MSL;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_SPIRV;
import static org.lwjgl.sdl.SDLGPU.SDL_WaitForGPUIdle;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLVersion.SDL_GetVersion;

public final class SdlTestRig {

    public final Device device;
    public final FrameManager frameManager;
    public final ResourceManager resourceManager;
    public final long sdlHandle;

    private SdlTestRig(long sdlHandle) {
        this.sdlHandle = sdlHandle;
        this.device = new Device();
        if (sdlHandle != 0L) {
            Reflect.set(this.device, "device", sdlHandle);
            Reflect.set(this.device, "supportedShaderFormats", SDL_GetGPUShaderFormats(sdlHandle));
            Reflect.set(this.device, "driverName", SDL_GetGPUDeviceDriver(sdlHandle));
            Reflect.invoke(this.device, "probeFences", new Class<?>[] { int.class }, SDL_GetVersion());
        }
        this.frameManager = new FrameManager(device);
        this.resourceManager = new ResourceManager(device, frameManager);
    }

    public static SdlTestRig create() {
        return new SdlTestRig(0L);
    }

    public static ResourceManager resourceManager() {
        return create().resourceManager;
    }

    public static FrameManager frameManager() {
        return create().frameManager;
    }

    public static ContextState contextState() {
        return Reflect.invokeStatic(SDLGPURenderBackend.class, "s", new Class<?>[0]);
    }

    public static ResourceManager resourceManagerOf(SDLGPURenderBackend backend) {
        return Reflect.get(backend, "resourceManager");
    }

    /** Not installed on the resource manager; pair with {@link #shutdown(TransferThread)}. */
    public TransferThread newTransferThread() {
        return new TransferThread(device, resourceManager);
    }

    public TransferThread installTransferThread() {
        final TransferThread tt = newTransferThread();
        resourceManager.setTransferThread(tt);
        return tt;
    }

    public static void shutdown(TransferThread tt) {
        tt.shutdown();
        try {
            tt.getThread().join(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static long realSdlDevice;

    private static final int SHADER_FORMATS = SDL_GPU_SHADERFORMAT_SPIRV | SDL_GPU_SHADERFORMAT_MSL | SDL_GPU_SHADERFORMAT_DXBC | SDL_GPU_SHADERFORMAT_DXIL;

    public static void initSdlVideo() {
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MAC_BACKGROUND_APP, "1");
        assumeTrue(SDLInit.SDL_Init(SDL_INIT_VIDEO), () -> "SDL_Init failed: " + SDLError.SDL_GetError());
    }

    public static SdlTestRig acquireRealDevice() {
        initSdlVideo();
        realSdlDevice = SDL_CreateGPUDevice(SHADER_FORMATS, true, (CharSequence) null);
        assumeTrue(realSdlDevice != 0, "No SDL GPU device: " + SDLError.SDL_GetError());

        final SdlTestRig rig = new SdlTestRig(realSdlDevice);
        rig.frameManager.setResourceManager(rig.resourceManager);
        return rig;
    }

    public static void releaseRealDevice() {
        if (realSdlDevice != 0) {
            SDL_WaitForGPUIdle(realSdlDevice);
            SDL_DestroyGPUDevice(realSdlDevice);
            realSdlDevice = 0;
        }
    }
}
