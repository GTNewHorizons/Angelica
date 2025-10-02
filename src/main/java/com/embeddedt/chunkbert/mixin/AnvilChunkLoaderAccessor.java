package com.embeddedt.chunkbert.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AnvilChunkLoader.class)
public interface AnvilChunkLoaderAccessor {
    @Invoker("writeChunkToNBT")
    void invokeWriteChunkToNBT(Chunk chunk, World world, NBTTagCompound compound);
}
