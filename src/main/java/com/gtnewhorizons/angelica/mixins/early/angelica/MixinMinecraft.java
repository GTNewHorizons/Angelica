package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "checkGLError", at = @At(value = "HEAD"), cancellable = true)
    private void angelica$checkGLError(CallbackInfo ci) {
        if(AngelicaConfig.disableMinecraftCheckGLErrors)
            ci.cancel();
    }
}
