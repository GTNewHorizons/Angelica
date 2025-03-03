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

import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.Iterator;

public interface IColumnDataView
{
	long get(int index);
	
	// FIXME probably horizontal size in blocks?
	int size();
	
	default LongIterator iterator()
	{
		return new LongIterator()
		{
			private int index = 0;
			private final int size = IColumnDataView.this.size();
			
			@Override
			public boolean hasNext() { return this.index < this.size; }
			
			@Override
			public long nextLong() { return IColumnDataView.this.get(this.index++); }
			
		};
	}
	
	// FIXME measured in blocks?
	int verticalSize();
	
	// FIXME how many datapoints in this LOD?
	int dataCount();
	
	IColumnDataView subView(int dataIndexStart, int dataCount);
	
	void copyTo(long[] target, int offset, int count);
	
}
