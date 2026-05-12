package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Shadow
    private Minecraft mc;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void updateDynamicLights(float p_78471_1_, long p_78471_2_, CallbackInfo ci){
        // Use startSection / endSection so the section only encompasses our updateAll call —
        // not all of vanilla renderWorld, which would happen with endStartSection (vanilla's
        // own startSection calls inside renderWorld would nest under us). The previous
        // structure made the F3 profiler attribute every child cost (entity shadows,
        // particles, etc.) to angelica_dynamic_lighting.
        mc.mcProfiler.startSection("angelica_dynamic_lighting");
        DynamicLights.get().updateAll(SodiumWorldRenderer.getInstance());
        mc.mcProfiler.endSection();
    }
}
