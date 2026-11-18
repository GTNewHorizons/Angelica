package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureManager.class)
public class MixinTextureManager {
    @Inject(method = "loadTexture", at = @At("RETURN"))
    private void onTextureLoad(ResourceLocation resource, ITextureObject texture, CallbackInfoReturnable<Boolean> cir) {
        if(texture != null && texture != TextureUtil.missingTexture && !GLStateManager.isRecordingDisplayList()) {
            final int curId = GLSMQueries.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlTextureId());
            GLDebug.nameObject(GL11.GL_TEXTURE, texture.getGlTextureId(), resource.toString());
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, curId);
        }
    }
}
