package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer {

    @Shadow public WorldServer worldObj;

    @Redirect(method = "unloadChunksIfNotNearSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/DimensionManager;shouldLoadSpawn(I)Z", remap = false))
    private boolean neverLoadSpawn(int dim) {
        return !ArchaicConfig.disableSpawnChunks && DimensionManager.shouldLoadSpawn(dim);
    }

    @Redirect(method = "originalLoadChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;", remap = true), remap = false)
    private Chunk populateChunkWithBiomes(IChunkProvider instance, int chunkX, int chunkZ) {
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
