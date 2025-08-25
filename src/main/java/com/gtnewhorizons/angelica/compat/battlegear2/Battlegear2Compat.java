package com.gtnewhorizons.angelica.compat.battlegear2;

import mods.battlegear2.api.core.IInventoryPlayerBattle;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * This class exists to isolate classes that exist in Battlegear 2 but not
 * in Battlegear 2 for Backhand in order to avoid class loading exceptions.
 */
public class Battlegear2Compat {
    public static ItemStack getBattlegear2Offhand(EntityPlayer player) {
        return ((IInventoryPlayerBattle) player.inventory).battlegear2$getCurrentOffhandWeapon();
    }
}
