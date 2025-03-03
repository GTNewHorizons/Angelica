package com.seibel.distanthorizons.common.wrappers.misc;

import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;

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
