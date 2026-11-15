package com.gtnewhorizons.angelica.api.particles;

import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.particle.EntityFX;

import java.util.Arrays;

/** For mods that draw particles themselves or change their size. */
public final class ParticleRendering {

    private ParticleRendering() {}

    /** A size factor per particle, 1 for a normal one. */
    @FunctionalInterface
    public interface RenderScale {

        float of(EntityFX fx);
    }

    private static final RenderScale[] NO_SCALES = new RenderScale[0];
    // Swapped for a new array on every change, so the per particle read is one field and a length check
    private static volatile RenderScale[] renderScales = NO_SCALES;

    /**
     * Scales the particles Angelica batches straight from their fields without calling {@code renderParticle}, which
     * is the ones that don't override it plus the vanilla and BoP ones it knows. Particles that do go through
     * {@code renderParticle} keep whatever size that gives, so scale those there. Every source added gets asked about
     * every batched particle and the factors multiply, so return 1 for particles that aren't yours.
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

    /** The size factor for this particle from every {@link #addRenderScale} source, 1 without any. */
    public static float renderScale(EntityFX fx) {
        final RenderScale[] sources = renderScales;
        if (sources.length == 0) return 1;
        float scale = 1;
        for (RenderScale source : sources) scale *= source.of(fx);
        return scale;
    }

    /**
     * Particles drawn outside the effect renderer (e.g. a mod's own list) get the pack's particle program between this
     * and {@link #endParticleDraw}, like the world's particles. Pass the returned token to the end call. Nests. Without
     * a pack nothing changes.
     */
    public static int beginParticleDraw() {
        return GbufferPrograms.beginParticles();
    }

    public static void endParticleDraw(int token) {
        GbufferPrograms.endParticles(token);
    }
}
