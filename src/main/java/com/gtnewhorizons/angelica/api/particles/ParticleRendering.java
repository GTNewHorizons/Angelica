package com.gtnewhorizons.angelica.api.particles;

import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.particle.EntityFX;

import java.util.Arrays;

/** Particle size and shader hooks for mods that scale or draw their own particles */
public final class ParticleRendering {

    private ParticleRendering() {}

    /** Size factor for a particle, 1 for unchanged */
    @FunctionalInterface
    public interface RenderScale {

        float of(EntityFX fx);
    }

    private static final RenderScale[] NO_SCALES = new RenderScale[0];
    // Copy on write so the per particle read is one volatile load
    private static volatile RenderScale[] renderScales = NO_SCALES;

    /**
     * Scales particles the instancer batches from their fields, without calling {@code renderParticle}. Particles that
     * go through {@code renderParticle} need scaling there instead. Every source sees every batched particle and the
     * factors multiply, so return 1 for particles that aren't yours.
     */
    public static synchronized void addRenderScale(RenderScale source) {
        for (RenderScale existing : renderScales) {
            if (existing == source) return;
        }
        final RenderScale[] next = Arrays.copyOf(renderScales, renderScales.length + 1);
        next[next.length - 1] = source;
        renderScales = next;
    }

    public static synchronized void removeRenderScale(RenderScale source) {
        final RenderScale[] old = renderScales;
        for (int i = 0; i < old.length; i++) {
            if (old[i] != source) continue;
            final RenderScale[] next = new RenderScale[old.length - 1];
            System.arraycopy(old, 0, next, 0, i);
            System.arraycopy(old, i + 1, next, i, old.length - i - 1);
            renderScales = next.length == 0 ? NO_SCALES : next;
            return;
        }
    }

    /** Product of every {@link #addRenderScale} source for this particle */
    public static float renderScale(EntityFX fx) {
        final RenderScale[] sources = renderScales;
        if (sources.length == 0) return 1;
        float scale = 1;
        for (RenderScale source : sources) scale *= source.of(fx);
        return scale;
    }

    /**
     * Binds the pack's particle program for particles drawn outside the effect renderer. Nests, pass the token to
     * {@link #endParticleDraw}. No-op without a shaderpack.
     */
    public static int beginParticleDraw() {
        return GbufferPrograms.beginParticles();
    }

    public static void endParticleDraw(int token) {
        GbufferPrograms.endParticles(token);
    }
}
