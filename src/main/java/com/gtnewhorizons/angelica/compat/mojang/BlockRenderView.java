package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

public interface BlockRenderView extends BlockView  {

    float getBrightness(ForgeDirection face, boolean shade);

    LightingProvider getLightingProvider();

    TileEntity getBlockEntity(BlockPos pos);

    int getColor(BlockPos pos, ColorResolver resolver);

    int getLightLevel(EnumSkyBlock type, BlockPos pos);

    BiomeGenBase getBiomeForNoiseGen(int x, int y, int z);

    FluidState getFluidState(BlockPos set);
}
