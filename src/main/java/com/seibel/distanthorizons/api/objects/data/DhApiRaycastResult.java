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

package com.seibel.distanthorizons.api.objects.data;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;

/**
 * Holds a single datapoint of terrain data
 * and the block position from the raycast.
 *
 * @author James Seibel
 * @version 2022-11-19
 * @since API 1.0.0
 */
public class DhApiRaycastResult
{
	/**
	 * LOD position of this raycast. <br><br>
	 *
	 * <strong>Note: </strong>
	 * This will NOT be the exact block position if the LOD the ray
	 * hits is more than one block tall. In that case this will
	 * be the bottom block position for that LOD.
	 */
	public final DhApiVec3i pos;
	
	/** The LOD data at this position. */
	public final DhApiTerrainDataPoint dataPoint;
	
	
	
	public DhApiRaycastResult(DhApiTerrainDataPoint dataPoint, DhApiVec3i blockPos)
	{
		this.dataPoint = dataPoint;
		this.pos = blockPos;
	}
	
}
