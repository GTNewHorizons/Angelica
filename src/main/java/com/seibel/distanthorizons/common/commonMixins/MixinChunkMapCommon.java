package com.seibel.distanthorizons.common.commonMixins;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class MixinChunkMapCommon
{

	public static void onChunkSave(WorldServer level, Chunk chunk, CallbackInfoReturnable<Boolean> ci)
	{
		// is this position already being updated?
		if (SharedApi.isChunkAtChunkPosAlreadyUpdating(chunk.xPosition, chunk.zPosition))
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

		// TODO if (chunk.isUnsaved() || chunk.getUpgradeData() != null || !chunk.isLightCorrect())
        if (chunk.isModified)
		{
			return;
		}



		// biome validation //

		// some chunks may be missing their biomes, which cause issues when attempting to save them
		// TODO if (chunk.getBiomes() == null) return;



		// submit the update event
		ServerApi.INSTANCE.serverChunkSaveEvent(
				new ChunkWrapper(chunk, ServerLevelWrapper.getWrapper(level)),
				ServerLevelWrapper.getWrapper(level)
		);
	}

}
