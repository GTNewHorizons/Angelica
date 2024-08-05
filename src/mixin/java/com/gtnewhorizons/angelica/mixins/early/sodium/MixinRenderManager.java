package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import me.jellysquid.mods.sodium.client.gui.options.named.LightingQuality;
import me.jellysquid.mods.sodium.client.model.light.EntityLighter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderManager.class)
public class MixinRenderManager {

    /**
     * Sodium: Use Sodium smooth entity light if enabled.
     */
    @Redirect(method = "renderEntityStatic", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBrightnessForRender(F)I"))
    private int sodium$getBrightnessForRender(Entity self, float partialTicks) {
        int lightmap;
        if (Minecraft.getMinecraft().gameSettings.ambientOcclusion == LightingQuality.HIGH.getVanilla()) {
            lightmap = EntityLighter.getBlendedLight(self, partialTicks);
        }else {
            lightmap = self.getBrightnessForRender(partialTicks);
        }

        if (DynamicLights.isEnabled()){
            int dynamicLightLevel = (int) DynamicLights.get().getDynamicLightLevel((int)self.posX, (int)self.posY, (int)self.posZ);
            if (dynamicLightLevel > 0) {
                lightmap = DynamicLights.get().getLightmapWithDynamicLight(dynamicLightLevel, lightmap);
            }
        }

        return lightmap;
    }
}
