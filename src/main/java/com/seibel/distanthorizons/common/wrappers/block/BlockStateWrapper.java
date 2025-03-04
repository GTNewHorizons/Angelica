package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.block.Block;

import java.awt.*;
import java.util.HashSet;

public class BlockStateWrapper implements IBlockStateWrapper {
    public static final IBlockStateWrapper AIR = null;

    public static IBlockStateWrapper deserialize(String resourceLocationString, ILevelWrapper levelWrapper) {
        return null;
    }

    public static void clearRendererIgnoredCaveBlocks() {

    }

    public static void clearRendererIgnoredBlocks() {
    }

    public static HashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks(ILevelWrapper levelWrapper) {
        return null;
    }

    public static HashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper) {
        return null;
    }

    public static IDhApiBlockStateWrapper fromBlockState(Block block, int meta, ILevelWrapper coreLevelWrapper) {
        return null;
    }

    @Override
    public boolean isAir() {
        return false;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isLiquid() {
        return false;
    }

    @Override
    public String getSerialString() {
        return "";
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public int getLightEmission() {
        return 0;
    }

    @Override
    public byte getMaterialId() {
        return 0;
    }

    @Override
    public boolean isBeaconBlock() {
        return false;
    }

    @Override
    public boolean isBeaconTintBlock() {
        return false;
    }

    @Override
    public boolean isBeaconBaseBlock() {
        return false;
    }

    @Override
    public Color getMapColor() {
        return null;
    }

    @Override
    public Color getBeaconTintColor() {
        return null;
    }

    @Override
    public Object getWrappedMcObject() {
        return null;
    }
}
