package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import java.util.List;
import java.util.Random;
import com.gtnewhorizons.angelica.models.CubeModel;
import com.gtnewhorizons.angelica.api.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockLeaves.class)
public abstract class MixinBlockLeaves extends Block implements QuadProvider {

    // TODO: Use modern model API
    private static final ThreadLocal<QuadProvider> model = ThreadLocal.withInitial(() -> new CubeModel(true));

    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, ObjectPooler<Quad> quadPool) {
        return model.get().getQuads(world, pos, block, meta, dir, random, quadPool);
    }

    private MixinBlockLeaves(Material materialIn) {
        super(materialIn);
    }
}
