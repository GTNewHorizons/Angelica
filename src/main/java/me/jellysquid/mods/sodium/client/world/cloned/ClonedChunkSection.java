package me.jellysquid.mods.sodium.client.world.cloned;

import com.gtnewhorizons.angelica.compat.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.mixins.interfaces.IExtendedBlockStorageExt;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ClonedChunkSection {
    public static final BlockState DEFAULT_BLOCK_STATE = new BlockState(Blocks.air, 0);
    private static final EnumSkyBlock[] LIGHT_TYPES = EnumSkyBlock.values();
    private static final ExtendedBlockStorage EMPTY_SECTION = new ExtendedBlockStorage(0, false);

    private final AtomicInteger referenceCount = new AtomicInteger(0);
    private final ClonedChunkSectionCache backingCache;

    private final Short2ObjectMap<TileEntity> tileEntities;

    private ExtendedBlockStorageExt data;
    private final World world;

    private ChunkSectionPos pos;

    @Getter
    private byte[] biomeData;

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

        IExtendedBlockStorageExt section = (IExtendedBlockStorageExt)getChunkSection(chunk, pos);

        if (section == null /*WorldChunk.EMPTY_SECTION*/ /*ChunkSection.isEmpty(section)*/) {
            section = (IExtendedBlockStorageExt)EMPTY_SECTION;
        }

        this.pos = pos;
        this.data = new ExtendedBlockStorageExt(section);

        this.biomeData = chunk.getBiomeArray();

        StructureBoundingBox box = new StructureBoundingBox(pos.getMinX(), pos.getMinY(), pos.getMinZ(), pos.getMaxX(), pos.getMaxY(), pos.getMaxZ());

        this.tileEntities.clear();

        for (Map.Entry<ChunkPosition, TileEntity> entry : chunk.chunkTileEntityMap.entrySet()) {
            BlockPos entityPos = new BlockPos(entry.getKey());

            if(box.isVecInside(entityPos.getX(), entityPos.getY(), entityPos.getZ())) {
                //this.blockEntities.put(BlockPos.asLong(entityPos.getX() & 15, entityPos.getY() & 15, entityPos.getZ() & 15), entry.getValue());
            	this.tileEntities.put(ChunkSectionPos.packLocal(entityPos), entry.getValue());
            }
        }
    }

    public BlockState getBlockState(int x, int y, int z) {
        final Block block = data.getBlockByExtId(x, y, z);
        if(block.isAir(null, 0, 0, 0)) /* dumb api */ {
            return DEFAULT_BLOCK_STATE;
        }
        return new BlockState(data.getBlockByExtId(x, y, z), data.getExtBlockMetadata(x, y, z));
    }
    public int getLightLevel(EnumSkyBlock type, int x, int y, int z) {
        if(type == EnumSkyBlock.Sky) {
            return data.hasSky ? data.getExtSkylightValue(x, y, z) : 0;
        }
        return data.getExtBlocklightValue(x, y, z);
    }

    public BiomeGenBase getBiomeForNoiseGen(int x, int y, int z) {
        int k = this.biomeData[x << 4 | z] & 255;
        return BiomeGenBase.getBiome(k);

    }

    public TileEntity getBlockEntity(int x, int y, int z) {
        return this.tileEntities.get(packLocal(x, y, z));
    }

    public ChunkSectionPos getPosition() {
        return this.pos;
    }

    public static boolean isOutOfBuildLimitVertically(int y) {
        return y < 0 || y >= 256;
    }

    private static ExtendedBlockStorage getChunkSection(Chunk chunk, ChunkSectionPos pos) {
        if (!isOutOfBuildLimitVertically(ChunkSectionPos.getBlockCoord(pos.y()))) {
            return chunk.getBlockStorageArray()[pos.y];
        }

        return null;
    }

    public void acquireReference() {
        this.referenceCount.incrementAndGet();
    }

    public boolean releaseReference() {
        return this.referenceCount.decrementAndGet() <= 0;
    }

    public long getLastUsedTimestamp() {
        return this.lastUsedTimestamp;
    }

    public void setLastUsedTimestamp(long timestamp) {
        this.lastUsedTimestamp = timestamp;
    }

    public ClonedChunkSectionCache getBackingCache() {
        return this.backingCache;
    }

    /**
     * @param x The local x-coordinate
     * @param y The local y-coordinate
     * @param z The local z-coordinate
     * @return An index which can be used to key entities or blocks within a chunk
     */
    private static short packLocal(int x, int y, int z) {
        return (short) (x << 8 | z << 4 | y);
    }
}
