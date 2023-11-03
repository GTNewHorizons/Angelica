package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.texture.DynamicTexture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.angelica.client.ShadersTex;

@Mixin(DynamicTexture.class)
public class MixinDynamicTexture {
    // TODO: PBR
    @ModifyVariable(argsOnly = true, at = @At(ordinal = 1, value = "LOAD"), index = 1, method = "<init>(II)V")
    private int angelica$resizeDynamicTextureData(int p_i1271_2_) {
        return p_i1271_2_ * 3;
    }

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;allocateTexture(III)V",
                    value = "INVOKE"),
            method = "<init>(II)V")
    private void angelica$initDynamicTexture(int p_110991_0_, int p_110991_1_, int p_110991_2_) {
        // p_110991_1_ needs to be divided by 3 to revert angelica$resizeDynamicTextureData
        ShadersTex.initDynamicTexture(p_110991_0_, p_110991_1_ / 3, p_110991_2_, (DynamicTexture) (Object) this);
    }

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTexture(I[III)V",
                    value = "INVOKE"),
            method = "updateDynamicTexture()V")
    private void angelica$updateDynamicTexture(int p_110988_0_, int[] p_110988_1_, int p_110988_2_, int p_110988_3_) {
        ShadersTex.updateDynamicTexture(p_110988_0_, p_110988_1_, p_110988_2_, p_110988_3_, (DynamicTexture) (Object) this);
    }

}
