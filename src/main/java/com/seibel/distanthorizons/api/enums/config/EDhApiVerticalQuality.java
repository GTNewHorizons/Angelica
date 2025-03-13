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

package com.seibel.distanthorizons.api.enums.config;

import com.seibel.distanthorizons.coreapi.util.MathUtil;

/**
 * HEIGHT_MAP <br>
 * LOW <br>
 * MEDIUM <br>
 * HIGH <br>
 * VERY_HIGH <br>
 * EXTREME <br>
 * PIXEL_ART <br>
 *
 * @author Leonardo Amato
 * @version 2024-4-6
 * @since API 2.0.0
 */
public enum EDhApiVerticalQuality
{
	HEIGHT_MAP( new int[]{1,     1,   1,  1,  1,  1,  1,  1,  1,  1, 1}),
	LOW(        new int[]{4,     4,   4,  3,  3,  3,  3,  3,  3,  3, 1}),
	MEDIUM(     new int[]{6,     6,   6,  4,  4,  4,  4,  4,  4,  4, 1}),
	HIGH(       new int[]{16,   16,  12, 12,  8,  8,  8,  8,  8,  8, 1}),
	VERY_HIGH(  new int[]{32,   16,  12, 12, 12, 12, 12, 12, 12, 12, 1}),
	EXTREME(    new int[]{64,   32,  32, 32, 16, 16, 16, 16, 16, 16, 1}),
	PIXEL_ART(  new int[]{512, 256, 128, 64, 32, 32, 16, 16, 16, 16, 1});
	
	/** represents how many LODs can be rendered in a single vertical slice */
	public final int[] maxVerticalData;
	
	
	
	EDhApiVerticalQuality(int[] maxVerticalData) { this.maxVerticalData = maxVerticalData; }
	
	
	
	public int calculateMaxVerticalData(byte dataDetail)
	{
		// for detail levels lower than what the enum defines, use the lowest quality item
		int index = MathUtil.clamp(0, dataDetail, this.maxVerticalData.length - 1);
		return this.maxVerticalData[index];
	}
	
}