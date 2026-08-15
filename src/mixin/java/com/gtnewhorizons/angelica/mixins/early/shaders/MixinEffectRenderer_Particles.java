package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Splits the particle batch between {@code gbuffers_particles} and {@code gbuffers_particles_translucent}.
 */
@Mixin(EffectRenderer.class)
public class MixinEffectRenderer_Particles {

    @WrapOperation(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V")
    )
    private void iris$beginRun(Tessellator tessellator, Operation<Void> original) {
        ParticleRunSplitter.beginRun();
        original.call(tessellator);
    }

    @WrapOperation(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V")
    )
    private void iris$splitRun(EntityFX particle, Tessellator tessellator, float partialTicks, float rotX, float rotXZ,
                               float rotZ, float rotYZ, float rotXY, Operation<Void> original) {
        ParticleRunSplitter.splitIfNeeded(particle, tessellator, partialTicks);
        original.call(particle, tessellator, partialTicks, rotX, rotXZ, rotZ, rotYZ, rotXY);
    }
}
