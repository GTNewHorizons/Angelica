package com.gtnewhorizons.angelica.mixins.early.angelica.particles;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.ffp.DeferredDrawBatcher;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures individual particle draw() calls and merges them into a single draw call per unique GL state.
 */
@Mixin(EffectRenderer.class)
public class MixinEffectRenderer_DeferredBatch {

    @Inject(method = "renderParticles",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V",
            shift = At.Shift.AFTER))
    private void angelica$enterDeferredMode(Entity player, float partialTickTime, CallbackInfo ci) {
        if (AngelicaMod.options() != null && AngelicaMod.options().advanced.enableDeferredBatching) {
            DeferredDrawBatcher.enter();
        }
    }

    @Inject(method = "renderParticles",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()I",
            shift = At.Shift.BEFORE))
    private void angelica$exitDeferredAndFlush(Entity player, float partialTickTime, CallbackInfo ci) {
        if (DeferredDrawBatcher.isActive()) {
            DeferredDrawBatcher.exitAndFlush();
        }
    }
}
