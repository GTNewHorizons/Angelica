package com.gtnewhorizons.angelica.mixins.early.novisoculis;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.List;
import java.util.Random;
import klaxon.klaxon.novisoculis.CubeModel;
import klaxon.klaxon.novisoculis.QuadProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockGrass.class)
public abstract class MixinBlockGrass extends Block implements QuadProvider {

    // this.blockIcon is grass_side
    @Shadow(aliases = "field_149991_b")
    private IIcon grassTop;
    @Shadow(aliases = "field_149993_M")
    private IIcon grassSideSnowed;
    @Shadow(aliases = "field_149994_N")
    private IIcon grassSideOverlay;


    private static final QuadProvider model = new CubeModel(new boolean[]{false, true, false, false, false, false});

    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random) {
        return model.getQuads(world, pos, block, meta, dir, random);
    }

    private MixinBlockGrass(Material materialIn) {
        super(materialIn);
    }
}
