package com.gtnewhorizons.angelica.rendering.celeritas.world;

import com.gtnewhorizons.angelica.api.IBlockAccessExtended;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.compat.mojang.CompatMathHelper;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ChunkRenderContext;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSectionCache;
import cpw.mods.fml.common.Optional;
import mega.fluidlogged.api.FLBlockAccess;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Takes a slice of world state (block states, biome and light data arrays) and copies the data for use in off-thread
 * operations. This allows chunk build tasks to see a consistent snapshot of chunk data at the exact moment the task was
 * created.
 *
 * World slices are not safe to use from multiple threads at once, but the data they contain is safe from modification
 * by the main client thread.
 *
 * Object pooling should be used to avoid huge allocations as this class contains many large arrays.
 */
@Optional.Interface(iface = "mega.fluidlogged.api.FLBlockAccess", modid = "fluidlogged")
public class WorldSlice implements IBlockAccessExtended, FLBlockAccess {

    private static final EnumSkyBlock[] LIGHT_TYPES = EnumSkyBlock.values();

    // The number of blocks on each axis in a section.
    private static final int SECTION_BLOCK_LENGTH = 16;

    // The number of blocks in a section.
    private static final int SECTION_BLOCK_COUNT = SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH;

    // The radius of blocks around the origin chunk that should be copied.
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;

    // The radius of chunks around the origin chunk that should be copied.
    private static final int NEIGHBOR_CHUNK_RADIUS = CompatMathHelper.roundUpToMultiple(NEIGHBOR_BLOCK_RADIUS, 16) >> 4;

    // The number of sections on each axis of this slice.
    private static final int SECTION_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // The size of the lookup tables used for mapping values to coordinate int pairs. The lookup table size is always
    // a power of two so that multiplications can be replaced with simple bit shifts in hot code paths.
    private static final int TABLE_LENGTH = CompatMathHelper.smallestEncompassingPowerOfTwo(SECTION_LENGTH);

    // The number of bits needed for each X/Y/Z component in a lookup table.
    private static final int TABLE_BITS = Integer.bitCount(TABLE_LENGTH - 1);

    // The array size for the section lookup table.
    private static final int SECTION_TABLE_ARRAY_SIZE = TABLE_LENGTH * TABLE_LENGTH * TABLE_LENGTH;

    // The world this slice has copied data from
    private final WorldClient world;

    // Local Section->Block table
    private final Block[][] blockArrays;
    private final Fluid[][] fluidArrays;
    private final int[][] metadataArrays;

    // Local Section->Light table
    private final NibbleArray[][] lightArrays;

    // Local section copies
    private ClonedChunkSection[] sections;

    // Biome data for each chunk section
    private BiomeGenBase[][] biomeData;

    // The starting point from which this slice captures blocks
    private int baseX, baseY, baseZ;

    private final int worldHeight;
    private final int[] defaultLightValues;

    // The chunk origin of this slice
    private ChunkSectionPos origin;
    private StructureBoundingBox volume;

    public static ChunkRenderContext prepare(World world, ChunkSectionPos origin, ClonedChunkSectionCache sectionCache) {
        final Chunk chunk = world.getChunkFromChunkCoords(origin.x, origin.z);
        final ExtendedBlockStorage section = chunk.getBlockStorageArray()[origin.y];

        // If the chunk section is absent or empty, terminate early
        if (section == null || section.isEmpty()) {
            return null;
        }

        final StructureBoundingBox volume = new StructureBoundingBox(
            origin.getMinX() - NEIGHBOR_BLOCK_RADIUS,
            origin.getMinY() - NEIGHBOR_BLOCK_RADIUS,
            origin.getMinZ() - NEIGHBOR_BLOCK_RADIUS,
            origin.getMaxX() + NEIGHBOR_BLOCK_RADIUS,
            origin.getMaxY() + NEIGHBOR_BLOCK_RADIUS,
            origin.getMaxZ() + NEIGHBOR_BLOCK_RADIUS
        );

        // The min/max bounds of the chunks copied by this slice
        final int minChunkX = origin.x - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkY = origin.y - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkZ = origin.z - NEIGHBOR_CHUNK_RADIUS;

        final int maxChunkX = origin.x + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkY = origin.y + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkZ = origin.z + NEIGHBOR_CHUNK_RADIUS;

        final ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_TABLE_ARRAY_SIZE];

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    sections[getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ)] =
                        sectionCache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        return new ChunkRenderContext(origin, sections, volume);
    }

    public WorldSlice(WorldClient world) {
        this.world = world;
        this.worldHeight = world.getHeight();
        this.defaultLightValues = new int[LIGHT_TYPES.length];
        this.defaultLightValues[EnumSkyBlock.Sky.ordinal()] = world.provider.hasNoSky ? 0 : EnumSkyBlock.Sky.defaultLightValue;
        this.defaultLightValues[EnumSkyBlock.Block.ordinal()] = EnumSkyBlock.Block.defaultLightValue;

        // Note: sections array is assigned in copyData() from ChunkRenderContext
        this.blockArrays = new Block[SECTION_TABLE_ARRAY_SIZE][];
        this.metadataArrays = new int[SECTION_TABLE_ARRAY_SIZE][];
        this.biomeData = new BiomeGenBase[SECTION_TABLE_ARRAY_SIZE][];
        this.lightArrays = new NibbleArray[SECTION_TABLE_ARRAY_SIZE][LIGHT_TYPES.length];

        if (ModStatus.isFluidLoggedLoaded) {
            this.fluidArrays = new Fluid[SECTION_TABLE_ARRAY_SIZE][];
        } else {
            this.fluidArrays = null;
        }

        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    final int i = getLocalSectionIndex(x, y, z);
                    this.blockArrays[i] = new Block[SECTION_BLOCK_COUNT];
                    Arrays.fill(this.blockArrays[i], Blocks.air);
                    this.metadataArrays[i] = new int[SECTION_BLOCK_COUNT];

                    if (ModStatus.isFluidLoggedLoaded && this.fluidArrays != null) {
                        this.fluidArrays[i] = new Fluid[SECTION_BLOCK_COUNT];
                    }
                }
            }
        }
    }

    public void copyData(ChunkRenderContext context) {
        this.origin = context.getOrigin();
        this.sections = context.getSections();
        this.volume = context.getVolume();

        this.baseX = (this.origin.x - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.baseY = (this.origin.y - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.baseZ = (this.origin.z - NEIGHBOR_CHUNK_RADIUS) << 4;
        Arrays.fill(this.biomeData, null);

        for (int x = 0; x < SECTION_LENGTH; x++) {
            for (int y = 0; y < SECTION_LENGTH; y++) {
                for (int z = 0; z < SECTION_LENGTH; z++) {
                    final int idx = getLocalSectionIndex(x, y, z);
                    final ClonedChunkSection section = this.sections[idx];

                    this.unpackBlockData(this.blockArrays[idx], this.metadataArrays[idx], section, context.getVolume());

                    if (ModStatus.isFluidLoggedLoaded && this.fluidArrays != null) {
                        this.unpackFluidLoggedData(this.fluidArrays[idx], section, context.getVolume());
                    }

                    this.biomeData[idx] = section.getBiomeData();

                    this.lightArrays[idx][EnumSkyBlock.Block.ordinal()] = section.getLightArray(EnumSkyBlock.Block);
                    this.lightArrays[idx][EnumSkyBlock.Sky.ordinal()] = section.getLightArray(EnumSkyBlock.Sky);
                }
            }
        }
    }

    public ChunkSectionPos getOrigin() {
        return this.origin;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) {
        if (y < 0 || y >= 256 || x < -30_000_000 || z < -30_000_000 || x >= 30_000_000 || z >= 30_000_000) {
            // skyBrightness = 15, blockBrightness = min
            return (15 << 20) | (min << 4);
        }

        final int skyBrightness = this.getSkyBlockTypeBrightness(EnumSkyBlock.Sky, x, y, z);
        int blockBrightness = this.getSkyBlockTypeBrightness(EnumSkyBlock.Block, x, y, z);

        if (blockBrightness < min) {
            blockBrightness = min;
        }

        if (DynamicLights.isEnabled() && !getBlock(x, y, z).isOpaqueCube()) {
            return DynamicLights.get().getLightmapWithDynamicLight(x, y, z, (skyBrightness << 20 | blockBrightness << 4));
        }

        return skyBrightness << 20 | blockBrightness << 4;
    }

    private int getSkyBlockTypeBrightness(EnumSkyBlock skyBlock, int x, int y, int z) {
        if (this.getBlock(x, y, z).getUseNeighborBrightness()) {
            int yp = this.getLightLevel(skyBlock, x, y + 1, z);
            final int xp = this.getLightLevel(skyBlock, x + 1, y, z);
            final int xm = this.getLightLevel(skyBlock, x - 1, y, z);
            final int zp = this.getLightLevel(skyBlock, x, y, z + 1);
            final int zm = this.getLightLevel(skyBlock, x, y, z - 1);

            if (xp > yp) yp = xp;
            if (xm > yp) yp = xm;
            if (zp > yp) yp = zp;
            if (zm > yp) yp = zm;

            return yp;
        }

        return this.getLightLevel(skyBlock, x, y, z);
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int directionIn) {
        return this.getBlock(x, y, z).isProvidingStrongPower(this, x, y, z, directionIn);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return this.getBlock(x, y, z).isAir(this, x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        if (!blockBoxContains(this.volume, x, volume.minY, z)) {
            return BiomeGenBase.plains;
        }

        final int relX = x - this.baseX;
        final int relZ = z - this.baseZ;

        final BiomeGenBase biome = this.biomeData[getLocalSectionIndex(relX >> 4, 0, relZ >> 4)]
            [(x & 15) | (z & 15) << 4];
        return biome == null ? BiomeGenBase.plains : biome;
    }

    @Override
    public int getHeight() {
        return this.worldHeight;
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
        return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return Blocks.air;
        }

        final int relX = x - this.baseX;
        final int relY = y - this.baseY;
        final int relZ = z - this.baseZ;
        return this.blockArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)][getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];
    }

    public Block getBlockRelative(int x, int y, int z) {
        return this.blockArrays[getLocalSectionIndex(x >> 4, y >> 4, z >> 4)][getLocalBlockIndex(x & 15, y & 15, z & 15)];
    }

    public int getBlockMetadataRelative(int x, int y, int z) {
        return this.metadataArrays[getLocalSectionIndex(x >> 4, y >> 4, z >> 4)][getLocalBlockIndex(x & 15, y & 15, z & 15)];
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return 0;
        }
        final int relX = x - this.baseX;
        final int relY = y - this.baseY;
        final int relZ = z - this.baseZ;

        return this.metadataArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)][getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return null;
        }
        final int relX = x - this.baseX;
        final int relY = y - this.baseY;
        final int relZ = z - this.baseZ;

        return this.sections[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)].getBlockEntity(relX & 15, relY & 15, relZ & 15);
    }

    public int getLightLevel(EnumSkyBlock type, int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z)) {
            return 0;
        }
        y = MathHelper.clamp_int(y, 0, 255);

        final int relX = x - this.baseX;
        final int relY = y - this.baseY;
        final int relZ = z - this.baseZ;

        final NibbleArray lightArray = this.lightArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)][type.ordinal()];
        if (lightArray == null) {
            return this.defaultLightValues[type.ordinal()];
        }

        return lightArray.get(relX & 15, relY & 15, relZ & 15);
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    // FluidLogged API support

    public @Nullable Fluid fl$getFluid(int x, int y, int z) {
        if (!blockBoxContains(this.volume, x, y, z) || this.fluidArrays == null) {
            return null;
        }

        final int relX = x - this.baseX;
        final int relY = y - this.baseY;
        final int relZ = z - this.baseZ;
        return this.fluidArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)][getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];
    }

    public void fl$setFluid(int x, int y, int z, @Nullable Fluid fluid) {
        throw new UnsupportedOperationException("Cannot set fluids in a world slice. WorldSlice is read-only");
    }

    // Internal helper methods

    private void unpackBlockData(Block[] blocks, int[] metas, ClonedChunkSection section, StructureBoundingBox box) {
        if (this.origin.equals(section.getPosition())) {
            this.unpackBlockDataZ(blocks, metas, section);
        } else {
            this.unpackBlockDataR(blocks, metas, section, box);
        }
    }

    private void unpackFluidLoggedData(Fluid[] fluids, ClonedChunkSection section, StructureBoundingBox box) {
        if (this.origin.equals(section.getPosition())) {
            this.unpackFluidLoggedDataZ(fluids, section);
        } else {
            this.unpackFluidLoggedDataR(fluids, section, box);
        }
    }

    private void unpackBlockDataZ(Block[] blocks, int[] metas, ClonedChunkSection section) {
        final ChunkSectionPos pos = section.getPosition();

        for (int y = pos.getMinY(); y <= pos.getMaxY(); y++) {
            for (int z = pos.getMinZ(); z <= pos.getMaxZ(); z++) {
                for (int x = pos.getMinX(); x <= pos.getMaxX(); x++) {
                    final int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);
                    blocks[blockIdx] = section.getBlock(x & 15, y & 15, z & 15);
                    metas[blockIdx] = section.getBlockMetadata(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    private void unpackBlockDataR(Block[] blocks, int[] metas, ClonedChunkSection section, StructureBoundingBox box) {
        final ChunkSectionPos pos = section.getPosition();

        final int minBlockX = Math.max(box.minX, pos.getMinX());
        final int maxBlockX = Math.min(box.maxX, pos.getMaxX());
        final int minBlockY = Math.max(box.minY, pos.getMinY());
        final int maxBlockY = Math.min(box.maxY, pos.getMaxY());
        final int minBlockZ = Math.max(box.minZ, pos.getMinZ());
        final int maxBlockZ = Math.min(box.maxZ, pos.getMaxZ());

        for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    final int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);
                    blocks[blockIdx] = section.getBlock(x & 15, y & 15, z & 15);
                    metas[blockIdx] = section.getBlockMetadata(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    private void unpackFluidLoggedDataZ(Fluid[] fluids, ClonedChunkSection section) {
        final ChunkSectionPos pos = section.getPosition();

        for (int y = pos.getMinY(); y <= pos.getMaxY(); y++) {
            for (int z = pos.getMinZ(); z <= pos.getMaxZ(); z++) {
                for (int x = pos.getMinX(); x <= pos.getMaxX(); x++) {
                    final int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);
                    fluids[blockIdx] = section.getFluid(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    private void unpackFluidLoggedDataR(Fluid[] fluids, ClonedChunkSection section, StructureBoundingBox box) {
        final ChunkSectionPos pos = section.getPosition();

        final int minBlockX = Math.max(box.minX, pos.getMinX());
        final int maxBlockX = Math.min(box.maxX, pos.getMaxX());
        final int minBlockY = Math.max(box.minY, pos.getMinY());
        final int maxBlockY = Math.min(box.maxY, pos.getMaxY());
        final int minBlockZ = Math.max(box.minZ, pos.getMinZ());
        final int maxBlockZ = Math.min(box.maxZ, pos.getMaxZ());

        for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                for (int x = minBlockX; x <= maxBlockX; x++) {
                    final int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);
                    fluids[blockIdx] = section.getFluid(x & 15, y & 15, z & 15);
                }
            }
        }
    }

    public static int getLocalBlockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    public static int getLocalSectionIndex(int x, int y, int z) {
        return y << TABLE_BITS << TABLE_BITS | z << TABLE_BITS | x;
    }

    private static boolean blockBoxContains(StructureBoundingBox box, int x, int y, int z) {
        return x >= box.minX && x <= box.maxX &&
               y >= box.minY && y <= box.maxY &&
               z >= box.minZ && z <= box.maxZ;
    }
}
