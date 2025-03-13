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
 * AUTO <br>
 * ENABLED <br>
 * DISABLED <br>
 *
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiLodShading
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	/** 
	 * Uses Minecraft's shading for LODs. <Br>
	 * This means if Minecraft's shading is disabled DH's shading will be as well.
	 */
	AUTO,
	
	/** 
	 * Simulates Minecraft's shading. <Br>
	 * This is most useful for shaders that disable Minecraft's shading
	 * but still require shading on LODs.
	 */
	ENABLED,
	
	/** LODs will have no shading */
	DISABLED;
	
}
