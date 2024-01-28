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

    public static boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        Block otherBlock = worldIn.getBlock(x, y, z);
        if(otherBlock.isOpaqueCube()) {
            return false;
        }
        if(otherBlock instanceof ILeafBlock) {
            switch ((LeavesQuality)Settings.MODE_LEAVES.option.getStore()) {
                case FAST, SMART -> {
                    return false;
                }
                case SHELLED_FANCY, SHELLED_FAST -> {
                    x -= Facing.offsetsXForSide[side];
                    y -= Facing.offsetsYForSide[side];
                    z -= Facing.offsetsZForSide[side];
                    int renderCheck = 0;
                    otherBlock = worldIn.getBlock(x + 1, y, z);
                    if(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube()) {
                        renderCheck++;
                    }
                    otherBlock = worldIn.getBlock(x - 1, y, z);
                    if(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube()) {
                        renderCheck++;
                    }
                    otherBlock = worldIn.getBlock(x, y + 1, z);
                    if(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube()) {
                        renderCheck++;
                    }
                    otherBlock = worldIn.getBlock(x, y - 1, z);
                    if(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube()) {
                        renderCheck++;
                    }
                    otherBlock = worldIn.getBlock(x, y, z + 1);
                    if(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube()) {
                        renderCheck++;
                    }
                    otherBlock = worldIn.getBlock(x, y, z - 1);
                    if(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube()) {
                        renderCheck++;
                    }
                    boolean renderSide = renderCheck == 6;
                    if (renderSide) {
                        x += 2 * Facing.offsetsXForSide[side];
                        y += 2 * Facing.offsetsYForSide[side];
                        z += 2 * Facing.offsetsZForSide[side];
                        otherBlock = worldIn.getBlock(x, y, z);
                        if(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube()) {
                            renderSide = false;
                        }
                        x -= Facing.offsetsXForSide[side];
                        y -= Facing.offsetsYForSide[side];
                        z -= Facing.offsetsZForSide[side];
                        int nextSide = relativeADirections[side];
                        otherBlock = worldIn.getBlock(
                            x + Facing.offsetsXForSide[nextSide],
                            y + Facing.offsetsYForSide[nextSide],
                            z + Facing.offsetsZForSide[nextSide]
                        );
                        if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                            return true;
                        }
                        nextSide = relativeBDirections[side];
                        otherBlock = worldIn.getBlock(
                            x + Facing.offsetsXForSide[nextSide],
                            y + Facing.offsetsYForSide[nextSide],
                            z + Facing.offsetsZForSide[nextSide]
                        );
                        if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                            return true;
                        }
                        nextSide = relativeCDirections[side];
                        otherBlock = worldIn.getBlock(
                            x + Facing.offsetsXForSide[nextSide],
                            y + Facing.offsetsYForSide[nextSide],
                            z + Facing.offsetsZForSide[nextSide]
                        );
                        if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                            return true;
                        }
                        nextSide = relativeDDirections[side];
                        otherBlock = worldIn.getBlock(
                            x + Facing.offsetsXForSide[nextSide],
                            y + Facing.offsetsYForSide[nextSide],
                            z + Facing.offsetsZForSide[nextSide]
                        );
                        if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
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
            return target.isFaceNonObstructing(worldIn, x, y, z, side, 0D, 0D, 0D, 1D, 1D, 1D);
        }
        return true;
    }

}
