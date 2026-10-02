package com.gtnewhorizons.angelica.common;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockIsbrhTest extends Block {

    public static int renderId = -1;

    public BlockIsbrhTest() {
        super(Material.rock);
        setHardness(1.5f);
        setBlockName("angelica.test_isbrh");
        setBlockTextureName("stone");
    }

    @Override
    public int getRenderType() {
        return renderId;
    }
}
