package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.renderer;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.GameSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cc.ColorizeWorld;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @ModifyArg(
        method = "renderSky(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_I(I)V"))
    private int modifyRenderSky2(int endSkyColor) {
        return ColorizeWorld.endSkyColor;
    }


    //TODO: decide if this is ok to leave out in Angelica
    /*
    @Redirect(
        method = "renderClouds(F)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z"))
    private boolean modifyRenderClouds(GameSettings instance) {
        return ColorizeWorld.drawFancyClouds(instance.fancyGraphics);
    }

     */
}
