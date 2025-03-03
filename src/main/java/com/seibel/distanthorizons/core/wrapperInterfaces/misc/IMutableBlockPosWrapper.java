package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;

/**
 * Currently this wrapper is just used to prevent 
 * accidentally passing in the wrong object to
 * {@link IChunkWrapper#getBlockState(int, int, int, IMutableBlockPosWrapper, IBlockStateWrapper)}
 */
public interface IMutableBlockPosWrapper extends IDhApiUnsafeWrapper
{
	
	
}
