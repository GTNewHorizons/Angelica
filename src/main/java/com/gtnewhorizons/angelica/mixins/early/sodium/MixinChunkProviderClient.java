package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkProviderClient.class)
public abstract class MixinChunkProviderClient {

    @Inject(method="unloadChunk", at=@At("TAIL"))
    private void sodium$unloadChunk(int x, int z, CallbackInfo ci) {
        SodiumWorldRenderer.getInstance().onChunkRemoved(x, z);
    }

}
