package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import java.util.List;
import java.util.Random;
import com.gtnewhorizons.angelica.models.CubeModel;
import com.gtnewhorizons.angelica.api.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockStone.class)
public abstract class MixinBlockStone implements QuadProvider {

    // TODO: Use modern model API
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, ObjectPooler<Quad> quadPool) {
        return CubeModel.INSTANCE.get().getQuads(world, pos, block, meta, dir, random, quadPool);
    }
}
