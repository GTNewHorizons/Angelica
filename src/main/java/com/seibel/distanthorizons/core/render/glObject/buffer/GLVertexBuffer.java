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

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL32;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;

/**
 * This is a container for a OpenGL
 * VBO (Vertex Buffer Object).
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class GLVertexBuffer extends GLBuffer
{
	/**
	 * When uploading to a buffer that is too small, recreate it this many times
	 * bigger than the upload payload
	 */
	protected int vertexCount = 0;
	public int getVertexCount() { return this.vertexCount; }
	// FIXME: This setter is needed for premapping buffer to manually set the vertexCount. Fix this.
	public void setVertexCount(int vertexCount) { this.vertexCount = vertexCount; }
	
	
	public GLVertexBuffer(boolean isBufferStorage)
	{
		super(isBufferStorage);
	}
	
	
	
	@Override
	public void destroyAsync()
	{
		super.destroyAsync();
		this.vertexCount = 0;
	}
	
	@Override
	public int getBufferBindingTarget() { return GL32.GL_ARRAY_BUFFER; }
	
	public void uploadBuffer(ByteBuffer byteBuffer, int vertCount, EDhApiGpuUploadMethod uploadMethod, int maxExpensionSize)
	{
		if (vertCount < 0)
		{
			throw new IllegalArgumentException("VertCount is negative!");
		}
		
		// If size is zero, just ignore it.
		if (byteBuffer.limit() - byteBuffer.position() != 0)
		{
			boolean useBuffStorage = uploadMethod.useBufferStorage;
			super.uploadBuffer(byteBuffer, uploadMethod, maxExpensionSize, useBuffStorage ? 0 : GL32.GL_STATIC_DRAW);
		}
		this.vertexCount = vertCount;
	}
	
	public ByteBuffer mapBuffer(int targetSize, EDhApiGpuUploadMethod uploadMethod, int maxExpansionSize)
	{
		return super.mapBuffer(targetSize, uploadMethod, maxExpansionSize,
				uploadMethod.useBufferStorage ? GL32.GL_MAP_WRITE_BIT :
						uploadMethod.useEarlyMapping ? GL32.GL_DYNAMIC_DRAW : GL32.GL_STATIC_DRAW,
				GL32.GL_MAP_WRITE_BIT | GL32.GL_MAP_UNSYNCHRONIZED_BIT | GL32.GL_MAP_INVALIDATE_BUFFER_BIT);
	}
	
}