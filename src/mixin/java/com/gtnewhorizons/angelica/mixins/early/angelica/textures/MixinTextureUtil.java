package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
public class MixinTextureUtil {

    @Redirect(method = "allocateTextureImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;deleteTexture(I)V"))
    private static void angelica$dontDeleteTexture(int textureId) {
        // NO-OP - Not sure why it's deleting a texture that was just generated and subsequently being bound...
    }

    @Inject(method = "allocateTextureImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;bindTexture(I)V", shift = At.Shift.AFTER))
    private static void angelica$setMaxLevel(int textureId, int mipmapLevels, int width, int height, float anisotropicFiltering, CallbackInfo ci) {
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmapLevels);
    }

    @Unique private static int angelica$savedFilterTextureId;

    @Inject(method = "func_152777_a(ZZF)V", at = @At("HEAD"))
    private static void angelica$captureSaveBoundId(boolean blurred, boolean mipmap, float aniso, CallbackInfo ci) {
        angelica$savedFilterTextureId = GLStateManager.getBoundTextureForServerState();
    }

    @Redirect(method = "func_147945_b()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_147952_b(II)V"))
    private static void angelica$restoreFilterOnSavedTexture(int min, int mag) {
        final int id = angelica$savedFilterTextureId;
        if (id != 0) {
            if (DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
                RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
                RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            } else {
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            }
        } else {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        }
    }

    @Redirect(method = "func_147945_b()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_152778_a(F)V"))
    private static void angelica$restoreAnisoOnSavedTexture(float aniso) {
        final int id = angelica$savedFilterTextureId;
        if (id != 0) {
            if (DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
                RenderSystem.texParameterf(id, GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
            } else {
                GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
            }
        } else {
            GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        }
    }

    @Inject(method = "func_147945_b()V", at = @At("RETURN"))
    private static void angelica$clearSavedAfterRestore(CallbackInfo ci) {
        angelica$savedFilterTextureId = 0;
    }
}
