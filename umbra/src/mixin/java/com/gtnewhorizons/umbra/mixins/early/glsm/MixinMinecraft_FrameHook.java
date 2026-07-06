package com.gtnewhorizons.umbra.mixins.early.glsm;

import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-frame hook to flush streaming buffer fences.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_FrameHook {

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void umbra$onFrameStart(CallbackInfo ci) {
        TessellatorStreamingDrawer.endFrame();
    }
}
