package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkStatus;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTracker;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public class MixinWorldClient implements ChunkTrackerHolder {
    private final ChunkTracker angelica$tracker = new ChunkTracker();

    @Inject(method = "doPreChunk", at = @At("TAIL"))
    private void sodium$loadChunk(int x, int z, boolean load, CallbackInfo ci) {
        if(load) {
            this.angelica$tracker.onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
        } else {
            this.angelica$tracker.onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
        }
    }

    @Override
    public ChunkTracker sodium$getTracker() {
        return this.angelica$tracker;
    }
}
