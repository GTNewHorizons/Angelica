package com.seibel.distanthorizons.core.network.messages;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

/** Implemented by messages that handle level data */
public interface ILevelRelatedMessage
{
	String getLevelName();
	
	/** Checks whether the message's level matches the given level. */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	default boolean isSameLevelAs(ILevelWrapper levelWrapper)
	{
		if (levelWrapper instanceof IServerLevelWrapper)
		{
			return this.getLevelName().equals(((IServerLevelWrapper) levelWrapper).getKeyedLevelDimensionName());
		}
		
		return this.getLevelName().equals(levelWrapper.getDhIdentifier());
	}
	
}