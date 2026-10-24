package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.glsm.hooks.TextureUtilHooks;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
public class MixinTextureUtil {

    @Redirect(method = "allocateTextureImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;deleteTexture(I)V"))
    private static void angelica$dontDeleteTexture(int textureId) {
    }

    @Inject(method = "allocateTextureImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;bindTexture(I)V", shift = At.Shift.AFTER))
    private static void angelica$setMaxLevel(int textureId, int mipmapLevels, int width, int height, float anisotropicFiltering, CallbackInfo ci) {
        TextureUtilHooks.setMaxLevel(mipmapLevels);
    }

    @Surround(id = "saveFilter", method = "func_152777_a(ZZF)V")
    private static void angelica$beginFilterSave(boolean blurred, boolean mipmap, float aniso) {
        @Surround.Carry boolean captured = TextureUtilHooks.beginFilterSave(blurred, mipmap, aniso);
    }

    @Surround.Catch("saveFilter")
    private static void angelica$filterSaveFailed(Throwable failure, @Surround.Carry boolean captured) {
        TextureUtilHooks.filterFailed(captured);
    }

    @Surround.Finally("saveFilter")
    private static void angelica$finishFilterSave(@Surround.Carry boolean captured) {
        TextureUtilHooks.endFilterSave(captured);
    }

    @Surround(id = "restoreFilter", method = "func_147945_b()V")
    private static void angelica$beginFilterRestore() {
        @Surround.Carry boolean captured = TextureUtilHooks.beginFilterRestore();
    }

    @Surround.Catch("restoreFilter")
    private static void angelica$filterRestoreFailed(Throwable failure, @Surround.Carry boolean captured) {
        TextureUtilHooks.filterFailed(captured);
    }

    @Redirect(method = "func_147945_b()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_147952_b(II)V"))
    private static void angelica$restoreFilterOnSavedTexture(int min, int mag) {
        TextureUtilHooks.restoreFilter(min, mag);
    }

    @Redirect(method = "func_147945_b()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_152778_a(F)V"))
    private static void angelica$restoreAnisoOnSavedTexture(float aniso) {
        TextureUtilHooks.restoreAniso(aniso);
    }

    @Surround.Finally("restoreFilter")
    private static void angelica$finishFilterRestore(@Surround.Carry boolean captured) {
        TextureUtilHooks.endFilterRestore(captured);
    }
}
