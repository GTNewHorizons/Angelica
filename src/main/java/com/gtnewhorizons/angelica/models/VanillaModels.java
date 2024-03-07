package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.api.Loader;
import com.gtnewhorizons.angelica.models.json.ModelLocation;
import com.gtnewhorizons.angelica.api.Variant;
import com.gtnewhorizons.angelica.models.template.BlockColoredCube;
import com.gtnewhorizons.angelica.models.template.BlockStaticCube;
import com.gtnewhorizons.angelica.models.template.Column3Rot;
import com.gtnewhorizons.angelica.models.template.Model4Rot;
import net.minecraft.init.Blocks;

public class VanillaModels {

    private static boolean init = false;

    public static BlockStaticCube STONE;
    public static BlockStaticCube GLASS;

    public static BlockColoredCube ACACIA_LEAVES;
    public static BlockColoredCube BIRCH_LEAVES;
    public static BlockColoredCube DARK_OAK_LEAVES;
    public static BlockColoredCube JUNGLE_LEAVES;
    public static BlockColoredCube OAK_LEAVES;
    public static BlockColoredCube SPRUCE_LEAVES;

    public static Column3Rot ACACIA_LOG;
    public static Column3Rot BIRCH_LOG;
    public static Column3Rot DARK_OAK_LOG;
    public static Column3Rot JUNGLE_LOG;
    public static Column3Rot OAK_LOG;
    public static Column3Rot SPRUCE_LOG;

    public static BlockStaticCube ACACIA_WOOD;
    public static BlockStaticCube BIRCH_WOOD;
    public static BlockStaticCube DARK_OAK_WOOD;
    public static BlockStaticCube JUNGLE_WOOD;
    public static BlockStaticCube OAK_WOOD;
    public static BlockStaticCube SPRUCE_WOOD;


    public static final Variant workbench = new Variant(
        new ModelLocation("block/crafting_table"),
        0,
        0,
        true
    );
    public static QuadProvider WORKBENCH;

    public static Model4Rot LECTERN;

    public static void init() {

        if (init) {
            throw new RuntimeException("Vanilla models were baked twice!");
        }

        if (AngelicaConfig.injectQPRendering) {
            STONE = new BlockStaticCube(Blocks.stone.getTextureName());
            GLASS = new BlockStaticCube(Blocks.glass.getTextureName());

            ACACIA_LEAVES = new BlockColoredCube("leaves_acacia");
            BIRCH_LEAVES = new BlockColoredCube("leaves_birch");
            DARK_OAK_LEAVES = new BlockColoredCube("leaves_big_oak");
            JUNGLE_LEAVES = new BlockColoredCube("leaves_jungle");
            OAK_LEAVES = new BlockColoredCube("leaves_oak");
            SPRUCE_LEAVES = new BlockColoredCube("leaves_spruce");

            ACACIA_LOG = new Column3Rot("log_acacia_top", "log_acacia");
            BIRCH_LOG = new Column3Rot("log_birch_top", "log_birch");
            DARK_OAK_LOG = new Column3Rot("log_big_oak_top", "log_big_oak");
            JUNGLE_LOG = new Column3Rot("log_jungle_top", "log_jungle");
            OAK_LOG = new Column3Rot("log_oak_top", "log_oak");
            SPRUCE_LOG = new Column3Rot("log_spruce_top", "log_spruce");

            ACACIA_WOOD = new BlockStaticCube("log_acacia");
            BIRCH_WOOD = new BlockStaticCube("log_birch");
            DARK_OAK_WOOD = new BlockStaticCube("log_big_oak");
            JUNGLE_WOOD = new BlockStaticCube("log_jungle");
            OAK_WOOD = new BlockStaticCube("log_oak");
            SPRUCE_WOOD = new BlockStaticCube("log_spruce");

            Loader.registerModels(VanillaModels::loadModels,
                workbench);
        }

        if (AngelicaConfig.enableTestBlocks)
            LECTERN = new Model4Rot(new ModelLocation("block/lectern"));

        init = true;
    }

    public static void loadModels() {
        WORKBENCH = Loader.getModel(workbench);
    }
}
