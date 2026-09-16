package com.gtnewhorizons.angelica.mixins.late.client.dragonapi;

import com.gtnewhorizons.angelica.rendering.particles.ParticleInstancer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = { "Reika/DragonAPI/Extras/ThrottleableEffectRenderer" }, remap = false)
public class MixinThrottleableEffectRenderer_ParticleBatch {

    @WrapOperation(method = "doRenderParticles",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V",
            remap = true),
        require = 1)
    private void angelica$captureParticle(EntityFX particle, Tessellator tessellator, float partialTicks, float rotationX, float rotationXZ, float rotationZ, float rotationYZ, float rotationXY, Operation<Void> original) {
        ParticleInstancer.renderParticle(particle, tessellator, partialTicks, rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY, original);
    }

    @WrapOperation(method = "doRenderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", remap = true),
        require = 1)
    private int angelica$endLayer(Tessellator tessellator, Operation<Integer> original) {
        ParticleInstancer.endLayer();
        return original.call(tessellator);
    }
}
