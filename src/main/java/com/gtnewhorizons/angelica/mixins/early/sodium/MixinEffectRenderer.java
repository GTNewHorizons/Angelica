package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
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
    private Frustrum cullingFrustum;

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void setupFrustum$standart(Entity player, float partialTickTime, CallbackInfo ci) {
        Frustrum frustum = SodiumWorldRenderer.getInstance().getFrustum();
        boolean useCulling = SodiumClientMod.options().advanced.useParticleCulling;
        // Setup the frustum state before rendering particles
        if (useCulling && frustum != null) {
            this.cullingFrustum = frustum;
        } else {
            this.cullingFrustum = null;
        }
    }

    @Redirect(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V"))
    private void renderParticles(EntityFX particle, Tessellator tessellator, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if(cullingFrustum == null || cullingFrustum.isBoundingBoxInFrustum(particle.boundingBox)) {
            particle.renderParticle(tessellator, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
    }

    @Inject(method = "renderLitParticles", at = @At("HEAD"))
    private void setupFrustum$lit(Entity player, float partialTickTime, CallbackInfo ci) {
        Frustrum frustum = SodiumWorldRenderer.getInstance().getFrustum();
        boolean useCulling = SodiumClientMod.options().advanced.useParticleCulling;
        // Setup the frustum state before rendering particles
        if (useCulling && frustum != null) {
            this.cullingFrustum = frustum;
        } else {
            this.cullingFrustum = null;
        }
    }

    @Redirect(method = "renderLitParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V"))
    private void renderLitParticles(EntityFX particle, Tessellator tessellator, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if(cullingFrustum == null || cullingFrustum.isBoundingBoxInFrustum(particle.boundingBox)) {
            particle.renderParticle(tessellator, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
    }

}
