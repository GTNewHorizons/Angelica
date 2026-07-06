package com.gtnewhorizons.angelica.mixins.early.angelica.ffp;

import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.renderer.Tessellator;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Tessellator.draw() to use streaming VBO+VAO for core profile rendering,
 * except when GTNHLib needs to intercept (display list compilation, DirectTessellator capture).
 */
@Mixin(Tessellator.class)
public class MixinTessellator_CoreProfile {

    @Inject(method = "draw", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;isDrawing:Z", opcode = Opcodes.GETFIELD), cancellable = true)
    private void angelica$coreProfileDraw(CallbackInfoReturnable<Integer> cir) {
        // Injecting after HEAD because GTNHLib's shouldInterceptDraw() takes priority
        cir.setReturnValue(TessellatorStreamingDrawer.draw((Tessellator) (Object) this));
    }
}
