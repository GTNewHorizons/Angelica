package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Splits the particle batch between {@code gbuffers_particles} and {@code gbuffers_particles_translucent}.
 */
@Mixin(EffectRenderer.class)
public class MixinEffectRenderer_Particles {

    @Redirect(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V")
    )
    private void iris$beginRun(Tessellator tessellator) {
        ParticleRunSplitter.beginRun();
        tessellator.startDrawingQuads();
    }

    @Redirect(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;getBrightnessForRender(F)I"),
        require = 1
    )
    private int iris$splitRun(EntityFX particle, float partialTicks) {
        ParticleRunSplitter.splitIfNeeded(particle, Tessellator.instance, partialTicks);
        return particle.getBrightnessForRender(partialTicks);
    }
}
