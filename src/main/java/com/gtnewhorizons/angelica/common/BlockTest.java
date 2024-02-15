package com.gtnewhorizons.angelica.common;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.api.BlockState;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.models.json.Loader;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

public class BlockTest extends Block implements QuadProvider, BlockState {

    public static final ResourceLocation modelId = new ResourceLocation("blocks/lectern");
    public static final List<Quad> EMPTY = ObjectImmutableList.of();

    public BlockTest() {

        super(Material.rock);
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {

        reg.registerIcon("angelica:test_block");
        reg.registerIcon("lectern_base");
        reg.registerIcon("lectern_front");
        reg.registerIcon("lectern_sides");
        reg.registerIcon("lectern_top");
        reg.registerIcon("oak_planks");
    }

    @Override
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, ObjectPooler<Quad> quadPool) {

        final JsonModel m = Loader.getModel(modelId);
        return (m != null) ? m.getQuads(world, pos, block, meta, dir, random, quadPool) : EMPTY;
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

    @Override
    public boolean hasFacing() {
        return true;
    }

    @Override
    public ForgeDirection getFacing(int meta) {

        return ForgeDirection.values()[meta];
    }
}
