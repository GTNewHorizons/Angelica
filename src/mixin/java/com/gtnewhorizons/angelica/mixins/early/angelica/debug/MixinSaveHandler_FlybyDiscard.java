package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SaveHandler.class)
public abstract class MixinSaveHandler_FlybyDiscard {

    @Inject(method = "writePlayerData", at = @At("HEAD"), cancellable = true)
    private void angelica$flybyDiscardPlayer(EntityPlayer player, CallbackInfo ci) {
        if (FlybyRunner.worldChangesDiscarded()) ci.cancel();
    }

    @Inject(method = "saveWorldInfoWithPlayer", at = @At("HEAD"), cancellable = true)
    private void angelica$flybyDiscardLevelWithPlayer(WorldInfo info, NBTTagCompound playerData, CallbackInfo ci) {
        if (FlybyRunner.worldChangesDiscarded()) ci.cancel();
    }

    @Inject(method = "saveWorldInfo", at = @At("HEAD"), cancellable = true)
    private void angelica$flybyDiscardLevel(WorldInfo info, CallbackInfo ci) {
        if (FlybyRunner.worldChangesDiscarded()) ci.cancel();
    }
}
