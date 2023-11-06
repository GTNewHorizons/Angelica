package me.jellysquid.mods.sodium.client.world.biome;

import com.gtnewhorizons.angelica.compat.mojang.ItemColorProvider;
import net.minecraft.item.ItemStack;

public interface ItemColorsExtended {
    ItemColorProvider getColorProvider(ItemStack stack);
}
