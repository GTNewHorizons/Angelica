package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import glowredman.txloader.Asset;
import glowredman.txloader.TXLoaderCore;

public class AssetLoader {

    public static final String[] texs = {
        "block/lectern_base",
        "block/lectern_front",
        "block/lectern_base",
        "block/lectern_sides",
        "block/lectern_top",
        "block/oak_planks",
        "block/stone",
        "block/oak_planks",
        "block/crafting_table_side",
        "block/crafting_table_front",
        "block/crafting_table_side",
        "block/crafting_table_top"
    };

    public static void load() {

        if (AngelicaConfig.injectQPRendering) {
            addModelAssets(
                "block/block",
                "block/cube",
                "block/cube_all",
                "block/lectern",
                "block/stone",
                "block/crafting_table"
            );
            addTexAssets(texs);
        }
    }

    private static void addModelAssets(String... resourcePaths) {
        for (String path : resourcePaths) {
            TXLoaderCore.getAssetBuilder("minecraft/models/" + path + ".json")
                .setVersion("1.20.4")
                .setSource(Asset.Source.CLIENT)
                .add();
        }
    }

    private static void addTexAssets(String... resourcePaths) {
        for (String path : resourcePaths) {
            TXLoaderCore.getAssetBuilder("minecraft/textures/" + path + ".png")
                .setOverride("minecraft/textures/blocks/" + path + ".png")
                .setVersion("1.20.4")
                .setSource(Asset.Source.CLIENT)
                .add();
        }
    }
}
