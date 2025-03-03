package com.seibel.distanthorizons.common.wrappers.misc;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public interface IMixinServerPlayer
{
	@Nullable
	ServerLevel distantHorizons$getDimensionChangeDestination();
	
	#if MC_VER == MC_1_16_5
	void distantHorizons$setDimensionChangeDestination(ServerLevel dimensionChangeDestination);
	#endif
	
}
