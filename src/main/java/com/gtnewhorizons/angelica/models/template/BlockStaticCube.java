package com.gtnewhorizons.angelica.models.template;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadBuilder;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.models.NdQuadBuilder;
import com.gtnewhorizons.angelica.api.ModelLoader;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class BlockStaticCube implements QuadProvider {

    private final String textureName;
    protected final List<QuadView>[] store = new List[7];

    public BlockStaticCube(String textureName) {
        this.textureName = textureName;
        ModelLoader.registerBaker(this::bake);
    }

    protected void bake() {

        final NdQuadBuilder builder = new NdQuadBuilder();
        for (ForgeDirection f : ForgeDirection.VALID_DIRECTIONS) {

            builder.square(f, 0, 0, 1, 1, 0);
            builder.spriteBake(this.textureName, QuadBuilder.BAKE_LOCK_UV);
            builder.setColors(-1);
            this.store[f.ordinal()] = ObjectImmutableList.of(builder.build(new Quad()));
        }
        this.store[6] = ObjectImmutableList.of();
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {
        return store[dir.ordinal()];
    }
}
