package com.gtnewhorizons.angelica.mixins.late.client.dragonapi;

import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

    @WrapOperation(
        method = "doRenderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V", remap = true)
    )
    private void angelica$splitRun(EntityFX particle, Tessellator tessellator, float partialTicks, float rotX, float rotXZ,
                                   float rotZ, float rotYZ, float rotXY, Operation<Void> original) {
        ParticleRunSplitter.splitIfNeeded(particle, tessellator, partialTicks);
        original.call(particle, tessellator, partialTicks, rotX, rotXZ, rotZ, rotYZ, rotXY);
    }
}
