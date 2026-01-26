package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @WrapOperation(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getLightBrightnessForSkyBlocks(IIII)I"))
    private int angelica$dynamiclights_renderItemInFirstPerson(WorldClient theWorld, int posX, int posY, int posZ, int p_72802_4_, Operation<Integer> original){
        int lightmap = original.call(theWorld, posX, posY, posZ, p_72802_4_);
        if (DynamicLights.isEnabled()) {
            final DynamicLights dl = DynamicLights.get();
            if (dl.hasLightSources()) {
                final double dynamicLightLevel = dl.getDynamicLightLevel(posX, posY, posZ);
                if (dynamicLightLevel > 0) {
                    lightmap = dl.getLightmapWithDynamicLight(dynamicLightLevel, lightmap);
                }
            }
        }
        return lightmap;
    }
}
