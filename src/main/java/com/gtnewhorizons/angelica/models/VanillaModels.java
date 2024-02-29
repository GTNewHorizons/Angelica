package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.models.template.BlockColoredCube;
import com.gtnewhorizons.angelica.models.template.BlockStaticCube;
import net.minecraft.init.Blocks;

public class VanillaModels {

    private static boolean init = false;

    public static BlockStaticCube STONE;
    public static BlockColoredCube ACACIA_LEAVES;
    public static BlockColoredCube BIRCH_LEAVES;
    public static BlockColoredCube DARK_OAK_LEAVES;
    public static BlockColoredCube JUNGLE_LEAVES;
    public static BlockColoredCube OAK_LEAVES;
    public static BlockColoredCube SPRUCE_LEAVES;

    public static void init() {

        if (init) {
            throw new RuntimeException("Vanilla models were baked twice!");
        }

        STONE = new BlockStaticCube(Blocks.stone.getTextureName());
        ACACIA_LEAVES = new BlockColoredCube("leaves_acacia");
        BIRCH_LEAVES = new BlockColoredCube("leaves_birch");
        DARK_OAK_LEAVES = new BlockColoredCube("leaves_big_oak");
        JUNGLE_LEAVES = new BlockColoredCube("leaves_jungle");
        OAK_LEAVES = new BlockColoredCube("leaves_oak");
        SPRUCE_LEAVES = new BlockColoredCube("leaves_spruce");

        init = true;
    }
}
