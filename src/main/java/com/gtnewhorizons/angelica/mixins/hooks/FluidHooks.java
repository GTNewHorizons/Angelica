package com.gtnewhorizons.angelica.mixins.hooks;

import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.BlockFluidBase;

public class FluidHooks {

    /**
     * {@link BlockFluidBase#getFluid()} that uses the given Block instead of fetching it from the world again
     */
    public static double getFlowDirection(IBlockAccess world, int x, int y, int z, BlockFluidBase block) {
        if (!block.getMaterial().isLiquid()) {
            return -1000.0;
        }
        Vec3 vec = block.getFlowVector(world, x, y, z);
        return vec.xCoord == 0.0D && vec.zCoord == 0.0D ? -1000.0D : Math.atan2(vec.zCoord, vec.xCoord) - Math.PI / 2D;
    }
}
