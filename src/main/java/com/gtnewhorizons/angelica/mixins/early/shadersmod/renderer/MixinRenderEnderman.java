package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.entity.RenderEnderman;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(RenderEnderman.class)
public class MixinRenderEnderman {
    // TODO: Rendering
    @Inject(
            at = @At(
                    remap = false,
                    shift = At.Shift.AFTER,
                    target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V",
                    value = "INVOKE"),
            method = "shouldRenderPass(Lnet/minecraft/entity/monster/EntityEnderman;IF)I")
    private void angelica$beginSpiderEyes(CallbackInfoReturnable<Integer> cir) {
        Shaders.beginGlowingEyes();
    }

}
