package com.gtnewhorizons.angelica.mixins.early.dragonapi;

import com.gtnewhorizons.angelica.compat.dragonapi.DragonAPICompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "Reika/DragonAPI/Auxiliary/Trackers/SpecialDayTracker", remap = false)
public class MixinSpecialDayTracker {

    // Can't break easter eggs now can we :trolley:
    @Inject(method = "loadXmasTextures", at = @At("RETURN"))
    private void angelica$captureXmasFlag(CallbackInfoReturnable<Boolean> cir) {
        DragonAPICompat.forcingSeasonalSnow = cir.getReturnValue();
    }
}
