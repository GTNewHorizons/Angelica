package com.gtnewhorizons.angelica.mixins.early.angelica.zoom;

import com.gtnewhorizons.angelica.zoom.Zoom;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_Zoom {

    @ModifyReturnValue(method = "getFOVModifier", at = @At("RETURN"))
    private float angelica$modifyFOV(float original) {
        if (Zoom.getZoomKey().getIsKeyPressed()) {
            return original / Zoom.getZoom();
        }
        return original;
    }

}
