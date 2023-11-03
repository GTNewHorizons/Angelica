package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.angelica.client.ShadersTex;

@Mixin(TextureManager.class)
public class MixinTextureManager {
    // TODO: PBR
    @Redirect(
            at = @At(target = "Lnet/minecraft/client/renderer/texture/ITextureObject;getGlTextureId()I", value = "INVOKE"),
            method = "bindTexture(Lnet/minecraft/util/ResourceLocation;)V")
    private int angelica$bindTexture(ITextureObject object) {
        ShadersTex.bindTexture(object);
        return 0;
    }

    @Redirect(
            at = @At(target = "Lnet/minecraft/client/renderer/texture/TextureUtil;bindTexture(I)V", value = "INVOKE"),
            method = "bindTexture(Lnet/minecraft/util/ResourceLocation;)V")
    private void angelica$noop(int p_94277_0_) {
        // NO-OP
    }

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/texture/ITextureObject;getGlTextureId()I",
                    value = "INVOKE"),
            method = "deleteTexture(Lnet/minecraft/util/ResourceLocation;)V")
    private int angelica$deleteMultiTex(ITextureObject itextureobject) {
        return ShadersTex.deleteMultiTex(itextureobject);
    }

}
