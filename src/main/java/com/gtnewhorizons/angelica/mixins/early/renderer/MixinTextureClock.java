package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.client.renderer.texture.TextureClock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.angelica.client.ShadersTex;

@Mixin(value = TextureClock.class)
public class MixinTextureClock {

    @Redirect(
            method = "updateAnimation()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"))
    private void angelica$updateAnimation(int[][] data, int width, int height, int xoffset, int yoffset, boolean linear,
            boolean clamp) {
        ShadersTex.uploadTexSub(data, width, height, xoffset, yoffset, linear, clamp);
    }

}
