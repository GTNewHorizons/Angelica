package com.gtnewhorizons.angelica.common;

import static com.gtnewhorizon.gtnhlib.client.model.ModelISBRH.JSON_ISBRH_ID;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

/// Unline {@link BlockError}, this *is* registered on both sides. It's intended to test functionality in a SP
/// development environment.
public class BlockTest extends Block {

    public BlockTest() {
        super(Material.wood);
        setHardness(0.7f);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    /**
     * Called when a block is placed using its ItemBlock. Args: World, X, Y, Z, side, hitX, hitY, hitZ, block metadata
     */
    @Override
    public int onBlockPlaced(@NotNull World worldIn, int x, int y, int z, int side, float subX, float subY, float subZ, int meta) {

        // Face NORTH if placed up or down
        final var s = ForgeDirection.getOrientation(side);
        if (s == ForgeDirection.UP || s == ForgeDirection.DOWN)
            return 2;

        // Face the placed side
        return side;
    }

    @Override
    public int getRenderType() {
        return JSON_ISBRH_ID;
    }
}
