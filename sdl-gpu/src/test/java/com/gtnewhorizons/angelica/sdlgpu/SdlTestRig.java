package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_CreateGPUDevice;
import static org.lwjgl.sdl.SDLGPU.SDL_DestroyGPUDevice;
import static org.lwjgl.sdl.SDLGPU.SDL_GetGPUShaderFormats;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_DXBC;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_DXIL;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_MSL;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERFORMAT_SPIRV;
import static org.lwjgl.sdl.SDLGPU.SDL_WaitForGPUIdle;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;

public final class SdlTestRig {

    public final Device device;
    public final FrameManager frameManager;
    public final ResourceManager resourceManager;
    public final long sdlHandle;

    private SdlTestRig(long sdlHandle) throws ReflectiveOperationException {
        this.sdlHandle = sdlHandle;
        this.device = new Device();
        if (sdlHandle != 0L) {
            final Field deviceField = Device.class.getDeclaredField("device");
            deviceField.setAccessible(true);
            deviceField.setLong(this.device, sdlHandle);
            final Field formatsField = Device.class.getDeclaredField("supportedShaderFormats");
            formatsField.setAccessible(true);
            formatsField.setInt(this.device, SDL_GetGPUShaderFormats(sdlHandle));
        }
        this.frameManager = new FrameManager(device);
        this.resourceManager = new ResourceManager(device, frameManager);
    }

    public static SdlTestRig create() {
        try {
            return new SdlTestRig(0L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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

    private static long realSdlDevice;

    private static final int SHADER_FORMATS = SDL_GPU_SHADERFORMAT_SPIRV | SDL_GPU_SHADERFORMAT_MSL | SDL_GPU_SHADERFORMAT_DXBC | SDL_GPU_SHADERFORMAT_DXIL;

    public static SdlTestRig acquireRealDevice() throws ReflectiveOperationException {
        assumeTrue(SDLInit.SDL_Init(SDL_INIT_VIDEO), "SDL_Init failed: " + SDLError.SDL_GetError());
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
