package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.item.ItemStack;

public interface ItemColorProvider {
    int getColor(ItemStack stack, int tintIndex);
}
