package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderGlobal.class)
public class MixinRenderGlobal {
    @Shadow public WorldClient theWorld;
    @Shadow public Minecraft mc;

    @Inject(method = "renderClouds(F)V", at = @At("HEAD"), cancellable = true)
    private void angelica$vboClouds(float partialTicks, CallbackInfo ci) {
        final IRenderHandler renderer = theWorld.provider.getCloudRenderer();
        if (renderer != null) {
            renderer.render(partialTicks, theWorld, mc);
            ci.cancel();
        }
    }
}
