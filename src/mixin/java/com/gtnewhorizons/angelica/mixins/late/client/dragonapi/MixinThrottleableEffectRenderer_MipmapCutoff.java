package com.gtnewhorizons.angelica.mixins.late.client.dragonapi;

import com.gtnewhorizons.angelica.rendering.particles.ParticleAlphaCutoff;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "Reika/DragonAPI/Extras/ThrottleableEffectRenderer", remap = false)
public class MixinThrottleableEffectRenderer_MipmapCutoff {

    @ModifyArg(method = "doRenderParticles",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glAlphaFunc(IF)V", ordinal = 0),
        index = 1,
        require = 1)
    private float angelica$terrainParticleCutoff(float alphaRef, @Local(ordinal = 0) int layer) {
        return ParticleAlphaCutoff.forLayer(alphaRef, layer);
    }
}
