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

package com.seibel.distanthorizons.core.render.fog;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogFalloff;

import java.util.Objects;

/**
 * Contains all configurable options related to fog.
 *
 * @version 2022-4-13
 */
public class FogSettings
{
	/** a FogSetting object with 0 for every value */
	public static final FogSettings EMPTY = new FogSettings(0, 0, 0, 0, 0, EDhApiFogFalloff.LINEAR);
	
	
	public final double start;
	public final double end;
	public final double min;
	public final double max;
	public final double density;
	public final EDhApiFogFalloff fogType;
	
	public FogSettings(double start, double end, double min, double max, double density, EDhApiFogFalloff fogType)
	{
		this.start = start;
		this.end = end;
		this.min = min;
		this.max = max;
		this.density = density;
		this.fogType = fogType;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		FogSettings that = (FogSettings) o;
		return Double.compare(that.start, start) == 0 && Double.compare(that.end, end) == 0 && Double.compare(that.min, min) == 0 && Double.compare(that.max, max) == 0 && Double.compare(that.density, density) == 0 && fogType == that.fogType;
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(start, end, min, max, density, fogType);
	}
	
	
}
