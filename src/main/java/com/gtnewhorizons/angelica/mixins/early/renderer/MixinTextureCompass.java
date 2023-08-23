package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.client.renderer.texture.TextureCompass;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.angelica.client.ShadersTex;

@Mixin(value = TextureCompass.class)
public class MixinTextureCompass {

    @Redirect(
            method = "updateCompass(Lnet/minecraft/world/World;DDDZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"))
    private void angelica$uploadTexSub(int[][] data, int width, int height, int xoffset, int yoffset, boolean linear,
            boolean clamp) {
        ShadersTex.uploadTexSub(data, width, height, xoffset, yoffset, linear, clamp);
    }

}
