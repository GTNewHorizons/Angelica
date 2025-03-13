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

package com.seibel.distanthorizons.core.render.glObject.vertexAttribute;

import com.seibel.distanthorizons.coreapi.util.MathUtil;
import org.lwjgl.opengl.GL32;

public final class VertexPointer
{
	public final int elementCount;
	public final int glType;
	public final boolean normalized;
	public final int byteSize;
	public final boolean useInteger;
	
	
	
	// basic constructors //
	
	public VertexPointer(int elementCount, int glType, boolean normalized, int byteSize, boolean useInteger)
	{
		this.elementCount = elementCount;
		this.glType = glType;
		this.normalized = normalized;
		this.byteSize = byteSize;
		this.useInteger = useInteger;
	}
	public VertexPointer(int elementCount, int glType, boolean normalized, int byteSize)
	{
		this(elementCount, glType, normalized, byteSize, false);
	}
	private static int _align(int bytes) { return MathUtil.ceilDiv(bytes, 4) * 4; }
	
	
	
	// named constructors //
	
	public static VertexPointer addFloatPointer(boolean normalized) { return new VertexPointer(1, GL32.GL_FLOAT, normalized, Float.BYTES); }
	public static VertexPointer addVec2Pointer(boolean normalized) { return new VertexPointer(2, GL32.GL_FLOAT, normalized, Float.BYTES * 2); }
	public static VertexPointer addVec3Pointer(boolean normalized) { return new VertexPointer(3, GL32.GL_FLOAT, normalized, Float.BYTES * 3); }
	public static VertexPointer addVec4Pointer(boolean normalized) { return new VertexPointer(4, GL32.GL_FLOAT, normalized, Float.BYTES * 4); }
	/** Always aligned to 4 bytes */
	public static VertexPointer addUnsignedBytePointer(boolean normalized, boolean useInteger) { return new VertexPointer(1, GL32.GL_UNSIGNED_BYTE, normalized, 4, useInteger); }
	/** aligned to 4 bytes */
	public static VertexPointer addUnsignedBytesPointer(int elementCount, boolean normalized, boolean useInteger) { return new VertexPointer(elementCount, GL32.GL_UNSIGNED_BYTE, normalized, _align(elementCount), useInteger); }
	public static VertexPointer addUnsignedShortsPointer(int elementCount, boolean normalized, boolean useInteger) { return new VertexPointer(elementCount, GL32.GL_UNSIGNED_SHORT, normalized, _align(elementCount * 2), useInteger); }
	public static VertexPointer addShortsPointer(int elementCount, boolean normalized, boolean useInteger) { return new VertexPointer(elementCount, GL32.GL_SHORT, normalized, _align(elementCount * 2), useInteger); }
	public static VertexPointer addIntPointer(boolean normalized, boolean useInteger) { return new VertexPointer(1, GL32.GL_INT, normalized, 4, useInteger); }
	public static VertexPointer addIVec2Pointer(boolean normalized, boolean useInteger) { return new VertexPointer(2, GL32.GL_INT, normalized, 8, useInteger); }
	public static VertexPointer addIVec3Pointer(boolean normalized, boolean useInteger) { return new VertexPointer(3, GL32.GL_INT, normalized, 12, useInteger); }
	public static VertexPointer addIVec4Pointer(boolean normalized, boolean useInteger) { return new VertexPointer(4, GL32.GL_INT, normalized, 16, useInteger); }
	
}
