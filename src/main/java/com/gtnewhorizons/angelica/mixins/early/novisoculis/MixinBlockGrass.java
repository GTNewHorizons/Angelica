package com.gtnewhorizons.angelica.mixins.early.novisoculis;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.List;
import java.util.Random;
import klaxon.klaxon.novisoculis.CubeModel;
import klaxon.klaxon.novisoculis.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockGrass.class)
public class MixinBlockGrass implements QuadProvider {

    private static final QuadProvider model = new CubeModel(new boolean[]{false, true, false, false, false, false});

    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random) {
        return model.getQuads(world, pos, block, meta, dir, random);
    }
}
