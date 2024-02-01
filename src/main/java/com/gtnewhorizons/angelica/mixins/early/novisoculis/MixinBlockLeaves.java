package com.gtnewhorizons.angelica.mixins.early.novisoculis;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.List;
import java.util.Random;
import klaxon.klaxon.novisoculis.CubeModel;
import klaxon.klaxon.novisoculis.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockLeaves.class)
public abstract class MixinBlockLeaves extends Block implements QuadProvider {

    private static final ThreadLocal<QuadProvider> model = ThreadLocal.withInitial(() -> new CubeModel(true));

    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random) {
        return model.get().getQuads(world, pos, block, meta, dir, random);
    }

    private MixinBlockLeaves(Material materialIn) {
        super(materialIn);
    }
}
