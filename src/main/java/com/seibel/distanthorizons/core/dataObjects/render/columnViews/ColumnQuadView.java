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

package com.seibel.distanthorizons.core.dataObjects.render.columnViews;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public class ColumnQuadView implements IColumnDataView
{
	private final LongArrayList data;
	private final int perColumnOffset; // per column (of columns of data) offset in longs
	private final int xSize; // x size in datapoints
	private final int zSize; // x size in datapoints
	private final int offset; // offset in longs
	private final int vertSize; // vertical size in longs
	
	public ColumnQuadView(LongArrayList data, int dataZWidth, int dataVertSize, int viewXOffset, int viewZOffset, int xSize, int zSize)
	{
		if (viewXOffset + xSize > (data.size() / (dataZWidth * dataVertSize)) || viewZOffset + zSize > dataZWidth)
		{
			throw new IllegalArgumentException("View is out of bounds");
		}
		
		this.data = data;
		this.xSize = xSize;
		this.zSize = zSize;
		this.vertSize = dataVertSize;
		this.perColumnOffset = dataZWidth * dataVertSize;
		this.offset = viewXOffset * perColumnOffset + viewZOffset * dataVertSize;
	}
	private ColumnQuadView(LongArrayList data, int perColumnOffset, int offset, int vertSize, int xSize, int zSize)
	{
		this.data = data;
		this.perColumnOffset = perColumnOffset;
		this.offset = offset;
		this.vertSize = vertSize;
		this.xSize = xSize;
		this.zSize = zSize;
	}
	
	@Override
	public long get(int index)
	{
		int x = index / perColumnOffset;
		int z = (index % perColumnOffset) / vertSize;
		int v = index % vertSize;
		return get(x, z, v);
	}
	
	public long get(int x, int z, int v)
	{
		return data.getLong(offset + x * perColumnOffset + z * vertSize + v);
	}
	
	public long set(int x, int z, int v, long value)
	{
		return data.set(offset + x * perColumnOffset + z * vertSize + v, value);
	}
	
	public ColumnArrayView get(int x, int z)
	{
		return new ColumnArrayView(data, vertSize, offset + x * perColumnOffset + z * vertSize, vertSize);
	}
	
	public ColumnArrayView getRow(int x)
	{
		return new ColumnArrayView(data, zSize * vertSize, offset + x * perColumnOffset, vertSize);
	}
	
	public void set(int x, int z, IColumnDataView singleColumn)
	{
		if (singleColumn.verticalSize() != vertSize) throw new IllegalArgumentException("Vertical size of singleColumn must be equal to vertSize");
		if (singleColumn.dataCount() != 1) throw new IllegalArgumentException("SingleColumn must contain exactly one data point");
		singleColumn.copyTo(data.elements(), offset + x * perColumnOffset + z * vertSize, singleColumn.size());
	}
	
	@Override
	public int size()
	{
		return xSize * zSize * vertSize;
	}
	
	@Override
	public int verticalSize()
	{
		return vertSize;
	}
	
	@Override
	public int dataCount()
	{
		return xSize * zSize;
	}
	
	@Override
	public IColumnDataView subView(int dataIndexStart, int dataCount)
	{
		if (dataCount != 1) throw new UnsupportedOperationException("Fixme: subView for QUadView only support one data point!");
		int x = dataIndexStart / xSize;
		int z = dataIndexStart % xSize;
		return new ColumnArrayView(data, vertSize * dataCount, offset + x * perColumnOffset + z * vertSize, vertSize);
	}
	
	public ColumnQuadView subView(int xOffset, int zOffset, int xSize, int zSize)
	{
		if (xOffset + xSize > this.xSize || zOffset + zSize > this.zSize) throw new IllegalArgumentException("SubView is out of bounds");
		return new ColumnQuadView(data, perColumnOffset, offset + xOffset * perColumnOffset + zOffset * vertSize, vertSize, xSize, zSize);
	}
	
	@Override
	public void copyTo(long[] target, int offset, int size)
	{
		if (size != this.size() && size > zSize * vertSize) throw new UnsupportedOperationException("Not supported yet");
		if (size <= xSize * vertSize)
		{
			System.arraycopy(data, this.offset, target, offset, size);
		}
		else
		{
			for (int x = 0; x < xSize; x++)
			{
				System.arraycopy(data, this.offset + x * perColumnOffset, target, offset + x * xSize * vertSize, xSize * vertSize);
			}
		}
	}
	
	public void copyTo(ColumnQuadView target)
	{
		if (target.xSize != xSize || target.zSize != zSize)
			throw new IllegalArgumentException("Target view must have same size as this view");
		
		for (int x = 0; x < xSize; x++)
		{
			target.getRow(x).changeVerticalSizeFrom(getRow(x));
		}
	}
	
	public void mergeMultiColumnFrom(ColumnQuadView source)
	{
		if (source.xSize == xSize && source.zSize == zSize)
		{
			source.copyTo(this);
			return;
		}
		if (source.xSize < xSize || source.zSize < zSize)
			throw new IllegalArgumentException("Source view must have same or larger size as this view");
		
		int srcXPerTrgX = source.xSize / xSize;
		int srcZPerTrgZ = source.zSize / zSize;
		if (source.xSize % xSize != 0 || source.zSize % zSize != 0)
			throw new IllegalArgumentException("Source view's size must be a multiple of this view's size");
		
		for (int x = 0; x < xSize; x++)
		{
			for (int z = 0; z < zSize; z++)
			{
				ColumnQuadView srcBlock = source.subView(x * srcXPerTrgX, z * srcZPerTrgZ, srcXPerTrgX, srcZPerTrgZ);
				get(x, z).mergeMultiDataFrom(srcBlock);
			}
		}
	}
	
}
