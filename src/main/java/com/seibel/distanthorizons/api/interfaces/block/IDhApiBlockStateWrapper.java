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

package com.seibel.distanthorizons.api.interfaces.block;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;

/**
 * A Minecraft version independent way of handling Blocks.
 *
 * @author James Seibel
 * @version 2023-6-11
 * @since API 1.0.0
 */
public interface IDhApiBlockStateWrapper extends IDhApiUnsafeWrapper
{
	/** @since API 1.0.0 */
	boolean isAir();
	
	/** @since API 1.0.0 */
	boolean isSolid();
	/** @since API 1.0.0 */
	boolean isLiquid();
	
	/**
	 * Returns the full serialized form of the given block
	 * as defined by DH's serialization methods.
	 * @since API 3.0.0 
	 */
	String getSerialString();
	/**
	 * Returns the byte value representing the {@link EDhApiBlockMaterial} enum.
	 * @see EDhApiBlockMaterial 
	 * @since API 3.0.0 
	 */
	byte getMaterialId();
	
}
