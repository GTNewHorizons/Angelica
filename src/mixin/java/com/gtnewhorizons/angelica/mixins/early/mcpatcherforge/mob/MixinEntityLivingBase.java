package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.mob;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.mob.MobRandomizer;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    @Inject(method = "writeEntityToNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"))
    private void modifyWriteEntityToNBT(NBTTagCompound tagCompound, CallbackInfo ci) {
        MobRandomizer.ExtraInfo.writeToNBT((EntityLivingBase) (Object) this, tagCompound);
    }

    @Inject(method = "readEntityFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"))
    private void modifyReadEntityFromNBT(NBTTagCompound tagCompound, CallbackInfo ci) {
        MobRandomizer.ExtraInfo.readFromNBT((EntityLivingBase) (Object) this, tagCompound);
    }
}
