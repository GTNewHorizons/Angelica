package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.compat.forge.IForgeBakedModel;
import com.gtnewhorizons.angelica.compat.forge.IModelData;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.Random;

public interface BakedModel extends IForgeBakedModel {

    List<BakedQuad> getQuads(BlockState state, ForgeDirection face, Random random);

    IModelData getModelData(BlockRenderView world, BlockPos pos, BlockState state, IModelData modelData);
}
