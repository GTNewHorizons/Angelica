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

package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.StatsMap;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Java representation of one or more OpenGL buffers for rendering.
 *
 * @see ColumnRenderBufferBuilder
 */
public class ColumnRenderBuffer implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** number of bytes a single quad takes */
	public static final int QUADS_BYTE_SIZE = LodUtil.LOD_VERTEX_FORMAT.getByteSize() * 4;
	/** how big a single VBO can be in bytes */
	public static final int MAX_VBO_BYTE_SIZE = 10 * 1024 * 1024; // 10 MB
	public static final int MAX_QUADS_PER_BUFFER = MAX_VBO_BYTE_SIZE / QUADS_BYTE_SIZE;
	public static final int FULL_SIZED_BUFFER = MAX_QUADS_PER_BUFFER * QUADS_BYTE_SIZE;
	
	
	
	public final DhBlockPos blockPos;
	
	public boolean buffersUploaded = false;
	
	private GLVertexBuffer[] vbos;
	private GLVertexBuffer[] vbosTransparent;
	
	private CompletableFuture<ColumnRenderBuffer> uploadFuture = null;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public ColumnRenderBuffer(DhBlockPos blockPos)
	{
		this.blockPos = blockPos;
		this.vbos = new GLVertexBuffer[0];
		this.vbosTransparent = new GLVertexBuffer[0];
	}
	
	
	
	//==================//
	// buffer uploading //
	//==================//
	
	/** Should be run on a DH thread. */
	public synchronized CompletableFuture<ColumnRenderBuffer> makeAndUploadBuffersAsync(LodQuadBuilder builder, EDhApiGpuUploadMethod gpuUploadMethod)
	{
		// separate variable to prevent race condition when checking null
		CompletableFuture<ColumnRenderBuffer> future = this.uploadFuture;
		if (future != null)
		{
			// upload already in process
			return future;
		}
		
		// new upload needed
		future = new CompletableFuture<>();
		this.uploadFuture = future;
		
		
		
		// make the buffers
		ArrayList<ByteBuffer> opaqueBuffers = builder.makeOpaqueVertexBuffers();
		ArrayList<ByteBuffer> transparentBuffers = builder.makeTransparentVertexBuffers();
		
		this.vbos = resizeBuffer(this.vbos, opaqueBuffers.size());
		this.vbosTransparent = resizeBuffer(this.vbosTransparent, transparentBuffers.size());
		
		
		// upload on MC's render thread
		GLProxy.getInstance().queueRunningOnRenderThread(() ->
		{
			try
			{
				// skip this event if requested
				if (Thread.interrupted() || this.uploadFuture.isCancelled())
				{
					throw new InterruptedException();
				}
				
				// upload on the render thread
				uploadBuffersDirect(this.vbos, opaqueBuffers, gpuUploadMethod);
				uploadBuffersDirect(this.vbosTransparent, transparentBuffers, gpuUploadMethod);
				this.buffersUploaded = true;
				
				// success
				this.uploadFuture.complete(this);
				this.uploadFuture = null;
			}
			catch (InterruptedException ignore) 
			{
				this.uploadFuture.complete(this);
				this.uploadFuture = null;
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected issue uploading buffer ["+this.blockPos +"], error: ["+e.getMessage()+"].", e);
				
				this.uploadFuture.completeExceptionally(e);
				this.uploadFuture = null;
			}
			finally
			{
				// all the buffers must be manually freed to prevent memory leaks
				
				for (ByteBuffer buffer : opaqueBuffers)
				{
					MemoryUtil.memFree(buffer);
				}
				
				for (ByteBuffer buffer : transparentBuffers)
				{
					MemoryUtil.memFree(buffer);
				}
			}
		});
		
		return future;
	}
	private static GLVertexBuffer[] resizeBuffer(GLVertexBuffer[] vbos, int newSize)
	{
		if (vbos.length == newSize)
		{
			return vbos;
		}
		
		GLVertexBuffer[] newVbos = new GLVertexBuffer[newSize];
		System.arraycopy(vbos, 0, newVbos, 0, Math.min(vbos.length, newSize));
		if (newSize < vbos.length)
		{
			for (int i = newSize; i < vbos.length; i++)
			{
				if (vbos[i] != null)
				{
					vbos[i].close();
				}
			}
		}
		return newVbos;
	}
	private static void uploadBuffersDirect(GLVertexBuffer[] vbos, ArrayList<ByteBuffer> byteBuffers, EDhApiGpuUploadMethod method) throws InterruptedException
	{
		int vboIndex = 0;
		for (int i = 0; i < byteBuffers.size(); i++)
		{
			if (vboIndex >= vbos.length)
			{
				throw new RuntimeException("Too many vertex buffers!!");
			}
			
			
			// get or create the VBO
			if (vbos[vboIndex] == null)
			{
				vbos[vboIndex] = new GLVertexBuffer(method.useBufferStorage);
			}
			GLVertexBuffer vbo = vbos[vboIndex];
			
			
			ByteBuffer buffer = byteBuffers.get(i);
			int size = buffer.limit() - buffer.position();
			
			try
			{
				vbo.bind();
				vbo.uploadBuffer(buffer, size / LodUtil.LOD_VERTEX_FORMAT.getByteSize(), method, FULL_SIZED_BUFFER);
			}
			catch (Exception e)
			{
				vbos[vboIndex] = null;
				vbo.close();
				LOGGER.error("Failed to upload buffer: ", e);
			}
			
			vboIndex++;
		}
		
		if (vboIndex < vbos.length)
		{
			throw new RuntimeException("Too few vertex buffers!!");
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	/** @return true if something was rendered, false otherwise */
	public boolean renderOpaque(LodRenderer renderContext, DhApiRenderParam renderEventParam)
	{
		boolean hasRendered = false;
		renderContext.setModelViewMatrixOffset(this.blockPos, renderEventParam);
		for (GLVertexBuffer vbo : this.vbos)
		{
			if (vbo == null)
			{
				continue;
			}
			
			if (vbo.getVertexCount() == 0)
			{
				continue;
			}
			
			hasRendered = true;
			renderContext.drawVbo(vbo, this);
		}
		return hasRendered;
	}
	
	/** @return true if something was rendered, false otherwise */
	public boolean renderTransparent(LodRenderer renderContext, DhApiRenderParam renderEventParam)
	{
		boolean hasRendered = false;
		
		try
		{
			// can throw an IllegalStateException if the GL program was freed before it should've been
			renderContext.setModelViewMatrixOffset(this.blockPos, renderEventParam);
			
			for (GLVertexBuffer vbo : this.vbosTransparent)
			{
				if (vbo == null)
				{
					continue;
				}
				
				if (vbo.getVertexCount() == 0)
				{
					continue;
				}
				
				hasRendered = true;
				renderContext.drawVbo(vbo, this);
			}
		}
		catch (IllegalStateException e)
		{
			LOGGER.error("renderContext program doesn't exist for pos: "+this.blockPos, e);
		}
		
		return hasRendered;
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** can be used when debugging */
	public boolean hasNonNullVbos() { return this.vbos != null || this.vbosTransparent != null; }
	
	/** can be used when debugging */
	public int vboBufferCount() 
	{
		int count = 0;
		
		if (this.vbos != null)
		{
			count += this.vbos.length;
		}
		
		if (this.vbosTransparent != null)
		{
			count += this.vbosTransparent.length;
		}
		
		return count;
	}
	
	public boolean uploadInProgress() { return this.uploadFuture != null; }
	
	public void debugDumpStats(StatsMap statsMap)
	{
		statsMap.incStat("RenderBuffers");
		statsMap.incStat("SimpleRenderBuffers");
		for (GLVertexBuffer vertexBuffer : vbos)
		{
			if (vertexBuffer != null)
			{
				statsMap.incStat("VBOs");
				if (vertexBuffer.getSize() == FULL_SIZED_BUFFER)
				{
					statsMap.incStat("FullsizedVBOs");
				}
				
				if (vertexBuffer.getSize() == 0)
				{
					GLProxy.GL_LOGGER.warn("VBO with size 0");
				}
				statsMap.incBytesStat("TotalUsage", vertexBuffer.getSize());
			}
		}
	}
	
	
	
	
	//================//
	// base overrides //
	//================//
	
	/**
	 * This method is called when object is no longer in use.
	 * Called either after uploadBuffers() returned false (On buffer Upload
	 * thread), or by others when the object is not being used. (not in build,
	 * upload, or render state). 
	 */
	@Override
	public void close()
	{
		this.buffersUploaded = false;
		
		GLProxy.getInstance().queueRunningOnRenderThread(() ->
		{
			for (GLVertexBuffer buffer : this.vbos)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}
			
			for (GLVertexBuffer buffer : this.vbosTransparent)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}
		});
	}
	
}
