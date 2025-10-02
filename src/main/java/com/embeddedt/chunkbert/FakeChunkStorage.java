package com.embeddedt.chunkbert;

import com.embeddedt.chunkbert.ext.AnvilChunkLoaderExt;
import com.embeddedt.chunkbert.mixin.AnvilChunkLoaderAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

public class FakeChunkStorage extends AnvilChunkLoader {
    public FakeChunkStorage(File file, DataFixer fixer) {
        super(file, fixer);
        if(ChunkbertConfig.noBlockEntities)
            ((AnvilChunkLoaderExt)this).chunkbert$setLoadsTileEntities(false);
    }
    public static FakeChunkStorage getFor(File file, BiomeProvider provider) {
        return new FakeChunkStorage(file, Minecraft.getMinecraft().getDataFixer());
    }
    public NBTTagCompound loadTag(ChunkPos pos) throws IOException {
        NBTTagCompound compound;
        DataInputStream datainputstream = RegionFileCache.getChunkInputStream(this.chunkSaveLocation, pos.x, pos.z);

        if (datainputstream == null)
        {
            return null;
        }

        compound = Minecraft.getMinecraft().getDataFixer().process(FixTypes.CHUNK, CompressedStreamTools.read(datainputstream));
        datainputstream.close(); // Forge: close stream after use
        return compound;
    }
    public Supplier<Chunk> deserialize(ChunkPos pos, NBTTagCompound tag, WorldClient world) {
        Object[] data = this.checkedReadChunkFromNBT__Async(world, pos.x, pos.z, tag);
        if(data != null) {
            Chunk chunk = (Chunk)data[0];
            NBTTagCompound nbttagcompound = (NBTTagCompound) data[1];
            return () -> {
                this.loadEntities(world, nbttagcompound.getCompoundTag("Level"), chunk);
                return chunk;
            };
        }
        return null;
    }
    public NBTTagCompound serialize(Chunk chunk) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();
        NBTTagCompound nbttagcompound1 = new NBTTagCompound();
        nbttagcompound.setTag("Level", nbttagcompound1);
        nbttagcompound.setInteger("DataVersion", 1343);
        net.minecraftforge.fml.common.FMLCommonHandler.instance().getDataFixer().writeVersionData(nbttagcompound);
        ((AnvilChunkLoaderAccessor)this).invokeWriteChunkToNBT(chunk, chunk.getWorld(), nbttagcompound1);
        return nbttagcompound;
    }
    public void save(ChunkPos pos, NBTTagCompound compound) {
        this.addChunkToPending(pos, compound);
    }
}
