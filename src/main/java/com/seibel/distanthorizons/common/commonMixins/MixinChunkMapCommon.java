package com.seibel.distanthorizons.common.commonMixins;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class MixinChunkMapCommon
{
	
	public static void onChunkSave(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<Boolean> ci)
	{
		// is this position already being updated?
		if (SharedApi.isChunkAtChunkPosAlreadyUpdating(chunk.getPos().x, chunk.getPos().z))
		{
			return;
		}
		
		
		
		// is this chunk being saved to disk?
		boolean savingChunkToDisk = ci.getReturnValue();
		// true means a chunk was saved to disk
		if (!savingChunkToDisk)
		{
			return;
		}
		
		// TODO are the following validations necessary since we are checking above if 
		//  the callback return value should state if the chunk was actually saved or not?
		//  Do we trust it to always be correct?
		
		
		
		// corrupt/incomplete chunk validation //
		
		// MC has a tendency to try saving incomplete or corrupted chunks (which show up as empty or black chunks)
		// this logic should prevent that from happening
		#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
		if (chunk.isUnsaved() || chunk.getUpgradeData() != null || !chunk.isLightCorrect())
		{
			return;
		}
		#else
		if (chunk.isUnsaved() || chunk.isUpgrading() || !chunk.isLightCorrect())
		{
			return;
		}
		#endif
		
		
		
		// biome validation //
		
		// some chunks may be missing their biomes, which cause issues when attempting to save them
		#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
		if (chunk.getBiomes() == null)
		{
			return;
		}
		#else
		try
		{
			// this will throw an exception if the biomes aren't set up
			chunk.getNoiseBiome(0,0,0);
		}
		catch (Exception e)
		{
			return;
		}
		#endif
		
		
		
		// submit the update event
		ServerApi.INSTANCE.serverChunkSaveEvent(
				new ChunkWrapper(chunk, ServerLevelWrapper.getWrapper(level)),
				ServerLevelWrapper.getWrapper(level)
		);
	}
	
}
