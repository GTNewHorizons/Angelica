package com.gtnewhorizons.angelica.rendering.particles;

import me.jellysquid.mods.sodium.client.gui.options.named.MipmapGeneration;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;

public final class ParticleAlphaCutoff {

    private static final int TERRAIN_PARTICLE_LAYER = 1;

    private ParticleAlphaCutoff() {
    }

    public static float forLayer(float original, int layer) {
        if (layer != TERRAIN_PARTICLE_LAYER) {
            return original;
        }
        final AlphaCutoffParameter cutoff = MipmapGeneration.alphaFloorCutoff();
        return cutoff == AlphaCutoffParameter.ZERO ? original : cutoff.cutoff();
    }
}
