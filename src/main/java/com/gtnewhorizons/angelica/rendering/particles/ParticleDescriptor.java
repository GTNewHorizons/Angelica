package com.gtnewhorizons.angelica.rendering.particles;

import net.minecraft.client.particle.EntityFX;

public interface ParticleDescriptor {

    boolean describe(EntityFX fx, float partialTicks, ParticleParams out, ParticleRenderState state);
}
