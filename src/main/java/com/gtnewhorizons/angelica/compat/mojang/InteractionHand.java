package com.gtnewhorizons.angelica.compat.mojang;


import com.gtnewhorizons.angelica.compat.ModStatus;
import net.minecraft.client.Minecraft;
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
            return BackhandUtils.getOffhandItem(Minecraft.getMinecraft().thePlayer);
        }
        else { // main hand
            return Minecraft.getMinecraft().thePlayer.getHeldItem();
        }
    }
}
