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

/**
 * MERGE_SAME_BLOCKS <br>
 * VISUALLY_EQUAL <br><br>
 * 
 * @version 2024-3-27
 * @since API 2.0.0
 */
public enum EDhApiWorldCompressionMode
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	/** 
	 * Every block/biome change is recorded in the database. <br>
	 * This is what DH 2.0 and 2.0.1 all used by default and will store a lot of data. 
	 */
	MERGE_SAME_BLOCKS(0),
	
	/** 
	 * Only visible block/biome changes are recorded in the database. <Br> 
	 * Hidden blocks (IE ores) are ignored. 
	 */
	VISUALLY_EQUAL(1);
	
	
	
	/** More stable than using the ordinal of the enum */
	public final byte value;
	
	EDhApiWorldCompressionMode(int value) { this.value = (byte) value; }
	
	
	public static EDhApiWorldCompressionMode getFromValue(byte value)
	{
		EDhApiWorldCompressionMode[] enumList = EDhApiWorldCompressionMode.values();
		for (int i = 0; i < enumList.length; i++)
		{
			if (enumList[i].value == value)
			{
				return enumList[i];
			}
		}
		
		throw new IllegalArgumentException("No lossy compression mode with the value ["+value+"]");
	}
	
	
}
