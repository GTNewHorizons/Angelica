package com.gtnewhorizons.angelica.rendering.celeritas.world.cloned;

import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.api.SectionLightData;
import com.gtnewhorizons.angelica.compat.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.lotr.LOTRCompat;
import com.gtnewhorizons.angelica.compat.cubicchunks.CubicChunksAPI;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.mixins.interfaces.IChunkTileEntityMapHolder;
import com.gtnewhorizons.angelica.utils.ConcurrentTileEntityMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import mega.fluidlogged.internal.mixin.hook.FLSubChunk;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fluids.Fluid;

import java.util.Map;

public class ClonedChunkSection {

    private static final ExtendedBlockStorage EMPTY_SECTION = new ExtendedBlockStorage(0, false);
    private static final int SECTION_BLOCK_LENGTH = 16;
    private static final int BIOME_DATA_LENGTH = SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH;

    private final ClonedChunkSectionCache backingCache;
    private final World world;

    private ChunkSectionPos pos;
    private ExtendedBlockStorageExt data;
    private BiomeGenBase[] biomeData;
    private SectionLightData sectionLightData;
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

        ExtendedBlockStorage section;

        if (ModStatus.isCubicChunksLoaded) {
            section = CubicChunksAPI.getCubeStorage(world, pos.x, pos.y, pos.z);
        } else {
            section = getChunkSection(chunk, pos);
        }

        if (section == null) {
            section = EMPTY_SECTION;
        }

        this.pos = pos;
        this.data = new ExtendedBlockStorageExt(chunk, section);

        this.biomeData = new BiomeGenBase[BIOME_DATA_LENGTH];

        copyBlockEntities(chunk, pos);

        fillBiomeData(chunk);

        this.sectionLightData = BlockLightProvider.getInstance().prepareSectionData(chunk, pos.y);
    }

    private void fillBiomeData(Chunk chunk) {
        if (LOTRCompat.isLotrProvider(world.provider)) {
            for (int z = pos.getMinZ(); z <= pos.getMaxZ(); z++) {
                for (int x = pos.getMinX(); x <= pos.getMaxX(); x++) {
                    this.biomeData[((z & 15) << 4) | (x & 15)] = world.getBiomeGenForCoords(x, z);
                }
            }
        } else {
            final WorldChunkManager wcm = world.getWorldChunkManager();
            for (int lz = 0; lz < SECTION_BLOCK_LENGTH; lz++) {
                for (int lx = 0; lx < SECTION_BLOCK_LENGTH; lx++) {
                    this.biomeData[(lz << 4) | lx] = chunk.getBiomeGenForWorldCoords(lx, lz, wcm);
                }
            }
        }
    }

    private void copyBlockEntities(Chunk chunk, ChunkSectionPos pos) {
        this.tileEntities.clear();

        final ConcurrentTileEntityMap map = ((IChunkTileEntityMapHolder) chunk).angelica$getConcurrentTEMap();

        map.readLock();
        try {
            map.forEachInYRange(pos.getMinY(), pos.getMaxY(), entry -> {
                final ChunkPosition tePos = entry.getKey();
                this.tileEntities.put(
                    ChunkSectionPos.packLocal(tePos.chunkPosX & 15, tePos.chunkPosY & 15, tePos.chunkPosZ & 15),
                    entry.getValue()
                );
            });
        } finally {
            map.readUnlock();
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

    public SectionLightData getSectionLightData() {
        return this.sectionLightData;
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

    public static boolean isOutOfBuildLimitVertically(World world, int y) {
        if (ModStatus.isCubicChunksLoaded) {
            return y < CubicChunksAPI.getMinHeight(world) || y >= CubicChunksAPI.getMaxHeight(world);
        }
        return y < 0 || y >= 256;
    }

    private ExtendedBlockStorage getChunkSection(Chunk chunk, ChunkSectionPos pos) {
        if (!isOutOfBuildLimitVertically(this.world, ChunkSectionPos.getBlockCoord(pos.y))) {
            return chunk.getBlockStorageArray()[pos.y];
        }
        return null;
    }
}
