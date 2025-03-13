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
 * USE_DEFAULT_FOG_COLOR, <br>
 * USE_SKY_COLOR, <br>
 *
 * @author James Seibel
 * @version 2024-4-6
 * @since API 2.0.0
 */
public enum EDhApiFogColorMode
{
	// Reminder:
	// when adding items: up the API minor version
	// when removing items: up the API major version
	
	/** Fog uses Minecraft's fog color. */
	USE_WORLD_FOG_COLOR,
	
	/**
	 * Replicates the effect of the clear sky mod.
	 * Making the fog blend in with the sky better
	 * For it to look good you need one of the following mods:
	 * https://www.curseforge.com/minecraft/mc-mods/clear-skies
	 * https://www.curseforge.com/minecraft/mc-mods/clear-skies-forge-port
	 */
	USE_SKY_COLOR,
	
}
