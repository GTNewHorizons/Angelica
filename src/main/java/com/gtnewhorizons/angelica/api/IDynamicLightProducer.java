package com.gtnewhorizons.angelica.api;

import net.minecraft.item.ItemStack;

/**
 * To be used for modded items that don't have blocks. Baubles for example.
 */
public interface IDynamicLightProducer {
    int getLuminance(ItemStack stack);
}
