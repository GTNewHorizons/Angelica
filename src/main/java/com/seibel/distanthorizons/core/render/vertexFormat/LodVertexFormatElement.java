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

import org.lwjgl.opengl.GL32;

/**
 * This object is used to build LodVertexFormats.
 * <p>
 * A (almost) exact copy of Minecraft's
 * VertexFormatElement class. <br>
 * A number of things were removed from the original
 * object since we didn't need them, specifically "usage".
 *
 * @author James Seibel
 * @version 11-13-2021
 */
public class LodVertexFormatElement
{
	private final LodVertexFormatElement.DataType dataType;
	/** James isn't sure what index is for */
	private final int index;
	private final int count;
	private final int byteSize;
	private final boolean isPadding;
	
	public LodVertexFormatElement(int newIndex, LodVertexFormatElement.DataType newType, int newCount, boolean isPadding)
	{
		this.dataType = newType;
		this.index = newIndex;
		this.count = newCount;
		this.byteSize = newType.getSize() * this.count;
		this.isPadding = isPadding;
	}
	
	public final boolean getIsPadding()
	{
		return isPadding;
	}
	
	public final LodVertexFormatElement.DataType getType()
	{
		return this.dataType;
	}
	
	public final int getIndex()
	{
		return this.index;
	}
	
	public final int getByteSize()
	{
		return this.byteSize;
	}
	
	// added by Forge
	public int getElementCount()
	{
		return count;
	}
	
	
	
	public enum DataType
	{
		FLOAT(4, "Float", GL32.GL_FLOAT),
		UBYTE(1, "Unsigned Byte", GL32.GL_UNSIGNED_BYTE),
		BYTE(1, "Byte", GL32.GL_BYTE),
		USHORT(2, "Unsigned Short", GL32.GL_UNSIGNED_SHORT),
		SHORT(2, "Short", GL32.GL_SHORT),
		UINT(4, "Unsigned Int", GL32.GL_UNSIGNED_INT),
		INT(4, "Int", GL32.GL_INT);
		
		private final int size;
		private final String name;
		private final int glType;
		
		DataType(int sizeInBytes, String newName, int openGlDataType)
		{
			this.size = sizeInBytes;
			this.name = newName;
			this.glType = openGlDataType;
		}
		
		public int getSize()
		{
			return this.size;
		}
		
		public String getName()
		{
			return this.name;
		}
		
		public int getGlType()
		{
			return this.glType;
		}
	}
	
	
	
	
	@Override
	public int hashCode()
	{
		int i = this.dataType.hashCode();
		i = 31 * i + this.index;
		return 31 * i + this.count;
	}
	
	@Override
	public String toString()
	{
		return this.count + "," + this.dataType.getName();
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
			LodVertexFormatElement LodVertexFormatElement = (LodVertexFormatElement) obj;
			if (this.count != LodVertexFormatElement.count)
			{
				return false;
			}
			else if (this.index != LodVertexFormatElement.index)
			{
				return false;
			}
			else if (this.dataType != LodVertexFormatElement.dataType)
			{
				return false;
			}
			else
			{
				return false;
			}
		}
		else
		{
			return false;
		}
	}
	
}