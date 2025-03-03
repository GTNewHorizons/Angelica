package com.seibel.distanthorizons.common.wrappers.misc;

import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import net.minecraft.core.BlockPos;

public class MutableBlockPosWrapper implements IMutableBlockPosWrapper
{
	public final BlockPos.MutableBlockPos pos;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public MutableBlockPosWrapper()
	{
		this.pos = new BlockPos.MutableBlockPos(); 
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override 
	public Object getWrappedMcObject() { return this.pos; }
	
}
