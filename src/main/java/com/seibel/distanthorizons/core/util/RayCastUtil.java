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

package com.seibel.distanthorizons.core.util;

/**
 * @author James Seibel
 * @version 2022-11-19
 */
public class RayCastUtil
{
	
	/**
	 * This function should work for any 2 perpendicular axis, X and Y could be replaced with X, Y, or Z
	 *
	 * @param rayX the ray's starting X position
	 * @param rayY the ray's starting Z position
	 * @param squareMinX the square's X corner closest to negative infinity
	 * @param squareMinY the square's Y corner closest to negative infinity
	 */
	public static boolean rayIntersectsSquare(
			double rayX, double rayY, double rayXDirection, double rayYDirection,
			double squareMinX, double squareMinY, double squareWidth)
	{
		double roundingValue = 0.05;
		
		// determine the other corner of the square
		double squareMaxX = squareMinX + squareWidth;
		double squareMaxY = squareMinY + squareWidth;
		
		
		// check if the ray originates in the square
		if (rayX >= squareMinX && rayX <= squareMaxX &&
				rayY >= squareMinY && rayY <= squareMaxY)
		{
			return true;
		}
		
		
		
		if (isRoughly(rayXDirection, 0, roundingValue) && isRoughly(rayYDirection, 0, roundingValue))
		{
			// slope is in a direction perpendicular to this ray
			
			// this ray can be treated like a point,
			// checking if the point originated inside the square
			// should catch if this was true
			return false;
		}
		else if (isRoughly(Math.abs(rayYDirection), 1, roundingValue) || isRoughly(Math.abs(rayXDirection), 0, roundingValue))
		{
			// slope is straight up or down
			
			// is the ray pointing towards the square?
			if ((rayYDirection > 0 && rayY > squareMaxY) || // up
					(rayYDirection < 0 && rayY < squareMinY)) // down
			{
				// the ray is pointing away from the square
				return false;
			}
			else
			{
				// check if the ray's X value is between the square's left and right sides
				return rayX >= squareMinX && rayX <= squareMaxX;
			}
		}
		else if (isRoughly(Math.abs(rayXDirection), 1, roundingValue) || isRoughly(rayYDirection, 0, roundingValue))
		{
			// slope is 0 (horizontal line)
			
			// is the ray pointing towards the square?
			if ((rayXDirection > 0 && rayX > squareMaxX) || // right
					(rayXDirection < 0 && rayX < squareMinX)) // left
			{
				// the ray is pointing away from the square
				return false;
			}
			else
			{
				// check if the ray's Y value is between the square's top and bottom sides
				return rayY >= squareMinY && rayY <= squareMaxY;
			}
		}
		else
		{
			// slope is a valid range (between -infinity and infinity)
			double slope = rayYDirection / rayXDirection;
			
			// move the square into ray space (where the ray is at the origin)
			squareMinX -= rayX;
			squareMaxX -= rayX;
			
			squareMinY -= rayY;
			squareMaxY -= rayY;
			
			
			boolean intersectsX = false;
			boolean intersectsY = false;
			
			
			
			// ray Y intersect
			// y = mx
			double yIntersectMin = slope * squareMinX;
			double yIntersectMax = slope * squareMaxX;
			
			// does the intersection happen before the ray's origin?
			if ((rayYDirection > 0 && (yIntersectMin <= rayY && yIntersectMax <= rayY)) || // moving in pos Y direction
					(rayYDirection < 0 && (yIntersectMin >= rayY && yIntersectMax >= rayY)))   // moving in neg Y direction
			{
				return false;
			}
			// does the line intersect the square?
			else if (yIntersectMin >= squareMinY && yIntersectMin <= squareMaxY)
			{
				intersectsY = true;
			}
			else if (yIntersectMax >= squareMinY && yIntersectMax <= squareMaxY)
			{
				intersectsY = true;
			}
			
			
			// ray X intersect
			// x = y/m
			double xIntersectMin = squareMinY / slope;
			double xIntersectMax = squareMaxY / slope;
			
			// does the intersection happen before the ray's origin?
			if ((rayXDirection > 0 && (xIntersectMin <= rayX && xIntersectMax <= rayX)) || // moving in pos X direction
					(rayXDirection < 0 && (xIntersectMin >= rayX && xIntersectMax >= rayX)))   // moving in neg X direction
			{
				return false;
			}
			// does the line intersect the square?
			else if (xIntersectMin >= squareMinX && xIntersectMin <= squareMaxX)
			{
				intersectsX = true;
			}
			else if (xIntersectMax >= squareMinX && xIntersectMax <= squareMaxX)
			{
				intersectsX = true;
			}
			
			
			// if the ray intersects both the top and side of the square, that means
			// the ray intersects the square as a whole
			return intersectsX && intersectsY;
		}
	}
	/** used to get around floating point number rounding errors */
	private static boolean isRoughly(double input, double equalsVal, double errorValue) { return input >= equalsVal - errorValue && input <= equalsVal + errorValue; }
	
	
}
