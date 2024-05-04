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
        mc.mcProfiler.endStartSection("angelica_dynamic_lighting");
        DynamicLights.get().updateAll(SodiumWorldRenderer.getInstance());
    }
}
