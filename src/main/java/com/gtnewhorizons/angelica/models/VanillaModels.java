package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.models.json.Loader;
import com.gtnewhorizons.angelica.models.json.ModelLocation;
import com.gtnewhorizons.angelica.models.json.Variant;
import com.gtnewhorizons.angelica.models.template.BlockColoredCube;
import com.gtnewhorizons.angelica.models.template.BlockStaticCube;
import com.gtnewhorizons.angelica.models.template.JsonModel4Rot;
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

    public static final Variant workbench = new Variant(
        new ModelLocation("block/crafting_table"),
        0,
        0,
        true
    );
    public static JsonModel WORKBENCH;

    public static JsonModel4Rot LECTERN;

    public static void init() {

        if (init) {
            throw new RuntimeException("Vanilla models were baked twice!");
        }

        if (AngelicaConfig.injectQPRendering) {
            STONE = new BlockStaticCube(Blocks.stone.getTextureName());
            ACACIA_LEAVES = new BlockColoredCube("leaves_acacia");
            BIRCH_LEAVES = new BlockColoredCube("leaves_birch");
            DARK_OAK_LEAVES = new BlockColoredCube("leaves_big_oak");
            JUNGLE_LEAVES = new BlockColoredCube("leaves_jungle");
            OAK_LEAVES = new BlockColoredCube("leaves_oak");
            SPRUCE_LEAVES = new BlockColoredCube("leaves_spruce");

            Loader.registerModels(VanillaModels::loadModels,
                workbench);
        }

        if (AngelicaConfig.enableTestBlocks)
            LECTERN = new JsonModel4Rot(new ModelLocation("block/lectern"));

        init = true;
    }

    public static void loadModels() {
        WORKBENCH = Loader.getModel(workbench);
    }
}
