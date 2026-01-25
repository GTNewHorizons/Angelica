package com.gtnewhorizons.angelica.rendering.celeritas.world.cloned;

import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;
import com.gtnewhorizons.angelica.compat.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import mega.fluidlogged.internal.mixin.hook.FLSubChunk;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fluids.Fluid;

public class ClonedChunkSection {

    private static final ExtendedBlockStorage EMPTY_SECTION = new ExtendedBlockStorage(0, false);

    private final ClonedChunkSectionCache backingCache;
    private final World world;

    private ChunkSectionPos pos;
    private ExtendedBlockStorageExt data;
    private BiomeGenBase[] biomeData;
    private final Short2ObjectOpenHashMap<TileEntity> tileEntities;

    private long lastUsedTimestamp = Long.MAX_VALUE;

    ClonedChunkSection(ClonedChunkSectionCache backingCache, World world) {
        this.backingCache = backingCache;
        this.world = world;
        this.tileEntities = new Short2ObjectOpenHashMap<>();
    }

    public void init(ChunkSectionPos pos) {
        final Chunk chunk = world.getChunkFromChunkCoords(pos.x, pos.z);

        if (chunk == null) {
            throw new RuntimeException("Couldn't retrieve chunk at " + pos.toChunkPos());
        }

        ExtendedBlockStorage section = getChunkSection(chunk, pos);

        if (section == null) {
            section = EMPTY_SECTION;
        }

        this.pos = pos;
        this.data = new ExtendedBlockStorageExt(chunk, section);

        final int bArrLength;
        if (ModStatus.isEIDBiomeLoaded) {
            bArrLength = ((ChunkBiomeHook) chunk).getBiomeShortArray().length;
        } else {
            bArrLength = chunk.getBiomeArray().length;
        }
        this.biomeData = new BiomeGenBase[bArrLength];

        copyBlockEntities(chunk, pos);

        // Fill biome data
        for (int z = pos.getMinZ(); z <= pos.getMaxZ(); z++) {
            for (int x = pos.getMinX(); x <= pos.getMaxX(); x++) {
                this.biomeData[(x & 15) | ((z & 15) << 4)] = world.getBiomeGenForCoords(x, z);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void copyBlockEntities(Chunk chunk, ChunkSectionPos pos) {
        this.tileEntities.clear();

        final Object2ObjectMap<ChunkPosition, TileEntity> chunkTEMap = (Object2ObjectMap<ChunkPosition, TileEntity>) chunk.chunkTileEntityMap;

        if (chunkTEMap.isEmpty()) {
            this.tileEntities.trim();
            return;
        }

        final int minY = pos.getMinY();
        final int maxY = pos.getMaxY();

        for (Object2ObjectMap.Entry<ChunkPosition, TileEntity> entry : Object2ObjectMaps.fastIterable(chunkTEMap)) {
            final ChunkPosition tePos = entry.getKey();
            if (tePos.chunkPosY < minY || tePos.chunkPosY > maxY) continue;

            final TileEntity te = entry.getValue();
            if (te != null && !te.isInvalid()) {
                this.tileEntities.put(ChunkSectionPos.packLocal(tePos.chunkPosX & 15, tePos.chunkPosY & 15, tePos.chunkPosZ & 15), te);
            }
        }

        this.tileEntities.trim();
    }

    public Block getBlock(int x, int y, int z) {
        return data.getBlockByExtId(x, y, z);
    }

    public int getBlockMetadata(int x, int y, int z) {
        return data.getExtBlockMetadata(x, y, z);
    }

    public Fluid getFluid(int x, int y, int z) {
        if (ModStatus.isFluidLoggedLoaded) {
            return ((FLSubChunk) data).fl$getFluid(x, y, z);
        }
        return null;
    }

    public NibbleArray getLightArray(EnumSkyBlock type) {
        if (type == EnumSkyBlock.Sky) {
            return (!world.provider.hasNoSky && data.hasSky) ? data.getSkylightArray() : null;
        }
        return data.getBlocklightArray();
    }

    public BiomeGenBase[] getBiomeData() {
        return this.biomeData;
    }

    public BiomeGenBase getBiomeForNoiseGen(int x, int z) {
        return this.biomeData[(x & 15) | ((z & 15) << 4)];
    }

    public TileEntity getBlockEntity(int x, int y, int z) {
        return this.tileEntities.get(ChunkSectionPos.packLocal(x, y, z));
    }

    public ChunkSectionPos getPosition() {
        return this.pos;
    }

    public ExtendedBlockStorageExt getData() {
        return this.data;
    }

    public ClonedChunkSectionCache getBackingCache() {
        return this.backingCache;
    }

    public long getLastUsedTimestamp() {
        return this.lastUsedTimestamp;
    }

    public void setLastUsedTimestamp(long timestamp) {
        this.lastUsedTimestamp = timestamp;
    }

    public static boolean isOutOfBuildLimitVertically(int y) {
        return y < 0 || y >= 256;
    }

    private static ExtendedBlockStorage getChunkSection(Chunk chunk, ChunkSectionPos pos) {
        if (!isOutOfBuildLimitVertically(ChunkSectionPos.getBlockCoord(pos.y))) {
            return chunk.getBlockStorageArray()[pos.y];
        }
        return null;
    }
}
