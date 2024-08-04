package com.gtnewhorizons.angelica.mixins.early.angelica.archaic;

import cpw.mods.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SuppressWarnings("deprecation")
@Mixin(SplashProgress.class)
public interface AccessorSplashProgress {

    @Accessor(remap = false)
    static int getBarBorderColor() {
        throw new AssertionError();
    }
    @Accessor(remap = false)
    static int getBarBackgroundColor() {
        throw new AssertionError();
    }
    @Accessor(remap = false)
    static int getFontColor() {
        throw new AssertionError();
    }
}
