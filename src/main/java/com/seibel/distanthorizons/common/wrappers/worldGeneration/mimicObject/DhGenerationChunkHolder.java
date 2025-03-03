package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

#if MC_VER >= MC_1_21_1

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;

public class DhGenerationChunkHolder extends GenerationChunkHolder
{
	
	public DhGenerationChunkHolder(ChunkPos pos) { super(pos); }
	
	@Override 
	public int getTicketLevel() { return 0; }
	@Override 
	public int getQueueLevel() { return 0; }
	
	#if MC_VER < MC_1_21_3
	#else
	@Override
	protected void addSaveDependency(CompletableFuture<?> completableFuture) { }
	#endif
	
}

#endif