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

package com.seibel.distanthorizons.api.interfaces.override.levelHandling;

import com.seibel.distanthorizons.api.enums.EDhApiDetailLevel;
import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

import java.io.File;

/**
 * Used to override which folder DH uses when loading a level.
 * Can be used to redirect LOD data saving into a more manageable location
 * or for replays/local-servers that are running out of a different folder
 * than where the DH data is normally saved.
 * 
 * @author James Seibel
 * @version 2024-9-28
 * @since API 4.0.0
 */
public interface IDhApiSaveStructure extends IDhApiOverrideable
{
	/**
	 * Called when DH first loads a level to determine which folder it should use
	 * for file handling.
	 * 
	 * @param currentFilePath the file path DH is planning to use. If this method returns null this is the file path that will be used.
	 * @param levelWrapper the level this file path is used for.
	 * @return null if you don't want to override the file path. Non-null if you want to change the file path.
	 */
	File overrideFilePath(File currentFilePath, IDhApiLevelWrapper levelWrapper);
	
}
