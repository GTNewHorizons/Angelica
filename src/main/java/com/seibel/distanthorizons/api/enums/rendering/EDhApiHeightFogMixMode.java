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
 * SPHERICAL                    <br>
 * CYLINDRICAL                  <br>
 *                              <br>
 * MAX                          <br>
 * ADDITION                     <br>
 * MULTIPLY                     <br>
 * INVERSE_MULTIPLY             <br>
 * LIMITED_ADDITION             <br>
 * MULTIPLY_ADDITION            <br>
 * INVERSE_MULTIPLY_ADDITION    <br>
 * AVERAGE                      <br>
 *
 * @author Leetom
 * @version 2024-4-6
 * @since API 2.0.0
 */
public enum EDhApiHeightFogMixMode
{
	/**
	 * Basic just means the fog will be based on the fragment depth
	 * not on any special height calculation IE spherical fog. <br><br>
	 * 
	 * Not to be confused with {@link EDhApiHeightFogMixMode#CYLINDRICAL}
	 * which causes fog to only apply based on horizontal distance.
	 */
	SPHERICAL(0),
	/**
	 * Fog is applied based on horizontal distance from the camera,
	 * IE cylindrical fog.
	 */
	CYLINDRICAL(1),
	
	MAX(2),
	ADDITION(3),
	MULTIPLY(4),
	INVERSE_MULTIPLY(5),
	LIMITED_ADDITION(6),
	MULTIPLY_ADDITION(7),
	INVERSE_MULTIPLY_ADDITION(8),
	AVERAGE(9);
	
	
	/** 
	 * Stable version of {@link EDhApiFogFalloff#ordinal()} 
	 * @since API 4.0.0
	 */
	public final int value;
	
	EDhApiHeightFogMixMode(int value) { this.value = value; }
		
}
