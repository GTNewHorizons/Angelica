package com.gtnewhorizons.angelica.api;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import net.minecraftforge.common.util.ForgeDirection;

public interface BlockPos {
    int getX();
    int getY();
    int getZ();
    BlockPosImpl offset(ForgeDirection d);
    BlockPosImpl down();
    BlockPosImpl up();
    long asLong();

    static long asLong(int x, int y, int z) {
        return CoordinatePacker.pack(x, y, z);
    }
}
