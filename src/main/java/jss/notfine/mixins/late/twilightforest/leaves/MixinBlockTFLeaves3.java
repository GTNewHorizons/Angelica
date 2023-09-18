package jss.notfine.mixins.late.twilightforest.leaves;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.util.ILeafBlock;
import jss.util.DirectionHelper;
import net.minecraft.block.BlockLeaves;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import twilightforest.block.BlockTFLeaves3;

@Mixin(value = BlockTFLeaves3.class)
public abstract class MixinBlockTFLeaves3 extends BlockLeaves {

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int renderMode = (int)Settings.MODE_LEAVES.getValue();
        switch(renderMode) {
            case -1:
                renderMode = SettingsManager.leavesOpaque ? 1 : 0;
                break;
            case 4:
                renderMode = world.getBlock(
                    x + DirectionHelper.xDirectionalIncrease[side],
                    y + DirectionHelper.yDirectionalIncrease[side],
                    z + DirectionHelper.zDirectionalIncrease[side]
                ) instanceof ILeafBlock ? 1 : 0;
                break;
            default:
                renderMode = renderMode > 1 ? 0 : renderMode;
                break;
        }
        return Blocks.leaves.field_150129_M[renderMode][0];
    }

}
