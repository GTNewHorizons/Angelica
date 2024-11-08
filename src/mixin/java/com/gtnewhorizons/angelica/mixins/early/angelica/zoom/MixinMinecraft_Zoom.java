package com.gtnewhorizons.angelica.mixins.early.angelica.zoom;

import com.gtnewhorizons.angelica.zoom.Zoom;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MixinMinecraft_Zoom {

    @ModifyExpressionValue(method = "runTick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventDWheel()I", remap = false))
    private int angelica$captureMouseWheel(int original) {
        if (Zoom.getZoomKey().getIsKeyPressed()) {
            Zoom.modifyZoom(original);
            return 0;
        }
        return original;
    }

}
