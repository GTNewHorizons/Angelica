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

package com.seibel.distanthorizons.api.objects.math;

/**
 * Often used to store block positions or any other
 * position in 3D space.
 *
 * @author James Seibel
 * @version 2024-7-9
 * @since API 3.0.0
 */
public class DhApiVec3d
{
	public double x;
	public double y;
	public double z;
	
	
	
	/** creates a Vec3 at (0,0,0) */
	public DhApiVec3d()
	{
		this.x = 0;
		this.y = 0;
		this.z = 0;
	}
	
	public DhApiVec3d(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (obj != null && this.getClass() == obj.getClass())
		{
			DhApiVec3d Vec3d = (DhApiVec3d) obj;
			if (Double.compare(Vec3d.x, this.x) != 0)
			{
				return false;
			}
			else if (Double.compare(Vec3d.y, this.y) != 0)
			{
				return false;
			}
			else
			{
				return Double.compare(Vec3d.z, this.z) == 0;
			}
		}
		else
		{
			return false;
		}
	}
	
	@Override
	public int hashCode()
	{
		long i = Double.doubleToLongBits(this.x);
		i = 31 * i + Double.doubleToLongBits(this.y);
		i = 31 * i + Double.doubleToLongBits(this.z);
		return Long.hashCode(i);
	}
	
	@Override
	public String toString() { return "[" + this.x + ", " + this.y + ", " + this.z + "]"; }
	
}
