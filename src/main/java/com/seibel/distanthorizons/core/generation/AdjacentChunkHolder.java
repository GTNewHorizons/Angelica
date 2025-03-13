package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;

/** holds adjacent chunks without having to create new Pos objects */
public class AdjacentChunkHolder
{
	final IChunkWrapper[] chunkArray = new IChunkWrapper[9];		
	
	
	//==============//
	// constructors //
	//==============//
	
	public AdjacentChunkHolder(IChunkWrapper centerWrapper) { this.chunkArray[4] = centerWrapper; }
	
	public AdjacentChunkHolder(IChunkWrapper centerWrapper, @NotNull ArrayList<IChunkWrapper> nearbyChunkList) 
	{ 
		this.chunkArray[4] = centerWrapper;
		
		DhChunkPos centerChunkPos = centerWrapper.getChunkPos();
		
		// generate the list of chunk pos we need,
		// currently a 3x3 grid
		HashSet<DhChunkPos> requestedAdjacentPositions = new HashSet<>(9);
		for (int xOffset = -1; xOffset <= 1; xOffset++)
		{
			for (int zOffset = -1; zOffset <= 1; zOffset++)
			{
				DhChunkPos adjacentPos = new DhChunkPos(centerChunkPos.getX() + xOffset, centerChunkPos.getZ() + zOffset);
				requestedAdjacentPositions.add(adjacentPos);
			}
		}
		
		for (int chunkIndex = 0; chunkIndex < nearbyChunkList.size(); chunkIndex++) // using iterators in high traffic areas can cause GC issues due to allocating a bunch of iterators, use an indexed for-loop instead
		{
			IChunkWrapper chunk = nearbyChunkList.get(chunkIndex);
			if (chunk != null && requestedAdjacentPositions.contains(chunk.getChunkPos()))
			{
				// remove the newly found position
				requestedAdjacentPositions.remove(chunk.getChunkPos());
				
				// add the adjacent chunk
				this.add(chunk);
			}
			
			if (requestedAdjacentPositions.isEmpty())
			{
				// we found every chunk we needed, we don't need to keep iterating
				break;
			}
		}
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	public void add(IChunkWrapper centerWrapper) 
	{
		DhChunkPos centerPos = this.chunkArray[4].getChunkPos();
		DhChunkPos offsetPos = centerWrapper.getChunkPos();
		
		int offsetX = offsetPos.getX() - centerPos.getX();
		if (offsetX < -1 || offsetX > 1)
		{
			return;
		}
		
		int offsetZ = offsetPos.getZ() - centerPos.getZ();
		if (offsetZ < -1 || offsetZ > 1)
		{
			return;
		}
		
		// equivalent to 4 + offsetX + (offsetZ * 3).
		this.chunkArray[4 + offsetX + offsetZ + (offsetZ << 1)] = centerWrapper;
	}

	public IChunkWrapper getByBlockPos(int blockX, int blockZ)
	{
		int chunkX = BitShiftUtil.divideByPowerOfTwo(blockX, 4);
		int chunkZ = BitShiftUtil.divideByPowerOfTwo(blockZ, 4);
		IChunkWrapper centerChunk = this.chunkArray[4];
		DhChunkPos centerPos = centerChunk.getChunkPos();
		if (centerPos.getX() == chunkX && centerPos.getZ() == chunkZ)
		{
			return centerChunk;
		}
		
		int offsetX = chunkX - centerPos.getX();
		if (offsetX < -1 || offsetX > 1)
		{
			return null;
		}
		
		int offsetZ = chunkZ - centerPos.getZ();
		if (offsetZ < -1 || offsetZ > 1)
		{
			return null;
		}
		
		// equivalent to 4 + offsetX + (offsetZ * 3).
		return this.chunkArray[4 + offsetX + offsetZ + (offsetZ << 1)];
	}
	
	
}
	