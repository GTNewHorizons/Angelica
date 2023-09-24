package com.gtnewhorizons.angelica.mixins.early.archaic.client.core;

import cpw.mods.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SplashProgress.class)
public interface AccessorSplashProgress {
    @Accessor(value="barBorderColor", remap = false)
    static int getBarBorderColor() {
        throw new AssertionError();
    }
    @Accessor(value="barBackgroundColor", remap = false)
    static int getBarBackgroundColor() {
        throw new AssertionError();
    }
    @Accessor(value="fontColor", remap = false)
    static int getFontColor() {
        throw new AssertionError();
    }
}
