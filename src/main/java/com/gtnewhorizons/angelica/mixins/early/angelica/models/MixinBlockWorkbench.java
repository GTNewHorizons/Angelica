package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.List;
import java.util.Random;
import com.gtnewhorizons.angelica.models.CubeModel;
import com.gtnewhorizons.angelica.api.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockWorkbench.class)
public class MixinBlockWorkbench implements QuadProvider {

    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random) {
        return CubeModel.INSTANCE.getQuads(world, pos, block, meta, dir, random);
    }
}
