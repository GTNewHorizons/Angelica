package com.gtnewhorizons.angelica.mixins.early.renderer;

import java.awt.image.BufferedImage;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.ShadersTex;

@Mixin(value = SimpleTexture.class)
public class MixinSimpleTexture extends AbstractTexture {

    private IResourceManager passedResourceManager;
    @Shadow
    protected ResourceLocation textureLocation;

    @Inject(method = "loadTexture(Lnet/minecraft/client/resources/IResourceManager;)V", at = @At("HEAD"))
    private void getResourceManager(IResourceManager p_110551_1_, CallbackInfo cbi) {
        passedResourceManager = p_110551_1_;
    }

    @Redirect(
            method = "loadTexture(Lnet/minecraft/client/resources/IResourceManager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureImageAllocate(ILjava/awt/image/BufferedImage;ZZ)I"))
    private int angelica$loadTexture(int textureID, BufferedImage bufferedImage, boolean flag, boolean flag1) {
        ShadersTex.loadSimpleTexture(
                textureID,
                bufferedImage,
                flag,
                flag1,
                passedResourceManager,
                this.textureLocation,
                ShadersTex.getMultiTexID(this));
        return 0;
    }

}
