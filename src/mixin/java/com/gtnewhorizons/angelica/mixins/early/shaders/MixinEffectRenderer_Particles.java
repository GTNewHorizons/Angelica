package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.client.rendering.DeferredDrawBatcher;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFireworkOverlayFX;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.client.particle.EntityFireworkStarterFX;
import net.minecraft.client.particle.EntitySpellParticleFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Puts the particle batch in its own rendering phase, and splits it between {@code gbuffers_particles} and
 * {@code gbuffers_particles_translucent}.
 */
@Mixin(EffectRenderer.class)
public class MixinEffectRenderer_Particles {

    @Unique
    private boolean iris$runTranslucent;

    @WrapMethod(method = "renderParticles")
    private void iris$particlePhase(Entity player, float partialTickTime, Operation<Void> original) {
        GbufferPrograms.beginParticles();
        try {
            original.call(player, partialTickTime);
        } finally {
            GbufferPrograms.endParticles();
        }
    }

    @WrapOperation(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V")
    )
    private void iris$beginRun(Tessellator tessellator, Operation<Void> original) {
        iris$runTranslucent = false;
        GbufferPrograms.setTranslucencyDeclaration(Boolean.FALSE);
        original.call(tessellator);
    }

    /**
     * Cut the batch when this particle needs the other program.
     */
    @WrapOperation(
        method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V")
    )
    private void iris$splitRun(EntityFX particle, Tessellator tessellator, float partialTicks, float rotX, float rotXZ,
                               float rotZ, float rotYZ, float rotXY, Operation<Void> original) {
        final boolean translucent = iris$isTranslucent(particle);

        if (translucent != iris$runTranslucent) {
            boolean iris$batcherWasActive = DeferredDrawBatcher.isActive();
            if (iris$batcherWasActive) {
                DeferredDrawBatcher.exitAndFlush();
            }
            tessellator.draw();

            iris$runTranslucent = translucent;
            GbufferPrograms.setTranslucencyDeclaration(translucent);

            tessellator.startDrawingQuads();
            if (iris$batcherWasActive) {
                DeferredDrawBatcher.enter();
            }
        }

        original.call(particle, tessellator, partialTicks, rotX, rotXZ, rotZ, rotYZ, rotXY);
    }

    /**
     * Whether this particle needs the translucent program.
     */
    @Unique
    private static boolean iris$isTranslucent(EntityFX particle) {
        if (particle.particleIcon instanceof TextureAtlasSprite sprite) {
            return ((SpriteExtension) sprite).celeritas$getTransparencyLevel() == SpriteTransparencyLevel.TRANSLUCENT;
        }

        return particle instanceof EntityFireworkSparkFX
            || particle instanceof EntityFireworkOverlayFX
            || particle instanceof EntityFireworkStarterFX
            || particle instanceof EntitySpellParticleFX
            || particle instanceof EntityCloudFX;
    }
}
