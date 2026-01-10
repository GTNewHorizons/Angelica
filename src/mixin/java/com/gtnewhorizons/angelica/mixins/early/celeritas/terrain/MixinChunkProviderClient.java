package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderClient.class)
public abstract class MixinChunkProviderClient {
    @Shadow @Final private World worldObj;

    @Inject(method = "loadChunk", at = @At("RETURN"))
    private void celeritas$afterLoadChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkTrackerHolder.get(this.worldObj).onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
    }

    @Inject(method = "unloadChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;onChunkUnload()V", shift = At.Shift.AFTER))
    private void celeritas$afterUnloadChunk(int x, int z, CallbackInfo ci) {
        ChunkTrackerHolder.get(this.worldObj).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
    }
}
