package com.gtnewhorizons.angelica.mixins.late.client.skidzoom;

import net.loudcats.wyatt.skidzoom.SkidZoom;
import net.minecraft.entity.player.InventoryPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryPlayer.class)
public abstract class InventoryPlayerMixin {
    @Inject(method = "changeCurrentItem", at = @At("HEAD"), cancellable = true)
    public void changeCurrentItem(int direction, CallbackInfo ci) {
        if(SkidZoom.isZooming()) {
            ci.cancel();
        }
    }
}
