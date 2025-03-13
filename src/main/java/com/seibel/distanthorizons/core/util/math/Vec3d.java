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

import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.util.MathUtil;

/**
 * This is closer to MC's implementation of a
 * 3 element float vector than a 3 element double
 * vector. Hopefully that shouldn't cause any issues.
 *
 * @author James Seibel
 * @version 11-18-2021
 */
public class Vec3d extends DhApiVec3d
{
	public static Vec3d XNeg = new Vec3d(-1.0F, 0.0F, 0.0F);
	public static Vec3d XPos = new Vec3d(1.0F, 0.0F, 0.0F);
	public static Vec3d YNeg = new Vec3d(0.0F, -1.0F, 0.0F);
	public static Vec3d YPos = new Vec3d(0.0F, 1.0F, 0.0F);
	public static Vec3d ZNeg = new Vec3d(0.0F, 0.0F, -1.0F);
	public static Vec3d ZPos = new Vec3d(0.0F, 0.0F, 1.0F);
	
	public static final Vec3d ZERO_VECTOR = new Vec3d(0.0D, 0.0D, 0.0D);
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public Vec3d() { }
	
	public Vec3d(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Vec3d(DhApiVec3d that)
	{
		this.x = that.x;
		this.y = that.y;
		this.z = that.z;
	}
	
	public Vec3d(double[] values) { this.set(values); }
	
	
	public Vec3d copy() { return new Vec3d(this); }
	
	
	
	//=========//
	// methods //
	//=========//
	
	public void multiply(double scalar)
	{
		this.x *= scalar;
		this.y *= scalar;
		this.z *= scalar;
	}
	
	public void multiply(double x, double y, double z)
	{
		this.x *= x;
		this.y *= y;
		this.z *= z;
	}
	
	public void clamp(double min, double max)
	{
		this.x = MathUtil.clamp(min, this.x, max);
		this.y = MathUtil.clamp(min, this.y, max);
		this.z = MathUtil.clamp(min, this.z, max);
	}
	
	public void set(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public void add(double x, double y, double z)
	{
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	public void add(Vec3d vector)
	{
		this.x += vector.x;
		this.y += vector.y;
		this.z += vector.z;
	}
	
	public void subtract(Vec3d vector)
	{
		this.x -= vector.x;
		this.y -= vector.y;
		this.z -= vector.z;
	}
	
	public double dotProduct(Vec3d vector) { return this.x * vector.x + this.y * vector.y + this.z * vector.z; }
	
	public Vec3d normalize()
	{
		double value = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
		return value < 1.0E-4D ? ZERO_VECTOR : new Vec3d(this.x / value, this.y / value, this.z / value);
	}
	
	public void crossProduct(Vec3d vector)
	{
		double f = this.x;
		double f1 = this.y;
		double f2 = this.z;
		double f3 = vector.x;
		double f4 = vector.y;
		double f5 = vector.z;
		this.x = f1 * f5 - f2 * f4;
		this.y = f2 * f3 - f * f5;
		this.z = f * f4 - f1 * f3;
	}
	
	public void set(double[] values)
	{
		this.x = values[0];
		this.y = values[1];
		this.z = values[2];
	}
	
	public double getManhattanDistance(DhApiVec3d other) { return getManhattanDistance(this, other); }
	public static double getManhattanDistance(DhApiVec3d a, DhApiVec3d b)
	{
		return Math.abs(a.x - b.x)
				+ Math.abs(a.y - b.y)
				+ Math.abs(a.z - b.z);
	}
	
	public double getDistance(DhApiVec3d other) { return getDistance(this, other); }
	public static double getDistance(DhApiVec3d a, DhApiVec3d b)
	{
		return Math.sqrt(Math.pow(a.x - b.x, 2)
				+ Math.pow(a.y - b.y, 2)
				+ Math.pow(a.z - b.z, 2));
	}
	
	/** @see Vec3d#getSquaredDistance(DhApiVec3d, DhApiVec3d)  */
	public double getSquaredDistance(DhApiVec3d other) { return getSquaredDistance(this, other); }
	/** slightly faster version of {@link Vec3d#getDistance} */
	public static double getSquaredDistance(DhApiVec3d a, DhApiVec3d b)
	{
		return Math.pow(a.x - b.x, 2)
				+ Math.pow(a.y - b.y, 2)
				+ Math.pow(a.z - b.z, 2);
	}
	
	/** @see Vec3d#getHorizontalDistance(DhApiVec3d, DhApiVec3d)  */
	public double getHorizontalDistance(DhApiVec3d other) { return getHorizontalDistance(this, other); }
	/** Gets the distance between points A and B, ignoring Y height. */
	public static double getHorizontalDistance(DhApiVec3d a, DhApiVec3d b)
	{
		return Math.sqrt(Math.pow(a.x - b.x, 2)
				+ Math.pow(a.z - b.z, 2));
	}
	
}
