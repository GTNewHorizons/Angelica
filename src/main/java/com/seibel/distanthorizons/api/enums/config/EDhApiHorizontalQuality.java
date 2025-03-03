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
 * LOWEST <br>
 * LOW <br>
 * MEDIUM <br>
 * HIGH <br>
 * EXTREME <br>
 *
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiHorizontalQuality
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	
	// Note: any quadraticBase less than 2.0f has issues with DetailDistanceUtil, and will always return the lowest detail level.
	//  So for now we are limiting the lowest value to 2.0
	//  LOWEST was originally 1.0f and LOW was 1.5f
	
	LOWEST(2.0f, 4),
	LOW(2.0f, 8),
	MEDIUM(2.0f, 12),
	HIGH(2.2f, 16),
	EXTREME(2.4f, 32),
	;
	
	
	
	public final double quadraticBase;
	public final int distanceUnitInBlocks;
	
	EDhApiHorizontalQuality(double quadraticBase, int distanceUnitInBlocks)
	{
		this.quadraticBase = quadraticBase;
		this.distanceUnitInBlocks = distanceUnitInBlocks;
	}
	
}