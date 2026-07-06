package jss.notfine.util;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.named.LeavesQuality;
import net.minecraft.block.Block;
import net.minecraft.util.Facing;
import net.minecraft.world.IBlockAccess;

public class LeafRenderUtil {

    public static final int[] relativeADirections = {
        2, 3, 4, 5, 0, 1
    };

    public static final int[] relativeBDirections = {
        3, 2, 5, 4, 1, 0
    };

    public static final int[] relativeCDirections = {
        4, 5, 0, 1, 2, 3
    };

    public static final int[] relativeDDirections = {
        5, 4, 1, 0, 3, 2
    };

    public static boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        final Block otherBlock = world.getBlock(x, y, z);
        if(otherBlock.isOpaqueCube()) {
            return false;
        }
        if(otherBlock instanceof ILeafBlock otherLeaf && otherLeaf.isFullLeaf(world, x, y, z)) {
            switch ((LeavesQuality)Settings.MODE_LEAVES.option.getStore()) {
                case FAST, SMART -> {
                    return false;
                }
                case SHELLED_FANCY, SHELLED_FAST -> {
                    x -= Facing.offsetsXForSide[side];
                    y -= Facing.offsetsYForSide[side];
                    z -= Facing.offsetsZForSide[side];
                    int renderCheck = 0;
                    renderCheck += ignoreWhenCulling(world, x + 1, y, z) ? 0 : 1;
                    renderCheck += ignoreWhenCulling(world, x - 1, y, z) ? 0 : 1;
                    renderCheck += ignoreWhenCulling(world, x, y + 1, z) ? 0 : 1;
                    renderCheck += ignoreWhenCulling(world, x, y - 1, z) ? 0 : 1;
                    renderCheck += ignoreWhenCulling(world, x, y, z + 1) ? 0 : 1;
                    renderCheck += ignoreWhenCulling(world, x, y, z - 1) ? 0 : 1;
                    boolean renderSide = renderCheck == 6;
                    if (renderSide) {
                        x += 2 * Facing.offsetsXForSide[side];
                        y += 2 * Facing.offsetsYForSide[side];
                        z += 2 * Facing.offsetsZForSide[side];
                        if(!ignoreWhenCulling(world, x, y, z)) {
                            renderSide = false;
                        }
                        x -= Facing.offsetsXForSide[side];
                        y -= Facing.offsetsYForSide[side];
                        z -= Facing.offsetsZForSide[side];
                        if(ignoreWhenCulling(world, x, y, z, relativeADirections[side])) {
                            return true;
                        }
                        if(ignoreWhenCulling(world, x, y, z, relativeBDirections[side])) {
                            return true;
                        }
                        if(ignoreWhenCulling(world, x, y, z, relativeCDirections[side])) {
                            return true;
                        }
                        if(ignoreWhenCulling(world, x, y, z, relativeDDirections[side])) {
                            return true;
                        }
                    }
                    return renderSide;
                }
                default -> {
                    return !SettingsManager.leavesOpaque;
                }
            }
        }
        //Check for IFaceObstructionCheckHelper
        if(otherBlock instanceof IFaceObstructionCheckHelper target) {
            return target.isFaceNonObstructing(world, x, y, z, side, 0D, 0D, 0D, 1D, 1D, 1D);
        }
        return true;
    }

    public static boolean isFaceNonObstructing(IBlockAccess world, int x, int y, int z) {
        if(Settings.MODE_LEAVES.option.getStore() != LeavesQuality.SHELLED_FAST) {
            return !SettingsManager.leavesOpaque;
        }
        if(ignoreWhenCulling(world, x + 1, y, z)) {
            return true;
        }
        if(ignoreWhenCulling(world, x - 1, y, z)) {
            return true;
        }
        if(ignoreWhenCulling(world, x, y + 1, z)) {
            return true;
        }
        if(ignoreWhenCulling(world, x, y - 1, z)) {
            return true;
        }
        if(ignoreWhenCulling(world, x, y, z + 1)) {
            return true;
        }
        return ignoreWhenCulling(world, x, y, z - 1);
    }

    public static boolean ignoreWhenCulling(IBlockAccess world, int x, int y, int z) {
        Block otherBlock = world.getBlock(x, y, z);
        return !(otherBlock instanceof ILeafBlock leafBlock && leafBlock.isFullLeaf(world, x, y, z) || otherBlock.isOpaqueCube());
    }

    public static boolean ignoreWhenCulling(IBlockAccess world, int x, int y, int z, int side) {
        x += Facing.offsetsXForSide[side];
        y += Facing.offsetsYForSide[side];
        z += Facing.offsetsZForSide[side];
        final Block otherBlock = world.getBlock(x, y, z);
        return !(otherBlock instanceof ILeafBlock leafBlock && leafBlock.isFullLeaf(world, x, y, z) || otherBlock.isOpaqueCube());
    }

    public static boolean selectRenderMode(IBlockAccess world, int x, int y, int z, int side) {
        if(Settings.MODE_LEAVES.option.getStore() == LeavesQuality.SHELLED_FAST) {
            x += Facing.offsetsXForSide[side];
            y += Facing.offsetsYForSide[side];
            z += Facing.offsetsZForSide[side];
            final Block otherBlock = world.getBlock(x, y, z);
            return otherBlock instanceof ILeafBlock leafBlock && leafBlock.isFullLeaf(world, x, y, z);
        }
        return SettingsManager.leavesOpaque;
    }

}
