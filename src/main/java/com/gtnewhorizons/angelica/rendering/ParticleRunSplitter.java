package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.client.rendering.DeferredDrawBatcher;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFireworkOverlayFX;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.client.particle.EntityFireworkStarterFX;
import net.minecraft.client.particle.EntitySpellParticleFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;

/**
 * Splits a particle run between {@code gbuffers_particles} and {@code gbuffers_particles_translucent}.
 */
public final class ParticleRunSplitter {

    private static boolean runTranslucent;

    private ParticleRunSplitter() {}

    public static void beginRun() {
        runTranslucent = false;
        GbufferPrograms.setTranslucencyDeclaration(Boolean.FALSE);
    }

    public static void splitIfNeeded(EntityFX particle, Tessellator tessellator, float partialTicks) {
        final boolean translucent = isTranslucent(particle);
        if (translucent == runTranslucent) {
            return;
        }

        final boolean batcherWasActive = DeferredDrawBatcher.isActive();
        if (batcherWasActive) {
            DeferredDrawBatcher.exitAndFlush();
        }
        tessellator.draw();

        runTranslucent = translucent;
        GbufferPrograms.setTranslucencyDeclaration(translucent);

        tessellator.startDrawingQuads();
        tessellator.setBrightness(particle.getBrightnessForRender(partialTicks));
        if (batcherWasActive) {
            DeferredDrawBatcher.enter();
        }
    }

    public static boolean isTranslucent(EntityFX particle) {
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
