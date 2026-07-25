package com.gtnewhorizons.angelica.mixins.early.notfine.clouds;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import jss.notfine.core.SettingsManager;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = WorldProvider.class)
public abstract class MixinWorldProvider {

    @ModifyReturnValue(method = "getCloudHeight", at = @At("RETURN"), remap = false)
    private float notFine$applyCloudHeightOffset(float original) {
        return original + SettingsManager.cloudHeightOffset;
    }

}
