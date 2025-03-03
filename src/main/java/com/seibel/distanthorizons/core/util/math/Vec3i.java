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

import com.seibel.distanthorizons.coreapi.util.MathUtil;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;

/**
 * A (almost) exact copy of Minecraft's 1.16.5
 * implementation of a 3 element integer vector.
 *
 * @author James Seibel
 * @version 2022-11-19
 */
public class Vec3i extends DhApiVec3i // extends the API object so it can be returned through the API
{
	public static Vec3i XNeg = new Vec3i(-1, 0, 0);
	public static Vec3i XPos = new Vec3i(1, 0, 0);
	public static Vec3i YNeg = new Vec3i(0, -1, 0);
	public static Vec3i YPos = new Vec3i(0, 1, 0);
	public static Vec3i ZNeg = new Vec3i(0, 0, -1);
	public static Vec3i ZPos = new Vec3i(0, 0, 1);
	
	// x,y,z variables are handled in the parent object
	
	
	
	public Vec3i()
	{
		this.x = 0;
		this.y = 0;
		this.z = 0;
	}
	
	public Vec3i(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	
	
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
	
	public void clamp(int min, int max)
	{
		this.x = MathUtil.clamp(min, this.x, max);
		this.y = MathUtil.clamp(min, this.y, max);
		this.z = MathUtil.clamp(min, this.z, max);
	}
	
	public void set(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public void add(int x, int y, int z)
	{
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	public void add(Vec3i vector)
	{
		this.x += vector.x;
		this.y += vector.y;
		this.z += vector.z;
	}
	
	public void subtract(Vec3i vector)
	{
		this.x -= vector.x;
		this.y -= vector.y;
		this.z -= vector.z;
	}
	
	public double distSqr(double x, double y, double z, boolean centerOfBlock)
	{
		double offset = centerOfBlock ? 0.5 : 0.0;
		double xAdd = this.x + offset - x;
		double yAdd = this.y + offset - y;
		double zAdd = this.z + offset - z;
		return (xAdd * xAdd) + (yAdd * yAdd) + (zAdd * zAdd);
	}
	
	public int distManhattan(Vec3i otherVec)
	{
		float xSub = Math.abs(otherVec.x - this.x);
		float ySub = Math.abs(otherVec.y - this.y);
		float zSub = Math.abs(otherVec.z - this.z);
		return (int) (xSub + ySub + zSub);
	}
	
	/** inner product */
	public float dotProduct(Vec3i vector)
	{
		return (this.x * vector.x) + (this.y * vector.y) + (this.z * vector.z);
	}
	
	/** Cross product */
	public Vec3i cross(Vec3i otherVec)
	{
		return new Vec3i(
				(this.y * otherVec.z) - (this.z * otherVec.y),
				(this.z * otherVec.x) - (this.x * otherVec.z),
				(this.x * otherVec.y) - (this.y * otherVec.x));
	}
	
	public Vec3i copy()
	{
		return new Vec3i(this.x, this.y, this.z);
	}
	
	
	
	
	// Forge start
	public Vec3i(int[] values) { this.set(values); }
	
	public void set(int[] values)
	{
		this.x = values[0];
		this.y = values[1];
		this.z = values[2];
	}
	
}
