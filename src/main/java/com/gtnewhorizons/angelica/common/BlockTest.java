package com.gtnewhorizons.angelica.common;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.utils.AssetLoader;
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

import static com.gtnewhorizons.angelica.models.VanillaModels.LECTERN;

public class BlockTest extends Block implements QuadProvider {

    public BlockTest() {

        super(Material.rock);
        this.setBlockTextureName("missingno");
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        for (String s : AssetLoader.testTexs) {
            reg.registerIcon(s);
        }
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

        if (meta < 2 || meta > 5) meta = 2;

        return LECTERN.models[meta - 2].getQuads(world, pos, block, meta, dir, random, color, quadPool);
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
