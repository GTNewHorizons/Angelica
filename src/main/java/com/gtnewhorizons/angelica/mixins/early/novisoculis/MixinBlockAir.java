package com.gtnewhorizons.angelica.mixins.early.novisoculis;

import com.gtnewhorizons.angelica.compat.nd.Quad;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Random;
import klaxon.klaxon.novisoculis.CubeModel;
import klaxon.klaxon.novisoculis.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockStone;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockAir.class)
public abstract class MixinBlockAir implements QuadProvider {

    private static List<Quad> quads = new ObjectArrayList<>();

    public List<Quad> getQuads(Block block, int meta, ForgeDirection dir, Random random) {
        return quads;
    }
}
