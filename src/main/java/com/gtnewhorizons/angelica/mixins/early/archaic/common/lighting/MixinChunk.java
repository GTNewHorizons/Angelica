package com.gtnewhorizons.angelica.mixins.early.archaic.common.lighting;

import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.embeddedt.archaicfix.lighting.api.IChunkLighting;
import org.embeddedt.archaicfix.lighting.api.IChunkLightingData;
import org.embeddedt.archaicfix.lighting.api.ILightingEngine;
import org.embeddedt.archaicfix.lighting.api.ILightingEngineProvider;
import org.embeddedt.archaicfix.lighting.world.WorldChunkSlice;
import org.embeddedt.archaicfix.lighting.world.lighting.LightingHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Chunk.class)
public abstract class MixinChunk implements IChunkLighting, IChunkLightingData, ILightingEngineProvider {

    /**
     * Callback injected to the head of getLightSubtracted(BlockPos, int) to force deferred light updates to be processed.
     *
     * @author Angeline
     */
    @Inject(method = "getBlockLightValue", at = @At("HEAD"))
    private void onGetLightSubtracted(int x, int y, int z, int amount, CallbackInfoReturnable<Integer> cir) {
        this.getLightingEngine().processLightUpdates();
    }

    /**
     * Callback injected at the end of onLoad() to have previously scheduled light updates scheduled again.
     *
     * @author Angeline
     */
    @Inject(method = "onChunkLoad", at = @At("RETURN"))
    private void onLoad(CallbackInfo ci) {
        LightingHooks.scheduleRelightChecksForChunkBoundaries(this.worldObj, (Chunk) (Object) this);
    }

    // === REPLACEMENTS ===

    /**
     * Replaces the call in setLightFor(Chunk, EnumSkyBlock, BlockPos) with our hook.
     *
     * @author Angeline
     */
    @Redirect(
            method = "setLightValue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;generateSkylightMap()V"
            ),
            expect = 0
    )
    private void setLightForRedirectGenerateSkylightMap(Chunk chunk, EnumSkyBlock type, int x, int y, int z, int value) {
        LightingHooks.initSkylightForSection(this.worldObj, (Chunk) (Object) this, this.storageArrays[y >> 4]);
    }

    private int getBlockLightOpacity(int x, int y, int z) {
        return this.getBlock(x, y, z).getLightOpacity(this.worldObj, x, y, z);
    }

    /**
     * @reason Overwrites relightBlock with a more efficient implementation.
     * @author Angeline
     */
    @Overwrite
    private void relightBlock(int x, int y, int z) {
        int i = this.heightMap[z << 4 | x] & 255;

        int j = Math.max(y, i);

        while (j > 0 && this.getBlockLightOpacity(x, j - 1, z) == 0) {
            --j;
        }

        if (j != i) {
            this.heightMap[z << 4 | x] = j;

            if (!this.worldObj.provider.hasNoSky) {
                LightingHooks.relightSkylightColumn(this.worldObj, (Chunk) (Object) this, x, z, i, j);
            }

            int l1 = this.heightMap[z << 4 | x];

            if (l1 < this.heightMapMinimum) {
                this.heightMapMinimum = l1;
            }
        }
    }

    /**
     * @reason Hook for calculating light updates only as needed. {@link MixinChunk#getCachedLightFor(EnumSkyBlock, int, int, int)} does not
     * call this hook.
     *
     * @author Angeline
     */
    @Overwrite
    public int getSavedLightValue(EnumSkyBlock type, int x, int y, int z) {
        getLightingEngine().processLightUpdatesForType(type);

        return this.getCachedLightFor(type, x, y, z);
    }

    /**
     * @reason Hooks into checkLight() to check chunk lighting and returns immediately after, voiding the rest of the function.
     *
     * @author Angeline
     */
    @Overwrite
    public void func_150809_p() {
        this.isTerrainPopulated = true;

        LightingHooks.checkChunkLighting((Chunk) (Object) this, this.worldObj);
    }

    /**
     * @reason Optimized version of recheckGaps. Avoids chunk fetches as much as possible.
     *
     * @author Angeline
     */
    @Overwrite
    private void recheckGaps(boolean onlyOne) {
        this.worldObj.theProfiler.startSection("recheckGaps");

        WorldChunkSlice slice = new WorldChunkSlice(this.worldObj, this.xPosition, this.zPosition);

        if (this.worldObj.doChunksNearChunkExist(this.xPosition * 16 + 8, 0, this.zPosition * 16 + 8, 16)) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    if (this.recheckGapsForColumn(slice, x, z)) {
                        if (onlyOne) {
                            this.worldObj.theProfiler.endSection();

                            return;
                        }
                    }
                }
            }

            this.isGapLightingUpdated = false;
        }

        this.worldObj.theProfiler.endSection();
    }

    private boolean recheckGapsForColumn(WorldChunkSlice slice, int x, int z) {
        int i = x + z * 16;

        if (this.updateSkylightColumns[i]) {
            this.updateSkylightColumns[i] = false;

            int height = this.getHeightValue(x, z);

            int x1 = this.xPosition * 16 + x;
            int z1 = this.zPosition * 16 + z;

            int max = this.recheckGapsGetLowestHeight(slice, x1, z1);

            this.recheckGapsSkylightNeighborHeight(slice, x1, z1, height, max);

            return true;
        }

        return false;
    }

    private int recheckGapsGetLowestHeight(WorldChunkSlice slice, int x, int z) {
        int max = Integer.MAX_VALUE;

        for (EnumFacing facing : LightingHooks.HORIZONTAL_FACINGS) {
            int j = x + facing.getFrontOffsetX();
            int k = z + facing.getFrontOffsetZ();

            max = Math.min(max, slice.getChunkFromWorldCoords(j, k).heightMapMinimum);
        }

        return max;
    }

    private void recheckGapsSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int height, int max) {
        this.checkSkylightNeighborHeight(slice, x, z, max);

        for (EnumFacing facing : LightingHooks.HORIZONTAL_FACINGS) {
            int j = x + facing.getFrontOffsetX();
            int k = z + facing.getFrontOffsetZ();

            this.checkSkylightNeighborHeight(slice, j, k, height);
        }
    }

    private void checkSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int maxValue) {
        int i = slice.getChunkFromWorldCoords(x, z).getHeightValue(x & 15, z & 15);

        if (i > maxValue) {
            this.updateSkylightNeighborHeight(slice, x, z, maxValue, i + 1);
        } else if (i < maxValue) {
            this.updateSkylightNeighborHeight(slice, x, z, i, maxValue + 1);
        }
    }

    private void updateSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int startY, int endY) {
        if (endY > startY) {
            if (!slice.isLoaded(x, z, 16)) {
                return;
            }

            for (int i = startY; i < endY; ++i) {
                this.worldObj.updateLightByType(EnumSkyBlock.Sky, x, i, z);
            }

            this.isModified = true;
        }
    }

    /**
     * @author embeddedt
     * @reason optimize random light checks so they complete faster
     */
    @Overwrite
    public void enqueueRelightChecks()
    {
        /* Skip object allocation if we weren't going to run checks anyway */
        if (this.queuedLightChecks >= 4096)
            return;
        boolean isActiveChunk = worldObj.activeChunkSet.contains(new ChunkCoordIntPair(this.xPosition, this.zPosition));
        int lightRecheckSpeed;
        if(worldObj.isRemote && isActiveChunk) {
            lightRecheckSpeed = 256;
        } else if(worldObj.isRemote)
            lightRecheckSpeed = 64;
        else
            lightRecheckSpeed = 32;
        for (int i = 0; i < lightRecheckSpeed; ++i)
        {
            if (this.queuedLightChecks >= 4096)
            {
                return;
            }

            int section = this.queuedLightChecks % 16;
            int x = this.queuedLightChecks / 16 % 16;
            int z = this.queuedLightChecks / 256;
            ++this.queuedLightChecks;
            int bx = (this.xPosition << 4) + x;
            int bz = (this.zPosition << 4) + z;

            for (int y = 0; y < 16; ++y)
            {
                int by = (section << 4) + y;
                ExtendedBlockStorage storage = this.storageArrays[section];

                boolean performFullLightUpdate = false;
                if (storage == null && (y == 0 || y == 15 || x == 0 || x == 15 || z == 0 || z == 15))
                    performFullLightUpdate = true;
                else if(storage != null) {
                    Block block = storage.getBlockByExtId(x, y, z);
                    if(block.getLightOpacity(this.worldObj, bx, by, bz) >= 255 && block.getLightValue(this.worldObj, bx, by, bz) <= 0) {
                        int prevLight = storage.getExtBlocklightValue(x, y, z);
                        if(prevLight != 0) {
                            storage.setExtBlocklightValue(x, y, z, 0);
                            this.worldObj.markBlockRangeForRenderUpdate(bx, by, bz, bx, by, bz);
                        }
                    } else
                        performFullLightUpdate = true;
                }
                if (performFullLightUpdate)
                {
                    this.worldObj.func_147451_t(bx, by, bz);
                }
            }
        }
    }

    @Shadow
    public abstract int getHeightValue(int i, int j);

    // === INTERFACE IMPL ===

    private short[] neighborLightChecks;

    private boolean isLightInitialized;

    private ILightingEngine lightingEngine;

    @Override
    public short[] getNeighborLightChecks() {
        return this.neighborLightChecks;
    }

    @Override
    public void setNeighborLightChecks(short[] data) {
        this.neighborLightChecks = data;
    }

    @Override
    public ILightingEngine getLightingEngine() {
        if(this.lightingEngine == null) {
            this.lightingEngine = ((ILightingEngineProvider) this.worldObj).getLightingEngine();
            if(this.lightingEngine == null)
                throw new IllegalStateException();
        }
        return this.lightingEngine;
    }

    @Override
    public boolean isLightInitialized() {
        return this.isLightInitialized;
    }

    @Override
    public void setLightInitialized(boolean lightInitialized) {
        this.isLightInitialized = lightInitialized;
    }

    @Shadow public World worldObj;

    @Shadow private ExtendedBlockStorage[] storageArrays;

    @Shadow public abstract boolean canBlockSeeTheSky(int p_76619_1_, int p_76619_2_, int p_76619_3_);

    @Shadow public int[] heightMap;

    @Shadow public abstract Block getBlock(int p_150810_1_, int p_150810_2_, int p_150810_3_);

    @Shadow public int heightMapMinimum;

    @Shadow public boolean isTerrainPopulated;

    @Shadow @Final public int xPosition;

    @Shadow @Final public int zPosition;

    @Shadow private boolean isGapLightingUpdated;

    @Shadow public boolean[] updateSkylightColumns;

    @Shadow public boolean isModified;

    @Shadow private int queuedLightChecks;

    @Override
    public void setSkylightUpdatedPublic() {
        for (int i = 0; i < this.updateSkylightColumns.length; ++i)
        {
            this.updateSkylightColumns[i] = true;
        }

        this.recheckGaps(false);
    }

    @Override
    public int getCachedLightFor(EnumSkyBlock type, int xIn, int yIn, int zIn) {
        int i = xIn & 15;
        int j = yIn;
        int k = zIn & 15;

        ExtendedBlockStorage extendedblockstorage = this.storageArrays[j >> 4];

        if (extendedblockstorage == null) {
            if (this.canBlockSeeTheSky(i, j, k)) {
                return type.defaultLightValue;
            } else {
                return 0;
            }
        } else if (type == EnumSkyBlock.Sky) {
            if (this.worldObj.provider.hasNoSky) {
                return 0;
            } else {
                return extendedblockstorage.getExtSkylightValue(i, j & 15, k);
            }
        } else {
            if (type == EnumSkyBlock.Block) {
                return extendedblockstorage.getExtBlocklightValue(i, j & 15, k);
            } else {
                return type.defaultLightValue;
            }
        }
    }


    // === END OF INTERFACE IMPL ===
}
