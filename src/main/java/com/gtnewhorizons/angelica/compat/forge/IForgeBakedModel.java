package com.gtnewhorizons.angelica.compat.forge;

import com.gtnewhorizons.angelica.compat.mojang.BakedModel;
import com.gtnewhorizons.angelica.compat.mojang.BakedQuad;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public interface IForgeBakedModel {
    default BakedModel getBakedModel()
    {
        return (BakedModel) this;
    }

    default List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable ForgeDirection side, @NotNull Random rand, @NotNull IModelData extraData)
    {
        return getBakedModel().getQuads(state, side, rand);
    }
}
