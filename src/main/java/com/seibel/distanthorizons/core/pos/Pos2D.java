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

package com.seibel.distanthorizons.core.pos;

import com.seibel.distanthorizons.coreapi.util.MathUtil;

import java.util.Objects;

/** immutable */
public class Pos2D
{
	public static final Pos2D ZERO = new Pos2D(0, 0);
	
	private final int x;
	public int getX() { return this.x; }
	
	private final int y;
	public int getY() { return this.y; }
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public Pos2D(int x, int y)
	{
		this.x = x;
		this.y = y;
	}
	
	
	
	
	//======//
	// math //
	//======//
	
	public Pos2D add(Pos2D other) { return new Pos2D(this.x + other.x, this.y + other.y); }
	public Pos2D subtract(Pos2D other) { return new Pos2D(this.x - other.x, this.y - other.y); }
	public Pos2D subtract(int value) { return new Pos2D(this.x - value, this.y - value); }
	
	public double dist(Pos2D other) { return Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2)); }
	public long distSquared(Pos2D other) { return MathUtil.pow2((long) this.x - other.x) + MathUtil.pow2((long) this.y - other.y); }
	
	/**
	 * Returns the maximum distance along either the X or Z axis <br><br>
	 *
	 * Example chebyshev distance between X and every point around it: <br>
	 * <code>
	 * 2 2 2 2 2 <br>
	 * 2 1 1 1 2 <br>
	 * 2 1 X 1 2 <br>
	 * 2 1 1 1 2 <br>
	 * 2 2 2 2 2 <br>
	 * </code>
	 */
	public int chebyshevDist(Pos2D other) { return Math.max(Math.abs(this.x - other.x), Math.abs(this.y - other.y)); }
	
	/**
	 * Can be used to quickly determine the rough distance between two points<Br>
	 * or determine the taxi cab (manhattan) distance between two points. <Br><Br>
	 *
	 * Manhattan distance is equivalent to determining the distance between two street intersections,
	 * where you can only drive along each street, instead of directly to the other point.
	 */
	public int manhattanDist(Pos2D other) { return Math.abs(this.x - other.x) + Math.abs(this.y - other.y); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public int hashCode() { return Objects.hash(this.x, this.y); }
	
	@Override
	public String toString() { return "[" + this.x + ", " + this.y + "]"; }
	
	@Override
	public boolean equals(Object otherObj)
	{
		if (otherObj == this)
			return true;
		if (otherObj instanceof Pos2D)
		{
			Pos2D otherPos = (Pos2D) otherObj;
			return this.x == otherPos.x && this.y == otherPos.y;
		}
		return false;
	}
	
}
