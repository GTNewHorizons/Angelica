package com.gtnewhorizons.umbra.mixins.early.ffp;

import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Tessellator.class)
public class MixinTessellator_CoreProfile {

    @Shadow public void reset() {}

    public void angelica$reset() {
        reset();
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void umbra$coreProfileDraw(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TessellatorStreamingDrawer.draw((Tessellator)(Object)this));
    }
}
