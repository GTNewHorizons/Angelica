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

package com.seibel.distanthorizons.core.enums.worldGeneration;

/**
 * MULTI_THREADED, <br>
 * SINGLE_THREADED, <br>
 * SERVER_THREAD, <br>
 *
 * @author James Seibel
 * @version 7-25-2022
 */
public enum EWorldGenThreadMode
{
	/**
	 * This world generator can be run on an unlimited number
	 * of concurrent threads.
	 */
	MULTI_THREADED,
	
	/**
	 * This world generator can only be run on one thread at
	 * a time, however that thread can run concurrently
	 * to Minecraft's server thread.
	 */
	SINGLE_THREADED,
	
	/**
	 * This world generator can only be run on Minecraft's
	 * server thread.
	 */
	SERVER_THREAD,
}
