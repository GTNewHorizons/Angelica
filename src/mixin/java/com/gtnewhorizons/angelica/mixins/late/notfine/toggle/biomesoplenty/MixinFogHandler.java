package com.gtnewhorizons.angelica.mixins.late.notfine.toggle.biomesoplenty;

import biomesoplenty.client.fog.FogHandler;
import jss.notfine.core.Settings;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogHandler.class, remap = false)
public class MixinFogHandler {

    @Inject(method = "renderFog", at = @At("HEAD"), cancellable = true)
    private static void notFine$modifyBoPFog(int fogMode, float farPlane, float nearFactor, CallbackInfo ci) {
        if (!(Boolean) Settings.TERRAIN_FOG.option.getStore()) {
            // same values from notfine.toggle.MixinEntityRenderer
            GL11.glFogf(GL11.GL_FOG_START, 1024 * 1024 * 15);
            GL11.glFogf(GL11.GL_FOG_END, 1024 * 1024 * 16);
            ci.cancel();
            return;
        }

        if (fogMode < 0) {
            GL11.glFogf(GL11.GL_FOG_START, 0.0F);
            GL11.glFogf(GL11.GL_FOG_END, farPlane);
        } else {
            float nearDistPercent = (int) Settings.FOG_NEAR_DISTANCE.option.getStore() * 0.01F;
            GL11.glFogf(GL11.GL_FOG_START, farPlane * nearFactor * nearDistPercent);
            GL11.glFogf(GL11.GL_FOG_END, farPlane);
        }
        ci.cancel();
    }
}
