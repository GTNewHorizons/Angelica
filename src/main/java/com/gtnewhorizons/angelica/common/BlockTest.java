package com.gtnewhorizons.angelica.common;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.models.json.Loader;
import com.gtnewhorizons.angelica.models.json.ModelLocation;
import com.gtnewhorizons.angelica.models.json.Variant;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class BlockTest extends Block implements QuadProvider {

    public static final Variant[] modelId = {
        new Variant(
            new ModelLocation("block/lectern"),
            0,
            0,
            false
        ),
        new Variant(
            new ModelLocation("block/lectern"),
            180,
            0,
            false
        ),
        new Variant(
            new ModelLocation("block/lectern"),
            90,
            0,
            false
        ),
        new Variant(
            new ModelLocation("block/lectern"),
            270,
            0,
            false
        ),
    };
    private static final List<QuadView> EMPTY = ObjectImmutableList.of();
    private static final JsonModel[] model = new JsonModel[4];

    public BlockTest() {

        super(Material.rock);
    }

    public static void loadModel() {
        for (int i = 0; i < 4; ++i)
            model[i] = Loader.getModel(modelId[i]);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {

        reg.registerIcon("angelica:test_block");
        reg.registerIcon("block/lectern_base");
        reg.registerIcon("block/lectern_front");
        reg.registerIcon("block/lectern_sides");
        reg.registerIcon("block/lectern_top");
        reg.registerIcon("block/oak_planks");
        reg.registerIcon("block/stone");
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

        if (meta < 2 || meta > 5) meta = 2;

        return (model[meta - 2] != null) ? model[meta - 2].getQuads(world, pos, block, meta, dir, random, color, quadPool) : EMPTY;
    }

    /**
     * Called when a block is placed using its ItemBlock. Args: World, X, Y, Z, side, hitX, hitY, hitZ, block metadata
     */
    @Override
    public int onBlockPlaced(@NotNull World worldIn, int x, int y, int z, int side, float subX, float subY, float subZ, int meta) {

        // Face NORTH if placed up or down
        final ForgeDirection s = ForgeDirection.values()[side];
        if (s == ForgeDirection.UP || s == ForgeDirection.DOWN)
            return 2;

        // Face the placed side
        return side;
    }
}
