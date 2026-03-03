package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
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
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmapLevels);
    }
}
