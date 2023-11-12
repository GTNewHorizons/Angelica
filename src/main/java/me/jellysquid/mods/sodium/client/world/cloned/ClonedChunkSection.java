package me.jellysquid.mods.sodium.client.world.cloned;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.compat.mojang.LightType;
import com.gtnewhorizons.angelica.compat.mojang.PackedIntegerArray;
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

    private final Short2ObjectMap<TileEntity> blockEntities;
    private final NibbleArray[] lightDataArrays;
    private final World world;

    private ChunkSectionPos pos;
    // TODO: Sodium - BlockState - Replace with 1.7.10 equivalents from ExtendedBlockStorage
    // Likely some combination of blockMetadataArray, blocklightArray, and skylightArray
//    private PackedIntegerArray blockStateData;
//    private ClonedPalette<BlockState> blockStatePalette;

    private byte[] biomeData;

    private long lastUsedTimestamp = Long.MAX_VALUE;

    ClonedChunkSection(ClonedChunkSectionCache backingCache, World world) {
        this.backingCache = backingCache;
        this.world = world;
        this.blockEntities = new Short2ObjectOpenHashMap<>();
        this.lightDataArrays = new NibbleArray[LIGHT_TYPES.length];
    }

    public void init(ChunkSectionPos pos) {
        Chunk chunk = world.getChunkFromChunkCoords(pos.x, pos.z);

        if (chunk == null) {
            throw new RuntimeException("Couldn't retrieve chunk at " + pos.toChunkPos());
        }

        ExtendedBlockStorage section = getChunkSection(chunk, pos);

        if (section == null /*WorldChunk.EMPTY_SECTION*/ /*ChunkSection.isEmpty(section)*/) {
            section = EMPTY_SECTION;
        }

        this.pos = pos;
//        PalettedContainerExtended<BlockState> container = PalettedContainerExtended.cast(new PalettedContainer<>()/*section.getContainer()*/);

//        this.blockStateData = copyBlockData(container);
//        this.blockStatePalette = copyPalette(container);

        for (LightType type : LIGHT_TYPES) {
            // TODO: Sodium - Lighting
            this.lightDataArrays[type.ordinal()] = null; /*world.getLightingProvider()
                    .get(type)
                    .getLightSection(pos);*/
        }

        this.biomeData = chunk.getBiomeArray();

        StructureBoundingBox box = new StructureBoundingBox(pos.getMinX(), pos.getMinY(), pos.getMinZ(), pos.getMaxX(), pos.getMaxY(), pos.getMaxZ());

        this.blockEntities.clear();

        for (Map.Entry<ChunkPosition, TileEntity> entry : chunk.chunkTileEntityMap.entrySet()) {
            BlockPos entityPos = new BlockPos(entry.getKey());

//            if (box.contains(entityPos)) {
            if(box.isVecInside(entityPos.getX(), entityPos.getY(), entityPos.getZ())) {
                //this.blockEntities.put(BlockPos.asLong(entityPos.getX() & 15, entityPos.getY() & 15, entityPos.getZ() & 15), entry.getValue());
            	this.blockEntities.put(ChunkSectionPos.packLocal(entityPos), entry.getValue());
            }
        }
    }

    public BlockState getBlockState(int x, int y, int z) {
        return null;
//        return this.blockStatePalette.get(this.blockStateData.get(y << 8 | z << 4 | x));
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
        return this.blockEntities.get(packLocal(x, y, z));
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

//    private static ClonedPalette<BlockState> copyPalette(PalettedContainerExtended<BlockState> container) {
//        Palette<BlockState> palette = container.getPalette();
//
//        if (palette instanceof IdListPalette) {
//            // TODO: Sodium
//            return new ClonedPaletteFallback<>(null/*Block.STATE_IDS*/);
//        }
//
//        BlockState[] array = new BlockState[1 << container.getPaletteSize()];
//
//        for (int i = 0; i < array.length; i++) {
//            array[i] = palette.getByIndex(i);
//
//            if (array[i] == null) {
//                break;
//            }
//        }
//
//        return new ClonedPalleteArray<>(array, container.getDefaultValue());
//    }

//    private static PackedIntegerArray copyBlockData(PalettedContainerExtended<BlockState> container) {
//        PackedIntegerArray array = container.getDataArray();
//        long[] storage = array.getStorage();
//
//        return new PackedIntegerArray(container.getPaletteSize(), array.getSize(), storage.clone());
//    }

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
