package com.gtnewhorizons.angelica.common;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
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
import net.minecraftforge.common.util.ForgeDirection;

public class BlockTest extends Block implements QuadProvider {

    public static final ResourceLocation modelId = new ResourceLocation("angelica", "blocks/test_block");
    public static final List<Quad> EMPTY = ObjectImmutableList.of();

    public BlockTest() {

        super(Material.rock);
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {

        reg.registerIcon("angelica:test_block");
    }

    @Override
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, ObjectPooler<Quad> quadPool) {

        final JsonModel m = Loader.getModel(modelId);
        return (m != null) ? m.getQuads(world, pos, block, meta, dir, random, quadPool) : EMPTY;
    }
}
