package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EffectRenderer.class, priority = 900)
public class MixinEffectRenderer_Particles {


    @Redirect(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V")
    )
    private void iris$beginRun(Tessellator tessellator) {
        ParticleRunSplitter.beginRun();
        tessellator.startDrawingQuads();
    }

    @ModifyReceiver(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;getBrightnessForRender(F)I"),
        require = 1
    )
    private EntityFX iris$splitRun(EntityFX particle, float partialTicks) {
        ParticleRunSplitter.splitIfNeeded(particle, Tessellator.instance, partialTicks);
        return particle;
    }
}
