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

package com.seibel.distanthorizons.api.enums;

/**
 * BLOCK  - Detail Level: 0, width 1 block, <br>
 * CHUNK  - Detail Level: 4, width 16 block, <br>
 * REGION - Detail Level: 9, width 512 block <br> <br>
 *
 * Detail levels in Distant Horizons represent how large a LOD
 * is, with the smallest being 0 (1 block wide). <br>
 * The width of a detail level can be calculated by putting the detail level to the power of 2. <br>
 * Example for the chunk detail level (4): 2^4 = 16 blocks wide <Br><br>
 *
 * This enum doesn't contain all valid detail levels, only those most likely to be needed.
 * Detail levels 1,2,3, ... 255 are all technically valid detail levels
 * (although anything beyond {@link EDhApiDetailLevel#REGION} may be difficult deal with).
 *
 * @author James Seibel
 * @version 2022-12-5
 * @since API 1.0.0
 */
public enum EDhApiDetailLevel
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	/**
	 * detail level: 0 <Br>
	 * width in Blocks: 1
	 */
	BLOCK(0, 1),
	/**
	 * detail level: 4 <Br>
	 * width in Blocks: 16
	 */
	CHUNK(4, 16),
	/**
	 * detail level: 9 <Br>
	 * width in Blocks: 512
	 */
	REGION(9, 512);
	
	
	public final byte detailLevel;
	public final byte widthInBlocks;
	
	EDhApiDetailLevel(int detailLevel, int widthInBlocks)
	{
		this.detailLevel = (byte) detailLevel;
		this.widthInBlocks = (byte) widthInBlocks;
	}
	
}
