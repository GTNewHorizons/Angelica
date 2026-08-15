package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.rendering.FramePacer;
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
        if (!this.gameSettings.showDebugInfo) return;
        final String indicator = FramePacer.debugIndicator();
        if (indicator == null) return;
        this.debug = this.debug.replace(" fps,", " fps" + indicator + ",");
    }
}
