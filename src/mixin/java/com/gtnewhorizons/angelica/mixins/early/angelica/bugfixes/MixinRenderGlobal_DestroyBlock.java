package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import org.lwjglx.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_DestroyBlock {
    @Inject(
        method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V")
    )
    private void iris$beginDrawBlockDamageTexture(Tessellator tessellator, EntityLivingBase entity, float partialTicks, CallbackInfo ci, @Share("lastMin") LocalIntRef lastMin, @Share("lastMag") LocalIntRef lastMag) {
        TextureInfo info = TextureInfoCache.INSTANCE.getInfo(GLStateManager.getBoundTexture());
        lastMin.set(info.getMinFilter());
        lastMag.set(info.getMagFilter());
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    @Inject(
        method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V"),
        remap = false
    )
    private void iris$endDrawBlockDamageTexture(Tessellator tessellator, EntityLivingBase entity, float partialTicks, CallbackInfo ci, @Share("lastMin") LocalIntRef lastMin, @Share("lastMag") LocalIntRef lastMag) {
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, lastMin.get());
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, lastMag.get());
    }
}
