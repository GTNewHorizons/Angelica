package com.gtnewhorizons.umbra.mixins.early.textures;

import com.gtnewhorizons.angelica.glsm.hooks.TextureUtilHooks;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
public class MixinTextureUtil {

    @Redirect(method = "allocateTextureImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;deleteTexture(I)V"))
    private static void umbra$dontDeleteTexture(int textureId) {
    }

    @Inject(method = "allocateTextureImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;bindTexture(I)V", shift = At.Shift.AFTER))
    private static void umbra$setMaxLevel(int textureId, int mipmapLevels, int width, int height, float anisotropicFiltering, CallbackInfo ci) {
        TextureUtilHooks.setMaxLevel(mipmapLevels);
    }

    @Inject(method = "func_152777_a(ZZF)V", at = @At("HEAD"))
    private static void umbra$captureSaveBoundId(boolean blurred, boolean mipmap, float aniso, CallbackInfo ci) {
        TextureUtilHooks.captureBoundFilterTexture();
    }

    @Redirect(method = "func_147945_b()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_147952_b(II)V"))
    private static void umbra$restoreFilterOnSavedTexture(int min, int mag) {
        TextureUtilHooks.restoreFilter(min, mag);
    }

    @Redirect(method = "func_147945_b()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_152778_a(F)V"))
    private static void umbra$restoreAnisoOnSavedTexture(float aniso) {
        TextureUtilHooks.restoreAniso(aniso);
    }

    @Inject(method = "func_147945_b()V", at = @At("RETURN"))
    private static void umbra$clearSavedAfterRestore(CallbackInfo ci) {
        TextureUtilHooks.clearSavedFilterTexture();
    }
}
