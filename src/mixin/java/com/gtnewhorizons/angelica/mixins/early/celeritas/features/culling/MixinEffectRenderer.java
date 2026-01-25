package com.gtnewhorizons.angelica.mixins.early.celeritas.features.culling;

import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EffectRenderer mixin for celeritas particle culling.
 * Culls particles that are outside the view frustum.
 */
@Mixin(EffectRenderer.class)
public class MixinEffectRenderer {

    @Unique
    private Viewport cullingViewport;

    @Unique
    private void setupCullingViewport() {
        final boolean useCulling = true;// TODO: boolean useCulling = SodiumClientMod.options().advanced.useParticleCulling;
        if(useCulling) {
            this.cullingViewport = CeleritasWorldRenderer.getInstance().getLastViewport();
        } else {
            this.cullingViewport = null;
        }
    }

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void setupViewport$standard(Entity player, float partialTickTime, CallbackInfo ci) {
        setupCullingViewport();
    }

    @Redirect(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V"))
    private void renderParticles(EntityFX particle, Tessellator tessellator, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if (cullingViewport == null || particle.boundingBox == TileEntity.INFINITE_EXTENT_AABB ||
            cullingViewport.isBoxVisible(particle.boundingBox.minX, particle.boundingBox.minY, particle.boundingBox.minZ,
                                         particle.boundingBox.maxX, particle.boundingBox.maxY, particle.boundingBox.maxZ)) {
            particle.renderParticle(tessellator, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
    }

    @Inject(method = "renderLitParticles", at = @At("HEAD"))
    private void setupViewport$lit(Entity player, float partialTickTime, CallbackInfo ci) {
        setupCullingViewport();
    }

    @Redirect(method = "renderLitParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V"))
    private void renderLitParticles(EntityFX particle, Tessellator tessellator, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if (cullingViewport == null || particle.boundingBox == TileEntity.INFINITE_EXTENT_AABB ||
            cullingViewport.isBoxVisible(particle.boundingBox.minX, particle.boundingBox.minY, particle.boundingBox.minZ,
                                         particle.boundingBox.maxX, particle.boundingBox.maxY, particle.boundingBox.maxZ)) {
            particle.renderParticle(tessellator, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
    }
}
