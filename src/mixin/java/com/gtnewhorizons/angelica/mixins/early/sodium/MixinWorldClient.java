package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.client.multiplayer.WorldClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import me.jellysquid.mods.sodium.client.render.chunk.map.IChunkTracker;

@Mixin(WorldClient.class)
public class MixinWorldClient implements ChunkTrackerHolder {
    @Unique
    private final IChunkTracker angelica$tracker = IChunkTracker.newInstance();

    @Inject(method = "doPreChunk", at = @At("TAIL"))
    private void sodium$loadChunk(int x, int z, boolean load, CallbackInfo ci) {
        if(load) {
            this.angelica$tracker.onChunkAdded(x, z);
        } else {
            this.angelica$tracker.onChunkRemoved(x, z);
        }
    }

    @Override
    public IChunkTracker sodium$getTracker() {
        return this.angelica$tracker;
    }
}
