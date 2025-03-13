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

import com.seibel.distanthorizons.coreapi.interfaces.config.IConverter;


/**
 * Returns the object passed in, doesn't do any conversion. <br>
 * Helpful as the default converter in some cases.
 *
 * @author James Seibel
 * @version 2022-6-30
 */
public class DefaultConverter<T> implements IConverter<T, T>
{
	@Override
	public T convertToCoreType(T apiObject)
	{
		return apiObject;
	}
	
	@Override
	public T convertToApiType(T coreObject)
	{
		return coreObject;
	}
	
}