package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.Random;

import static com.gtnewhorizons.angelica.models.CubeModel.INSTANCE;

@Mixin(BlockLeaves.class)
public abstract class MixinBlockLeaves extends Block implements QuadProvider {

    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, ObjectPooler<Quad> quadPool) {
        return INSTANCE.get().getQuads(world, pos, block, meta, dir, random, color, quadPool);
    }

    private MixinBlockLeaves(Material materialIn) {
        super(materialIn);
    }
}
