package com.gtnewhorizons.angelica.mixins.early.celeritas.features.culling;

import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.particles.ParticleCulling;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectRenderer.class)
public class MixinEffectRenderer {

    @Unique
    private static final Tracy.ZoneId angelica$Z_PARTICLE_PASS = Tracy.zoneId("particlePass", Tracy.COLOR_CLIENT);

    @Unique
    private CeleritasWorldRenderer cullingRenderer;

    @Unique
    private boolean particleVisible;

    @Inject(method = {"renderParticles", "renderLitParticles"}, at = @At("HEAD"))
    private void setupViewport(Entity player, float partialTickTime, CallbackInfo ci) {
        final boolean useCulling = ClientProxy.options().advanced.useParticleCulling;
        if(useCulling) {
            this.cullingRenderer = CeleritasWorldRenderer.getInstanceOrNull();
        } else {
            this.cullingRenderer = null;
        }
        if (Tracy.ENABLED) Tracy.beginZone(angelica$Z_PARTICLE_PASS);
    }

    @Inject(method = {"renderParticles", "renderLitParticles"}, at = @At("RETURN"))
    private void endParticlePass(Entity player, float partialTickTime, CallbackInfo ci) {
        if (Tracy.ENABLED) Tracy.endZone();
    }

    @Redirect(method = {"renderParticles", "renderLitParticles"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;getBrightnessForRender(F)I"))
    private int cullParticles(EntityFX particle, float partialTicks) {
        this.particleVisible = ParticleCulling.visible(this.cullingRenderer, particle.posX, particle.posY, particle.posZ);
        return this.particleVisible ? particle.getBrightnessForRender(partialTicks) : 0;
    }

    @WrapWithCondition(method = {"renderParticles", "renderLitParticles"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V"))
    private boolean renderParticles(EntityFX particle, Tessellator tessellator, float partialTicks, float rotationX, float rotationXZ, float rotationZ, float rotationYZ, float rotationXY) {
        return this.particleVisible;
    }
}
