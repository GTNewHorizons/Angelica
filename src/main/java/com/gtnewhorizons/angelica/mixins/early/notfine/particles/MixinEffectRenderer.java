package com.gtnewhorizons.angelica.mixins.early.notfine.particles;

import net.minecraft.client.particle.EffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EffectRenderer.class)
public abstract class MixinEffectRenderer {

    /**
     * @author jss2a98aj
     * @reason Makes most particles drawScreen with the expected depth.
     */
    @Redirect(
        method = "renderParticles",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V",
            ordinal = 0,
            remap = false
        )
    )
    private void skipGlDepthMask(boolean flag) {
    }

}
