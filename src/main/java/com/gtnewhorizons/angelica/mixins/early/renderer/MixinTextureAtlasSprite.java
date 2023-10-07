package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.angelica.client.ShadersTex;

@Mixin(TextureAtlasSprite.class)
public class MixinTextureAtlasSprite {

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V",
                    value = "INVOKE"),
            method = "updateAnimation()V")
    private void angelica$uploadTexSub(int[][] p_147955_0_, int p_147955_1_, int p_147955_2_, int p_147955_3_,
            int p_147955_4_, boolean p_147955_5_, boolean p_147955_6_) {
        ShadersTex.uploadTexSub(p_147955_0_, p_147955_1_, p_147955_2_, p_147955_3_, p_147955_4_, p_147955_5_, p_147955_6_);
    }

}
