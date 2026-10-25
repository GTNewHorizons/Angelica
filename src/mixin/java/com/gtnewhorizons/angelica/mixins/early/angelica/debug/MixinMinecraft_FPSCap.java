package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.screen.DebugScreen;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries;
import com.gtnewhorizons.angelica.rendering.FpsReducer;
import com.gtnewhorizons.angelica.rendering.FramePacer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft_FPSCap {

    @Shadow public String debug;

    @Inject(method = "runGameLoop", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;debug:Ljava/lang/String;", shift = At.Shift.AFTER, ordinal = 0))
    private void angelica$appendFPSCapInfo(CallbackInfo ci) {
        if (!DebugScreen.isOverlayVisible() && !DebugScreenEntries.FPS.isEnabled()) return;
        final String indicator = FramePacer.debugIndicator();
        final String tag = FpsReducer.debugTag();
        if (indicator == null && tag == null) return;
        final String suffix = indicator == null ? tag : (tag == null ? indicator : indicator + tag);
        this.debug = this.debug.replace(" fps,", " fps" + suffix + ",");
    }
}
