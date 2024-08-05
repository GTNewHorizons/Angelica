package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.base;

import net.minecraft.client.resources.SimpleReloadableResourceManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;

@Mixin(SimpleReloadableResourceManager.class)
public abstract class MixinSimpleReloadableResourceManager {

    @Inject(method = "notifyReloadListeners()V", at = @At("HEAD"))
    private void modifyNotifyReloadListeners1(CallbackInfo ci) {
        TexturePackChangeHandler.beforeChange1();
    }

    @Inject(method = "notifyReloadListeners()V", at = @At("RETURN"))
    private void modifyNotifyReloadListeners2(CallbackInfo ci) {
        TexturePackChangeHandler.afterChange1();
    }
}
