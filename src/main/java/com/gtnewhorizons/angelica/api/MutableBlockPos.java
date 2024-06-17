package com.gtnewhorizons.angelica.api;

import net.minecraftforge.common.util.ForgeDirection;

public interface MutableBlockPos extends BlockPos {

    /**
     * Moves this by the given offset.
     */
    MutableBlockPos move(int dX, int dY, int dZ);

    /**
     * See {@link #move(int, int, int)}
     */
    MutableBlockPos move(ForgeDirection d);

    MutableBlockPos set(int x, int y, int z);

    /**
     * Sets the position to the passed one, shifted by the direction. If {@link ForgeDirection#UNKNOWN}, no shift is
     * applied.
     */
    MutableBlockPos set(BlockPos b, ForgeDirection d);
    MutableBlockPos set(BlockPos b);
    MutableBlockPos set(long packedPos);
}
