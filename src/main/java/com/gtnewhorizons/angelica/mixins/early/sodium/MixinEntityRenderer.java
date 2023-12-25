package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.compat.FogHelper;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Shadow
    float fogColorRed, fogColorGreen, fogColorBlue;
    @Inject(method = "updateFogColor", at = @At("RETURN"))
    private void storeFog(CallbackInfo ci) {
        FogHelper.red = fogColorRed;
        FogHelper.green = fogColorGreen;
        FogHelper.blue = fogColorBlue;
    }

    @Redirect(method = "renderRainSnow(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z", opcode = Opcodes.GETFIELD))
    protected boolean redirectGetFancyWeather(GameSettings settings) {
        return SodiumClientMod.options().quality.weatherQuality.isFancy();
    }
}
