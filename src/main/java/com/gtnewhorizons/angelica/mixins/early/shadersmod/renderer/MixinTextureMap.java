package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import com.gtnewhorizons.angelica.client.ShadersTex;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(TextureMap.class)
public class MixinTextureMap extends MixinAbstractTexture {
    // TODO: Textures/Stitcher

    public int angelica$atlasWidth;
    public int angelica$atlasHeight;

    // loadTextureAtlas

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/resources/IResourceManager;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;",
                    value = "INVOKE"),
            method = "loadTextureAtlas(Lnet/minecraft/client/resources/IResourceManager;)V")
    private IResource angelica$loadResource(IResourceManager p_110571_1_, ResourceLocation resourcelocation1) throws IOException {
        return ShadersTex.loadResource(p_110571_1_, resourcelocation1);
    }

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;allocateTextureImpl(IIIIF)V",
                    value = "INVOKE"),
            method = "loadTextureAtlas(Lnet/minecraft/client/resources/IResourceManager;)V")
    private void angelica$allocateTextureMap(int p_147946_0_, int p_147946_1_, int p_147946_2_, int p_147946_3_,
            float p_147946_4_, @Local Stitcher stitcher) {
        ShadersTex.allocateTextureMap(p_147946_0_, p_147946_1_, p_147946_2_, p_147946_3_, p_147946_4_, stitcher, (TextureMap) (Object) this);
    }

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;getIconName()Ljava/lang/String;",
                    value = "INVOKE"),
            method = "loadTextureAtlas(Lnet/minecraft/client/resources/IResourceManager;)V",
            slice = @Slice(
                    from = @At(
                            args = "ldc=Uploading GL texture",
                            remap = false,
                            target = "Lcpw/mods/fml/common/ProgressManager$ProgressBar;step(Ljava/lang/String;)V",
                            value = "INVOKE_STRING")))
    private String angelica$getIconName(TextureAtlasSprite textureatlassprite) {
        return ShadersTex.setIconName(ShadersTex.setSprite(textureatlassprite).getIconName());
    }

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V",
                    value = "INVOKE"),
            method = "loadTextureAtlas(Lnet/minecraft/client/resources/IResourceManager;)V")
    private void angelica$uploadTexSubForLoadAtlas(int[][] p_147955_0_, int p_147955_1_, int p_147955_2_,
            int p_147955_3_, int p_147955_4_, boolean p_147955_5_, boolean p_147955_6_) {
        ShadersTex.uploadTexSubForLoadAtlas(p_147955_0_, p_147955_1_, p_147955_2_, p_147955_3_, p_147955_4_, p_147955_5_, p_147955_6_);
    }

    // updateAnimations

    @Inject(at = @At("HEAD"), method = "updateAnimations()V")
    private void angelica$setUpdatingTex(CallbackInfo ci) {
        ShadersTex.updatingTex = this.angelica$getMultiTexID();
    }

    @Inject(at = @At("RETURN"), method = "updateAnimations()V")
    private void angelica$resetUpdatingTex(CallbackInfo ci) {
        ShadersTex.updatingTex = null;
    }

}
