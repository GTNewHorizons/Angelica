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

package com.seibel.distanthorizons.core.util.gridList;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/*Layout:
 * 0,1,2,
 * 3,4,5,
 * 6,7,8
 */

public class PosArrayGridList<T> extends ArrayGridList<T>
{
	
	private int offsetX;
	private int offsetY;
	
	/*
	 * WARNING: Non Thread safe!
	 */
	public PosArrayGridList(int gridSize, int offsetX, int offsetY, BiFunction<Integer, Integer, T> filler)
	{
		super(gridSize, filler);
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}
	
	public PosArrayGridList(int gridSize, int offsetX, int offsetY)
	{
		this(gridSize, offsetX, offsetY, (x, y) -> null);
	}
	
	public PosArrayGridList(PosArrayGridList<T> copy)
	{
		super(copy);
		offsetX = copy.offsetX;
		offsetY = copy.offsetY;
	}
	
	public PosArrayGridList(PosArrayGridList<T> source, int minR, int maxR)
	{
		super(source, minR, maxR);
	}
	
	@Override
	protected int _indexOf(int x, int y)
	{
		return (x - offsetX) + (y - offsetY) * gridSize;
	}
	
	@Override
	public void forEachPos(BiConsumer<Integer, Integer> consumer)
	{
		for (int y = offsetY; y < offsetY + gridSize; y++)
		{
			for (int x = offsetX; x < offsetX + gridSize; x++)
			{
				consumer.accept(x, y);
			}
		}
	}
	
	public int getOffsetX()
	{
		return offsetX;
	}
	public int getOffsetY()
	{
		return offsetY;
	}
	
	@Override
	public boolean inRange(int x, int y)
	{
		return (x >= offsetX && x < offsetX + gridSize &&
				y >= offsetY && y < offsetY + gridSize);
	}
	
	private T _directGet(int x, int y)
	{
		if (!inRange(x, y)) return null;
		return get(x, y);
	}
	
	// Return false if haven't changed. Return true if it did
	public boolean move(int deltaX, int deltaY, Consumer<? super T> dealloc)
	{
		if (deltaX == 0 && deltaY == 0) return false;
		
		// if the x or z offset is equal to or greater than
		// the total width, just delete the current data
		// and update the centerX and/or centerZ
		if (Math.abs(deltaX) >= gridSize || Math.abs(deltaY) >= gridSize)
		{
			clear(dealloc);
			// update the new center
			offsetX += deltaX;
			offsetY += deltaY;
			return true;
		}
		int newMinX = offsetX + deltaX;
		int newMinY = offsetY + deltaY;
		int newMaxX = newMinX + gridSize;
		int newMaxY = newMinY + gridSize;
		
		// Dealloc stuff
		if (dealloc != null)
			forEachPos((x, y) -> {
				if (x < newMinX || y < newMinY ||
						x >= newMaxX || y >= newMaxY)
				{
					T t = get(x, y);
					if (t != null) dealloc.accept(t);
				}
			});
		
		offsetX = newMinX;
		offsetY = newMinY;
		
		// X
		if (deltaX >= 0 && deltaY >= 0)
		{
			
			// move everything over to the left-up (as the center moves to the right-down)
			for (int x = newMinX; x < newMaxX; x++)
			{
				for (int y = newMinY; y < newMaxY; y++)
				{
					set(x, y, _directGet(x + deltaX, y + deltaY));
				}
			}
		}
		else if (deltaX < 0 && deltaY >= 0)
		{
			// move everything over to the right-up (as the center moves to the left-down)
			for (int x = newMaxX - 1; x >= newMinX; x--)
			{
				for (int y = newMinY; y < newMaxY; y++)
				{
					set(x, y, _directGet(x + deltaX, y + deltaY));
				}
			}
		}
		else if (deltaX >= 0) // && deltaY < 0)
		{
			// move everything over to the left-down (as the center moves to the right-up)
			for (int x = newMinX; x < newMaxX; x++)
			{
				for (int y = newMaxY - 1; y >= newMinY; y--)
				{
					set(x, y, _directGet(x + deltaX, y + deltaY));
				}
			}
		}
		else //if (deltaX < 0 && deltaY < 0)
		{
			// move everything over to the right-down (as the center moves to the left-up)
			for (int x = newMaxX - 1; x >= newMinX; x--)
			{
				for (int y = newMaxY - 1; y >= newMinY; y--)
				{
					set(x, y, _directGet(x + deltaX, y + deltaY));
				}
			}
		}
		return true;
	}
	
	@Override
	public String toString()
	{
		return getClass().toString() + "[" + offsetX + "," + offsetY + "] " + gridSize + "*" + gridSize + "[" + size() + "]";
	}
	
}
