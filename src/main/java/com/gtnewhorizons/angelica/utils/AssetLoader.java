package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import glowredman.txloader.Asset;
import glowredman.txloader.TXLoaderCore;

public class AssetLoader {

    public static final String[] injectTexs = {
        "block/stone",
        "block/crafting_table_side",
        "block/crafting_table_front",
        "block/crafting_table_side",
        "block/crafting_table_top",
        "block/oak_planks"
    };

    public static final String[] testTexs = {
        "block/lectern_base",
        "block/lectern_front",
        "block/lectern_base",
        "block/lectern_sides",
        "block/lectern_top",
        "block/oak_planks"
    };

    public static void load() {

        if (AngelicaConfig.injectQPRendering || AngelicaConfig.enableTestBlocks) {
            addModelAssets(
                "block/block",
                "block/cube",
                "block/cube_all"
            );
        }

        if (AngelicaConfig.injectQPRendering) {
            addModelAssets(
                "block/stone",
                "block/crafting_table");
            addTexAssets(injectTexs);
        }

        if (AngelicaConfig.enableTestBlocks) {
            addModelAssets("block/lectern");
            addTexAssets(testTexs);
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
