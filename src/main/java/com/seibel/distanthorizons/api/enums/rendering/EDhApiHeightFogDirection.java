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

package com.seibel.distanthorizons.api.enums.rendering;

/**
 * ABOVE_CAMERA,				<br>
 * BELOW_CAMERA,				<br>
 * ABOVE_AND_BELOW_CAMERA,		<br>
 * ABOVE_SET_HEIGHT,			<br>
 * BELOW_SET_HEIGHT,			<br>
 * ABOVE_AND_BELOW_SET_HEIGHT,	<br>
 *
 * @author Leetom
 * @version 2024-4-6
 * @since API 2.0.0
 */
public enum EDhApiHeightFogDirection
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	
	ABOVE_CAMERA                (true,  true,  false),
	BELOW_CAMERA                (true,  false, true),
	ABOVE_AND_BELOW_CAMERA      (true,  true,  true),
	ABOVE_SET_HEIGHT            (false, true,  false),
	BELOW_SET_HEIGHT            (false, false, true),
	ABOVE_AND_BELOW_SET_HEIGHT  (false, true,  true);
	
	public final boolean basedOnCamera;
	public final boolean fogAppliesUp;
	public final boolean fogAppliesDown;
	
	EDhApiHeightFogDirection(boolean basedOnCamera, boolean fogAppliesUp, boolean fogAppliesDown)
	{
		this.basedOnCamera = basedOnCamera;
		this.fogAppliesUp = fogAppliesUp;
		this.fogAppliesDown = fogAppliesDown;
	}
}
