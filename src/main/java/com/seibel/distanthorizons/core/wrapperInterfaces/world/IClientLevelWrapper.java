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

package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @version 2022-9-16
 */
public interface IClientLevelWrapper extends ILevelWrapper
{
	
	@Nullable
	IServerLevelWrapper tryGetServerSideWrapper();
	
	int getBlockColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper blockState);
	/** @return -1 if there was a problem getting the color */
	int getDirtBlockColor();
	void clearBlockColorCache();
	
	/** Will return null if there was an issue finding the biome. */
	@Nullable
	IBiomeWrapper getPlainsBiomeWrapper();
	
	Color getCloudColor(float tickDelta);
	
}
