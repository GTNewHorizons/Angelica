package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.common.util.ForgeDirection;

@Deprecated
public interface BlockRenderView extends BlockView  {

    float getBrightness(ForgeDirection face, boolean shade);

    TileEntity getTileEntity(BlockPos pos);

    int getLightLevel(EnumSkyBlock type, BlockPos pos);

    FluidState getFluidState(BlockPos set);
}
