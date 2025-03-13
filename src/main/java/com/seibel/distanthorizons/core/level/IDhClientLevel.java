/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;

public interface IDhClientLevel extends IDhLevel
{
	void clientTick();
	
	void render(DhApiRenderParam renderEventParam, IProfilerWrapper profiler);
	void renderDeferred(DhApiRenderParam renderEventParam, IProfilerWrapper profiler);
	
	int computeBaseColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper block);
	
	IClientLevelWrapper getClientLevelWrapper();
	
	/**
	 * Re-creates the color, render data.
	 * This method should be called after resource packs are changed or LOD settings are modified.
	 */
	void clearRenderCache();
	
}
