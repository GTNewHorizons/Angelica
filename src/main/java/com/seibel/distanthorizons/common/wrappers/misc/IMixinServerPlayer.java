package com.seibel.distanthorizons.common.wrappers.misc;

import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

public interface IMixinServerPlayer
{
	@Nullable
    WorldServer distantHorizons$getDimensionChangeDestination();

	void distantHorizons$setDimensionChangeDestination(WorldServer dimensionChangeDestination);
}
