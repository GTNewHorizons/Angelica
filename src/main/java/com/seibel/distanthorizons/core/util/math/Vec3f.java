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

package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.util.MathUtil;

/**
 * A (almost) exact copy of Minecraft's 1.16.5
 * implementation of a 3 element float vector.
 *
 * @author James Seibel
 * @version 11-11-2021
 */
public class Vec3f extends DhApiVec3f
{
	//==============//
	// constructors //
	//==============//
	
	public Vec3f() { this(0,0,0); }
	
	public Vec3f(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Vec3f(DhApiVec3f pos)
	{
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}
	
	public Vec3f(Vec3d pos)
	{
		this.x = (float) pos.x;
		this.y = (float) pos.y;
		this.z = (float) pos.z;
	}
	
	
	
	
	//==============//
	// math methods //
	//==============//
	
	public void mul(float scalar)
	{
		this.x *= scalar;
		this.y *= scalar;
		this.z *= scalar;
	}
	
	public void mul(float x, float y, float z)
	{
		this.x *= x;
		this.y *= y;
		this.z *= z;
	}
	
	public void clamp(float min, float max)
	{
		this.x = MathUtil.clamp(min, this.x, max);
		this.y = MathUtil.clamp(min, this.y, max);
		this.z = MathUtil.clamp(min, this.z, max);
	}
	
	public void add(float x, float y, float z)
	{
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	public void add(Vec3f vector)
	{
		this.x += vector.x;
		this.y += vector.y;
		this.z += vector.z;
	}
	
	public void subtract(Vec3f vector)
	{
		this.x -= vector.x;
		this.y -= vector.y;
		this.z -= vector.z;
	}
	
	public float dotProduct(Vec3f vector) { return this.x * vector.x + this.y * vector.y + this.z * vector.z; }
	
	/** @return true if normalization had to be done */
	public boolean normalize()
	{
		float squaredSum = this.x * this.x + this.y * this.y + this.z * this.z;
		if (squaredSum < 1.0E-5D)
		{
			return false;
		}
		else
		{
			float f1 = MathUtil.fastInvSqrt(squaredSum);
			this.x *= f1;
			this.y *= f1;
			this.z *= f1;
			return true;
		}
	}
	
	public void crossProduct(Vec3f vector)
	{
		float f = this.x;
		float f1 = this.y;
		float f2 = this.z;
		float f3 = vector.x;
		float f4 = vector.y;
		float f5 = vector.z;
		this.x = f1 * f5 - f2 * f4;
		this.y = f2 * f3 - f * f5;
		this.z = f * f4 - f1 * f3;
	}
	
	public static float getManhattanDistance(DhApiVec3f a, DhApiVec3f b)
	{
		return Math.abs(a.x - b.x)
				+ Math.abs(a.y - b.y)
				+ Math.abs(a.z - b.z);
	}
	
	public static double getDistance(DhApiVec3f a, DhApiVec3f b)
	{
		return Math.sqrt(Math.pow(a.x - b.x, 2)
				+ Math.pow(a.y - b.y, 2)
				+ Math.pow(a.z - b.z, 2));
	}
	
	
	
	//==============//
	// misc methods //
	//==============//
	
	public void set(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Vec3f copy() { return new Vec3f(this.x, this.y, this.z); }
	
}
