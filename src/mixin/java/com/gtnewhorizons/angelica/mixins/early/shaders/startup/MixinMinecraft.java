package com.gtnewhorizons.angelica.mixins.early.shaders.startup;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Unique
    private static boolean angelica$hasFirstInit;

    @Inject(method = "startGame",
        at = @At(value = "INVOKE",
            remap = false,
            shift = At.Shift.BEFORE,
            target = "Lcpw/mods/fml/client/SplashProgress;clearVanillaResources(Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/util/ResourceLocation;)V"))
    public void angelica$shadersOnLoadingComplete(CallbackInfo ci) {
        if (!angelica$hasFirstInit && GLStateManager.isMainThread()) {
            angelica$hasFirstInit = true;
            Iris.onLoadingComplete();
        }
    }

}
