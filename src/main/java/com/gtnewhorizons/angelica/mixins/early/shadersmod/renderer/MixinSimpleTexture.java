package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import com.gtnewhorizons.angelica.client.ShadersTex;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.awt.image.BufferedImage;

@Mixin(value = SimpleTexture.class)
public abstract class MixinSimpleTexture extends AbstractTexture {
    // TODO: PBR

    @Shadow
    protected ResourceLocation textureLocation;

    @Redirect(
            method = "loadTexture(Lnet/minecraft/client/resources/IResourceManager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureImageAllocate(ILjava/awt/image/BufferedImage;ZZ)I"))
    private int angelica$loadSimpleTexture(int textureID, BufferedImage bufferedImage, boolean flag, boolean flag1, @Local IResourceManager p_110551_1_) {
        ShadersTex.loadSimpleTexture(textureID, bufferedImage, flag, flag1, p_110551_1_, this.textureLocation, ShadersTex.getMultiTexID(this));
        return 0;
    }

}
