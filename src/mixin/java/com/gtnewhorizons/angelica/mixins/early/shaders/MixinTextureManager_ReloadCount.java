package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.Iris;
import net.coderbot.iris.texture.format.TextureFormatLoader;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook into TextureManger to reload the PBR texture format and increment the reload count.
 */
@Mixin(TextureManager.class)
public class MixinTextureManager_ReloadCount {

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void angelica$bumpTextureReloadCount(IResourceManager resourceManager, CallbackInfo ci) {
        try {
            TextureFormatLoader.reload(resourceManager);
        } catch (Exception e) {
            Iris.logger.error("Failed to reload the shader pipeline for a texture format change", e);
        }
        PBRTextureManager.INSTANCE.clear();
        CapturedRenderingState.INSTANCE.incrementTextureReloadCount();
    }
}
