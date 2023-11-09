package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListenerManager;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderClient.class)
public abstract class MixinChunkProviderClient implements ChunkStatusListenerManager {
    private ChunkStatusListener sodium$listener;
    private final LongOpenHashSet sodium$loadedChunks = new LongOpenHashSet();
    @Unique private boolean sodium$needsTrackingUpdate = false;

    @Shadow private Chunk blankChunk;
    @Shadow public abstract Chunk provideChunk(int x, int z);

    @Override
    public void setListener(ChunkStatusListener listener) {
        this.sodium$listener = listener;
    }

    @Inject(method="loadChunk", at = @At(value = "INVOKE", target="Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z", shift = At.Shift.BEFORE), remap = false)
    private void sodium$loadChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (this.sodium$listener != null) {
            this.sodium$listener.onChunkAdded(x, z);
            this.sodium$loadedChunks.add(ChunkPos.toLong(x, z));
        }
    }

    @Inject(method="unloadChunk", at=@At("TAIL"))
    private void sodium$unloadChunk(int x, int z, CallbackInfo ci) {
        if (this.sodium$listener != null) {
            this.sodium$listener.onChunkRemoved(x, z);
            this.sodium$loadedChunks.remove(ChunkPos.toLong(x, z));
        }
    }


    @Inject(method="unloadQueuedChunks", at=@At("TAIL"))
    private void afterTick(CallbackInfoReturnable<Boolean> cir) {
        // TODO: sodium$needsTrackingUpdate - is it Relevant here?
        // Gets set on Sodium by afterChunkMapCenterChanged & afterLoadDistanceChanged
        if (!this.sodium$needsTrackingUpdate) {
            return;
        }

        LongIterator it = this.sodium$loadedChunks.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            int x = ChunkPos.getPackedX(pos);
            int z = ChunkPos.getPackedZ(pos);

            if (provideChunk(x, z) == blankChunk) {
                it.remove();

                if (this.sodium$listener != null) {
                    this.sodium$listener.onChunkRemoved(x, z);
                }
            }
        }

        this.sodium$needsTrackingUpdate = false;
    }


}
