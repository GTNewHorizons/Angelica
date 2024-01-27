package com.gtnewhorizons.angelica.mixins.early.novisoculis;

import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.List;
import java.util.Random;
import klaxon.klaxon.novisoculis.CubeModel;
import klaxon.klaxon.novisoculis.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockStone.class)
public abstract class MixinBlockStone implements QuadProvider {

    public List<Quad> getQuads(Block block, int meta, ForgeDirection dir, Random random) {
        return CubeModel.INSTANCE.getQuads(block, meta, dir, random);
    }
}
