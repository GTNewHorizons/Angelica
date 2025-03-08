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

package com.seibel.distanthorizons.core.render.glObject.buffer;

import org.lwjgl.opengl.GL32;

/**
 * This is a container for a OpenGL
 * VBO (Vertex Buffer Object).
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class GLElementBuffer extends GLBuffer
{
	/**
	 * When uploading to a buffer that is too small, recreate it this many times
	 * bigger than the upload payload
	 */
	protected int indicesCount = 0;
	public int getIndicesCount() { return this.indicesCount; }
	protected int type = GL32.GL_UNSIGNED_INT;
	public int getType() { return type; }
	
	public GLElementBuffer(boolean isBufferStorage)
	{
		super(isBufferStorage);
	}
	
	@Override
	public void destroyAsync()
	{
		super.destroyAsync();
		this.indicesCount = 0;
	}
	
	@Override
	public int getBufferBindingTarget()
	{
		return GL32.GL_ELEMENT_ARRAY_BUFFER;
	}
	
}