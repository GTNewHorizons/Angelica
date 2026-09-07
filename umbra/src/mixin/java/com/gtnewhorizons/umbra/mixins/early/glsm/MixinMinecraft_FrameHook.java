package com.gtnewhorizons.umbra.mixins.early.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.FrameHooks;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft_FrameHook {

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void umbra$bootstrapFirstFrame(CallbackInfo ci) {
        FrameHooks.bootstrapFirstFrame();
    }

    @Inject(method = "func_147120_f", at = @At("HEAD"))
    private void umbra$onFrameEnd(CallbackInfo ci) {
        FrameHooks.frameEnd();
    }

    @Inject(method = "func_147120_f", at = @At("RETURN"))
    private void umbra$onFrameBegin(CallbackInfo ci) {
        FrameHooks.frameBegin();
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(F)V"))
    private void umbra$freshenMouseInput(CallbackInfo ci) {
        GLStateManager.pumpDisplayMessages();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/FMLCommonHandler;onRenderTickEnd(F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void umbra$injectLightingFixPostRenderTick(CallbackInfo ci) {
        GLStateManager.glEnable(GL11.GL_LIGHTING);
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void umbra$streamingBufferEndFrame(CallbackInfo ci) {
        TessellatorStreamingDrawer.endFrame();
        ShaderManager.endFrame();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void umbra$onShutdown(CallbackInfo ci) {
        FrameHooks.shutdown();
    }
}
