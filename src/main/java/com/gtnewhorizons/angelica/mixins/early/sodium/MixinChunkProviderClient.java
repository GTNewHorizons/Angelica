package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListenerManager;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderClient.class)
public class MixinChunkProviderClient implements ChunkStatusListenerManager {
    private ChunkStatusListener listener;
    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();

    @Override
    public void setListener(ChunkStatusListener listener) {
        this.listener = listener;
    }

    @Inject(method="loadChunk", at = @At(value = "INVOKE", target="Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z", shift = At.Shift.BEFORE))
    private void sodium$loadChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (this.listener != null) {
            this.listener.onChunkAdded(x, z);
            this.loadedChunks.add(ChunkPos.toLong(x, z));
        }
    }

    @Inject(method="unloadChunk", at=@At("TAIL"))
    private void sodium$unloadChunk(int x, int z, CallbackInfo ci) {
        if (this.listener != null) {
            this.listener.onChunkRemoved(x, z);
            this.loadedChunks.remove(ChunkPos.toLong(x, z));
        }
    }

}
