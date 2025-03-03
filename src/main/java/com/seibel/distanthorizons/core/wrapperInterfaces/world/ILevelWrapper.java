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

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/** Can be either a Server world or a Client world. */
public interface ILevelWrapper extends IDhApiLevelWrapper, IBindable
{
	@Override
	IDimensionTypeWrapper getDimensionType();
	
	@Override
	String getDimensionName();
	
	long getHashedSeed();
	/**
	 * Returns the result of {@link #getHashedSeed()}, encoded into a short string. <br>
	 * Prefer using this method over stringifying the number directly.
	 */
	default String getHashedSeedEncoded()
	{
		String encoded = BaseEncoding.base32Hex().encode(Longs.toByteArray(this.getHashedSeed()));
		return encoded.substring(0, 13).toLowerCase(); // Remaining 3 chars are padding
	}
	
	/**
	 * A string intended to uniquely identify this level.
	 */
	@Override
	String getDhIdentifier();
	
	@Override
	boolean hasCeiling();
	
	@Override
	boolean hasSkyLight();
	
	@Override
	int getMaxHeight();
	@Override
	default int getMinHeight() { return 0; }
	
	default IChunkWrapper tryGetChunk(DhChunkPos pos) { return null; }
	
	boolean hasChunkLoaded(int chunkX, int chunkZ);
	
	@Deprecated
	IBlockStateWrapper getBlockState(DhBlockPos pos);
	
	@Deprecated
	IBiomeWrapper getBiome(DhBlockPos pos);
	
	/** Fired when the level is being unloaded. Doesn't unload the level. */
	void onUnload();
	
	// TODO James doesn't like this circular reference, can we merge the level wrapper and DhLevels?
	@Deprecated
	void setParentLevel(IDhLevel parentLevel);
	
}
