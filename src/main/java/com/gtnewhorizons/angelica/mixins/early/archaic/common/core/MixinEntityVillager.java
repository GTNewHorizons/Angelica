package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;


import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityVillager.class)
public class MixinEntityVillager {
    @Shadow private MerchantRecipeList buyingList;

    @Shadow private int timeUntilReset;

    @Shadow private boolean needsInitilization;

    @Inject(method = "readEntityFromNBT", at = @At("TAIL"))
    private void handleLockedVillager(NBTTagCompound compound, CallbackInfo ci) {
        if(compound.hasKey("archTimeToReset")) {
            this.timeUntilReset = compound.getInteger("archTimeToReset");
            this.needsInitilization = this.timeUntilReset > 0;
        } else if(this.buyingList != null) {
            // FIX: Robustness, and fixes accumulated bugged merchants:
            if (((MerchantRecipe) this.buyingList.get(this.buyingList.size() - 1)).isRecipeDisabled()) {
                this.timeUntilReset = 40;
                this.needsInitilization = true;
            }
        }
    }

    @Inject(method = "writeEntityToNBT", at = @At("TAIL"))
    private void persistResetTime(NBTTagCompound compound, CallbackInfo ci) {
        compound.setInteger("archTimeToReset", this.timeUntilReset);
    }
}
