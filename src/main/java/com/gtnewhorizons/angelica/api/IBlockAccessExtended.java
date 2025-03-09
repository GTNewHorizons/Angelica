package com.gtnewhorizons.angelica.api;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public interface IBlockAccessExtended extends IBlockAccess {
    World getWorld();
}
