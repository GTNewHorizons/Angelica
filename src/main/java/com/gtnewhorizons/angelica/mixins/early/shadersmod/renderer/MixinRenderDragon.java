package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.entity.RenderDragon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(RenderDragon.class)
public class MixinRenderDragon {
    // TODO: Rendering
    @Inject(
            at = @At(
                    remap = false,
                    shift = At.Shift.AFTER,
                    target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V",
                    value = "INVOKE"),
            method = "shouldRenderPass(Lnet/minecraft/entity/boss/EntityDragon;IF)I")
    private void angelica$beginSpiderEyes(CallbackInfoReturnable<Integer> cir) {
        Shaders.beginSpiderEyes();
    }

}
