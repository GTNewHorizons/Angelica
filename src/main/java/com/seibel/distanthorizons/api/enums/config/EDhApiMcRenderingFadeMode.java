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
 * Handles how Minecraft's rendering
 * is faded out to smooth the transition
 * between MC and DH rendering. <br><br>
 * 
 * NONE, <br>
 * SINGLE_PASS, <br>
 * DOUBLE_PASS, <br>
 *
 * @since API 4.0.0
 * @version 2024-10-3
 */
public enum EDhApiMcRenderingFadeMode
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	/**
	 * No fading is done, there will be a pronounced border between
	 * Minecraft and Distant Horizons. <br>
	 * Fastest.
	 */
	NONE,
	/**
	 * Fading only runs after the translucent render pass. <br>
	 * Looks good for the tops of oceans and rivers, but
	 * doesn't fade the opaque blocks underwater.
	 */
	SINGLE_PASS,
	/** 
	 * Fading runs after both opaque and translucent render passes. 
	 * Slowest, but oceans and rivers look better.
	 */
	DOUBLE_PASS;
	
}