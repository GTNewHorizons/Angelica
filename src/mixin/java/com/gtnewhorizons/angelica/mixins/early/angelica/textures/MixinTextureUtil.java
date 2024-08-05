package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TextureUtil.class)
public class MixinTextureUtil {

    @Redirect(method = "allocateTextureImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureUtil;deleteTexture(I)V"))
    private static void angelica$dontDeleteTexture(int textureId) {
        // NO-OP - Not sure why it's deleting a texture that was just generated and subsequently being bound...
    }
}
