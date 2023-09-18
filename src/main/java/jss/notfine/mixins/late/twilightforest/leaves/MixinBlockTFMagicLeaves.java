package jss.notfine.mixins.late.twilightforest.leaves;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.util.ILeafBlock;
import jss.util.DirectionHelper;
import net.minecraft.block.BlockLeaves;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import twilightforest.block.BlockTFMagicLeaves;

@Mixin(value = BlockTFMagicLeaves.class)
public abstract class MixinBlockTFMagicLeaves extends BlockLeaves {
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int renderMode = (int) Settings.MODE_LEAVES.getValue();
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
        maskedMeta = maskedMeta > 1 ? 0 : maskedMeta;
        switch(maskedMeta) {
            default:
                return renderMode == 1 ? SPR_TIMELEAVES_OPAQUE : SPR_TIMELEAVES;
            case 1:
                return renderMode == 1 ? SPR_TRANSLEAVES_OPAQUE : SPR_TRANSLEAVES;
            case 3:
                return renderMode == 1 ? SPR_SORTLEAVES_OPAQUE : SPR_SORTLEAVES;
        }
    }

    @Shadow(remap = false)
    public static IIcon SPR_TIMELEAVES;
    @Shadow(remap = false)
    public static IIcon SPR_TIMELEAVES_OPAQUE;
    @Shadow(remap = false)
    public static IIcon SPR_TRANSLEAVES;
    @Shadow(remap = false)
    public static IIcon SPR_TRANSLEAVES_OPAQUE;
    @Shadow(remap = false)
    public static IIcon SPR_SORTLEAVES;
    @Shadow(remap = false)
    public static IIcon SPR_SORTLEAVES_OPAQUE;

}
