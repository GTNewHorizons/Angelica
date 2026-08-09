package com.gtnewhorizons.angelica.mixins.early.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import net.minecraftforge.client.ForgeHooksClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.PixelFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.imageio.ImageIO;

@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient_SDLGPUDisplay {

    @Unique private static final Logger LOGGER = LogManager.getLogger("Angelica/SDLGPU");

    @Shadow static int stencilBits;

    @Inject(method = "createDisplay", at = @At("HEAD"), cancellable = true)
    private static void angelicaSdl$createDisplay(CallbackInfo ci) throws LWJGLException {
        if (!SDLGPUGate.isDeviceReady()) {
            LOGGER.warn("No usable SDL GPU device; creating an OpenGL window instead");
            return;
        }

        ImageIO.setUseCache(false);
        stencilBits = 8;
        final PixelFormat format = new PixelFormat().withDepthBits(24).withStencilBits(stencilBits);

        if (SDLGPUGate.createSDLGPUDisplay(format, null)) {
            LOGGER.info("Created SDL GPU window");
            ci.cancel();
            return;
        }

        LOGGER.warn("SDL GPU could not take the window; falling back to OpenGL");
        SDLGPUGate.fallBackToGL();
    }
}
