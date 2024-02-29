package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

@Mixin(BlockAir.class)
public abstract class MixinBlockAir implements QuadProvider {

    @Unique
    private static final List<QuadView> angelica$EMPTY = ObjectImmutableList.of();

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {
        return angelica$EMPTY;
    }
}
