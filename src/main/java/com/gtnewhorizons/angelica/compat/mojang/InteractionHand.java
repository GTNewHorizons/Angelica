package com.gtnewhorizons.angelica.compat.mojang;


import com.gtnewhorizons.angelica.compat.ModStatus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import xonin.backhand.api.core.BackhandUtils;

public enum InteractionHand {
    MAIN_HAND,
    OFF_HAND;

    InteractionHand() {
    }

    public ItemStack getItemInHand(EntityPlayer player){
        if (ModStatus.isBackhandLoaded && this == InteractionHand.OFF_HAND){ // off hand (requires backhand)
            return BackhandUtils.getOffhandItem(player);
        }
        else { // main hand
            return player.getHeldItem();
        }
    }
}
