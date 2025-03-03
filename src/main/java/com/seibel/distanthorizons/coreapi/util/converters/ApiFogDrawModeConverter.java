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

package com.seibel.distanthorizons.coreapi.util.converters;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConverter;

/**
 * Used for supporting the deprecated {@link EDhApiFogDrawMode}.
 *
 * @author James Seibel
 * @version 2024-10-12
 */
@Deprecated
public class ApiFogDrawModeConverter implements IConverter<Boolean, EDhApiFogDrawMode>
{
	
	@Override 
	public Boolean convertToCoreType(EDhApiFogDrawMode renderingMode)
	{
		if (renderingMode == EDhApiFogDrawMode.USE_OPTIFINE_SETTING)
		{
			return true;
		}
		else
		{
			return renderingMode == EDhApiFogDrawMode.FOG_ENABLED;	
		}
	}
	
	@Override 
	public EDhApiFogDrawMode convertToApiType(Boolean renderingEnabled)
	{ return renderingEnabled ? EDhApiFogDrawMode.FOG_ENABLED : EDhApiFogDrawMode.FOG_DISABLED; }
	
}
