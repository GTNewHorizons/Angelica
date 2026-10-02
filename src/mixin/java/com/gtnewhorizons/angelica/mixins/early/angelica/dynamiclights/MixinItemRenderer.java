package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @Surround(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getLightBrightnessForSkyBlocks(IIII)I"))
    private void angelica$dynamiclights_renderItemInFirstPerson() {}

    @Surround.Return
    private int angelica$dynamiclights_renderItemInFirstPersonResult(int lightmap, WorldClient theWorld, int posX, int posY, int posZ){
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
