package com.gtnewhorizons.angelica.models.template;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.models.NdQuadBuilder;
import com.gtnewhorizons.angelica.models.json.Loader;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static me.jellysquid.mods.sodium.common.util.DirectionUtil.ALL_DIRECTIONS;

public class BlockStaticCube implements QuadProvider {

    private final String textureName;
    protected final List<QuadView>[] store = new List[7];

    public BlockStaticCube(String textureName) {
        this.textureName = textureName;
        Loader.registerBaker(this::bake);
    }

    protected void bake() {

        final NdQuadBuilder builder = new NdQuadBuilder();
        for (int i = 0; i < 6; ++i) {

            builder.square(ALL_DIRECTIONS[i], 0, 0, 1, 1, 0);
            builder.spriteBake(this.textureName, NdQuadBuilder.BAKE_LOCK_UV);
            builder.color(-1, -1, -1, -1);
            this.store[i] = ObjectImmutableList.of(builder.build(new Quad()));
        }
        this.store[6] = ObjectImmutableList.of();
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {
        return store[dir.ordinal()];
    }
}
