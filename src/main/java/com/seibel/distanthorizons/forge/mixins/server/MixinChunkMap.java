package com.seibel.distanthorizons.forge.mixins.server;

import com.seibel.distanthorizons.common.commonMixins.MixinChunkMapCommon;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public class MixinChunkMap
{
	
	@Unique
	private static final String CHUNK_SERIALIZER_WRITE
			= "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;write(" +
			"Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)" +
			"Lnet/minecraft/nbt/CompoundTag;";
	
	@Shadow
	@Final
	ServerLevel level;
	
	// firing at INVOKE causes issues with C2ME and is probably unnecessary since we
	// don't need the chunk(s) before MC has finished saving them
	@Inject(method = "save", at = @At(value = "RETURN", target = CHUNK_SERIALIZER_WRITE))
	private void onChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> ci)
	{ MixinChunkMapCommon.onChunkSave(this.level, chunk, ci); }
	
}