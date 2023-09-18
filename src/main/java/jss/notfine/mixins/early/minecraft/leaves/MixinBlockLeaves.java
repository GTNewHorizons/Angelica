package jss.notfine.mixins.early.minecraft.leaves;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.util.ILeafBlock;
import jss.util.DirectionHelper;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockLeaves.class)
public abstract class MixinBlockLeaves extends BlockLeavesBase {

    /**
     * @author jss2a98aj
     * @reason Control leaf opacity.
     */
    @Overwrite
    public boolean isOpaqueCube() {
        return SettingsManager.leavesOpaque;
    }

    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        if(field_150129_M[0] == null) {
            //A mod dev had no idea what they were doing.
            return getIcon(side, world.getBlockMetadata(x, y, z));
        }
        int renderMode = (int)Settings.MODE_LEAVES.getValue();
        int maskedMeta = world.getBlockMetadata(x, y, z) & 3;
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
        maskedMeta = maskedMeta >= field_150129_M[renderMode].length ? 0 : maskedMeta;
        return field_150129_M[renderMode][maskedMeta];
    }

    @Shadow protected IIcon[][] field_150129_M;

    protected MixinBlockLeaves(Material material, boolean overridden) {
        super(material, overridden);
    }

}
