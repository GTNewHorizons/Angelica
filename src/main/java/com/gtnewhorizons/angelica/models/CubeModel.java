package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.Random;

public class CubeModel implements QuadProvider {

    /*
    * By the power of ~~theft~~ inspiration, this class is wayyy simpler.
    * It's also a basic example of dynamic model building.
    */

    public static final ThreadLocal<CubeModel> INSTANCE = ThreadLocal.withInitial(CubeModel::new);
    private final NdQuadBuilder builder = new NdQuadBuilder();
    private static final List<Quad> EMPTY = ObjectImmutableList.of();
    private final List<Quad> ONE = new ObjectArrayList<>(1);

    public CubeModel() {

        this.ONE.add(null);
    }

    @Override
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, ObjectPooler<Quad> quadPool) {

        if (dir == ForgeDirection.UNKNOWN)
            return EMPTY;

        this.builder.square(dir, 0, 0, 1, 1, 0);
        final IIcon tex = block.getIcon(dir.ordinal(), meta);
        this.builder.spriteBake(tex, NdQuadBuilder.BAKE_LOCK_UV);

        this.builder.color(color, color, color, color);

        ONE.set(0, this.builder.build(quadPool.getInstance()));
        return ONE;
    }
}
