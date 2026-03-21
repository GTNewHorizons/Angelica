package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks frame boundaries and shutdown in Minecraft's game loop to notify the active render backend.
 *
 * Frame begin: at the start of runGameLoop (before any rendering)
 * Frame end: after framebuffer blit and before Display.update (func_147120_f)
 * Shutdown: at the start of shutdownMinecraftApplet (cleanup GPU resources)
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_FrameHook {

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void angelica$onFrameBegin(CallbackInfo ci) {
        RENDER_BACKEND.onFrameBegin();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147120_f()V")
    )
    private void angelica$onFrameEnd(CallbackInfo ci) {
        RENDER_BACKEND.onFrameEnd();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void angelica$onShutdown(CallbackInfo ci) {
        BackendManager.shutdown();
    }
}
