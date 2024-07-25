package com.gtnewhorizons.angelica.models.template;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * While this class does not provide dynamic quads, they still need to be threadlocaled because the color of the quads
 * are modified.
 */
public class BlockColoredCube extends BlockStaticCube {

    private ThreadLocal<List<QuadView>[]> threadedStore;

    public BlockColoredCube(String block) {
        super(block);
    }

    @Override
    protected void bake() {
        super.bake();

        // Kind of a mess, but it's required because I don't want to just store quads - that would require initializing
        // a new List every time. This ain't efficient, but it only runs when a new thread is spun off.
        this.threadedStore = ThreadLocal.withInitial(() -> {
            final List<QuadView>[] ret = new List[7];

            for (int i = 0; i < 6; ++i) {
                ret[i] = this.store[i].stream().map(new Quad()::copyFrom)
                    .collect(ObjectImmutableList.toListWithExpectedSize(1));
            }
            ret[6] = ObjectImmutableList.of();

            return ret;
        });
    }

    @Override
    public int getColor(IBlockAccess world, BlockPos pos, Block block, int meta, Random random) {
        return QuadProvider.getDefaultColor(world, pos, block);
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

        final List<QuadView> ret = this.threadedStore.get()[dir.ordinal()];
        if (dir != ForgeDirection.UNKNOWN)
            ret.get(0).setColors(color);

        return ret;
    }
}
