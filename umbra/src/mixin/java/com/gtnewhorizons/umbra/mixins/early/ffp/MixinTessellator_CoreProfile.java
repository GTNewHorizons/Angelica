package com.gtnewhorizons.umbra.mixins.early.ffp;

import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.renderer.Tessellator;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Tessellator.class)
public class MixinTessellator_CoreProfile {

    @Inject(method = "draw", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;isDrawing:Z", opcode = Opcodes.GETFIELD), cancellable = true)
    private void umbra$coreProfileDraw(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TessellatorStreamingDrawer.draw((Tessellator) (Object) this));
    }
}
