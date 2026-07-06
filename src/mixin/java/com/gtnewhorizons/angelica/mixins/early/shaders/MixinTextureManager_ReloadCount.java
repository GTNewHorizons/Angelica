package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook into TextureManger to increment the reload count.
 */
@Mixin(TextureManager.class)
public class MixinTextureManager_ReloadCount {

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void angelica$bumpTextureReloadCount(IResourceManager resourceManager, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.incrementTextureReloadCount();
    }
}
