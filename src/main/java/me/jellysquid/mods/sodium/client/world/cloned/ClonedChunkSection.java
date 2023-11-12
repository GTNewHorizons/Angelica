package me.jellysquid.mods.sodium.client.world.cloned;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.compat.mojang.LightType;
import com.gtnewhorizons.angelica.compat.mojang.PackedIntegerArray;
import com.gtnewhorizons.angelica.mixins.interfaces.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.mixins.interfaces.ExtendedNibbleArray;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ClonedChunkSection {
    private static final LightType[] LIGHT_TYPES = LightType.values();
    private static final ExtendedBlockStorage EMPTY_SECTION = new ExtendedBlockStorage(0, false);

    private final AtomicInteger referenceCount = new AtomicInteger(0);
    private final ClonedChunkSectionCache backingCache;
    private boolean hasSky = false;
    private final Short2ObjectMap<TileEntity> tileEntities;
    private final NibbleArray[] lightDataArrays;
    /** Contains the least significant 8 bits of each block ID belonging to this block storage's parent Chunk. */
    private byte[] blockLSBArray;
    /** Contains the most significant 4 bits of each block ID belonging to this block storage's parent Chunk. */
    private NibbleArray blockMSBArray;
    /** Stores the metadata associated with blocks in this ExtendedBlockStorage. */
    private NibbleArray blockMetadataArray;
    /** The NibbleArray containing a block of Block-light data. */
    private NibbleArray blocklightArray;
    /** The NibbleArray containing a block of Sky-light data. */
    private NibbleArray skylightArray;

    private final World world;

    private ChunkSectionPos pos;

    private byte[] biomeData;

    private long lastUsedTimestamp = Long.MAX_VALUE;

    ClonedChunkSection(ClonedChunkSectionCache backingCache, World world) {
        this.backingCache = backingCache;
        this.world = world;
        this.tileEntities = new Short2ObjectOpenHashMap<>();
        this.lightDataArrays = new NibbleArray[LIGHT_TYPES.length];
        this.blockLSBArray = new byte[4096];
        this.blockMetadataArray = new NibbleArray(this.blockLSBArray.length, 4);
        this.blocklightArray = new NibbleArray(this.blockLSBArray.length, 4);
        this.hasSky = !world.provider.hasNoSky;
        if (hasSky) {
            this.skylightArray = new NibbleArray(this.blockLSBArray.length, 4);
        }
    }

    public void init(ChunkSectionPos pos) {
        Chunk chunk = world.getChunkFromChunkCoords(pos.x, pos.z);

        if (chunk == null) {
            throw new RuntimeException("Couldn't retrieve chunk at " + pos.toChunkPos());
        }

        ExtendedBlockStorageExt section = (ExtendedBlockStorageExt)getChunkSection(chunk, pos);

        if (section == null /*WorldChunk.EMPTY_SECTION*/ /*ChunkSection.isEmpty(section)*/) {
            section = (ExtendedBlockStorageExt)EMPTY_SECTION;
        }

        this.pos = pos;

        System.arraycopy(section.getBlockLSBArray(), 0, this.blockLSBArray, 0, this.blockLSBArray.length);
        if(section.getBlockMSBArray() != null) {
            this.blockMSBArray = new NibbleArray(this.blockLSBArray.length, 4);
            copyNibbleArray((ExtendedNibbleArray) section.getBlockMSBArray(), (ExtendedNibbleArray) this.blockMSBArray);
        }
        copyNibbleArray((ExtendedNibbleArray) section.getBlockMetadataArray(), (ExtendedNibbleArray)this.blockMetadataArray);
        copyNibbleArray((ExtendedNibbleArray) section.getBlocklightArray(), (ExtendedNibbleArray)this.blocklightArray);
        if(section.getSkylightArray() != null) {
            if(this.skylightArray == null) {
                hasSky = true;
                this.skylightArray = new NibbleArray(this.blockLSBArray.length, 4);
            }
            copyNibbleArray((ExtendedNibbleArray) section.getSkylightArray(), (ExtendedNibbleArray) this.skylightArray);
        }

        for (LightType type : LIGHT_TYPES) {
            // TODO: Sodium - Lighting
            this.lightDataArrays[type.ordinal()] = null; /*world.getLightingProvider()
                    .get(type)
                    .getLightSection(pos);*/
        }

        this.biomeData = chunk.getBiomeArray();

        StructureBoundingBox box = new StructureBoundingBox(pos.getMinX(), pos.getMinY(), pos.getMinZ(), pos.getMaxX(), pos.getMaxY(), pos.getMaxZ());

        this.tileEntities.clear();

        for (Map.Entry<ChunkPosition, TileEntity> entry : chunk.chunkTileEntityMap.entrySet()) {
            BlockPos entityPos = new BlockPos(entry.getKey());

//            if (box.contains(entityPos)) {
            if(box.isVecInside(entityPos.getX(), entityPos.getY(), entityPos.getZ())) {
                //this.blockEntities.put(BlockPos.asLong(entityPos.getX() & 15, entityPos.getY() & 15, entityPos.getZ() & 15), entry.getValue());
            	this.tileEntities.put(ChunkSectionPos.packLocal(entityPos), entry.getValue());
            }
        }
    }

    private static void copyNibbleArray(ExtendedNibbleArray srcArray, ExtendedNibbleArray dstArray) {
        if(srcArray == null || dstArray == null) {
            throw new RuntimeException("NibbleArray is null src: " + (srcArray==null) + " dst: " + (dstArray==null));
        }
        final byte[] data = srcArray.getData();
        System.arraycopy(data, 0, dstArray.getData(), 0, data.length);
    }


    public int getLightLevel(LightType type, int x, int y, int z) {
        NibbleArray array = this.lightDataArrays[type.ordinal()];

        if (array != null) {
            return array.get(x, y, z);
        }

        return 0;
    }

    public BiomeGenBase getBiomeForNoiseGen(int x, int y, int z) {
        int k = this.biomeData[x << 4 | z] & 255;
        return BiomeGenBase.getBiome(k);

    }

    public TileEntity getBlockEntity(int x, int y, int z) {
        return this.tileEntities.get(packLocal(x, y, z));
    }

    public PackedIntegerArray getBlockData() {
        return null;
//        return this.blockStateData;
    }

//    public ClonedPalette<BlockState> getBlockPalette() {
//        return this.blockStatePalette;
//    }

    public ChunkSectionPos getPosition() {
        return this.pos;
    }

    public static boolean isOutOfBuildLimitVertically(int y) {
        return y < 0 || y >= 256;
    }

    private static ExtendedBlockStorage getChunkSection(Chunk chunk, ChunkSectionPos pos) {
        ExtendedBlockStorage section = null;

        if (!isOutOfBuildLimitVertically(ChunkSectionPos.getBlockCoord(pos.y()))) {
            section = chunk.getBlockStorageArray()[pos.y];
        }

        return section;
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
