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

package com.seibel.distanthorizons.core.render.vertexFormat;

import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * This is used to represent a single vertex
 * stored in GPU memory,
 * <p>
 * A (almost) exact copy of Minecraft's
 * VertexFormat class, several methods
 * were commented out since we didn't need them.
 *
 * @author James Seibel
 * @version 12-9-2021
 */
public class LodVertexFormat
{
	private final ImmutableList<LodVertexFormatElement> elements;
	private final IntList offsets = new IntArrayList();
	private final int byteSize;
	
	public LodVertexFormat(ImmutableList<LodVertexFormatElement> elementList)
	{
		this.elements = elementList;
		int i = 0;
		
		for (LodVertexFormatElement LodVertexFormatElement : elementList)
		{
			this.offsets.add(i);
			i += LodVertexFormatElement.getByteSize();
		}
		
		this.byteSize = i;
	}
	
	public int getByteSize()
	{
		return this.byteSize;
	}
	
	public ImmutableList<LodVertexFormatElement> getElements()
	{
		return this.elements;
	}
	
	
	// Forge added method
	public int getOffset(int index)
	{
		return offsets.getInt(index);
	}
	
	
	
	@Override
	public String toString()
	{
		return "format: " + this.elements.size() + " elements: " + this.elements.stream().map(Object::toString).collect(Collectors.joining(" "));
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
			LodVertexFormat vertexFormat = (LodVertexFormat) obj;
			return this.byteSize == vertexFormat.byteSize && this.elements.equals(vertexFormat.elements);
		}
		else
		{
			return false;
		}
	}
	
	@Override
	public int hashCode()
	{
		return this.elements.hashCode();
	}
	
	
	
	
	
	

	/* not currently needed setupBufferState()
	public void setupBufferState(long p_227892_1_)
	{
		if (!RenderSystem.isOnRenderThread())
		{
			RenderSystem.recordRenderCall(() ->
			{
				this.setupBufferState(p_227892_1_);
			});
		}
		else
		{
			int i = this.getVertexSize();
			List<LodVertexFormatElement> list = this.getElements();
			
			for (int j = 0; j < list.size(); ++j)
			{
				list.get(j).setupBufferState(p_227892_1_ + this.offsets.getInt(j), i);
			}
			
		}
	}
	*/
	
	/* not currently needed clearBufferState()
	public void clearBufferState()
	{
		if (!RenderSystem.isOnRenderThread())
		{
			RenderSystem.recordRenderCall(this::clearBufferState);
		}
		else
		{
			for (LodVertexFormatElement LodVertexFormatElement : this.getElements())
			{
				LodVertexFormatElement.clearBufferState();
			}
			
		}
	}
	*/
	
	
	/* not currently needed has-Position/Normal/Color/UV
	public boolean hasPosition()
	{
		return elements.stream().anyMatch(e -> e.getUsage() == LodVertexFormatElement.Usage.POSITION);
	}
	
	public boolean hasNormal()
	{
		return elements.stream().anyMatch(e -> e.getUsage() == LodVertexFormatElement.Usage.NORMAL);
	}
	
	public boolean hasColor()
	{
		return elements.stream().anyMatch(e -> e.getUsage() == LodVertexFormatElement.Usage.COLOR);
	}
	
	public boolean hasUV(int which)
	{
		return elements.stream().anyMatch(e -> e.getUsage() == LodVertexFormatElement.Usage.UV && e.getIndex() == which);
	}
	*/
	
}