package com.gtnewhorizons.angelica.mixins.early.archaic.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderClient.class)
public class MixinChunkProviderClient {
    @Inject(method = "loadChunk", at = @At("HEAD"))
    private void onChunkLoad(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if(!Minecraft.getMinecraft().func_152345_ab/*isCallingFromMinecraftThread*/())
            throw new IllegalStateException("Attempted to load a chunk off-thread!");
    }
}
