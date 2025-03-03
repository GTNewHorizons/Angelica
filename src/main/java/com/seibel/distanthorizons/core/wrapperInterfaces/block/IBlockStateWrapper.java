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

package com.seibel.distanthorizons.core.wrapperInterfaces.block;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.core.util.LodUtil;

import java.awt.*;

/** A Minecraft version independent way of handling Blocks. */
public interface IBlockStateWrapper extends IDhApiBlockStateWrapper
{
	//=========//
	// methods //
	//=========//
	
	String getSerialString();
	
	/**
	 * Returning a value of 0 means the block is completely transparent. <br.
	 * Returning a value of 15 means the block is completely opaque.
	 * 
	 * @see LodUtil#BLOCK_FULLY_OPAQUE
	 * @see LodUtil#BLOCK_FULLY_TRANSPARENT
	 */
	int getOpacity();
	
	int getLightEmission();
	
	byte getMaterialId();
	
	boolean isBeaconBlock();
	/** IE a glass block that can affect the beacon beam color */
	boolean isBeaconTintBlock();
	/** 
	 * The blocks used by a beacon's base
	 * IE Iron, diamond, gold, etc. 
	 */
	boolean isBeaconBaseBlock();
	
	Color getMapColor();
	Color getBeaconTintColor();
	
}
