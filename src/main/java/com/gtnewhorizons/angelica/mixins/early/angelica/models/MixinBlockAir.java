package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Random;
import com.gtnewhorizons.angelica.api.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockAir.class)
public abstract class MixinBlockAir implements QuadProvider {

    private static List<Quad> quads = new ObjectArrayList<>();

    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random) {
        return quads;
    }
}
