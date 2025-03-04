package com.seibel.distanthorizons.common.wrappers.misc;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;

public class MutableBlockPosWrapper implements IMutableBlockPosWrapper
{
	public final BlockPos pos;



	//=============//
	// constructor //
	//=============//

	public MutableBlockPosWrapper()
	{
		this.pos = new BlockPos();
	}



	//===========//
	// overrides //
	//===========//

	@Override
	public Object getWrappedMcObject() { return this.pos; }

}
