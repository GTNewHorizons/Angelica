package me.jellysquid.mods.sodium.client.world.biome;

import com.gtnewhorizons.angelica.compat.mojang.BlockColorProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;

public interface BlockColorsExtended {
    BlockColorProvider getColorProvider(BlockState state);
}
