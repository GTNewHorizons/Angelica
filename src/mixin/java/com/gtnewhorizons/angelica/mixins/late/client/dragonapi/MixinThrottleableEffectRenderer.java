package com.gtnewhorizons.angelica.mixins.late.client.dragonapi;

import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = { "Reika/DragonAPI/Extras/ThrottleableEffectRenderer" }, remap = false)
public class MixinThrottleableEffectRenderer {

    @Redirect(
        method = "doRenderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V", remap = true)
    )
    private void angelica$beginRun(Tessellator tessellator) {
        ParticleRunSplitter.beginRun();
        tessellator.startDrawingQuads();
    }

    @Redirect(
        method = "doRenderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;getBrightnessForRender(F)I", remap = true),
        require = 1
    )
    private int angelica$splitRun(EntityFX particle, float partialTicks) {
        ParticleRunSplitter.splitIfNeeded(particle, Tessellator.instance, partialTicks);
        return particle.getBrightnessForRender(partialTicks);
    }
}
