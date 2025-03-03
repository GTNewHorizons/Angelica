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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import com.seibel.distanthorizons.api.enums.config.EDhApiGrassSideRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

/**
 * Used to create the quads before they are converted to render-able buffers. <br><br>
 *
 * Note: the magic number 6 you see throughout this method represents the number of sides on a cube.
 */
public class LodQuadBuilder
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] opaqueQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] transparentQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	
	private final boolean doTransparency;
	private final IClientLevelWrapper clientLevelWrapper;
	
	private final EDhApiDebugRendering debugRenderingMode;
	private final EDhApiGrassSideRendering grassSideRenderingMode;
	
	
	public static final int[][][] DIRECTION_VERTEX_IBO_QUAD = new int[][][]
			{
					// X,Z //
					{ // UP
							{1, 0}, // 0
							{1, 1}, // 1
							{0, 1}, // 2
							{0, 0}, // 3
					},
					{ // DOWN
							{0, 0}, // 0
							{0, 1}, // 1
							{1, 1}, // 2
							{1, 0}, // 3
					},
					
					// X,Y //
					{ // NORTH
							{0, 0}, // 0
							{0, 1}, // 1
							{1, 1}, // 2
							
							{1, 0}, // 3
					},
					{ // SOUTH
							{1, 0}, // 0
							{1, 1}, // 1
							{0, 1}, // 2
							
							{0, 0}, // 3
					},
					
					// Z,Y //
					{ // WEST
							{0, 0}, // 0
							{1, 0}, // 1
							{1, 1}, // 2
							
							{0, 1}, // 3
					},
					{ // EAST
							{0, 1}, // 0
							{1, 1}, // 1
							{1, 0}, // 2
							
							{0, 0}, // 3
					},
			};
	
	private int premergeCount = 0;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public LodQuadBuilder(boolean doTransparency, IClientLevelWrapper clientLevelWrapper)
	{
		this.doTransparency = doTransparency;
		for (int i = 0; i < 6; i++)
		{
			this.opaqueQuads[i] = new ArrayList<>();
			this.transparentQuads[i] = new ArrayList<>();
		}
		
		this.clientLevelWrapper = clientLevelWrapper;
		
		this.debugRenderingMode = Config.Client.Advanced.Debugging.debugRendering.get();
		this.grassSideRenderingMode = Config.Client.Advanced.Graphics.Quality.grassSideRendering.get();
		
	}
	
	
	
	//===========//
	// add quads //
	//===========//
	
	public void addQuadAdj(
			EDhDirection dir, short x, short y, short z,
			short widthEastWest, short widthNorthSouthOrUpDown,
			int color, byte irisBlockMaterialId, byte skyLight, byte blockLight)
	{
		if (dir == EDhDirection.DOWN)
		{
			throw new IllegalArgumentException("addQuadAdj() is only for adj direction! Not UP or Down!");
		}
		
		BufferQuad quad = new BufferQuad(x, y, z, widthEastWest, widthNorthSouthOrUpDown, color, irisBlockMaterialId, skyLight, blockLight, dir);
		ArrayList<BufferQuad> quadList = (this.doTransparency && ColorUtil.getAlpha(color) < 255) ? this.transparentQuads[dir.ordinal()] : this.opaqueQuads[dir.ordinal()];
		if (!quadList.isEmpty() &&
				(
						quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
								|| quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
		)
		{
			this.premergeCount++;
			return;
		}
		
		quadList.add(quad);
	}
	
	// XZ
	public void addQuadUp(short minX, short maxY, short minZ, short widthEastWest, short widthNorthSouthOrUpDown, int color, byte irisBlockMaterialId, byte skylight, byte blocklight) // TODO argument names are wrong
	{
		BufferQuad quad = new BufferQuad(minX, maxY, minZ, widthEastWest, widthNorthSouthOrUpDown, color, irisBlockMaterialId, skylight, blocklight, EDhDirection.UP);
		boolean isTransparent = (this.doTransparency && ColorUtil.getAlpha(color) < 255);
		ArrayList<BufferQuad> quadList = isTransparent ? this.transparentQuads[EDhDirection.UP.ordinal()] : this.opaqueQuads[EDhDirection.UP.ordinal()];
		
		
		// attempt to merge this quad with adjacent ones
		if (!quadList.isEmpty() &&
				(
					quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
					|| quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
			)
		{
			this.premergeCount++;
			return;
		}
		
		quadList.add(quad);
	}
	
	public void addQuadDown(short x, short y, short z, short width, short wz, int color, byte irisBlockMaterialId, byte skylight, byte blocklight)
	{
		BufferQuad quad = new BufferQuad(x, y, z, width, wz, color, irisBlockMaterialId, skylight, blocklight, EDhDirection.DOWN);
		ArrayList<BufferQuad> qs = (doTransparency && ColorUtil.getAlpha(color) < 255)
				? transparentQuads[EDhDirection.DOWN.ordinal()] : opaqueQuads[EDhDirection.DOWN.ordinal()];
		if (!qs.isEmpty()
				&& (qs.get(qs.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
						|| qs.get(qs.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
			)
		{
			premergeCount++;
			return;
		}
		qs.add(quad);
	}
	
	
	
	//=================//
	// data finalizing //
	//=================//
	
	/** runs any final data cleanup, merging, etc. */
	public void finalizeData() { this.mergeQuads(); }
	
	/** Uses Greedy meshing to merge this builder's Quads. */
	public void mergeQuads()
	{
		long mergeCount = 0; // can be used for debugging
		long preQuadsCount = this.getCurrentOpaqueQuadsCount() + this.getCurrentTransparentQuadsCount();
		if (preQuadsCount <= 1)
		{
			return;
		}
		
		for (int directionIndex = 0; directionIndex < 6; directionIndex++)
		{
			mergeCount += mergeQuadsInternal(this.opaqueQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			if (this.doTransparency)
			{
				mergeCount += mergeQuadsInternal(this.transparentQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			}
			
			
			// only run the second merge if the face is the top or bottom
			if (directionIndex == EDhDirection.UP.ordinal() || directionIndex == EDhDirection.DOWN.ordinal())
			{
				mergeCount += mergeQuadsInternal(this.opaqueQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
				if (this.doTransparency)
				{
					mergeCount += mergeQuadsInternal(this.transparentQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
				}
			}
		}
		
		//long postQuadsCount = this.getCurrentOpaqueQuadsCount() + this.getCurrentTransparentQuadsCount();
		//LOGGER.trace("Merged "+mergeCount+"/"+preQuadsCount+"("+(mergeCount / (double) preQuadsCount)+") quads");
	}
	
	/** Merges all of this builder's quads for the given directionIndex (up, down, left, etc.) in the given direction */
	private static long mergeQuadsInternal(ArrayList<BufferQuad>[] list, int directionIndex, BufferMergeDirectionEnum mergeDirection)
	{
		if (list[directionIndex].size() <= 1)
			return 0;
		
		list[directionIndex].sort((objOne, objTwo) -> objOne.compare(objTwo, mergeDirection));
		
		long mergeCount = 0;
		ListIterator<BufferQuad> iter = list[directionIndex].listIterator();
		BufferQuad currentQuad = iter.next();
		while (iter.hasNext())
		{
			BufferQuad nextQuad = iter.next();
			
			if (currentQuad.tryMerge(nextQuad, mergeDirection))
			{
				// merge successful, attempt to merge the next quad
				mergeCount++;
				iter.set(null);
			}
			else
			{
				// merge fail, move on to the next quad
				currentQuad = nextQuad;
			}
		}
		list[directionIndex].removeIf(Objects::isNull);
		return mergeCount;
	}
	
	
	
	//==============//
	// buffer setup //
	//==============//
	
	public ArrayList<ByteBuffer> makeOpaqueVertexBuffers() { return this.makeVertexBuffers(this.opaqueQuads); }
	public ArrayList<ByteBuffer> makeTransparentVertexBuffers() { return this.makeVertexBuffers(this.transparentQuads); }
	private ArrayList<ByteBuffer> makeVertexBuffers(ArrayList<BufferQuad>[] quadList)
	{
		ArrayList<ByteBuffer> byteBufferList = new ArrayList<>(3);
		
		ByteBuffer buffer = null;
		for (int directionIndex = 0; directionIndex < 6; directionIndex++)
		{
			// ignore empty directions
			if (quadList[directionIndex].isEmpty())
			{
				continue;
			}
			
			// put all the quads in this direction into the buffer
			for (int quadIndex = 0; quadIndex < quadList[directionIndex].size(); quadIndex++)
			{
				// if this is the first iteration or the buffer is full, 
				// create a new buffer
				if (buffer == null || !buffer.hasRemaining())
				{
					buffer = MemoryUtil.memAlloc(ColumnRenderBuffer.FULL_SIZED_BUFFER);
					byteBufferList.add(buffer);
				}
				
				this.putQuad(buffer, quadList[directionIndex].get(quadIndex));
			}
		}
		
		// rewind all the buffers so they can be read from
		for (int i = 0; i < byteBufferList.size(); i++)
		{
			buffer = byteBufferList.get(i);
			buffer.limit(buffer.position());
			buffer.rewind();
		}
		
		return byteBufferList;
	}
	private void putQuad(ByteBuffer bb, BufferQuad quad)
	{
		int[][] quadBase = DIRECTION_VERTEX_IBO_QUAD[quad.direction.ordinal()];
		short widthEastWest = quad.widthEastWest;
		short widthNorthSouth = quad.widthNorthSouthOrUpDown;
		byte normalIndex = (byte) quad.direction.ordinal();
		EDhDirection.Axis axis = quad.direction.getAxis();
		for (int i = 0; i < quadBase.length; i++)
		{
			short dx, dy, dz;
			int mx, my, mz;
			switch (axis)
			{
				case X: // ZY
					dx = 0;
					dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					dz = quadBase[i][0] == 1 ? widthEastWest : 0;
					mx = 0;
					my = quadBase[i][1] == 1 ? 1 : -1;
					mz = quadBase[i][0] == 1 ? 1 : -1;
					break;
				case Y: // XZ
					dx = quadBase[i][0] == 1 ? widthEastWest : 0;
					dy = 0;
					dz = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					mx = quadBase[i][0] == 1 ? 1 : -1;
					my = 0;
					mz = quadBase[i][1] == 1 ? 1 : -1;
					break;
				case Z: // XY
					dx = quadBase[i][0] == 1 ? widthEastWest : 0;
					dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					dz = 0;
					mx = quadBase[i][0] == 1 ? 1 : -1;
					my = quadBase[i][1] == 1 ? 1 : -1;
					mz = 0;
					break;
				default:
					throw new IllegalArgumentException("Invalid Axis enum: " + axis);
			}
			
			
			int color = quad.color;
			
			// use custom side color logic for grass blocks
			if (quad.irisBlockMaterialId == EDhApiBlockMaterial.GRASS.index)
			{
				// only use dirt colors if debug rendering is disabled
				if (this.debugRenderingMode == EDhApiDebugRendering.OFF)
				{
					// determine if any custom coloring logic should be used
					if (this.grassSideRenderingMode != EDhApiGrassSideRendering.AS_GRASS)
					{
						// only change the vertex color if it's on the side or bottom
						if (quad.direction.getAxis().isHorizontal() || quad.direction == EDhDirection.DOWN)
						{
							if (this.grassSideRenderingMode == EDhApiGrassSideRendering.AS_DIRT
									// if we want the color to fade, only apply the dirt color to the bottom vertices
									|| (this.grassSideRenderingMode == EDhApiGrassSideRendering.FADE_TO_DIRT && quadBase[i][1] == 0)
									// always render the bottom as dirt
									|| quad.direction == EDhDirection.DOWN)
							{
								// for horizontal and bottom faces of grass blocks, use the  dirt color to
								// prevent green cliff walls
								color = this.clientLevelWrapper.getDirtBlockColor();
								color = ColorUtil.applyShade(color, MC.getShade(quad.direction));
							}
						}
					}
				}
			}
			
			
			this.putVertex(bb, (short) (quad.x + dx), (short) (quad.y + dy), (short) (quad.z + dz),
					quad.hasError ? ColorUtil.RED : color,
					quad.hasError ? 0 : normalIndex,
					quad.hasError ? 0 : quad.irisBlockMaterialId,
					quad.hasError ? 15 : quad.skyLight,
					quad.hasError ? 15 : quad.blockLight,
					mx, my, mz);
		}
	}
	private void putVertex(ByteBuffer bb, short x, short y, short z, int color, byte normalIndex, byte irisBlockMaterialId, byte skylight, byte blocklight, int mx, int my, int mz)
	{
		skylight %= 16;
		blocklight %= 16;
		
		bb.putShort(x);
		bb.putShort(y);
		bb.putShort(z);
		
		short meta = 0;
		meta |= (skylight | (blocklight << 4));
		byte mirco = 0;
		// mirco offset which is a xyz 2bit value
		// 0b00 = no offset
		// 0b01 = positive offset
		// 0b11 = negative offset
		// format is: 0b00zzyyxx
		if (mx != 0) mirco |= mx > 0 ? 0b01 : 0b11;
		if (my != 0) mirco |= my > 0 ? 0b0100 : 0b1100;
		if (mz != 0) mirco |= mz > 0 ? 0b010000 : 0b110000;
		meta |= mirco << 8;
		
		bb.putShort(meta);
		byte r = (byte) ColorUtil.getRed(color);
		byte g = (byte) ColorUtil.getGreen(color);
		byte b = (byte) ColorUtil.getBlue(color);
		byte a = this.doTransparency ? (byte) ColorUtil.getAlpha(color) : (byte) 255; // TODO should this be called here or happen somewhere else?
		bb.put(r);
		bb.put(g);
		bb.put(b);
		bb.put(a);
		
		// Block ID and normal index are used by the Iris format
		bb.put(irisBlockMaterialId);
		bb.put(normalIndex);
		bb.putShort((short) 0); // padding to make sure the vertex format as a whole is a multiple of 4
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public int getCurrentOpaqueQuadsCount()
	{
		int i = 0;
		for (ArrayList<BufferQuad> quadList : this.opaqueQuads)
		{
			i += quadList.size();
		}
		
		return i;
	}
	public int getCurrentTransparentQuadsCount()
	{
		if (!this.doTransparency)
		{
			return 0;
		}
		
		int i = 0;
		for (ArrayList<BufferQuad> quadList : this.transparentQuads)
		{
			i += quadList.size();
		}
		
		return i;
	}
	
	/** Returns how many GpuBuffers will be needed to render opaque quads in this builder. */
	public int getCurrentNeededOpaqueVertexBufferCount() { return MathUtil.ceilDiv(this.getCurrentOpaqueQuadsCount(), ColumnRenderBuffer.MAX_QUADS_PER_BUFFER); }
	/** Returns how many GpuBuffers will be needed to render transparent quads in this builder. */
	public int getCurrentNeededTransparentVertexBufferCount()
	{
		if (!this.doTransparency)
		{
			return 0;
		}
		
		return MathUtil.ceilDiv(this.getCurrentTransparentQuadsCount(), ColumnRenderBuffer.MAX_QUADS_PER_BUFFER);
	}
	
}
