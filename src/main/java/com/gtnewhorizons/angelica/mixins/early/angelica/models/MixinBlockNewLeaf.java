package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import net.minecraft.block.Block;
import net.minecraft.block.BlockNewLeaf;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.gtnewhorizons.angelica.models.VanillaModels.*;

@Mixin(BlockNewLeaf.class)
public abstract class MixinBlockNewLeaf implements QuadProvider {

    @Override
    public int getColor(IBlockAccess world, BlockPos pos, Block block, int meta, Random random) {
        return QuadProvider.getDefaultColor(world, pos, block);
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

        return (switch (meta % 2) {
            case 0 -> ACACIA_LEAVES;
            case 1 -> DARK_OAK_LEAVES;
            default -> throw new IllegalStateException("Unexpected value: " + meta);
        }).getQuads(world, pos, block, meta, dir, random, color, quadPool);
    }
}
