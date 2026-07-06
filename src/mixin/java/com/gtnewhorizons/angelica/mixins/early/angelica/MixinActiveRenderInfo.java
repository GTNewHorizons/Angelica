package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

@Mixin(ActiveRenderInfo.class)
public class MixinActiveRenderInfo {
    @Shadow private static FloatBuffer modelview;
    @Shadow private static FloatBuffer projection;
    @Inject(method = "updateRenderInfo", at = @At(value = "TAIL"))
    private static void angelica$onUpdateRenderInfo(CallbackInfo ci) {
        RenderingState.INSTANCE.setProjectionMatrix(projection);
        RenderingState.INSTANCE.setModelViewMatrix(modelview);
    }
}
