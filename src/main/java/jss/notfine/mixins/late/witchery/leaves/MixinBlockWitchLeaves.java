package jss.notfine.mixins.late.witchery.leaves;

import com.emoniph.witchery.blocks.BlockWitchLeaves;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.util.ILeafBlock;
import jss.util.DirectionHelper;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockWitchLeaves.class)
public abstract class MixinBlockWitchLeaves extends BlockLeavesBase {

    /**
     * @author jss2a98aj
     * @reason Support new leaf rendering modes on Witchery leaves.
     */
    @Overwrite
    public boolean isOpaqueCube() {
        return SettingsManager.leavesOpaque;
    }

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
        return iconsForModes[renderMode][maskedMeta];
    }

    @Shadow(remap = false)
    private IIcon[][] iconsForModes;

    protected MixinBlockWitchLeaves(Material material, boolean unused) {
        super(material, unused);
    }

}
