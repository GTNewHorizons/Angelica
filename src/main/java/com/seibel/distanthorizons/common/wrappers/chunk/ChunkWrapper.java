package com.seibel.distanthorizons.common.wrappers.chunk;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;

public class ChunkWrapper implements IChunkWrapper {
    public ChunkWrapper(Chunk chunk, ILevelWrapper levelWrapper) {
    }

    @Override
    public DhChunkPos getChunkPos() {
        return null;
    }

    @Override
    public int getInclusiveMinBuildHeight() {
        return 0;
    }

    @Override
    public int getExclusiveMaxBuildHeight() {
        return 0;
    }

    @Override
    public int getMinNonEmptyHeight() {
        return 0;
    }

    @Override
    public int getMaxNonEmptyHeight() {
        return 0;
    }

    @Override
    public int getSolidHeightMapValue(int xRel, int zRel) {
        return 0;
    }

    @Override
    public int getLightBlockingHeightMapValue(int xRel, int zRel) {
        return 0;
    }

    @Override
    public int getMaxBlockX() {
        return 0;
    }

    @Override
    public int getMaxBlockZ() {
        return 0;
    }

    @Override
    public int getMinBlockX() {
        return 0;
    }

    @Override
    public int getMinBlockZ() {
        return 0;
    }

    @Override
    public void setIsDhBlockLightCorrect(boolean isDhLightCorrect) {

    }

    @Override
    public void setIsDhSkyLightCorrect(boolean isDhLightCorrect) {

    }

    @Override
    public boolean isDhBlockLightingCorrect() {
        return false;
    }

    @Override
    public boolean isDhSkyLightCorrect() {
        return false;
    }

    @Override
    public int getDhSkyLight(int relX, int relY, int relZ) {
        return 0;
    }

    @Override
    public void setDhSkyLight(int relX, int relY, int relZ, int lightValue) {

    }

    @Override
    public void clearDhSkyLighting() {

    }

    @Override
    public int getDhBlockLight(int relX, int relY, int relZ) {
        return 0;
    }

    @Override
    public void setDhBlockLight(int relX, int relY, int relZ, int lightValue) {

    }

    @Override
    public void clearDhBlockLighting() {

    }

    @Override
    public ArrayList<DhBlockPos> getWorldBlockLightPosList() {
        return null;
    }

    @Override
    public IBlockStateWrapper getBlockState(int relX, int relY, int relZ) {
        return null;
    }

    @Override
    public IBlockStateWrapper getBlockState(int relX, int relY, int relZ, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess) {
        return null;
    }

    @Override
    public IMutableBlockPosWrapper getMutableBlockPosWrapper() {
        return null;
    }

    @Override
    public IBiomeWrapper getBiome(int relX, int relY, int relZ) {
        return null;
    }

    @Override
    public boolean isStillValid() {
        return false;
    }
}
