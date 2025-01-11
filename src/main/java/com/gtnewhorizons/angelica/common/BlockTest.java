package com.gtnewhorizons.angelica.common;

import com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadProvider;
import com.gtnewhorizon.gtnhlib.client.renderer.util.DirectionUtil;
import com.gtnewhorizons.angelica.mixins.interfaces.ModeledBlock;
import com.gtnewhorizons.angelica.utils.AssetLoader;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import static com.gtnewhorizons.angelica.models.VanillaModels.LECTERN;

public class BlockTest extends Block implements ModeledBlock {

    @Getter
    @Setter
    private QuadProvider model;

    public BlockTest() {
        super(Material.wood);
        setHardness(0.7f);
        setBlockTextureName("missingno");
        setModel((world, x, y, z, block, meta, dir, random, color, quadPool) -> {
            if (meta < 2 || meta > 5) meta = 2;
            return LECTERN.models[meta - 2].getQuads(world, x, y, z, block, meta, dir, random, color, quadPool);
        });
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

    /**
     * Called when a block is placed using its ItemBlock. Args: World, X, Y, Z, side, hitX, hitY, hitZ, block metadata
     */
    @Override
    public int onBlockPlaced(@NotNull World worldIn, int x, int y, int z, int side, float subX, float subY, float subZ, int meta) {

        // Face NORTH if placed up or down
        final ForgeDirection s = DirectionUtil.ALL_DIRECTIONS[side];
        if (s == ForgeDirection.UP || s == ForgeDirection.DOWN)
            return 2;

        // Face the placed side
        return side;
    }
}
