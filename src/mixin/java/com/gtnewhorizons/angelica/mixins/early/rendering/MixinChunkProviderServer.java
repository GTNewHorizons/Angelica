package com.gtnewhorizons.angelica.mixins.early.rendering;

import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// run after https://github.com/embeddedt/ArchaicFix/blob/main/src/main/java/org/embeddedt/archaicfix/mixins/common/core/MixinChunkProviderServer.java
// to allow this mixin to fail silently
@Mixin(value = ChunkProviderServer.class, priority = 999)
public class MixinChunkProviderServer {
    @Redirect(method = "originalLoadChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;", remap = true), remap = false, expect = 0)
    private Chunk sodium$populateChunkWithBiomes(IChunkProvider instance, int chunkX, int chunkZ) {
        Chunk chunk = instance.provideChunk(chunkX, chunkZ);
        if(chunk != null) {
            WorldChunkManager manager = chunk.worldObj.getWorldChunkManager();
            for(int z = 0; z < 16; z++) {
                for(int x = 0; x < 16; x++) {
                    chunk.getBiomeGenForWorldCoords(x, z, manager);
                }
            }
        }
        return chunk;
    }
}
