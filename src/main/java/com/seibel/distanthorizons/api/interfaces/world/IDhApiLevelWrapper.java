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

package com.seibel.distanthorizons.api.interfaces.world;

import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;

import java.io.File;

/**
 * Can be either a Server or Client level.<br>
 * A level is equivalent to a dimension in vanilla Minecraft.
 *
 * @author James Seibel
 * @version 2024-7-28
 * @since API 1.0.0
 */
public interface IDhApiLevelWrapper extends IDhApiUnsafeWrapper
{
	IDhApiDimensionTypeWrapper getDimensionType();
	
	/** @since API 4.0.0 */
	String getDimensionName();
	
	/**
	 * Returns a string intended to uniquely identify this level.
	 *
	 * @since API 4.0.0
	 */
	String getDhIdentifier();

	EDhApiLevelType getLevelType();
	
	boolean hasCeiling();
	
	boolean hasSkyLight();
	
	/** 
	 * Deprecated, use {@link IDhApiLevelWrapper#getMaxHeight} instead. <br>
	 * Returns the max block height of the level.
	 * 
	 * @see IDhApiLevelWrapper#getMaxHeight
	 */
	@Deprecated
	default int getHeight() { return this.getMaxHeight(); }
	/** 
	 * Returns the max block height of the level 
	 * @since API 3.0.0 
	 */
	int getMaxHeight();
	
	/**
	 * Returns the lowest possible block position for the level. <br>
	 * For MC versions before 1.18 this will return 0.
	 */
	default int getMinHeight() { return 0; }
	
	/** 
	 * Will return null if called on the server,
	 * or if called before the renderer has been set up.
	 * 
	 * @since API 3.0.0
	 */
	IDhApiCustomRenderRegister getRenderRegister();
	
	/**
	 * Returns the folder Distant Horizons uses to save
	 * data associated with this level. 
	 * Will return null if the level is not loaded.
	 *
	 * @since API 4.0.0
	 */
	File getDhSaveFolder();
	
	
	
}
