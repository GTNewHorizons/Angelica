package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import com.gtnewhorizons.angelica.rendering.particles.ParticleAlphaCutoff;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.particle.EffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EffectRenderer.class)
public class MixinEffectRenderer {

    @ModifyArg(method = "renderParticles",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glAlphaFunc(IF)V", ordinal = 0, remap = false),
        index = 1)
    private float angelica$terrainParticleCutoff(float alphaRef, @Local(name = "k") int layer) {
        return ParticleAlphaCutoff.forLayer(alphaRef, layer);
    }
}
