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

package com.seibel.distanthorizons.core.file.structure;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import java.io.File;

/** Used to determining where LOD data should be saved to. */
public interface ISaveStructure extends AutoCloseable
{
	String DATABASE_NAME = "DistantHorizons.sqlite";
	
	/** 
	 * Returns the folder that contains LOD data for the given {@link ILevelWrapper}.
	 * If no appropriate folder exists, one will be created. 
	 */
	File getSaveFolder(ILevelWrapper levelWrapper);
	
	File getPre23SaveFolder(ILevelWrapper levelWrapper);
	
}

