package com.gtnewhorizons.angelica.compat.mojang;


import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.backhand.BackhandReflectionCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public enum InteractionHand {
    MAIN_HAND,
    OFF_HAND;

    public ItemStack getItemInHand(EntityPlayer player) {
        if (ModStatus.isBackhandLoaded && this == InteractionHand.OFF_HAND) { // off hand (requires backhand)
            return BackhandReflectionCompat.getOffhandItem(player);
        } else { // main hand
            return player.getHeldItem();
        }
    }
}
