package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds FPS cap indicator to the F3 debug string.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_FPSCap {

    @Shadow public String debug;
    @Shadow public GameSettings gameSettings;

    @Inject(method = "runGameLoop", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;debug:Ljava/lang/String;", shift = At.Shift.AFTER, ordinal = 0))
    private void angelica$appendFPSCapInfo(CallbackInfo ci) {
        final int limit = this.gameSettings.limitFramerate;
        String indicator = null;
        if (this.gameSettings.enableVsync) {
            indicator = " [vsync]";
        } else if (limit < 260) {
            indicator = " [cap " + limit + "]";
        }
        if (indicator != null) {
            this.debug = this.debug.replace(" fps,", " fps" + indicator + ",");
        }
    }
}
