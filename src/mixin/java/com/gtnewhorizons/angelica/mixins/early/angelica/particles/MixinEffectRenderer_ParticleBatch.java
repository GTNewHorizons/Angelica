package com.gtnewhorizons.angelica.mixins.early.angelica.particles;

import com.gtnewhorizons.angelica.rendering.particles.ParticleInstancer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EffectRenderer.class, priority = 900)
public class MixinEffectRenderer_ParticleBatch {

    @WrapOperation(method = "renderParticles",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V"))
    private void angelica$captureParticle(EntityFX particle, Tessellator tessellator, float partialTicks, float rotationX, float rotationXZ, float rotationZ, float rotationYZ, float rotationXY, Operation<Void> original) {
        ParticleInstancer.renderParticle(particle, tessellator, partialTicks, rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY, original);
    }

    @Inject(method = "renderParticles",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()I",
            shift = At.Shift.BEFORE))
    private void angelica$endLayer(Entity player, float partialTickTime, CallbackInfo ci) {
        ParticleInstancer.endLayer();
    }
}
