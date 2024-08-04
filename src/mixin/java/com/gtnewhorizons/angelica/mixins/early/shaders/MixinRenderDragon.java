package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderDragon.class)
public class MixinRenderDragon {
    @Inject(method = "Lnet/minecraft/client/renderer/entity/RenderDragon;shouldRenderPass(Lnet/minecraft/entity/boss/EntityDragon;IF)I", at = @At(value="RETURN", ordinal = 1))
    private void onShouldRenderPass(EntityDragon entity, int pass, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.ENTITY_EYES);
    }
}
