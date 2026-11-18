package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.item.ItemStack;

/**
 * Whether a mob's worn armor piece is enchanted, cached per stack instance.
 */
public interface ArmorEnchantCache {
    boolean angelica$isArmorEnchanted(int slot, ItemStack stack);
}
