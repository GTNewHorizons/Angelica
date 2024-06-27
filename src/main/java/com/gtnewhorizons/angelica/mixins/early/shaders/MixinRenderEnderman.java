package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.entity.RenderEnderman;
import net.minecraft.entity.monster.EntityEnderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderEnderman.class)
public class MixinRenderEnderman {
    @Inject(method = "Lnet/minecraft/client/renderer/entity/RenderEnderman;shouldRenderPass(Lnet/minecraft/entity/monster/EntityEnderman;IF)I", at = @At(value="RETURN", ordinal = 1))
    private void onShouldRenderPass(EntityEnderman entity, int pass, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.ENTITY_EYES);
    }

}
