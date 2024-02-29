package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.gtnewhorizons.angelica.models.VanillaModels.STONE;

@Mixin(BlockStone.class)
public abstract class MixinBlockStone implements QuadProvider {

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {
        return STONE.getQuads(world, pos, block, meta, dir, random, color, quadPool);
    }
}
