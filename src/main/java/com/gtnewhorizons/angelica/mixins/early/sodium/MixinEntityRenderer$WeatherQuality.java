package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dynamic surroundings overwrites the method resulting in a clash
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer$WeatherQuality {

    @Redirect(method = "renderRainSnow(F)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z", opcode = Opcodes.GETFIELD))
    protected boolean redirectGetFancyWeather(GameSettings settings) {
        return SodiumClientMod.options().quality.weatherQuality.isFancy();
    }
}
