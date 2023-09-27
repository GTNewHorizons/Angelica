package org.embeddedt.archaicfix.lighting.world.lighting;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.embeddedt.archaicfix.ArchaicLogger;
import org.embeddedt.archaicfix.lighting.api.IChunkLighting;
import org.embeddedt.archaicfix.lighting.api.IChunkLightingData;
import org.embeddedt.archaicfix.lighting.api.ILightingEngine;
import org.embeddedt.archaicfix.lighting.api.ILightingEngineProvider;

@SuppressWarnings("unused")
public class LightingHooks {
    private static final EnumSkyBlock[] ENUM_SKY_BLOCK_VALUES = EnumSkyBlock.values();

    private static final AxisDirection[] ENUM_AXIS_DIRECTION_VALUES = AxisDirection.values();

    public static final EnumFacing[] HORIZONTAL_FACINGS = new EnumFacing[] { EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST };

    private static final int FLAG_COUNT = 32; //2 light types * 4 directions * 2 halves * (inwards + outwards)

    public static void relightSkylightColumn(final World world, final Chunk chunk, final int x, final int z, final int height1, final int height2) {
        final int yMin = Math.min(height1, height2);
        final int yMax = Math.max(height1, height2) - 1;

        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

        final int xBase = (chunk.xPosition << 4) + x;
        final int zBase = (chunk.zPosition << 4) + z;

        scheduleRelightChecksForColumn(world, EnumSkyBlock.Sky, xBase, zBase, yMin, yMax);

        if (sections[yMin >> 4] == null && yMin > 0) {
            world.updateLightByType(EnumSkyBlock.Sky, xBase, yMin - 1, zBase);
        }

        short emptySections = 0;

        for (int sec = yMax >> 4; sec >= yMin >> 4; --sec) {
            if (sections[sec] == null) {
                emptySections |= 1 << sec;
            }
        }

        if (emptySections != 0) {
            for (final EnumFacing dir : HORIZONTAL_FACINGS) {
                final int xOffset = dir.getFrontOffsetX();
                final int zOffset = dir.getFrontOffsetZ();

                final boolean neighborColumnExists =
                        (((x + xOffset) | (z + zOffset)) & 16) == 0
                                //Checks whether the position is at the specified border (the 16 bit is set for both 15+1 and 0-1)
                                || LightingEngineHelpers.getLoadedChunk(world.getChunkProvider(), chunk.xPosition + xOffset, chunk.zPosition + zOffset) != null;

                if (neighborColumnExists) {
                    for (int sec = yMax >> 4; sec >= yMin >> 4; --sec) {
                        if ((emptySections & (1 << sec)) != 0) {
                            scheduleRelightChecksForColumn(world, EnumSkyBlock.Sky, xBase + xOffset, zBase + zOffset, sec << 4, (sec << 4) + 15);
                        }
                    }
                } else {
                    flagChunkBoundaryForUpdate(chunk, emptySections, EnumSkyBlock.Sky, dir, getAxisDirection(dir, x, z), EnumBoundaryFacing.OUT);
                }
            }
        }
    }

    public static void scheduleRelightChecksForArea(final World world, final EnumSkyBlock lightType, final int xMin, final int yMin, final int zMin,
                                                    final int xMax, final int yMax, final int zMax) {
        for (int x = xMin; x <= xMax; ++x) {
            for (int z = zMin; z <= zMax; ++z) {
                scheduleRelightChecksForColumn(world, lightType, x, z, yMin, yMax);
            }
        }
    }

    private static void scheduleRelightChecksForColumn(final World world, final EnumSkyBlock lightType, final int x, final int z, final int yMin, final int yMax) {
        for (int y = yMin; y <= yMax; ++y) {
            world.updateLightByType(lightType, x, y, z);
        }
    }

    public enum EnumBoundaryFacing {
        IN, OUT;

        public EnumBoundaryFacing getOpposite() {
            return this == IN ? OUT : IN;
        }
    }

    public static void flagSecBoundaryForUpdate(final Chunk chunk, final BlockPos pos, final EnumSkyBlock lightType, final EnumFacing dir,
                                                final EnumBoundaryFacing boundaryFacing) {
        flagChunkBoundaryForUpdate(chunk, (short) (1 << (pos.getY() >> 4)), lightType, dir, getAxisDirection(dir, pos.getX(), pos.getZ()), boundaryFacing);
    }

    public static void flagChunkBoundaryForUpdate(final Chunk chunk, final short sectionMask, final EnumSkyBlock lightType, final EnumFacing dir,
                                                  final AxisDirection axisDirection, final EnumBoundaryFacing boundaryFacing) {
        initNeighborLightChecks(chunk);
        ((IChunkLightingData) chunk).getNeighborLightChecks()[getFlagIndex(lightType, dir, axisDirection, boundaryFacing)] |= sectionMask;
        chunk.setChunkModified();
    }

    public static int getFlagIndex(final EnumSkyBlock lightType, final int xOffset, final int zOffset, final AxisDirection axisDirection,
                                   final EnumBoundaryFacing boundaryFacing) {
        return (lightType == EnumSkyBlock.Block ? 0 : 16) | ((xOffset + 1) << 2) | ((zOffset + 1) << 1) | (axisDirection.getOffset() + 1) | boundaryFacing
                .ordinal();
    }

    public static int getFlagIndex(final EnumSkyBlock lightType, final EnumFacing dir, final AxisDirection axisDirection,
                                   final EnumBoundaryFacing boundaryFacing) {
        return getFlagIndex(lightType, dir.getFrontOffsetX(), dir.getFrontOffsetZ(), axisDirection, boundaryFacing);
    }

    private static AxisDirection getAxisDirection(final EnumFacing dir, final int x, final int z) {
        return (((dir == EnumFacing.EAST || dir == EnumFacing.WEST) ? z : x) & 15) < 8 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE;
    }

    private static EnumFacing getOpposite(EnumFacing in) {
        return switch (in) {
            case NORTH -> EnumFacing.SOUTH;
            case SOUTH -> EnumFacing.NORTH;
            case EAST -> EnumFacing.WEST;
            case WEST -> EnumFacing.EAST;
            case DOWN -> EnumFacing.UP;
            case UP -> EnumFacing.DOWN;
            default -> throw new IllegalArgumentException();
        };
    }

    private static AxisDirection getAxisDirection(EnumFacing in) {
        switch (in) {
            case DOWN:
            case NORTH:
            case WEST:
                return AxisDirection.NEGATIVE;
            default:
                return AxisDirection.POSITIVE;
        }
    }

    public static void scheduleRelightChecksForChunkBoundaries(final World world, final Chunk chunk) {
        for (final EnumFacing dir : HORIZONTAL_FACINGS) {
            final int xOffset = dir.getFrontOffsetX();
            final int zOffset = dir.getFrontOffsetZ();

            final Chunk nChunk = LightingEngineHelpers.getLoadedChunk(world.getChunkProvider(), chunk.xPosition + xOffset, chunk.zPosition + zOffset);

            if(nChunk == null)
                continue;

            for (final EnumSkyBlock lightType : ENUM_SKY_BLOCK_VALUES) {
                for (final AxisDirection axisDir : ENUM_AXIS_DIRECTION_VALUES) {
                    //Merge flags upon loading of a chunk. This ensures that all flags are always already on the IN boundary below
                    mergeFlags(lightType, chunk, nChunk, dir, axisDir);
                    mergeFlags(lightType, nChunk, chunk, getOpposite(dir), axisDir);

                    //Check everything that might have been canceled due to this chunk not being loaded.
                    //Also, pass in chunks if already known
                    //The boundary to the neighbor chunk (both ways)
                    scheduleRelightChecksForBoundary(world, chunk, nChunk, null, lightType, xOffset, zOffset, axisDir);
                    scheduleRelightChecksForBoundary(world, nChunk, chunk, null, lightType, -xOffset, -zOffset, axisDir);
                    //The boundary to the diagonal neighbor (since the checks in that chunk were aborted if this chunk wasn't loaded, see scheduleRelightChecksForBoundary)
                    scheduleRelightChecksForBoundary(world, nChunk, null, chunk, lightType, (zOffset != 0 ? axisDir.getOffset() : 0),
                            (xOffset != 0 ? axisDir.getOffset() : 0), getAxisDirection(dir) == AxisDirection.POSITIVE ?
                                    AxisDirection.NEGATIVE :
                                    AxisDirection.POSITIVE);
                }
            }
        }
    }

    private static void mergeFlags(final EnumSkyBlock lightType, final Chunk inChunk, final Chunk outChunk, final EnumFacing dir,
                                   final AxisDirection axisDir) {
        IChunkLightingData outChunkLightingData = (IChunkLightingData) outChunk;

        if (outChunkLightingData.getNeighborLightChecks() == null) {
            return;
        }

        IChunkLightingData inChunkLightingData = (IChunkLightingData) inChunk;

        initNeighborLightChecks(inChunk);

        final int inIndex = getFlagIndex(lightType, dir, axisDir, EnumBoundaryFacing.IN);
        final int outIndex = getFlagIndex(lightType, getOpposite(dir), axisDir, EnumBoundaryFacing.OUT);

        inChunkLightingData.getNeighborLightChecks()[inIndex] |= outChunkLightingData.getNeighborLightChecks()[outIndex];
        //no need to call Chunk.setModified() since checks are not deleted from outChunk
    }

    private static void scheduleRelightChecksForBoundary(final World world, final Chunk chunk, Chunk nChunk, Chunk sChunk, final EnumSkyBlock lightType,
                                                         final int xOffset, final int zOffset, final AxisDirection axisDir) {
        IChunkLightingData chunkLightingData = (IChunkLightingData) chunk;

        if (chunkLightingData.getNeighborLightChecks() == null) {
            return;
        }

        final int flagIndex = getFlagIndex(lightType, xOffset, zOffset, axisDir, EnumBoundaryFacing.IN); //OUT checks from neighbor are already merged

        final int flags = chunkLightingData.getNeighborLightChecks()[flagIndex];

        if (flags == 0) {
            return;
        }

        if (nChunk == null) {
            nChunk = LightingEngineHelpers.getLoadedChunk(world.getChunkProvider(),chunk.xPosition + xOffset, chunk.zPosition + zOffset);
            if(nChunk == null)
                return;
        }

        if (sChunk == null) {
            int theX = chunk.xPosition + (zOffset != 0 ? axisDir.getOffset() : 0);
            int theZ = chunk.zPosition + (xOffset != 0 ? axisDir.getOffset() : 0);

            sChunk = LightingEngineHelpers.getLoadedChunk(world.getChunkProvider(), theX, theZ);
            if(sChunk == null)
                return;
        }

        final int reverseIndex = getFlagIndex(lightType, -xOffset, -zOffset, axisDir, EnumBoundaryFacing.OUT);

        chunkLightingData.getNeighborLightChecks()[flagIndex] = 0;

        IChunkLightingData nChunkLightingData = (IChunkLightingData) nChunk;

        if (nChunkLightingData.getNeighborLightChecks() != null) {
            nChunkLightingData.getNeighborLightChecks()[reverseIndex] = 0; //Clear only now that it's clear that the checks are processed
        }

        chunk.setChunkModified();
        nChunk.setChunkModified();

        //Get the area to check
        //Start in the corner...
        int xMin = chunk.xPosition << 4;
        int zMin = chunk.zPosition << 4;

        //move to other side of chunk if the direction is positive
        if ((xOffset | zOffset) > 0) {
            xMin += 15 * xOffset;
            zMin += 15 * zOffset;
        }

        //shift to other half if necessary (shift perpendicular to dir)
        if (axisDir == AxisDirection.POSITIVE) {
            xMin += 8 * (zOffset & 1); //x & 1 is same as abs(x) for x=-1,0,1
            zMin += 8 * (xOffset & 1);
        }

        //get maximal values (shift perpendicular to dir)
        final int xMax = xMin + 7 * (zOffset & 1);
        final int zMax = zMin + 7 * (xOffset & 1);

        for (int y = 0; y < 16; ++y) {
            if ((flags & (1 << y)) != 0) {
                scheduleRelightChecksForArea(world, lightType, xMin, y << 4, zMin, xMax, (y << 4) + 15, zMax);
            }
        }
    }

    public static void initNeighborLightChecks(final Chunk chunk) {
        IChunkLightingData lightingData = (IChunkLightingData) chunk;

        if (lightingData.getNeighborLightChecks() == null) {
            lightingData.setNeighborLightChecks(new short[FLAG_COUNT]);
        }
    }

    public static final String neighborLightChecksKey = "NeighborLightChecks";

    public static void writeNeighborLightChecksToNBT(final Chunk chunk, final NBTTagCompound nbt) {
        short[] neighborLightChecks = ((IChunkLightingData) chunk).getNeighborLightChecks();

        if (neighborLightChecks == null) {
            return;
        }

        boolean empty = true;

        final NBTTagList list = new NBTTagList();

        for (final short flags : neighborLightChecks) {
            list.appendTag(new NBTTagShort(flags));

            if (flags != 0) {
                empty = false;
            }
        }

        if (!empty) {
            nbt.setTag(neighborLightChecksKey, list);
        }
    }

    public static void readNeighborLightChecksFromNBT(final Chunk chunk, final NBTTagCompound nbt) {
        if (nbt.hasKey(neighborLightChecksKey, 9)) {
            final NBTTagList list = nbt.getTagList(neighborLightChecksKey, 2);

            if (list.tagCount() == FLAG_COUNT) {
                initNeighborLightChecks(chunk);

                short[] neighborLightChecks = ((IChunkLightingData) chunk).getNeighborLightChecks();

                for (int i = 0; i < FLAG_COUNT; ++i) {
                    neighborLightChecks[i] = ((NBTTagShort) list.tagList.get(i)).func_150289_e();
                }
            } else {
                ArchaicLogger.LOGGER.warn("Chunk field {} had invalid length, ignoring it (chunk coordinates: {} {})", neighborLightChecksKey, chunk.xPosition, chunk.zPosition);
            }
        }
    }

    public static void initChunkLighting(final Chunk chunk, final World world) {
        final int xBase = chunk.xPosition << 4;
        final int zBase = chunk.zPosition << 4;

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(xBase, 0, zBase);

        if (world.checkChunksExist(xBase - 16, 0, zBase - 16, xBase + 31, 255, zBase + 31)) {
            final ExtendedBlockStorage[] extendedBlockStorage = chunk.getBlockStorageArray();

            for (int j = 0; j < extendedBlockStorage.length; ++j) {
                final ExtendedBlockStorage storage = extendedBlockStorage[j];

                if (storage == null) {
                    continue;
                }

                int yBase = j * 16;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            Block block = storage.getBlockByExtId(x, y, z);
                            if(block != Blocks.air) {
                                pos.setPos(xBase + x, yBase + y, zBase + z);
                                int light = LightingEngineHelpers.getLightValueForState(block, world, pos.getX(), pos.getY(), pos.getZ());

                                if (light > 0) {
                                    world.updateLightByType(EnumSkyBlock.Block, pos.getX(), pos.getY(), pos.getZ());
                                }
                            }
                        }
                    }
                }
            }

            if (!world.provider.hasNoSky) {
                ((IChunkLightingData) chunk).setSkylightUpdatedPublic();
            }

            ((IChunkLightingData) chunk).setLightInitialized(true);
        }
    }

    public static void checkChunkLighting(final Chunk chunk, final World world) {
        if (!((IChunkLightingData) chunk).isLightInitialized()) {
            initChunkLighting(chunk, world);
        }

        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                if (x != 0 || z != 0) {
                    Chunk nChunk = LightingEngineHelpers.getLoadedChunk(world.getChunkProvider(), chunk.xPosition + x, chunk.zPosition + z);

                    if (nChunk == null || !((IChunkLightingData) nChunk).isLightInitialized()) {
                        return;
                    }
                }
            }
        }

        chunk.isLightPopulated = true;
    }

    public static void initSkylightForSection(final World world, final Chunk chunk, final ExtendedBlockStorage section) {
        if (!world.provider.hasNoSky) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    if (chunk.getHeightValue(x, z) <= section.getYLocation()) {
                        for (int y = 0; y < 16; ++y) {
                            section.setExtSkylightValue(x, y, z, EnumSkyBlock.Sky.defaultLightValue);
                        }
                    }
                }
            }
        }
    }

    private static short[] getNeighborLightChecks(Chunk chunk) {
        return ((IChunkLightingData) chunk).getNeighborLightChecks();
    }

    private static void setNeighborLightChecks(Chunk chunk, short[] table) {
        ((IChunkLightingData) chunk).setNeighborLightChecks(table);
    }

    public static int getCachedLightFor(Chunk chunk, EnumSkyBlock type, int x, int y, int z) {
        return ((IChunkLighting) chunk).getCachedLightFor(type, x, y, z);
    }

    public static ILightingEngine getLightingEngine(World world) {
        return ((ILightingEngineProvider) world).getLightingEngine();
    }

    /**
     * Get the intrinsic or saved block light value in a chunk.
     * @param chunk the chunk
     * @param x X coordinate (0-15)
     * @param y Y coordinate (0-255)
     * @param z Z coordinate (0-15)
     * @return light level
     */
    public static int getIntrinsicOrSavedBlockLightValue(Chunk chunk, int x, int y, int z) {
        int savedLightValue = chunk.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
        int bx = x + (chunk.xPosition * 16);
        int bz = z + (chunk.zPosition * 16);
        Block block = chunk.getBlock(x, y, z);
        int lightValue = LightingEngineHelpers.getLightValueForState(block, chunk.worldObj, bx, y, bz);
        return Math.max(savedLightValue, lightValue);
    }
}
