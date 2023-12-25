package jss.notfine.util;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.named.LeavesQuality;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

public interface ILeafBlock extends IFaceObstructionCheckHelper {

    @Override()
    default boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {

        if(Settings.MODE_LEAVES.option.getStore() == LeavesQuality.SHELLED_FAST) {
            Block otherBlock;
            otherBlock = worldIn.getBlock(x + 1, y, z);
            if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                return true;
            }
            otherBlock = worldIn.getBlock(x - 1, y, z);
            if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                return true;
            }
            otherBlock = worldIn.getBlock(x, y + 1, z);
            if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                return true;
            }
            otherBlock = worldIn.getBlock(x, y - 1, z);
            if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                return true;
            }
            otherBlock = worldIn.getBlock(x, y, z + 1);
            if(!(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube())) {
                return true;
            }
            otherBlock = worldIn.getBlock(x, y, z - 1);
            return !(otherBlock instanceof ILeafBlock || otherBlock.isOpaqueCube());
        } else {
            return !SettingsManager.leavesOpaque;
        }
    }

}
