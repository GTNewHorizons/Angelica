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

package com.seibel.distanthorizons.api.methods.override;

import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGeneratorOverrideRegister;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.coreapi.DependencyInjection.WorldGeneratorInjector;

/**
 * Handles adding world generator overrides.
 *
 * @author James Seibel
 * @version 2022-12-10
 * @since API 1.0.0
 */
public class DhApiWorldGeneratorOverrideRegister implements IDhApiWorldGeneratorOverrideRegister
{
	public static DhApiWorldGeneratorOverrideRegister INSTANCE = new DhApiWorldGeneratorOverrideRegister();
	
	private DhApiWorldGeneratorOverrideRegister() { }
	
	
	
	@Override
	public DhApiResult<Void> registerWorldGeneratorOverride(IDhApiLevelWrapper levelWrapper, IDhApiWorldGenerator worldGenerator)
	{
		try
		{
			WorldGeneratorInjector.INSTANCE.bind(levelWrapper, worldGenerator);
			return DhApiResult.createSuccess();
		}
		catch (Exception e)
		{
			return DhApiResult.createFail(e.getMessage());
		}
	}
	
}
