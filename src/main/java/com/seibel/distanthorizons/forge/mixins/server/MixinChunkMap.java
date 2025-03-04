package com.seibel.distanthorizons.forge.mixins.server;

import com.seibel.distanthorizons.common.commonMixins.MixinChunkMapCommon;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilChunkLoader.class)
public class MixinChunkMap
{
	@Inject(method = "saveChunk", at = @At(value = "RETURN"))
	private void onChunkSave(World world, Chunk chunk, CallbackInfo ci)
	{ MixinChunkMapCommon.onChunkSave((WorldServer)world, chunk, ci); }

}
