package com.gtnewhorizons.angelica.models.template;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.ModelLoader;
import com.gtnewhorizons.angelica.api.QuadBuilder;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.models.NdQuadBuilder;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class ColumnModel implements QuadProvider {

    private final String topTex;
    private final String sideTex;
    protected final List<QuadView>[] store = new List[7];
    private boolean rotate = false;
    private Matrix4f rotMat;

    public ColumnModel(String topTex, String sideTex, Matrix4f rotMat) {
        this(topTex, sideTex);
        this.rotate = true;
        this.rotMat = rotMat;
    }

    public ColumnModel(String topTex, String sideTex) {
        this.topTex = topTex;
        this.sideTex = sideTex;
        ModelLoader.registerBaker(this::bake);
    }

    protected void bake() {

        final NdQuadBuilder builder = new NdQuadBuilder();

        for (ForgeDirection f : ForgeDirection.VALID_DIRECTIONS) {

            builder.square(f, 0, 0, 1, 1, 0);

            final String tex = (f == ForgeDirection.UP || f == ForgeDirection.DOWN) ? this.topTex : this.sideTex;
            builder.spriteBake(tex, QuadBuilder.BAKE_LOCK_UV);

            builder.setColors(-1);

            final List<QuadView> tmp = ObjectImmutableList.of(
                (this.rotate) ? builder.build(new Quad(), this.rotMat) : builder.build(new Quad()));

            this.store[tmp.get(0).getCullFace().ordinal()] = tmp;
        }
        this.store[6] = ObjectImmutableList.of();
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {
        return this.store[dir.ordinal()];
    }
}
