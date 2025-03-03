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
 * DISABLED,					<br>
 * FAKE,						<br>
 * COMPLETE,					<br>
 * 
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiTransparency
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	
	DISABLED(false, false),
	FAKE(true, true),
	COMPLETE(true, false);
	
	public final boolean transparencyEnabled;
	public final boolean fakeTransparencyEnabled;
	
	EDhApiTransparency(boolean transparencyEnabled, boolean fakeTransparencyEnabled)
	{
		this.transparencyEnabled = transparencyEnabled;
		this.fakeTransparencyEnabled = fakeTransparencyEnabled;
	}
	
}
